# Cursor Configuration

Cursor IDE-specific configuration.

## Structure

```
.cursor/
├── rules/    → ../docs/agent-standards/cursor-rules/  (symlink)
└── skills/   → ../docs/agent-standards/skills/        (symlink)
```

## How It Works

- **Rules**: Symlinked to `docs/agent-standards/cursor-rules/` (full content, Cursor syntax)
- **Skills**: Symlinked to `docs/agent-standards/skills/` (shared with Antigravity)

## Documentation

See [`docs/agent-standards/README.md`](../docs/agent-standards/README.md) for:
- How to add/update rules and skills
- Architecture overview
- Maintenance procedures

---

**Note:** Cursor uses `.mdc` files with `alwaysApply: true` syntax. Antigravity uses different syntax but references the same content via @ mentions.
