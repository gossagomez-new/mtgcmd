# Playtest improvement backlog

Refined on 2026-10-19 from the original local playtest notes
(`feedback_20261015.md`, retained separately from the published backlog).
The test involved actual players using a two-player Standard table. Shared
improvements apply to Standard and Commander where applicable; sideboarding
initially applies to Standard.

This is a planning backlog, not an approved implementation specification. Delivery status is recorded below. Acceptance criteria describe intended outcomes;
individual specifications must settle state transitions, contracts, verification
targets, compatibility and any required architecture decisions. Prioritization
and delivery order below are recommendations.

## Confirmed product decisions

- Every player sees the same turn controls. Only the active player can advance
  phases or end the turn; the host receives no override.
- End turn advances the turn marker and resets the phase. Untapping and drawing
  remain manual.
- Casting and resolution use assisted actions with manual exceptions.
- Card interaction uses a quick-action menu plus a separate details dialog.
- Mulligans support configurable house rules from the first version: a
  configurable free-mulligan count, London-style bottoming, or unlimited
  seven-card redraws. The starting hand remains seven; settings are visible
  before play. The free count applies to the London mode.
- Replay is available only after a finished game, in the same room with the same
  players/decks and an optional between-game sideboarding step. Automatic match
  scoring is deferred.
- Token presentation has low priority.

## Shared table interactions

| ID  | Requirement                          | Acceptance criteria                                                                                                                                                                                                                      |
| --- | ------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| R01 | Double-click to tap/untap            | Double-clicking an editable battlefield card toggles it exactly once. Inspection does not interfere with the gesture. A visible, keyboard-accessible tap action remains available.                                                       |
| R02 | Persistent turn controls             | Current player, turn, phase, Next phase and End turn remain visible while scrolling the board. Unauthorized controls are disabled. The backend rejects advancement by anyone except the active player, including the host.               |
| R03 | Turn-start notification              | The active player receives a visible, accessible notification when their turn begins. Ordinary snapshot updates and reconnects do not repeatedly announce the same turn.                                                                 |
| R04 | Discoverable Untap all               | Players can find and activate Untap all directly from their battlefield. It affects only battlefield cards they control. This improves access to an existing capability.                                                                 |
| R05 | Group tap/untap                      | Players can select multiple editable battlefield cards and explicitly choose Tap selected or Untap selected. Selection is visible. Invalid or stale selections cannot cause partial changes.                                             |
| R06 | Drag cards out of the battlefield    | Editable battlefield cards can be dragged to supported destinations with clear drop feedback. Invalid drops make no change. Existing movement rules, ownership and cleanup still apply.                                                  |
| R07 | Quick-action menu and details dialog | Right-click or a visible action button opens the card's contextual actions. Keyboard access is supported. A separate dialog presents larger artwork, rules text and detailed controls.                                                   |
| R08 | Clear attachment workflow            | Players can select an attachment source and battlefield target, see the relationship and detach it. Use recognizable attachment terminology. Payment, targeting legality and card effects remain player-managed.                         |
| R09 | Life beside opponent identity        | Each opponent's name and life total appear together, including in the focused battlefield view. Both displays update from the same server state.                                                                                         |
| R10 | Focused opponent battlefield         | Players can enlarge one opponent's battlefield in a non-modal popover without losing the table session or blocking the table. Cards remain grouped, use current controller placement and retain the same visibility/editing permissions. |
| R11 | Gameplay-oriented logging            | Logs distinguish casting, resolution, zone movement, turn changes and relevant state changes. Public card names are included when appropriate. Drawing, inspecting or sideboarding must not expose private identities.                   |
| R12 | Improved token presentation          | Token name and characteristics are readable at normal battlefield size and in inspection. Tokens remain visually distinguishable from physical cards. Low priority.                                                                      |

## Gameplay workflows

