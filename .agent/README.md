# Google Antigravity Configuration

Google Antigravity AI agent configuration.

## Structure

```
.agent/
├── rules/     # Lightweight wrappers (@ mention Cursor rules)
└── skills/    → ../docs/agent-standards/skills/  (symlink)
```

## How It Works

**Rules**: Antigravity rules are minimal wrappers that reference Cursor rules via @ mentions:

```markdown
---
trigger: always_on
---
# Rule Title

@/docs/agent-standards/cursor-rules/00-context.mdc
```

This loads the full Cursor rule content without duplication.

**Skills**: Symlinked to `docs/agent-standards/skills/` (shared with Cursor)

## Key Discovery

✅ Antigravity's `@mention` feature successfully loads Cursor `.mdc` files  
✅ Ignores incompatible `alwaysApply: true` frontmatter  
✅ Enables single source of truth  

## Documentation

See [`docs/agent-standards/README.md`](../docs/agent-standards/README.md) for:
- How to add/update rules and skills
- Architecture overview
- Maintenance procedures
