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

| Command from the repository root          | Purpose                                                     |
| ----------------------------------------- | ----------------------------------------------------------- |
| `npm --prefix frontend run dev`           | Start the local Vite server                                 |
| `npm --prefix frontend run build`         | TypeScript checks and production build into `frontend/dist` |
| `npm --prefix frontend run typecheck`     | TypeScript project checks                                   |
| `npm --prefix frontend test`              | Vitest unit tests under `src`                               |
| `npm --prefix frontend run lint`          | ESLint                                                      |
| `npm --prefix frontend run analyze`       | Typed ESLint, accessibility, complexity, and Knip reports   |
| `npm --prefix frontend run test:analysis` | Analyzer/report integration tests                           |
| `npm --prefix frontend run format:check`  | Check configured source/test/config formatting              |
| `npm --prefix frontend run format`        | Rewrite configured source/test/config formatting            |
| `npm --prefix frontend run test:e2e`      | Playwright browser scenarios                                |

To run a specific unit-test file:

```sh
npm --prefix frontend test -- src/protocol.test.ts
```

For a complete packaged app, use `./scripts/build.sh` from the repository root.
It builds this frontend before Java copies `frontend/dist` into the executable
jar. Standalone frontend output still needs the backend's API and WebSocket
services; it is not an independent game server.

## Static analysis

Run `npm --prefix frontend run analyze` to generate reports under
`frontend/reports/static-analysis/`. The full `./scripts/build.sh` runs it before
frontend compilation, including when `--skip-tests` is used. A standalone
`npm run build` or Docker packaging does not replace that verified build workflow.

- `eslint.json` and `eslint.txt`: existing enforced rules plus new advisory
  type-aware TypeScript, JSX accessibility, and complexity findings.
- `knip.json` and `knip.txt`: unused files, exports, dependencies, and other
  project-graph diagnostics. Text retains issue categories and locations/names
  supplied by Knip; JSON retains the complete native report.
- `summary.json` and `summary.txt`: completion status and counts. A Knip issue
  counts one reported item/group, so duplicate/cycle groups are not expanded.
- `failure.txt`: execution/enforced-lint failure details. Failed runs remove
  prior success summaries; partial diagnostic reports are not a clean result.

New findings do not abort a build. Existing lint errors, parsing/configuration
errors, analyzer failures, and inability to write reports do. `npm run lint`
continues enforcing the original rule set without enabling the new warnings.
Analysis does not autofix or upload source code. Knip can load local tool configs;
run it only on a trusted checkout, as with the build itself.

Initial production-function thresholds:

| ESLint rule              | Warning threshold                                 |
| ------------------------ | ------------------------------------------------- |
| `complexity`             | More than 10 cyclomatic paths                     |
| `max-depth`              | More than 4 nested blocks                         |
| `max-lines-per-function` | More than 80 lines, excluding blank/comment lines |

Metrics apply to `src` functions excluding `*.test.ts`/`*.test.tsx`. Typed checks
also cover TypeScript tests and root tool configs; accessibility checks cover
source JSX. Generated output and reports are excluded. The Knip configuration
recognizes Vite entry points, unit/browser tests, and executable tooling scripts.

Review warnings in context before changing behavior or introducing abstractions.
Type API boundaries rather than suppressing unsafe-value findings; verify UI
accessibility in the browser as well as statically. Promote selected rules only
after reviewing their relevance. Reports are regenerated and ignored by Git,
excluded from Docker build context, and never copied into `dist` or the jar.

The initial run found 42 ESLint warnings (type safety and function size/complexity)
and no Knip findings; this is a dated observation, not a suppression baseline.
See the [specification](../spec/frontend-static-analysis.md) and
[ADR-0010](../docs/adr/0010-frontend-static-analysis.md).

## Review analysis and compare baselines

After generating analysis, summarize the native reports without rerunning tools:

```sh
npm --prefix frontend run review:analysis
```

The review separates production, tests, and other files; groups findings by rule
and file; and quotes complexity/length/depth metrics supplied by ESLint. Knip
counts include unused files as well as per-file issue categories.

To save a baseline and compare a later run, use fresh output paths:

```sh
npm --prefix frontend run review:analysis -- --json /tmp/frontend-before.json
npm --prefix frontend run analyze
npm --prefix frontend run review:analysis -- --baseline /tmp/frontend-before.json --json /tmp/frontend-after.json --markdown /tmp/frontend-after.md
```

