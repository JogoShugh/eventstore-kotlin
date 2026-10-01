package org.starbornag.eventstore

import io.r2dbc.postgresql.codec.Json
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import java.time.OffsetDateTime
import java.util.*
import kotlin.reflect.KClass

/**
 * PostgreSQL event store over R2DBC.
 *
 * Every append runs in one transaction, together with the matching [projections]
 * and the caller's `beforeCommit` hook, so read models never see a partial append.
 */
class PgEventStore(
    private val connectionFactory: ConnectionFactory,
    private val projections: List<Projection> = emptyList(),
    private val typeMapper: EventTypeMapper = EventTypeMapper(),
    private val serializer: EventSerializer = JacksonEventSerializer()
) : EventStore {

    private val schemaSql: String by lazy {
        checkNotNull(PgEventStore::class.java.getResource("schema.sql")) { "schema.sql resource not found" }
            .readText()
    }

    override suspend fun init() {
        connectionFactory.withSession { it.execute(schemaSql) }
    }

    override suspend fun appendEvents(
        streamType: KClass<*>,
        streamId: UUID,
        events: List<Any>,
        expectedVersion: Long?,
        beforeCommit: suspend (session: SqlSession, newVersion: Long) -> Unit
    ): Long {
        require(events.isNotEmpty()) { "No events to append to stream $streamId" }

        return connectionFactory.inTransaction { session ->
            events.forEachIndexed { index, event ->
                // Each event in a batch advances the stream by one, so the expected version does too.
                val expectedForEvent = expectedVersion?.plus(index)
                if (!appendEvent(session, streamType, streamId, event, expectedForEvent)) {
                    throw WrongExpectedVersion(streamId, expectedVersion, streamVersion(session, streamId))
                }
                projections
                    .filter { it.handles(event::class) }
                    .forEach { it.handle(session, event) }
            }
            val newVersion = checkNotNull(streamVersion(session, streamId)) { "Stream $streamId missing after append" }
            beforeCommit(session, newVersion)
            newVersion
        }
    }

    private suspend fun appendEvent(
        session: SqlSession,
        streamType: KClass<*>,
        streamId: UUID,
        event: Any,
        expectedVersion: Long?
    ): Boolean =
        try {
            session.querySingleOrNull(
                "SELECT append_event($1, $2, $3, $4, $5, $6) AS succeeded",
                UUID.randomUUID(),
                Json.of(serializer.serialize(event)),
                typeMapper.toName(event::class),
                streamId,
                typeMapper.toName(streamType),
                expectedVersion ?: sqlNull<Long>()
            ) { it.value<Boolean>("succeeded") } == true
        } catch (e: R2dbcDataIntegrityViolationException) {
            // A concurrent transaction created the stream (or the same version) first.
            throw WrongExpectedVersion(streamId, expectedVersion, null, e)
        }

    override suspend fun readStream(
        streamId: UUID,
        atStreamVersion: Long?,
        atTimestamp: OffsetDateTime?
    ): List<RecordedEvent> {
        val params = mutableListOf<Any?>(streamId)
        val sql = buildString {
            append("SELECT id, data, stream_id, type, version, created FROM events WHERE stream_id = \$1")
            atStreamVersion?.let {
                params += it
                append(" AND version <= \$${params.size}")
            }
            atTimestamp?.let {
                params += it
                append(" AND created <= \$${params.size}")
            }
            append(" ORDER BY version")
        }

        return connectionFactory.withSession { session ->
            session.query(sql, params) { row ->
                val type = row.value<String>("type")!!
                val eventClass = checkNotNull(typeMapper.toType(type)) {
                    "Unknown event type '$type' in stream $streamId"
                }
                RecordedEvent(
                    id = row.value<UUID>("id")!!,
                    streamId = row.value<UUID>("stream_id")!!,
                    type = type,
                    version = row.value<Long>("version")!!,
                    created = row.value<OffsetDateTime>("created")!!,
                    data = serializer.deserialize(row.value<Json>("data")!!.asString(), eventClass)
                )
            }
        }
    }

    override suspend fun getStreamState(streamId: UUID): StreamState? =
        connectionFactory.withSession { session ->
            session.querySingleOrNull("SELECT id, type, version FROM streams WHERE id = \$1", streamId) { row ->
                StreamState(
                    id = row.value<UUID>("id")!!,
                    type = row.value<String>("type")!!,
                    version = row.value<Long>("version")!!
                )
            }
        }

    override suspend fun streamIds(streamType: KClass<*>): List<UUID> =
        connectionFactory.withSession { session ->
            session.query(
                """
                SELECT s.id FROM streams s
                JOIN events e ON e.stream_id = s.id AND e.version = 0
                WHERE s.type = ${'$'}1
                ORDER BY e.created, s.id
                """.trimIndent(),
                typeMapper.toName(streamType)
            ) { it.value<UUID>("id")!! }
        }

    private suspend fun streamVersion(session: SqlSession, streamId: UUID): Long? =
        session.querySingleOrNull("SELECT version FROM streams WHERE id = \$1", streamId) {
            it.value<Long>("version")
        }
}
