# HTTP and test resource ownership

## Metadata

- Status: Completed
- Priority: Medium
- Priority rationale: Make lifecycle ownership explicit and provide deterministic cleanup.
- Depends on: [JsonHttp request lifecycle](json-http-send-refactor.md)

## Summary

Spring owns the injected HTTP client; JsonHttp borrows it. Standalone adapters own
internally constructed clients and provide explicit cleanup. Close real test clients,
protect fixture setup and await executor termination; retain framework-managed
WebSocket lifetimes and narrowly suppress only demonstrated contextual warnings.

## Goals

- Deterministic shutdown of real HTTP clients and test executors.
- Explicit borrowed/owned lifecycle semantics without closing shared clients per request.
- Evidence-backed treatment of CloseResource warnings.

## Non-goals

- Request pacing/concurrency changes, new transport dependencies, or broad PMD suppression.
- Closing player connections after individual messages or closing mocks just for PMD.

## Background

The audit found real WebTest/JarSmoke clients without explicit closure and executor
setup outside the protected scope, despite existing shutdownNow cleanup. JsonHttp
creates a private real client that Spring cannot independently dispose. Mock clients
have no real resources; WebSocket decorators share framework connection lifetime.
Ownership changes are within the approved scope. The user approved comprehensive
verification and ADR-0006. Keep provider behavior and lifecycle serialization intact.

## Requirements

### Functional requirements

1. Spring creates a shared configured HttpClient (8-second connect timeout, no
   redirects) and closes it at context shutdown, including failed context startup.
2. JsonHttp accepts an externally owned client, preserving defaults and existing
   construction APIs. Closing a borrowed adapter does not close or disable that client.
3. Internally created clients belong to standalone JsonHttp instances. Owned close
   is idempotent, retains errors, and prevents new requests after closure. Keep close
   and request admission serialized; complete an admitted request before owned close.
4. Invalid construction limits must not allocate an owned client. Invalid borrowed
   construction must not close the caller's resource.
5. Real clients in HTTP tests and jar smoke use try-with-resources. Owned adapters in
   local HTTP fixtures and smoke imports are closed after use. Preserve test results.
6. Executor setup is protected by cleanup. Stop HTTP servers before interrupting pools;
   await termination with a bounded deadline and report failure rather than ignore it.
7. Keep sockets open for their framework connection lifecycle; normal closure removes
   registry entries and failures close sessions. Verify browser restart/reconnect.
8. Suppress CloseResource only at specific demonstrated mock/framework ownership or verified cleanup sites
   with a reason. No ruleset changes or blanket class-level suppressions.

### Non-functional requirements

- Preserve timeouts, redirects, bounded bodies, request locking, and typed causes.
- Preserve real cleanup failures and existing shutdown ordering.

## Contracts and data

JsonHttp becomes AutoCloseable. Owned constructors transfer only internally allocated
client ownership. The new external-client constructor borrows the client. Owned close
rejects subsequent requests with IllegalStateException("HTTP adapter is closed");
borrowed close is a no-op. Spring lifecycle configuration owns the shared client.
No API/schema/persistence changes.

## Technical constraints

Use Java 25 HttpClient.close, Spring bean destruction, and existing local fixtures.
Do not hold socket resources in method-local try-with-resources scopes.

## Invariants

- Shared clients remain reusable across requests and adapter close.
- Owner closure happens once; failed/invalid setup cannot orphan created resources.
- Executor and server cleanup runs on both normal and assertion-failure paths.
- No premature WebSocket closure or hidden registry retention after close callbacks.

## Edge cases and failures

Repeated close, close failure, invalid configuration, context startup failure,
setup failures, failed requests, outstanding requests during shutdown, mocks and
borrowed resources, normal and failed socket closure.

## Migration and compatibility

Existing request behavior and constructors remain compatible. Standalone callers
should close owned adapters; injected callers leave client closure to Spring.
Application shutdown adds explicit graceful client closure, which can wait for
outstanding work; no new shutdown deadline policy is introduced.

## Acceptance criteria

- [x] Ownership tests fail for missing behavior, then pass with implementation.
- [x] Spring and standalone cleanup follow the approved ownership convention.
- [x] Real test resources close deterministically and sockets retain correct lifetimes.
- [x] Contextual suppressions are narrow and justified; fresh PMD inspected.
- [x] Full backend, formatting, packaging, jar smoke, and browser flows pass.

## Verification

Confirmed target: comprehensive coverage. Add owned/borrowed close tests, reuse,
idempotence/failure, invalid construction, serialization with in-flight work,
Spring configured client/shutdown/startup failure, and socket registry/lifetime
checks. Retain request and diagnostics suites. Run focused tests, full backend
verify/Spotless, native PMD, jar smoke without live imports, browser reconnect
flows, and Markdown/link checks. Record actual results and blocked checks.

### Executed verification

- Red ownership contracts: 63 focused cases ran; nine expected ownership failures,
  while 54 transport/diagnostic/lifecycle cases passed. No syntax or environment
  failure was used as the red signal.
- Owned/borrowed close, allocation-free invalid construction, repeated/failing
  close, in-flight serialization, Spring configuration and failed-startup cleanup,
  and normal socket registry removal pass. Existing failure-close tests remain green.
- Full `spotless:apply spotless:check verify` passed: 497 backend tests, no
  failures/errors/skips, successful formatting and packaging.
- Fresh PMD reports zero active CloseResource findings, zero analysis diagnostics,
  and 28 explicitly documented contextual suppressions retained in native XML.
  No new production cognitive/cyclomatic/GodClass findings were introduced.
- Packaged jar smoke passed without live provider requests.
- All five browser flows passed, including Commander/Standard durable reconnect
  after closing/restarting the application context.
- Markdown formatting, local documentation references, and whitespace checks passed.

## Architecture decisions

[ADR-0006](../docs/adr/0006-http-client-resource-ownership.md) records lifecycle ownership.
[ADR-0004](../docs/adr/0004-catalog-component-boundaries.md) retains provider adapter boundaries.

## Open questions

None.
