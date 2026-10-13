# Multi-face catalog lookup

## Metadata

- Status: Completed
- Priority: High
- Priority rationale: Valid double-faced cards currently block deck imports on a cold catalog cache.
- Depends on: [Commander tabletop](commander-tabletop.md)

## Summary

Translate a two-part imported card name into its front-face identifier for
Scryfall collection requests, while retaining the original name for response
matching, cache keys, errors, and deck assembly.

## Goals

- Resolve `Clearwater Pathway // Murkwater Pathway` from Moxfield or text exports.
- Preserve the complete canonical card definition and both faces.
- Preserve batch efficiency, cache behavior, input ordering, and unknown-card rejection.

## Non-goals

- Fuzzy matching, accepting incorrect back-face names, or changing importer syntax.
- Changing Moxfield transport, database schema, public API, or HTTP protections.

## Background

A live Scryfall collection request with the full name returned an empty `data`
list and that name in `not_found`. Requesting the front name returned the full
canonical two-face definition. `CardCatalog.resolve` formerly sent the imported
name unchanged. A warm canonical cache or another face lookup in the same batch
could hide the failure. Direct access to the reported public Moxfield deck was
blocked during investigation; full-deck validation is therefore unconfirmed.

## Requirements

### Functional requirements

1. For an uncached name containing the printed-name separator ` // `, submit
   the trimmed portion before its first separator as the collection identifier.
2. Preserve the original imported name for matching against the returned
   canonical name or an individual face name, using existing normalization.
3. Preserve names without the separator and maintain at most 75 identifiers
   per collection request.
4. Cache successfully matched aliases and canonical definitions as before.
5. Preserve returned definitions, including faces, images, text, colors, and legality.

### Non-functional requirements

- No additional fallback request or per-card lookup is required.
- Regression tests use embedded PostgreSQL and mocked HTTP responses, without
  contacting external providers.

## Contracts and data

`CardCatalog` constructors, `resolve`, `Definition`, catalog schema, and importer
interfaces are unchanged. The only production change is provider request-name
translation. Cache lookup remains based on the original normalized name.

## Technical constraints

Use existing Java, Jackson, JDBC, PostgreSQL, and test dependencies. Keep the
provider translation in `CardCatalog`, rather than stripping card faces in
Moxfield or text parsers.

## Invariants

- A successful result matches the original full card name or individual face.
- A known front face cannot make an incorrect full name valid.
- Resolved cards retain input order and all face metadata.
- Cached cards remain usable without external requests.

## Edge cases and failures

- Cold-cache full names must resolve through their front-face identifier.
- Case and surrounding whitespace retain existing matching behavior.
- Unknown full names and incorrect back faces retain the original unknown-card
  message and must not be cached under an invalid alias.
- Mixed cache hits and misses, repeated face aliases, and batches crossing 75
  names retain correct output order.

## Migration and compatibility

No migration. Existing cache entries remain valid. Previously failing valid
full names now resolve; no fuzzy matching or relaxed name validation is added.

## Acceptance criteria

- [x] Reported Moxfield-format card preview succeeds with both faces intact.
- [x] Two-part card identifiers use the front face in collection requests.
- [x] Normal and face-name imports, cache reuse, and batch ordering remain correct.
- [x] Incorrect back faces and unknown cards are rejected under their original names.

## Verification

The user selected **comprehensive coverage**. Parameterized fixtures cover the
reported modal double-face name, another transforming-card name, and a split
name; normal/front/back lookups; metadata preservation; normalization; cache
reuse; mixed results; invalid names; and batch sizes 74, 75, 76, 150, and 151.
A Moxfield-shaped preview regression exercises the parser/catalog boundary.

Explain cases before writing tests; demonstrate expected regression failures
before production changes. Run focused catalog/importer tests, the complete
backend suite, Java formatting checks, and backend packaging. Record live API
observations separately from offline automated results.

Results:

- Live isolated full-name collection request: zero cards, original name in
  `not_found`; the front-face identifier returned the full two-face definition.
- Red phase: ten expected lookup failures, with 86 importer cases and three
  unaffected catalog cases passing before production changes.
- Green phase: all 99 focused catalog/importer cases passed.
- Full verification: `mvn -f backend/pom.xml spotless:check verify` passed,
  including 116 backend tests and jar packaging. Embedded PostgreSQL required
  execution outside the sandbox to bind local ports.
- `git diff --check` passed.
- Browser tests were not rerun: frontend contracts and parsing are unchanged;
  the new preview regression covers the parser/catalog integration.
- The entire live Moxfield deck could not be tested because the provider blocked
  the direct request. No running application was restarted or deployed.

## Architecture decisions

No new ADR: this is a local provider-compatibility correction with unchanged
system boundaries and persistence. Existing architecture:
[ADR-0001](../docs/adr/0001-authoritative-game-state.md).

## Open questions

None.
