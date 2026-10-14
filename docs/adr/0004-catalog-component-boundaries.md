# ADR-0004: Catalog component boundaries

- Status: Accepted
- Date: 2026-10-14
- Related specification: [Catalog decomposition](../../spec/catalog-decomposition.md)

## Context

CardCatalog combines format policy with database persistence and Scryfall protocol
details. Multi-face matching, legality refresh, fallback, and error diagnostics
have made its resolution workflow difficult to change independently.

## Decision

Keep CardCatalog as an application coordinator. Extract JdbcCardCache behind a
narrow CardCache repository contract and ScryfallCardSource as the provider adapter.
The coordinator owns freshness and recovery; the repository owns SQL and stored
JSON; the source owns HTTP/JSON mapping and verified matches. Shared key
normalization is a package helper, not another service. Mapping/matching remain
private adapter operations rather than separate components.

The provider owns its batch limit; the coordinator processes batches to retain
per-batch recovery. Typed results expose envelope validity and lazy matched cards.
Laziness preserves current incremental persistence before later processing errors.
Existing constructor/factory entry points delegate to the extracted components.

## Alternatives considered

### Private-method-only extraction

- Benefits: smaller change and fewer collaborators.
- Drawbacks: SQL, provider protocol, and policy still share one class/dependency surface.
- Reason not selected: the responsibilities already change independently.

### Generic provider and format Strategy framework

- Benefits: extensive future extensibility.
- Drawbacks: extra abstractions without current multiple-provider requirements.
- Reason not selected: three cohesive components cover today's needs.

### Eagerly map the full provider batch

- Benefits: simpler immutable result list.
- Drawbacks: moves mapping failures ahead of all cache writes and changes recovery/error phase.
- Reason not selected: preserving existing behavior is the refactor's requirement.

## Rationale

Repository and Adapter boundaries localize persistence/protocol changes while
keeping format policy visible. Narrow interfaces and typed results enable focused
tests without introducing new technologies or storage conventions.

## Consequences

### Benefits

- Independent cache/source tests and clearer coordinator workflow.
- Stable caller contracts, format rules, typed diagnostics, and storage semantics.

### Drawbacks and risks

- More files and composition points.
- Lazy results require tests for failures during iteration, not only lookup calls.
- Legacy format-sensitive validation remains explicit compatibility behavior.

### Operational impact

No migration, deployment change, dependency, or additional external request.

## Follow-up actions

- Review bulk persistence or stricter provider schemas separately if needed.

## References

- [Service diagnostics specification](../../spec/service-failure-diagnostics.md)
- [Multi-face lookup specification](../../spec/catalog-multiface-lookup.md)
