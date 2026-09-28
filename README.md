# eventstore-kotlin

An event store on PostgreSQL for Kotlin, using coroutines on R2DBC, with no
Spring dependency.

It is a Kotlin port of Oskar Dudycz's "Build Your Own Event Store" workshop:
steps e01–e06 from his
[JVM version](https://github.com/oskardudycz/EventSourcing.JVM/tree/build_your_own_event_store/workshops/build-your-own-event-store)
and e07–e09 from the
[.NET workshop](https://github.com/oskardudycz/EventSourcing.NetCore/tree/main/Workshops/BuildYourOwnEventStore).
As far as I found, no Kotlin port existed before.

## What it does

- **Streams and events** in two tables, `streams` and `events`, written only
  through the `append_event` PL/pgSQL function.
- **Optimistic concurrency.** `appendEvents(..., expectedVersion)` rejects a
  stale write with `WrongExpectedVersion`, which carries the stream's actual
  version. Stream versions are 0-based: a new stream starts at -1, so its
  first event is version 0. `EventStore.NO_STREAM` (-1) means "must not exist yet".
- **Atomic batches.** All events of one append land in one transaction, or none do.
- **Reading and aggregation.** `readStream`, `getEvents`, `getStreamState`, and
  `aggregateStream(initial, evolve)`, optionally as of a version or a point in time.
- **Command handling.** `Repository.handle(id, expectedVersion) { state -> events }`
  loads state, runs a decide function, and appends at the loaded version.
- **Snapshots and inline projections** run in the append transaction, so read
  models commit or roll back together with the events.

## Changes from the workshop

- The JVM version passed the same expected version for every event in a
  batch, so a batch of more than one event with a version check always
  failed. Here each event expects the previous one's version.
- Two writers creating the same new stream at once get `WrongExpectedVersion`
  instead of a raw unique-constraint error.
- Snapshots and projections run in the same transaction as the append. The
  .NET workshop runs them outside it.
- Functional style: `initial`, `evolve` and decide functions instead of a
  mutable aggregate base class and reflection.

## Usage

```kotlin
val eventStore = PgEventStore(connectionFactory)   // any R2DBC ConnectionFactory for PostgreSQL
eventStore.init()                                  // creates tables and append_event if missing

val users = Repository<User?, User.Event>(eventStore, User::class, { null }, User::evolve)
users.handle(userId, decide = User.create(userId, "John Doe"))
users.handle(userId, expectedVersion = 0, decide = User.rename("Adam Smith"))
val current = users.find(userId)                   // Versioned(state, version)
```

See `src/test/kotlin/users/` and `src/test/kotlin/bankaccounts/` for complete
example domains.

## Build and test

- Java 21. `gradle.properties` pins `org.gradle.java.home` to a local
  Java 21, because Detekt 1.23 cannot run inside a Java 25 Gradle daemon.
  Change the path for your machine.
- Docker, for Testcontainers (`postgres:17-alpine`).

```sh
./gradlew check                 # Cucumber scenarios, JUnit, Konsist, Detekt
scripts/mutation-check.sh       # plants 11 known bugs and checks the tests catch each one
```

## How it is tested

- Behaviour is specified as table-driven Gherkin in
  `src/test/resources/features/`, run against a real PostgreSQL.
- Konsist tests enforce the architecture: the `EventStore` port imports no
  driver or JSON library; only `PgEventStore` uses the PostgreSQL driver.
- Mutation testing shows the scenarios catch real bugs: see
  [docs/mutation-testing.md](docs/mutation-testing.md).
- Gherkin-first development is enforced by hooks and a pre-commit check: see
  [docs/bdd-quality-gates.md](docs/bdd-quality-gates.md).

## Not proven yet

Connection pooling, long streams under load, renamed event classes (stored
events carry the JVM class name unless a stable name is registered with
`EventTypeMapper`), and schema migrations. See the end of
[docs/mutation-testing.md](docs/mutation-testing.md).
