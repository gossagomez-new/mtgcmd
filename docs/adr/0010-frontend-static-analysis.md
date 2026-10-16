# ADR-0010: Frontend static analysis

- Status: Accepted
- Date: 2026-10-16
- Related specification: [Frontend static analysis](../../spec/frontend-static-analysis.md)

## Context

The React/TypeScript frontend already uses ESLint and Prettier, but ESLint does
not use type information and the build does not produce analysis reports. The
user requested open-source analysis including ESLint complexity rules, with an
advisory rollout comparable to backend PMD reporting.

## Decision

Retain existing ESLint lint enforcement. Add a separate analysis configuration
which includes that policy and adds recommended type-aware TypeScript and JSX
accessibility rules at warning severity. Add ESLint complexity, max-depth, and
max-lines-per-function warnings for production code. Add Knip for the project
dependency/usage graph. Pin installed tooling through package-lock.json.

Run analysis in the full build and produce ignored JSON/text reports. New findings
are advisory. Existing ESLint errors, parser/configuration/tool failures, and
report failures block the build. Keep formatting with Prettier. Security-pattern
scanning with Opengrep is deferred.

## Alternatives considered

### Replace tooling with Biome

- Benefits: Integrated fast linting/formatting.
- Drawbacks: Migration and changed rule/format behavior before measuring a need.
- Reason not selected: Existing ESLint integration can provide the requested checks.

### Only ESLint

- Benefits: Fewer tools and one report format.
- Drawbacks: Does not replace Knip's project-wide unused-file/export/dependency graph.
- Reason not selected: Those complementary findings are part of the approved scope.

### Make every new finding blocking immediately

- Benefits: Strong enforcement from the first run.
- Drawbacks: Forces unrelated cleanup and premature acceptance of every rule.
- Reason not selected: Start advisory, then promote reviewed rules deliberately.

## Rationale

This extends familiar tooling with complementary analysis and preserves the
existing quality gate. Separating findings from execution failures prevents a
broken analyzer from silently appearing clean.

## Consequences

### Benefits

- Reproducible local reports, including type-aware and project-wide analysis.
- No application rewrite required to adopt diagnostics.

### Drawbacks and risks

- Typed linting and Knip add build time and dependency maintenance.
- Static accessibility checks do not establish full accessibility; Knip needs
  accurate entry points and findings need contextual review.

### Operational impact

Developers run npm ci after checkout. Full builds generate report files outside
dist and the jar. Review reports before promoting rules or suppressing findings.

## Follow-up actions

- [ ] Review initial findings and prioritize behavior-preserving fixes separately.

## References

- [Typed ESLint](https://typescript-eslint.io/getting-started/typed-linting/)
- [JSX accessibility](https://github.com/jsx-eslint/eslint-plugin-jsx-a11y)
- [Knip CLI](https://knip.dev/reference/cli)
- [ESLint complexity](https://eslint.org/docs/latest/rules/complexity)
