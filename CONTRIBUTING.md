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
- Analyzer reporting tests, build-wrapper/PMD integration tests, and Python tooling tests.
- The full analyzed frontend/backend build, including Spotless and Java tests.
- Chromium browser flows with the embedded PostgreSQL test server.

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

## Release and deploy

Merging to main makes a change eligible for release; it does not deploy it.
Select a verified commit on `main`, create an annotated `vMAJOR.MINOR.PATCH` tag
when authorized, and build the deployment image from that exact clean checkout.
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
