package org.starbornag.eventstore

import java.time.OffsetDateTime
import java.util.*
import kotlin.reflect.KClass

/**
 * Append-only store of event streams.
 *
 * Stream versions are 0-based: the first event of a stream has version 0.
 * An `expectedVersion` of `null` accepts any version, [NO_STREAM] requires that the
 * stream does not exist yet, and `n` requires that the stream's last event has version `n`.
 */
interface EventStore {
    companion object {
        const val NO_STREAM = -1L
    }

    /** Creates the tables and the `append_event` function if they do not exist. */
    suspend fun init()

    /**
     * Appends [events] to the stream atomically and returns the new stream version.
     * [beforeCommit] runs in the same transaction, after the events are appended.
     *
     * @throws WrongExpectedVersion when [expectedVersion] does not match the stream.
     */
    suspend fun appendEvents(
        streamType: KClass<*>,
        streamId: UUID,
        events: List<Any>,
        expectedVersion: Long? = null,
        beforeCommit: suspend (session: SqlSession, newVersion: Long) -> Unit = { _, _ -> }
    ): Long

    suspend fun readStream(
        streamId: UUID,
        atStreamVersion: Long? = null,
        atTimestamp: OffsetDateTime? = null
    ): List<RecordedEvent>

    suspend fun getEvents(
        streamId: UUID,
        atStreamVersion: Long? = null,
        atTimestamp: OffsetDateTime? = null
    ): List<Any> = readStream(streamId, atStreamVersion, atTimestamp).map { it.data }

    suspend fun getStreamState(streamId: UUID): StreamState?
}

data class RecordedEvent(
    val id: UUID,
    val streamId: UUID,
    val type: String,
    val version: Long,
    val created: OffsetDateTime,
    val data: Any
)

data class StreamState(
    val id: UUID,
    val type: String,
    val version: Long
)

class WrongExpectedVersion(
    val streamId: UUID,
    val expectedVersion: Long?,
    val actualVersion: Long?,
    cause: Throwable? = null
) : RuntimeException(
    "Stream $streamId: expected version $expectedVersion but was ${actualVersion ?: "unknown"}",
    cause
)

/**
 * Rebuilds state by folding [evolve] over the stream's events, starting from [initial].
 * Returns `null` when the stream has no events (at the given version or time).
 */
suspend fun <S, E : Any> EventStore.aggregateStream(
    streamId: UUID,
    initial: () -> S,
    evolve: (S, E) -> S,
    atStreamVersion: Long? = null,
    atTimestamp: OffsetDateTime? = null
): S? {
    val events = getEvents(streamId, atStreamVersion, atTimestamp)
    if (events.isEmpty()) return null
    @Suppress("UNCHECKED_CAST")
    return events.fold(initial()) { state, event -> evolve(state, event as E) }
}
