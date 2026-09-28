package org.starbornag.eventstore.cucumber

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import bankaccounts.BankAccount
import io.cucumber.datatable.DataTable
import io.cucumber.java.ParameterType
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.starbornag.eventstore.aggregateStream
import java.time.OffsetDateTime
import java.util.*

/** A stream version to aggregate up to as written in Gherkin: a number or "latest". */
data class AtVersion(val value: Long?)

class AggregationSteps(private val world: EventStoreWorld) {

    @ParameterType("latest|\\d+")
    fun atVersion(text: String) = AtVersion(if (text == "latest") null else text.toLong())

    @Given("the time of the last event is noted")
    fun theTimeOfTheLastEventIsNoted() = world.blocking {
        world.notedTime = world.eventStore.readStream(world.streamId).last().created
    }

    @When("the bank account is aggregated at version {atVersion}")
    fun theBankAccountIsAggregatedAtVersion(atVersion: AtVersion) = world.blocking {
        world.aggregate = aggregate(atStreamVersion = atVersion.value)
    }

    @When("the bank account is aggregated as of the noted time")
    fun theBankAccountIsAggregatedAsOfTheNotedTime() = world.blocking {
        world.aggregate = aggregate(atTimestamp = world.notedTime!!)
    }

    @When("an unknown bank account is aggregated")
    fun anUnknownBankAccountIsAggregated() = world.blocking {
        world.streamId = UUID.randomUUID()
        world.aggregate = aggregate()
    }

    @Then("the bank account is:")
    fun theBankAccountIs(table: DataTable) {
        val expected = table.asMaps().single()
        val account = world.aggregate!!
        assertThat(account.status.name).isEqualTo(expected["status"])
        assertThat(account.balance).isEqualTo(expected["balance"]!!.toDouble())
        assertThat(account.version).isEqualTo(expected["version"]!!.toLong())
    }

    @Then("there is no bank account")
    fun thereIsNoBankAccount() {
        assertThat(world.aggregate).isNull()
    }

    private suspend fun aggregate(atStreamVersion: Long? = null, atTimestamp: OffsetDateTime? = null) =
        world.eventStore.aggregateStream<BankAccount?, BankAccount.Event>(
            world.streamId, { null }, BankAccount::evolve, atStreamVersion, atTimestamp
        )
}
