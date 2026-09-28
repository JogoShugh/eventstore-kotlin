package org.starbornag.eventstore.cucumber

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.starbornag.eventstore.SnapshotToTable
import org.starbornag.eventstore.value
import users.User

class SnapshotSteps(private val world: EventStoreWorld) {

    private var queriedNames: List<String> = emptyList()

    @Given("user snapshots are stored in a users table")
    fun userSnapshotsAreStoredInAUsersTable() = world.blocking {
        world.sql { it.execute(CREATE_USERS_TABLE) }
        world.userSnapshot = SnapshotToTable(UPSERT_USER) { user: User?, version ->
            listOf(user!!.id, user.name, version)
        }
    }

    @Given("the users table refuses the name {string}")
    fun theUsersTableRefusesTheName(name: String) = world.blocking {
        // DDL cannot take bind parameters; the name comes from the feature file, not from users.
        world.sql { it.execute("ALTER TABLE users ADD CONSTRAINT refused_name CHECK (name <> '$name')") }
    }

    @When("the users table is queried for names containing {string}")
    fun theUsersTableIsQueried(fragment: String) = world.blocking {
        queriedNames = world.sql { session ->
            session.query("SELECT name FROM users WHERE name LIKE \$1 ORDER BY name", "%$fragment%") {
                it.value<String>("name")!!
            }
        }
    }

    @Then("the query returns:")
    fun theQueryReturns(table: DataTable) {
        assertThat(queriedNames).isEqualTo(table.asMaps().map { it["name"] })
    }

    @Then("the users table contains:")
    fun theUsersTableContains(table: DataTable) = world.blocking {
        val rows = world.sql { session ->
            session.query("SELECT name, version FROM users ORDER BY name") {
                mapOf("name" to it.value<String>("name"), "version" to it.value<Long>("version").toString())
            }
        }
        assertThat(rows).isEqualTo(table.asMaps())
    }

    private companion object {
        const val CREATE_USERS_TABLE = """
            CREATE TABLE users (
                id      UUID    NOT NULL PRIMARY KEY,
                name    TEXT    NOT NULL,
                version BIGINT  NOT NULL
            )
        """

        const val UPSERT_USER = """
            INSERT INTO users (id, name, version) VALUES ($1, $2, $3)
            ON CONFLICT (id) DO UPDATE SET name = $2, version = $3
        """
    }
}
