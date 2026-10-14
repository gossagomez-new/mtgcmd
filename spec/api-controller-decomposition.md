# API controller decomposition

## Metadata

- Status: Completed
- Priority: Medium
- Priority rationale: Separate endpoint, deck workflow, persistence, and error responsibilities.
- Depends on: [Service failure diagnostics](service-failure-diagnostics.md), [Standard tables](standard-tables.md)

## Summary

Replace ApiController with session, room, and deck controllers. Extract deck workflow
coordination, draft SQL/JSON persistence, and explicitly scoped controller advice.
Preserve HTTP behavior and RoomStore transaction ownership.

## Goals

- Reduce CouplingBetweenObjects (22) and TooManyMethods by cohesive responsibilities.
- Keep HTTP translation independent of draft persistence and workflow.
- Preserve all routes, permissions, mutation semantics, error/log contracts.

## Non-goals

- New transactions, schemas, DTO conventions, validation policy, or public API changes.
- Consuming drafts, room-scoping them, changing TTL, or broadcasting failed mutations.
- Global advice for unrelated endpoints, generic controller inheritance, or event frameworks.

## Background

The approved audit traced six dependencies across session/room/deck endpoints, SQL,
JSON, broadcasts, and exception translation. This localized maintainability finding
has medium priority (severity 30 + high confidence 15 + medium effort 14 = 59).
Extraction adds components but isolates real change boundaries. No outside prerequisite
was found. Compatibility details include format validation before controller guest
lookup, deletion before preview serialization, strict SQL expiry comparisons, and
borrowed/reusable guest-scoped drafts. The user approved comprehensive tests and ADR-0007.

## Requirements

### Functional requirements

1. Preserve all /api session, room list/create/join/view, command, preview, and deck-load
   routes, methods, request fields, default/null format semantics, and JSON responses.
2. SessionController owns guest bootstrap; RoomController owns room endpoints and
   commands; DeckController owns HTTP authentication and deck response mapping.
3. DeckImportService owns membership/format checks, importer invocation, draft storage
   coordination, and RoomStore.load. Controllers broadcast after successful join,
   command, or load as before; failure timing remains compatible.
4. DeckDraftRepository owns unchanged SQL/JSON. Save purges this guest's drafts older
   than one day, then serializes/inserts. Lookup selects this guest's draft newer than
   one day; missing/expired returns the existing error. Drafts remain reusable and
   guest-scoped, without room binding or new transactional scope.
5. Preserve operation/error order: create format parsing before guest lookup; preview
   guest/membership then format/import then UUID/purge/serialization/insert; load
   guest/membership then format/commander check then draft read then RoomStore.load.
6. ApiExceptionHandler advice applies only to the three endpoint controllers. Keep
   exact 409/403/400/500 bodies and existing exception specificity. Preserve logger
   category dev.mtgcmd.web.ApiController, WARN for typed service failures, ERROR for
   unexpected failures, no logs for ordinary validation. Do not broaden advice to
   test-support/SPA/security-filter endpoints.
7. RoomStore retains locking, revision/idempotency, persisted atomicity, and recovery.
   Do not replace snapshot maps, card definitions, or duplicate-retry behavior.

### Non-functional requirements

- No hidden-card disclosure, schema/dependency/client changes, or unrelated PMD cleanup.
- Verify scoped advice in composition and avoid relocating coupling into another controller.

## Contracts and data

Wire contracts remain unchanged. Request records move with their controllers.
DeckImportService returns a StoredPreview containing draft ID and DeckImporter.Draft;
the controller creates the same response map. Existing ApiController Java class is
replaced internally; it is not a supported external client contract. Logging category
remains stable even though handler implementation changes.

## Technical constraints

Use existing Spring MVC controller advice and explicit bean configuration. Repository
lives in store; workflow service and controllers live in web. Keep separate JDBC
operations and checked JSON failures; no new error translation or transaction.

## Invariants

