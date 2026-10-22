# Opening-hand waiting names and hand hover clarity

## Metadata

- Status: Completed
- Priority: Medium
- Priority rationale: R26/R27 address immediate clarity and artwork obstruction found in playtesting.
- Depends on: [Configurable mulligans](configurable-mulligans.md), [Card artwork previews](card-artwork-previews.md), [Frequent table interactions](table-interactions.md)

## Summary

Deliver R26 named opening-hand waiting messages and R27 removal of the hand drop
tooltip in Standard and Commander.

## Goals

- Tell players who still needs to keep their opening hand.
- Preserve unobstructed hand artwork previews and existing drop behavior.

## Non-goals

- New game commands, permissions, combat, library menus or waiting-player actions.
- Altering R24 labels, mana costs/symbols or other pending playtest improvements.

## Background

The focused audit traced OpeningHands, mulligan metadata, YourHand, artwork preview
and card drop handlers. Waiting text is generic although progress already contains
each player's confirmed keep state. The hand container has a native `title` tooltip
inherited by child cards. Both issues are localized; existing server state and
interaction handlers can be retained without a prerequisite refactor.

## Requirements

1. After the viewer keeps, name players whose server-confirmed progress has
   `kept=false`, excluding the viewer and players who kept. Use singular/plural
   wording and natural lists, preserving supplied names as text.
2. Update when a pending player keeps or their displayed name changes, and restore
   from snapshots after reload/reconnect. Do not infer completion from connection
   presence. Disconnected players remain named until they keep.
3. Use a polite, atomic status region. Unrelated snapshots with the same pending
   names must not rewrite its contents or repeatedly announce them. If an opening
   snapshot temporarily contains no pending peers, say `Waiting for the game to begin.`;
   server status alone determines when gameplay replaces opening.
4. Retain opening-hand progress, artwork/inspection access and existing kept,
   pending and disconnected restrictions. Do not expose hidden cards or issue
   commands to compute or refresh waiting names.
5. Hovering hand cards must not inherit the native `Drop to the owner’s hand`
   tooltip. Remove the hand container's title while preserving artwork tooltips,
   keyboard inspection, accessible card names and drag/drop handlers/feedback.

## Contracts and data

Read existing player names, seats and mulligan progress; no wire, persistence or
command changes. Keep raw names rendered as text, not HTML.

## Technical constraints

Use existing React rendering, browser list formatting and status semantics. No
new dependency, connection or global announcement state.

## Invariants

- Server state controls readiness; reading/presenting it never sends a command.
- Hand visibility and permitted drops retain their established rules.

## Edge cases and failures

One/several pending peers, viewer at another seat, repeated names, long/special
names, all peers kept, unrelated snapshots, redraw progress, reload, disconnection,
missing artwork, invalid drops and pending/disabled actions.

## Migration and compatibility

Frontend-only behavior using existing protocol data. No migration/version bump.
Keep this independent of unmerged R24 and surveil/mill implementation branches.

## Acceptance criteria

- [x] Waiting status names only pending peers and updates accessibly in both formats.
- [x] Unchanged snapshots do not rewrite the status; reload/transition behavior holds.
- [x] Native hand tooltip is absent while artwork previews and allowed drops work.
- [x] Relevant frontend/browser checks, build and documentation validation pass.

## Verification

User confirmed comprehensive coverage on 2026-10-22: both formats; one/multiple
pending players; keep/name/snapshot/reload updates; accessible status without
repeated unchanged mutations; native-title absence, artwork previews, keyboard
inspection and preserved drop behavior/guards. Use behavioral browser red before
production changes, followed by focused tests and the relevant frontend/browser
profiles with task slug `opening-hand-feedback`.

Results on 2026-10-22:

- Browser red tests demonstrated the missing named status and inherited hand
  title before production changes. A subsequent narrow-screen regression
  demonstrated overflow with an unbroken 40-character name before adding wrapping.
- Seven formatter unit cases cover list grammar, seat ordering, duplicate names,
  viewer exclusion and missing/all-kept progress. Twelve new browser cases cover
  both formats, confirmation timing, unchanged DOM announcements, name updates,
  reload/reconnect, narrow screens, artwork/inspection and drop guards.
- `./scripts/verify.sh --task opening-hand-feedback frontend` passed: 291 unit
  tests, 41 analyzer tests, lint, formatting, analysis and TypeScript/Vite build.
  Analysis reports nine advisory ESLint warnings and zero Knip issues.
- `./scripts/verify.sh --task opening-hand-feedback browser` passed all 206 cases,
  including the real Commander and Standard gameplay/privacy/reconnect flows.
- Final implementation evidence is in
  `/tmp/mtgcmd-verify-opening-hand-feedback-lfkocl2z/manifest.json` (frontend) and
  `/tmp/mtgcmd-verify-opening-hand-feedback-m8_i5rc1/manifest.json` (browser).
  Both bind unchanged implementation snapshots before this results-only
  documentation update; they are not rebound to later documentation edits.
- Native tooltip removal is verified structurally by absence of inherited
  `title`, alongside sustained artwork hover tests; operating-system tooltip
  rendering is not asserted directly.
- No Java source changes or Docker rebuild/restart. Backend unit/PIT runs and
  deployment are outside this frontend-only increment.

## Architecture decisions

No new ADR: localized presentation changes retain the existing ownership and
contracts in [ADR-0013](../docs/adr/0013-app-composition-boundaries.md).

## Open questions

None.
