# Backend

Java 25 / Spring Boot server for the MTG tabletop. It owns room state,
format-specific validation, guest permissions, deck imports, and persistence.
REST handles session/room/deck operations; WebSocket commands and filtered
snapshots keep players synchronized.

See the [project README](../README.md) for game features and complete deployment
setup, and the [frontend README](../frontend/README.md) for the browser client.
**Run all commands below from the repository root.**

## Requirements

- Java 25 and Maven 3.9+.
- PostgreSQL for a running app. The Compose database uses PostgreSQL 17.
- Node 24 and installed frontend dependencies when building the complete app.

Tests launch their own embedded PostgreSQL and local HTTP fixtures. They do not
need the development database, but must run as a non-root user able to bind
local ports.

## Run for development

For a Compose-managed database, copy `.env.example` to `.env` on first setup,
choose `DATABASE_PASSWORD`, and run:

```sh
docker compose up -d db
```

Alternatively, provide a local PostgreSQL database and user named `mtgcmd`, or
configure the connection variables below. Flyway applies migrations at startup.

Install Java dependencies and start the server with the database's password:

```sh
mvn -f backend/pom.xml dependency:resolve
DATABASE_PASSWORD='your-password' PUBLIC_ORIGIN=http://127.0.0.1:5173 \
  mvn -f backend/pom.xml spring-boot:run
```

