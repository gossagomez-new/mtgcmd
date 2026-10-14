---
name: pmd-report-analysis
description: Analyze this repository's native PMD XML reports, compare saved summaries, and prioritize evidence-backed Java cleanup. Use for PMD report reviews; does not automatically fix code or change analysis rules.
---

# PMD report analysis

Use deterministic counts from the repository parser, then inspect code to interpret
findings. This is a companion to the general design-audit skill; leave that skill's
workflow unchanged.

## Read or regenerate

Default to reading `backend/target/pmd.xml`. Report its timestamp and that freshness
against the current source revision is unverified. Do not imply a new build ran.
If the report is missing, say so; regenerate only when the requested task includes
that action. Discover the configured command in the backend README/build files;
currently `mvn -f backend/pom.xml test-compile pmd:pmd` generates native reports.
Respect the environment's execution permissions. Never replace a failed report
run with an assertion of clean analysis.

## Summarize and compare

From the repository root:

```sh
python3 scripts/analyze-pmd.py
```

Optional new artifacts and baseline comparison:

```sh
python3 scripts/analyze-pmd.py --markdown backend/target/pmd-analysis.md --json backend/target/pmd-summary.json
python3 scripts/analyze-pmd.py --baseline /tmp/previous-pmd-summary.json
```

Output files must not already exist. Pick a fresh name when an artifact exists;
do not delete a previous baseline to make room. Preserve a baseline outside
`backend/target` if it must survive Maven clean. An explicit report path and
`--root` support other checkouts; defaults work from any directory.

Exit 0 means the report has no analysis diagnostics, regardless of violations.
Exit 2 means PMD reported processing/configuration errors: the summary remains
available, but analysis is incomplete. Exit 1 means input/output validation failed.
Suppressed findings are separate from active totals. Other-source scope must remain
visible rather than silently counted as production or tests.

Comparisons are aggregate counts by scope, rule, and normalized file, not identities
of introduced or resolved findings. Inspect PMD versions, ruleset changes, source
coverage, and incomplete baselines before attributing deltas to code quality.
An increase after extraction or new tests can reflect style/documentation rules.
The baseline never suppresses findings or creates a build gate.

## Interpret evidence

Inspect the implicated source and relevant tests before recommending remediation.
Use the existing [audit review framework](../codebase-design-audit/references/review-framework.md)
for design findings that need evidence, severity/confidence, effort, and trade-offs.
Do not manufacture findings solely from rule counts or PMD priority numbers.

Pay particular attention to:

- Complexity and coupling: identify independently changing responsibilities; avoid
  extracting methods merely to push complexity into another class.
- Exception warnings: trace translation, cause preservation, unwrapping, interruption,
  and tests before recommending additional wrappers or narrower catches.
- Resource warnings: establish lifecycle ownership, especially framework-managed
  WebSockets and shared clients, before recommending closure.
- Style/documentation findings and test fixtures: separate mechanical cleanup from
  behavior or maintainability risks. Multiple assertions may verify one invariant.

Lead the user-facing review with the outcome of any previous targeted cleanup,
then counts separated by production/tests/other, remaining actionable hotspots,
and a reasoned next recommendation. Link local source locations. Report analysis
errors and verification limits explicitly. Summarize dominant conventional rules
without presenting every advisory as a defect. Code fixes, rule changes, baseline
writes, or report regeneration require the scope/authorization of the active task;
ordinary report review is read-only.
