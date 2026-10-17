# Tabletop test protocol boundaries

## Metadata

- Status: Completed
- Priority: Medium
- Priority rationale: Make real-server browser assertions trustworthy at JSON boundaries.
- Depends on: [Validated frontend protocol](validated-frontend-protocol.md), [Shared browser fixtures](shared-browser-fixtures.md)

## Summary

Validate real-server test traffic using existing decoders, retaining raw privacy evidence.

## Goals

- Remove unchecked JSON and snapshot assertions from tabletop scenarios.
- Make malformed responses observable without changing gameplay behavior.

## Non-goals

- Application changes, new dependencies, mocked replacements for real-server scenarios.

## Background

Tabletop tests observe WebSockets, intercept imports, and issue HTTP setup commands.

## Requirements

1. Decode every server message and preserve snapshot, ack, and error-with-view updates.
2. Retain original frame text for privacy checks; decoding may strip unknown fields.
3. Observe only the application `/ws` endpoint, excluding development-tool sockets.
   Record malformed frames and fail the scenario without unhandled listener errors.
4. Check HTTP status before success-body validation. Validate sessions and snapshots.
5. Validate import URL/text fields against the existing request shape.
6. Reconnect polling retries transport failures and temporary unavailable responses only;
   malformed successful JSON and permanent HTTP failures must fail immediately.
7. Commands execute once with the current revision and original payload semantics.

## Contracts and data

Reuse protocol.ts decoders. Test helpers own observation and HTTP test assertions.
Raw frame history remains test-local and is never included in parsing error messages.

## Technical constraints

Use existing Playwright and Zod dependencies. Keep production code unchanged.

## Invariants

- Preserve Commander/Standard scenarios, permissions, selectors, and cleanup.
- Preserve explicit-reveal behavior; privacy checks inspect appropriate pre-reveal frames.

## Edge cases and failures

Presence and errors without views preserve the last snapshot. Invalid frames remain
recorded as failures even if subsequent valid frames arrive. Successful HTTP responses
with invalid JSON fail immediately. No mutation retries.

## Migration and compatibility

No migration; test-only changes.

## Acceptance criteria

- [x] Typed boundary tests and existing browser flows pass.
- [x] Tabletop unsafe findings disappear without suppressions.
- [x] Raw unexpected fields cannot evade privacy assertions through schema stripping.

## Verification

User confirmed comprehensive coverage: every message variant, malformed HTTP/WebSocket
data, raw-frame privacy evidence, import request validation, reconnect retry boundaries,
single-attempt commands, and existing Commander/Standard browser flows. Run focused
Playwright helper tests and tabletop scenarios, frontend unit tests, lint, typecheck,
format checks, static analysis, and specification validation. No performance assertions.

### Executed checks

- TDD: four new boundary tests failed against helper stubs for expected behavior;
  the initial sandbox server-start failure was resolved before confirming red.
- Nine focused Playwright tests passed: four boundary tests and all five existing
  tabletop scenarios. After expanding edge assertions and correcting the async
  fixture callback, all four boundary tests passed again.
- All 66 frontend unit tests passed.
- Frontend typecheck, lint, and format checks passed.
- Final analysis completed with six existing production warnings, zero test
  warnings, zero errors, and zero Knip issues (previously 17 warnings).
- Specification validation and Git diff whitespace checks passed.
- No backend source changed; the independent backend suite and unrelated browser
  scenarios were not rerun. Tabletop browser checks started the real test backend.

## Architecture decisions

[ADR-0011](../docs/adr/0011-validated-frontend-protocol-boundary.md) already establishes
shared runtime decoders. Test-local helper extraction requires no new ADR.

## Open questions

None.
