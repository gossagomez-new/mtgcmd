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
