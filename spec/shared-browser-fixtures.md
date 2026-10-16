# Shared browser characterization fixtures

## Metadata

- Status: Completed
- Priority: Medium
- Priority rationale: Remove repeated mocked transport setup and prevent selector and lifecycle mistakes in recurring browser characterization work.
- Depends on: [App composition](app-composition.md), [Inspector composition](inspector-composition.md), [Deck import composition](deck-import-composition.md)

## Summary

Share typed browser setup, transport delivery, request capture, owned deferred responses, and stable source/commander selectors while retaining scenario assertions.

## Goals

- Provide a per-test mocked table fixture with automatic cleanup.
- Make snapshots and acknowledgments explicit and wait for observable UI evidence supplied by the scenario.
- Reuse stable selectors as textarea content and select options change.
- Migrate repeated setup in composition, protocol, Inspector, and DeckImport suites.

## Non-goals

- Change production UI, APIs, game rules, state lifetimes, or privacy.
- Mock the real-server tabletop privacy/reconnect flows.
- Hide command expectations or permissions in a generic testing DSL.
- Replace existing server lifecycle, add dependencies, or create a new skill.

## Background

The pre-design audit found duplicate session/room/WebSocket setup and request queues across mocked suites. Existing updates sometimes send frames without evidence React accepted them. Earlier DeckImport work encountered exact-label selectors failing after textarea or select contents changed. The approved automation addresses these test-support issues; assertions and real-server coverage remain explicit.

## Requirements

### Functional requirements

1. Supply independent per-test session bootstrap, optional validated initial room state, mocked sockets, session/connection counts, and typed command capture. Reject invalid initial snapshots/room identifiers promptly.
2. Do not acknowledge commands automatically. Scenarios explicitly choose callbacks, snapshot delivery, acknowledgment delivery, or raw malformed messages.
3. Snapshot/ack helpers preserve provided state and revision and require an awaited observation callback. The callback must assert observable rendered evidence; sending a frame is insufficient. Scenarios own any deliberate marker changes.
4. Keep malformed-frame injection available for protocol recovery tests without weakening normal typed snapshot validation.
5. Capture JSON request bodies using caller-provided schemas and an explicit HTTP method. Support response queues, default replies, and fixture-owned deferred replies. Unmatched methods fall through to the existing route.
6. Dispose all owned pending replies, await active handlers, close sockets, and remove only owned HTTP routes. Cleanup is idempotent, runs on test failure, and does not mask startup failures. Unexpected handler errors must fail verification rather than disappear.
7. Provide source/commander locators stable before and after textarea values and select options change. Preserve product labels and accessible controls.
8. Keep fixture payload construction and command/permission/lifecycle assertions in scenarios. Migrate a representative mocked suite first, then the remaining duplication. Real-server tabletop tests reuse selectors only.

### Non-functional requirements

- Use existing Playwright, Zod, protocol schemas, and test commands.
- No arbitrary sleeps, production instrumentation, new test-only app fields, or hidden snapshot mutations in the common fixture.
- Include new support tests in the existing browser-test command and preserve enforced lint/type checks.

## Contracts and data

A test-scoped mockTable fixture owns a Page, optional Snapshot, captured commands, routes, socket connections, outstanding handlers, and deferred replies. Shared command capture retains id, revision, type, and data; scenarios choose the fields to assert. Reply queues accept JSON replies or owned deferred replies. Explicit disposal resolves unresolved deferred replies with a synthetic 503 response and releases resources before surfacing handler errors. Snapshot and acknowledgment callbacks differ only in transport semantics; the helper does not invent revisions or assume an acknowledgment clears a particular command ID.

## Technical constraints

Preserve production protocol behavior and reuse existing schema validation. Negative protocol tests send raw frames deliberately. Inspector and composition scenario wrappers may use explicit visible phase/deck-count markers for accepted updates; these remain scenario-owned synthetic fixture data. No new ADR is needed for reversible test-support extraction under existing component/protocol conventions.

## Invariants

- One test's mock state and queues cannot leak into another.
- Snapshot delivery alone leaves a pending command pending.
- Existing assertions remain visible and retain their meanings.
- Real-server privacy/reconnect flows continue to exercise actual transport.

## Edge cases and failures

Invalid startup state, absent sockets, malformed request bodies, handler failures, pending deferred responses at teardown, repeated disposal, closed pages, malformed server messages, and unchanged form values after accepted snapshots need explicit handling. Selector tests include populated textarea values and modified option content.

## Migration and compatibility

Tests and documentation only; no runtime migration. Rollback reverts test-support extraction. Preserve existing transport-failure and cancellation scenarios.

## Acceptance criteria

- [x] Existing browser suite passes before migration.
- [x] New support tests demonstrate missing fixture behavior before implementation.
- [x] Composition migration passes before remaining mocked suites migrate.
- [x] Full browser suite and appropriate unit/lint/type/format/build/analysis checks pass.
- [x] Deferred replies and startup failures clean up without unhandled work.

## Verification

User selected comprehensive coverage. Verify startup failures, snapshot/ack pending semantics, awaited observations, typed request capture, method matching, fallback and queued responses, deferred cleanup, handler failures, repeated cleanup, selectors across contents/options, and cross-test isolation. Retain all existing browser gameplay/privacy/reconnect/lifecycle assertions. Run new support cases first, composition as the initial migration, the complete browser suite, existing frontend unit tests, enforced lint/types/format, frontend production build, and static analysis. Backend and tooling behavior are unchanged, so standalone backend and wrapper tests are not required.

### Executed checks

- Original browser suite before migration: 28 passed.
- All seven new support cases failed on the stubbed/missing helper behavior before implementation and passed afterward.
- Representative composition/support migration: 12 passed before the remaining suites migrated.
- Final complete browser suite: 35 passed, retaining real-server Commander/Standard/privacy/reconnect flows and including method fallthrough, typed capture, pending/ack behavior, observed updates, deferred cleanup, invalid startup, closed-page cleanup, and selector regressions.
- Existing frontend unit suite: 66 passed. Lint, types, formatting, frontend production build, and git whitespace checks passed.
- Final static analysis: 17 advisory warnings, zero errors, zero Knip findings. New helpers and support cases have no findings.
- Application/backend/build-tool production code is unchanged. Standalone backend, build-wrapper suites, and the full packaging wrapper were not rerun; browser checks start the existing Java/database test server.

## Architecture decisions

[ADR-0013](../docs/adr/0013-app-composition-boundaries.md) and [ADR-0011](../docs/adr/0011-validated-frontend-protocol-boundary.md) establish existing state-lifetime and protocol conventions. This test-only reuse does not change those boundaries or require a new ADR.

## Open questions

None.
