package org.starbornag.eventstore

/**
 * Writes an aggregate's new state after its events are appended, in the same transaction,
 * so a snapshot commits or rolls back together with the events it reflects.
 */
fun interface Snapshot<S> {
    suspend fun handle(session: SqlSession, state: S, version: Long)
}

/**
 * Upserts the state into a table. [params] turns the state and its version into the
 * statement's positional parameters (`$1`, `$2`, …).
 */
class SnapshotToTable<S>(
    private val upsertSql: String,
    private val params: (state: S, version: Long) -> List<Any?>
) : Snapshot<S> {
    override suspend fun handle(session: SqlSession, state: S, version: Long) {
        session.execute(upsertSql, params(state, version))
    }
}
