# Validated frontend protocol boundary

## Metadata

- Status: Completed
- Priority: High
- Priority rationale: Prevent unchecked network payloads entering React state.
- Depends on: [Frontend static analysis](frontend-static-analysis.md), [Standard tables](standard-tables.md)

## Summary

Validate HTTP and WebSocket responses using shared Zod schemas with inferred
TypeScript types. Move HTTP transport out of App and decode complete socket
messages before applying effects.

## Goals

- Validate session, saved-room, draft, snapshot, and message payloads.
- Preserve transport/lifecycle behavior and existing backend contracts.
- Reject malformed data safely without retrying committed mutations.

## Non-goals

- UI decomposition, backend changes, game-rule validation, acknowledgment ID
  correlation, new reconnect policy, or a broader test-suite cleanup.

## Background

The audit found unchecked response.json/JSON.parse results entering state.
Current card types omit legitimate nulls on commanderKey/attachedTo; hidden
definitions are omitted. Existing socket envelopes use optional fields rather
than variant-specific requirements. These boundary issues are in scope; UI
complexity and unrelated test parsing remain separate work.

## Requirements

### Functional requirements

1. Define complete schemas and infer existing exported protocol types. Accept
   additive unknown object properties, but do not coerce malformed field values.
2. Preserve optional legalities/legalityCheckedAt, nullable search, omitted hidden
   definitions, and nullable/omitted commanderKey/attachedTo. Accept negative
   correction counters. Do not invent hidden data or reimplement server policy.
3. Decode session/room-list/draft and all HTTP snapshots before consumption.
   Endpoint methods choose decoders; callers cannot assert an arbitrary T.
4. Keep CSRF, request payloads, command IDs, initial session error text, ignored
   room-list non-success, silent initial room 403, and cancellation guards.
5. Represent HTTP failures with status/message, protocol failures with safe
   messages, and preserve original network/abort errors. Malformed HTTP error
   bodies use safe fallback text while retaining status.
6. A failed POST with status 409 triggers one current-room GET, even if its error
   body is malformed. Apply only a validated refreshed snapshot; refresh failure
   remains attached as the original HTTP error's cause and cannot mask it.
   This failure handling is an intentional improvement over the current code.
7. Never automatically retry a mutation after HTTP, parsing, or refresh failure.
8. Validate full text socket frames before effects. Support snapshot(view),
   ack(commandId/view), error(optional nullable message/optional view), and
   presence(online). Reject malformed/unknown variants and non-text frames.
9. Preserve snapshot ordering, backoff, pending timeout, stopped-connection guard,
   and view-before-acknowledgment effects. Invalid frames leave view/presence and
   pending state intact and show the existing safe update-read error.

### Non-functional requirements

- No payload bodies or schema input values in protocol error messages/causes.
- No whole-payload logging. Backend remains authoritative for privacy/permissions.
- Keep parsing centralized and inferred types aligned with runtime validation.

## Contracts and data

Existing backend JSON shapes are unchanged. Frontend nullable types are corrected.
Unknown additive fields are ignored by object schemas. Required nested fields
must be present; identifiers/status strings remain strings without new business
constraints. Format accepts COMMANDER/STANDARD. Numeric integers and finite
numeric coordinates are validated; arrays/maps validate their elements.

HttpError retains status and a safe validated/fallback message. ProtocolError
retains a safe fixed message without raw validation/JSON errors. Requests use
same-origin browser credentials as before.

## Technical constraints

Use Zod as a runtime dependency and existing Vitest/Playwright. Share Snapshot
decoding across transports. Do not change server persistence or API endpoints.

## Invariants

- Only fully decoded network values reach UI callbacks.
- A malformed ack cannot acknowledge a command.
- A failed response does not imply that a mutation was uncommitted.
- Hidden definitions stay absent; old snapshots remain ignored by acceptSnapshot.

## Edge cases and failures

- Empty/non-JSON bodies, invalid nested fields, null error messages, unknown
  variants, aborted reads, failed conflict refresh, and late completion after
  effect cancellation are covered.
- Schema errors never include private card strings. Network errors are preserved.

## Migration and compatibility

Install with npm ci. No backend migration. Valid wire payloads preserve behavior;
malformed payloads now fail before partial state updates. No silent defaults for
required fields and no automatic retries. Zod bundle cost is measured by build.

## Acceptance criteria

- [x] Schema and transport success/edge/failure tests pass.
- [x] Invalid socket messages cause no partial effects or acknowledgment.
- [x] Existing gameplay/privacy/reconnect browser flows pass.
- [x] Frontend checks/build/analysis pass; relevant unsafe production findings disappear.

## Verification

User selected **comprehensive coverage**: complete Commander/Standard and hidden-card
fixtures, nullable/omitted fields, all message variants, malformed nested values,
HTTP error/conflict recovery, cancellation, no retries, and browser privacy/reconnect.
Tests precede implementation, including a red runtime baseline for missing decoders.
Retain existing unit/browser tests and add targeted malformed-transport browser
checks; run lint, types, format, analysis, and packaging.

Executed 2026-10-16: the red baseline had 57 runtime failures from the explicit
unimplemented boundary stubs, while all 8 existing tests passed. After implementation
and the response-body cancellation test, all 66 unit tests passed. All 9 browser
tests passed, including four new malformed-response/403/cancellation/pending-ack
checks and the existing real Commander/Standard privacy, import, search, grouping,
and reconnect flows. Lint, type checking, formatting, analysis, and the full
build passed; Maven verified 571 tests with no failures/errors/skips.

Analysis changed from 42 to 24 warnings with zero errors or Knip findings: all 18
production unsafe-value diagnostics disappeared. Remaining UI complexity/async
handlers and 11 existing test-only warnings are out of scope. The new JavaScript
bundle is 350.93 kB / 106.21 kB gzip, compared with 253.48 kB / 78.55 kB gzip
before this refactor. This measures the complete change, not isolated Zod cost.

## Architecture decisions

[ADR-0011](../docs/adr/0011-validated-frontend-protocol-boundary.md), explicitly approved.

## Open questions

None.
