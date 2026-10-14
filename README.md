# Commander Table

A private tabletop for four-player Commander and two-player Standard.
React/TypeScript supplies the desktop table; Java 25 owns state, permissions,
and saved games. Players use external
voice and adjudicate card text, priority, mana, damage, and rules themselves.

## Run locally

Requirements: Java 25, Maven 3.9+, Node 24, and PostgreSQL. Docker Compose is an
optional way to run the database and packaged app.

1. Copy `.env.example` to `.env` and choose `DATABASE_PASSWORD`.
2. Run `docker compose up -d db`, or provide an existing local PostgreSQL database
   named `mtgcmd` and user `mtgcmd`.
3. Install dependencies:

   ```sh
   npm --prefix frontend ci
   mvn -f backend/pom.xml dependency:resolve
   ```

4. Start the backend, using the same database password as the database:

   ```sh
   DATABASE_PASSWORD=your-password mvn -f backend/pom.xml spring-boot:run
   ```

5. In another terminal, run `npm --prefix frontend run dev` and open
   `http://127.0.0.1:5173`. Use that exact origin; `localhost` is a different
   origin. Create a table and send its URL to your friends.

Separate browser profiles are necessary to simulate different players on one
computer. Tabs in the same profile share the same guest seat. Guest credentials
live in an HttpOnly cookie; clearing cookies loses access to occupied seats.

## Packaged app and HTTPS

`docker compose up --build app` builds both components and serves them at
`http://127.0.0.1:8080`. PostgreSQL data lives in a named volume and survives app
restarts. No containers or public service are started by installing dependencies.

For remote friends, point a domain at your machine, make ports 80/443 reachable,
and set `TABLE_DOMAIN=table.example.com`, `PUBLIC_ORIGIN=https://table.example.com`,
and `SECURE_COOKIE=true` in `.env`. Start the optional proxy with
`docker compose --profile https up --build`. Caddy handles certificates and
WebSocket forwarding. The database and direct app ports bind only to loopback.
Public hosting is a separate operator action; this implementation has not been
published or deployed.

Without containers, build and run the executable jar:

```sh
./scripts/build.sh
PUBLIC_ORIGIN=http://127.0.0.1:8080 DATABASE_PASSWORD=your-password java -jar backend/target/tabletop-0.1.0.jar
```

The wrapper builds the frontend, requires its generated index, then runs Maven
`clean verify` to remove stale copied assets and run the Java tests before
reporting the jar's absolute path. It works from other working directories and
repository paths containing spaces. Use `./scripts/build.sh --help` for usage
or `./scripts/build.sh --skip-tests` for packaging with Java tests explicitly
skipped. Neither mode runs frontend unit tests, lint, or browser checks.
Dependencies must already be installed; the script does not start services or
deploy. A failed tool stops the build with its exit code. Missing frontend output
or missing/ambiguous jars also fail instead of reporting success.

For troubleshooting, the underlying commands are
`npm --prefix frontend run build` followed by
`mvn -f backend/pom.xml clean verify`. Maven embeds `frontend/dist` into the jar.
`DATABASE_URL` accepts a JDBC PostgreSQL URL; `DATABASE_USER`, `DATABASE_PASSWORD`,
`PORT`, `BIND_ADDRESS`, `PUBLIC_ORIGIN`, and `SECURE_COOKIE` configure the server.
Only the packaged app is exposed through the proxy; the Vite development server
stays local. Back up PostgreSQL before upgrading saved-game formats.

## Table formats

Choose the format when creating a table. Commander remains the default for old
requests and saved rooms; a table's format cannot change after creation.

| Format | Players | Starting life | Deck | Commanders |
| --- | --- | --- | --- | --- |
| Commander | 4 | 40 | 1–250 imported cards; 100-card size is a warning | One or two required |
| Standard | 2 | 20 | 60–250 main-deck cards required | None |

Standard supports single games. Sideboards are excluded from imports; there is
no match progression or automated mulligan/draw-step/win enforcement. The host
chooses who starts, defaulting to the host. Both players draw seven opening cards.

Standard legality and nonbasic four-copy limits are advisory warnings that must
be accepted before readiness. Basic lands and recognized any-number exceptions
are exempt; review other card-specific exceptions yourself. Standard previews
refresh missing or at-least-24-hour-old legality. If a provider is unavailable,
cached definitions remain usable with verification warnings; unknown uncached
cards still require resolution. Legality observations are not tournament certification.

## Playing

- Import a public Moxfield URL or paste a text export using quantity/name lines.
  `Commander`, `Deck`, and `Sideboard` section headers are supported; sideboard
  and considering/maybeboard cards are excluded. Printing annotations from
  common text exports are stripped.
- Preview the import, choose one or two commanders for Commander tables, load
  the deck, review warnings, and ready up. Standard has no commander selection.
  The host starts when every required seat is ready.
- Click a card to inspect it and access actions. Drag playable cards onto your
  battlefield or use their action menu. Permanents group automatically by primary
  type, with lands below other permanents; cards retain their order within each
  group. Battlefield cards use automatic placement rather than free positioning.
