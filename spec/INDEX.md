# Specification index

Each specification's `Depends on` metadata is authoritative.

| Specification | Status | Priority | Purpose | Depends on | Related ADRs |
| --- | --- | --- | --- | --- | --- |
| [Commander tabletop](commander-tabletop.md) | Completed | High | Four-player assisted Commander | None | [ADR-0001](../docs/adr/0001-authoritative-game-state.md): authoritative state, recovery, and privacy |
| [Build automation](build-automation.md) | Completed | Medium | Frontend-first clean packaging and artifact reporting | [Commander tabletop](commander-tabletop.md) | None |
| [Deck import refactor](deck-import-refactor.md) | Completed | Medium | Source-specific parsing and strict provider validation | [Commander tabletop](commander-tabletop.md) | [ADR-0001](../docs/adr/0001-authoritative-game-state.md): existing authoritative state boundary |
| [Multi-face catalog lookup](catalog-multiface-lookup.md) | Completed | High | Resolve full multi-face card names through Scryfall identifiers | [Commander tabletop](commander-tabletop.md) | [ADR-0001](../docs/adr/0001-authoritative-game-state.md): existing authoritative state boundary |
| [Card and library UX](tabletop-card-and-library-ux.md) | Completed | Medium | Standard tap orientation and private library name filtering | [Commander tabletop](commander-tabletop.md) | [ADR-0001](../docs/adr/0001-authoritative-game-state.md): existing privacy boundary |
| [Battlefield grouping](battlefield-grouping.md) | Completed | Medium | Automatic permanent type groups with lands below | [Commander tabletop](commander-tabletop.md) | [ADR-0001](../docs/adr/0001-authoritative-game-state.md): existing state and privacy boundary |

## Dependency graph

```mermaid
flowchart TD
    tabletop["Commander tabletop (independent)"]
    build["Build automation"]
    imports["Deck import refactor"]
    imports -->|depends on| tabletop
    catalog["Multi-face catalog lookup"]
    catalog -->|depends on| tabletop
    ux["Card and library UX"]
    ux -->|depends on| tabletop
    grouping["Battlefield grouping"]
    grouping -->|depends on| tabletop
    build -->|depends on| tabletop
```

## Unresolved issues

None.
