# Skills

Place repository-specific reusable skills under:

```text
skills/<skill-name>/SKILL.md
```

A skill should encode a recurring and sufficiently stable workflow. Supporting
scripts, templates, and reference material may live beside its `SKILL.md` when
they are needed by that workflow.

Do not create a skill merely to hold repository policy. Mandatory repository
rules belong in the root `AGENTS.md`.

## PMD report analysis

[pmd-report-analysis](pmd-report-analysis/SKILL.md) combines the read-only
`python3 scripts/analyze-pmd.py` parser with contextual Java review. It can save
new Markdown/JSON artifacts and compare a previous JSON summary without changing
PMD rules or the build. See the [specification](../spec/pmd-report-analysis.md).

Run its fixture tests with:

```sh
python3 -B -m unittest discover -s scripts -p test_analyze_pmd.py
```

## Refactor design explanation

[refactor-design-explanation](refactor-design-explanation/SKILL.md) explains a
proposed method/class refactor with reasoning, relevant PMD rules, pattern
trade-offs, before/after examples, and compatibility-focused verification. It
reuses the design-audit framework and PMD analysis tooling; it does not implement
changes automatically.

Example request:

> Use skills/refactor-design-explanation/SKILL.md to explain how you would refactor
> JsonHttp.send, including PMD rules, patterns, code examples, and verification.

## Frontend analysis review

[frontend-analysis-review](frontend-analysis-review/SKILL.md) uses the existing
ESLint/Knip analyzer and deterministic review CLI for source scopes, rule/file
counts, native metrics, provenance, and saved-baseline comparisons. It reuses the
audit/refactor skills for recommendations and does not automatically fix code or
change analysis policy. See the [specification](../spec/frontend-analysis-review.md).

```sh
npm --prefix frontend run review:analysis
npm --prefix frontend run test:analysis
```