| ID  | Requirement                     | Acceptance criteria                                                                                                                                                                                                                                                       |
| --- | ------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| R13 | Top-of-library actions          | Provide Look at top N and Exile top N without opening the whole library. The server selects the current top cards. Inspection exposes only the permitted cards to the authorized player.                                                                                  |
| R14 | Scry workflow                   | Players privately inspect the top N cards, choose which remain on top or move to the bottom, and order each group. Confirmation applies the arrangement together. Cancellation preserves library order.                                                                   |
| R15 | Assisted casting and resolution | Cast moves a spell to the stack. Resolve suggests battlefield for permanent spells and graveyard for instants/sorceries, with manual destination overrides. Lands have a separate play action. Direct battlefield placement remains available for effects.                |
| R16 | Configurable mulligans          | Before readiness, expose the free-mulligan count and selected mode: London-style bottoming or unlimited seven-card redraws. Settings are visible to everyone. Players can mulligan or keep. Normal gameplay begins only after everyone keeps. Choices survive reconnects. |
| R17 | Replay a finished game          | Preserve the room, seats, registered decks and agreed settings. Reset gameplay state and require readiness again before dealing. Replay is unavailable during an unfinished game.                                                                                         |
| R18 | Optional Standard sideboarding  | Import and retain a private sideboard separately from the main deck. Between games, players can exchange cards, review validation and become ready. Skipping sideboarding preserves the current deck configuration.                                                       |

## Additional requirements

### R19 — Double-click to play from hand

Applies to Standard and Commander; pending delivery.

- Double-clicking a card in the player's own hand invokes its play action exactly
  once: lands go to the battlefield; spells go to the stack, following R15's
  assisted casting workflow. Spells do not resolve automatically.
- Single-click inspection, keyboard inspection and dragging remain available.
  Double-clicking must not also open inspection or issue a second command.
  A visible, keyboard-accessible Play land or Cast action remains available.
- The shortcut is available only during play and for cards the player is permitted
  to play. Opponent cards, stale cards and disabled/pending actions cannot trigger
  it. Server authority, revisions, idempotency and hidden-information protections
  remain applicable; timing, payment and card-specific legality stay player-managed.
- A rejected action leaves the card in its original zone and shows a useful error.
  Reconnects and snapshot updates must not replay the gesture.
- Verification must cover lands and spells in both formats, exactly-once command
  submission, inspection/drag interactions, permission and pending-state guards,
  rejection and reconnect behavior.

Added from a follow-up user request after the initial playtest backlog. Plan this
with R15 so the shortcut uses the same Play land/Cast behavior as explicit actions;
coordinate gesture handling with R01 and R07.

### R20 — Give back the turn

Applies to Standard and Commander; pending delivery.

- Provide a visible, keyboard-accessible Give back turn button in the persistent
  turn controls. Only the current active player may use it; the host has no
  override. The server enforces this permission.
- The action returns the turn to the player who was actually active immediately
  before the current turn, rather than merely selecting the preceding seat.
  It is unavailable before any turn handoff, outside an ongoing game, while an
  action is pending, or if that previous player has conceded.
- Giving back the turn changes turn tracking only. It does not reverse draws,
  moves, taps, life changes or stack actions. Those remain manual corrections.
  Any phase, turn-number and history restoration policy must be confirmed in the
  implementation specification before this requirement is implemented.
- All players see the updated active player and an explicit public log entry.
  Apply the handoff atomically with existing revision and idempotency safeguards;
  rejected requests leave state unchanged, and reconnects do not repeat it.
- Verification must cover both formats, actual previous-player tracking,
  permissions, the first turn, conceded players, repeated handoffs, stale and
  duplicate requests, reconnects, and preservation of unrelated gameplay state.

Added from a follow-up user request. This extends R02's active-player-only turn
controls. Before implementation, confirm whether repeated Give back turn actions
should alternate between two players or walk backwards through turn history, and
which phase and turn number should be restored. These choices remain unresolved.

### R21 — Distinguish the active player's table colour

Applies to Standard and Commander; included in the card-interaction increment.

- Give the active player's battlefield panel a distinct background/border colour
  compared with inactive players, on both the normal board and focused opponent
  view. Include an Active turn label so colour is not the only cue.
