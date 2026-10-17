# Contributing

Use GitHub Flow: `main` is the only permanent integration branch and should stay
deployable. Work on focused topic branches, review through pull requests, and
deploy explicitly selected release tags. There is no permanent `develop` branch.

Repository development, test coverage, specification, ADR, and authorization
policy remains in [AGENTS.md](AGENTS.md). The rationale is recorded in
[ADR-0012](docs/adr/0012-github-flow.md).

## Start a change

After a GitHub remote named `origin` is configured, start from updated `main`:

```sh
git switch main
git pull --ff-only origin main
git switch -c refactor/frontend-inspector
```

Use a short purpose-specific prefix such as `feat/`, `fix/`, `refactor/`, `build/`,
`ci/`, `docs/`, `test/`, or `chore/`. Keep one reviewable outcome per branch;
several related commits are fine. Do not reuse a branch after its PR is merged.
Keep incomplete features on their branch or behind a deliberately designed flag.

Experiment branches such as `benchmark/immutable-game-state` may remain separate
without merging. Merge only when the experiment's implementation is deliberately
adopted and verified. Avoid permanent environment or release branches.

## Implement and review

Follow TDD and documentation requirements in AGENTS.md. Use Conventional Commits
for authorized commits. Open a draft PR when early review is useful; target `main`.
The [PR template](.github/pull_request_template.md) asks for the behavior, related
specification/ADRs, actual verification, and material release risks.

Review the final diff yourself when working alone. Independent review is welcome
when available, but a second person's approval is not required by this workflow.
Resolve conversations and ensure required checks pass before merging. When main
advances, bring the branch up to date and rerun affected checks; coordinate before
rewriting a shared branch. Updating a remote branch still requires authorization.

Squash merge by default, with a Conventional Commit PR title. Preserve multiple
commits through a reviewed exception when their separation materially helps
future maintenance; merge commits remain an available repository setting for
those cases. Each published state must pass its required checks.

Delete completed merged topic branches when authorized. A maintainer can enable
automatic deletion of merged head branches in GitHub repository settings. Never
delete unmerged experiments or unrelated branches as cleanup.

## Required verification

[Project verification](.github/workflows/verify.yml) defines one stable job named
**`verify`** for PRs targeting `main`, pushes to `main`, and manual workflow runs.
It runs on Ubuntu as a non-root user with Node 24 and Java 25:

- Frontend lint, type checking, formatting, and unit tests.
- Analyzer reporting tests, build-wrapper/PMD/JaCoCo/PIT integration fixtures, and Python tooling tests.
- Offline specification, ADR-link, and dependency-index validation.
- The full analyzed frontend/backend build, including Spotless and Java tests.
- Chromium browser flows with the embedded PostgreSQL test server.

