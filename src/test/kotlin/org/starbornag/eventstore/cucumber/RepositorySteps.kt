package org.starbornag.eventstore.cucumber

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.starbornag.eventstore.Repository
import org.starbornag.eventstore.Versioned
import org.starbornag.eventstore.WrongExpectedVersion
import users.User
import java.util.*

class RepositorySteps(private val world: EventStoreWorld) {

    private val repository by lazy {
        Repository<User?, User.Event>(world.eventStore, User::class, { null }, User::evolve)
    }

    private var userId: UUID = UUID.randomUUID()
    private var commandOutcome: Result<Versioned<User?>>? = null
    private var loadedUser: Versioned<User?>? = null

    @When("a user is created with name {string}")
    fun aUserIsCreated(name: String) = world.blocking {
        repository.handle(userId, decide = User.create(userId, name))
    }

    @When("the user is renamed to {string}")
    fun theUserIsRenamed(name: String) = world.blocking {
        repository.handle(userId, decide = User.rename(name))
    }

    @Given("a user {string} renamed {int} times")
    fun aUserRenamedTimes(name: String, renames: Int) = world.blocking {
        repository.handle(userId, decide = User.create(userId, name))
        (1..renames).forEach { repository.handle(userId, decide = User.rename("Rename $it")) }
    }

    @When("the user is renamed to {string} expecting version {expected}")
    fun theUserIsRenamedExpectingVersion(name: String, expected: ExpectedVersion) = world.blocking {
        commandOutcome = runCatching { repository.handle(userId, expected.value, User.rename(name)) }
    }

    @When("an unknown user is loaded")
    fun anUnknownUserIsLoaded() = world.blocking {
        loadedUser = repository.find(UUID.randomUUID())
    }

    @Then("the command is accepted")
    fun theCommandIsAccepted() {
        commandOutcome!!.getOrThrow()
    }

    @Then("the command is rejected")
    fun theCommandIsRejected() {
        assertThat(commandOutcome!!.exceptionOrNull()).isNotNull().isInstanceOf(WrongExpectedVersion::class)
    }

    @Then("the user loaded from the repository is:")
    fun theUserLoadedIs(table: DataTable) = world.blocking {
        val expected = table.asMaps().single()
        val loaded = repository.find(userId)!!
        assertThat(loaded.state?.name).isEqualTo(expected["name"])
        assertThat(loaded.version).isEqualTo(expected["version"]!!.toLong())
    }

    @Then("no user is found")
    fun noUserIsFound() {
        assertThat(loadedUser).isNull()
    }
}
