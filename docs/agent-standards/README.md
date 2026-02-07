# Agent Standards

Detailed, tool-specific rules and skills that extend the root `AGENTS.md`.

## Relationship to AGENTS.md

`AGENTS.md` (repo root) is the **single source of truth** for all AI coding agents. It contains the universal project context, commands, conventions, and safety rules that every agent should follow.

The files in this directory provide **tool-specific extensions**:

- **`cursor-rules/*.mdc`** — Cursor-format rules with frontmatter metadata (`alwaysApply`, `description`, glob patterns). These add Cursor-specific delivery mechanisms (conditional loading, scoping) on top of the same content in `AGENTS.md`.
- **`skills/`** — Reusable agent skills shared across tools.
- **`templates/`** — Structured task and checklist templates.
- **`commands.md`** — Canonical verified commands (also summarised in `AGENTS.md` Section 1).

When updating rules, **update `AGENTS.md` first**, then keep the Cursor rules consistent.

## Structure

```
docs/agent-standards/
├── cursor-rules/    # Cursor-specific rules (.mdc with frontmatter)
├── skills/          # Reusable agent skills
├── templates/       # Task templates
└── commands.md      # Verified project commands
```

## How It Works

### Rules

**Cursor** uses rules directly via symlink:
```
.cursor/rules → docs/agent-standards/cursor-rules/
```

**Antigravity** references Cursor rules via @ mentions:
```markdown
---
trigger: always_on
---
@/docs/agent-standards/cursor-rules/00-context.mdc
```

**Claude Code** reads `CLAUDE.md` at the repo root, which bridges to `AGENTS.md`:
```
CLAUDE.md → @AGENTS.md
```

**Copilot, Codex, Windsurf, Zed** read `AGENTS.md` natively.

**Result:** Edit `AGENTS.md` for universal rules; edit `cursor-rules/` only for Cursor-specific delivery metadata.

### Skills

Both tools share skills via symlinks:
```
.cursor/skills → docs/agent-standards/skills/
.agent/skills → docs/agent-standards/skills/
```

## Adding a New Rule

1. Create Cursor rule in `cursor-rules/your-rule.mdc`
2. Create Antigravity wrapper in `.agent/rules/your-rule.md` with @ mention
3. Reload Antigravity workspace

## Updating Rules

1. Edit the Cursor rule in `cursor-rules/`
2. Antigravity: Reload workspace

## Adding a New Skill

1. Create `skills/skill-name/SKILL.md`
2. Available automatically to both tools

## Rule Naming

```
00-context.mdc          # Global (always on)
10-workflow.mdc         # Global (always on)
20-safety-security.mdc  # Global (always on)
30-*.mdc                # Domain-specific (conditional)
```

Numbers indicate loading order.
