# ADR-0018: Reporting-only backend coverage with JaCoCo

- Status: Accepted
- Date: 2026-10-17
- Related specification: [Backend code coverage](../../spec/backend-code-coverage.md)

## Context

The Java 25 backend has a substantial Surefire suite, Maven packaging, advisory
PMD analysis, and CI report retention, but no coverage measurement. Surefire
already requires native-access arguments. Browser scenarios launch a separate
backend through Maven exec and are outside Surefire's JVM. The user approved
JaCoCo, comprehensive verification, and reporting without an initial coverage gate.

## Decision

Use pinned JaCoCo 0.8.15 as a build-only Maven plugin. Prepare a dedicated agent
property consumed with Surefire late evaluation, preserving existing native-access
and caller JVM options. Generate native HTML/XML/CSV reports at verify and retain
them with the existing CI artifacts. Measure production classes exercised by the
Surefire suite, with no blanket exclusions or minimum coverage threshold.

Use clean verify for a fresh full baseline. Non-clean runs can reuse prior data;
skipped/failed/focused executions must not be described as fresh full-suite coverage.
Do not instrument production processes or separately launched browser servers.
The contribution/backend guides provide operational commands and report limitations.

## Alternatives considered

### Immediate minimum coverage gate

- Benefits: Prevents selected coverage regressions.
- Drawbacks: A threshold chosen before a measured baseline can block builds or
  encourage low-value tests and exclusions.
- Reason not selected: Establish visibility first; select any future gate explicitly.

### External coverage service

- Benefits: Hosted diff coverage and historical visualizations.
- Drawbacks: Additional service, credentials, external uploads, and configuration.
- Reason not selected: Native reports and current CI artifacts meet the initial need.

### Instrument every backend launch

- Benefits: Can include browser and smoke-test execution.
- Drawbacks: Cross-process data coordination and reporting scope become harder
  to reproduce; production launch settings would need additional ownership rules.
- Reason not selected: Keep a clear Surefire-only measurement boundary.

## Rationale

Maven lifecycle integration reuses the existing test/build entry points. A dedicated
agent property avoids competing ownership of Surefire's argLine. Native reports
provide detail without a new service, while reporting-only operation makes the
baseline available before choosing enforcement policy.

## Consequences

### Benefits

- Coverage is visible locally and in CI with Java 25 support.
- Existing test failures remain blocking; uncovered code remains visible.
- Production dependencies and runtime behavior are unchanged.

### Drawbacks and risks

- Instrumentation adds test runtime and memory overhead.
- Non-clean data can be stale or cumulative; fresh claims require clean execution.
- Browser and standalone smoke coverage are not included. A future gate or combined
  cross-process report requires a separate proposal.

### Operational impact

First builds download the plugin/agent. Reports remain ignored build artifacts,
removed by clean. No application version, publication, or deployment change.

## Follow-up actions

- [x] Record the initial full-suite baseline and verification results in the
      [specification](../../spec/backend-code-coverage.md#verification) and backend guide.

## References

- [JaCoCo change history](https://www.jacoco.org/jacoco/trunk/doc/changes.html)
- [Agent preparation](https://www.jacoco.org/jacoco/trunk/doc/prepare-agent-mojo.html)
- [Native reports](https://www.jacoco.org/jacoco/trunk/doc/report-mojo.html)
- [GitHub Flow](0012-github-flow.md)
