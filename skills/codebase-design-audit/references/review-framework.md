# Review framework

## Analytical lenses

Treat these bodies of design guidance as lenses, not absolute rules:

- **Clean Code:** local readability, naming, functions, tests, and
  implementation clarity.
- **Domain-Driven Design:** domain language, business rules, invariants,
  ownership, bounded contexts, and dependency direction.
- **Ousterhout-inspired design principles:** complexity management,
  information hiding, deep modules, interface design, and change amplification.

Summarize principles in original language. Do not reproduce substantial text
from source books.

A deviation from a principle is not itself a finding. Every finding must show
an observable cost or credible risk, such as defects, unsafe changes, leaked
implementation details, scattered business rules, excessive cognitive load,
poor testability, unnecessary coupling, or change amplification.

## Resolving conflicting guidance

Evaluate competing recommendations in this order:

1. Correctness, security, privacy, and data integrity.
2. Explicit product requirements and repository policy.
3. Evidence from the codebase and expected patterns of change.
4. Domain integrity and clear ownership of business rules.
5. Reduction of overall system complexity.
6. Local readability and stylistic preferences.

Prefer the option that minimizes total long-term complexity for expected
changes. Do not silently resolve a genuine conflict. Identify the principles
in tension, explain the relevant context, recommend an option, and state its
disadvantages and the conditions under which another option would be better.

For example, extracting small methods may improve local readability but create
many shallow abstractions. Recommend extraction only when it creates a coherent
abstraction, hides meaningful complexity, reduces caller knowledge, or
localizes future changes. Method length alone is not sufficient evidence.

## Proportional use of DDD

Apply DDD patterns only when business complexity or domain boundaries justify
them. Do not recommend entities, value objects, aggregates, repositories, or
domain services merely to conform to terminology. Simple CRUD behavior should
usually retain the simplest design that preserves correctness and ownership.

Keep strategic observations about bounded contexts, language, ownership, and
integration distinct from tactical pattern recommendations. A strategic DDD
issue does not automatically justify additional tactical abstractions.

## Finding thresholds

Prefer a few actionable findings over an unprioritized smell inventory.

Do not:

- Treat style preferences as defects.
- Assume every long function or large class is problematic.
- Recommend an abstraction without explaining which complexity it hides.
- Recommend DDD ceremony without demonstrated domain complexity.
- Infer runtime behavior from static inspection when it can be verified safely.
- Present a speculative concern as a confirmed defect.

Each finding must include evidence, concrete impact, contextual remediation,
trade-offs, and viable alternatives when they exist.

## Severity

Severity describes impact independently of remediation effort:

| Level | Points | Meaning |
| --- | ---: | --- |
| Critical | 60 | Likely severe correctness, security, data, or systemic failure |
| High | 45 | Material maintenance risk, recurring defects, or costly change amplification |
| Medium | 30 | Meaningful localized complexity or design friction |
| Low | 15 | Limited impact or opportunistic improvement |

## Confidence

Confidence describes the strength of the evidence:

| Level | Points | Meaning |
| --- | ---: | --- |
| High | 15 | Directly demonstrated by code, tests, or reproducible behavior |
| Medium | 10 | Strongly supported but dependent on an unstated assumption |
| Low | 5 | Plausible risk requiring further investigation |

## Estimated effort

Estimate implementation and verification effort using relative sizes, not
calendar-time promises:

| Level | Points | Typical scope |
| --- | ---: | --- |
| XS | 25 | Local isolated change with straightforward tests |
| S | 20 | A few files using established project patterns |
| M | 14 | Several components or moderate test changes |
| L | 8 | Cross-module work, migration, compatibility, or substantial testing |
| XL | 3 | Architectural change, staged migration, or significant uncertainty |

Account for implementation complexity, tests, integration, refactoring, data
or API migrations, compatibility, rollout, documentation, uncertainty, and
coordination. State assumptions and give a brief effort rationale.

Effort points represent ease of remediation: cheaper fixes receive more points
when impact and confidence are comparable. Keep the effort estimate visible so
the aggregate does not obscure development cost.

## Priority rating

Calculate the remediation priority mechanically:

```text
priority rating = severity points + confidence points + effort points
```

The possible range is 23–100:

| Rating | Priority |
| ---: | --- |
| 85–100 | Immediate |
| 70–84 | High |
| 50–69 | Medium |
| 23–49 | Low |

Use the rating for ordering remediation, not as a substitute for judgment.
Severity, confidence, and effort must remain separately visible. Recommend
investigation of a low-confidence concern with potentially critical impact
even if its aggregate rating is lower.

Qualitative fields such as the primary lens, competing principle, trade-offs,
and alternatives inform judgment but are not assigned artificial numeric
values.

## Finding schema

```md
## Finding: <concise title>

- Priority rating: <score>/100 — <band>
- Calculation: <severity points> + <confidence points> + <effort points>
- Severity: Critical | High | Medium | Low
- Confidence: High | Medium | Low
- Estimated effort: XS | S | M | L | XL
- Effort rationale: <scope, complexity, verification, and assumptions>
- Location: `<file>:<line>`
- Evidence: <observable code or behavior>
- Concrete impact: <maintenance, correctness, or complexity cost>
- Primary lens: Clean Code | DDD | Ousterhout
- Competing principle: <principle or `None`>
- Contextual recommendation: <recommended remediation>
- Trade-offs: <costs and disadvantages>
- Alternatives: <other viable approaches or `None`>
```

Ensure the displayed score matches the calculation. For example, High
severity, High confidence, and Medium effort yields `45 + 15 + 14 = 74`, or
High priority.
