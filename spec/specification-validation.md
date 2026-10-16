# Specification and index validation

## Metadata

- Status: Completed
- Priority: High
- Priority rationale: Detect documentation drift during each pull request.
- Depends on: [GitHub Flow](github-flow.md)

## Summary

Add an offline, read-only Python validator for specifications, related ADR links,
and the derived specification index and dependency graph. Integrate it into the
existing verify job without changing the required job name.

## Goals

- Diagnose inconsistent metadata, links, dependencies, and index entries.
- Preserve authoritative specification metadata and current document structure.
- Give deterministic file/line diagnostics and meaningful CLI exit codes.

## Non-goals

- Rewrite documents, infer architectural dependencies, or approve decisions.
- Prove that recorded tests ran or that a coverage choice was authorized.
- Probe external URLs or implement a complete Markdown/Mermaid parser.
- Modify skills, application code, or deployment behavior.

## Background

The audit found multiple index tables and standalone rows, graph labels with an
independence suffix, and varied prose for confirmed verification targets. These
existing forms must remain compatible. Related ADRs are navigation references,
not additional specification dependencies.

## Requirements

### Functional requirements

1. Discover direct spec/*.md documents except INDEX.md and *TEMPLATE.md. Require
   one title, Metadata, Verification, and Architecture decisions sections, one
   Status, Priority, nonempty Priority rationale, and Depends on metadata field.
   Allowed statuses are Draft, Approved, In progress, Completed, Superseded;
   priorities are Critical, High, Medium, Low.
2. Depends on is None or local Markdown links to discovered specifications.
   Reject duplicates, self-dependencies, missing targets, and cycles with a path.
3. Check inline and reference Markdown links in specifications and INDEX.md;
   ignore fenced code and inline code. Resolve relative paths against the source,
   percent-decode paths, support angle-bracket destinations, and reject paths or
   symlinks escaping the repository. Require local targets to exist. For Markdown
   fragments, use GitHub-style heading slugs (including duplicate suffixes) and
   explicit HTML id/name anchors. External schemes and protocol-relative URLs are
   reported as unchecked informational counts and never fetched. Non-Markdown
   fragments are outside anchor validation. Reject malformed or undefined
   references; image links use the same local-path checks.
4. Reject explicit `TODO`/`TBD`/`FIXME` markers and angle-bracket template placeholders
   outside code in published documents, including drafts. Do not mistake HTML
   anchors, autolinks, generic comparisons, or unchecked acceptance boxes for
   placeholders. Templates are excluded; drafts may record genuine open questions.
5. Require a nonempty Verification section containing an explicit coverage or
   verification target record (User selected/chose/confirmed, Confirmed target,
   or equivalent existing phrasing). Validate its presence only.
6. Parse all six-column index rows, including standalone rows and multiple tables.
   Require exactly one row per discovered specification; compare Status, Priority,
   Depends on sets, and related ADR-link sets with the specification's Architecture
   decisions section. Require a purpose and a description for each related ADR.
   ADR links must reference numbered Markdown files under docs/adr. No ADR is
   required when the section explains None/no new decision or references conventions.
7. Require one Mermaid flowchart TD/TB/LR/RL/BT block. Supported graph syntax is
   one node declaration id["title"] or one edge source -->|depends on| target per
   line, optional semicolon, blank lines, and %% comments. Node labels identify
   spec titles or index link labels, optionally suffixed with (independent).
   Require one node per specification and exactly the authoritative dependency
   edges. Reject duplicate nodes/edges, unknown endpoints, ambiguous labels,
   missing nodes, unsupported statements, and spurious edges.
8. CLI: python3 -B scripts/validate-specs.py [--root PATH]. Default root derives
   from script location, independent of working directory. Print sorted relative
   file:line: error messages and an external-link summary. Exit 0 on valid input,
   1 on validation/read errors, 2 on CLI usage errors. Do not write files.
9. Run fixture tests through existing Python unittest discovery and add a separate
   validator step to the existing verify job. Document the local command.

### Non-functional requirements

- Python standard library only; no network, subprocess tools, or package installs.
- Diagnostics should identify the affected source and expected relationship.
- No automatic dependency/index generation or baseline exemptions.

## Contracts and data

Specification metadata is authoritative. The index table and graph are derived
views. Related ADR sets are collected from Architecture decisions only; links
elsewhere do not imply dependencies. Link checks apply to the source spec/index,
not recursively to every linked document. Markdown support is intentionally
bounded; future syntax additions require tests and an explicit parser update.

## Technical constraints

Reuse current Python unittest discovery and retain the verify job identity.
No skill changes are included. Do not alter historical approval or verification
claims to satisfy a parser.

## Invariants

- Validation performs no repository writes or external requests.
- Cycles remain errors even when documented as unresolved.
- Templates and fenced examples do not create dependencies or findings.
- Coverage presence does not establish approval or execution evidence.

## Edge cases and failures

Malformed input, invalid UTF-8, unreadable files, absent spec/index directories,
unknown references, duplicate fields, and invalid Mermaid statements fail with
source diagnostics rather than a success message or Python traceback.

## Migration and compatibility

Existing metadata, prose, index layout, and independent-label suffix remain
supported. Fix only objectively inconsistent existing links/edges if uncovered;
do not change architectural decisions. Rollback removes the CI step and tool.

## Acceptance criteria

- [x] Valid fixtures and the repository validate without writes or network access.
- [x] Invalid fixtures fail with deterministic, actionable diagnostics.
- [x] Metadata/index/graph and ADR discrepancies cannot pass validation.
- [x] CI runs the validator and its fixture suite under the existing verify job.

## Verification

User selected comprehensive coverage. Test valid and invalid metadata, all statuses,
placeholders versus code examples, coverage records, multiple tables/standalone
rows, ADR comparisons, shared/independent dependencies, cycles, duplicate/missing
nodes and edges, unsupported graphs, path normalization/escaping/symlinks,
reference links and heading anchors, CLI defaults/errors/read failures, deterministic
output, and read-only behavior. Confirm expected red before implementation. Run
new tests, all Python tooling tests, repository validation, workflow validation
when available, documentation link checks, and whitespace checks. App/backend
and browser suites are not required because application code is unchanged.

### Executed verification

On 2026-10-16, the CLI scaffold accepted invalid documents and failed the new
behavior tests as expected (17 test methods, with failing invalid-input subcases).
After implementation, 22 validator tests passed, including later red/green cases
for heading code spans, document-scoped references, and external autolink counts.
The full Python tooling discovery passed all 48 tests. The validator accepted all
28 repository specifications. Actionlint validated the updated workflow;
formatting checks for changed Markdown/workflow files and git diff --check passed.

Repository validation identified and corrected only missing index references:
build automation to ADR-0003, resource ownership to ADR-0004, and API decomposition
to ADR-0001/0002. Authoritative specification metadata, dependencies, and historical
approval claims were preserved. Templates and unrelated untracked files were not
modified. Application/backend/browser suites and packaging were not rerun because
application code and build behavior are unchanged. Hosted CI remains pending
publication; local checks do not establish a successful GitHub run.

## Architecture decisions

[ADR-0014](../docs/adr/0014-specification-validation.md): offline validation,
authoritative metadata, and CI enforcement.

## Open questions

None.
