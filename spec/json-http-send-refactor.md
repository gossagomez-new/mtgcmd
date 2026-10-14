# JsonHttp request lifecycle refactor

## Metadata

- Status: Completed
- Priority: Medium
- Priority rationale: Make transport lifecycle and failure translation independently reviewable.
- Depends on: [Service failure diagnostics](service-failure-diagnostics.md)

## Summary

Retain JsonHttp as the adapter. Keep lifecycle coordination under a private instance lock in send;
extract request execution, response validation/decoding, nullable cancellation,
and asynchronous failure translation into private helpers.

## Goals

- Remove send cognitive and cyclomatic complexity findings without relocating them.
- Preserve request configuration, bounded bodies, lifecycle, and typed failures.

## Non-goals

- New adapters, strategies, dependencies, concurrency policy, or retry behavior.
- Changing exception identity/cause chains, status acceptance, or resource ownership.
- Suppressing warnings or refactoring BoundedBody.

## Background

The pre-design audit inspected JsonHttp, its callers, and tests. send combines
request construction, execution/waiting, response validation, and cause mapping
(cognitive 16, cyclomatic 19). This is a medium-priority localized complexity
finding: severity 30 + high confidence 15 + small effort 20 = 65. Private extraction
adds a few helpers but retains the existing lifecycle owner. No outside prerequisite
was found. Existing tests protect transport causes, size bounds, timeout and
interruption; extend them before extraction.

## Requirements

### Functional requirements

1. Preserve GET/POST behavior, fixed headers, body, request timeout, UTF-8 decoding,
   and status 200 as the only success. Preserve constructor limits and redirect policy.
2. Use a private instance lock around spacing, execution, waiting, and translation.
   Unrelated code synchronizing on the adapter must not block its requests.
   Preserve one-at-a-time execution through the shared adapter.
3. Preserve bounded response subscriber and its complete-body limit behavior.
4. Waiting timeout and interruption cancel an existing future. Before execution,
   interruption must not start a request. Restore the interrupt flag on interruption.
5. Success, rejected status, and asynchronous failures do not add cancellation.
6. Preserve exact messages and mappings: CardServiceException and RuntimeException
   causes propagate unchanged; HttpTimeoutException maps before IOException;
   those translations retain ExecutionException and its original cause; other
   causes become IllegalStateException retaining ExecutionException.
7. Extract private cohesive helpers without public contracts or caller changes.

### Non-functional requirements

- Do not introduce unconditional finally cancellation or narrow synchronization.
- Review PMD warnings contextually; never add exception wrappers just for a metric.

## Contracts and data

Public constructors/get/post remain unchanged. Definitions, wire contracts, and
persistence remain untouched. Response failures and transport failures retain
existing categories, descriptions, instance identity, and cause-chain structure.

## Technical constraints

Use existing HttpClient, CompletableFuture, BoundedBody, and service exceptions.
No new lifecycle object or class-per-failure framework. Preserve shared adapter usage.

## Invariants

- No requests start after interruption during spacing.
- Cancellation occurs only for existing waiting-timeout/interruption paths.
- Interrupt state is restored; exception subtype precedence remains fixed.
- Serialized callers cannot overlap request execution through the adapter.
- Every failure releases the private lock so other callers can proceed.

## Edge cases and failures

Empty/multibyte bodies; exact size boundary/excess; non-200 statuses; every typed
failure and unexpected checked/runtime/null cause; synchronous failure; interrupted
spacing/waiting; concurrent callers and lock release after failure.

## Migration and compatibility

No migration or client/caller changes. Pure private implementation refactor.

## Acceptance criteria

- [x] Comprehensive characterization passes before and after extraction.
- [x] Existing catalog/import and transport integration tests pass.
- [x] JsonHttp has no cognitive or cyclomatic complexity findings.
- [x] Private-lock isolation and cross-thread failure release pass; method-level synchronization warning is removed.
- [x] Formatting, full backend verification, and packaging pass.

## Verification

Confirmed target: comprehensive coverage. Retain existing HTTP/body-limit tests;
add request configuration/body/decoding, status cases, all asynchronous mappings,
unknown/null causes, no extra cancellation, interruption before execution, bounded
subscriber boundaries, and concurrent serialization. Establish a green baseline
before extraction because no behavior change is intended. Run focused JsonHttp,
DeckImporter, CardCatalog tests then Spotless/full backend verify and inspect fresh
PMD including contextual stack-trace and synchronization findings. No browser
regression needed for private transport-helper extraction.

### Private-lock follow-up

The user approved replacing method-level synchronization with a private lock.
Retain comprehensive coverage: add external-monitor isolation and cross-thread
lock-release checks for all failure paths. Keep noninterruptible monitor admission
and full lifecycle serialization; provider-specific limiters remain outside scope.

### Initial helper verification

- All 29 JsonHttp cases and existing importer/catalog tests passed against the
  original code, then passed again after extraction. No behavior change was
  intended, so characterization established a green baseline.
- Full `spotless:apply spotless:check verify` passed: 480 backend tests, zero
  failures/errors/skips, successful formatting and packaging.
- Fresh PMD has no CognitiveComplexity, CyclomaticComplexity, or PreserveStackTrace
  findings in JsonHttp and no analysis diagnostics. AvoidSynchronizedAtMethodLevel
  remains intentionally: synchronization scope is part of the preserved lifecycle.
- Markdown and whitespace checks passed. Browser tests were not rerun because
  only private transport helpers changed.

### Private-lock verification

- The isolation test first failed with TimeoutException while an unrelated caller
  held the adapter monitor; all other 34 transport cases passed.
- After the private-lock change, all 35 transport cases and importer/catalog
  tests passed, including cross-thread release for five failure categories.
- Full `spotless:apply spotless:check verify` passed: 486 backend tests, zero
  failures/errors/skips, successful formatting and packaging.
- Fresh PMD has no AvoidSynchronizedAtMethodLevel, CognitiveComplexity,
  CyclomaticComplexity, or PreserveStackTrace findings in JsonHttp; no analysis
  diagnostics. No warning suppression was added.
- Markdown formatting and whitespace checks passed.

## Architecture decisions

[ADR-0004](../docs/adr/0004-catalog-component-boundaries.md) retains adapter ownership.
No new ADR: private extraction preserves boundaries and contracts.

## Open questions

None.
