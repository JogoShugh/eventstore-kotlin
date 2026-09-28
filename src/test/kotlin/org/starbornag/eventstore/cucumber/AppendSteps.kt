package org.starbornag.eventstore.cucumber

import assertk.assertThat
import assertk.assertions.each
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import io.cucumber.java.ParameterType
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.starbornag.eventstore.EventStore.Companion.NO_STREAM
import org.starbornag.eventstore.WrongExpectedVersion

/** An expected stream version as written in Gherkin: a number, "no stream" or "any". */
data class ExpectedVersion(val value: Long?)

class AppendSteps(private val world: EventStoreWorld) {

    @ParameterType("no stream|any|-?\\d+")
    fun expected(text: String) = ExpectedVersion(
        when (text) {
            "no stream" -> NO_STREAM
            "any" -> null
            else -> text.toLong()
        }
    )

    @When("{int} event(s) is/are appended to a new bank account stream")
    fun eventsAreAppendedToANewStream(count: Int) = world.blocking {
        world.appendOutcome = runCatching { world.append(world.account.numbered(count)) }
    }

    @When("{int} events are appended expecting version {expected}")
    fun eventsAreAppendedExpectingVersion(count: Int, expected: ExpectedVersion) = world.blocking {
        val events = world.account.numbered(count, fromVersion = world.appended.size.toLong())
        world.appendOutcome = runCatching { world.append(events, expected.value) }
    }

    @When("{int} writers each append 1 event expecting version {expected} at the same time")
    fun writersAppendConcurrently(writers: Int, expected: ExpectedVersion) = world.blocking {
        val event = world.account.numbered(1, fromVersion = world.appended.size.toLong())
        world.concurrentOutcomes = (1..writers).map {
            async(Dispatchers.IO) { runCatching { world.append(event, expected.value) } }
        }.awaitAll()
    }

    @Then("the append is accepted")
    fun theAppendIsAccepted() {
        world.appendOutcome!!.getOrThrow()
    }

    @Then("the append is rejected")
    fun theAppendIsRejected() = world.blocking {
        val error = world.appendOutcome!!.exceptionOrNull()
        assertThat(error).isNotNull().isInstanceOf(WrongExpectedVersion::class)
        // The rejection reports the version the stream really has, which a client needs to re-sync.
        val actual = world.eventStore.getStreamState(world.account.bankAccountId)?.version
        assertThat((error as WrongExpectedVersion).actualVersion).isEqualTo(actual)
    }

    @Then("exactly 1 writer is accepted and the rest are rejected")
    fun exactlyOneWriterIsAccepted() {
        val (accepted, rejected) = world.concurrentOutcomes.partition { it.isSuccess }
        assertThat(accepted.size).isEqualTo(1)
        assertThat(rejected.map { it.exceptionOrNull() is WrongExpectedVersion }).each { it.isTrue() }
    }
}