Existing files are never overwritten. Keep baselines outside the regenerated
report directory and preferably outside frontend input paths (for example in
`/tmp`). A baseline is the review JSON, not the native `summary.json`. The direct
CLI `node frontend/scripts/review-analysis.mjs --help` describes custom frontend
roots and report directories; its defaults do not depend on the current working
directory.

Freshness is **current**, **stale**, or **unverified**. New analyzer summaries bind
the raw report hashes to a fingerprint of frontend JS/TS/JSON/HTML/CSS source and
configuration inputs. Dependencies, generated outputs, and reports are excluded.
This does not attest backend state, external plugins, environment equivalence,
or correctness. Source edits during generation fail analysis; legacy completed
reports without provenance can be reviewed as unverified.

Comparisons preserve duplicate occurrences and ignore line/column moves. File
renames and changed messages count as resolved/new; these are diagnostic
identities, not proof of semantic introductions or removals. Compare configuration,
package lock, and runtime changes before attributing deltas to code quality.
Exit 0 means successful review regardless of advisory findings, 2 means incomplete
analysis, and 1 means invalid inputs/outputs or CLI usage. Neither baselines nor
review counts change build gates or suppress findings.

See the [review specification](../spec/frontend-analysis-review.md) and
[frontend review skill](../skills/frontend-analysis-review/SKILL.md).

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

HTTP and WebSocket responses cross a shared Zod validation boundary before
entering UI state. Protocol types are inferred from those schemas, including
omitted hidden-card definitions and nullable card references. Additive object
fields are ignored; malformed required fields are rejected without coercion.
Invalid socket messages retain the previous view and do not acknowledge pending
commands. Unknown message variants are rejected with a safe error.

HTTP failures retain their status and a validated server message or safe fallback.
A 409 triggers one current-room refresh; a failed refresh cannot mask the
original conflict. Mutations are never automatically retried after response
validation fails, because the server may already have committed them. Network
and abort failures retain their identity. Protocol diagnostics omit raw JSON and
schema input values. See the [boundary specification](../spec/validated-frontend-protocol.md)
and [ADR-0011](../docs/adr/0011-validated-frontend-protocol-boundary.md).

## Code map

| File                                                   | Responsibility                                                                              |
| ------------------------------------------------------ | ------------------------------------------------------------------------------------------- |
| [src/main.tsx](src/main.tsx)                           | React entry point                                                                           |
| [src/App.tsx](src/App.tsx)                             | Screen composition, persistent forms, and overlay selection                                 |
| [src/useTableSession.ts](src/useTableSession.ts)       | Guest bootstrap, room loading, accepted snapshots, and table mutations                      |
| [src/table/](src/table/)                               | Welcome/lobby/table screens, playing regions, and card/zone components                      |
| [src/table/DeckImport.tsx](src/table/DeckImport.tsx)   | Import form state and preview request coordination                                          |
| [src/table/deckImport/](src/table/deckImport/)         | Controlled deck source fields, preview, and commander selection                             |
| [src/table/Inspector.tsx](src/table/Inspector.tsx)     | Inspector form state and card-bound action dispatch                                         |
| [src/table/inspector/](src/table/inspector/)           | Card details and controlled movement, counter, relation, combat, reveal, and token controls |
| [src/protocol.ts](src/protocol.ts)                     | Zod schemas, inferred protocol types, decoding, and revision acceptance                     |
| [src/http.ts](src/http.ts)                             | Validated session/room/deck endpoints and conflict recovery                                 |
| [src/socketMessages.ts](src/socketMessages.ts)         | Validated message dispatch before state effects                                             |
| [src/useGameSocket.ts](src/useGameSocket.ts)           | Connection, acknowledgments, presence, and reconnect behavior                               |
| [src/battlefield.ts](src/battlefield.ts)               | Stable permanent grouping and land separation                                               |
| [src/style.css](src/style.css)                         | Table layout, card presentation, and tap rotation                                           |
| [tests/tabletop.spec.ts](tests/tabletop.spec.ts)       | Browser scenarios                                                                           |
| [tests/composition.spec.ts](tests/composition.spec.ts) | Form, readiness, overlay, stack, and permission lifetimes                                   |
| [tests/inspector.spec.ts](tests/inspector.spec.ts)     | Inspector payloads, permissions, state lifetimes, presentation, and token prompts           |
| [tests/deckImport.spec.ts](tests/deckImport.spec.ts)   | Import payloads, formats, selections, invalidation, and pending/error recovery              |
| [tests/protocol.spec.ts](tests/protocol.spec.ts)       | Malformed responses, cancellation, and pending-ack browser checks                           |

See the [specification index](../spec/INDEX.md) for the approved behavior and
feature verification requirements.
