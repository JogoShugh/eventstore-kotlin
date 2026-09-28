package org.starbornag.eventstore.e06_time_travelling

import assertk.assertThat
import assertk.assertions.isEqualTo
import bankaccounts.BankAccount
import bankaccounts.BankAccount.BankAccountStatus.Opened
import bankaccounts.BankAccountEvents
import org.junit.jupiter.api.Test
import org.starbornag.eventstore.aggregateStream
import org.starbornag.eventstore.tools.PostgresTest
import org.starbornag.eventstore.tools.test
import java.util.*

class TimeTravellingTests : PostgresTest() {

    @Test
    fun `aggregateStream should return specified version of the stream`() = test {
        val account = BankAccountEvents.create()
        eventStore.appendEvents(BankAccount::class, account.bankAccountId, account.all)

        assertThat(bankAccount(account.bankAccountId, atStreamVersion = 0))
            .isEqualTo(BankAccount(account.bankAccountId, Opened, 0.0, 0))
        assertThat(bankAccount(account.bankAccountId, atStreamVersion = 1))
            .isEqualTo(BankAccount(account.bankAccountId, Opened, 100.0, 1))
        assertThat(bankAccount(account.bankAccountId, atStreamVersion = 2))
            .isEqualTo(BankAccount(account.bankAccountId, Opened, 50.0, 2))
    }

    @Test
    fun `aggregateStream should return state at specified timestamp`() = test {
        val account = BankAccountEvents.create()
        eventStore.appendEvents(BankAccount::class, account.bankAccountId, listOf(account.opened, account.deposited))
        val afterDeposit = eventStore.readStream(account.bankAccountId).last().created
        Thread.sleep(10)
        eventStore.appendEvents(BankAccount::class, account.bankAccountId, listOf(account.withdrawn))

        assertThat(bankAccount(account.bankAccountId, atTimestamp = afterDeposit))
            .isEqualTo(BankAccount(account.bankAccountId, Opened, 100.0, 1))
        assertThat(bankAccount(account.bankAccountId))
            .isEqualTo(BankAccount(account.bankAccountId, Opened, 50.0, 2))
    }

    private suspend fun bankAccount(
        id: UUID,
        atStreamVersion: Long? = null,
        atTimestamp: java.time.OffsetDateTime? = null
    ) = eventStore.aggregateStream(id, { null }, BankAccount::evolve, atStreamVersion, atTimestamp)
}
