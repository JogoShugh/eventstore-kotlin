package org.starbornag.eventstore.tools

import io.r2dbc.spi.ConnectionFactory
import org.starbornag.eventstore.value
import org.starbornag.eventstore.withSession

/** Reads table and function metadata from the current schema. */
class PostgresSchemaProvider(private val connectionFactory: ConnectionFactory) {

    data class Column(val name: String, val type: String)

    data class Table(val name: String, val columns: List<Column>) {
        fun column(name: String): Column? = columns.find { it.name == name }
    }

    object ColumnType {
        const val UUID = "uuid"
        const val BIGINT = "bigint"
        const val TEXT = "text"
        const val JSONB = "jsonb"
        const val TIMESTAMP_WITH_TIME_ZONE = "timestamp with time zone"
    }

    suspend fun getTable(tableName: String): Table? {
        val columns = connectionFactory.withSession { session ->
            session.query(
                """
                SELECT column_name AS name, data_type AS type
                FROM information_schema.columns
                WHERE table_name = $1 AND table_schema = current_schema()
                """.trimIndent(),
                tableName
            ) { row -> Column(row.value<String>("name")!!, row.value<String>("type")!!) }
        }
        return if (columns.isEmpty()) null else Table(tableName, columns)
    }

    suspend fun functionExists(functionName: String): Boolean =
        connectionFactory.withSession { session ->
            session.querySingleOrNull(
                """
                SELECT exists(
                    SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
                    WHERE p.proname = $1 AND n.nspname = current_schema()
                ) AS exist
                """.trimIndent(),
                functionName
            ) { it.value<Boolean>("exist") } == true
        }
}
