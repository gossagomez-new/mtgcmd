# Advisory Java static analysis

## Metadata

- Status: Completed
- Priority: Medium
- Priority rationale: Make Java code-quality findings visible during packaging without disrupting delivery.
- Depends on: [Build automation](build-automation.md)

## Summary

Run broad PMD analysis of production and test Java sources during packaging.
Rule violations are advisory, and every reported finding is written to a
human-readable findings log. The user approved comprehensive verification and
creation of the related ADR during planning.

## Goals

- Analyze Java sources automatically in the existing packaging paths.
- Preserve successful builds when PMD reports rule violations.
- Produce complete, discoverable reports on every successful build.

## Non-goals

- Fix existing findings, establish a blocking quality gate, or introduce CPD.
- Change application behavior, deployment, or frontend analysis.

## Background

The pre-design audit inspected Maven, the build wrapper, and Docker packaging.
Binding analysis only to verify would miss the wrapper's skip-test path and
Docker's package invocation. The audit of broad rules found 1,971 violations
across production and test sources; these are observations, not individually
confirmed defects. LoosePackageCoupling cannot run meaningfully without an
explicit allowed-package configuration and is excluded until one is defined.
No unrelated source remediation is included.

## Requirements

### Functional requirements

1. Pin Maven PMD Plugin 3.28.0 and configure Java 25 source analysis.
2. Bind the report goal to prepare-package so package and verify analyze main
   and test sources, including when test execution is skipped with -DskipTests.
3. Include all priorities across best practices, code style, design,
   documentation, error-prone code, multithreading, performance, and security.
   Exclude only the unconfigured LoosePackageCoupling rule.
4. Rule violations must not fail packaging. PMD execution and
   report-writing errors remain build failures.
5. Produce backend/target/pmd.txt, containing PMD's complete native text report:
   findings identify source file, line, rule, and message; native suppression
   records and diagnostics are retained when reported.
6. Produce backend/target/pmd.xml and backend/target/reports/pmd.html alongside
   the findings log. Replace previous reports rather than appending.
7. With no reported findings, still create an empty text log.
8. Both build-wrapper modes require a regular log file after Maven succeeds;
   absence exits 1 before any success message. Print the log's absolute path.
9. The Docker backend build stage generates the same reports. Reports remain
   build artifacts and are not bundled in the application jar or runtime image.

### Non-functional requirements

- Reuse PMD's renderer rather than scraping potentially incomplete console output.
- Store rules inside backend so Docker's existing copy includes them.
- Preserve existing build arguments, environment forwarding, and path handling.

## Contracts and data

No application API or persistence changes. The wrapper's successful output adds
`PMD findings log: <absolute path>`. A zero exit status permits advisory
violations but requires the application artifact and PMD text log to exist.
Report paths are generated, ignored files; clean removes them. Direct Maven
execution writes the same reports without the wrapper's final path announcement.

## Technical constraints

Use the existing Java 25/Maven build and native txt reporting. Do not bind the
violation-checking goal. A standalone report can be generated using
`mvn -f backend/pom.xml test-compile pmd:pmd` without running unit tests.

## Invariants

- Violations alone never change the packaging exit status.
- Skipping test execution does not skip test-source analysis.
- A missing log cannot be represented as a complete wrapper build.
- Application behavior and persisted state remain unchanged.

## Edge cases and failures

Empty reports are valid. Existing violations and suppressed findings remain
visible rather than being removed through a baseline. Invalid rulesets,
dependency/environment problems, and unwritable reports must not be confused
with successful complete analysis. Reports from repeated runs must not retain
stale findings. Paths with spaces and calls outside the repository still work.

## Migration and compatibility

No data migration. Packaging adds analysis time and a build-plugin dependency.
Existing wrapper commands and Docker packaging commands remain compatible.
The runtime artifact does not acquire a PMD dependency.

## Acceptance criteria

- [x] Default, skipped-test, and direct Maven packaging succeed with findings.
- [x] Main/test findings are fully represented in text and XML reports.
- [x] Empty and repeated reports behave correctly.
- [x] Analysis/report-writing errors and missing wrapper logs prevent success.
- [x] Both wrapper modes print the absolute findings-log path.
- [x] Docker backend-stage reports are verified, or environment restrictions reported.
- [x] Formatting, script checks, and the normal verified build pass.

## Verification

Confirmed target: comprehensive coverage, selected by the user during planning.
Build-wrapper tests use isolated repositories and stub tools to verify log
guards, empty/nonempty logs, mode behavior, path handling, and existing failures.
Real Maven fixtures use the backend plugin and ruleset, exercise Java 25
constructor syntax, assert main/test findings and text/XML counts, check
suppression records, repeat standalone generation, verify genuinely empty
reports, and exercise invalid rulesets and report-writing failure. Fixtures do
not modify application sources or rely on their current finding count.

Run `node --test --test-isolation=none scripts/build.test.mjs` and
`node --test --test-isolation=none scripts/pmd.test.mjs`. Run Bash syntax,
ShellCheck, changed JavaScript formatting checks, Spotless, the default build,
and the skipped-test build. Compare actual report counts and inspect the jar
for report exclusions. Verify the Docker backend stage when daemon access is
available; report a blocked check without claiming it passed.

Executed on 2026-10-14: all 19 build-wrapper tests and three real Maven
integration tests passed. New wrapper assertions first failed for missing log
guards/path reporting; fixture tests first failed for absent PMD configuration,
while the 13 existing wrapper tests continued to pass. Bash syntax, ShellCheck,
changed JavaScript formatting, Spotless, and git diff whitespace checks passed.
The default build passed all 164 backend tests and packaged the frontend;
the skipped-test build also succeeded and generated reports. Every one of the
1,971 XML findings (1,262 main and 709 test) was matched to its exact text entry,
with no processing/configuration errors. Jar inspection found no PMD reports
or PMD runtime dependencies. Standalone generation with test-compile pmd:pmd
passed against the application; repeated/empty reports were also exercised by
the Maven fixtures.

The first default build was blocked by sandbox local-port restrictions; the
escalated retry passed. Docker daemon access was denied both normally and after
an escalated retry, so the backend container stage could not be executed. Its
existing package -DskipTests path was verified through local Maven packaging;
container-specific execution remains unverified. Gameplay/browser checks were
not run because application behavior did not change.

## Architecture decisions

- [ADR-0003: Advisory Java static analysis](../docs/adr/0003-advisory-java-static-analysis.md)
  records the build dependency and advisory convention.

## Open questions

None.
