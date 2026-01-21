---
name: "Data & Migration Checklist"
description: "Checklist template for data persistence, repository changes, schema evolution, and migrations"
---
# Data / Migration Checklist

## Scope
- Storage: Mongo | JDBC | Both
- Entity(s): <list>
- Change type: Schema | Index | Query | Data migration | Behavioural

## Repository changes
- [ ] Repository interface extends CrudRepository<T, String> (unless established pattern differs)
- [ ] Prefer scoped queries over broad reads + filtering
- [ ] Avoid loading wide datasets unnecessarily
- [ ] Naming follows existing patterns (Mongo{Entity}Repository / Jdbc{Entity}Repository)

## Liquibase (if applicable)
- [ ] Related changes grouped into a single changeset
- [ ] Changeset id/author follows repo conventions
- [ ] Rollback considered (if required)
- [ ] Existing migration patterns reviewed

## Mongo indexes (if applicable)
- [ ] Index name follows established initials + sort-order convention
- [ ] Index fields/order reviewed against query patterns
- [ ] New indexes justified

## Compatibility & rollout
- [ ] Backward/forward compatibility considered
- [ ] No destructive changes without explicit migration plan
- [ ] Operational impact assessed (startup, locks, reindexing)

## Tests & validation
- [ ] Behaviour parity verified across Mongo and JDBC (if both supported)
- [ ] Integration tests updated/added
- [ ] Commands run (refer to commands.md)

## Notes
- Commands run:
- Files changed:
- Risks/follow-ups:
