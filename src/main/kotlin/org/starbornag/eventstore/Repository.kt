package org.starbornag.eventstore

import org.starbornag.eventstore.EventStore.Companion.NO_STREAM
import java.util.*
import kotlin.reflect.KClass

/** State rebuilt from a stream, with the version of the stream's last event. */
data class Versioned<S>(val state: S, val version: Long)

/**
 * Loads aggregates by folding their events with [evolve], and handles commands
 * as `decide(state) -> events`, appended at the version the state was loaded at.
 *
 * This is the functional take on the .NET workshop's step 07 (aggregate and repository):
 * there is no mutable aggregate base class, only `initial`, `evolve` and a decide function.
 */
class Repository<S, E : Any>(
    private val eventStore: EventStore,
    private val streamType: KClass<*>,
    private val initial: () -> S,
    private val evolve: (S, E) -> S
) {
    suspend fun find(id: UUID): Versioned<S>? {
        val recorded = eventStore.readStream(id)
        if (recorded.isEmpty()) return null
        @Suppress("UNCHECKED_CAST")
        val state = recorded.fold(initial()) { state, event -> evolve(state, event.data as E) }
        return Versioned(state, recorded.last().version)
    }

    /**
     * Runs [decide] on the current state and appends its events.
     *
     * With an [expectedVersion], the command fails with [WrongExpectedVersion] unless the stream
     * is still at that version, which is how a client's `If-Match` reaches the store.
     * Without one, the loaded version is expected, so a concurrent change still fails the append.
     */
    suspend fun handle(id: UUID, expectedVersion: Long? = null, decide: (S) -> List<E>): Versioned<S> {
        val current = find(id)
        val loadedVersion = current?.version ?: NO_STREAM
        if (expectedVersion != null && expectedVersion != loadedVersion) {
            throw WrongExpectedVersion(id, expectedVersion, loadedVersion)
        }

        val state = current?.state ?: initial()
        val events = decide(state)
        if (events.isEmpty()) return Versioned(state, loadedVersion)

        val newVersion = eventStore.appendEvents(streamType, id, events, loadedVersion)
        return Versioned(events.fold(state, evolve), newVersion)
    }
}
