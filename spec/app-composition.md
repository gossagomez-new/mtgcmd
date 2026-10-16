# App composition

## Metadata

- Status: Completed
- Priority: Medium
- Priority rationale: Reduce composition complexity while preserving game and privacy behavior.
- Depends on: [Validated frontend protocol](validated-frontend-protocol.md), [Frontend static analysis](frontend-static-analysis.md)

## Summary

Separate App session lifecycle, screens, and playing-table regions without changing UI behavior.

## Goals

- Keep App focused on navigation, persistent form values, and overlay selection.
- Isolate session bootstrap, room requests, socket lifetime, and mutations in a cohesive hook.
- Compose welcome, lobby, and playing screens from explicit data and callback props.

## Non-goals

- Change transport contracts, game rules, styling, or nested card-editor behavior.
- Introduce global state or a state-management dependency.

## Background

The App function combines session effects and every screen. The design audit identified state lifetime as a required extraction constraint: warning acceptance is keyed by deck revision and zone filters by room/seat/zone. Existing form state survives navigation. These are prerequisites within the approved decomposition, not additional feature work.

## Requirements

### Functional requirements

1. Preserve all labels, classes, controls, filtering, player visibility, and command payloads.
2. Retain form values above screen lifetimes and derive inspected cards from the latest accepted snapshot.
3. Preserve canceled room-load protection and stable bootstrap/socket callbacks.
4. Preserve library SEARCH/CLOSE_SEARCH commands, readiness reset keys, stack ordering, and disabled-action rules.

### Non-functional requirements

- Use existing validated protocol boundaries and dependencies.
- Move existing card, import, counter, and inspector components without redesigning their internals.

## Contracts and data

HTTP, WebSocket, snapshot schemas, and server authority remain unchanged. Components receive explicit data and callbacks. Session state is owned by the session hook; form and overlay state remains owned by App.

## Technical constraints

Keep existing DOM wrappers and CSS classes. Preserve host finish permission independently of concession. Do not retain obsolete selected card objects.

## Invariants

- A lower snapshot revision cannot replace a newer one.
- Private card visibility remains server-filtered.
- Local UI edits do not reconnect the socket.

## Edge cases and failures

Canceled GETs, initial forbidden loads, malformed messages, failed mutations, pending commands, conceded players, finished tables, empty stacks, and changed deck revisions retain existing behavior.

## Migration and compatibility

No persisted-data or public-contract migration. Rollback is a frontend code revert.

## Acceptance criteria

- [x] Existing gameplay and privacy flows pass unchanged.
- [x] New lifecycle characterization tests pass before and after extraction.
- [x] App delegates screen rendering and session effects to cohesive modules.
- [x] Type, lint, formatting, build, and unused-code checks pass.

## Verification

User selected comprehensive coverage. Retain unit/browser suites and add form/navigation persistence, warning revision reset, overlay/search lifetime, current-card selection, stack ordering, disabled actions, and stable session/socket checks. Existing browser tests cover canceled loads, both formats, reconnects, and privacy. No performance assertion is required for this composition-only change.

### Executed checks

- Five new characterization tests passed against the original App before extraction.
- Final browser suite: 14 passed, including all five characterization tests.
- Frontend unit suite: 66 passed.
- Type checking, enforced lint, formatting, final frontend production build, and whitespace checks passed.
- Full build passed, including 571 backend tests. The final frontend refinements were subsequently checked with the frontend build and full browser suite.
- Static analysis: 22 advisory ESLint warnings (previously 24), no errors, and zero Knip findings. App complexity fell from 54 to 15; its function length fell from 672 to 111 lines. Remaining App composition warnings and unchanged nested-component diagnostics remain advisory; this work does not claim to eliminate them.
- Compared all 11 relocated legacy component functions with their original implementations; only module exports/imports and formatting changed.

## Architecture decisions

[ADR-0013](../docs/adr/0013-app-composition-boundaries.md) records component and session boundaries.

## Open questions

None.
