# Frontend-first build automation

## Metadata

- Status: Completed
- Priority: Medium
- Priority rationale: Make packaging repeatable and remove stale bundled assets.
- Depends on: [Commander tabletop](commander-tabletop.md)

## Summary

Add a small Bash wrapper around the existing npm and Maven builds. The user
approved implementation of this design, verification by default, and
risk-focused test coverage.

## Goals

- Build React before packaging Java and report the executable jar's location.
- Remove stale backend build outputs using Maven's clean lifecycle.
- Stop on failures without reporting an unverified build as successful.

## Non-goals

- Dependency installation, environment diagnosis, deployment, service startup,
  live imports, browser checks, or a full-stack verification runner.
- New skills, application changes, or broader platform/symlink-launch support.

## Background

A focused pre-design audit inspected the build configuration and documented
commands. Maven copies frontend output into `target/classes/static`; incremental
packaging previously retained old frontend bundles. This finding is directly
within the approved proposal. Reusing npm build and Maven clean avoids custom
asset cleanup. Clean builds cost recompilation but produce predictable outputs.

## Requirements

### Functional requirements

1. `./scripts/build.sh` runs frontend build followed by Maven `clean verify`.
2. `--skip-tests` runs frontend build then Maven `clean package -DskipTests`,
   explicitly reporting that Java tests were skipped.
3. `--help` prints usage and exits successfully without invoking build tools.
4. Unsupported or extra arguments print usage and exit 2 before building.
5. Determine the repository from the script location; support other working
   directories and repository paths containing spaces.
6. Require `frontend/dist/index.html` after frontend success and before Maven.
7. Preserve build-tool output and environment settings, including `MAVEN_OPTS`.
8. A build-tool failure terminates the script with that tool's exit status.
9. Require exactly one regular `.jar` in `backend/target` after Maven success;
   missing/ambiguous output exits 1. Report its absolute path without a fixed
   application version. No success message precedes these checks.
10. As extended by [Advisory Java static analysis](java-static-analysis.md),
    require backend/target/pmd.txt and report its absolute path. An empty log is
    valid; PMD findings are advisory.

### Non-functional requirements

- Bash on Linux, Java 25, Maven, Node 24, and installed dependencies.
- Use existing commands, no new dependencies or tool version management.

## Contracts and data

CLI: `build.sh [--skip-tests]` or `build.sh --help`. Options cannot be combined
or repeated. Exit 0 means the requested build succeeded with a unique artifact;
exit 2 means usage error; guard failures exit 1; tool failures retain their code.
Success reports either Java verification completed or Java tests skipped, plus
the artifact and PMD findings-log paths. Frontend unit tests/lint/browser checks
are separate.

## Technical constraints

Resolve paths relative to the executed script, quote all paths, and run tools
synchronously. Maven clean manages generated backend outputs; the script must
not clean application data, database volumes, or unrelated directories.

## Invariants

- Maven never runs after a failed frontend build or missing generated index.
- Failure and skipped testing never appear as verified success.
- Application contracts and persisted state remain unchanged.

## Edge cases and failures

Invalid/extra options, missing build tools, failed builds, missing frontend
output, missing jars, and multiple jars fail explicitly. Paths with spaces and
calls outside the repository work. Dependency download or local-port restrictions
remain environment failures, not evidence of broken application behavior.

## Migration and compatibility

The wrapper becomes the documented canonical build command. Underlying npm and
Maven commands remain available for troubleshooting. No data migration.

## Acceptance criteria

- [x] Default invocation builds frontend then verifies a clean Java package.
- [x] Skip mode packages and accurately discloses skipped Java tests.
- [x] Help and invalid arguments invoke neither build tool.
- [x] Failures and missing/ambiguous output cannot report success.
- [x] Other working directories and paths containing spaces work.
- [x] A real build and offline packaged-jar smoke check pass.

## Verification

Confirmed target: risk-focused coverage, selected by the user during planning.
Node's built-in test runner uses isolated temporary repositories and stub tools
to exercise ordering, default/skip arguments, paths, environment inheritance,
output propagation, early termination, help/invalid arguments, and artifact
guards. Demonstrate failing assertions before implementation, then passing
tests. Run Bash syntax checks, one real default build, the existing JarSmoke
helper without live imports, and inspect the packaged frontend assets and
absence of test-only classes. No additional gameplay/browser testing required.

Executed on 2026-10-13: 13 isolated script tests passed after expected initial
failures. Bash syntax, ShellCheck, and JavaScript formatting passed. The real
default wrapper invocation built React (including TypeScript checks), passed all
21 Java tests, and produced the executable jar. JarSmoke passed without live
imports. Artifact inspection found only the current frontend index/JS/CSS and
no test-support classes. No additional gameplay/browser scenarios were run for
this build-only change.

## Architecture decisions

The original wrapper required no ADR. Its PMD extension follows
[ADR-0003](../docs/adr/0003-advisory-java-static-analysis.md); requirements and
verification for that dependency belong to the linked static-analysis specification.

## Open questions

None.
