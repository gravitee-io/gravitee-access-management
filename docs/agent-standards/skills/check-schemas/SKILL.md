---
name: check-schemas
description: "Detect breaking changes in schema-form.json plugin descriptors; run the schema compatibility checker, interpret findings, and guide fixes. Use when modifying schema-form.json files, checking backward compatibility of plugin schemas, validating schema changes before merge, or investigating schema migration issues."
---

# Schema Backward-Compatibility Check

Run a schema compatibility check to detect breaking changes in `schema-form.json` files.

The checker works in any repo that contains `schema-form.json` plugin descriptor files. Examples
below use Gravitee AM conventions but the tooling is generic.

## Workflow

1. **Run the check** — see [Quick usage](#quick-usage) for commands
2. **Interpret findings** — see [Understanding findings](#understanding-findings)
3. **Fix errors** — apply the matching fix from [How to fix breaking changes](#how-to-fix-breaking-changes)
4. **Re-run to verify** — repeat step 1 to confirm the fix resolved the finding
5. **Commit** — include the schema change alongside the code change

## Quick usage

When `--base` is omitted the baseline is resolved automatically
(see `.circleci/scripts/_compat_common.sh`):
- `master` or `x.y.x` release branches → `HEAD~1`
- All other branches → git tracking branch (`@{u}`) if set to a different branch, else `HEAD~1`

Auto-detection may only reach back one commit if tracking is not set up. For reliable results,
always pass `--base` explicitly.

```bash
# Check all schema-form.json files changed since merge-base (what CI does):
bash .circleci/scripts/schema-compat-check.sh

# Check against an explicit baseline — any valid git ref:
bash .circleci/scripts/schema-compat-check.sh --base 4.11.0          # tag
bash .circleci/scripts/schema-compat-check.sh --base origin/master   # branch tip
bash .circleci/scripts/schema-compat-check.sh --base abc1234         # commit SHA
bash .circleci/scripts/schema-compat-check.sh --base HEAD~10         # relative ref

# Include uncommitted (staged + unstaged) changes — useful locally before committing:
bash .circleci/scripts/schema-compat-check.sh --use-head false

# Same flag works for EE plugins:
bash .circleci/scripts/ee-schema-compat-check.sh --use-head false

# Check a single schema pair explicitly:
node scripts/schema-compatibility/check-schema-compatibility.mjs \
  --old path/to/old-schema-form.json \
  --new path/to/new-schema-form.json \
  --plugin my-plugin-name

# Permit breaking changes (major version bump):
node scripts/schema-compatibility/check-schema-compatibility.mjs \
  --old old.json --new new.json --allow-breaking
```

## What counts as a breaking change?

The most common breaking changes (ERROR — blocks CI):

| Category | Severity |
|---|---|
| Field added to `required` / new required field | ERROR |
| Property key removed or renamed | ERROR |
| `type` value changed | ERROR |
| Enum value removed / `enum` added to free-form field | ERROR |
| `additionalProperties: false` added | ERROR |
| Constraint tightened (`minLength`, `maxLength`, `minimum`, `maximum`, `minItems`, `maxItems`) | ERROR |

Non-breaking: optional field added, `description`/`title` changed, enum value added, constraint loosened, `const` removed.

For the full classification table (40+ categories) and authoritative fixture references, see [BREAKING_CHANGES_REFERENCE.md](BREAKING_CHANGES_REFERENCE.md).

## Breaking-change exemption

CI passes `--allow-breaking` (warnings only, exit 0) when the `pom.xml` version indicates a **minor version bump** (e.g. `4.11.x` → `4.12.x`) or a **first patch** (`4.12.0`). Logic is in `.circleci/scripts/_compat_common.sh`. Without a `pom.xml`, breaking changes always fail.

## Understanding findings

```
── Schema compatibility report: my-plugin ──

🔴  BREAKING CHANGES (1):
  ERROR  properties.timeout
         property "timeout" removed — existing data referencing this field will lose its value

🟡  WARNINGS (1):
  WARN   required[port]
         field "port" removed from required (demoted to optional) — generally safe but verify intent
```

## How to fix breaking changes

| Error | Fix |
|---|---|
| Field added to required | Make it optional (remove from `required`) or provide a default |
| Property removed | Keep the field; mark it deprecated in `description` |
| Type changed | Add a new field with the new type; keep the old field |
| Enum value removed | Keep the old value in the enum |
| Constraint tightened | Loosen or remove the constraint |

## Checking schemas from external plugin repos locally

For checking schemas in external plugins (EE plugins, Maven artifacts, ZIPs), see [EE_PLUGINS_REFERENCE.md](EE_PLUGINS_REFERENCE.md). Quick single-schema check:

```bash
node scripts/schema-compatibility/check-schema-compatibility.mjs \
  --old path/to/old-schema-form.json \
  --new path/to/new-schema-form.json \
  --plugin my-plugin-name
```

## Checker coverage and limitations

The checker covers the subset of JSON Schema used by AM plugin `schema-form.json` descriptors.
Three constructs cannot be fully classified by static analysis alone and emit WARN rather than
ERROR — see `scripts/schema-compatibility/test/fixtures/maybe-breaking/README.md` for the
reasoning behind each:

- **`if`-condition changes**: which instances hit `then`/`else` depends on runtime data
- **`not` content changes**: the semantics are inverted; direction of impact is unreliable statically
- **Tuple `items` structural changes**: impact depends on the length and content of existing arrays

Combiner branch reordering (`anyOf`/`oneOf`/`allOf`) produces false positives — branches are
compared positionally, so reordering looks like content changes.

To extend the checker, edit `compareSchemas` in
`scripts/schema-compatibility/check-schema-compatibility.mjs` and add fixture pairs under
`scripts/schema-compatibility/test/fixtures/`.

## Running the test suite

```bash
node scripts/schema-compatibility/test/run-tests.mjs
```

Fixtures are split into three categories:

| Directory | Assertion |
|-----------|-----------|
| `breaking/` | exit 1 |
| `non-breaking/` | exit 0 **and** no `WARN` in output |
| `maybe-breaking/` | exit 0 **and** at least one `WARN` in output |

`maybe-breaking/` captures changes the checker detected but cannot classify — the impact depends
on actual data. CI does not block; a human reviewer should assess. See
`scripts/schema-compatibility/test/fixtures/maybe-breaking/README.md` for detail.

Expected: 0 FAIL (SKIPs are expected for fixtures with no `old.json`; the runner reports the
exact count). The test suite also runs automatically as the first step of the
`schema-compat-check` CI job.
