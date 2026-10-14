# ADR-0003: Advisory Java static analysis

- Status: Accepted
- Date: 2026-10-14
- Related specification: [Advisory Java static analysis](../../spec/java-static-analysis.md)

## Context

Java packaging currently compiles and tests code without a reusable static
analysis report. The user wants broad PMD analysis of production and test
sources, complete findings logs, and successful builds despite rule violations.
A broad initial audit reported 1,971 violations, many involving style or
intentional framework/test patterns. These results require review before cleanup.
Packaging occurs through verify, package with skipped tests, and Docker.

## Decision

Introduce pinned Maven PMD Plugin 3.28.0 and its bundled PMD engine, configured
for Java 25. Bind the report goal to prepare-package, including test sources
and all rule priorities. Use all eight Java categories, excluding the
unconfigured LoosePackageCoupling rule.

Rule violations are advisory. Execution and report-writing failures remain
errors because they prevent complete analysis. Use PMD's native text renderer
for backend/target/pmd.txt and retain XML/HTML companion reports. The wrapper
requires the log and announces its absolute path. Reports are regenerated build
artifacts, excluded from runtime packaging.

## Alternatives considered

### Blocking broad rule gate

- Benefits: prevents newly delivered code from violating enabled rules.
- Drawbacks: existing findings immediately prevent packaging and require large
  cleanup or baseline exclusions; findings are not uniformly actionable.
- Reason not selected: the user explicitly chose advisory analysis.

### Focused rules or a priority-based gate

- Benefits: fewer findings and a smaller initial remediation burden.
- Drawbacks: hides broader maintainability observations or blocks selected findings.
- Reason not selected: the user chose broad coverage without a violation gate.

### Capture Maven console output in the wrapper

- Benefits: a simple wrapper-only log.
- Drawbacks: console summaries can omit findings and direct Maven/Docker builds
  do not pass through the wrapper.
- Reason not selected: native Maven-side reports provide complete shared output.

## Rationale

An advisory report improves visibility while allowing packaging to continue.
Prepare-package covers the existing build paths without changing their commands.
Native reporting avoids a custom renderer or parsing pipeline and retains
machine-readable results for future tooling.

## Consequences

### Benefits

- Developers receive broad, inspectable analysis with each package.
- Production and test code are analyzed consistently.
- Existing findings do not disrupt builds or require immediate cleanup.

### Drawbacks and risks

- Broad rules generate noise and require contextual review.
- Advisory findings do not enforce future compliance.
- Packaging takes additional time and downloads a new build dependency.

### Operational impact

- Reports live under backend/target and are removed by clean.
- Docker reports are available in the backend build stage, not the runtime image.
- Future rule/version changes should review report differences and compatibility.

## Follow-up actions

- Review findings separately before proposing source remediation.
- Configure package-boundary analysis only when allowed dependencies are defined.

## References

- [Maven PMD Plugin report goal](https://maven.apache.org/plugins/maven-pmd-plugin/pmd-mojo.html)
- [PMD Java rules](https://docs.pmd-code.org/pmd-doc-7.17.0/pmd_rules_java.html)
- [Build automation specification](../../spec/build-automation.md)
