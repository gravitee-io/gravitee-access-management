---
name: AM workflow
layer: local
description: Plan-first and approval discipline stricter than the shared rules
---

# AM workflow

## Plan first

- Produce a short plan (goal, steps, files, tests, validation criteria) before implementing.
- **Stop after the plan** unless the user explicitly says "continue".
- For non-trivial tasks (behavior/API/data changes, unclear requirements, or changes spanning 3+ files), structure the plan using the Work Request template headings.
- When relevant to the change type (e.g., API contracts, migrations), apply the corresponding checklist during planning and validation; reference it in the plan and summarise the key items performed.

## Ambiguity handling

- If requirements are ambiguous or under-specified: **ask clarifying questions** and propose **2–3 options** with a recommended approach.

## Execution discipline

- **Cite key files inspected** (code + docs) that informed changes.
- Search for similar implementations first before writing new code.
- Prefer safe/dry-run/diff modes first.
- Freely create, modify, or update code, test and configuration files as part of normal implementation.
- **Ask before deleting any files** (code or documentation).
- **Do not execute commands that modify state, data, or external systems without explicit approval.**
- Safe commands: `git status`, `git diff`, `git log`, read-only searches, typecheck/lint (read-only).
- Always ask before: `git commit`, `git push`, dependency installs, formatter auto-fixes, SDK regeneration, migrations.

## Documentation discipline

- If `docs/agent-standards/commands.md` is missing or incomplete: suggest commands; only create/update after developer approval.
- Keep single source of truth: update existing docs; **do not create new documentation files** (*.md, README) unless explicitly asked.
- JavaDoc and code documentation are always encouraged.
- If recurring caveats are discovered: call them out and propose whether to update existing docs.

## Cross-agent consistency

- When multiple agents or iterations work on the same area, converge on a **single agreed approach**.
- Do not introduce competing patterns or parallel abstractions.
