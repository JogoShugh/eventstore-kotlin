package org.starbornag.eventstore.cucumber

import bankaccounts.BankAccount
import bankaccounts.BankAccountEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.starbornag.eventstore.EventStore
import org.starbornag.eventstore.PgEventStore
import org.starbornag.eventstore.RecordedEvent
import org.starbornag.eventstore.tools.PostgresDatabase
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

    fun startScenario() = blocking {
        val schemaName = "scenario_" + UUID.randomUUID().toString().replace("-", "")
        eventStore = PgEventStore(PostgresDatabase.freshSchema(schemaName))
        eventStore.init()
    }

    suspend fun append(events: List<BankAccount.Event>, expectedVersion: Long? = null): Long =
        eventStore.appendEvents(BankAccount::class, account.bankAccountId, events, expectedVersion)
            .also { appended += events }

    fun <T> blocking(block: suspend CoroutineScope.() -> T): T = runBlocking(block = block)
}
