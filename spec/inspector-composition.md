# Inspector composition

## Metadata

- Status: Completed
- Priority: Medium
- Priority rationale: Localize card-control changes in the largest remaining frontend complexity hotspot.
- Depends on: [App composition](app-composition.md)

## Summary

Compose Inspector from card details and cohesive controlled action groups while preserving its behavior.

## Goals

- Separate presentation, movement, identity, counters, permanent relations, combat, revealing, and token controls.
- Keep form state and card-bound command dispatch owned by Inspector.
- Reduce the current 280-line function and complexity of 32 without suppressions.

## Non-goals

- Change game rules, command payloads, card privacy, or UI styling.
- Change selection reset policies or split the shared controller/reveal audience value.
- Introduce dependencies, global state, or a generic action registry.

## Background

The pre-design audit found independently changing controls combined in Inspector. Two required compatibility constraints were presented and approved in the design: copying is independent of card editability, and all four form values outlive conditionally rendered controls and changes of the selected card. These constraints guide extraction; there is no additional remediation scope.

## Requirements

### Functional requirements

1. Preserve command names, payload fields, ordering of controls, DOM wrappers, labels, and CSS classes.
2. Preserve card-specific dispatch by adding the current card ID to each action.
3. Preserve the counter name, selected player, attachment, and blocker while Inspector remains mounted, including card changes and temporary permission/zone changes. Closing/removing Inspector resets these values.
4. Preserve sharing of the selected-player value between transfer control and reveal audience, initially set to the local seat.
5. Render editable controls only when card.editable and gameplay is enabled. Allow copying any defined card outside library/hand while gameplay is enabled, independently of card.editable.
6. Preserve server-filtered definitions and current face/name/image/type/text fallbacks.
7. Preserve available movement destinations, attachment candidates, attacker candidates, empty counter/blocker restrictions, reveal audiences, and token prompt behavior.

### Non-functional requirements

- Use explicit controlled props and existing protocol types.
- No transport, persistence, security, or backend behavior change.

## Contracts and data

Inspector retains its existing public props: card, view, act, disabled, close. Child components receive relevant card/player/candidate data, controlled form values, setters, and a card-bound Action callback. Copy and token-edit permissions remain separate. Pending commands temporarily unmount the editable controls, closing the reveal disclosure while retaining the selected audience. Command contracts remain unchanged, including an empty attachment target for detach and an empty combat marker for clearing.

## Technical constraints

Follow ADR-0013 component composition. Child components must not introduce conditional state lifetimes or add a key that resets Inspector when the selected card changes. Group related components in cohesive files.

## Invariants

- Every action targets the currently selected card.
- Hidden definitions are not reconstructed client-side.
- Copy eligibility does not imply edit eligibility.
- Snapshots update displayed information without overwriting form selections.

## Edge cases and failures

Missing definitions/faces, empty fallback text, face-down cards, public read-only cards, private zones, disabled gameplay, empty names, no candidates, snapshot-driven card removal, token prompt cancellation, and both table formats retain existing behavior. Canceling the token name aborts editing; canceling later prompts produces empty type/text, as currently implemented.

## Migration and compatibility

No data or protocol migration. Rollback is a frontend code revert.

## Acceptance criteria

- [x] New characterization coverage passes before and after extraction.
- [x] Existing unit/browser suites pass without changed expectations.
- [x] Inspector delegates presentation and action groups while retaining state ownership.
- [x] Formatting, lint, types, build, and analysis succeed without new advisory findings.

## Verification

User selected comprehensive coverage. Verify exact payloads for all commands and movement variants; public/private/read-only/disabled copy permissions; commander destination filtering; attachment and attacker candidates; shared player selection; counter/blocker guards; card/zone/permission state lifetimes; hidden and multi-face presentation; token prompt defaults/cancellation; latest-snapshot dispatch; close/reopen reset; and both formats. Establish a green characterization baseline before behavior-preserving extraction. Run the existing frontend unit suite, complete browser suite, formatting/type/lint checks, frontend production build, and static analysis. Backend code is unchanged.

### Executed checks

- All seven new browser characterization cases passed against the original Inspector before extraction and against the extracted components afterward.
- Final complete browser suite: 21 passed, including both formats, privacy, cancellation, reconnects, and the seven Inspector cases.
- Frontend unit suite: 66 passed before and after extraction.
- Enforced lint, type checking, formatting, frontend production build, and git whitespace checks passed.
- Frontend analysis: 20 advisory warnings (previously 22), zero errors, and zero Knip findings. Inspector and its extracted components have no analysis findings; its function shrank from 280 to 75 lines.
- The standalone backend test suite and full packaging wrapper were not rerun: production changes are confined to frontend composition. Browser verification starts the existing backend test server.

## Architecture decisions

[ADR-0013](../docs/adr/0013-app-composition-boundaries.md) already establishes controlled components, explicit callback props, and local state ownership. This extraction applies that convention and needs no new ADR.

## Open questions

None.
