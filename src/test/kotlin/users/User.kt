package users

import java.util.*

/** Test domain from the .NET workshop's steps 07-09, written as decide/evolve functions. */
data class User(val id: UUID, val name: String) {

    sealed interface Event {
        data class UserCreated(val userId: UUID, val userName: String) : Event
        data class UserNameUpdated(val userId: UUID, val userName: String) : Event
    }

    companion object {
        fun evolve(user: User?, event: Event): User? =
            when (event) {
                is Event.UserCreated -> User(event.userId, event.userName)
                is Event.UserNameUpdated -> user?.copy(name = event.userName)
            }

        fun create(id: UUID, name: String): (User?) -> List<Event> = { user ->
            check(user == null) { "User $id already exists" }
            listOf(Event.UserCreated(id, name))
        }

        /** Renaming to the current name decides nothing, so no event is recorded. */
        fun rename(name: String): (User?) -> List<Event> = { user ->
            checkNotNull(user) { "Cannot rename a user that does not exist" }
            if (user.name == name) emptyList() else listOf(Event.UserNameUpdated(user.id, name))
        }
    }
}
