package org.starbornag.eventstore.tools

import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.starbornag.eventstore.PgEventStore

/** Base class for JUnit tests against a real PostgreSQL, in a fresh schema named after the test class. */
abstract class PostgresTest {
    private val schemaName = this::class.simpleName!!.lowercase()

    protected lateinit var connectionFactory: ConnectionFactory
    protected lateinit var schemaProvider: PostgresSchemaProvider

    @BeforeEach
    fun setUpSchema() = test {
        connectionFactory = PostgresDatabase.freshSchema(schemaName)
        schemaProvider = PostgresSchemaProvider(connectionFactory)
        PgEventStore(connectionFactory).init()
    }
}

/** Runs a suspending test body; JUnit needs test methods that return Unit. */
fun test(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)
