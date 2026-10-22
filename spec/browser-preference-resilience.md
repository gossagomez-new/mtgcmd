# Browser preference resilience

## Metadata

- Status: Completed
- Priority: Medium
- Priority rationale: TD06 prevents optional name persistence from interrupting rendering or successful room entry.
- Depends on: [App composition](app-composition.md)

## Summary

Deliver TD06 from the [technical debt backlog](../docs/technical-debt-backlog.md).
The player-name preference is optional; browser storage failure must not prevent
rendering or turn successful create/join responses into apparent API failures.

## Goals

- Keep create/join usable without local storage.
- Preserve saved-name behavior when storage works and current React state otherwise.

## Non-goals

- Changes to cookies, guest identity, turn-notification storage or backend contracts.
- A generic preference framework, global in-memory cache or new dependency.

## Background

The pre-design audit found an unguarded render-time storage read in App and a
write after successful HTTP create/join but before snapshot acceptance/navigation
in useTableSession. A throwing getter or operation interrupts these paths.
Both are required TD06 fixes; no additional remediation is included.

## Requirements

### Functional requirements

1. Read the existing `table-name` key safely; missing/unavailable/throwing storage returns an empty initial name.
2. Perform initial preference loading lazily rather than reading on each render.
3. Guard storage acquisition and writes. A write failure after successful create/join must not interrupt navigation, snapshot acceptance, socket connection or busy-state cleanup, or produce a false error/retry.
4. Preserve the entered name in current React state. Without persistent storage, a fresh reload may lose the preference.
5. Working storage restores and saves names under the unchanged key. Actual API failures remain errors and do not save unsuccessful names or navigate.

### Non-functional requirements

- Storage errors are optional-preference failures only; do not swallow unrelated API or navigation failures.
- Preserve accessibility, both formats and server authority.

## Contracts and data

Keep the existing string preference, HTTP payloads and snapshots. A small guarded
name-preference helper owns storage error handling; callers retain state/workflow ownership.

## Technical constraints

Use existing React state and test tooling. No module-level fallback state or new dependency.

## Invariants

- A submitted create/join is not automatically retried because storage failed.
- Cookie/session identity remains independent of the name preference.

## Edge cases and failures

Storage getter denied, missing storage, failing getItem/setItem, missing/empty/saved
values, blocked reads with working writes, successful responses and genuine API failures.

## Migration and compatibility

No migration, backend change or version bump. Reuse `table-name` exactly.

## Acceptance criteria

- [x] Rendering and both-format create/join survive preference failures.
- [x] Successful mutations are accepted once without false errors; genuine failures remain visible.
- [x] Available storage and current-state fallback behave as specified.
- [x] Relevant unit/browser, lint, format, type/build and specification checks pass.

## Verification

User confirmed comprehensive coverage on 2026-10-22: unavailable/throwing storage
access, reads and writes; missing/saved names; both-format create/join; no duplicate
requests or false errors; in-memory state; genuine API failures; and browser reloads.
Add failing behavior tests before production edits, then run relevant unit/browser
suites and frontend verification. Record executed checks and limitations here.

### Executed verification (2026-10-22)

- Behavioral red: the original create flow displayed `Preference denied` and
  failed to enter the lobby after a successful response. Five helper cases then
  failed on unguarded storage operations while all existing unit tests passed.
- Green: `./scripts/verify.sh --task browser-preferences frontend` passed all
  240 unit cases (nine new preference cases), 41 analyzer fixtures, lint, formatting,
  analysis and TypeScript/production build. Analysis retained eight advisory
  warnings and zero Knip issues; no rules were weakened.
- `./scripts/verify.sh --task browser-preferences browser` passed all 170 cases,
  including 21 new preference scenarios and the existing real Commander/Standard
  multiplayer flows. Create/join coverage includes denied getters, unavailable
  storage, failed reads and failed writes in both formats.
- Evidence manifests:
  `/tmp/mtgcmd-verify-browser-preferences-ednt3xyb/manifest.json` and
  `/tmp/mtgcmd-verify-browser-preferences-0paigeg0/manifest.json`.
  These describe executable source before final documentation completion updates.
- Specification links/graph, Markdown formatting and whitespace checks passed.
  No separate backend unit, PIT or deployment run was required for unchanged
  backend code; browser tests started the real backend test server.

## Architecture decisions

No new ADR required: a local guarded helper is a reversible implementation detail
within the existing App/session ownership established by
[ADR-0013](../docs/adr/0013-app-composition-boundaries.md).

## Open questions

None.
