# Deck import parsing and validation

## Metadata

- Status: Completed
- Priority: Medium
- Priority rationale: Separate independently changing import formats and reject malformed provider data before catalog resolution.
- Depends on: [Commander tabletop](commander-tabletop.md)

## Summary

Keep `DeckImporter.preview` as the public facade while separating text parsing,
Moxfield URL validation, and provider response decoding. Give validation rules
explicit names and ownership. Harden malformed provider entries without changing
supported imports or the frontend's pasted-export fallback.

## Goals

- A short coordinator with independently testable source parsers.
- Explicit source-specific policies and shared quantity/name limits.
- Strict provider entry validation and overflow-safe total checks.

## Non-goals

- New import sources, generic validation chains, or rule registries.
- Changes to HTTP transport, catalog resolution, persistence, or frontend contracts.

## Background

The former method combined URL security policy, provider decoding, text syntax,
section handling, aggregation, catalog resolution, and draft construction.
Jackson quantity coercion could accept malformed provider fields. The frontend
recognizes actionable Moxfield errors through the word `paste`.

## Requirements

### Functional requirements

1. A nonblank URL takes precedence over text; a blank or absent URL selects text.
2. Text parsing owns input limits, line-numbered errors, section aliases,
   comments, printing annotations, and commander detection. A `DeckSection`
   enum represents inclusion and commander semantics.
3. `MoxfieldDeckUrl` owns named URL predicates and constructs a fixed API URI.
4. `MoxfieldDeckSource` owns fetching and provider decoding into ordered entries.
5. Shared aggregation merges exact, case-sensitive names, preserves first
   occurrence order, and retains commander status from any occurrence.
6. Resolve unique names only after validation. Preserve expanded card ordering,
   distinct catalog choices, and distinct suggested commander IDs.

### Non-functional requirements

- No network or catalog calls for rejected input at their respective boundaries.
- Reuse existing injected catalog/fetcher interfaces and transport protections.
- Use offline fixtures for the comprehensive unit test matrix.

## Contracts and data

Public `preview(text, url)`, `Draft`, and the `(Catalog, Fetcher)` constructor
remain unchanged. Package-private parsers produce an immutable `ParsedDeck` of
ordered entries with name, quantity, and commander status.

URL policy preserves HTTPS, the exact approved hosts, no user information or
explicit port, and `/decks/[A-Za-z0-9_-]{1,80}/?`. URI-decoded paths remain the
validation input; query strings and fragments remain accepted and ignored.

Text remains limited to 100,000 Java string characters; names to 200 characters;
quantities and total deck size to 1–250. Existing text syntax and errors remain.
Comments, blank lines, and excluded sideboard/maybeboard/considering lines are
ignored before entry validation.

Provider responses must have an object root. Boards may be at the root or in an
object `boards` member. Missing commander/mainboard boards are permitted; present
boards and optional `cards` wrappers must be objects or arrays. Entries and
`card` fields must be objects. Names must be nonblank strings of at most 200
characters. Missing quantity defaults to one; present quantity must be an
integral JSON number in 1–250, without coercion or integer truncation.

## Technical constraints

Use Java 25 and existing Jackson/JUnit dependencies. Keep parsing collaborators
in the existing catalog package. Use named URL predicates rather than a rule
enum or Chain of Responsibility. Share limits without combining source-specific
validators into a generic validator.

## Invariants

- Quantity aggregation uses bounded accumulation before integer merging.
- Moxfield requests always target the reconstructed fixed API endpoint.
- Draft ordering and commander deduplication remain stable.
- Catalog results must match the number of requested unique names.

## Edge cases and failures

- Invalid URI syntax and disallowed URLs retain their existing messages.
- Fetch failures and invalid JSON retain the unavailable/private-deck message.
- Empty/null responses, invalid structures, and malformed fields fail with the
  unsupported-deck message containing `paste`.
- Valid but empty provider decks retain the no-cards fallback message.
- Invalid totals retain the shared 1–250-card message.
- Catalog failures propagate; result-count mismatches retain the existing error.

## Migration and compatibility

No data migration or deployment configuration changes. Deliberate tightening
rejects provider numeric strings, fractional/null/boolean quantities, oversized
integers, non-string/overlong names, and invalid containers previously ignored
or coerced. Valid text exports and supported provider layouts retain behavior.

## Acceptance criteria

- [x] Preview delegates format-specific work to separate collaborators.
- [x] Supported inputs preserve ordering, counts, and commander suggestions.
- [x] Malformed provider data fails before catalog resolution with actionable errors.
- [x] Invalid URLs never fetch; totals cannot overflow during aggregation.
- [x] Comprehensive regression checks pass, including browser fallback behavior.

## Verification

The user selected the **comprehensive matrix** target: parameterized URL,
text-format, provider-layout, field-type, and boundary tests, plus source
selection, aggregation, ordering, deduplication, dependency failures, and
no-side-effect assertions. This increases confidence and maintenance effort;
unit cases remain deterministic and offline.

Add passing characterization tests first. Add hardening regressions and confirm
expected failures before production changes. Run targeted importer/parser tests,
the complete backend suite, `spotless:check`, and existing browser tests covering
import and pasted-export fallback. Results:

- Characterization baseline: 30 cases passed before production changes.
- Hardening regressions: nine expected failures confirmed before implementation.
- Final importer/parser matrix: 86 cases passed.
- Full backend suite: 103 tests passed; local-port tests required execution outside
  the filesystem/network sandbox.
- Java formatting: `spotless:check` passed.
- Backend packaging: `mvn -f backend/pom.xml -DskipTests verify` passed after the
  full test run.
- Browser suite: both tests passed, including pasted-deck fallback; server and
  browser startup required execution outside the sandbox.
- Working-tree whitespace: `git diff --check` passed.

### Fixture readability follow-up

Separate provider layout enumeration from JSON construction while retaining all eight nested/wrapped/array combinations, order, and existing aggregation/suggestion assertions. Production behavior and existing comprehensive test coverage remain unchanged.
Focused verification: all 93 DeckImporterTest and 59 GameEngineCommandsTest cases passed. Full `spotless:apply spotless:check verify` passed all 554 backend tests, formatting, and packaging. Fresh PMD has no cognitive/cyclomatic/NPath or GodClass findings in DeckImporterTest, GameEngineCommandsTest, or GameCommandData. The first helper extraction introduced a GodClass advisory in the command test; moving preparation into the dedicated fixture removed it. All eight layout cases and 57 format/command cases remain. Browser tests were not rerun for this test-only cleanup.

## Architecture decisions

No new ADR: internal responsibility extraction and approved input validation
changes introduce no new system boundary, technology, or persistence pattern.
Related system architecture: [ADR-0001](../docs/adr/0001-authoritative-game-state.md).

## Open questions

None.
