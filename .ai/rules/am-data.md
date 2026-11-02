---
name: AM data
layer: local
description: MongoDB index changesets
dirs: [gravitee-am-repository, gravitee-am-dataplane]
---

# AM data

## MongoDB indexes (changesets)

- Indexes are declared in append-only changesets, one JSON file per scope: `mongodb/indexes/{management,oauth2,gateway,dataplane}.json`.
- A committed changeset is **immutable**, released or not: never edit, reorder or remove it. Every change is a new changeset with a new unique `id` naming its intent (e.g. `rate-limit-created-at`), holding the `create`/`drop` delta.
- Each changeset is its own upgrader, recorded as `<ScopeUpgraderClass>_<id>`: a node applies, in file order, only the changesets it has no record of, each with its own delta. Backports are cherry-picked as-is, without renaming their changeset; a backported changeset that depends on an earlier one (drops or redefines its index) needs that one backported too, which the backport review checks.
- Changesets hold literal values only: no `${...}` placeholders or environment-dependent values. Config-driven indexes (e.g. the events TTL) stay in their repository.
- When adding a changeset, pin its id and checksum in `MongoIndexUpgradersTest` / `MongoDataPlaneIndexUpgraderTest`. Never change the pinned checksum of an existing changeset.

## Reference

- Migration checklist: `docs/agent-standards/templates/migration_checklist.template.md`
