package org.starbornag.eventstore.cucumber

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import bankaccounts.BankAccount
import bankaccounts.BankAccountEvents
import io.cucumber.datatable.DataTable
import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import java.util.UUID
import kotlinx.coroutines.delay
import users.User

/** Steps shared by several features: arranging streams and checking their state. */
class StreamSteps(private val world: EventStoreWorld) {

    @Before
    fun startScenario() = world.startScenario()

    @Given("a bank account stream with {int} events")
    fun aBankAccountStreamWithEvents(count: Int) = world.blocking {
        if (count > 0) world.append(world.account.numbered(count))
    }

    @Given("a bank account with these events:")
    fun aBankAccountWithTheseEvents(table: DataTable) = world.blocking {
        world.append(eventsFrom(table, fromVersion = 0))
    }

    @Given("later these events are appended:")
    fun laterTheseEventsAreAppended(table: DataTable) = world.blocking {
        // Leave a visible gap after the noted time; each append is its own transaction and timestamp.
        delay(GAP_MILLIS)
        world.append(eventsFrom(table, fromVersion = world.appended.size.toLong()))
    }

    @Then("the stream state is:")
    fun theStreamStateIs(table: DataTable) = world.blocking {
        val expected = table.asMaps().single()
        val state = world.eventStore.getStreamState(world.streamId)!!
        assertThat(state.type).isEqualTo(expected["type"])
        assertThat(state.version).isEqualTo(expected["version"]!!.toLong())
    }

    @Then("the stream version is {long}")
    fun theStreamVersionIs(version: Long) = world.blocking {
        assertThat(world.eventStore.getStreamState(world.streamId)?.version).isEqualTo(version)
    }

    @Then("there is no stream state")
    fun thereIsNoStreamState() = world.blocking {
        assertThat(world.eventStore.getStreamState(world.streamId)).isNull()
    }

    private var otherAccount: UUID? = null
    private var listed: List<UUID> = emptyList()

    @Given("another bank account opened later")
    fun anotherBankAccountOpenedLater() = world.blocking {
        delay(GAP_MILLIS)
        val other = BankAccountEvents()
        world.eventStore.appendEvents(BankAccount::class, other.bankAccountId, other.numbered(1))
        otherAccount = other.bankAccountId
    }

    @Given("a stream of another type")
    fun aStreamOfAnotherType() = world.blocking {
        world.eventStore.appendEvents(User::class, UUID.randomUUID(), BankAccountEvents().numbered(1))
    }

    @When("the bank account streams are listed")
    fun theBankAccountStreamsAreListed() = world.blocking {
        listed = world.eventStore.streamIds(BankAccount::class)
    }

    @Then("the listed streams are this bank account, then the other one")
    fun theListedStreamsAreThisThenTheOther() {
        assertThat(listed).isEqualTo(listOf(world.account.bankAccountId, otherAccount))
    }

    private fun eventsFrom(table: DataTable, fromVersion: Long) =
        table.asMaps().mapIndexed { index, row ->
            world.account.event(row["event"]!!, row["amount"]?.toDoubleOrNull(), fromVersion + index)
        }

    private companion object {
        const val GAP_MILLIS = 20L
    }
}
