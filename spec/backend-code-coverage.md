# Backend code coverage

## Metadata

- Status: Completed
- Priority: Medium
- Priority rationale: Establish visible backend coverage before considering quality gates.
- Depends on: [Build automation](build-automation.md), [GitHub Flow](github-flow.md), [Local verification](local-verification.md)

## Summary

Add pinned JaCoCo Maven instrumentation and production-code coverage reports.

## Goals

- Measure existing Surefire tests on Java 25 and expose local/CI reports.
- Preserve JVM options, test failure behavior, and production packaging.

## Non-goals

- Minimum coverage gate, new tests to raise application coverage, external coverage
  services, browser-server instrumentation, or runtime application dependencies.

## Background

Surefire currently supplies a literal native-access argLine. Naive prepare-agent
integration would lose instrumentation or replace that flag. The pre-design audit
identified preserving JVM arguments and distinguishing Surefire from Maven-exec
browser servers as required within-scope constraints. Existing canonical builds
use clean verify; skipped-test packaging uses clean package -DskipTests.

## Requirements

1. Pin JaCoCo 0.8.15, supporting Java 25, as a build-only plugin.
2. Collect target/jacoco.exec in forked Surefire tests with a dedicated agent
   property and late property evaluation. Preserve native-access and user argLine.
3. Generate HTML, XML, and CSV production-class reports during verify in
   backend/target/site/jacoco. Maven test collects data without generating reports.
4. Include production classes, including uncovered code, without blanket exclusions.
   Test classes and dependencies are not report subjects.
5. Coverage is reporting-only: no minimum gate. Tests and plugin/report failures
   still fail the build. A failed test run is not a complete coverage report.
6. Document fresh clean verify, focused-test scope, jacoco.skip, and skipped-test
   behavior. Non-clean execution may retain/reuse previous data and reports; their
   presence is not evidence that tests ran. Clean skipped builds create no coverage.
7. Preserve existing PMD/Spotless/package behavior and user JVM options. Keep agents,
   execution data, and reports outside the production jar and runtime image.
8. Retain coverage reports and execution data in the existing CI artifact upload.
   Add real Maven fixture checks to CI and the existing local verification profile.
9. Document commands, report paths, limitations, and measured initial baseline.

## Contracts and data

Execution data: backend/target/jacoco.exec. Reports:
backend/target/site/jacoco/index.html, jacoco.xml, jacoco.csv. JaCoCo's native
counter semantics apply; coverage does not prove correctness. Scope is tests
executed by Surefire, not separately launched browser servers or packaged smoke JVMs.

## Technical constraints

Use a named JaCoCo agent property and an empty fallback so disabled instrumentation
cannot leave a literal late-evaluation token in the test JVM command. Preserve
--enable-native-access=ALL-UNNAMED and explicit caller -DargLine options. Reuse
existing tooling commands and artifact retention; add no coverage-service credentials.

## Invariants

- No coverage threshold or excluded production package hides uncovered behavior.
- Skipping tests does not imply coverage validation.
- No version bump, publication, or deployment in this implementation.

## Edge cases and failures

Clean/non-clean skipped builds, instrumentation disabled, failed tests, report-write
failure, stale execution data, focused test selection, supplied JVM arguments, and
zero coverage of a production method must have documented/verified outcomes.

## Migration and compatibility

Build-only compatible maintenance. Existing command entry points remain valid;
Maven verify additionally produces coverage. First execution may download JaCoCo.

## Acceptance criteria

- [x] Real Java 25 fixture and backend suite produce usable nonempty coverage.
- [x] Reports retain uncovered code without failing a coverage gate.
- [x] JVM arguments and failure/skip semantics are verified.
- [x] CI/local checks and documentation expose the report scope correctly.

## Verification

User confirmed comprehensive coverage: Java 25 instrumentation, HTML/XML/CSV reports,
preserved JVM arguments, test/verify lifecycles, skipped-test/stale-data behavior,
packaging, and CI artifact configuration. Add fixture tests before POM changes,
confirm expected failures, then verify green. Run existing wrapper/PMD tests,
Python runner tests, backend Spotless and full verify, packaging/coverage inspection,
workflow validation when available, specification validation, and changed-file
formatting. No application coverage percentage is required to pass.

### Executed checks

- TDD: fixture tests failed before POM integration for missing execution data and
  caller JVM arguments; the 23 existing wrapper tests remained green. Initial
  JaCoCo dependency resolution was sandbox-blocked and was rerun with network access.
- All six real Maven coverage fixtures passed: Java 25 compilation/instrumentation,
  test/verify reports, uncovered production methods, skipped/stale data, disabled
  instrumentation, native/caller arguments, blocking test/report errors, corrupt
  execution data, packaging, and CI/local-profile configuration.
- Clean backend Spotless/verify succeeded: all 571 tests passed, PMD completed,
  executable jar packaged, and JaCoCo analyzed 73 production classes.
- Initial counters: lines 1,546 covered / 45 missed (97.17%); branches 589 covered /
  93 missed (86.36%); instructions 7,809 covered / 236 missed (97.07%). These are
  full Surefire baseline observations, not quality gates.
- Packaged jar inspection found no coverage agent, execution data, reports, or
  test-support classes. Dockerfile inspection confirms only the jar enters runtime;
  no Docker rebuild was performed.
- All 26 existing wrapper/PMD tests and all 89 Python tooling tests passed.
- Workflow YAML parsing and checks for retained artifacts, fixture invocation,
  stable verify job, and read-only permissions passed. Dedicated actionlint was
  unavailable; actual hosted CI execution remains pending publication.
- Specification/index validation, changed-file Markdown/JS/YAML formatting, and
  diff whitespace checks passed. Browser/frontend suites were not rerun because
  frontend sources/protocols and production behavior did not change.

## Architecture decisions

[ADR-0018](../docs/adr/0018-backend-code-coverage.md) records approved reporting-only
JaCoCo and JVM integration, extending existing build and GitHub Flow conventions.

## Open questions

None.