- Derive the indicator from the server's current active seat. Update all views
  after turn changes and reconnects; remove the highlight when the game finishes.
- Preserve readable contrast, card artwork, tap orientation, selection and
  conceded-player presentation. Verify both formats and active-seat transitions.

Added from a follow-up user request during the second increment. Deliver with
R03's turn notification; see the [card-interaction specification](../spec/card-interactions.md).

### R22 — Card actions use a popover

Applies to Standard and Commander; included in the card-interaction increment as
an explicit refinement of R07.

- Open the contextual card-actions menu in a non-modal popover beside its card
  or Actions button, rather than a modal dialog. The rest of the table remains
  available for interaction.
- Support right-click and the visible keyboard-accessible Actions button, menu
  arrow/Home/End navigation, Escape and outside-click dismissal. Restore focus
  appropriately without cancelling an outside click on another control.
- Position the menu within the viewport. Preserve current snapshot permissions,
  pending-state guards, card details access and tap/drag gesture behavior.
- Keep the larger card details view as a separate modal dialog.

### R23 — Enlarged artwork on card hover

Applies to Standard and Commander; delivered by the
[artwork preview increment](../spec/card-artwork-previews.md).

- Hovering a card with available artwork shows a larger artwork tooltip using
  the currently displayed face and the latest server-permitted definition. Offer
  the same preview on keyboard focus and a useful accessible name.
- Do not fetch or infer hidden identities. Cards without permitted artwork do
  not show an image tooltip; known face-down cards follow the viewer's existing
  server-granted definition visibility.
- Keep the preview within the viewport and larger than the normal card image.
  It must not block the originating card or prevent card actions, inspection,
  tapping or dragging. Define hover delay and tooltip placement in its specification.
- Support Escape dismissal and pointer movement into the tooltip; hide it when
  the pointer/focus leaves both surfaces, dragging starts, actions/details open,
  the card disappears, or its permitted artwork becomes unavailable.
- Verify both formats, multiple faces and image fallbacks, hidden/missing art,
  snapshot updates, viewport edges, keyboard access and gesture conflicts.

## Rules and compatibility constraints

- Playing a land does not use the stack. Distinguish casting a spell from other
  ways a permanent enters the battlefield; retain manual exceptions rather than
  requiring every permanent to traverse the stack.
- Standard sideboards allow up to 15 cards. Copy restrictions consider the main
  deck and sideboard together, with applicable exceptions. Preserve the app's
  established warning policy for legality and copy exceptions.
- Recommended mulligan defaults are London mode with zero free mulligans for
  Standard and one for four-player Commander. These defaults remain proposals;
  configurable modes and the free count are confirmed scope.
- Reconnecting must not repeat draws, mulligans or other committed actions.
- Inspection, scry and logs must preserve hidden information. Full-library search
  is not an appropriate substitute for bounded top-of-library inspection.
- Batch tap/untap must apply to the validated selection together or make no
  change. Server authority, existing permissions, revisions and idempotency remain
  applicable to new commands.

Rules references: [Official Comprehensive Rules, effective 2026-09-25](https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.txt),
rules 100.4a (sideboards), 103.5 and 103.5c (mulligans), and 305.1 (lands).

## Current implementation observations

These observations were recorded before the first increment and describe the
repository at that point, not necessarily the version used in the playtest. See
delivery progress below for subsequent changes. The feedback does not identify
the deployed revision.

- Untap all already exists in [YourBattlefield](../frontend/src/table/YourBattlefield.tsx).
  Determine whether the playtest issue was discoverability, availability or behavior.
- Attachments already exist in [PermanentRelationsEditor](../frontend/src/table/inspector/PermanentRelationsEditor.tsx).
  R08 improves the interaction without requiring automated card-rule enforcement.
- Opponent life already appears in [CardViews](../frontend/src/table/CardViews.tsx),
  while [the stylesheet](../frontend/src/style.css) pushes it to the far right.
