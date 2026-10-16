# Deck import composition

## Metadata

- Status: Completed
- Priority: Medium
- Priority rationale: Separate source, preview, and commander selection from the remaining import workflow hotspot.
- Depends on: [App composition](app-composition.md)

## Summary

Compose DeckImport from controlled source inputs, a preview, and commander selection while retaining workflow ownership and compatibility.

## Goals

- Keep source text, URL, draft, commanders, and local preview pending state owned by DeckImport.
- Extract controlled source, preview, and commander-selection components.
- Replace the inline asynchronous event handler with a named error-handling workflow and synchronous event wrapper.

## Non-goals

- Change source precedence, backend validation, endpoint contracts, or game rules.
- Add cancellation or stale-response rejection, change reset policies, or redesign error messages.
- Introduce state libraries, generic registries, new dependencies, or duplicated loading state.

## Background

The design audit identified source inputs, asynchronous preview work, and commander selection mixed in a 142-line component with complexity 15. The approved extraction keeps state lifetimes and current pending semantics. Inputs remain editable while a preview is pending; response invalidation was identified as an optional behavioral improvement and is outside this refactor.

## Requirements

### Functional requirements

1. Preserve text, URL, draft, commanders, and pending state across accepted lobby snapshots. Source changes clear the current draft without changing the other input.
2. Preserve labels, introductory copy, placeholders, DOM wrappers, order, and CSS classes.
3. Pass both raw source strings unchanged to preview. Disable preview when gameplay is disabled, locally pending, or both trimmed sources are empty.
4. Preview success installs the returned draft and resets commanders to suggested values for Commander or an empty array for Standard. Starting or failing a new preview does not otherwise clear a previous draft.
5. Preview failure retains existing message normalization. Clear URL only when the captured URL is nonempty and the lowercased error message includes "paste". Always release local pending state.
6. Keep commander selection controlled. Preserve existing primary/secondary update expressions, order of choices, primary exclusion in secondary options, empty-selection behavior, and first-commander load restriction.
7. Standard hides commander controls and always loads with an empty commander array.
8. Preserve load eligibility: parent disabled applies, but local preview pending does not independently disable loading an existing draft.
9. Keep load coordination, busy state, revision, command IDs, error handling, conflict recovery, and snapshot acceptance in useTableSession and the existing HTTP adapter.

### Non-functional requirements

- Use existing protocol types and explicit data/callback props.
- Do not add suppressions or alter analysis thresholds.

## Contracts and data

DeckImport retains its existing format, disabled, preview, load, and fail props. Child components receive controlled values and synchronous callbacks. The named preview workflow catches failures; its JSX event wrapper explicitly discards the handled promise. No public HTTP/WebSocket schema changes.

## Technical constraints

Use ADR-0013 component composition. Do not move selected commanders into the conditionally mounted preview or combine preview pending with parent busy. Backend retains final commander validation responsibility.

## Invariants

- Source edits invalidate the displayed draft.
- Accepted snapshots do not reset the import form.
- Requests are not automatically retried.
- All five existing local state values retain their current owner and lifetime.

## Edge cases and failures

Whitespace-only sources, both sources supplied, empty/multiple suggestions, optional second commander, failed re-preview with an existing draft, URL-specific paste fallback, malformed responses, pending preview, failed loading, and loading retries retain existing behavior. Late preview results are not newly rejected; that behavioral decision is explicitly deferred.

## Migration and compatibility

No data or protocol migration. Rollback is a frontend code revert.

## Acceptance criteria

- [x] New characterization tests pass before and after extraction.
- [x] Existing frontend unit and full browser suites pass unchanged.
- [x] DeckImport delegates source, preview, and selection rendering while retaining state and preview orchestration.
- [x] Lint, type, formatting, build, and analysis checks pass without new findings.

## Verification

User selected comprehensive coverage. Verify exact preview/load request bodies and format behavior, source invalidation, whitespace guards, commander defaults and selection edges, pending/re-preview semantics, preview failure/recovery, paste fallback, malformed responses, snapshot persistence, and parent loading busy/error/retry behavior. Establish a green characterization baseline before extraction. Run frontend unit tests, the complete browser suite, enforced lint, types, formatting, frontend production build, and static analysis. Backend production code and build tooling remain unchanged.

### Executed checks

- All seven new characterization cases passed against the original component before production extraction.
- Final complete browser suite: 28 passed, including the seven new import cases, both formats, gameplay, privacy, reconnects, and earlier component lifetimes.
- Frontend unit suite: 66 passed before and after extraction.
- Enforced lint, type checking, formatting, frontend production build, and git whitespace checks passed.
- Final static analysis: 17 advisory warnings (previously 20), zero errors, and zero Knip findings. DeckImport and its extracted components have no findings. The coordinating function shrank from 142 to 70 lines.
- The standalone backend suite and full packaging wrapper were not rerun because production and tooling changes are confined to frontend composition. Browser verification starts the existing backend test server.

## Architecture decisions

[ADR-0013](../docs/adr/0013-app-composition-boundaries.md) establishes the existing controlled-component and explicit-prop conventions. This extraction applies those decisions without new dependencies or architectural conventions; no new ADR is needed.

## Open questions

None.
