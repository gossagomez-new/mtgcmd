# ADR-0019: Opt-in backend mutation testing with PIT

- Status: Accepted
- Date: 2026-10-17
- Related specification: [Backend mutation testing](../../spec/backend-mutation-testing.md)

## Context

JaCoCo measures test execution but cannot determine whether assertions detect
changed behavior. The Java 25 backend uses JUnit 5, Mockito, embedded PostgreSQL,
and network fixtures. Mutation testing repeats tests against many modified classes,
so it has a different runtime and JVM lifecycle from normal verification. The user
approved opt-in Maven/manual CI execution, reporting-only scores, comprehensive
verification, and this ADR.

## Decision

Pin PIT 1.30.1 and its JUnit 5 adapter 1.2.3 in an opt-in mutation Maven profile.
Invoke mutationCoverage explicitly after test compilation. Keep ordinary builds
unchanged, default to all application classes/tests and one worker, and allow
focused target/test/worker/JVM selection. Use native DEFAULTS mutators and HTML/XML
reports, without score gates or blanket production exclusions.

Disable JaCoCo within this profile and give PIT explicit native-access JVM arguments
instead of importing Surefire's agent-bearing argLine. Keep fresh runs clean and
history-independent. Add a manually triggered, bounded CI workflow with retained
reports. Ordinary CI and the existing full local profile run only small tooling
fixtures, not a full project mutation campaign.

## Alternatives considered

### Mutation testing on every verify/PR

- Benefits: Immediate visibility into mutation regressions.
- Drawbacks: Repeated database/server tests and thousands of mutants can add
  substantial runtime and intermittent resource failures to normal work.
- Reason not selected: Establish repeatable opt-in measurement before choosing a gate.

### Mutation-score threshold immediately

- Benefits: Enforces a numeric floor.
- Drawbacks: Unreviewed survivors, equivalent mutants, and worker failures make a
  threshold misleading before an initial baseline is understood.
- Reason not selected: Keep scores advisory while failing real execution/baseline errors.

### External mutation-testing service

- Benefits: Hosted comparisons and automation.
- Drawbacks: Additional service, permissions, and publication concerns.
- Reason not selected: Native reports and existing GitHub tooling meet the current need.

## Rationale

Explicit execution keeps mutation cost separate from ordinary coverage and build
verification. Independent worker JVM arguments prevent accidental agent conflicts.
One worker reduces contention for embedded databases and sockets. Retained native
reports preserve actionable survivors without forcing cosmetic coverage improvements.

## Consequences

### Benefits

- Test fault detection becomes inspectable alongside JaCoCo execution coverage.
- No production dependency or mandatory build-time mutation cost.
- Scopes and failure semantics are explicit and reproducible.

### Drawbacks and risks

- Full runs can be expensive; narrowed results cannot represent the whole backend.
- Equivalent mutants, timeouts, and worker errors need contextual review.
- Reporting-only operation requires intentional review of surviving mutants.

### Operational impact

First opt-in execution downloads tools and starts child JVMs. Database/network tests
still need local permissions. Reports stay ignored and out of runtime packaging.
Manual workflow availability requires integration into the default branch. This
change does not authorize workflow dispatch, release, deployment, or image publishing.

## Follow-up actions

- [x] Record measured scopes/results and verification limitations in the specification.

## References

- [PIT Maven documentation](https://pitest.org/quickstart/maven/)
- [PIT releases](https://github.com/hcoles/pitest/releases)
- [JUnit 5 adapter](https://github.com/pitest/pitest-junit5-plugin)
- [JaCoCo coverage decision](0018-backend-code-coverage.md)
