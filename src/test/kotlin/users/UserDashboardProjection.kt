package users

import org.starbornag.eventstore.Projection

/** Read model across two streams: a user's name plus the count and total of their orders. */
class UserDashboardProjection : Projection() {
    init {
        projects<User.Event.UserCreated> { session, event ->
            session.execute(
                "INSERT INTO user_dashboards (id, user_name, orders_count, total_amount) VALUES (\$1, \$2, 0, 0)",
                event.userId, event.userName
            )
        }
        projects<User.Event.UserNameUpdated> { session, event ->
            session.execute("UPDATE user_dashboards SET user_name = \$2 WHERE id = \$1", event.userId, event.userName)
        }
        projects<Order.Event.OrderCreated> { session, event ->
            session.execute(
                """
                UPDATE user_dashboards
                SET orders_count = orders_count + 1, total_amount = total_amount + $2
                WHERE id = $1
                """.trimIndent(),
                event.userId, event.amount
            )
        }
    }

    companion object {
        const val CREATE_TABLE = """
            CREATE TABLE user_dashboards (
                id            UUID     NOT NULL PRIMARY KEY,
                user_name     TEXT     NOT NULL,
                orders_count  INTEGER  NOT NULL,
                total_amount  DECIMAL  NOT NULL
            )
        """
    }
}
