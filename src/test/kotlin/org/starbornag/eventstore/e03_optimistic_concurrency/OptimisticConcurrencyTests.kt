package org.starbornag.eventstore.e03_optimistic_concurrency

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isEmpty
import assertk.assertions.prop
import bankaccounts.BankAccount
import bankaccounts.BankAccountEvents
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Test
import org.starbornag.eventstore.EventStore.Companion.NO_STREAM
import org.starbornag.eventstore.WrongExpectedVersion
import org.starbornag.eventstore.tools.PostgresTest
import org.starbornag.eventstore.tools.test

class OptimisticConcurrencyTests : PostgresTest() {

    @Test
    fun `append with matching expected version succeeds`() = test {
        val account = BankAccountEvents.create()
        eventStore.appendEvents(BankAccount::class, account.bankAccountId, listOf(account.opened), NO_STREAM)

        val newVersion = eventStore.appendEvents(BankAccount::class, account.bankAccountId, listOf(account.deposited), 0)

        assertThat(newVersion).isEqualTo(1L)
    }

    @Test
    fun `append with wrong expected version fails and reports actual version`() = test {
        val account = BankAccountEvents.create()
        eventStore.appendEvents(BankAccount::class, account.bankAccountId, listOf(account.opened, account.deposited))

        assertFailure {
            eventStore.appendEvents(BankAccount::class, account.bankAccountId, listOf(account.withdrawn), 0)
        }.isInstanceOf(WrongExpectedVersion::class).prop(WrongExpectedVersion::actualVersion).isEqualTo(1L)
    }

    @Test
    fun `append with no-stream expected version fails when stream exists`() = test {
        val account = BankAccountEvents.create()
        eventStore.appendEvents(BankAccount::class, account.bankAccountId, listOf(account.opened))

        assertFailure {
            eventStore.appendEvents(BankAccount::class, account.bankAccountId, listOf(account.opened), NO_STREAM)
        }.isInstanceOf(WrongExpectedVersion::class)
    }

    // Oskar's JVM appendEvents passed the same expected version for every event,
    // so a batch of more than one event with an expected version always failed.
    @Test
    fun `batch append with expected version succeeds`() = test {
        val account = BankAccountEvents.create()

        val newVersion = eventStore.appendEvents(BankAccount::class, account.bankAccountId, account.all, NO_STREAM)

        assertThat(newVersion).isEqualTo(2L)
        assertThat(eventStore.getEvents(account.bankAccountId)).containsExactly(*account.all.toTypedArray())
    }

    @Test
    fun `failed batch append leaves no partial events`() = test {
        val account = BankAccountEvents.create()
        eventStore.appendEvents(BankAccount::class, account.bankAccountId, listOf(account.opened))

        assertFailure {
            // Expected version 5 is wrong for the first event, so nothing of the batch may land.
            eventStore.appendEvents(BankAccount::class, account.bankAccountId, listOf(account.deposited, account.withdrawn), 5)
        }.isInstanceOf(WrongExpectedVersion::class)

        assertThat(eventStore.getEvents(account.bankAccountId)).containsExactly(account.opened)
    }

    @Test
    fun `concurrent appends with same expected version - exactly one wins`() = test {
        val account = BankAccountEvents.create()
        eventStore.appendEvents(BankAccount::class, account.bankAccountId, listOf(account.opened))

        val results = (1..5).map {
            async(Dispatchers.IO) {
                runCatching {
                    eventStore.appendEvents(BankAccount::class, account.bankAccountId, listOf(account.deposited), 0)
                }
            }
        }.awaitAll()

        assertThat(results.count { it.isSuccess }).isEqualTo(1)
        assertThat(results.mapNotNull { it.exceptionOrNull() }.filterNot { it is WrongExpectedVersion }).isEmpty()
        assertThat(eventStore.getStreamState(account.bankAccountId)!!.version).isEqualTo(1L)
    }

    @Test
    fun `concurrent creation of a new stream - exactly one wins`() = test {
        val account = BankAccountEvents.create()

        val results = (1..5).map {
            async(Dispatchers.IO) {
                runCatching {
                    eventStore.appendEvents(BankAccount::class, account.bankAccountId, listOf(account.opened), NO_STREAM)
                }
            }
        }.awaitAll()

        assertThat(results.count { it.isSuccess }).isEqualTo(1)
        assertThat(results.mapNotNull { it.exceptionOrNull() }.filterNot { it is WrongExpectedVersion }).isEmpty()
        assertThat(eventStore.getEvents(account.bankAccountId)).containsExactly(account.opened)
    }
}
