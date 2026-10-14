# Typed service failures and diagnostics

## Metadata

- Status: Completed
- Priority: Medium
- Priority rationale: Preserve diagnostic evidence and classify service failures without changing player error responses.
- Depends on: [Deck import refactor](deck-import-refactor.md), [Multi-face catalog lookup](catalog-multiface-lookup.md), [Standard tables](standard-tables.md)

## Summary

Introduce a small service exception hierarchy, retain original causes, and
record handled HTTP/WebSocket failures and recovered catalog refresh failures.

## Goals

- Identify timeout, unavailability, invalid response, and interruption by type.
- Preserve classification and causes through adapter translations.
- Make handled failures observable without logging ordinary validation errors.

## Non-goals

- Catalog/game-engine decomposition, new logging infrastructure, retries, or
  changes to HTTP status policy and application wire shapes.

## Background

The approved PMD audit found cause loss in transport/provider adapters and
unlogged exceptions in transport handlers. The focused follow-up audit confirmed
that Moxfield wrapping can erase subtype information and that Standard's broad
refresh catch can swallow interruption. Both are directly within the approved
diagnostics work. Logging at handling/recovery points avoids duplicate logs from
each propagation layer; recovered failures need their own local diagnostic.

## Requirements

### Functional requirements

1. CardServiceException is an abstract IllegalArgumentException subtype with
   timeout, unavailable, invalid-response, and interrupted concrete subclasses.
2. Each subtype retains its cause and supports translation to an adapter's
   existing player message without losing its concrete classification.
3. JsonHttp classifies deadlines, connectivity/status failures, oversized
   responses, and interruption. Retain cancellation, no redirects, size bounds,
   pacing, and interrupt restoration.
4. Moxfield translates known service failures into its existing messages and
   classifies malformed/unsupported provider data as invalid responses. Narrow
   catches to known failures; unexpected programming errors must propagate.
5. Catalog transport/parsing failures retain classification. Standard refresh
   failures may use cached definitions only under existing fallback conditions;
   interruption always propagates. Recovered failures produce one warning.
6. HTTP and WebSocket boundaries log service failures as warnings and unexpected
   failures as errors. Ordinary validation errors do not produce failure logs.
7. Socket view/send/transport failures and failed recovery/cleanup attempts must
   remain observable. Propagated failures are not logged by each adapter.
8. Preserve causes when translating existing URL, quantity, and format parsing
   exceptions without classifying those input errors as service failures.

### Non-functional requirements

- Use existing SLF4J/Logback dependencies. Logging context contains operation and
  room/connection identifiers, not raw decklists, credentials, or snapshots.
- Causes and traces remain server-side; client messages and HTTP status policy
  for expected service and validation failures remain unchanged.

## Contracts and data

Fetcher documents typed operational failures. The hierarchy is internal Java
behavior and remains compatible with IllegalArgumentException handlers. Original
causes may be chained through subtype-preserving adapter translation. Native
provider status failures have no underlying exception and may have a null cause.

## Technical constraints

Keep existing module/transport boundaries. Avoid a general exception factory,
retry framework, or global exception-handler migration.

## Invariants

- No exception stack or internal cause is returned to players.
- Cache recovery does not invent definitions or swallow interruption.
- Message translation does not flatten typed failures into the base type.
- Ordinary input errors remain separate from operational service failures.

## Edge cases and failures

Transport timeouts include both asynchronous HTTP timeout and future-wait timeout.
Interruptions during pacing or waiting cancel active work and restore the flag.
Malformed JSON and unexpected provider shapes are reported without logging raw
responses as context. Failed socket cleanup must not erase the original failure.
Cached fallbacks retain existing legality observation timestamps and warnings.

## Migration and compatibility

No data migration or frontend change. Expected error statuses and messages stay
compatible. Unknown programming exceptions are no longer converted into provider
outages or swallowed by the catalog refresh fallback.

## Acceptance criteria

- [x] All four failure types retain causes and survive provider translation.
- [x] Transport cancellation, interruption, and size/redirect bounds pass.
- [x] Cached fallbacks remain compatible and interruption propagates.
- [x] HTTP/WebSocket diagnostics and responses match their failure category.
- [x] Recovered failures and failed socket cleanup are observable.
- [x] Ordinary validation remains quiet and existing backend tests pass.

## Verification

Confirmed target: comprehensive coverage, selected by the user during
implementation. Add tests before production changes; demonstrate failures for
missing causes, classification, and logging while existing unrelated assertions
pass. Exercise all subtype translations, both timeout mechanisms, connection and
status failures, body bounds, interruptions/cancellation, schema/JSON failures,
cache-only recovery versus uncached failure, and interruption propagation. Capture
Logback events to verify levels, original causes, no duplicate propagation logs,
normal validation silence, and socket send/recovery/cleanup failures.

Run focused catalog/web tests during TDD, then Spotless and the complete backend
verify lifecycle (including advisory PMD and jar packaging). Gameplay/browser
checks are unnecessary because no frontend behavior changes.

Executed on 2026-10-14: initial focused regression tests failed for absent typed
failures, discarded causes, and missing HTTP/WebSocket diagnostics. Expanded
tests then demonstrated missing catalog recovery logging, swallowed interruption,
programming errors masked by fallback, and missing socket recovery/cleanup
diagnostics. The green focused run passed 153 cases. The final suite added actual
Spring MVC routing for all four typed failures and transport cleanup failure
coverage. `spotless:check verify` passed all 201 backend tests with zero failures,
errors, or skips, generated PMD reports without processing/configuration errors,
and packaged the executable jar. `git diff --check` passed.

PMD still reports three PreserveStackTrace findings on direct-cause rethrows and
the subtype-preserving withMessage call. Tests verify original exception identity
and complete cause chains on these paths; no broad suppression was added.
The original sandboxed test attempt could not bind local HTTP ports, so the
red/green and final verification runs used approved escalation. Frontend/browser
tests and live provider calls were not run for this backend-only change.

### Moxfield stack-trace analysis follow-up

Keep the existing subtype-preserving `withMessage` translation and its original
exception cause chain. A documented method-local `PMD.PreserveStackTrace`
suppression on `MoxfieldDeckSource.fetchRoot` acknowledges that PMD cannot infer
the receiver method's cause-preserving contract. The rule remains enabled
elsewhere; future edits to this method must preserve causes in both catch paths.
No runtime behavior or public contract changes, and no new ADR is required.

Retain the confirmed comprehensive coverage target. The existing parameterized
`moxfieldTranslationPreservesEveryFailureCategoryAndCause` test covers all four
failure subtypes, contextual messages, original exception identity, and nested
causes. No new tests are needed for this analysis-only annotation change.

Executed on 2026-10-14: `DeckImporterTest` passed, followed by
`spotless:apply spotless:check verify` with all 554 backend tests passing and no
failures, errors, or skips. The regenerated PMD report has no active
`PreserveStackTrace` findings and records the Moxfield finding as suppressed,
with no analysis errors. `git diff --check` passed. Frontend/browser checks and
live provider calls were unnecessary for this annotation-only change.

## Architecture decisions

No new ADR: this is a reversible internal hierarchy and diagnostic improvement
using established adapters, transport handlers, and logging dependencies.
[ADR-0001](../docs/adr/0001-authoritative-game-state.md) remains authoritative for
private views and state recovery.

## Open questions

None.
