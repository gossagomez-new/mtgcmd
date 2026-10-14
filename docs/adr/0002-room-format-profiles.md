# ADR-0002: Server-owned room format profiles

- Status: Accepted
- Date: 2026-10-14
- Related specification: [Standard tables](../../spec/standard-tables.md)

## Context

The tabletop initially assumes four-player Commander throughout engine, deck
validation, catalog legality, and UI. Two-player Standard needs different seat,
life, and deck rules without changing authoritative state or privacy boundaries.
Room format becomes a persisted and public contract. Existing snapshots and
request payloads must retain their Commander meaning.

## Decision

Store an immutable COMMANDER or STANDARD format in each room's authoritative
JSON snapshot. Define server-owned profiles for capacity, starting life, deck
limits, and commander requirements; keep deck validation separate from physical
card actions. Snapshots expose format and capacity. Missing/null legacy format
means Commander. The client derives presentation from server-provided format;
it cannot change a room's format after creation.

Keep original Commander legality and add optional format legality observations
and timestamps to card definitions. Standard import attempts bounded refresh of
missing/stale legality; cached definitions remain available with explicit
verification warnings during provider failures. Format legality is advisory,
while seat and deck-size limits are mandatory.

## Alternatives considered

### Scattered format conditionals

- Benefits: fewer initial types.
- Drawbacks: duplicates capacity and life rules, risks divergence between actions,
  validation, and frontend expectations.
- Reason not selected: existing hardcoded assumptions already affect several layers.

### Generic rules/plugins framework

- Benefits: extensibility for many future formats.
- Drawbacks: configuration and lifecycle machinery exceeds the needs of two formats;
  it could imply automated rules interpretation this app does not provide.
- Reason not selected: simple profiles and cohesive validators are sufficient.

### Separate Standard service or application

- Benefits: isolates format-specific logic.
- Drawbacks: duplicates sessions, storage, transport, privacy, and tabletop actions.
- Reason not selected: the existing authoritative service can host both formats.

## Rationale

A room-level immutable format keeps saved games deterministic and centralizes
hard requirements. Legacy defaults preserve existing rooms without data migration.
Explicit legality freshness avoids confusing Commander legality with Standard
legality while retaining the assisted-tabletop model during external outages.

## Consequences

### Benefits

- Shared tabletop mechanics and privacy protections remain consistent.
- Two- and four-player behavior has one source of truth for format rules.
- Existing Commander requests and snapshots retain their meaning.

### Drawbacks and risks

- Tests must cover both profiles and legacy defaults.
- Standard legality can change; stale/unavailable observations require warnings.
- Snapshot compatibility is forward additive; rollback to code unaware of Standard
  rooms requires preserving their stored data rather than loading them as Commander.

### Operational impact

No new dependency, service, or SQL migration. Catalog enrichment is lazy.
Twenty-four-hour Standard refresh uses existing bounded, paced collection requests.
No deployment or publishing is authorized by this record.

## Follow-up actions

- [ ] Complete the frontend UI after implementing profiles, compatibility defaults, and validation.
- [x] Verify both formats and legacy cache/snapshot recovery.

## References

- [ADR-0001](0001-authoritative-game-state.md)
- [Standard format guide](https://magic.wizards.com/en/formats/standard)
