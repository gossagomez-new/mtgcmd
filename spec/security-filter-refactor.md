# Security filter refactor

## Metadata

- Status: Completed
- Priority: Medium
- Priority rationale: Make security policy easier to review while preserving protection and rejection order.
- Depends on: [Commander tabletop](commander-tabletop.md)

## Summary

Keep SecurityFilter as the servlet entry point. Extract coherent header-writing,
request-classification, authorization, and rejection-response methods.

## Goals

- Remove the filter's cognitive and cyclomatic complexity findings.
- Make guest, origin, CSRF, and body-length policy explicit and independently readable.
- Preserve observable behavior exactly.

## Non-goals

- New filter chains, policy frameworks, dependencies, endpoints, or security policies.
- Changes to guest storage, WebSocket lifecycle, or unrelated PMD findings.

## Background

The pre-design audit confirmed nested checks mix classification, authorization,
headers, and response writing. PMD reports cognitive complexity 24 and cyclomatic
complexity 13. Extraction reduces local review cost while retaining one policy
owner; the trade-off is a small set of additional private methods. No prerequisite
outside the approved scope was identified.

## Requirements

### Functional requirements

1. Write unchanged browser security headers on all requests.
2. Protect paths starting with /api/ and exactly /ws; set no-store on these paths.
3. Exempt exactly /api/session from guest, origin, CSRF, and body-length checks.
4. Other protected requests require a guest. All methods except GET require
   origin validation; /ws requires origin validation even for GET.
5. Non-GET protected requests except /ws require the constant-time CSRF comparison.
6. Non-GET protected requests, including /ws, require a length from 0 through
   100,000 inclusive. Missing length or excess length returns the existing 413 JSON.
7. Preserve validation order: guest, origin, CSRF, length. Guest Forbidden returns
   the existing 403 JSON. Unexpected failures propagate.
8. Continue the downstream chain exactly once only when accepted; preserve
   downstream failure propagation and the scope of Forbidden handling.

### Non-functional requirements

- Preserve all header values, status codes, content types, and response bodies.
- Reduce complexity through named predicates and guard clauses without relocating
  an oversized decision method.

## Contracts and data

No public contract changes. Session exemptions and non-GET semantics, including
HEAD/OPTIONS and non-GET WebSockets, retain current behavior.

## Technical constraints

Use the existing servlet filter, GuestSessions, and MessageDigest.isEqual.
Keep the security policy in SecurityFilter; no new dependency or module boundary.

## Invariants

- Rejected requests never reach the downstream chain.
- Invalid credentials take precedence over an invalid body length.
- Forbidden errors from downstream code are not translated by the filter.

## Edge cases and failures

Missing/mismatched headers; missing guest; missing/empty/maximum/excess length;
look-alike paths; methods other than GET; unexpected guest and downstream errors.

## Migration and compatibility

No migration. Only private implementation structure changes.

## Acceptance criteria

- [x] Characterization tests pass before and after extraction.
- [x] Named methods expose the approved policy without changing security behavior.
- [x] SecurityFilter has no cognitive or cyclomatic complexity findings.
- [x] Formatting, backend verification, and existing security integration tests pass.

## Verification

Confirmed target: comprehensive coverage. Add direct filter tests using mock
servlet requests/responses and GuestSessions. Cover method/path combinations,
headers, credential failures, length boundaries, validation order, chain
execution, non-GET WebSockets, and unexpected failure propagation. Establish a
green characterization baseline before production extraction; no behavior change
is intended. Run focused filter and web integration tests, then Spotless and full
backend verify with PMD reporting. Record actual results below.

### Executed verification

- 82 direct characterization cases passed against the original filter and after extraction; existing HTTP integration tests also passed at both stages.
- The initial fixture used a content-length header; Spring mocks derive length from content. Corrected the fixture before establishing the baseline.
- Full `spotless:apply spotless:check verify` passed: 430 tests, zero failures/errors/skips, successful packaging and PMD reports.
- SecurityFilter has no CognitiveComplexity or CyclomaticComplexity findings. Existing advisory style findings remain.
- All five browser regression flows passed, including real WebSocket connections and reconnects.

## Architecture decisions

Existing [ADR-0001](../docs/adr/0001-authoritative-game-state.md) establishes the
security/privacy boundary. No new ADR: private-method extraction is reversible
and leaves module boundaries and public contracts unchanged.

## Open questions

None.
