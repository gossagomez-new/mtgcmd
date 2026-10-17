# Card button presentation

## Metadata

- Status: Completed
- Priority: Medium
- Priority rationale: Separate independent presentation concerns in the shared card button.
- Depends on: [App composition](app-composition.md), [Shared browser fixtures](shared-browser-fixtures.md)

## Summary

Separate presentation resolution, artwork, and badges from the card interaction shell.

## Goals

- Reduce CardButton complexity with cohesive private functions and components.
- Preserve behavior across every existing caller.

## Non-goals

- PlayerArea changes, new dependencies, CSS changes, alternate card presentation policy.

## Background

CardButton is shared by hands, battlefields, stack, shared cards, and zone viewers.
Its reported ESLint complexity is 22 against a limit of 10.

## Requirements

1. Keep the exported CardButton props and outer button structure unchanged.
2. Resolve selected-face name/image before definition name/image, using existing
   truthy fallbacks, including empty strings and out-of-range face indices.
3. Keep fallback type/text sourced from the definition, not the selected face.
4. Preserve missing-definition placeholders and authorized face-down identity.
5. Preserve lazy images, alt text, caption, classes, badge order and content.
6. Preserve counter entry order, zero/negative values and separators, combat prefix
   matching, and empty optional attachment/combat behavior.
7. Inspection receives the current card. Dragging is enabled only for editable
   cards outside the battlefield; preserve application/card ID and move effect.
8. Components remain stateless and update from incoming snapshots.

## Contracts and data

Existing Card and inspect callback. Private presentation helper, artwork component,
and badge component remain within CardViews.tsx with no public API changes.

## Technical constraints

No new state, hooks, memoization, dependencies, or exported presentation API.

## Invariants

- Keep CSS selectors, DOM ordering and 90-degree tapped/hover orientation.
- Use only the server-filtered definition; faceDown alone does not hide identity
  from an authorized player.

## Edge cases and failures

Missing definitions, empty names/images/types, missing selected faces, image-less
cards, overlapping badges, updated card objects, and editable/zone combinations.

## Migration and compatibility

No migration or deployment change.

## Acceptance criteria

- [x] Characterization scenarios pass before and after extraction.
- [x] CardButton and new private helpers have no complexity warnings.
- [x] No new advisory findings or suppressions.

## Verification

User confirmed comprehensive coverage: face/name/image fallbacks, missing definitions,
all badge combinations, snapshot updates, inspection, drag permissions/payloads, and
tap/hover orientation on both player views. Add browser characterization tests first
and confirm green against existing behavior before production extraction. Run those
with existing composition, inspector, and tabletop scenarios; frontend unit tests,
typecheck, lint, formatting, static analysis, and specification validation. This
behavior-preserving extraction introduces no new behavior needing a red test.

### Executed checks

- Five browser characterization tests passed before production extraction, covering
  all 32 badge combinations, real drag payload/effect, inspection updates, missing
  identity, artwork/name fallbacks, and tap/hover rotation on both player views.
  An initial synthetic drag fixture was corrected to use a native browser drag
  before establishing the green characterization baseline.
- After extraction, the same five tests and 16 existing composition, inspector,
  and tabletop tests passed in the combined run (21 of 22). The remaining Commander
  scenario timed out on its initial untapped rotation check; it passed unchanged
  in isolation. This timing-sensitive first-run failure is retained as a limitation.
- All 66 frontend unit tests passed; typecheck, lint, formatting, production build,
  specification validation, and diff whitespace checks passed.
- Fresh static analysis found no CardButton/helper/test additions flagged. Production
  warnings decreased from six to five; the known 11 tabletop test warnings remain
  on this main-based branch until the independent PR #10 integrates. Total findings:
  16 ESLint warnings, zero errors, zero Knip issues. No thresholds or suppressions changed.
- Backend-only tests and unrelated browser scenarios were not rerun. Real-server
  tabletop scenarios used the existing backend test server.

### Publication verification after integrating main

PR #10 was integrated from main before publication. All 22 selected browser tests
passed together on the integrated source, including the Commander rotation scenario.
Typecheck, lint, specification validation, and diff checks passed again. Fresh analysis
reports five production warnings, no test warnings, zero errors, and zero Knip issues.
The earlier results above describe the pre-integration source and retained first-run
failure; the full integrated rerun required no retry.

## Architecture decisions

[ADR-0013](../docs/adr/0013-app-composition-boundaries.md) establishes composition
with explicit props. Private helper extraction requires no new ADR.

## Open questions

None.
