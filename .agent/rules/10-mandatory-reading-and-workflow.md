---
trigger: manual
description: "Workflow: mandatory reading, plan-first, command handling, and documentation discipline"
---

# Workflow

> This rule is the source of truth for Cursor agent behaviour. Templates and commands are supporting references.

## Before Any Implementation

- Produce a short plan (goal + steps + files + tests + validation criteria).
- **Stop after the plan** unless the user explicitly says “continue”.

## Ambiguity Handling

- If requirements are ambiguous or under-specified: **ask clarifying questions** and propose **2–3 options** with a recommended approach.

## Execution Discipline

- **Cite key files inspected** (code + docs) that informed changes.
- Prefer safe/dry-run/diff modes first.
- **Freely create, modify, or update code files** as part of normal implementation.
- **Ask before deleting any files** (code or documentation).
- **Do not execute commands that modify state, data, or external systems without explicit approval.**
- Safe commands examples: `git status`, `git diff`, `git log`, read-only searches, typecheck/lint that does not write.
- Always ask before: `git commit`, `git push`, dependency installs, formatter auto-fixes, SDK regeneration, migrations.

## Documentation Discipline

- If `commands.md` is missing or incomplete: suggest commands; only create/update after developer approval.
- Keep single source of truth: update existing docs; **do not create new documentation/guideline files (*.md, README)** unless explicitly asked.
- If recurring caveats are discovered: call them out and propose whether to update existing docs.
- JavaDoc, inline comments, and code documentation are always encouraged.

## Templates (Guidance)

- Templates under `@docs/agent-standards/templates/` are guidance:
  - use them to structure plans and self-checks
  - do not create new documentation/guideline files (*.md, README) unless explicitly requested
  - code files, tests, and configuration files can be created freely as needed
- For non-trivial tasks (behaviour/API/data changes, unclear requirements, or changes spanning 3+ files), structure the plan using the Work Request template headings.
- When relevant to the change type (e.g., API contracts, migrations), apply the corresponding checklist during planning and validation; reference it in the plan and summarise the key items performed.