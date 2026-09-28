package org.starbornag.eventstore

import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Maps event classes to the type names stored in the `events.type` column.
 *
 * Registered names win. Otherwise the JVM class name is used, so renaming or moving
 * an unregistered event class breaks reading its old events; register stable names
 * for anything long-lived.
 */
class EventTypeMapper {
    private val typeToName = ConcurrentHashMap<KClass<*>, String>()
    private val nameToType = ConcurrentHashMap<String, KClass<*>>()

    fun register(type: KClass<*>, name: String): EventTypeMapper {
        typeToName[type] = name
        nameToType[name] = type
        return this
    }

    inline fun <reified T : Any> register(name: String) = register(T::class, name)

    fun toName(type: KClass<*>): String =
        typeToName.getOrPut(type) { type.java.name }

    fun toType(name: String): KClass<*>? =
        nameToType[name] ?: runCatching { Class.forName(name).kotlin }
            .getOrNull()
            ?.also { nameToType[name] = it }
}
