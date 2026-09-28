package org.starbornag.eventstore.e05_stream_aggregation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import bankaccounts.BankAccount
import bankaccounts.BankAccountEvents
import org.junit.jupiter.api.Test
import org.starbornag.eventstore.aggregateStream
import org.starbornag.eventstore.tools.PostgresTest
import org.starbornag.eventstore.tools.test
import java.util.*

class StreamAggregationTests : PostgresTest() {

    @Test
    fun `aggregateStream should return object with state based on events`() = test {
        val account = BankAccountEvents.create()
        eventStore.appendEvents(BankAccount::class, account.bankAccountId, account.all)

        val bankAccount = eventStore.aggregateStream(account.bankAccountId, { null }, BankAccount::evolve)

        assertThat(bankAccount).isEqualTo(
            BankAccount(account.bankAccountId, BankAccount.BankAccountStatus.Opened, 50.0, 2)
        )
    }

    @Test
    fun `aggregateStream should return null for empty stream`() = test {
        val bankAccount = eventStore.aggregateStream(UUID.randomUUID(), { null }, BankAccount::evolve)

        assertThat(bankAccount).isNull()
    }
}
