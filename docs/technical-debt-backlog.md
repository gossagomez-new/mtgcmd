# Technical debt backlog

- Created: 2026-10-21.
- Refined: 2026-10-22; delivery boundaries clarified without refreshing analysis measurements.
- Status: TD01 implemented and verified locally; other items remain open. Creating this backlog does not authorize implementation.
- Audited revision: `0f4a41ed2d63f7564cfe18d64fe477b706aed479`, integrated into `main` as `d2ba354` through PR #24.
- Scope: Backend/frontend source review, JaCoCo, PMD, full-backend PIT, and ESLint/Knip findings.

## Purpose and boundaries

Improve behavioral assurance, browser resilience, accessibility, and maintainability.
Prioritize demonstrated costs and test gaps over warning counts. A surviving
mutation is not automatically a production defect or a missing test: redundant
guards, valid-domain equivalence, and separately executed browser coverage require
interpretation.

This document records candidate work and completion criteria, not approved
implementation specifications or architectural decisions. Before starting an item,
refresh its evidence, audit the affected code, create a scoped specification where
required, confirm the verification target, and evaluate ADR requirements under
[AGENTS.md](../AGENTS.md). Verification expectations below are recommendations,
not already confirmed coverage choices. Existing wire/persistence compatibility,
privacy, transaction ownership, revisions, and idempotency remain constraints.

Coordinate product work through the [playtest backlog](playtest-backlog.md) and
reusable tooling through [automation proposals](automation-proposals.md). Those
backlogs are separate; this one does not authorize their features or new tooling.

## Evidence baseline

These are measurements at the audited revision, not live project health or gates.
The normal build reports were preserved before PIT's clean build. PMD has no
independent source fingerprint; frontend report provenance was current at review.

| Evidence                              | Observed result                                                                           | Interpretation                                                                                            |
| ------------------------------------- | ----------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------- |
| Backend verification                  | 739 Java tests passed                                                                     | Execution success does not prove every postcondition is asserted                                          |
| Frontend verification                 | 231 unit tests, 41 analyzer fixtures, 149 browser tests passed                            | Browser checks include a separately launched backend JVM                                                  |
| JaCoCo 0.8.15                         | 1,772/1,819 lines (97.42%); 770/850 branches (90.59%)                                     | Strong execution coverage, with localized branch/failure gaps                                             |
| PMD 7.17.0                            | 342 production, 859 test, 0 other active findings; 39 suppressed; no analysis diagnostics | Counts are advisory, not defect totals                                                                    |
| Frontend analysis                     | 0 ESLint errors, 8 production warnings, 0 test/other warnings, 0 Knip issues              | Seven function-length findings and one complexity finding                                                 |
| Full PIT 1.30.1 / JUnit adapter 1.2.3 | Exit 0; 989 mutations: 846 killed, 130 survived, 10 uncovered, 3 timed out                | 85.54% assertion-based kills; 85.84% detected including timeouts (86% rounded); 87% rounded test strength |

The completed full PIT run used Java 25, default production/test globs, DEFAULTS
mutators, and one worker. Its results supersede the earlier interrupted attempt
and focused runs for whole-backend measurement. There were no execution, memory,
non-viable, or unfinished mutation statuses in its complete XML. Timeouts are
separate from assertion-based kills.

Backend browser-server execution is outside the normal JaCoCo/PIT instrumented
JVMs. For example, browser tests boot the real Spring application even though
configuration factories and handshake code have weak mutation-visible coverage.
No comparable frontend coverage or mutation score was measured. This review did
not establish load capacity, perform penetration testing, or audit dependencies.

