---
name: change-handoff
description: Prepare and carry out scoped commit, push, and pull-request handoffs using local validation, actual verification evidence, and live GitHub status. Use for completed changes awaiting handoff; do not use to implement features, merge PRs, or deploy.
---

# Change handoff

Use [prepare-pr.py](../../scripts/prepare-pr.py) for deterministic local checks
and drafts. Follow [AGENTS.md](../../AGENTS.md) and
[CONTRIBUTING.md](../../CONTRIBUTING.md) for policy. The script cannot determine
remote branch history, authorization, or whether excluded changes affected tests.

## Inspect the intended handoff

Determine which actions the user authorized and the completed change they cover.
An explicit “commit, push and create PR” covers those three actions; use that
existing authorization. Finish the scoped review and concrete draft before asking
for any missing action authorization. Implementation alone does not grant it.

Inspect the current branch, full PR diff, index, unstaged changes, and untracked
files. Select exact file paths. Preserve unrelated feedback, caches, and queued
user work. For an incomplete multi-part change, identify the intended verified
subset rather than staging everything.

For GitHub handoffs, confirm the repository and query live branch PR history,
including merged/closed PRs, plus the current base and CI status. Fetch the main
ref when needed; the script does not fetch. Local ancestry alone misses squash
merges. Do not reuse a completed topic branch. If it has an appropriate open PR,
update that PR instead of creating a duplicate. A PR for a different outcome
requires a separate branch/scope decision. For a local-only commit with sufficient
session evidence of a fresh branch, leave unnecessary publication checks pending.

Useful read-only queries (supply the actual repository/branch as argv values):

```sh
gh repo view --json nameWithOwner
gh pr list --repo OWNER/REPO --head BRANCH --state all --json number,state,mergedAt,headRefName,baseRefName,url
gh pr view NUMBER --repo OWNER/REPO --json state,mergeable,statusCheckRollup
```

## Prepare a concrete review

Read `python3 -B scripts/prepare-pr.py --help` for the current CLI. Supply the topic
branch, selected main ref, literal scoped paths, Conventional Commit title,
summary/review files, specification/ADR references (or a routine-change reason),
and actual verification evidence/notes. Put ephemeral inputs and output directories
outside the checkout; writing them inside can invalidate evidence.

Reuse [the runner](../../scripts/verify.sh) and its `--check` operation when current
manifests exist. Run missing checks appropriate to the change and the confirmed
coverage target. For manually executed checks, provide accurate notes with their
actual results and omissions. Do not turn advisory findings into suppressions or
claim a broader profile ran. For specification changes, reuse
[the validator](../../scripts/validate-specs.py).

Review both `handoff.json` and `pr.md`. Resolve local findings. The exit code only
summarizes deterministic local checks: remote/human pending items remain even when
it is zero. In particular, a spec/ADR link is not proof of approval, and evidence
from a dirty tree may include excluded functional changes. Isolate/reverify the
intended code when those changes affect applicability; preserve user work.

Preserve the generated artifacts. Make a separate reviewed PR body if editing the
draft to resolve pending items or improve its explanation. Replace pending items
only with observed facts; keep hosted CI pending until actually checked. Use the
repository template's sections, explain behavior and material risks, and remove
internal coordination details that do not help a reviewer.

## Perform only the authorized actions

Immediately before staging, ensure the reviewed source has not changed. Current
runner evidence can establish that exact pre-staging state. A rejected manifest
must not supply copied success claims; inspect actual historical records or rerun
checks as appropriate. Preserve the original manifest and logs.

Stage only `stage_paths` from the report, using literal Git path arguments. For
example, pass `git --literal-pathspecs add -- PATH...` through a structured process
call or correctly quoted argv; never interpolate filenames as shell code.
`stage_paths` intentionally omits already-staged/committed deletion and rename
sources that no longer match `git add`. Inspect the final staged diff and reject
unrelated staged files; do not silently unstage or discard another person's work.

Commit when authorized, then inspect the resulting diff and working tree for
hook changes or unexpected content. Staging/committing change the exact runner
fingerprint. Do not rewrite/rebind the old manifest or call it verification of
the resulting commit. Describe it as verification of the pre-staging source,
with its actual scope. Matching selected file contents alone does not establish
that excluded changes did not influence tests. Code changes require new relevant
checks; use fresh post-commit verification when that claim is needed.

Push and create/update the PR only when covered by session authorization. Use
`--body-file` with the reviewed body and the actual repository/base/head. Recheck
GitHub state when needed to avoid duplicate PRs or completed-branch reuse. Do not
merge, deploy, delete branches, or send unrelated communications as part of handoff.

Report the actual commit, branch, PR URL, verification results/limits, and excluded
work. Distinguish local checks from hosted CI. If an operation fails, retain the
completed local work and diagnose the concrete failure rather than repeating
mutations blindly.
