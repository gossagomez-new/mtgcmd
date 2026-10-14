# Frontend

React 19 / TypeScript browser client for the MTG tabletop, built with Vite.
The interface supports four-player Commander and two-player Standard; the
backend supplies authoritative state, format information, and seat-filtered
card views.

See the [project README](../README.md) for game formats and complete startup,
and the [backend README](../backend/README.md) for Java/database configuration.
**Run all commands below from the repository root.**

## Run for development

Requirements: Node 24, npm, and a running backend. Follow the
[backend setup](../backend/README.md#run-for-development) first, then install
frontend dependencies and start Vite:

```sh
npm --prefix frontend ci
npm --prefix frontend run dev -- --port 5173 --strictPort
```

Open **http://127.0.0.1:5173**. Strict-port mode prevents Vite from silently
choosing another port whose origin the backend would reject. The backend's
`PUBLIC_ORIGIN` must match the URL you actually open; `localhost` is a different
origin from `127.0.0.1`.

[Vite configuration](vite.config.ts) proxies `/api` and `/ws` to the local
backend on port 8080. If the backend uses another port, set `API_PORT` when
starting Vite, for example:

```sh
API_PORT=8081 npm --prefix frontend run dev -- --port 5173 --strictPort
```

`API_PORT` controls the development proxy; production requests use the same
origin as the packaged app. Changing the Vite port requires a matching backend
`PUBLIC_ORIGIN`. The `/__test` proxy is used by the isolated browser-test server.

## Build and checks

Commands are defined in [package.json](package.json).

| Command from the repository root         | Purpose                                                     |
| ---------------------------------------- | ----------------------------------------------------------- |
| `npm --prefix frontend run dev`          | Start the local Vite server                                 |
| `npm --prefix frontend run build`        | TypeScript checks and production build into `frontend/dist` |
| `npm --prefix frontend run typecheck`    | TypeScript project checks                                   |
| `npm --prefix frontend test`             | Vitest unit tests under `src`                               |
| `npm --prefix frontend run lint`         | ESLint                                                      |
| `npm --prefix frontend run format:check` | Check configured source/test/config formatting              |
| `npm --prefix frontend run format`       | Rewrite configured source/test/config formatting            |
| `npm --prefix frontend run test:e2e`     | Playwright browser scenarios                                |

To run a specific unit-test file:

```sh
npm --prefix frontend test -- src/protocol.test.ts
```

For a complete packaged app, use `./scripts/build.sh` from the repository root.
It builds this frontend before Java copies `frontend/dist` into the executable
jar. Standalone frontend output still needs the backend's API and WebSocket
services; it is not an independent game server.

## Browser tests

Playwright starts a test-only Java server with embedded PostgreSQL on port 8081
and Vite on port 5173. Java 25 and Maven are therefore required in addition to
Node. Run as a non-root user with permission to start local servers; keep those
ports available.

Install Chromium from the frontend directory:

```sh
(cd frontend && npx playwright install chromium)
```

Then run:

```sh
npm --prefix frontend run test:e2e
```

Alternatively, use an existing Chrome installation:

```sh
CHROME_PATH=/path/to/google-chrome npm --prefix frontend run test:e2e
```

The suite uses isolated player contexts, synthetic card data, and a temporary
database. It covers Commander/Standard flows, private views and library search,
battlefield layout/tapping, reconnect/restart behavior, and import fallback.
It does not depend on the development database or live Moxfield/Scryfall access.
Test-only server endpoints are excluded from the production jar. The
[Playwright configuration](playwright.config.ts) defines startup commands,
timeouts, and retained failure traces.

## Player controls

- Import a public Moxfield URL or paste quantity/name lines. Supported headers
  include Commander, Deck, and Sideboard; sideboard and maybeboard cards are
  excluded. Common printing annotations are stripped.
- Preview, choose commanders when applicable, load, review warnings, and ready
  up. The host starts once the required seats are ready.
- Click a card to inspect it or use its action menu. Drag playable cards onto
  the battlefield. Permanents group by primary type; lands occupy a lower
  section, and tapped cards rotate 90 degrees. Placement is automatic.
- Open zone viewers to inspect public zones or search a permitted library.
  Library search filters by card name while retaining original position numbers;
  move cards to the top/bottom or shuffle as required.
- Use menus for reveals, face-down cards, transformation, attachments, control
  changes, tokens, and combat markers. Shared stack markers track abilities and
  triggers; players resolve effects themselves.
- Track life, poison, named counters, and dice. Commander tables also expose
  commander cast/tax and damage controls. Private-zone grants let another seat
  inspect/manipulate your hand and search your library; revoke them when done.

To test multiple seats manually, use separate browser profiles or different
browsers. Tabs in one profile share the guest cookie and therefore the same
identity. An HttpOnly cookie holds the guest credential; browser local storage
keeps the display name. Clearing cookies loses access to associated seats.

## State and connection behavior

REST bootstraps the session and handles room/deck operations. Game commands
travel over WebSocket with a command ID and the last observed revision. The
client ignores older snapshots, tracks acknowledgments, and reconnects with
backoff. Actions are disabled while disconnected or awaiting acknowledgment.

The browser displays only the card information sent by the backend. Permission
and privacy checks belong to the server. Snapshot and command shapes are
documented in the [API reference](../docs/API.md).

## Code map

| File                                             | Responsibility                                                     |
| ------------------------------------------------ | ------------------------------------------------------------------ |
| [src/main.tsx](src/main.tsx)                     | React entry point                                                  |
| [src/App.tsx](src/App.tsx)                       | Room/deck flows, table controls, zone viewers, and card inspection |
| [src/protocol.ts](src/protocol.ts)               | Snapshot/message types, revision acceptance, and command envelopes |
| [src/useGameSocket.ts](src/useGameSocket.ts)     | Connection, acknowledgments, presence, and reconnect behavior      |
| [src/battlefield.ts](src/battlefield.ts)         | Stable permanent grouping and land separation                      |
| [src/style.css](src/style.css)                   | Table layout, card presentation, and tap rotation                  |
| [tests/tabletop.spec.ts](tests/tabletop.spec.ts) | Browser scenarios                                                  |

See the [specification index](../spec/INDEX.md) for the approved behavior and
feature verification requirements.