- Unauthorized deck access stops before import/draft access.
- Required commander selection precedes draft lookup.
- Draft isolation remains by guest, and exactly-one-day rows are unavailable but retained.
- Failed mutations do not broadcast; successful duplicate retries retain existing behavior.
- HTTP error handling is shared only by the selected controllers.

## Edge cases and failures

Null/nontext/unknown formats, malformed request JSON, unavailable drafts, wrong guest,
strict TTL boundaries, malformed stored JSON, serialization/SQL failures and partial
purge, service subtypes, conflict/forbidden, broadcast failure, and repeated loads.

## Migration and compatibility

No migration or client changes. Keep request field names and JSON types and explicit
format behavior. Existing diagnostics tests move to advice composition and retain
logger category assertions. Browser restart behavior remains unchanged.

## Acceptance criteria

- [x] HTTP characterization passes before and after extraction.
- [x] Repository/workflow contracts fail for missing behavior, then pass.
- [x] New components have the approved responsibilities with compatible semantics.
- [x] API production components have no coupling/method-count/complexity findings.
- [x] Full backend, formatting, packaging, browser, and jar smoke checks pass.

## Verification

Confirmed target: comprehensive coverage. Characterize routes/JSON, auth and validation
order, mutation broadcasts and failures, every error mapping/subtype/log level, and
malformed-body compatibility. Add workflow contracts and repository tests for guest
isolation, exact database TTL boundaries, JSON mapping errors, SQL failures and partial
purge. Retain RoomStore idempotency/rollback tests and add workflow retry evidence.
Run focused tests, full Spotless/backend verify, inspect fresh PMD, jar smoke without
live imports, five browser flows, and documentation formatting/references. Record
actual red/green results and blocked checks.

### Executed verification

- Initial green characterization: 31 new API route cases plus 21 existing HTTP/
  diagnostics cases passed against the original controller.
- Repository/workflow red: 21 cases failed on explicit unimplemented contracts;
  the original 52 characterization/HTTP cases remained green.
- Mockito fixture evaluation order and a PostgreSQL fixture DataSource mismatch
  were corrected; neither was treated as the intended red behavior signal.
- Composed controllers/advice passed 77 focused cases, including four additional
  scope/group-error characterizations. An additional cross-room draft reuse case
  also passed in full verification.
- Full `spotless:apply spotless:check verify`: 554 tests, zero failures/errors/skips;
  formatting, packaging, and PMD reporting passed.
- No coupling, method-count, GodClass, cognitive/cyclomatic/NPath, or unnecessary
  import findings in the new production API components; no analysis diagnostics.
- Jar smoke passed without live imports; artifact inspection confirms replacement
  controllers/advice are bundled and the old ApiController class is absent.
- All five browser regression flows passed, including both formats, deck import
  fallback, privacy, and durable reconnect.
- Markdown formatting, documentation references, and whitespace checks passed.

### API failure-test readability follow-up

The user requested cleanup of ApiRoutesTest. Replace the six-stage conditional
fixture with six focused failure tests and branch-free import/draft/request helpers.
Retain comprehensive coverage and all response/logging assertions, strengthening
explicit operation-order checks for purge, insert, stored JSON, room load, and
broadcast failures. Production behavior is unchanged.

- [x] All 35 API route cases remain green and fixture complexity findings are removed.
- [x] Formatting and full backend verification pass.

Follow-up verification: all 35 API cases and all 554 backend tests passed.
Spotless, packaging, and fresh PMD passed; ApiRoutesTest has no cognitive,
cyclomatic, or NPath complexity findings. The existing class-level TooManyMethods
advisory remains. Browser and jar smoke were not rerun for this test-only cleanup;
the preceding decomposition verification remains recorded above.

## Architecture decisions

- [ADR-0007](../docs/adr/0007-api-component-boundaries.md): API component boundaries.
- [ADR-0001](../docs/adr/0001-authoritative-game-state.md): authority and transactions.
- [ADR-0002](../docs/adr/0002-room-format-profiles.md): format profiles.

## Open questions

None.
