package users

import java.math.BigDecimal
import java.util.*

/** Test domain from the .NET workshop's step 09: an order placed by a user. */
data class Order(val id: UUID, val userId: UUID, val number: String, val amount: BigDecimal) {

    sealed interface Event {
        data class OrderCreated(val orderId: UUID, val userId: UUID, val number: String, val amount: BigDecimal) : Event
    }

    companion object {
        // An order has only a creation event, so the previous state is never needed.
        @Suppress("UnusedParameter")
        fun evolve(order: Order?, event: Event): Order? =
            when (event) {
                is Event.OrderCreated -> Order(event.orderId, event.userId, event.number, event.amount)
            }

        fun create(id: UUID, userId: UUID, number: String, amount: BigDecimal): (Order?) -> List<Event> = { order ->
            check(order == null) { "Order $id already exists" }
            listOf(Event.OrderCreated(id, userId, number, amount))
        }
    }
}
