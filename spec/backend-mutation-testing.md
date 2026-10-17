# Backend mutation testing

## Metadata

- Status: Completed
- Priority: Medium
- Priority rationale: Measure test fault detection beyond execution coverage without slowing normal builds.
- Depends on: [Backend code coverage](backend-code-coverage.md), [Build automation](build-automation.md), [GitHub Flow](github-flow.md)

## Summary

Add opt-in PIT mutation testing with JUnit 5 support and manual CI reports.

## Goals

- Execute mutation testing on Java 25 and distinguish killed, surviving, and uncovered mutants.
- Keep normal builds and JaCoCo independent, with no initial mutation-score gate.

## Non-goals

- Automatically improving the application tests, mandatory PR mutation scores,
  external services, production instrumentation, or a new local verification profile.

## Background

The pre-design audit identified separate PIT worker JVM arguments, real database
and server test resources, and agent coexistence as required within-scope constraints.
The user selected opt-in Maven execution, manual CI, reporting-only scores, and
comprehensive verification. Work starts on the verified JaCoCo branch while its
PR is pending; this does not merge the remote PR.

## Requirements

1. Pin PIT 1.30.1 with the JUnit 5 adapter 1.2.3 as build-only dependencies inside
   an inactive-by-default mutation Maven profile.
2. Explicitly invoke test-compile and pitest:mutationCoverage using that profile;
   normal test/package/verify and profile activation alone must not run PIT.
3. Default to all application production classes and all application tests, using
   native DEFAULTS mutators and one worker. Permit explicit target/test/thread/JVM
   overrides for focused runs; report focused results as focused, not whole-backend.
4. Disable JaCoCo in the profile and avoid importing Surefire's late agent argLine
   into PIT. Supply native-access arguments to worker JVMs, with documented overrides.
5. Produce HTML/XML reports in backend/target/pit-reports without timestamp subfolders.
   Reports are ignored artifacts, removed by clean. Failed/non-clean runs may leave
   stale or partial data; only successful clean execution establishes a fresh result.
6. Surviving/uncovered mutants do not fail a score gate. Unmutated test failures,
   tool failures, and no matching mutations must fail the invocation. Report timeouts
   and execution errors separately; do not equate them with assertion-based kills.
7. Add a workflow_dispatch-only CI workflow with literal scoped inputs passed through
   environment variables and quoted arguments, read-only permissions, bounded runtime,
   and report/log retention. It must not run on PRs/push or deploy/publish artifacts
   outside normal verification-report retention. Document default-branch activation.
8. Add small real PIT fixtures to existing integration-check commands in CI and the
   full local verification profile. These validate tooling, not whole-backend scores.
9. Keep PIT dependencies and reports out of the runtime jar/image. Document commands,
   scope, defaults, runtime cost, failure semantics, and observed baseline results.

### Test-harness compatibility prerequisite

The full PIT baseline exposed an invalid zero-body event in JsonHttpTest:
responseSubscriberEnforcesExactBoundary(0) delivered an empty ByteBuffer to the
JDK byte-array subscriber. With JDK HTTP assertions enabled this fails before
mutation. Correct the fixture to omit onNext for zero bytes and still complete
normally. Preserve all zero/exact-limit/overflow and cancellation assertions;
change no production handling or test/target selection. A targeted Surefire run
with -ea:jdk.internal.net.http... reproduces the failure before correction.

## Contracts and data

Profile: mutation. Native report files: target/pit-reports/index.html and mutations.xml.
Target globs, test selection, mutator set, source revision, and successful invocation
are required context for interpreting scores. Tests still require non-root local
port/process permissions. Mutation score is not the same measure as JaCoCo coverage.

## Technical constraints

No production code changes or blanket exclusions to improve scores. Test native
Java 25 compilation, JUnit discovery/parameterization, agent isolation and custom
worker JVM options. The workflow must not interpolate raw input into shell code.

## Invariants

- Ordinary Maven builds do not run mutation testing or lose JaCoCo instrumentation.
- No minimum mutation threshold and no deployment/version bump.
- New workflow dispatch or publication requires separate authorization.

