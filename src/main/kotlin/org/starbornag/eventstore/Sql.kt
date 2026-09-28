package org.starbornag.eventstore

import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.Row
import io.r2dbc.spi.Statement
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.withContext

/** A typed SQL NULL. R2DBC needs the parameter type to bind a null. */
data class SqlNull(val type: Class<*>)

inline fun <reified T : Any> sqlNull() = SqlNull(T::class.javaObjectType)

/** Thin coroutine wrapper over one R2DBC connection. Parameters bind positionally to `$1`, `$2`, … */
class SqlSession(val connection: Connection) {

    /**
     * Runs a statement and returns the number of rows updated.
     * Without parameters, several statements may be sent at once.
     */
    suspend fun execute(sql: String, vararg params: Any?): Long = execute(sql, params.asList())

    suspend fun execute(sql: String, params: List<Any?>): Long {
        var rowsUpdated = 0L
        statement(sql, params).execute().asFlow().collect { result ->
            result.rowsUpdated.asFlow().collect { rowsUpdated += it }
        }
        return rowsUpdated
    }

    suspend fun <T> query(sql: String, vararg params: Any?, mapper: (Row) -> T): List<T> =
        query(sql, params.asList(), mapper)

    suspend fun <T> query(sql: String, params: List<Any?>, mapper: (Row) -> T): List<T> {
        val rows = mutableListOf<T>()
        statement(sql, params).execute().asFlow().collect { result ->
            // Reactive streams cannot emit null, so wrap each mapped value.
            result.map { row, _ -> listOf(mapper(row)) }.asFlow().collect { rows += it }
        }
        return rows
    }

    suspend fun <T> querySingleOrNull(sql: String, vararg params: Any?, mapper: (Row) -> T): T? =
        query(sql, params.asList(), mapper).singleOrNull()

    private fun statement(sql: String, params: List<Any?>): Statement {
        val statement = connection.createStatement(sql)
        params.forEachIndexed { index, param ->
            when (param) {
                null -> throw IllegalArgumentException(
                    "Parameter \$${index + 1} is null; bind SqlNull to give its type"
                )
                is SqlNull -> statement.bindNull(index, param.type)
                else -> statement.bind(index, param)
            }
        }
        return statement
    }
}

inline fun <reified T : Any> Row.value(name: String): T? = get(name, T::class.javaObjectType)

suspend fun <T> ConnectionFactory.withSession(block: suspend (SqlSession) -> T): T {
    val connection = create().awaitSingle()
    try {
        return block(SqlSession(connection))
    } finally {
        withContext(NonCancellable) { connection.close().awaitFirstOrNull() }
    }
}

/** Runs [block] in one transaction: commits when it returns, rolls back when it throws. */
suspend fun <T> ConnectionFactory.inTransaction(block: suspend (SqlSession) -> T): T =
    withSession { session ->
        session.connection.beginTransaction().awaitFirstOrNull()
        val result = try {
            block(session)
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            // Any failure, including cancellation, must roll back; the exception is rethrown unchanged.
            withContext(NonCancellable) { session.connection.rollbackTransaction().awaitFirstOrNull() }
            throw e
        }
        session.connection.commitTransaction().awaitFirstOrNull()
        result
    }