- CardViews explicitly prevents battlefield cards from being dragged. R06 adds
  a missing interaction rather than merely renaming an existing control.
- [TurnControls](../frontend/src/table/TurnControls.tsx) and
  [LifecycleActions](../backend/src/main/java/dev/mtgcmd/game/LifecycleActions.java)
  currently allow the host to advance phases. R02 deliberately changes that
  authority to the active player only.
- Mulligans, sideboards and match scoring were excluded from the original
  [Standard table specification](../spec/standard-tables.md). Mulligans, replay
  and sideboarding need new specifications; scoring remains deferred.
- Preserve the [assisted tabletop scope](../spec/commander-tabletop.md): players
  adjudicate timing, payment, triggers and card-specific effects.

## Delivery progress

On 2026-10-19, the [first increment](../spec/table-interactions.md) implemented
R01, R02, R04, R06 and R09 for the current table UI in both formats. R09's focused
presentation was deferred to R10. The intentional active-only turn-authority
change is recorded in [ADR-0020](adr/0020-table-interaction-contracts.md).

The [second increment](../spec/card-interactions.md) delivers R03, R05, R07, R08,
R10, R21 and R22 in both formats, including R09's focused-view identity/life
presentation. It includes a non-modal action popover, modal details, explicit
attachments, atomic group tapping, enlarged focused battlefields, turn notices
and active-player colour/labels. Its originally modal battlefield focus is refined
by the [focused battlefield popover specification](../spec/focused-battlefield-popover.md)
at the user’s request. Verification and compatibility details are in
the specification and [ADR-0021](adr/0021-card-interaction-contracts.md).

The [third increment](../spec/card-artwork-previews.md) delivers R23 enlarged
artwork previews on hover and keyboard focus in both formats, preserving privacy,
card gestures, focused views and access to Actions on narrow screens.

The other requirements remain pending, including R19 double-click from hand and
R20 giving back the turn. R20 still requires its turn-history policy to be confirmed.

## Proposed delivery order

1. **Frequent interaction fixes:** R02 persistent turn controls, R01 double-click
   tapping, R04 visible untap-all, R06 battlefield dragging and R09 life placement.
2. **Card interaction:** R07 quick-action menu, R08 attachments, R05 group tapping,
   R10 focused battlefield, R03 turn notifications, R21 active-player colour,
   R22 non-modal card actions, then R23 artwork previews and R20 give back turn
   (after its turn-history policy is confirmed).
3. **Gameplay workflows:** R15 casting/resolution, R19 double-click from hand and
   R11 logging together;
   then R13 bounded library actions, R14 scry and R16 mulligans.
4. **Repeated games:** R17 replay and R18 sideboarding together.
5. **Presentation polish:** R12 token appearance.

R07's gesture design must accommodate R01 without conflicting click behavior.
R14 shares bounded inspection/privacy requirements with R13. R17 must integrate
R16's pregame choices; R18 must preserve main/sideboard separation through replay.
These are planning relationships, not authoritative specification dependencies.

## Traceability to the original feedback

| Original note                             | Refined items                                |
| ----------------------------------------- | -------------------------------------------- |
| Double click for tap/untap                | R01                                          |
| Step button accessibility or pass turn    | R02                                          |
| Turn-start notification                   | R03                                          |
| Library interactions                      | R13, R14                                     |
| Mulligan                                  | R16                                          |
| Restart match                             | R17, refined to replay after a finished game |
| Right-click menu/modal instead of sidebar | R07                                          |
| Enchant/equip interactions                | R08                                          |
| Token appearance, low priority            | R12                                          |
| Same step/phase controls for both players | R02                                          |
| Untap all button                          | R04                                          |
| Group tap/untap                           | R05                                          |
| Drag from battlefield                     | R06                                          |
| Logging focused on what was played        | R11                                          |
| Spells/permanents through stack           | R15, with land and manual exceptions         |
| Opponent life close to name               | R09                                          |
| Focused opponent battlefield modal        | R10                                          |
| Sideboard                                 | R18                                          |
