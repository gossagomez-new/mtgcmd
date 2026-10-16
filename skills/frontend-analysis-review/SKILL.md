---
name: frontend-analysis-review
description: Review this repository's ESLint/Knip findings, compare explicitly saved frontend baselines, and prioritize source-backed cleanup. Use for frontend analysis reviews; does not automatically refactor code or change analysis policy.
---

# Frontend analysis review

Use the existing analyzer and deterministic review CLI, then inspect code to
interpret the findings. Reuse the [design-audit framework](../codebase-design-audit/references/review-framework.md)
and [refactor explanation](../refactor-design-explanation/SKILL.md) when the user
asks how to address a hotspot.

## Read or regenerate

Read `frontend/reports/static-analysis/summary.json` and the native reports by
default. If regeneration is part of the user's request, run the configured
`npm --prefix frontend run analyze`; otherwise report missing or incomplete data
without silently running a build. Execution permissions still apply.

The analyzer clears its owned report files. Before regenerating, preserve an
explicitly requested baseline outside that directory. Do not overwrite or silently
advance a baseline. A saved baseline is a review JSON artifact, not the native
analyzer completion summary.

## Summarize and compare

From the repository root:

```sh
npm --prefix frontend run review:analysis
npm --prefix frontend run review:analysis -- --baseline /tmp/previous-frontend-review.json
npm --prefix frontend run review:analysis -- --json /tmp/new-frontend-review.json --markdown /tmp/new-frontend-review.md
```

Output files must be new. Select another name if one exists; do not delete old
artifacts to make room. `--root` selects the frontend source directory and
`--reports` an explicit report directory; defaults work from any current directory
when invoking the script by its path. Use `--help` for the exact CLI options.

Exit 0 means successful parsing/review, even with advisory findings. Exit 2 means
analysis is incomplete. Exit 1 means input/output/usage validation failed. Never
interpret a missing report, failed command, or empty captured output as clean
analysis; inspect command exit status and execution errors.

Freshness is `current`, `stale`, or `unverified`. Current means the recorded
frontend input fingerprint and raw report hashes match; it does not prove backend
state, external plugins, environment equivalence, or correctness. Legacy reports
without provenance are unverified. For comparisons, inspect package lock/config
changes and runtime metadata before attributing differences to a refactor.

Comparisons use occurrence multisets of tool, normalized file, rule, severity, and
message. Line/column moves do not change identity. Duplicates remain counted;
renames or changed diagnostic messages appear as resolved/new. Do not describe
those identities as proof of semantic introductions/removals. Only quote exact
complexity/length/depth metrics when supplied by a native diagnostic; a clean
component has no inferred metric.

## Interpret and report

Lead with the outcome of the previous targeted change, counts by production,
tests, and other sources, then the remaining hotspots and a reasoned next target.
Link the implicated source and relevant tests. Treat report counts as evidence to
investigate, not an automatic priority score. Distinguish presentation complexity,
workflow/state ownership, unsafe boundary parsing, test-fixture issues, and
length-only findings. Preserve privacy, permissions, command payloads, and state
lifetimes when proposing extraction.

Use the existing audit/refactor skills instead of duplicating their design
procedure. Report analysis failures, stale/unverified provenance, comparison
limitations, and checks not executed. Reading/recommending does not authorize
source changes, suppressions, threshold changes, baseline writes, commits,
publishing, or deployment.