Backend JaCoCo coverage is reporting-only, without a minimum percentage gate.
A clean verified build generates HTML/XML/CSV in `backend/target/site/jacoco/`;
CI retains those reports and `backend/target/jacoco.exec`. See the
[backend coverage guide](backend/README.md#code-coverage) for commands, fresh-data
requirements, and the Surefire-only measurement scope.

New frontend/PMD findings remain advisory under the existing policies. Existing
lint errors, broken analysis, compilation errors, and failed tests block the job.
There are no path filters: documentation PRs also emit the required check. The
workflow has a read-only repository token, no production secrets, no publication
or deployment steps, and retains diagnostic reports for seven days. Actions are
pinned to immutable commits; review and update those pins intentionally.

Run the same commands locally when relevant; their exact sequence is in the
workflow. For browser tests use the documented Chromium installation or
`CHROME_PATH`. For local Maven cache overrides use `MAVEN_OPTS`; CI uses its
ordinary cached Maven repository. Dedicated workflow validation can be run with
`actionlint .github/workflows/verify.yml` when actionlint is installed.

## Optional backend mutation analysis

PIT is independent of the required `verify` job's application build. Use the
[backend mutation guide](backend/README.md#mutation-testing) for the opt-in Maven
profile and HTML/XML reports. Project-wide mutation testing runs only by explicit
local invocation or the separate [manual workflow](.github/workflows/mutation.yml),
not on each PR/push. Its scores have no minimum gate. Tiny integration fixtures in
normal CI verify the tooling configuration; they are not a backend mutation score.
Manual workflow dispatch still requires authorization, and first becomes available
when the workflow is on the default branch. No deployment or release is implied.

## Local verification profiles

Use the same checks through an explicit local profile:

```sh
./scripts/verify.sh frontend
./scripts/verify.sh backend
./scripts/verify.sh full --browser
```

| Profile    | Scope                                                                                                                                                                         |
| ---------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `frontend` | Lint, formatting, unit/analyzer tests, analysis, and production build (including TypeScript checks).                                                                          |
| `backend`  | Spotless, Maven verification/Java tests, and PMD; does not refresh bundled frontend assets.                                                                                   |
| `full`     | Frontend lint/format/unit/analyzer tests, build-wrapper/PMD and Python tooling tests, specification validation, Java formatting, and the existing frontend-first clean build. |

`--browser` adds browser tests last to any profile and needs both toolchains.
Install dependencies and the browser first using the existing setup instructions.
All commands run sequentially. Full delegates analysis, frontend compilation,
and Java clean verification to `build.sh`; it avoids a second frontend build or
standalone type check. Browser startup intentionally recompiles its test server.
The explicit Spotless command checks Java formatting without applying changes.
Profiles leave CI's existing sequence and required job name intact.

Each invocation prints a `Manifest:` path in a fresh private temporary directory,
with a versioned JSON manifest and individual merged stdout/stderr logs. Use
`--output /existing/parent/new-run` to choose a fresh directory outside the checkout.
Existing paths, symlink ancestors, and in-checkout output are refused. The runner
never removes older reports. Command failures stop the run, retain their exit
status, and mark later/unselected checks as skipped. Interruptions stop the active
process group and retain interrupted evidence when possible.

Validate saved evidence without running checks or modifying reports:

```sh
./scripts/verify.sh --check /tmp/your-run/manifest.json
```

This requires a successful, complete current command plan, matching source/settings
and resolved tool paths, and intact logs. Source identity includes HEAD, branch,
staged/unstaged state, and tracked/nonignored untracked contents. A source edit,
commit, branch change, option change, or log modification can invalidate evidence.
Ignored dependency caches/artifacts, transient edits restored during a run, tool
binary contents, and arbitrary environment variables are outside this claim.

The runner inherits settings such as `MAVEN_OPTS` and `CHROME_PATH`. Metadata keeps
only allowlisted paths/booleans and hashes of option strings; it does not dump the
environment. Child logs are verbatim and may contain sensitive tool output: review
them before sharing. Evidence does not prove approval, correctness, or authenticity.
A passing frontend profile does not claim backend or browser checks passed.
See [the specification](spec/local-verification.md) and
[ADR-0015](docs/adr/0015-local-verification-evidence.md).

## Validate specifications locally

From the repository root, run the read-only consistency check and fixture tests:

```sh
python3 -B scripts/validate-specs.py
python3 -B -m unittest discover -s scripts -p 'test_validate_specs.py'
```

The command path above is relative to the repository root. The script locates its
repository by its own location; pass `--root PATH` to validate a fixture or another
checkout. Exit codes are 0 for valid input, 1 for validation/read failures, and 2
for invalid CLI arguments. Diagnostics identify the source file and line.

The checker compares authoritative specification metadata with all index rows
and the dependency graph, checks local links/Markdown heading anchors and related
ADR sets, and requires a recorded coverage/verification target. External URLs are
counted as unchecked; no network requests occur. It cannot prove user approval,
architectural correctness, or that recorded checks actually ran.

Use ordinary inline or explicit reference Markdown links. In the index graph,
put each node (`id["Specification title"]`) or dependency
(`source -->|depends on| target`) on its own line, with optional semicolons and
`%%` comments. Use a `flowchart TD` (or TB/LR/RL/BT) header. Labels may use spec
titles or index labels and the existing `(independent)` suffix. Unsupported graph
syntax fails explicitly. Drafts must also have consistent metadata and links;
record genuine open questions as prose rather than template placeholders. See
[the specification](spec/specification-validation.md) and
[ADR-0014](docs/adr/0014-specification-validation.md) for the complete contract.

## Prepare a scoped handoff

The [change-handoff skill](skills/change-handoff/SKILL.md) combines local
preparation with live GitHub review and authorized actions. The offline script
only creates artifacts; it never stages, commits, fetches, pushes, or opens PRs.

Create summary/review prose outside the checkout, then run, for example:

```sh
python3 -B scripts/prepare-pr.py --branch feat/example --base origin/main --path frontend/src/example.ts --title "feat(example): add the behavior" --summary-file /tmp/change-summary.md --review-file /tmp/change-review.md --spec spec/example.md --evidence /tmp/your-run/manifest.json --output /tmp/new-handoff-directory
```

Replace the example inputs with real changed file paths and references. Repeat
`--path`, `--spec`, `--adr`, `--evidence`, or `--verification-note` as needed.
Scope paths are exact repository-relative files, including rename endpoints;
patterns are not expanded. Use `--routine-reason` instead of a specification only
for a routine change. `--repository OWNER/REPO` can provide an otherwise unknown
GitHub identity; a recognized conflicting origin is an error.

The fresh output directory must be outside the checkout with an existing parent;
existing paths/symlink ancestors are refused. Read `handoff.json` and `pr.md`.
Exit 0 means local deterministic checks passed, 1 means findings or operational
failure, and 2 means invalid CLI arguments. Remote PR history/base freshness, CI,
authorization, and human diff/verification review remain explicit pending checks.
Local ancestry cannot establish whether a topic was already squash-merged.

`stage_paths` lists only scoped unstaged/untracked candidates. Unrelated staged
or committed PR changes block local preparation; unrelated unstaged/untracked
files are listed as excluded. Preparation does not alter the index or resolve
scope automatically. Check whether excluded functional work influenced tests.

Accepted evidence describes the working tree before staging. Staging/committing
invalidate the runner's exact Git-state fingerprint; preserve the original record
and report that historical meaning rather than rebinding it. Rejected records
supply no copied success claims. Author-provided verification notes require review
against actual executed checks. A generated draft is neither approval nor proof
that the future commit or hosted CI was verified.

## Activate protection on GitHub

These files prepare CI; they cannot enable hosting settings. After publishing to
the intended repository with approval:

1. Set `main` as the default branch and enable Actions as appropriate for the
   repository's plan/usage budget.
2. Run the workflow on a PR and verify that all steps work on the hosted runner.
   Register **`verify`**, from the GitHub Actions app, as the required status check
   using the actual observed check name.
3. Create an active branch ruleset/protection for `main`: require pull requests,
   require `verify` to pass, require the branch to be up to date, and resolve
   conversations before merging. Do not require an additional approving reviewer
   while the project has only one active maintainer.
4. Block force pushes and branch deletion; apply the policy to maintainers too
   where supported. Avoid routine administrator bypasses.
5. Enable squash merging and use PR titles for squash commit titles. Keep merge
   commits available only for the reviewed multi-commit exceptions above. Rebase
   merging is unnecessary for this convention.

GitHub plan/repository visibility can affect available protection features. If a
setting is unavailable, record that limitation rather than claiming protection
is active. Required check names must change in coordination with workflow edits;
removing/renaming a required check without updating protection can block merges.
No remote is configured in the current checkout, so activation is still pending.

## Semantic versioning

The application follows [Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html).
Use one version for the packaged frontend and backend; they are released together.
The current development version is `0.1.0`. Version declarations change in a
reviewed release-preparation PR, rather than on every feature or refactor PR.

### Compatibility contract

Judge compatibility against the last published application release, considering
all changes included in the proposed release. The contract covers:

- Documented HTTP routes, methods, request/response fields, WebSocket messages,
  command semantics, and error behavior used by existing clients.
- Documented Commander/Standard workflows and permissions, including private-card
  visibility and guest access to saved rooms.
- Preservation and readability of saved rooms/snapshots through the documented
  upgrade procedure. A migration that preserves this behavior is not automatically
  a breaking change; dropping saved games or requiring a destructive reset is.
- Documented deployment configuration and operator-facing commands. Removing a
  required interface or adding mandatory configuration without a compatible default
  is a compatibility change.

A compatible release must continue to support previously valid use of these
interfaces, including the prior release's browser client communicating with the
new server. Forward compatibility of a new client with an older server is not
promised. Internal Java/TypeScript helpers, component structure, CSS class names,
and test fixtures are not public APIs. Visual changes alone need not break the
contract, but removing a documented supported workflow does.

### Choosing a version

| Included change                                                     | During `0.y.z`                                                      | From `1.0.0` onward                                                 |
| ------------------------------------------------------------------- | ------------------------------------------------------------------- | ------------------------------------------------------------------- |
| Backward-compatible bug fix                                         | PATCH                                                               | PATCH                                                               |
| Backward-compatible feature or public API deprecation               | MINOR                                                               | MINOR                                                               |
| Breaking change                                                     | MINOR, with explicit breaking notes                                 | MAJOR                                                               |
| Internal refactor, dependency/tooling maintenance, or documentation | No release required; PATCH if releasing only compatible maintenance | No release required; PATCH if releasing only compatible maintenance |

The `0.x` bump rules are this project's initial-development policy: SemVer itself
allows the public API to remain unstable before `1.0.0`. Nevertheless, breaking
changes must be reviewed and identified, including upgrade instructions and any
migration or client-refresh requirement. Choose `1.0.0` explicitly when adopting a
stable compatibility contract; it is not triggered by a particular feature count.

Choose the highest required bump across the full release scope. Reset PATCH to
zero for a MINOR bump, and both MINOR and PATCH to zero for a MAJOR bump. For
example, from `0.1.4`, a compatible fix becomes `0.1.5`, while a feature or break
becomes `0.2.0`; from `1.2.3`, the equivalents are `1.2.4`, `1.3.0`, and `2.0.0`.
A security fix that breaks the contract still follows the breaking-change rule.

Conventional Commit types help review the release scope: `feat` normally suggests
MINOR and `fix` PATCH. A `!` or `BREAKING CHANGE:` marker takes precedence, regardless
of type. Types do not replace compatibility review or automatically publish a
release. Record breaking changes even during `0.x`. PRs should describe their
release impact; ordinary PRs do not need to edit version declarations.

For optional prereleases, use a suffix such as `0.2.0-rc.1`, identical in every
version declaration. Prereleases precede the corresponding final release. Normal
versions have three numeric components without leading zeroes. Git tags use a
`v` prefix; the version values in manifests do not. Use the commit and image digest
for build identity rather than adding build metadata to release versions; SemVer
build metadata does not affect precedence, and `+` is not a portable image-tag
character.

### Release preparation

1. Review the last published version and all proposed changes, including remote
   tag/release history. Classify compatibility impact and choose the version using
   the table above. Do not infer the baseline solely from a local tag list.
2. In a focused release-preparation PR, update these values to the same version:
   `backend/pom.xml`'s project version (not its Spring Boot parent),
   `frontend/package.json`'s version, and both root package version fields in
   `frontend/package-lock.json`. Keep dependency versions independent.
3. Include release notes in that PR: features/fixes, breaking changes or an explicit
   statement that none are included, and upgrade/migration/client-refresh needs.
   Check version equality, SemVer syntax, and compatibility impact during review.
   Run the existing required verification workflow and relevant migration checks.
4. After the release PR merges and the selected main commit is verified, follow
   the authorized tagging/build/deployment process below. The annotated tag must
   be `v` plus the exact manifest version; verify it is unused before creating it.
   Use the same version for the image tag, and record its immutable digest.

Published versions and tags are immutable, including prereleases. Correct a
published mistake using a new version; do not replace an existing release. This
policy establishes manual review checks; CI does not yet enforce version equality,
classify compatibility, create tags, or publish releases automatically.

The rationale is recorded in [ADR-0017](docs/adr/0017-semantic-versioning.md).

## Release and deploy

Merging to main makes a change eligible for release; it does not deploy it.
Select a verified commit on `main`, create an annotated SemVer tag (for example
`v0.2.0` or `v0.2.0-rc.1`) matching the version declarations when authorized,
and build the deployment image from that exact clean checkout.
Release tags are immutable; corrections receive a new tag. Do not use an unrelated
working-tree image or a mutable `latest` tag as the production release identity.

Record the release tag, full source commit, image ID/digest, and deployed platform.
Use the [deployment guide](docs/deployment-oracle.md) to load the image and perform
the manual VM update, backup, smoke checks, and schema-compatible rollback.
Keep the prior image and backup. Creating/pushing release tags, publishing images,
and changing the production VM require separate explicit authorization.

Production fixes normally branch from updated main and use the same PR/check
process. Maintaining an older production release when main has incompatible
changes needs an explicit backport plan; do not silently introduce a long-lived
maintenance branch or tag an unreviewed production fix.

## Transition from existing branches

The existing `deploy/oracle-public-ip` branch includes deployment preparation,
frontend analysis, and validated protocol changes. Review and integrate it into
main before using main as the base for future product work. This policy does not
authorize merging or deleting it.

`docs/github-flow` was created from that branch to preserve those verified changes
while preparing this workflow. This is a one-time bootstrap exception. Once the
deployment branch is integrated, move only the workflow commits onto updated
main through an explicitly reviewed rebase/cherry-pick as appropriate, especially
if the deployment PR was squash merged. Check the PR diff for already-integrated
changes. Keep the benchmark branch independent.

References: [GitHub Flow](https://docs.github.com/en/get-started/using-github/github-flow),
[protected branches](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches).
