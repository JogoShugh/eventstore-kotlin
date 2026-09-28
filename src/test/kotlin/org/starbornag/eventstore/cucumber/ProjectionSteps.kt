package org.starbornag.eventstore.cucumber

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.starbornag.eventstore.Repository
import org.starbornag.eventstore.value
import users.Order
import users.UserDashboardProjection
import java.math.BigDecimal
import java.util.*

class ProjectionSteps(private val world: EventStoreWorld) {

    private val orders by lazy {
        Repository<Order?, Order.Event>(world.eventStore, Order::class, { null }, Order::evolve)
    }

    @Given("a user dashboard projection into a user_dashboards table")
    fun aUserDashboardProjection() = world.blocking {
        world.sql { it.execute(UserDashboardProjection.CREATE_TABLE) }
        world.useProjections(UserDashboardProjection())
    }

    @Given("the user_dashboards table refuses the user name {string}")
    fun theDashboardsTableRefusesTheUserName(name: String) = world.blocking {
        // DDL cannot take bind parameters; the name comes from the feature file, not from users.
        world.sql { it.execute("ALTER TABLE user_dashboards ADD CONSTRAINT refused_name CHECK (user_name <> '$name')") }
    }

    @Given("the user places these orders:")
    fun theUserPlacesTheseOrders(table: DataTable) = world.blocking {
        table.asMaps().forEach { row ->
            val orderId = UUID.randomUUID()
            val placeOrder = Order.create(orderId, world.userId, row["number"]!!, BigDecimal(row["amount"]))
            orders.handle(orderId, decide = placeOrder)
        }
    }

    @Then("the user dashboard is:")
    fun theUserDashboardIs(table: DataTable) = world.blocking {
        val expected = table.asMaps().single()
        val dashboard = world.sql { session ->
            session.querySingleOrNull(
                "SELECT user_name, orders_count, total_amount FROM user_dashboards WHERE id = \$1",
                world.userId
            ) { row ->
                Triple(
                    row.value<String>("user_name"),
                    row.value<Int>("orders_count"),
                    row.value<BigDecimal>("total_amount")
                )
            }
        }!!
        assertThat(dashboard.first).isEqualTo(expected["user name"])
        assertThat(dashboard.second).isEqualTo(expected["orders"]!!.toInt())
        assertThat(dashboard.third!!.compareTo(BigDecimal(expected["total amount"]))).isEqualTo(0)
    }
}
