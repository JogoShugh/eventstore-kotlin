package org.starbornag.eventstore.cucumber

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import bankaccounts.BankAccount
import bankaccounts.amount
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import java.util.*

class ReadSteps(private val world: EventStoreWorld) {

    @When("the stream is read")
    fun theStreamIsRead() = world.blocking {
        world.readEvents = world.eventStore.readStream(world.streamId)
    }

    @When("an unknown stream is read")
    fun anUnknownStreamIsRead() = world.blocking {
        world.streamId = UUID.randomUUID()
        world.readEvents = world.eventStore.readStream(world.streamId)
    }

    @Then("the stream contains:")
    fun theStreamContains(table: DataTable) {
        val expected = table.asMaps().map { row ->
            listOf(row["version"]!!.toLong(), row["event"], row["amount"]?.toDoubleOrNull())
        }
        val actual = world.readEvents.map { recorded ->
            listOf(recorded.version, recorded.data::class.simpleName, (recorded.data as BankAccount.Event).amount)
        }
        assertThat(actual).isEqualTo(expected)
    }

    @Then("every event equals the one that was appended")
    fun everyEventEqualsTheAppendedOne() {
        assertThat(world.readEvents.map { it.data }).isEqualTo(world.appended)
    }

    @Then("the stream contains no events")
    fun theStreamContainsNoEvents() {
        assertThat(world.readEvents).isEmpty()
    }
}
