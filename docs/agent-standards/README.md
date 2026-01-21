# Agent Standards

Single source of truth for AI agent rules and skills used by both Cursor IDE and Google Antigravity.

## Structure

```
docs/agent-standards/
├── cursor-rules/    # Full rule content (Cursor syntax)
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

**Result:** Edit once in `cursor-rules/`, both tools benefit.

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