## Edge cases and failures

Weak assertions, uncovered code, strong assertions, nested classes and glob scope,
empty targets, red baseline tests, worker timeouts, stale reports, disabled profiles,
custom JVM options, and separately started resource-using tests need explicit handling.

## Migration and compatibility

Compatible build-tooling maintenance, no runtime contracts changed. First opt-in
run downloads PIT and its adapter. The new workflow becomes dispatchable after it
is on GitHub's default branch; this implementation does not trigger hosted runs.

## Acceptance criteria

- [x] Fixture mutations produce killed, surviving, and uncovered statuses on Java 25.
- [x] Real backend runs succeed with explicit scope and no JaCoCo agent interference.
- [x] Failures/default-build isolation and report scope are verified.
- [x] Manual CI, local fixture checks, and documentation agree.

## Verification

User confirmed comprehensive coverage: Java 25/JUnit discovery, killed/surviving/
uncovered mutants, real backend mutation runs, JVM arguments, failure/empty-target
behavior, report freshness, packaging, and CI isolation. Add real fixture tests
first and confirm missing-integration failures, then implement. Run a full backend
mutation analysis if practical; otherwise record precise successfully measured
production scopes and remaining full-run limitations. Also run normal backend
verify/JaCoCo, existing integration fixtures, Python runner tests, workflow checks,
formatting, and specification validation. No score threshold is required.

### Executed checks

On 2026-10-17, Java 25, based on commit 980bfad plus this working-tree change:

- Confirmed missing-integration failures before implementation: real PIT workers
  could not run JUnit 5 tests without the adapter, and profile/configuration checks
  failed. The normal-build fixture remained green.
- All six real Maven/PIT fixture tests passed, including parameterized/nested
  tests, killed/surviving/uncovered mutations, custom worker arguments, clean
  replacement, baseline/empty-target/output failures, normal JaCoCo packaging,
  and activation-only isolation.
- Clean backend Spotless/verify passed all 571 tests and generated JaCoCo reports.
  Runtime jar inspection found no PIT/JaCoCo dependencies or report files.
- Existing build-wrapper, PMD, and JaCoCo fixtures passed: 32 tests. Python tooling
  tests passed: 89 tests.
- The zero-body fixture prerequisite was reproduced with JDK HTTP assertions
  before correction; all 35 JsonHttp tests passed with the same assertions afterward.
- Workflow YAML/trigger/permissions/input handling and artifact configuration were
  inspected locally. Hosted Actions execution remains pending default-branch
  integration and separately authorized dispatch; no hosted run was claimed.
- Frontend/browser and Docker execution were outside this build-only change.

- Formatting, whitespace, and offline specification validation passed (36 specifications).

### Initial full-backend baseline

Successful clean invocation used the mutation profile with default production/test
globs dev.mtgcmd.*, DEFAULTS mutators, one worker, PIT 1.30.1 and adapter 1.2.3.
Maven completed in 14 minutes 22 seconds, including compilation and baseline
analysis. PIT examined 111 test classes and ran 6,277 selected test executions
across 818 mutants in 51 mutated classes:

| Status      | Count |
| ----------- | ----- |
| KILLED      | 648   |
| SURVIVED    | 154   |
| NO_COVERAGE | 15    |
| TIMED_OUT   | 1     |

No RUN_ERROR, MEMORY_ERROR, NON_VIABLE, or unfinished statuses were recorded.
PIT reports 649 detected (79%), including the timeout, and 81% test strength;
only 648 are KILLED results. The timeout removed a conditional in
LifecycleActions.concede at line 99; its meaning needs separate review.
HTML/XML reports exist and parse successfully. No jacoco.exec was produced by
the clean mutation run. These observations establish a historical baseline,
not thresholds or proof that every survivor is a missing assertion. Earlier
baseline failures from the zero-body fixture are diagnostic attempts, not scores.

## Architecture decisions

[ADR-0019](../docs/adr/0019-backend-mutation-testing.md) records opt-in/reporting-only
mutation testing and separation from [JaCoCo](../docs/adr/0018-backend-code-coverage.md).

## Open questions

None.