The backend listens at `http://127.0.0.1:8080`. Start the
[frontend dev server](../frontend/README.md#run-for-development) separately and
use its URL to interact with the app.

Compose reads `.env`; local Maven/Java commands do not automatically load it.
Pass the same database password explicitly or export it in the shell.

## Configuration

Defaults are defined in [application.properties](src/main/resources/application.properties).

| Variable            | Default                                   | Purpose                                                |
| ------------------- | ----------------------------------------- | ------------------------------------------------------ |
| `DATABASE_URL`      | `jdbc:postgresql://127.0.0.1:5432/mtgcmd` | JDBC connection URL                                    |
| `DATABASE_USER`     | `mtgcmd`                                  | Database user                                          |
| `DATABASE_PASSWORD` | `mtgcmd`                                  | Database password; set to match your database          |
| `BIND_ADDRESS`      | `127.0.0.1`                               | Server bind address                                    |
| `PORT`              | `8080`                                    | HTTP/WebSocket port                                    |
| `PUBLIC_ORIGIN`     | `http://127.0.0.1:5173`                   | Exact browser origin allowed for writes and WebSockets |
| `SECURE_COOKIE`     | `false`                                   | Whether the guest cookie requires HTTPS                |

For the packaged app served on port 8080, set
`PUBLIC_ORIGIN=http://127.0.0.1:8080`. For public HTTPS, use your exact HTTPS
origin and enable `SECURE_COOKIE`. Changing the backend port also requires
setting the frontend's `API_PORT` during development.

## Build and package

The canonical full-project build generates frontend assets first:

```sh
./scripts/build.sh
```

It runs the frontend build, then Maven `clean verify`, and reports the jar and
PMD findings-log paths. `./scripts/build.sh --skip-tests` uses
`clean package -DskipTests`; PMD still runs. Neither mode starts the app or
runs frontend unit/browser tests.

For the equivalent underlying build commands:

```sh
npm --prefix frontend run build
mvn -f backend/pom.xml clean verify
```

Maven embeds `frontend/dist` in the jar. Building the backend alone does not
produce fresh React assets. Run the packaged app against PostgreSQL with:

```sh
DATABASE_PASSWORD='your-password' PUBLIC_ORIGIN=http://127.0.0.1:8080 \
  java -jar backend/target/tabletop-0.1.0.jar
```

## Tests and formatting

```sh
mvn -f backend/pom.xml test
mvn -f backend/pom.xml -Dtest=GameEngineTest test
mvn -f backend/pom.xml spotless:check
mvn -f backend/pom.xml spotless:apply
```

The suite covers imports and multi-face matching, legality observations, game
commands and privacy, transactional persistence, revisions/idempotency,
concurrency, session/origin/CSRF handling, and service diagnostics.
`spotless:apply` rewrites Java formatting; `spotless:check` only checks it.
Maven `verify` runs tests, PMD reporting, JaCoCo reporting, and packaging, but the Java formatting
check is a separate goal unless explicitly included.

For build-wrapper, real PMD, and JaCoCo fixture tests (Node 24 required):

```sh
node --test --test-isolation=none scripts/build.test.mjs
node --test --test-isolation=none scripts/pmd.test.mjs
node --test --test-isolation=none scripts/jacoco.test.mjs
```

To smoke-test the packaged app with temporary PostgreSQL after building:

```sh
mvn -f backend/pom.xml exec:java -Dexec.mainClass=dev.mtgcmd.testsupport.JarSmoke -Dexec.classpathScope=test -Dexec.args=backend/target/tabletop-0.1.0.jar
```

Add `--live-import` inside the `-Dexec.args` value to include live Scryfall
lookups. The normal smoke check uses no live catalog calls. Test helpers and
browser-test endpoints are not included in the production jar. `MAVEN_OPTS`
can configure Maven settings such as a custom dependency cache.

## Code coverage

JaCoCo 0.8.15 measures production code exercised by the existing Surefire unit and
integration tests. Coverage is reporting-only: there is no minimum percentage gate
or blanket production exclusion. Test failures and coverage/report execution errors
still fail Maven. The agent is test-only and is not packaged in the application.

For a fresh full-suite report:

```sh
mvn -f backend/pom.xml clean verify
```

| Output         | Path from the repository root           |
| -------------- | --------------------------------------- |
| HTML report    | `backend/target/site/jacoco/index.html` |
| XML report     | `backend/target/site/jacoco/jacoco.xml` |
| CSV report     | `backend/target/site/jacoco/jacoco.csv` |
| Execution data | `backend/target/jacoco.exec`            |

Open the HTML index in a browser to inspect packages, classes, lines, and branches.
CI retains the report directory and execution data with the existing verification
artifacts. Reports are ignored build output, and `clean` removes them. The Docker
image build skips tests and therefore produces no fresh coverage. Its runtime
stage copies only the packaged jar, without an agent or coverage reports.

`mvn -f backend/pom.xml test` collects execution data but stops before report
creation. After a successful test run, `mvn -f backend/pom.xml jacoco:report` renders
its existing data without rerunning tests. It does not validate freshness or test
success. For a focused measurement, use `clean verify -Dtest=GameEngineTest`; that
report measures only the selected tests, not the full suite.

Clean skipped-test builds (`clean verify -DskipTests`, `clean verify
-Dmaven.test.skip=true`, or the wrapper's `--skip-tests` mode) create no coverage.
Non-clean runs can retain old reports and append/reuse old execution data, even
when tests are skipped or instrumentation is disabled. An existing report is not
proof that the current sources/tests were measured. Use `clean verify` before
claiming fresh full-suite coverage; a failed test run is incomplete even if it
left execution data behind.

To disable instrumentation and report generation explicitly:

```sh
mvn -f backend/pom.xml clean verify -Djacoco.skip=true
```

Surefire retains `--enable-native-access=ALL-UNNAMED` and accepts additional JVM
options through `-DargLine=...`. A separate late-evaluated `jacocoArgLine` property
supplies the agent; disabling coverage does not leave an unresolved JVM token.
Do not override that internal agent property. Test forking must remain enabled;
JaCoCo's agent cannot measure tests run inside Maven with `forkCount=0`.

Coverage excludes separately launched Maven-exec browser servers and packaged
smoke-test JVMs; it does not combine their execution. JaCoCo's native bytecode
filters apply, so coverage counters do not necessarily map one-to-one to source
conditions. Coverage measures execution, not assertion quality or correctness.

Initial observed baseline (2026-10-17, Java 25 / JaCoCo 0.8.15, clean full
Surefire run with 571 tests):

| Counter      | Covered / total | Coverage |
| ------------ | --------------- | -------- |
| Lines        | 1,546 / 1,591   | 97.17%   |
| Branches     | 589 / 682       | 86.36%   |
| Instructions | 7,809 / 8,045   | 97.07%   |

These are historical observations, not required thresholds. Regenerate a fresh
report to evaluate later changes.

See the [coverage specification](../spec/backend-code-coverage.md) and
[ADR-0018](../docs/adr/0018-backend-code-coverage.md).

## Mutation testing

PIT 1.30.1 with its JUnit 5 adapter 1.2.3 tests whether assertions detect deliberate
changes to production bytecode. It complements JaCoCo execution coverage. Mutation
scores are reporting-only, with no minimum gate; baseline test failures, empty
mutation targets, and tool/report failures still fail the command.

Mutation testing is opt-in. A normal Maven test/package/verify, the Docker build,
and `scripts/build.sh` do not run PIT. Activate the profile and invoke its goal
explicitly for a fresh full-backend analysis:

```sh
mvn -f backend/pom.xml -Pmutation clean test-compile pitest:mutationCoverage
```

Profile activation alone does not run PIT. The profile disables JaCoCo to keep
agents independent; use ordinary `clean verify` separately for fresh coverage.
PIT supplies its own worker JVM options instead of inheriting Surefire's argLine.
The default is one worker, native access enabled, all `dev.mtgcmd.*` production
classes/tests, and native DEFAULTS mutators, without additional class exclusions.
Full runs repeat many tests and may take substantially longer than normal builds.
Tests with PostgreSQL, HTTP fixtures, and resource cleanup still run as a non-root
user with local process and socket permissions.

For a focused deck-rules analysis:

```sh
mvn -f backend/pom.xml -Pmutation clean test-compile pitest:mutationCoverage \
  '-Dpit.targetClasses=dev.mtgcmd.game.DeckRules*' \
  '-Dpit.targetTests=dev.mtgcmd.game.DeckRulesTest'
```

Quote globs to avoid shell expansion. A trailing `*` includes nested classes;
an exact class glob does not. Focused tests/classes change the measurement scope
and must not be described as a whole-backend result. `-Dtest=...` controls Surefire,
not PIT; use `pit.targetTests`. Local worker count is configurable through
`-Dpit.threads=2`; start with one for resource-heavy tests.

To replace worker JVM options, use a comma-separated property, retaining native
access and supplying every option required by the chosen tests:

```sh
mvn -f backend/pom.xml -Pmutation clean test-compile pitest:mutationCoverage \
  '-Dpit.jvmArgs=--enable-native-access=ALL-UNNAMED,-Xmx1g'
```

The profile deliberately does not copy Surefire's `argLine` or JaCoCo agent into
PIT workers. Do not enable JaCoCo in a mutation run or pass its agent explicitly.
The profile changes no production dependencies and adds no runtime instrumentation.

| Report       | Path from the repository root              |
| ------------ | ------------------------------------------ |
| HTML         | `backend/target/pit-reports/index.html`    |
| Mutation XML | `backend/target/pit-reports/mutations.xml` |

Open the HTML report to investigate surviving mutations and their covering tests.
Killed mutations show that a test detected a change; surviving mutations expose
potential assertion gaps or equivalent behavior. NO_COVERAGE means no selected
test exercised the mutant. Timeouts, memory/worker errors, and equivalent mutants
need separate review; do not describe every detected result as an assertion kill
or silently exclude code to improve the score. This integration does not rewrite
tests to raise mutation scores.

Reports are ignored output. Use `clean` before a fresh analysis; failed/non-clean
runs can leave stale or partial reports. A successful goal, source revision,
target/test globs, mutator set, and execution status are necessary context for a
baseline. Enabling incremental history or narrowing tests changes that context.
A report file alone is not evidence that the current tests completed successfully.
Maven-exec browser servers and packaged smoke-test JVMs are not PIT test workers.

Initial full-backend baseline (2026-10-17, Java 25, the default globs/mutators,
one worker): 818 mutations across 51 mutated classes; 648 KILLED, 154 SURVIVED,
15 NO_COVERAGE, and 1 TIMED_OUT. Maven succeeded in 14 minutes 22 seconds.
PIT's reported 79% detection includes the timeout; it is not 649 assertion kills.
No worker execution or memory errors were recorded. These are historical results,
not gates; see the [specification](../spec/backend-mutation-testing.md) for scope
and verification details.

[Backend mutation testing](../.github/workflows/mutation.yml) is a separate manual
GitHub Actions workflow, with optional class/test scope selection, one worker,
a 60-minute timeout, and HTML/XML/log retention for seven days even on failure.
It has no PR/push/schedule trigger and does not publish releases or deploy. GitHub
makes manual dispatch available after the workflow is integrated into the default
branch; dispatch still requires explicit authorization. Review the selected ref
and target globs when comparing CI artifacts.

Ordinary CI and the full local verification profile run small Maven fixtures to
check the PIT integration. They do not run a whole-project mutation campaign:

```sh
node --test --test-isolation=none scripts/pit.test.mjs
```

See the [mutation specification](../spec/backend-mutation-testing.md) and
[ADR-0019](../docs/adr/0019-backend-mutation-testing.md).

## PMD reports

[The ruleset](pmd-ruleset.xml) enables broad analysis of production and test
sources. Maven runs PMD before packaging, including the Docker backend build.
**Rule violations are advisory and do not fail the build.** Analysis execution
and report-writing failures still fail the build.

[ADR-0008](../docs/adr/0008-java-source-conventions.md) records the Java conventions:
final bindings where possible, explicit types, clear names, braces, named
constants, and literal-first comparisons that preserve non-null invariants.
Documentation focuses on contracts and decisions. Blanket declaration/visibility
comment requirements are excluded; comment width matches the formatter at 100
columns. Duplicate-literal analysis excludes annotation tokens so compiler and
analysis annotations remain recognizable. Persisted short field names retain
their existing wire and snapshot contracts.

| Report                      | Path from the repository root     |
| --------------------------- | --------------------------------- |
| Human-readable findings log | `backend/target/pmd.txt`          |
| Machine-readable report     | `backend/target/pmd.xml`          |
| HTML report                 | `backend/target/reports/pmd.html` |

Reports are replaced on each run and removed by `clean`. An empty findings log
is valid. PMD reports and build dependencies are not bundled in the application
jar; Docker reports remain in its backend build stage.

Generate reports without packaging or executing unit tests:

```sh
mvn -f backend/pom.xml test-compile pmd:pmd
```

See the [static-analysis specification](../spec/java-static-analysis.md) and
[advisory-analysis ADR](../docs/adr/0003-advisory-java-static-analysis.md).

Summarize an existing XML report without rebuilding:

```sh
python3 scripts/analyze-pmd.py
```

Use `--json new-summary.json` to save a baseline and `--baseline previous-summary.json`
to compare counts. Optional `--markdown new-analysis.md` saves the human-readable
summary. Output files must be new; reports and baselines in `backend/target` are
removed by `clean`. Exit 2 indicates incomplete PMD analysis, while exit 1 indicates
an input/output error. This optional tool requires Python 3 and uses only its
standard library. See the [review skill](../skills/pmd-report-analysis/SKILL.md).

## State, API, and diagnostics

Rooms are persisted as authoritative PostgreSQL snapshots. Mutations use
transactional locking, revision checks, and command IDs. Players receive views
filtered for their seat; hidden hands and library identities are not sent to
unauthorized clients. Guest identity uses an HttpOnly cookie, and writes require
origin and CSRF checks.

Moxfield and pasted-export adapters feed the catalog, which resolves names
through Scryfall and caches definitions. Standard refresh can retain cached
cards when a provider fails; unresolved cards still require a successful lookup.

`CardCatalog` coordinates freshness, batch recovery, and ordered results.
`JdbcCardCache` owns SQL and stored-definition serialization;
`ScryfallCardSource` owns provider requests, definition mapping, and verified
aliases. See the [catalog decomposition specification](../spec/catalog-decomposition.md)
and [component boundary ADR](../docs/adr/0004-catalog-component-boundaries.md).

`GameEngine` validates and dispatches commands, then advances revisions and logs.
Grouped action handlers share `GameAccess` and `CardZoneTransitions`;
`GameViewMapper` builds seat-filtered snapshots. `RoomStore` retains transaction
ownership. See the [engine decomposition specification](../spec/game-engine-decomposition.md)
and [engine boundary ADR](../docs/adr/0005-game-engine-component-boundaries.md).

HTTP routes are grouped in `SessionController`, `RoomController`, and
`DeckController`. `DeckImportService` coordinates deck operations;
`DeckDraftRepository` owns guest-scoped draft SQL/JSON. `ApiExceptionHandler`
applies the existing errors only to those controllers and retains the historical
HTTP logger category. See the [API decomposition specification](../spec/api-controller-decomposition.md)
and [API boundary ADR](../docs/adr/0007-api-component-boundaries.md).

Spring owns the shared card-service `HttpClient` and closes it when the application
context shuts down. Injected `JsonHttp` adapters borrow that client. Standalone
`new JsonHttp()` instances own their internal client and should be used with
try-with-resources; closing an adapter constructed with an external client leaves
that client usable. See the [resource ownership specification](../spec/resource-ownership.md)
and [lifecycle ADR](../docs/adr/0006-http-client-resource-ownership.md).

Service failures distinguish timeout, unavailability, invalid response, and
interruption. Adapter message translation retains the subtype and original
cause. HTTP/WebSocket handlers emit WARN for service failures and ERROR for
unexpected failures; ordinary input validation does not create failure logs.
Recovered catalog refresh failures are logged where recovery happens.
Interruptions propagate, and internal stack traces stay in server logs.

Read the [API reference](../docs/API.md),
[state ownership ADR](../docs/adr/0001-authoritative-game-state.md),
[format profile ADR](../docs/adr/0002-room-format-profiles.md), and
[diagnostics specification](../spec/service-failure-diagnostics.md).

## Code map

| Package/location                                  | Responsibility                                                                       |
| ------------------------------------------------- | ------------------------------------------------------------------------------------ |
| [game/](src/main/java/dev/mtgcmd/game/)           | Table formats, deck rules, physical actions, and filtered views                      |
| [catalog/](src/main/java/dev/mtgcmd/catalog/)     | Deck parsers, Moxfield adapter, Scryfall cache, bounded HTTP, and service exceptions |
| [store/](src/main/java/dev/mtgcmd/store/)         | Room snapshots and transactional command application                                 |
| [web/](src/main/java/dev/mtgcmd/web/)             | REST, WebSocket, guest sessions, and request security                                |
| [db/migration/](src/main/resources/db/migration/) | Flyway schema migrations                                                             |
| [src/test/](src/test/)                            | Unit/integration tests and test-only server/smoke helpers                            |
