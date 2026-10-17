# Change handoff preparation

## Metadata

- Status: Completed
- Priority: High
- Priority rationale: Repeated commit/push/PR handoffs need consistent scope and evidence checks.
- Depends on: [Local verification](local-verification.md), [GitHub Flow](github-flow.md), [Specification validation](specification-validation.md)

## Summary

Add an offline preparation CLI and a repository skill for repeated commit and PR
handoffs. The CLI checks local inputs and writes reviewable JSON/Markdown outside
the checkout. The skill checks live GitHub state and performs only authorized
Git/GitHub actions, preserving the meaning of pre-commit verification evidence.

## Goals

- Expose the exact intended files, PR diff, unrelated changes, and staging candidates.
- Reuse existing verification evidence and the repository PR template.
- Generate concrete drafts while distinguishing local checks from pending decisions.

## Non-goals

- Automatically stage, commit, fetch, push, open/merge PRs, deploy, or clean files.
- Infer authorization, architectural approval, or verification applicability.
- Rebind historical evidence to a new Git state or change the runner schema.
- Attest the published commit from matching pre-commit scope alone.

## Background

The audit identified two relevant constraints. Exact runner fingerprints change
on staging/committing, and local ancestry cannot detect squash-merged topic reuse.
The script must preserve original evidence and leave remote checks to the skill.
Unrelated feedback files and generated caches have repeatedly remained in the
working tree during project handoffs and must not be staged implicitly.

## Requirements

### Functional requirements

1. CLI: python3 -B scripts/prepare-pr.py --branch NAME --base origin/main|main
   --path FILE (repeatable) --title TITLE --summary-file FILE --review-file FILE
   --output DIRECTORY [--spec FILE ...] [--adr FILE ...]
   [--routine-reason TEXT] [--evidence MANIFEST ...] [--verification-note TEXT ...].
   Base defaults to origin/main. Root derives from script location. Accept paths
   relative to the repository root for scope and references; input/output file
   paths follow the caller's directory. Invalid CLI arguments exit 2.
2. Require the named current topic branch, reject main/detached HEAD, verify the
   selected main ref exists and is an ancestor of HEAD, and expose head/base/merge
   base IDs plus committed PR changes. An inherited or diverged main must be
   integrated before local preparation passes. Do not fetch or query GitHub.
3. Scope consists of exact file paths, not directories/globs/pathspec expressions.
   Reject absolute paths, traversal, .git internals, duplicates, nonexistent or
   unchanged scope entries. Preserve literal unusual filenames. Compare committed
   PR, staged, unstaged, and nonignored untracked changes using NUL-delimited Git
   output. Require committed PR changes and all staged changes to be within scope.
   Require both endpoints of staged/committed renames when either is in scope.
4. List unrelated unstaged/untracked paths as excluded, never hide or mutate them.
   Emit staging candidates only for scoped unstaged/untracked paths. Already staged
   or committed deletion/rename source paths must not become invalid git-add args.
   Excluded functional changes may affect verification and remain a human concern.
5. Validate Conventional Commit syntax, a lowercase nonempty description, and
   single-line titles without control characters. Do not claim to validate grammar,
   imperative wording, or appropriate semantic versioning. Permit custom lowercase
   Conventional Commit types; do not impose an unapproved length limit.
6. Require at least one specification or an explicit routine-change explanation.
   Specifications must be real non-template files directly under spec; ADRs must
   be numbered files under docs/adr. Referenced new/untracked files must be in
   scope so the draft does not link to excluded unpublished documentation. Do not
   infer architecture approval from a link. Validate repository-wide consistency
   with the existing specification validator as a separate skill/check command.
7. Infer GitHub owner/repository from a credential-free origin HTTPS/SSH URL, or
   accept --repository OWNER/REPO. Reject an explicit identity conflicting with a
   recognized origin. Never echo origin credentials or contact URLs. If identity
   is unavailable, emit local reference text and mark repository/link review pending.
   Use branch-based absolute GitHub links for selected specs/ADRs when known;
   publication and link availability remain pending until the branch is pushed.
8. Reuse the runner's strict validate_evidence API. Accepted current manifests
   supply only recorded scope/check IDs and source/state identities to the draft.
   Preserve the original manifest and logs byte-for-byte. Rejected evidence is
   reported with its reason and creates a pending verification item; do not copy
   its asserted successes. No manifest means verification remains pending unless
   human-reviewed actual checks are supplied as notes. Notes are clearly labeled
   author-provided and never described as script-validated evidence.
9. Every draft with accepted evidence states it matches the working tree before
   staging; staging/committing invalidate its exact fingerprint. Do not call this
   verification of a future commit. The skill retains the original record and
   reports its scope honestly after authorized commits. Source/code changes need
   fresh checks; arbitrary inherited settings, ignored dependencies, and excluded
   changes remain outside automated applicability judgments.
10. Read the repository PR template and retain its four sections: Change,
    Specification and decisions, Verification, Review and release considerations.
    Require nonempty UTF-8 summary/review files; use their supplied prose verbatim
    within the appropriate section. Populate references, recorded checks, pending
    items, and the no-deployment statement without claiming remote checks passed.
    Reject unsupported template structure rather than silently dropping sections.