Raw reports are ignored build artifacts and can disappear on clean. Preserve
before/after reports outside `backend/target` when executing an item. Relevant
commands and report locations are documented in the [backend guide](../backend/README.md#code-coverage),
[mutation guide](../backend/README.md#mutation-testing), and
[frontend guide](../frontend/README.md#static-analysis).

## Priority and effort

Use the [audit framework](../skills/codebase-design-audit/references/review-framework.md):
priority = severity points + confidence points + ease-of-remediation points.
High is 70–84, Medium 50–69, Low 23–49; no Immediate item was established.

Severity: High 45, Medium 30, Low 15. Confidence: High 15, Medium 10.
Effort: S 20, M 14. S means a few files using established patterns; M means
several components or moderate test changes. Estimates include relevant tests,
integration, documentation, and compatibility checks; they are not time promises.
Cheaper fixes receive more points. Scheduling also considers risk and related
feature work, so a cheap presentation change need not precede transport assurance.

## Prioritized register

TD01 is Done locally (merge/release pending); all other entries
are Open. Investigation entries require diagnosis before proposing a fix.

| ID   | Area  | Work                                                     | Evidence type                           | Severity | Confidence | Effort | Calculation / priority    | Coordination                                             |
| ---- | ----- | -------------------------------------------------------- | --------------------------------------- | -------- | ---------- | ------ | ------------------------- | -------------------------------------------------------- |
| TD01 | BE    | Counter command postconditions and permission tests      | Demonstrated test gap                   | High     | High       | M      | 45 + 15 + 14 = 74, High   | TD10 for random-result variance                          |
| TD02 | BE    | Object command permissions, limits, and ordering tests   | Demonstrated test gap                   | High     | High       | M      | 45 + 15 + 14 = 74, High   | Shared fixture patterns with TD01                        |
| TD03 | BE    | Persisted inspection cleanup assertions                  | Demonstrated test gap                   | Medium   | High       | S      | 30 + 15 + 20 = 65, Medium | Complete before replay relies on cleanup                 |
| TD04 | BE    | Room response and durable-event assertions               | Demonstrated test gap                   | Medium   | High       | S      | 30 + 15 + 20 = 65, Medium | Preserve existing rollback/retry checks                  |
| TD05 | BE    | Required view metadata assertions                        | Demonstrated test gap                   | Medium   | High       | S      | 30 + 15 + 20 = 65, Medium | Recommended characterization before TD14                 |
| TD06 | FE    | Safe optional browser preferences                        | Source-supported failure path           | Medium   | High       | S      | 30 + 15 + 20 = 65, Medium | Independent                                              |
| TD07 | FE    | Zone-viewer keyboard and modal lifecycle                 | Source-supported accessibility gap      | Medium   | High       | S      | 30 + 15 + 20 = 65, Medium | Preserve nested details and search lifecycle             |
| TD08 | FE    | Decompose PlayerArea by cohesive regions                 | Maintainability finding                 | Medium   | High       | S      | 30 + 15 + 20 = 65, Medium | Opportunistic with battlefield UI work                   |
| TD09 | BE    | HTTP subscriber terminal-event assertions                | Coverage and mutation gap               | Medium   | High       | S      | 30 + 15 + 20 = 65, Medium | Preserve established client ownership                    |
| TD10 | BE    | Diagnose timeouts and classify survivor equivalence      | Investigation                           | Medium   | Medium     | S      | 30 + 10 + 20 = 60, Medium | Can proceed independently                                |
| TD11 | BE    | Successful WebSocket and presence contracts              | Coverage and mutation gap               | Medium   | High       | M      | 30 + 15 + 14 = 59, Medium | Coordinate fixtures with TD12                            |
| TD12 | BE    | Session reuse, handshake, and startup verification scope | Mixed test gap and measurement boundary | Medium   | High       | M      | 30 + 15 + 14 = 59, Medium | Choose verification approach before tooling changes      |
| TD13 | BE/FE | Typed command contracts                                  | Maintainability finding                 | Medium   | High       | M      | 30 + 15 + 14 = 59, Medium | Incrementally with R15/R19/R11                           |
| TD14 | BE    | Detached immutable read projections                      | Maintainability finding                 | Medium   | High       | M      | 30 + 15 + 14 = 59, Medium | Relevant TD05 characterization before production changes |
| TD15 | BE    | Semantic public-log assertions                           | Demonstrated test gap                   | Low      | High       | S      | 15 + 15 + 20 = 50, Medium | Coordinate with R11                                      |
| TD16 | BE    | Contextual PMD convention cleanup                        | Maintenance/triage                      | Low      | High       | M      | 15 + 15 + 14 = 44, Low    | After higher-value behavioral work                       |

Coordination notes are planning relationships, not additional specification
dependencies. No item technically requires a global-state library, microservice
split, complete immutable-engine migration, or new coverage threshold.

## Task readiness and shared work

The register retains stable IDs and the original priority scores. Subtasks below
define delivery boundaries, not new findings or additional implementation approval.
Before marking an item Ready, record its refreshed source/report revision, exact
behavioral scope, exclusions, approved specification and verification target where
required, and any unresolved decision. Do not refresh the entire analysis suite
when focused evidence is sufficient.

| Related items              | Ownership and sequencing                                                                                                                                                                                                                                     |
| -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| TD01 / TD10                | TD01 owns deterministic dice assertions and any necessary test seam. TD10 owns diagnosis and classification of random-result variance and timeouts; reuse TD01 evidence instead of creating a second random-test mechanism.                                  |
| TD03 / replay R17          | TD03 establishes existing persisted cleanup guarantees. Replay consumes those guarantees later; replay behavior is outside this task.                                                                                                                        |
| TD05 / TD14                | TD05 owns required metadata/privacy characterization. The relevant tests must pass before TD14 changes read ownership; they may be delivered in the same PR. Completion of all TD05 work is not a prerequisite. Reuse those tests as compatibility evidence. |
| TD11 / TD12                | TD11 owns command delivery, broadcasts, and presence. TD12 owns guest identity, handshake admission/attributes, and startup verification scope. Reuse transport fixtures without duplicating scenario ownership.                                             |
| TD13 / TD15 / product work | Typed commands and log assertions may accompany the named product features, but each debt item must retain a bounded acceptance scope and separate completion evidence.                                                                                      |

TD07 and TD08 retain provisional S estimates. Confirm overlay focus behavior and
component state ownership during their scoped audits; revise effort and scheduling
if the work requires broader lifecycle changes. This refinement does not raise
their priority solely because their eventual implementation could be larger.

## Item definitions

### TD01 — Protect counter command behavior

- Status: Done locally, 2026-10-22; merge/release pending.
- Specification: [Counter action behavioral verification](../spec/counter-actions-verification.md).
- Confirmed target: Comprehensive coverage, 2026-10-22.
- Branch: `test/counter-command-assurance`; PR: not created.
- Result: 12 new parameterized cases across both formats; 751 backend tests passed. Focused PIT killed all 24 CounterActions mutations, versus 13 killed/11 surviving in the saved baseline. No production changes.

Evidence: [CounterActions](../backend/src/main/java/dev/mtgcmd/game/CounterActions.java)
killed 13/24 mutations; removing counter updates, commander ownership checks, or
range/capacity guards can survive. Execution coverage is approximately 96% of lines.

Acceptance criteria:

- Assert exact player/card counter and commander-cast updates, including explicit value and delta paths.
- Reject unknown/opponent commander keys and numeric/capacity violations without state, revision, or log changes; preserve valid corrections and updates at capacity.
- Verify both formats, including Standard commander rejection, and deterministic dice endpoints/arguments.
- Focused PIT demonstrates detection of the relevant incorrect updates and guards; explain remaining survivors.

Scope/trade-off: M, several command cases and existing both-format fixtures.
Prefer behavioral assertions over reproducing implementation branches. This is
test-strength work; production changes require a demonstrated original-code defect.

### TD02 — Protect object command invariants

Evidence: [ObjectActions](../backend/src/main/java/dev/mtgcmd/game/ObjectActions.java)
has 100% JaCoCo line coverage but killed only 11/24 mutations, with 12 survivors
and one uncovered mutation.

Acceptance criteria:

- Assert token creation/editing, copy characteristics, ability creation/order, and resolution of exactly the selected ability.
- Reject non-token edits, forbidden private/hidden copies, wrong ability controllers, unknown abilities, and object/stack capacity violations atomically.
- Verify independent owners/controllers and both formats without changing assisted-tabletop rules.
- Rerun focused PIT and connect changed assertions to the reported permission/postcondition mutants.

Scope/trade-off: M, several workflows and permission/limit fixtures. Keep tests
grouped by domain invariant rather than creating a class for every mutation.

### TD03 — Assert persisted inspection cleanup

Evidence: [BoundedLibraryTest](../backend/src/test/java/dev/mtgcmd/game/BoundedLibraryTest.java)
checks filtered views; [GameAccess](../backend/src/main/java/dev/mtgcmd/game/GameAccess.java)
can hide sessions even when cleanup is removed. LibraryInspections has 12 survivors;
related cleanup-call removal also survives in lifecycle and transition handlers.

Acceptance criteria:

- Assert affected stored entries are removed on revocation, concession, finish, library moves, shuffle, and scry.
- Verify actor/target distinctions, preservation of unrelated sessions, and no cleanup on rejected operations.
- Verify cleanup through serialization/recovery while retaining existing raw-view privacy assertions.
- Focused mutations distinguish missing stored cleanup from merely hidden metadata.

Scope/trade-off: S, existing domain fixtures plus focused persistence coverage.
Stored-map assertions are justified by the persisted cleanup contract; they do
not establish a current disclosure bug. Coordinate with replay R17 before reuse.

### TD04 — Assert room responses and action history

Evidence: [RoomStore](../backend/src/main/java/dev/mtgcmd/store/RoomStore.java)
has seven survivors, including removed create/join events and empty join/load responses.

Acceptance criteria:

- Successful responses contain the correct room, seat, revision, and relevant resulting state.
- Required creation/join events are durably recorded with the expected revision/seat.
- Duplicate operations preserve established idempotency semantics; rejected/failed operations leave no partial history.
- Existing rollback/concurrency tests remain passing and focused PIT detects the targeted omissions.

Scope/trade-off: S, extend PostgreSQL fixtures. Database assertions are appropriate
for repository guarantees; avoid pinning incidental SQL statements or map order.

### TD05 — Assert positive view metadata

Evidence: five [GameViewMapper](../backend/src/main/java/dev/mtgcmd/game/GameViewMapper.java)
survivors affect owner warnings and commander projection.

Acceptance criteria:

- Assert owner warnings are present and unauthorized viewers do not receive them.
- Assert commander identity, owner association, casts, and per-card keys where visibility permits; assert concealment where it does not.
- Cover both formats and relevant zone/control changes without changing the wire shape.
- Focused PIT detects lost required metadata, alongside existing privacy checks.

Scope/trade-off: S, semantic field-value fixtures. Positive and negative assertions
must complement each other; a survivor is not automatically a privacy leak.

### TD06 — Make optional browser preferences failure-tolerant

Evidence: [App](../frontend/src/App.tsx) reads localStorage during render;
[useTableSession](../frontend/src/useTableSession.ts) writes after server success
but before accepting/navigating to the room. Storage exceptions can interrupt either path.

Acceptance criteria:

- Unavailable/throwing storage does not prevent rendering or successful room entry.
- Preference write failure does not turn a successful create/join into an apparent server failure or repeat the mutation.
- Available storage still restores/saves the player name; fallback behavior remains usable in memory.
- Verify blocked reads/writes and successful create/join through appropriate unit/browser checks.

Scope/trade-off: S, a guarded preference helper or local try/catch plus callers.
Do not change guest-cookie identity or other session storage as incidental scope.

### TD07 — Unify zone-viewer keyboard behavior

Evidence: [ZoneViewer](../frontend/src/table/ZoneViewer.tsx) uses a visual modal
and dialog role without native focus/Escape handling; [Modal](../frontend/src/table/Modal.tsx)
already provides much of the required lifecycle.

Acceptance criteria:

- Supply initial focus, modal focus containment, Escape/visible-close behavior, and sensible focus restoration.
- Preserve private-search closure, local filtering, original positions, current permissions, and pending/disconnected guards.
- Nested card details close back to the viewer; keyboard actions do not leak through to background controls.
- Verify both formats, keyboard navigation, snapshots, nested overlays, and dismissal behavior.

Scope/trade-off: S, reuse Modal where suitable. A deliberately non-modal viewer is
an alternative requiring a product decision, not an assumed implementation choice.

### TD08 — Separate PlayerArea presentation regions

Evidence: [PlayerArea](../frontend/src/table/CardViews.tsx) reports 127 lines and
complexity 15, combining identity/life, presence, focus controls, zones, battlefield, and counters.

Acceptance criteria:

- Give independently changing identity, shortcut, and counter regions cohesive boundaries with explicit props.
- Preserve active/conceded appearance, life/counter values, format-specific zones, permissions, focus behavior, and live updates.
- Keep card interactions and state lifetimes stable; no new global state or connection churn.
- Existing browser flows pass and analysis is reviewed for actual structural improvement, without suppressions or threshold increases.

Scope/trade-off: S, a few components/selectors with presentation characterization.
Do not split individual JSX fragments solely to cross a line-count threshold.

### TD09 — Verify HTTP subscriber terminal events

Evidence: [JsonHttp.BoundedBody](../backend/src/main/java/dev/mtgcmd/catalog/JsonHttp.java)
has three survivors; onError lines 214–218 were unexecuted in JaCoCo.

Acceptance criteria:

- Verify upstream failures reach the body consumer and retain appropriate failure meaning/cause.
- Verify repeated/late terminal signals and post-completion data do not corrupt completion or resume consumption.
- Preserve response-size limits, cancellation, interruption, timeout translation, and owned/borrowed client lifecycle.
- Relevant subscriber tests and focused nested-class PIT validate the assertions; classify redundant guards separately.

Scope/trade-off: S, targeted Flow/subscriber fixtures. Avoid a transport rewrite
or unsupported publisher behavior introduced solely to increase coverage.

### TD10 — Diagnose timeouts and equivalent/redundant mutants

Evidence: the complete run timed out two LifecycleActions.concede mutations and
one [GameLookup.player](../backend/src/main/java/dev/mtgcmd/game/GameLookup.java)
boundary mutation. Dice results varied across runs; some parsing guards are redundant.

Acceptance criteria:

- Preserve original XML/logs and reproduce relevant cases with focused scope and unchanged settings before classifying them.
- Diagnose the GameLookup timeout; do not assume a production hang or assertion kill.
- Document lifecycle-loop behavior, valid-domain equivalence, and duplicated validation with source/invariant evidence.
- Make random-boundary verification deterministic with TD01; do not alter production solely to kill equivalent mutants.
- Limit the initial investigation to the three reported timeouts and random-result variance relevant to TD01. For each case, record reproduction settings/results, source-based explanation, classification (or unresolved status), and a recommended follow-up. An unresolved diagnosis is a reported outcome, not permission to exclude a mutant.

Scope/trade-off: S investigation estimate with Medium confidence; revise effort
after reproduction if necessary. No automatic timeout-policy change, exclusion,
score gate, or promise to classify all 130 survivors is approved here.

### TD11 — Strengthen WebSocket success and presence contracts

Evidence: [GameSocket](../backend/src/main/java/dev/mtgcmd/web/GameSocket.java)
killed 20/34 mutations; ack/broadcast removals are uncovered. Presence, room
filters, seat mapping, and the message limit have survivors; JaCoCo branch coverage is 65%.

Acceptance criteria:

- Valid commands produce the correct ack ID/view and exactly the intended durable mutation; rejection retains established error/recovery behavior.
- Broadcasts/presence reach only the correct room and use each recipient's filtered view.
- Cover connect/disconnect, duplicate seats, message-size configuration, and existing transport failure handling.
- Focused PIT detects targeted delivery/presence regressions; retain real browser integration coverage.

Scope/trade-off: M, several transport fixtures and audiences. A focused JUnit
suite provides fast mutation visibility; instrumenting browser JVMs is a larger
alternative and is not implied by this item.

### TD12 — Verify session reuse and handshake/startup boundaries

Evidence: [GuestSessions](../backend/src/main/java/dev/mtgcmd/web/GuestSessions.java)
has weak reuse/missing-guest assertions. [AppConfiguration](../backend/src/main/java/dev/mtgcmd/web/AppConfiguration.java)
has surviving factory mutations and four uncovered handshake-interceptor mutants;
browser TestServer boots real Spring outside the instrumented JVM.

Acceptance criteria:

- Verify valid guest reuse without unintended identity replacement, missing/invalid guest rejection, and existing cookie properties.
- Verify handshake guest/room/membership checks and successful seat/guest/room attributes, retaining origin enforcement.
- Decide and document whether existing out-of-process startup coverage is sufficient or a focused instrumented context test adds value.
- Distinguish accepted measurement boundaries from real missing assertions; record actual verification scope and results.

Scope/trade-off: M, authentication/handshake fixtures and a verification-scope
decision. Do not create shallow bean-getter tests solely for mutation percentages.
New build conventions or instrumentation require separate specification/ADR review.

### TD13 — Introduce typed commands incrementally

Evidence: frontend [Action](../frontend/src/table/types.ts) and backend
[Game.Command](../backend/src/main/java/dev/mtgcmd/game/Game.java) accept arbitrary
command names/dictionaries, allowing payload mistakes to compile.

Acceptance criteria:

- Establish a bounded initial command family with TypeScript name/payload correlation and typed backend decoding where it meaningfully hides validation complexity.
- Demonstrate compile-time rejection of mismatched frontend payloads while retaining authoritative runtime validation of untrusted input.
- Preserve wire names/defaults, errors/precedence, revisions, retries, and compatibility.
- Record a staged migration boundary and run appropriate compile, runtime, protocol, and browser checks.

Scope/trade-off: M for an initial family, larger for all commands. Coordinate
with casting R15, hand shortcut R19, and logging R11. Command-specific builders
are an alternative. Contract/module-boundary decisions need evaluation and ADR
approval when selected; this backlog does not finalize a hierarchy or dependency.

### TD14 — Return detached immutable read data

Evidence: [GameViewMapper](../backend/src/main/java/dev/mtgcmd/game/GameViewMapper.java)
aliases game logs, counters, and abilities in read maps. Internal callers can
mutate the supplied Game through its view; serialization limits current exposure.

Acceptance criteria:

- Returned nested collections/objects cannot mutate the supplied game, and later game changes do not silently change an existing read snapshot.
- Preserve the existing JSON shape, nullable/legacy fields, permissions, and required metadata characterized by TD05.
- Establish passing TD05 characterization for the affected metadata/privacy contracts before production changes. Those tests may be delivered within the TD14 PR; unrelated TD05 work need not be complete.
- Verify nested mutation attempts, detachment, serialization compatibility, and privacy in both formats.
- Keep RoomStore transaction ownership and existing persistence unchanged.

Scope/trade-off: M, deep-copy/view-model changes plus compatibility checks.
Immutable view records and defensive nested copies are alternatives; merely
wrapping the outer map is insufficient. This is not full immutable Game or undo
work. Evaluate the selected boundary decision for an ADR before implementation.

### TD15 — Assert useful privacy-safe log semantics

Evidence: empty descriptions for look/exile/close and changes to other log content
survive. Tests often forbid private names without requiring useful public information.

Acceptance criteria:

- Logs retain required operation/count/target meaning where specified and never expose private identities or scry arrangement.
- Verify successful actions, rejection/no-extra-entry behavior, and established revision/idempotency rules.
- Prefer semantic assertions over exact punctuation or incidental prose.
- Coordinate broader event-schema or logging changes with playtest R11; do not introduce them implicitly.

Scope/trade-off: S, focused assertions across current commands and both formats.
Structured events are a larger alternative requiring separate design decisions.

### TD16 — Triage and clean conventions in focused batches

Evidence: PMD is dominated by model traversal, fixture conventions, guard clauses,
field ordering, and similar rules. Several FE findings are length-only.

Acceptance criteria:

- Inspect rule/file context, separate mechanical improvements from intentional patterns, and preserve behavioral evidence before edits.
- Apply established explicit-type/final/naming/braces conventions where useful; review documentation and exception boundaries selectively.
- Add missing `@Override` annotations where methods implement or override inherited contracts (`MissingOverride`: 13 production findings in the saved report). For example, annotate `CardCatalog.resolve(List<String>)`; confirm the compiler accepts each annotation without changing method signatures or behavior.
- Move fields before methods, constructors, initializers, and nested types where flagged by `FieldDeclarationsShouldBeAtStartOfClass` (26 production findings in the saved report). Start with `DeckImporter`, whose nested interfaces and record precede its instance fields. Preserve field initializer order and dependencies; review any reordering that could affect initialization behavior separately.
- Retain justified identity comparisons, request synchronization, borrowed-resource ownership, transaction-owned/local maps, and multi-assert invariant tests.
- Retain `OnlyOneReturn` with `allowGuardIfs=true` after upgrading PMD from the reported 7.17.0 to a compatible version supporting the property (introduced in 7.25.0). Review upgrade compatibility and analysis deltas separately from source cleanup; do not configure an unsupported property.
- Verify that leading guard clauses are accepted and multiple non-guard returns remain reported. Review remaining findings individually: guards after setup or other work are not covered by the leading-guard exemption, so preserve readable control flow rather than mechanically nesting code.
- Run scoped format/build/analysis checks and review deltas; do not claim warning reductions establish correctness.

Rule review: prefer configuring `OnlyOneReturn` over excluding it. See the
[rule documentation](https://pmd.github.io/pmd/pmd_rules_java_codestyle.html#onlyonereturn)
and the [7.25.0 release announcement](https://sourceforge.net/p/pmd/news/2026/05/pmd-7250-29-may-2026-released/).
This backlog records the intended follow-up; it does not change the current
ruleset or establish that all 28 production findings are leading guards.

Scope/trade-off: M for bounded cleanup batches, not every warning in the repository.
Begin with the annotation and field-ordering cleanup as a small, separately
reviewable batch. Verify compilation, formatting, relevant tests, and targeted PMD
findings; treat the saved counts as a baseline to recheck, not a guaranteed edit count.
Do not add blanket suppressions, replace every HashMap with ConcurrentHashMap,
remove guard clauses, or split cohesive modules to satisfy thresholds. Rule-policy
changes are separate decisions; further length-only FE decomposition is deferred
until evidence of independently changing responsibilities supports it.

Deliver TD16 through independent subtasks; completion of one does not close the
others. No subtask depends on completing all other debt items.

| Subtask | Deliverable and scope                                                                                                                                                                                                                                    | Completion evidence                                                                                                                                                    | Estimate                                           |
| ------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------- |
| TD16a   | Add missing `@Override` annotations and reorder field declarations safely. Preserve signatures, field initialization order, and behavior; separate any case requiring a behavioral change.                                                               | Compiler/format checks, relevant tests, and focused before/after PMD findings.                                                                                         | S                                                  |
| TD16b   | Upgrade PMD to a compatible version supporting `allowGuardIfs`, then enable it for `OnlyOneReturn`. Preserve advisory analysis and existing report/log behavior; evaluate new defaults/rule changes without silently accepting unrelated policy changes. | Rule fixtures for leading guards versus multiple non-guard returns, successful analyzer/build integration, and categorized report deltas against a preserved baseline. | S provisionally; revise after compatibility review |
| TD16c   | Review the six rules named below and record a disposition for each. Implementation of proposed rule-policy changes is separate work.                                                                                                                     | Evidence-backed dispositions and identified follow-ups; scoped verification for any approved edits, without a warning-count target.                                    | M provisionally for the six-rule review            |

TD16a may proceed independently of the upgrade. TD16b must establish version
support before enabling the property and reviewing the remaining `OnlyOneReturn`
findings as part of TD16b. TD16c can proceed independently against the current
version and covers exactly these rules:

- `UseConcurrentHashMap`
- `CompareObjectsWithEquals`
- `GuardLogStatement`
- `AvoidSynchronizedStatement`
- `DoNotUseThreads`
- `UnitTestContainsTooManyAsserts`

For each rule, record representative source evidence, affected scope, a recommended
disposition (retain, configure, selectively suppress, or propose exclusion), and
the trade-offs. TD16c is complete when all six dispositions are documented and
unresolved implementation or policy decisions have explicit follow-up entries.
It does not require approval or implementation of every recommendation. Other
rules require separately scoped follow-ups rather than extending TD16c indefinitely.
Rule-policy decisions outside the agreed guard configuration remain unresolved
until reviewed. The register's M estimate is provisional for the initial TD16
package; re-estimate its combined scope before scheduling, rather than treating
it as the sum of completed subtasks or a promise to clear all PMD findings.

## Suggested delivery sequence

1. **Command assurance:** TD01, then TD02, as separate focused changes; investigate
   TD10 alongside affected cases. Existing production behavior is expected to remain.
2. **Browser storage resilience:** TD06, preserving successful room entry when
   optional preference storage fails.
3. **State and read contracts:** TD03, TD04, and TD05. Add positive postconditions
   without weakening existing privacy/rollback assertions.
4. **Transport assurance:** TD11, then TD12, prioritizing observable failure,
   delivery, and authentication behavior over instrumentation percentages.

Independent work may be scheduled around that queue: TD07 for accessibility,
TD09 for subscriber assurance, and TD16a for straightforward conventions. TD16b
is a separate tooling change; TD16c is a bounded rule review. None should delay
higher-impact behavioral assurance merely to reduce warning counts.

Coordinate TD13/TD15 with R15/R19/R11 and TD08 with related battlefield work.
TD14 requires the relevant passing TD05 characterization before its production
changes, which may be included in the same PR. These relationships do not block
unrelated product development. Each item still needs its own applicable
specification and confirmed verification target before implementation.

## Completion and tracking

An item is Done only when its approved scope is implemented and verified, its
specification/ADRs are linked where applicable, and remaining limitations are
recorded. Test-only work must show that the intended incorrect mutation fails the
new assertion while original code passes. Behavior-changing fixes need a meaningful
red test before production edits. Preserve relevant before/after evidence and
explain surviving/equivalent/time-out results rather than requiring 100% scores.

When work starts, add the specification, PR, confirmed verification target, and
status to that item. Record implementation separately from merge/release/deployment.
Ordinary debt work does not bump application versions or authorize deployment.

Do not count already completed decompositions, R13/R14 delivery, the fixed
battlefield-space regression, or existing automation capabilities as new debt.
Preserve centralized access/transition policies, transactional mutations, strict
incoming protocol validation, and explicit resource ownership throughout this work.
