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
Maven `verify` runs tests, PMD reporting, and packaging, but the Java formatting
check is a separate goal unless explicitly included.

For build-wrapper and real PMD fixture tests (Node 24 required):

```sh
node --test --test-isolation=none scripts/build.test.mjs
node --test --test-isolation=none scripts/pmd.test.mjs
```

To smoke-test the packaged app with temporary PostgreSQL after building:

```sh
mvn -f backend/pom.xml exec:java -Dexec.mainClass=dev.mtgcmd.testsupport.JarSmoke -Dexec.classpathScope=test -Dexec.args=backend/target/tabletop-0.1.0.jar
```

Add `--live-import` inside the `-Dexec.args` value to include live Scryfall
lookups. The normal smoke check uses no live catalog calls. Test helpers and
browser-test endpoints are not included in the production jar. `MAVEN_OPTS`
can configure Maven settings such as a custom dependency cache.

## PMD reports

[The ruleset](pmd-ruleset.xml) enables broad analysis of production and test
sources. Maven runs PMD before packaging, including the Docker backend build.
**Rule violations are advisory and do not fail the build.** Analysis execution
and report-writing failures still fail the build.

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

## State, API, and diagnostics

Rooms are persisted as authoritative PostgreSQL snapshots. Mutations use
transactional locking, revision checks, and command IDs. Players receive views
filtered for their seat; hidden hands and library identities are not sent to
unauthorized clients. Guest identity uses an HttpOnly cookie, and writes require
origin and CSRF checks.

Moxfield and pasted-export adapters feed the catalog, which resolves names
through Scryfall and caches definitions. Standard refresh can retain cached
cards when a provider fails; unresolved cards still require a successful lookup.

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
