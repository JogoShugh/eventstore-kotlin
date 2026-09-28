package org.starbornag.eventstore.tools

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.spi.ConnectionFactory
import org.starbornag.eventstore.withSession
import org.testcontainers.containers.PostgreSQLContainer

/**
 * One PostgreSQL container for the whole test run. Each test or scenario works in its own
 * fresh schema, and its connection factory's search_path points at that schema.
 */
object PostgresDatabase {
    private val container: PostgreSQLContainer<*> =
        PostgreSQLContainer("postgres:17-alpine").apply { start() }

    suspend fun freshSchema(schemaName: String): ConnectionFactory {
        val connectionFactory = connectionFactory(schemaName)
        connectionFactory.withSession { session ->
            session.execute("DROP SCHEMA IF EXISTS $schemaName CASCADE; CREATE SCHEMA $schemaName;")
        }
        return connectionFactory
    }

    private fun connectionFactory(schemaName: String): ConnectionFactory =
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
}
