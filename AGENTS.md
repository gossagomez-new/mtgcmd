# AGENTS.md

This file defines the development, documentation, and collaboration rules for
this repository. These instructions apply to the entire repository unless a
more specific `AGENTS.md` exists in a subdirectory.

## Project commands

Run commands from the repository root unless otherwise noted. Java 25,
Maven, and Node 24 are required. Backend tests start a real embedded PostgreSQL
instance and must run as a non-root user with permission to bind local ports.

- Install dependencies: `npm --prefix frontend ci` and
  `mvn -f backend/pom.xml dependency:resolve`
- Run tests: `mvn -f backend/pom.xml test` and `npm --prefix frontend test`
- Run build-wrapper tests: `node --test --test-isolation=none scripts/build.test.mjs`
- Run backend coverage integration fixtures:
  `node --test --test-isolation=none scripts/jacoco.test.mjs`
- Run browser tests: `npm --prefix frontend run test:e2e` (starts test servers;
  install Chromium with `cd frontend && npx playwright install chromium`, or
  set `CHROME_PATH` to a local Chrome executable)
- Run a specific test: `mvn -f backend/pom.xml -Dtest=GameEngineTest test`;
  `npm --prefix frontend test -- src/protocol.test.ts`
- Run linting: `npm --prefix frontend run lint` (Java compiler checks during build)
- Run formatting checks: `npm --prefix frontend run format:check` and
  `mvn -f backend/pom.xml spotless:check`
- Run formatting: `npm --prefix frontend run format` and
  `mvn -f backend/pom.xml spotless:apply`
- Run type checks: `npm --prefix frontend run typecheck`
- Build the project: `./scripts/build.sh` (frontend first, then Maven clean verify;
  embeds the frontend and runs Java tests). For packaging with Java tests
  explicitly skipped: `./scripts/build.sh --skip-tests`.
  Underlying troubleshooting commands: `npm --prefix frontend run build`, then
  `mvn -f backend/pom.xml clean verify`.

Do not invent project commands. Discover them from repository configuration or
ask the user when they cannot be determined safely.

## Development workflow

