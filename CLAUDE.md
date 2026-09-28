# eventstore-kotlin

PostgreSQL event store for Kotlin (coroutines + R2DBC, no Spring). A port of
Oskar Dudycz's "Build Your Own Event Store" workshop. Extracted on 2026-09-28
from `eventstore/` in the StarbornAG repository (`~/repos/starbornes`), which
is its first consumer. See `README.md` for the API.

## Conventions

- Stream versions are 0-based: a new stream starts at -1, and its first event
  is version 0. `EventStore.NO_STREAM` is -1.
- Hexagonal: `EventStore` is the port, `PgEventStore` the adapter. Konsist
  tests in `src/test/kotlin/**/architecture/` enforce the boundaries.
- Functional aggregates: `initial`, `evolve` and decide functions. No mutable
  aggregate base class.

## Quality gates

Deterministic checks, not advice. Full design: `docs/bdd-quality-gates.md`.

- **Gherkin first.** Behaviour lives in table-driven `.feature` files under
  `src/test/resources/features/NN-area/`. Use Scenario Outlines and data
  tables, not repetitive Given/When/Then. Write or change the scenario, run it
  and see it fail, then change production code.
- **PreToolUse hook** blocks edits to `src/main/kotlin/**/*.kt` while no
  `.feature` file has an uncommitted change.
- **PostToolUse hooks** run Detekt after each Kotlin edit and append
  `MAIN_WRITE` / `FEATURE_WRITE` facts to `.claude/tdd-events.log` (local,
  gitignored).
- **Stop hook** runs `./gradlew check` and blocks the end of a turn until it
  is green.
- **`.githooks/pre-commit`** checks the event log for a commit that touches
  `src/main/kotlin`: a `FEATURE_WRITE`, a `CUCUMBER_RUN FAIL` before the first
  `MAIN_WRITE`, and a final `CUCUMBER_RUN PASS`.
  Activate once per clone with `git config core.hooksPath .githooks`.
- Edit production code with the Edit/Write tools, never with shell
  redirection or `sed`: shell edits bypass the gate and the event log.

## Build

- The Gradle daemon runs on Java 21 whatever your shell's Java is:
  `gradle/gradle-daemon-jvm.properties` asks for it, and the foojay resolver
  in `settings.gradle.kts` downloads one if none is installed. Detekt 1.23
  cannot run inside a Java 25 daemon. Never put a machine path
  (`org.gradle.java.home`) in the repository.
- Tests need Docker (Testcontainers starts `postgres:17-alpine`).
- `./gradlew check`
- `scripts/mutation-check.sh` plants 11 known bugs in a temp copy and checks
  the tests catch each one (about 4 minutes). Run it after changing production
  code, and keep its mutation list matching the code. See
  `docs/mutation-testing.md`.
