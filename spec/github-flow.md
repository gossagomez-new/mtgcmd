# GitHub Flow and verification setup

## Metadata

- Status: In progress
- Priority: Medium
- Priority rationale: Establish focused changes and a verifiable integration gate.
- Depends on: [Build automation](build-automation.md), [Frontend static analysis](frontend-static-analysis.md)

## Summary

Adopt GitHub Flow with one deployable main branch, short-lived topic branches,
reviewed PRs, squash merges by default, and tagged manual releases. Prepare local
CI/policy files and document the separate GitHub activation steps.

## Goals

- Record branch, review, merge, and release conventions.
- Emit a stable required verification check without deploying or publishing.
- Preserve existing work and identify the one-time bootstrap transition.

## Non-goals

- Configure a remote, create/push/merge PRs, delete branches, create release tags,
  publish images, deploy, or enable remote settings from this checkout.
- Introduce Gitflow, a permanent develop branch, new application behavior,
  automatic releases, or mandatory second-person review for a solo maintainer.

## Background

No remote or GitHub workflow is configured. The current deployment topic contains
several completed changes and has not been integrated into main. The audit found
that required checks need a checked-in workflow and a separately verified hosted
check name. Existing build/test commands are retained rather than replaced.

## Requirements

### Functional requirements

1. CONTRIBUTING.md describes topic branches from updated main, PRs, verified
   merges, authorized branch cleanup, and immutable tags/manual deployment.
2. AGENTS.md owns repository policy and links the guide; the root README provides
   navigation. A PR template asks for behavior, spec/ADRs, checks, and release risks.
3. Prepare a read-only GitHub Actions workflow with one stable verify job for PRs
   targeting main, pushes to main, and manual execution. Do not skip documentation
   paths or use privileged pull_request_target execution.
4. Check existing frontend tooling/unit tests, analyzer fixtures, wrapper/PMD tests,
   Python tooling, the full Java/frontend build, and Chromium browser tests.
5. Use Node 24, Java 25, a non-root Ubuntu runner, immutable action pins, bounded
   execution/report retention, and no production credentials or deployment steps.
6. Document protected-main activation and leave its status pending until a
   repository is known and its actual CI/check/protection behavior is verified.
7. Preserve deployment and experiment branches. Document transitioning this
   bootstrap branch onto main without replaying squash-merged changes.

### Non-functional requirements

- Existing advisory analysis policies remain unchanged.
- No commits, pushes, deployments, or other external changes are implied by
  adopting the workflow; retain AGENTS.md authorization requirements.

## Contracts and data

The hosting contract is the verify status emitted by Project verification.
Repository settings must require the observed check from GitHub Actions and keep
that configuration coordinated with workflow/job renames. Local files alone do
not enforce protection. Release metadata maps tag to source commit and image ID/digest.

## Technical constraints

Use discovered project commands and existing embedded PostgreSQL tests. Do not
require a running production database or production secrets. Actionlint provides
workflow syntax/expression validation; only GitHub can validate the hosted run.

## Invariants

- Main remains deployable; merge does not mean deployment.
- Failed tests/enforced lint/analysis failures block; advisory findings do not.
- No unmerged work or unrelated local files are deleted or overwritten.

## Edge cases and failures

- Remote absence or plan restrictions leave hosting activation pending.
- Fork PRs use ordinary read-only pull_request execution with no secrets.
- Failed builds still attempt diagnostic report retention; absent reports are
  not evidence of success. A required-check rename needs coordinated settings.
- Older production backports require a deliberate plan rather than bypassing checks.

## Migration and compatibility

Current docs/github-flow is a bootstrap branch based on deploy/oracle-public-ip.
Review/integrate the latter, then move only the workflow changes onto updated main
as appropriate. Preserve benchmark work independently. No application/schema migration.

## Acceptance criteria

- [x] Local workflow validation and equivalent existing checks pass.
- [x] Policy, contribution guide, PR template, ADR, and index agree.
- [x] Remote activation tasks and bootstrap exception are explicit.
- [ ] GitHub run and branch protection are verified after remote setup (pending).

## Verification

User chose **comprehensive verification**: actionlint validation, trigger/permission
and required-name review, equivalent existing local checks, and explicit pending
remote verification. No mirror unit tests for declarative documentation/CI;
existing meaningful suites verify the commands the workflow invokes.

Local verification on 2026-10-16: actionlint 1.7.12 (including available ShellCheck)
validated the workflow; action pins were resolved from the official repositories.
Frontend lint/types/format, 66 unit tests, 14 analyzer integration tests, 26
build-wrapper/PMD integration tests, 26 Python tooling tests, and the full build
with 571 backend tests passed. All 9 browser tests also passed using the installed
Chrome through CHROME_PATH; GitHub's Chromium/system-dependency installation,
action setup/caching, artifact uploads, and actual protection enforcement must
still be confirmed on the first hosted run. No remote configuration or publication
was performed. Status remains In progress until hosted activation is verified.

## Architecture decisions

[ADR-0012](../docs/adr/0012-github-flow.md), explicitly approved.

## Open questions

- Owner: GitHub repository, visibility/plan, and authorization to publish/activate.