Use GitHub Flow: `main` is the only permanent integration branch and must remain
deployable. Start focused topic branches from updated `main` and integrate through
reviewed pull requests with the required `verify` check passing once GitHub is
configured. Squash merge by default using a Conventional Commit PR title; use a
reviewed exception when preserving multiple commits materially helps maintenance.
Do not reuse completed topic branches. Keep experiments isolated until adopted.
Use one shared application version following Semantic Versioning 2.0.0. During
`0.x`, features and breaking changes increment MINOR; compatible fixes increment
PATCH. From `1.0.0`, breaking changes increment MAJOR. Compatible maintenance-only
releases use PATCH; ordinary PRs do not bump versions. Prepare version changes in
a release PR, keeping the backend project, frontend package, and lockfile root
versions identical. Review compatibility and the full release scope using
[CONTRIBUTING.md](CONTRIBUTING.md#semantic-versioning); always mark breaking changes.
Releases use immutable version tags on verified main commits and manual deployment;
merging does not authorize deployment. Existing authorization rules below still
apply. See [CONTRIBUTING.md](CONTRIBUTING.md) for the workflow, hosting activation,
and the one-time transition for existing branches.

Use test-driven development for features, bug fixes, and behavior-changing
refactors. Follow the red-green-refactor cycle:

Before writing or updating unit tests, explain in bullet form what will be
tested, including the behavior under test and the relevant success, edge, and
failure cases. Keep the list proportional to the scope of the change.

When designing the unit test suite for a specification, ask the user to choose
the target coverage for that specification. Present meaningful coverage
options, explain their trade-offs in confidence, implementation effort,
maintenance cost, and test runtime, and clearly recommend a default. Define
coverage in terms of behaviors, risks, and edge cases; include a numeric code
coverage target only when the repository measures it and the metric would be
useful. Record the confirmed target in the specification's verification
section.

1. Add or update the relevant tests before writing production logic.
2. Run the tests and confirm that the new tests fail for the expected reason.
3. Implement the minimum production logic required to make the tests pass.
4. Run the relevant test suite and confirm that it passes.
5. Refactor while keeping the tests passing.

Before implementing production logic, confirm that the test demonstrates the
missing or incorrect behavior. Its failure must not be caused by syntax errors,
broken fixtures, or environment problems, and unrelated tests should continue
to pass.

Do not publish commits that knowingly leave the required test suite failing.
When practical, keep tests and production logic as distinct logical changes so
the TDD progression remains easy to review, but every published commit must
leave the required test suite passing.

Creating commits requires authorization from the user. If commits are
authorized, commit the completed and verified change. Otherwise, leave the
changes in the working tree and propose an appropriate commit message.

## Specifications

Record non-trivial features, refactors, endpoints, data models, and schema
changes in `spec/<spec-name>.md`, using `spec/TEMPLATE.md` as the starting
point.

Specifications define what must be delivered and how correctness will be
verified. They should capture behavior, scope, constraints, invariants, edge
cases, compatibility requirements, acceptance criteria, and testing needs.
They should not duplicate architectural rationale recorded in an ADR.

### Specification index

Maintain `spec/INDEX.md` as the repository-wide overview of specifications and
their dependencies. Create it from `spec/INDEX-TEMPLATE.md` if it does not
exist.

When creating a specification, or changing its dependencies or related
architecture decisions, update the index to include:

- The specification's status, priority, and a brief statement of purpose.
- Its dependencies, matching the authoritative `Depends on` metadata in the
  specification.
- Links to related ADRs and a brief description of the decision each ADR
  records, without duplicating its rationale.
- Its position in the specification dependency graph.

Treat each specification's `Depends on` field as authoritative. The index is a
derived navigation aid and must not define additional dependencies. Represent
shared dependencies and independent specifications accurately rather than
forcing the graph into a strict tree. Identify dependency cycles as unresolved
issues instead of presenting them as valid ordering.

### Pre-design audit

Before designing or materially updating a specification that affects an
existing codebase, use the `codebase-design-audit` skill to inspect the affected
code and identify design issues that could materially influence the proposed
change.

Present relevant findings to the user before incorporating their remediation
into the specification. For each finding, explain its evidence, impact, why it
should be addressed as part of or before the proposed work, and the trade-offs
of doing so. Distinguish required prerequisites from optional improvements.

Obtain explicit confirmation before expanding the specification to address a
finding outside the originally approved scope. Continue drafting unaffected
portions of the specification when possible.

After the user confirms the expanded scope, update the specification
accordingly and evaluate whether any resulting architectural decision requires
an ADR under the policy below. Do not create or finalize an ADR without the
confirmation required by this file.

## Architecture decision records

Record significant architectural and technical decisions in
`docs/adr/<number>-<decision-name>.md`, using `docs/adr/TEMPLATE.md` as the
starting point.

An ADR is appropriate when a decision:

- Changes system, service, or module boundaries.
- Introduces or replaces a long-lived dependency or technology.
- Changes data ownership, persistence, or communication patterns.
- Establishes a convention future work must follow.
- Affects a public or difficult-to-change contract.
- Has significant alternatives or difficult-to-reverse trade-offs.

Do not create ADRs for routine implementation details, easily reversible
choices, or decisions already established by project conventions.

When creating or updating a specification, evaluate whether the work requires
an ADR. If it does and the user has not already authorized one, ask for
confirmation before creating it. Explain:

- The architectural decision that should be recorded.
- Why the specification alone is insufficient.
- The main alternatives and their trade-offs.
- The recommended option and its rationale.
- The proposed ADR title and location.

The specification may be drafted while confirmation is pending, but mark the
decision as unresolved and do not present the architecture as finalized.

## Decision-making and user confirmation

Ask the user before making a decision that is significant, ambiguous, outside
the established requirements, or difficult to reverse. Provide the viable
choices, their benefits, drawbacks, risks, and consequences, followed by a
reasoned recommendation clearly marked as the default.

Do not request confirmation for routine, low-risk choices; decisions already
established by a specification, ADR, or repository convention; safely
reversible decisions without material impact; or actions the user has already
authorized.

Always require explicit confirmation before actions that:

- Delete or irreversibly overwrite data.
- Change a public contract outside the approved scope.
- Weaken security or privacy protections.
- Incur costs.
- Deploy or publish changes.
- Send external communications.
- Create other significant external effects.

Lack of a response is not consent. If confirmation is required, stop and wait
for the user's decision.

## Skill opportunities

While planning or performing work, identify recurring, consistent, or
error-prone workflows that would benefit from reusable instructions, scripts,
templates, or validation in a skill.

Do not propose skills for one-off or trivial work, procedures too unstable to
reuse, or workflows already covered adequately by an existing skill.

Before creating or modifying a skill, ask for confirmation unless the user has
already authorized it. Explain:

- The workflow the skill would cover and why it is reusable.
- How it would improve consistency, speed, or reliability.
- The inputs, outputs, scripts, templates, or checks it would contain.
- Whether an existing skill could be extended instead.
- Its proposed name and location.

Continue the original task when possible; a skill proposal should not
unnecessarily block requested work.

## Verification

Before declaring work complete, run the smallest relevant set of tests,
linting, formatting, type checks, and build checks that adequately covers the
change.

Report which checks were run, whether they passed, and any checks that could
not be run and why. Never claim a check passed if it was not executed.

## Commit conventions

Use Conventional Commits for every authorized commit:

```text
<type>(<optional-scope>): <description>
```

Use an imperative, concise, lowercase description and the narrowest meaningful
scope. Typical types include `feat`, `fix`, `refactor`, `test`, `docs`, `build`,
`ci`, `perf`, and `chore`.

Examples:

```text
test(auth): cover expired reset tokens
feat(auth): add password-reset endpoint
refactor(api): extract request validation
docs(adr): record authentication boundary decision
```

Mark breaking changes with `!` or a `BREAKING CHANGE:` footer:

```text
feat(api)!: remove legacy user endpoint
```

When commits are not authorized, suggest suitable commit messages without
creating commits.

## Documentation responsibilities

- `AGENTS.md` defines repository-wide development and collaboration rules.
- `spec/*.md` defines what a particular change must accomplish.
- `docs/adr/*.md` explains why a significant architectural decision was made.
- `skills/*/SKILL.md` defines a reusable execution procedure.

Link related specifications and ADRs instead of duplicating their contents.
Repository policy remains in `AGENTS.md`; reusable procedures belong in skills.
