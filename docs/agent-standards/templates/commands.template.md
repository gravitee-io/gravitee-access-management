---
name: "Canonical Commands"
description: "Template for documenting canonical build, test, lint, and contract commands for a repository"
---
# commands.md (canonical commands)

This file lists the canonical commands for building, testing, linting, and generating artefacts.
Keep it short and accurate. Prefer existing scripts over ad-hoc commands.

## Canonical sources
Fill commands from:
- package.json scripts
- pom.xml / Maven modules
- Makefile / Taskfile (if present)
- CI configuration (for CI-equivalent commands)

## Quick start
- Prerequisites: <e.g. Java 17, Node.js 20>
- Install deps (backend): <fill>
- Install deps (frontend): <fill>

## Backend (Java)
- Build: <fill>
- Unit tests: <fill>
- Integration tests: <fill or N/A>
- Lint/format: <fill or N/A>

## Frontend (Angular / TypeScript)
- Install deps: <fill>
- Dev server: <fill>
- Lint: <fill>
- Unit tests: <fill>
- Build: <fill>

## API / Contracts
- OpenAPI spec location: <path>
- Validate / generate OpenAPI: <fill>
- Regenerate SDK/clients: <fill>
- Contract/API tests: <fill>

## Common workflows
- Local verification (pre-PR): <1–3 commands>
- CI-equivalent verification: <fill>

## Owner & update policy
- Owner(s): <team/handle>
- Update when tooling, scripts, or mandatory checks change.
