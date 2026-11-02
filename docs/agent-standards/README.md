# Agent Standards

Skills, templates and the command reference that AI coding agents use in this repository. The rules agents follow are no longer kept here; they live in `.ai/rules/`.

## Structure

```
docs/agent-standards/
├── skills/          # Reusable agent skills
├── templates/       # Task templates
└── commands.md      # Verified project commands
```

## Where the rules live

The rules are written once, in `.ai/rules/`. A tool called `gbuddy` turns them, together with the shared Gravitee rules, into the files the AI tools read: `AGENTS.md` at the repository root and in each module folder, a `CLAUDE.md` beside each of them, and `.github/instructions/code-review.instructions.md` for Copilot code review. Cursor, Codex, Copilot and Antigravity read `AGENTS.md`; Claude Code reads `CLAUDE.md`, which points to `AGENTS.md`.

Never edit a generated file by hand. Its first lines say it is generated, and the `Verify AI context` check fails a pull request that edits one by hand.

## Changing a rule

1. Edit or add the rule in `.ai/rules/` (read `.ai/rules/ai-context-authoring.md` first).
2. Run `gbuddy setup` at the repository root.
3. Commit everything it changed, including `.ai/generated.yaml`.

If you cannot run `gbuddy`, change only the rule in `.ai/rules/`, say so in the pull request, and ask a maintainer to regenerate the files on your branch.

## Skills

Claude Code, Cursor and Antigravity share the skills through symlinks:

```
.claude/skills → docs/agent-standards/skills/
.cursor/skills → docs/agent-standards/skills/
.agent/skills  → docs/agent-standards/skills/
```

To add a skill, create `skills/skill-name/SKILL.md`; every tool picks it up.

## Templates and commands

`templates/` holds the plan and checklist templates that the rules point to. `commands.md` is the reference for build, test and validation commands. Update it when a tool, script or required check changes.
