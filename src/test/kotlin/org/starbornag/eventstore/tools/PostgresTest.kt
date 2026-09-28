package org.starbornag.eventstore.tools

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.starbornag.eventstore.EventStore
import org.starbornag.eventstore.PgEventStore
import org.starbornag.eventstore.Projection
import org.starbornag.eventstore.SqlSession
import org.starbornag.eventstore.withSession
import org.testcontainers.containers.PostgreSQLContainer

/**
 * Base class for tests against a real PostgreSQL.
 *
 * One container is shared by the whole test run. Each test gets a fresh schema named
 * after its test class, and the connection factory's search_path points at it.
 */
abstract class PostgresTest {
    companion object {
        private val container: PostgreSQLContainer<*> =
            PostgreSQLContainer("postgres:17-alpine").apply { start() }
    }

    private val schemaName = this::class.simpleName!!.lowercase()

    protected val connectionFactory: ConnectionFactory =
        PostgresqlConnectionFactory(
            PostgresqlConnectionConfiguration.builder()
                .host(container.host)
                .port(container.firstMappedPort)
                .database(container.databaseName)
                .username(container.username)
                .password(container.password)
                .schema(schemaName)
                .build()
        )

    protected val schemaProvider = PostgresSchemaProvider(connectionFactory)

    protected open fun projections(): List<Projection> = emptyList()

    protected val eventStore: EventStore by lazy { PgEventStore(connectionFactory, projections()) }

    /** Extra tables a test needs, created after the event store schema. */
    protected open suspend fun migrate(session: SqlSession) {}

    @BeforeEach
    fun setUpSchema() = test {
        connectionFactory.withSession { session ->
            session.execute("DROP SCHEMA IF EXISTS $schemaName CASCADE; CREATE SCHEMA $schemaName;")
        }
        eventStore.init()
        connectionFactory.withSession { migrate(it) }
    }

    protected fun <T> sql(block: suspend (SqlSession) -> T): T =
        runBlocking { connectionFactory.withSession(block) }
}

/** Runs a suspending test body; JUnit needs test methods that return Unit. */
fun test(block: suspend CoroutineScope.() -> Unit) = runBlocking(block = block)
