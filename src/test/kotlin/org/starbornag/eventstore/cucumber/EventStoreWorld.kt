package org.starbornag.eventstore.cucumber

import bankaccounts.BankAccount
import bankaccounts.BankAccountEvents
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.starbornag.eventstore.EventStore
import org.starbornag.eventstore.PgEventStore
import org.starbornag.eventstore.Projection
import org.starbornag.eventstore.RecordedEvent
import org.starbornag.eventstore.Snapshot
import org.starbornag.eventstore.SqlSession
import org.starbornag.eventstore.tools.PostgresDatabase
import org.starbornag.eventstore.withSession
import users.User
import java.time.OffsetDateTime
import java.util.*

/** Per-scenario state shared by the step classes through picocontainer. */
class EventStoreWorld {
    lateinit var eventStore: EventStore

    val account = BankAccountEvents()
    val appended = mutableListOf<BankAccount.Event>()

    /** The stream the When steps read or aggregate; an unknown one when a step asks for it. */
    var streamId: UUID = account.bankAccountId

    var appendOutcome: Result<Long>? = null
    var concurrentOutcomes: List<Result<Long>> = emptyList()
    var readEvents: List<RecordedEvent> = emptyList()
    var notedTime: OffsetDateTime? = null
    var aggregate: BankAccount? = null

    val userId: UUID = UUID.randomUUID()

    /** Set by a Background step before the user repository is first used. */
    var userSnapshot: Snapshot<User?>? = null

    private lateinit var connectionFactory: ConnectionFactory

    fun startScenario() = blocking {
        val schemaName = "scenario_" + UUID.randomUUID().toString().replace("-", "")
        connectionFactory = PostgresDatabase.freshSchema(schemaName)
        eventStore = PgEventStore(connectionFactory)
        eventStore.init()
    }

    /** Replaces the event store with one that runs [projections]; call before any repository is used. */
    fun useProjections(vararg projections: Projection) {
        eventStore = PgEventStore(connectionFactory, projections.asList())
    }

    /** Runs SQL directly against the scenario's schema, outside the event store. */
    suspend fun <T> sql(block: suspend (SqlSession) -> T): T = connectionFactory.withSession(block)

    suspend fun append(events: List<BankAccount.Event>, expectedVersion: Long? = null): Long =
        eventStore.appendEvents(BankAccount::class, account.bankAccountId, events, expectedVersion)
            .also { appended += events }

    fun <T> blocking(block: suspend CoroutineScope.() -> T): T = runBlocking(block = block)
}
