package org.starbornag.eventstore

import kotlin.reflect.KClass
import kotlin.reflect.full.cast

/**
 * Inline read-model projection. [PgEventStore] runs it in the append transaction,
 * so the read model commits or rolls back together with the events.
 */
abstract class Projection {
    private val handlers = mutableMapOf<KClass<*>, suspend (SqlSession, Any) -> Unit>()

    fun <E : Any> projects(type: KClass<E>, handler: suspend (SqlSession, E) -> Unit) {
        handlers[type] = { session, event -> handler(session, type.cast(event)) }
    }

    inline fun <reified E : Any> projects(noinline handler: suspend (SqlSession, E) -> Unit) =
        projects(E::class, handler)

    fun handles(type: KClass<*>): Boolean = type in handlers

    suspend fun handle(session: SqlSession, event: Any) {
        handlers[event::class]?.invoke(session, event)
    }
}