11. Write handoff.json and pr.md to a fresh private output directory outside the
    checkout, using existing report-storage safeguards. Reject symlinks, existing
    paths, missing parents, and in-checkout output. JSON is the completion marker;
    include source identity, inputs, title, scope/status lists, stage paths,
    findings, pending checks, evidence results, draft hash, and schema version 1.
    Check source stability across preparation. Exit 0 when deterministic local
    checks pass, 1 on findings or operational failures. Pending remote/human checks
    never imply ready to publish, and may remain with exit 0. Findings still produce
    a reviewable draft/report when inputs and output storage can be read safely.
12. Create skills/change-handoff/SKILL.md using skill-creator guidance. It must
    inspect live PR history (including squash merges), base freshness and CI,
    choose explicit scope, reuse actual verification, review generated artifacts,
    and perform only actions authorized by the session. An explicit commit/push/PR
    request covers all three without redundant confirmation. Do not create another
    PR when the branch already has an appropriate open PR. Merging, deployment,
    deletion, and unrelated changes retain existing authorization boundaries.
13. The skill must finish concrete preparation before requesting missing action
    authorization. Store ephemeral summaries, notes, drafts, and reports outside
    the checkout. Stage only reported scoped candidates using literal Git path
    arguments; inspect the final index/commit diff, including hook changes. Retain
    the pre-staging evidence label after commits. If excluded functional changes
    influenced tests, isolate/reverify the intended code rather than claiming the
    published commit was tested. Reuse AGENTS.md and CONTRIBUTING.md for policy.

### Non-functional requirements

- Python standard library and existing Git/evidence helpers; no new dependencies.
- No shell evaluation, network requests, source/index/ref writes, or implicit actions
  from the preparation script. Generated artifacts are the only script writes.
- Stable sorted path lists and actionable findings; never dump inherited environment.

## Contracts and data

handoff.json schema version 1 describes local preparation, not a publication
approval. local_checks_passed excludes remote/human pending checks. Evidence
results are current or rejected/missing; rejected manifests are not reinterpreted
as historical successes. The Markdown draft preserves pre-commit verification
labels. stage_paths contains only scoped unstaged/untracked files. Local GitHub
identity inference is not proof of remote branch status or publication rights.

## Technical constraints

The existing evidence reader's contract and runner schema remain unchanged. The
skill is a separate contextual procedure; no existing skill needs modification.
Tests use temporary repositories, stub verification tools, and recorded GitHub
scenarios without live mutations. New tests join existing Python discovery.

## Invariants

- Scope checks do not stage or discard unrelated work.
- Local ancestry never establishes that a squash-merged branch is reusable.
- Prepared drafts, accepted evidence, and authorization are different facts.
- Script output never claims GitHub status or a future commit was verified.

## Edge cases and failures

Handle missing/diverged refs, detached/main/mismatched branches, no changes,
staged deletions/renames, staged-plus-unstaged files, untracked additions, ignored
files, unusual literal names, missing/unpublished references, bad titles/templates,
invalid input encodings, stale/damaged evidence, unsafe output paths, and source
changes during preparation. Fail without repository writes or evidence rewrites.

## Migration and compatibility

Opt-in CLI/skill with no runtime, data, CI job, or runner-schema migration. Existing
manual workflows remain available. Revert by removing the new tools/guidance.

## Acceptance criteria

- [x] Preparation exposes precise scope, excluded paths, candidates, and pending checks.
- [x] Valid and invalid fixtures produce accurate drafts/reports without Git writes.
- [x] Current evidence is reused honestly and stale evidence is never promoted.
- [x] Skill walkthroughs preserve authorization, branch lifecycle, and evidence meaning.

## Verification

User selected comprehensive coverage. Cover all CLI inputs, title/reference
validation, valid and diverged bases, committed/staged/unstaged/untracked scope,
renames/deletions, literal path safety, current/rejected/missing evidence, manual
notes, output safeguards, artifact/template content, and read-only/source-stability
behavior. Confirm expected red before production logic. Run all Python tooling
tests, specification validation, formatting/whitespace checks, skill validation,
and isolated skill walkthroughs for a fresh change, an existing open PR, a
squash-merged branch, and a post-commit historical record. Run existing verification
profiles appropriate to changed runner-adjacent tooling; application/backend and
browser behavior is unchanged. Hosted checks remain separate from local evidence.

### Executed verification

On 2026-10-16 the CLI scaffold failed expected missing-report and validation
assertions before production logic. Additional red/green cases covered regular
input files and escaped Markdown reference labels. All 23 handoff test methods
passed; full Python discovery passed 89 tooling tests. The fixtures verify literal
staging arguments, source/index/ref preservation, evidence preservation, and source
changes during preparation. The skill validator, documentation formatting,
specification validation (30 specifications), and whitespace checks passed.

Single-agent isolated walkthroughs exercised a fresh scoped commit followed by
rejected stale evidence, an existing open PR selected for update, and new work
moved off a squash-merged branch without losing edits. GitHub states were recorded
fixture inputs; these walkthroughs made no external mutations and were not an
independent model evaluation.

The real repository probe correctly rejected PR #8's committed dependency files
outside the selected handoff scope, while writing reviewable artifacts and leaving
Git state untouched. That is a delivery dependency, not permission to widen scope.
Full-profile execution records are retained separately by the runner; only records
matching the current source are accepted by preparation. Browser tests are not
required for these tooling-only changes. Hosted CI for the new handoff code,
publication, and deployment remain separate steps.

## Architecture decisions

[ADR-0016](../docs/adr/0016-change-handoff.md): offline preparation plus contextual
handoff, with preserved pre-commit evidence and explicit pending remote checks.

## Open questions

None.
