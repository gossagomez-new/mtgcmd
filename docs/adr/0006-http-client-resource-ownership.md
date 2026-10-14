# ADR-0006: HTTP client resource ownership

- Status: Accepted
- Date: 2026-10-14
- Related specification: [Resource ownership](../../spec/resource-ownership.md)

## Context

The production JsonHttp bean creates an internal client whose lifecycle is not
visible to Spring. Tests also create real clients, executors, and mocks with
different owners. Closing borrowed clients or connection decorators per operation
would break reuse. The user approved explicit ownership and this ADR.

## Decision

Spring owns and destroys the production shared HttpClient; JsonHttp borrows it.
Standalone JsonHttp constructors own internally created clients and expose
AutoCloseable cleanup. External-client constructors borrow. Real short-lived test
resources use lexical cleanup; WebSocket sessions follow framework connection
lifecycle. Document and narrowly suppress only proven contextual PMD warnings.

## Alternatives considered

### Adapter owns every client

- Benefits: One universal close path.
- Drawbacks: Closing an adapter could close an externally shared client.
- Reason not selected: Injection does not imply ownership transfer.

### Rely on implicit reclamation

- Benefits: No lifecycle API changes.
- Drawbacks: Shutdown and test cleanup are nondeterministic.
- Reason not selected: Explicit owner cleanup is reviewable and testable.

### Separate owned/borrowed adapter classes

- Benefits: Distinct type-level ownership.
- Drawbacks: Duplicate adapter surfaces for a small construction distinction.
- Reason not selected: Existing constructors plus explicit lifecycle semantics suffice.

## Rationale

The creator with the broadest intended lifetime controls closure. Spring owns the
application resource; standalone construction retains convenient use while making
cleanup explicit. Consumers do not close shared resources per request.

## Consequences

### Benefits

- Deterministic context shutdown and isolated test cleanup.
- Shared resource lifetime remains independent of individual adapter operations.

### Drawbacks and risks

- Standalone callers must close owned adapters.
- Graceful client close may wait for outstanding requests; existing request deadlines
  and cancellation remain, without introducing a separate shutdown timeout policy.

### Operational impact

No migration. Context shutdown now explicitly closes the shared client. Real test
fixtures close resources; mocks do not gain meaningless close interactions.

## Follow-up actions

- Implemented and verified the linked specification.

## References

- [Catalog boundaries](0004-catalog-component-boundaries.md)
- [Java 25 HttpClient lifecycle](https://docs.oracle.com/en/java/javase/25/docs/api/java.net.http/java/net/http/HttpClient.html)
