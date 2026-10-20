# ADR-0023: Task-scoped verification commands

- Status: Accepted
- Date: 2026-10-19
- Related specification: [Task-scoped verification](../../spec/task-scoped-verification.md)

## Context

Long environment-prefixed commands and changing log filenames create repeated
approval friction. The existing runner already centralizes fixed checks and
source-bound evidence. A task identifier can distinguish requested command
prefixes without handing the runner control over Codex's approval rules.
The user approved this ADR and comprehensive verification.

## Decision

Extend verify.sh with a task-first option and fixed frontend/backend/browser/full
scopes. Reuse the existing runner, adding browser-only selection. Keep fresh logs
inside the runner and optional local path defaults in ignored JSON, validated as
paths rather than executable settings. Record task labels and effective settings
in existing source-bound evidence without changing legacy invocation behavior.

Recommend a task-specific command prefix for user review. Do not install rules,
auto-approve commands, or claim rules expire or pin script contents. Existing
inherited environment variables win over local defaults.

## Alternatives considered

### Separate task runner

- Benefits: Isolated interface.
- Drawbacks: Duplicates execution, signal cleanup and evidence behavior.
- Reason not selected: The current runner already owns those responsibilities.

### Broad shell or interpreter permission

- Benefits: Fewer prompts for many command shapes.
- Drawbacks: Much broader scope than verification and harder to review.
- Reason not selected: The user wants bounded verification commands.

### Environment-prefixed commands only

- Benefits: No tooling change.
- Drawbacks: Repeats configuration and changes command approval text.
- Reason not selected: Does not address recurring approval friction.

## Rationale

A stable task-first prefix allows reusable human approval while keeping execution
limited to known profiles. Path-only defaults avoid a general local command hook.
Preserving the runner prevents a second verification pipeline.

## Consequences

### Benefits

- Reusable per-task command prefixes and fresh, labelled evidence.
- Browser-only retries and environment setup without shell wrappers.

### Drawbacks and risks

- Codex owns rule persistence; old task rules may need manual removal.
- Script/dependency contents and inherited environment remain trusted execution inputs.
- Local settings introduce validation and evidence-compatibility responsibilities.
- Configured JVM paths cannot contain whitespace/quotes because Maven's launcher
  splits option variables; executable paths may contain spaces.

### Operational impact

No production changes or deployment. Existing CLI/CI remain available. Local path
settings stay untracked; output is private and never cleaned up automatically.

## Follow-up actions

- [x] Verify compatibility, invalid-input boundaries and real task execution.

## References

- [ADR-0015](0015-local-verification-evidence.md)
- [OpenAI command rules](https://learn.chatgpt.com/docs/agent-configuration/rules)
