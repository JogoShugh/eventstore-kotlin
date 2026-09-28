# Proving the Kotlin event store works: mutation testing

_2026-09-28. Written while this code still lived in `eventstore/` of the StarbornAG repository._

## The question

All Cucumber scenarios pass against a real PostgreSQL 17
(Testcontainers, no mocks). But passing tests only prove something if they
would fail when the code is wrong. Most scenarios here first went red because
their steps did not exist yet, not because an assertion caught wrong
behaviour. So the question was: would these tests notice a real bug?

## The method

`scripts/mutation-check.sh` plants known bugs ("mutations") in a
throwaway copy of the repository, one at a time, and runs the full test suite
after each:

1. Copy the repository to a temporary directory. The working tree is never
   touched.
2. For each mutation, replace one exact piece of production code or SQL with
   a buggy version.
3. Run `./gradlew test`.
4. **Caught** means at least one test failed. **Survived** means every test
   still passed, which is a gap in the scenarios.
5. Restore the file and move on to the next mutation.

Each mutation is a bug a real change could plausibly introduce: a dropped
concurrency check, an off-by-one, a wrong sort order, a missing rollback.

## Run it yourself

```sh
scripts/mutation-check.sh            # writes build/reports/mutation/mutation-results.md
scripts/mutation-check.sh out.md     # or to a file of your choice
```

It needs Docker, takes about 4 minutes, and exits non-zero if any mutation
survives or can no longer be applied. When production code changes, update
the mutation list in the script so every entry still matches the code.

## What the first run found

The first run (before this document) caught all 11 bugs, but one of them only
weakly:

- **#3, commit instead of rollback, was caught by just 1 test**, and only
  indirectly through the concurrency scenario. The snapshot and projection
  rollback scenarios did not catch it. Their failures were SQL constraint
  errors, and after a SQL error PostgreSQL aborts the transaction itself, so
  even a `COMMIT` rolls back. Nothing tested a failure that happens in Kotlin
  code after the events are written, where only our explicit rollback
  protects the data.

The gap was closed Gherkin-first:

- `src/test/resources/features/05-snapshots/snapshots.feature` and `06-projections/projections.feature`
  gained a `Crash` example: the snapshot or projection throws a plain Kotlin
  error after the event is appended.
- The scenarios went red first (undefined steps), then green once their steps
  existed. No production change was needed: the rollback already worked; only
  the proof was missing.
- On the rerun below, #3 is caught by 3 tests, and the two new `Crash`
  examples are exactly the new ones.

A second, unrelated finding: in an early version of the script, mutation #7
was reported as not applied. The `&&` in its search text was escaped wrongly,
so the bug was never planted. The script now reports a mutation it cannot
apply as a failure, instead of silently skipping it.

## Results

Rerun after the rollback scenarios were added: **0 of 11 planted bugs survived.**

