package org.starbornag.eventstore.e04_event_store_methods

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import bankaccounts.BankAccount
import bankaccounts.BankAccountEvents
import org.junit.jupiter.api.Test
import org.starbornag.eventstore.StreamState
import org.starbornag.eventstore.tools.PostgresTest
import org.starbornag.eventstore.tools.test
import java.util.*

class EventStoreMethodsTests : PostgresTest() {

    @Test
    fun `getEvents should return appended events`() = test {
        val account = BankAccountEvents.create()
        eventStore.appendEvents(BankAccount::class, account.bankAccountId, account.all)

        val events = eventStore.getEvents(account.bankAccountId)

        assertThat(events).containsExactly(account.opened, account.deposited, account.withdrawn)
    }

    @Test
    fun `readStream should return events with 0-based versions`() = test {
        val account = BankAccountEvents.create()
        eventStore.appendEvents(BankAccount::class, account.bankAccountId, account.all)

        val versions = eventStore.readStream(account.bankAccountId).map { it.version }

        assertThat(versions).containsExactly(0L, 1L, 2L)
    }

    @Test
    fun `getStreamState should return stream type and version`() = test {
        val account = BankAccountEvents.create()
        eventStore.appendEvents(BankAccount::class, account.bankAccountId, account.all)

        val state = eventStore.getStreamState(account.bankAccountId)

        assertThat(state).isEqualTo(StreamState(account.bankAccountId, BankAccount::class.java.name, 2))
    }

    @Test
    fun `getStreamState should return null for unknown stream`() = test {
        assertThat(eventStore.getStreamState(UUID.randomUUID())).isNull()
    }
}
