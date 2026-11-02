---
name: AI Context Authoring
layer: local
dirs: [.ai]
description: How this repository's AI context is organised, regenerated, and extended - read before editing anything under .ai/
---

# AI Context Authoring

This directory is the source of the repository's AI context:

- `manifest.yaml` — the repository's identity for assembly (`product` and `modules`). Declaring a new module takes three steps together: add its path under `modules:`, add its `CLAUDE.md` negation to `.gitignore` as `!/<path>/CLAUDE.md` with the leading slash (or the new shim silently never enters git; without the slash the line un-ignores every personal `CLAUDE.md` in every folder), and run `gbuddy setup`, committing the generated files. Every directory with a build file is declared (nested Maven modules are covered by their declared parent). Deliberately not modules: `release` (`gbuddy audit` reports it as an undeclared module on every run; that is expected), `helm`, `docs`, `assets`, `playwright` (the top-level folder; `gravitee-am-test/playwright` gets its `AGENTS.md` from a `dirs:` rule), `.circleci`, `docker` other than `docker/local-stack`, and `scripts` other than `scripts/migration-tool`.
- `rules/` — repo-specific always-on rules, assembled into the generated `AGENTS.md` files together with the shared organisation-wide layers. A rule with `dirs:` in its frontmatter lands in those directories' files instead of root, beside the shared rule it adjusts.
- `generated.yaml` — the record of generated files; `AGENTS.md`, `CLAUDE.md`, and `.github/instructions/**` are generated from the sources above. **Never edit generated files by hand** — edit a rule, run `gbuddy setup`, and commit the result; `gbuddy check` verifies no generated file was hand-edited since the last regeneration. If `generated.yaml` conflicts on a rebase or merge, do not resolve it by hand: once the source rules are resolved, delete `.ai/generated.yaml`, run `gbuddy setup`, add the regenerated files, and continue the rebase or merge. `gbuddy` is a Gravitee-internal tool: if you cannot run it, edit only the source rule under `.ai/rules/`, say so in your PR, and leave the generated files untouched for a maintainer to regenerate.

## What earns a rule

- Only add rules that earn their token cost — not things easily inferred from context (scripts in `package.json`, standard framework behaviour, language syntax) and not things a check already enforces.
- Place content at the right level: if a line applies only to some directories, scope it with `dirs:` beside the shared rule it adjusts; if it is a procedure or a command block, put it in `docs/agent-standards/commands.md` and point to it with one line.
- `am-review.md` repeats the AM checks of the `code-review` skill under `docs/agent-standards/skills/`, so that review tools that never load skills see them; change both together.
