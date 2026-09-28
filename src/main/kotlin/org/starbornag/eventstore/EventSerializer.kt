package org.starbornag.eventstore

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlin.reflect.KClass

interface EventSerializer {
    fun serialize(event: Any): String
    fun deserialize(json: String, type: KClass<*>): Any
}

class JacksonEventSerializer(
    private val mapper: ObjectMapper = defaultMapper()
) : EventSerializer {
    companion object {
        fun defaultMapper(): ObjectMapper =
            jacksonObjectMapper()
                .registerModule(JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    }

    override fun serialize(event: Any): String = mapper.writeValueAsString(event)

    override fun deserialize(json: String, type: KClass<*>): Any = mapper.readValue(json, type.java)
}