- Use zone viewers to search a library or inspect graveyard/exile/command cards.
  Library cards are numbered in order; move selections to the top or bottom to
  reorder them. Shuffle closes searches and removes temporary reveals.
- Use the shared stack for spells and ability/trigger markers. Resolving a spell
  means moving it to its destination; ability markers have a resolve/remove
  button. The server does not interpret their effects.
- Card menus support face-down play/exile, transformation, targeted reveals,
  token copies/edits, attachments, control changes, and combat markers.
- Player tools track named counters, poison, dice, and private-zone grants;
  Commander tables also track commander casts/tax and commander damage. A grant
  allows another player to inspect and manipulate your hand and search your library. Revoke it when
  the effect ends.
- Corrections are explicit logged actions. There is no automatic undo. A
  disconnect reserves the seat; reconnecting loads committed state. The host
  marks the game finished after players resolve the result.

## Imports and offline behavior

Scryfall card definitions and format legality observations are fetched in bounded
batches and cached in PostgreSQL. Loaded games continue without an external catalog connection;
images remain externally hosted and may be unavailable offline. Unknown names
require correction. Deck legality checks are warnings, not a complete rules
validator; commander pairing and eligibility require player review.

Live Scryfall single-card and collection requests returned HTTP 200 during
verification. The Moxfield endpoint returned HTTP 403. URL import reports an
actionable export/paste fallback; it does not bypass provider access controls.
Moxfield parsing has fixture tests for both root and board-based formats.

## Verification

```sh
node --test --test-isolation=none scripts/build.test.mjs
npm --prefix frontend test
npm --prefix frontend run typecheck
npm --prefix frontend run lint
npm --prefix frontend run format:check
mvn -f backend/pom.xml spotless:check
./scripts/build.sh
```

The wrapper's default build runs the Java suite once. To run that suite alone,
use `mvn -f backend/pom.xml test`. The build-wrapper tests use temporary
repositories and fake tools; they do not rebuild or clean the application.
Their coverage includes command ordering, arguments, paths with spaces,
failure/exit-code propagation, output, and artifact guards.

Backend integration tests launch actual embedded PostgreSQL and local HTTP
fixtures. Run them as a non-root user with permission to bind local ports.
For browser tests, install Chromium with `cd frontend && npx playwright install
chromium`, then run `npm --prefix frontend run test:e2e`. Alternatively set
`CHROME_PATH` to an installed Chrome executable. Browser tests launch isolated
Java/PostgreSQL and Vite servers on 8081/5173 and use a test-only synthetic card
catalog. Test fixtures and their restart endpoint are not included in the
production jar. `MAVEN_OPTS` may configure a custom Maven dependency cache.

The browser scenarios check four guests, deck import/readiness, hidden hands,
draw/play/reveal, life changes, refresh, an actual Spring server restart,
grant/revoke library access, face-down exile, and the pasted-export fallback.
Frontend unit tests check revision ordering and
command IDs. Backend tests cover gameplay/privacy, import parsing, persistence,
idempotency, concurrency, rollback, guest cookies, and CSRF/origin enforcement.

Docker image build and public HTTPS certificate issuance have not been tested
in this workspace, which has no Docker executable. Compose YAML has been parsed
successfully. See the final implementation report for executed checks.

Verified on 2026-10-13:

| Check | Result |
| --- | --- |
| Java unit/integration suite | 21 tests passed, including actual PostgreSQL and HTTP fixtures |
| Frontend unit suite | 2 tests passed |
| Four-player browser flow and import fallback | 2 scenarios passed in Chrome |
| TypeScript, ESLint, Prettier, Java formatting | Passed |
| React build and executable Java jar | Passed |
| Packaged-jar HTTP smoke check and live Scryfall import | Passed |
| Compose YAML syntax | Parsed successfully |
| Docker image execution / public HTTPS | Not run; Docker unavailable / no deployment authorized |

The packaged-jar smoke check starts temporary PostgreSQL, checks the embedded
React index/assets and room route, creates a guest room, and confirms test-only
endpoints are absent. Add `--live-import` to verify real Scryfall lookups:

```sh
mvn -f backend/pom.xml exec:java -Dexec.mainClass=dev.mtgcmd.testsupport.JarSmoke -Dexec.classpathScope=test '-Dexec.args=backend/target/tabletop-0.1.0.jar --live-import'
```

The live smoke check successfully previewed and loaded a 100-card deck with
Gisa and Geralf, Sol Ring, and Islands. It never writes to an operator's database.

## Project documentation

- [Specification](spec/commander-tabletop.md) and [specification index](spec/INDEX.md)
- [Build automation specification](spec/build-automation.md)
- [State ownership and recovery ADR](docs/adr/0001-authoritative-game-state.md)
- [API and command reference](docs/API.md)
- [Standard tables specification](spec/standard-tables.md)
- [Room format profiles decision](docs/adr/0002-room-format-profiles.md)
- [Repository rules and canonical commands](AGENTS.md)
