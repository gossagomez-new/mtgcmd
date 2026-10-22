# MTG Tabletop (mtgcmd)

A browser-based Magic: The Gathering tabletop for four-player Commander (EDH)
and two-player Standard. Each player joins from their own browser session;
a Java backend owns game state, permissions, and saved rooms.

This is an assisted tabletop. Players handle card rules, priority, mana,
combat damage, and game results themselves, and use an external voice/chat tool.

The frontend and backend share one application version under
[Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html). See the
[versioning policy](CONTRIBUTING.md#semantic-versioning) for compatibility rules,
initial-development (`0.x`) bumps, and release preparation.

## Component guides

- **[Contributing](CONTRIBUTING.md):** GitHub Flow, required checks, and tagged manual deployments.
- **[Backend](backend/README.md):** Java setup, PostgreSQL, configuration, API,
  tests, PMD reports, and service diagnostics.
- **[Frontend](frontend/README.md):** React/TypeScript setup, development proxy,
  UI code, unit/browser tests, and player controls.

## What you can do

- Create a Commander or Standard table and share its room URL.
- Import a public Moxfield deck URL or paste a text export, review warnings,
  and load a deck before becoming ready.
- Draw, shuffle, search libraries, and move cards between zones. Battlefields
  group permanents by type and place lands below other permanents.
- Tap, transform, reveal, attach, and change control of cards; create/edit
  tokens and track counters, combat markers, and stack objects.
- Grant or revoke another player's access to your private zones.
- Reconnect to your reserved seat and recover committed state from PostgreSQL.

| Format    | Players | Starting life | Imported deck                          | Commanders          |
| --------- | ------- | ------------- | -------------------------------------- | ------------------- |
| Commander | 4       | 40            | 1–250 cards; 100-card size is advisory | One or two required |
| Standard  | 2       | 20            | 60–250 main-deck cards                 | None                |

The table's format is fixed at creation. Standard supports single games,
without sideboards or match progression. Legality and copy-limit checks produce
warnings that players must review and accept; they are not a complete rules
validator. Commander eligibility and pairing require player review.

## Run the packaged app with Docker

Requirements: Docker and Docker Compose. The image builds the Java and Node
components, so local Java/Node installations are not needed for this mode.

On first setup, copy the example environment file:

```sh
cp .env.example .env
```

Edit `.env` and choose `DATABASE_PASSWORD`, then start the app and database:

```sh
docker compose up --build app
```

Open **http://127.0.0.1:8080**. PostgreSQL data is stored in a named volume and
survives container restarts. The app and database ports bind to loopback by
default; this setup is for local access.

## Develop locally

Requirements: **Java 25, Maven 3.9+, Node 24, and PostgreSQL**. Compose can supply
just PostgreSQL with `docker compose up -d db` after configuring `.env`.

From the repository root, install dependencies:

```sh
npm --prefix frontend ci
mvn -f backend/pom.xml dependency:resolve
```

Follow the [backend development setup](backend/README.md#run-for-development)
and the [frontend development setup](frontend/README.md#run-for-development)
in separate terminals. Open **http://127.0.0.1:5173**, the default allowed
browser origin. `localhost` and `127.0.0.1` are different origins.

To simulate multiple players on one computer, use separate browser profiles
or different browsers. Tabs in the same profile share a guest identity and
seat. Clearing the guest cookie loses access to seats associated with it.

## Build and run without Docker

After installing dependencies, build both components:

```sh
./scripts/build.sh
```

The script analyzes and builds React, cleans and verifies Java, and prints the executable
jar, frontend analysis summary, and PMD findings-log paths. New frontend analysis
findings are advisory; existing lint errors and analyzer failures block the build.
Use `./scripts/build.sh --skip-tests` to package
with Java tests skipped. PMD still analyzes main and test sources, and its rule
violations do not abort packaging. Frontend unit tests, lint, and browser tests
are separate checks described in the component guides. Frontend analysis report
details and commands are in the [frontend guide](frontend/README.md#static-analysis).

With PostgreSQL running, use its configured password to start the jar:

```sh
DATABASE_PASSWORD='your-password' PUBLIC_ORIGIN=http://127.0.0.1:8080 \
  java -jar backend/target/tabletop-0.1.0.jar
```

Open **http://127.0.0.1:8080**. Unlike Compose, local Maven/Java commands do not
load `.env` automatically; pass configuration through environment variables.

## Play a game

1. Create a table, choose its format, and share the room URL with the other players.
2. Import and preview each deck. Choose commanders for Commander tables, load
   the deck and review applicable warnings.
3. Review the visible mulligan rules before becoming ready. The host can choose
   London mode and a free-mulligan count, or unlimited seven-card redraws. Changing
   rules resets readiness. Defaults are London with zero free for Standard and one for Commander.
   Accept applicable warnings and become ready after reviewing the rules.
4. The host starts once every required seat is ready. Standard lets the host
   choose the starting player, defaulting to the host. Each player independently
   redraws or keeps their opening hand. For paid London mulligans, select and
   order the cards to bottom when keeping; play begins after everyone keeps.
5. Use card menus and player/zone controls to perform tabletop actions. Resolve
   card effects yourselves; the server does not interpret card text.
6. The host marks the game finished after players settle the result. Corrections
   are explicit actions; there is no automatic undo.

URL imports depend on Moxfield availability and deck access. If they fail,
export the deck and paste its text. Scryfall definitions are cached in
PostgreSQL; loaded games can continue without a catalog connection, but images
remain externally hosted and uncached cards still need resolution.

## Hosting with HTTPS

For an Oracle Cloud VM with a public IP and no domain, follow the
[public-IP deployment guide](docs/deployment-oracle.md). It uses a separate
production configuration and includes the remaining operator verification steps.

The optional Compose `https` profile runs Caddy in front of the packaged app.
For a domain pointing at your host, configure `.env` with:

```dotenv
TABLE_DOMAIN=table.example.com
PUBLIC_ORIGIN=https://table.example.com
SECURE_COOKIE=true
```

Make ports 80 and 443 reachable, then run:

```sh
docker compose --profile https up --build
```

The proxy forwards HTTP and WebSocket traffic to the app. The Vite development
server is intended for local development. Back up PostgreSQL before upgrading
saved-game formats.

## Repository map

| Location                        | Purpose                                          |
| ------------------------------- | ------------------------------------------------ |
| [backend/](backend/README.md)   | Authoritative game server and persistence        |
| [frontend/](frontend/README.md) | Browser client and UI tests                      |
| [scripts/](scripts/)            | Full-project build and build/report verification |
| [docs/API.md](docs/API.md)      | REST, WebSocket, and command contracts           |
| [spec/INDEX.md](spec/INDEX.md)  | Feature specifications and dependencies          |
| [docs/adr/](docs/adr/)          | Architecture decisions                           |
| [AGENTS.md](AGENTS.md)          | Repository workflow and canonical commands       |
