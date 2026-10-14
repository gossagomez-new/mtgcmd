# Catalog component decomposition

## Metadata

- Status: Completed
- Priority: Medium
- Priority rationale: Separate persistence and provider details from format-specific resolution policy.
- Depends on: [Service failure diagnostics](service-failure-diagnostics.md)

## Summary

Keep CardCatalog as the entry point and coordinator. Extract JdbcCardCache for
persistence and ScryfallCardSource for provider communication, definition mapping,
and verified name matches. The user approved comprehensive coverage and ADR-0004.

## Goals

- Separate responsibilities without changing catalog behavior or storage.
- Reduce CardCatalog's GodClass, CognitiveComplexity, and CyclomaticComplexity findings.
- Allow independent persistence/provider tests and retain integration protection.

## Non-goals

- Bulk SQL, retries, new providers, schema tightening, or a format Strategy hierarchy.
- Game-engine refactoring, unrelated PMD style cleanup, or public API changes.

## Background

The approved audit found resolve mixes SQL/JSON persistence, Scryfall requests,
matching, freshness, batching, and recovery (cognitive complexity 43 and cyclomatic
complexity 30). Extraction adds collaborators but hides independently changing
details. Follow-up inspection confirmed format-sensitive envelope validation and
incremental writes before later mapping failures; both must remain compatible.

## Requirements

### Functional requirements

1. CardCatalog coordinates cache reads, required lookups, batch recovery, writes,
   unknown-name errors, and output order. It must not implement SQL or provider JSON traversal.
2. JdbcCardCache owns SQL, stored-definition serialization, and normalized keys.
   A narrow CardCache contract exposes find/store; a shared name-key helper keeps
   repository, matching, and coordinator normalization consistent.
3. ScryfallCardSource owns the endpoint, identifier conversion, JSON parsing,
   definition mapping, image filtering, verified aliases, and the 75-card batch limit.
   CardCatalog still processes batches separately so recovery remains per batch.
4. Provider results expose supported-envelope status and lazy matched definitions,
   never raw Jackson nodes. Standard rejects unsupported envelopes; Commander
   retains existing permissive processing and resulting errors.
5. Process returned definitions in provider order. Store each canonical name first,
   then its verified requested aliases, updating the resolution map in the existing
   sequence. Earlier writes remain when a later mapping/write/name check fails.
6. Preserve original order and multiplicity, exact pending-name deduplication,
   case/whitespace support, multi-face identifiers, and incorrect-back-face rejection.
7. Keep freshness and fallback in CardCatalog: refresh stale/missing Standard
   observations, recover only batches wholly covered by cache, log recovered
   failures once, and always propagate interruption/programming failures.
8. Persistence failures are not provider fallback. Preserve cached-corruption
   errors, typed service failures, causes, unknown-name messages, and clock observations.
9. Retain existing public resolve methods, constructors, and static definition
   factory as compatibility delegates. Narrow the image URI catch to IllegalArgumentException.

### Non-functional requirements

- No new dependencies, SQL/schema changes, wire changes, or private-data logging.
- Keep mapping/matching private to the adapter; avoid additional service layers.

## Contracts and data

Existing DeckImporter.Catalog and Definition contracts remain unchanged. Internal
provider results contain a supported-envelope flag and an Iterable of definitions
with verified original requested aliases. Lazy iteration preserves exception phase
and incremental cache effects. Shared keys use trim and Locale.ROOT lowercase.

## Technical constraints

Repository and source adapters remain in the catalog package. Composition may use
existing compatibility constructors; a package constructor supports isolated
coordinator tests. Keep format policy out of the Scryfall adapter.

## Invariants

- Shortening a provider identifier does not validate the original requested name.
- Incorrect aliases are never persisted as successful matches.
- Result order, duplicates, batch size, and per-batch stopping behavior are preserved.
- Typed errors and earlier partial writes survive decomposition.

## Edge cases and failures

Test missing/corrupt cache entries, failed reads/writes, incomplete/malformed
provider responses, multiple aliases, image/text/id fallbacks, unknown names in
the first batch, duplicate requests, lazy mapping failure, and interruption.

## Migration and compatibility

No migration or caller changes required. Existing integration tests continue to
exercise real PostgreSQL through the compatibility constructors.

## Acceptance criteria

- [x] Three components have the approved responsibilities.
- [x] Cache/source tests and existing catalog/import integration tests pass.
- [x] Ordering, matching, batching, recovery, partial writes, and errors remain compatible.
- [x] CardCatalog no longer has the targeted GodClass/complexity findings.
- [x] Formatting, full backend verification, PMD, and packaging pass.

## Verification

Confirmed target: comprehensive coverage. Add characterization tests before
extraction for duplicates, early batch rejection, partial mapping writes, and
write failure outside fallback. Introduce component contracts and failing stubs
only after adding their tests; confirm expected unimplemented-boundary failures
before production logic. Cache tests cover normalization, serialization, misses,
corruption, and read/write failures. Source tests cover identifiers, aliases,
envelopes, mapping fallbacks, lazy observation, and typed provider/parsing failures.
Retain the real PostgreSQL catalog suite including freshness, 74/75/76/150/151
batch boundaries, private import regression, and diagnostics. Run focused tests,
Spotless, full backend verify, and inspect the resulting PMD report. No frontend
or browser checks required for unchanged wire behavior.

Executed on 2026-10-14: the four new characterization cases passed before
extraction alongside all existing catalog integration tests. The new boundary
tests then failed on deliberately unimplemented cache/source contracts, while
the original 39 catalog cases remained passing. Implementing the adapters passed
all 62 boundary/integration cases; wiring the coordinator also passed the import
suite. A further compatibility test confirms permissive Commander envelopes and
strict Standard envelopes remain distinct.

The final `spotless:check verify` passed 229 backend tests with zero failures,
errors, or skips, generated the executable jar, and generated PMD reports without
processing/configuration errors. None of CardCatalog, JdbcCardCache, or
ScryfallCardSource has GodClass, CognitiveComplexity, CyclomaticComplexity, or
AvoidCatchingGenericException findings. Other broad advisory PMD findings remain.
Documentation formatting, relative links, and git diff whitespace checks passed.
PostgreSQL verification used approved escalation for local-port access. Frontend,
browser, Docker, and live-provider checks were not run for this internal refactor.

## Architecture decisions

- [ADR-0004](../docs/adr/0004-catalog-component-boundaries.md): coordinator,
  repository, and provider-adapter boundaries.
- [ADR-0001](../docs/adr/0001-authoritative-game-state.md): existing state/privacy boundary.

## Open questions

None.
