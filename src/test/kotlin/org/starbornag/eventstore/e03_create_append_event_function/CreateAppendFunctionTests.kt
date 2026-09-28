package org.starbornag.eventstore.e03_create_append_event_function

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import bankaccounts.BankAccount
import bankaccounts.BankAccountEvents
import org.junit.jupiter.api.Test
import org.starbornag.eventstore.tools.PostgresTest
import org.starbornag.eventstore.tools.test
import org.starbornag.eventstore.value
import java.util.*

class CreateAppendFunctionTests : PostgresTest() {

    @Test
    fun `append_event function should be created`() = test {
        assertThat(schemaProvider.functionExists("append_event")).isTrue()
    }

    @Test
    fun `append_event when stream does not exist creates new stream and appends new event`() = test {
        val account = BankAccountEvents.create()

        val newVersion = eventStore.appendEvents(BankAccount::class, account.bankAccountId, listOf(account.opened))

        assertThat(newVersion).isEqualTo(0L)
        assertThat(exists("SELECT exists(SELECT 1 FROM streams WHERE id = \$1) AS exist", account.bankAccountId)).isTrue()
        assertThat(exists("SELECT exists(SELECT 1 FROM events WHERE stream_id = \$1) AS exist", account.bankAccountId)).isTrue()
    }

    private fun exists(sql: String, id: UUID): Boolean =
        sql { it.querySingleOrNull(sql, id) { row -> row.value<Boolean>("exist") } } == true
}
