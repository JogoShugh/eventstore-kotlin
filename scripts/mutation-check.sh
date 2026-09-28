#!/usr/bin/env bash
# Plants known bugs in a throwaway copy of this repository, one at a time, and runs the tests after each.
# Every bug must make at least one test fail; a bug that "survives" shows a gap in the scenarios.
# The working tree is never modified. Needs Docker (Testcontainers) like the normal test run.
#
# Usage: scripts/mutation-check.sh [output-file]
# Results are printed and written as Markdown to output-file,
# default build/reports/mutation/mutation-results.md.
set -uo pipefail

source_dir="$(cd "$(dirname "$0")/.." && pwd)"
work_dir="$(mktemp -d)/eventstore"
output="${1:-$source_dir/build/reports/mutation/mutation-results.md}"
mkdir -p "$(dirname "$output")"
rsync -a --exclude build --exclude .gradle --exclude .git --exclude .claude --exclude scripts "$source_dir/" "$work_dir/"
cd "$work_dir" || exit 1

k=src/main/kotlin/org/starbornag/eventstore
sql=src/main/resources/org/starbornag/eventstore/schema.sql

# label | file | text to find | replacement
mutations=(
  "SQL ignores the expected version|$sql|IF expected_stream_version IS NOT NULL AND stream_version != expected_stream_version THEN|IF false THEN"
  "Batch reuses one expected version for every event|$k/PgEventStore.kt|val expectedForEvent = expectedVersion?.plus(index)|val expectedForEvent = expectedVersion"
  "Failed transaction is committed instead of rolled back|$k/Sql.kt|session.connection.rollbackTransaction()|session.connection.commitTransaction()"
  "Time travel uses >= instead of <=|$k/PgEventStore.kt|append(\" AND created <= |append(\" AND created >= "
  "Version filter off by one (< instead of <=)|$k/PgEventStore.kt|append(\" AND version <= |append(\" AND version < "
  "Events read newest first|$k/PgEventStore.kt|append(\" ORDER BY version\")|append(\" ORDER BY version DESC\")"
  "Repository ignores the caller's expected version|$k/Repository.kt|if (expectedVersion != null && expectedVersion != loadedVersion) {|if (false) {"
  "Snapshot never written|$k/Repository.kt|snapshot?.handle(session, newState, version)|Unit"
  "Projections never run|$k/PgEventStore.kt|.forEach { it.handle(session, event) }|.forEach { }"
  "New stream starts at version 0 instead of -1|$sql|stream_version := -1;|stream_version := 0;"
  "Concurrent stream creation not mapped to WrongExpectedVersion|$k/PgEventStore.kt|} catch (e: R2dbcDataIntegrityViolationException) {|} catch (e: IllegalStateException) {"
)

echo "| # | Planted bug | Change | Result | Failing tests |" | tee "$output"
echo "|---|---|---|---|---|" | tee -a "$output"
survivors=0
n=0
for mutation in "${mutations[@]}"; do
  n=$((n + 1))
  IFS='|' read -r label file find replace <<<"$mutation"
  cp "$file" "$file.orig"
  if ! python3 - "$file" "$find" "$replace" <<'PY'
import sys
path, old, new = sys.argv[1:]
text = open(path).read()
if old not in text:
    sys.exit(1)
open(path, "w").write(text.replace(old, new, 1))
PY
  then
    mv "$file.orig" "$file"
    echo "| $n | $label | \`$file\` | NOT APPLIED: text not found | |" | tee -a "$output"
    survivors=$((survivors + 1))
    continue
  fi
  result=$(./gradlew test --console=plain 2>&1)
  mv "$file.orig" "$file"
  failing=$(echo "$result" | grep -E ' FAILED$' | grep -v '^> Task' | sed -E 's/ FAILED$//' | sed -E 's/^RunCucumberTest > //' | paste -sd ';' - | sed 's/;/<br>/g')
  change="\`$(basename "$file")\`: \`$find\` → \`$replace\`"
  if echo "$result" | grep -q 'BUILD SUCCESSFUL'; then
    echo "| $n | $label | $change | **SURVIVED** | |" | tee -a "$output"
    survivors=$((survivors + 1))
  else
    count=$(echo "$result" | grep -E ' FAILED$' | grep -vc '^> Task')
    echo "| $n | $label | $change | caught ($count) | $failing |" | tee -a "$output"
  fi
done

rm -rf "$(dirname "$work_dir")"
echo
echo "$survivors of $n planted bugs survived." | tee -a "$output"
echo "Results: $output"
exit $((survivors > 0))
