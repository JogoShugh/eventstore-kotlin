# BDD quality gates

Ported on 2026-09-28 from `genai-service-gradle-plugin`, where they were built
and verified.

## Why

Instructions in `CLAUDE.md` are advisory: a model can drift past them under
context pressure. So every rule that matters is a real check with a real exit
code: a Gradle task, a JUnit test, or a hook.

## The pieces

| Piece | Where | What it enforces |
|---|---|---|
| Cucumber suite | `src/test/kotlin/**/cucumber/RunCucumberTest.kt`, features in `src/test/resources/features/NN-area/` | Behaviour is specified as table-driven Gherkin and executed |
| Detekt 1.23.8 | `build.gradle.kts` (`buildUponDefaultConfig`, no baseline) | Complexity and style, with Detekt's default thresholds |
| Konsist | `src/test/kotlin/**/architecture/ArchitectureBoundaryTest.kt` | Hexagonal boundaries: no Spring; the `EventStore` port imports no driver or JSON library; only `PgEventStore` uses the PostgreSQL driver; only `EventSerializer` uses Jackson |
| PreToolUse hook | `.claude/settings.json` | No edit to `src/main/kotlin/**/*.kt` while no `.feature` file has an uncommitted change |
| PostToolUse hooks | `.claude/settings.json` | Detekt after each edit; `MAIN_WRITE` / `FEATURE_WRITE` facts in `.claude/tdd-events.log` |
| `logCucumberRun` task | `build.gradle.kts`, `finalizedBy` on `test` | Appends `CUCUMBER_RUN PASS|FAIL` from Cucumber's JSON report. Undefined or pending steps count as FAIL |
| Stop hook | `.claude/settings.json` | `./gradlew check` must be green before a turn ends |
| pre-commit | `.githooks/pre-commit` | A commit touching `src/main/kotlin` needs, since the last commit: a `FEATURE_WRITE`, a `CUCUMBER_RUN FAIL` before the first `MAIN_WRITE`, and a final `CUCUMBER_RUN PASS` |

Activate the pre-commit hook once per clone:

```sh
git config core.hooksPath .githooks
```

## Differences from the genai version

- `logCucumberRun` reads `build/reports/cucumber/report.json`. Gradle 9 writes
  one JUnit XML file per feature, not one for the suite class.
- The Detekt PostToolUse hook runs only when a `.kt` or `.kts` file is edited.
- Gradle runs on Java 21 (`org.gradle.java.home`), because Detekt 1.23
  crashes inside a Java 25 daemon. Detekt 2.0 alphas need a newer Kotlin
  Gradle plugin than 2.0.21.

## Known limits

- Shell edits (`sed`, heredocs) bypass the PreToolUse gate and the event log.
  Edit production code with the Edit/Write tools only.
- Ordering is checked per commit, not per scenario.
- Steps e01–e06 were written as JUnit first and converted to Gherkin
  afterwards, so their scenarios never went red before the code existed. The
  ordering gate applies from e07 (repository) on.
- Hook commands run automatically for anyone who opens the repo in Claude
  Code. Keep them to local Gradle calls and review changes to them like CI
  changes.
