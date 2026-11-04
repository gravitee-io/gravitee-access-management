# Cursor Configuration

Cursor-specific configuration.

## Structure

```
.cursor/
└── skills/   → ../docs/agent-standards/skills/  (symlink)
```

## How It Works

- **Rules**: Cursor reads the generated `AGENTS.md` at the repository root and in each module folder. The rules are written in `.ai/rules/`; see [`docs/agent-standards/README.md`](../docs/agent-standards/README.md) for how to change them.
- **Skills**: symlinked to `docs/agent-standards/skills/` (shared with the other tools).