| # | Planted bug | Change | Result | Failing tests |
|---|---|---|---|---|
| 1 | SQL ignores the expected version | `schema.sql`: `IF expected_stream_version IS NOT NULL AND stream_version != expected_stream_version THEN` → `IF false THEN` | caught (4) | Appending events to a stream > The expected version decides whether an append is accepted > Examples > Example #1.6<br>Appending events to a stream > The expected version decides whether an append is accepted > Examples > Example #1.7<br>Appending events to a stream > The expected version decides whether an append is accepted > Examples > Example #1.8<br>Appending events to a stream > Only one of several concurrent writers wins > Examples > Example #1.2 |
| 2 | Batch reuses one expected version for every event | `PgEventStore.kt`: `val expectedForEvent = expectedVersion?.plus(index)` → `val expectedForEvent = expectedVersion` | caught (2) | Appending events to a stream > The expected version decides whether an append is accepted > Examples > Example #1.2<br>Appending events to a stream > The expected version decides whether an append is accepted > Examples > Example #1.4 |
| 3 | Failed transaction is committed instead of rolled back | `Sql.kt`: `session.connection.rollbackTransaction()` → `session.connection.commitTransaction()` | caught (3) | Appending events to a stream > Only one of several concurrent writers wins > Examples > Example #1.1<br>Snapshots of aggregate state > A failed snapshot write rolls back the events > Examples > Example #1.2<br>Inline projections into read models > A command that does not append leaves the dashboard unchanged > Examples > Example #1.3 |
| 4 | Time travel uses >= instead of <= | `PgEventStore.kt`: `append(" AND created <= ` → `append(" AND created >= ` | caught (1) | Aggregating a stream into state > Aggregating as of a point in time ignores later events |
| 5 | Version filter off by one (< instead of <=) | `PgEventStore.kt`: `append(" AND version <= ` → `append(" AND version < ` | caught (3) | Aggregating a stream into state > Aggregating up to a stream version > Examples > Example #1.1<br>Aggregating a stream into state > Aggregating up to a stream version > Examples > Example #1.2<br>Aggregating a stream into state > Aggregating up to a stream version > Examples > Example #1.3 |
| 6 | Events read newest first | `PgEventStore.kt`: `append(" ORDER BY version")` → `append(" ORDER BY version DESC")` | caught (13) | Reading streams > Events come back in append order with 0-based versions<br>Aggregating a stream into state > Aggregating up to a stream version > Examples > Example #1.2<br>Aggregating a stream into state > Aggregating up to a stream version > Examples > Example #1.3<br>Aggregating a stream into state > Aggregating up to a stream version > Examples > Example #1.4<br>Aggregating a stream into state > Aggregating as of a point in time ignores later events<br>Handling commands through a repository > A user is created, renamed and loaded back<br>Handling commands through a repository > A command may carry the version its sender last saw > Examples > Example #1.1<br>Handling commands through a repository > A command may carry the version its sender last saw > Examples > Example #1.2<br>Handling commands through a repository > A command may carry the version its sender last saw > Examples > Example #1.3<br>Handling commands through a repository > A command may carry the version its sender last saw > Examples > Example #1.4<br>Snapshots of aggregate state > The snapshot follows the stream > Examples > Example #1.2<br>Snapshots of aggregate state > A failed snapshot write rolls back the events > Examples > Example #1.1<br>Snapshots of aggregate state > A failed snapshot write rolls back the events > Examples > Example #1.2 |
| 7 | Repository ignores the caller's expected version | `Repository.kt`: `if (expectedVersion != null && expectedVersion != loadedVersion) {` → `if (false) {` | caught (3) | Handling commands through a repository > A command may carry the version its sender last saw > Examples > Example #1.4<br>Handling commands through a repository > A command may carry the version its sender last saw > Examples > Example #1.5<br>Inline projections into read models > A command that does not append leaves the dashboard unchanged > Examples > Example #1.1 |
| 8 | Snapshot never written | `Repository.kt`: `snapshot?.handle(session, newState, version)` → `Unit` | caught (5) | Snapshots of aggregate state > The snapshot follows the stream > Examples > Example #1.1<br>Snapshots of aggregate state > The snapshot follows the stream > Examples > Example #1.2<br>Snapshots of aggregate state > Snapshots can be queried like any table<br>Snapshots of aggregate state > A failed snapshot write rolls back the events > Examples > Example #1.1<br>Snapshots of aggregate state > A failed snapshot write rolls back the events > Examples > Example #1.2 |
| 9 | Projections never run | `PgEventStore.kt`: `.forEach { it.handle(session, event) }` → `.forEach { }` | caught (4) | Inline projections into read models > The dashboard follows a user and their orders across streams<br>Inline projections into read models > A command that does not append leaves the dashboard unchanged > Examples > Example #1.1<br>Inline projections into read models > A command that does not append leaves the dashboard unchanged > Examples > Example #1.2<br>Inline projections into read models > A command that does not append leaves the dashboard unchanged > Examples > Example #1.3 |
| 10 | New stream starts at version 0 instead of -1 | `schema.sql`: `stream_version := -1;` → `stream_version := 0;` | caught (32) | Appending events to a stream > Appending to a new stream creates the stream<br>Appending events to a stream > The expected version decides whether an append is accepted > Examples > Example #1.1<br>Appending events to a stream > The expected version decides whether an append is accepted > Examples > Example #1.2<br>Appending events to a stream > The expected version decides whether an append is accepted > Examples > Example #1.3<br>Appending events to a stream > The expected version decides whether an append is accepted > Examples > Example #1.4<br>Appending events to a stream > The expected version decides whether an append is accepted > Examples > Example #1.5<br>Appending events to a stream > The expected version decides whether an append is accepted > Examples > Example #1.6<br>Appending events to a stream > The expected version decides whether an append is accepted > Examples > Example #1.7<br>Appending events to a stream > The expected version decides whether an append is accepted > Examples > Example #1.8<br>Appending events to a stream > Only one of several concurrent writers wins > Examples > Example #1.1<br>Appending events to a stream > Only one of several concurrent writers wins > Examples > Example #1.2<br>Reading streams > Events come back in append order with 0-based versions<br>Reading streams > The stream state reports its type and last version<br>Aggregating a stream into state > Aggregating up to a stream version > Examples > Example #1.1<br>Aggregating a stream into state > Aggregating up to a stream version > Examples > Example #1.2<br>Aggregating a stream into state > Aggregating up to a stream version > Examples > Example #1.3<br>Handling commands through a repository > A user is created, renamed and loaded back<br>Handling commands through a repository > A command may carry the version its sender last saw > Examples > Example #1.1<br>Handling commands through a repository > A command may carry the version its sender last saw > Examples > Example #1.2<br>Handling commands through a repository > A command may carry the version its sender last saw > Examples > Example #1.3<br>Handling commands through a repository > A command may carry the version its sender last saw > Examples > Example #1.4<br>Handling commands through a repository > A command may carry the version its sender last saw > Examples > Example #1.5<br>Handling commands through a repository > A decision without events leaves the stream unchanged<br>Snapshots of aggregate state > The snapshot follows the stream > Examples > Example #1.1<br>Snapshots of aggregate state > The snapshot follows the stream > Examples > Example #1.2<br>Snapshots of aggregate state > Snapshots can be queried like any table<br>Snapshots of aggregate state > A failed snapshot write rolls back the events > Examples > Example #1.1<br>Snapshots of aggregate state > A failed snapshot write rolls back the events > Examples > Example #1.2<br>Inline projections into read models > The dashboard follows a user and their orders across streams<br>Inline projections into read models > A command that does not append leaves the dashboard unchanged > Examples > Example #1.1<br>Inline projections into read models > A command that does not append leaves the dashboard unchanged > Examples > Example #1.2<br>Inline projections into read models > A command that does not append leaves the dashboard unchanged > Examples > Example #1.3 |
| 11 | Concurrent stream creation not mapped to WrongExpectedVersion | `PgEventStore.kt`: `} catch (e: R2dbcDataIntegrityViolationException) {` → `} catch (e: IllegalStateException) {` | caught (1) | Appending events to a stream > Only one of several concurrent writers wins > Examples > Example #1.1 |


## What this proves

The scenarios check real behaviour: concurrency control, ordering, version
and time filters, snapshots, projections and transactional rollback all have
at least one test that fails when that behaviour breaks.

## What it does not prove

- **Use from an application or Spring.** Nothing outside the tests calls the library yet.
- **Connection pooling.** The tests open a new R2DBC connection per operation.
- **Long streams or load.** Every test stream has only a handful of events.
- **Renamed or moved event classes.** Stored events carry the JVM class name
  unless a stable name is registered with `EventTypeMapper`.
- **Schema evolution.** `init()` runs `CREATE ... IF NOT EXISTS`; there is no
  migration path yet.
- **Bugs outside the list.** Mutation testing shows the tests catch these 11
  bugs, not every possible bug. A tool such as PIT could generate mutations
  automatically; it does not understand the SQL in `schema.sql`, which is
  why this list is hand-written.
