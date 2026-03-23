---
name: check-schemas
description: Detect breaking changes in schema-form.json plugin descriptors; run the schema compatibility checker, interpret findings, and guide fixes.
---

# Schema Backwards-Compatibility Check

Run a schema compatibility check to detect breaking changes in `schema-form.json` files.

The checker works in any repo that contains `schema-form.json` plugin descriptor files. Examples
below use Gravitee AM conventions but the tooling is generic.

## Quick usage

When `--base` is omitted the baseline is resolved automatically
(see `.circleci/scripts/_schema_compat_common.sh`):
- `master` or `x.y.x` release branches → `HEAD~1`
- All other branches → `git merge-base HEAD origin/master`

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

The table below is a quick reference. The authoritative source is the fixture suite under
`scripts/schema-compatibility/test/fixtures/breaking/` and `non-breaking/` — each subdirectory
is a concrete example the checker is tested against.

| Category | Severity |
|---|---|
| Field added to `required` | ERROR |
| New required field (didn't exist before) | ERROR |
| Property key removed | ERROR |
| Property key renamed (= remove + add) | ERROR |
| `type` value changed | ERROR |
| `const` added or changed | ERROR |
| Enum value removed | ERROR |
| `enum` added to previously free-form field | ERROR |
| `additionalProperties: false` added | ERROR |
| `additionalProperties` schema added (e.g., `{type:"string"}`) | ERROR |
| `additionalProperties` schema type changed | ERROR |
| `additionalProperties` schema or `false` removed | Non-breaking |
| `pattern` or `format` added | ERROR |
| `minLength` increased or added | ERROR |
| `maxLength` decreased or added | ERROR |
| `minimum`/`maximum` tightened or added | ERROR |
| `minItems` increased or added | ERROR |
| `maxItems` decreased or added | ERROR |
| `uniqueItems: true` added | ERROR |
| `allOf` entry added | ERROR |
| `anyOf`/`oneOf` added from scratch | ERROR |
| `anyOf`/`oneOf` branch removed | ERROR |
| `if`/`then`/`else` added | ERROR |
| `not` added | ERROR |
| `allOf`/`anyOf`/`oneOf` branch content changed | ERROR |
| `then`/`else` content changed | ERROR |
| `then`/`else` branch added to existing conditional | ERROR |
| `then`/`else` branch removed from existing conditional | WARN (exit 0) |
| `if` condition changed (then/else unchanged) | WARN (exit 0) |
| `not` content changed or removed | WARN (exit 0) |
| Tuple `items` form or count changed | WARN (exit 0) |
| Tuple `items` positional schema changed | ERROR or WARN (recursed) |
| Required field added to `items` schema | ERROR |
| Property removed from `items` schema | ERROR |
| Definition removed from `definitions`/`$defs` | ERROR |
| Breaking change inside a definition | ERROR |
| Field removed from `required` (demoted to optional) | WARN (exit 0) |
| Optional field added | Non-breaking |
| Optional field added to `items` schema | Non-breaking |
| Definition added to `definitions`/`$defs` | Non-breaking |
| `const` removed (field becomes free-form) | Non-breaking |
| `description`/`title` added or changed | Non-breaking |
| `maxLength` increased | Non-breaking |
| `minimum`/`maximum` loosened | Non-breaking |
| Enum value added | Non-breaking |

## Breaking-change exemption

The CI scripts pass `--allow-breaking` (findings printed as warnings, exit 0) in two cases,
both detected by comparing the `pom.xml` `<version>` at baseline vs. HEAD
(logic in `.circleci/scripts/_schema_compat_common.sh`):

- **Minor version bump** — e.g. `4.11.x` → `4.12.x`. Breaking changes between releases are
  expected.
- **Patch is zero** — e.g. `4.12.0`. No release of this minor version exists yet, so there
  are no deployed configurations to protect.

If there is no `pom.xml` (non-Maven repos), version detection is skipped and breaking changes
always fail the build.

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

If the repo you're working in depends on plugins from other repos (e.g. EE plugins distributed as
ZIPs), you can compare their schemas against any baseline manually:

```bash
# 1. Obtain the new schema — from a ZIP artifact or local build output:
unzip -p /path/to/plugin.zip 'schemas/schema-form.json' > /tmp/new-schema.json
# — or, if the plugin is built locally:
cp other-repo/src/main/resources/schemas/schema-form.json /tmp/new-schema.json

# 2. Get the old schema.
#    NOTE: git show only works if the schema was previously tracked in THIS repo.
#    For EE plugins (external ZIPs), use mvn dependency:copy instead — see below.
git show 4.11.0:path/to/schema-form.json > /tmp/old-schema.json

# 3. Run the check:
node scripts/schema-compatibility/check-schema-compatibility.mjs \
  --old /tmp/old-schema.json \
  --new /tmp/new-schema.json \
  --plugin my-plugin-name
```

If the plugin is distributed as a Maven artifact (ZIP), resolve both old and new versions with:

```bash
mvn dependency:copy \
  -Dartifact=<groupId>:<artifactId>:<version>:zip \
  -DoutputDirectory=/tmp/plugins
unzip -p /tmp/plugins/<artifactId>-<version>.zip 'schemas/schema-form.json' > /tmp/schema.json
```

> **Schema path inside ZIPs**: try `schemas/schema-form.json` first; some plugins place it at
> `schema-form.json` in the ZIP root. The `unzip -p` call will produce empty output (not an error)
> if the path does not exist — always check that the output file is non-empty.

### Gravitee AM — EE (Enterprise) plugin schemas

The automated `schema-compat-check.sh` only covers schemas tracked in this git repository. EE
plugins are distributed as closed-source ZIPs and are **not checked automatically**. Use the
dedicated script instead.

> **Prerequisite:** run `mvn install -P full-bundle` first. The EE script locates new plugin
> ZIPs from the local build output; it will error with a clear message if they are absent.

```bash
# Run OSS check first — only proceed to EE check if OSS passes:
bash .circleci/scripts/schema-compat-check.sh --base 4.7.0 && \
  bash .circleci/scripts/ee-schema-compat-check.sh --base 4.7.0
```

The EE script auto-discovers plugins, skips unchanged versions, downloads old ZIPs from Maven,
and applies the same exemption logic as the OSS script.

`--use-head false` on the EE script reads version properties from the working-tree `pom.xml`
instead of HEAD, but new plugin ZIPs are always sourced from `target/` regardless of this flag.

**How EE plugin coordinates are structured in this repo** (relevant if checking manually):

| What | Where |
|---|---|
| Version properties (e.g. `gravitee-am-factor-call.version`) | Root `pom.xml` `<properties>` |
| GroupId + artifactId + version property reference | Distribution `pom.xml` `<dependencies>` and `<artifactItems>` |
| GroupId prefix for all EE plugins | `com.graviteesource.am.*` |
| Built ZIPs for the current version | `gravitee-am-gateway/.../target/distribution/plugins/` and `gravitee-am-management-api/.../target/distribution/plugins/` |

Because version properties and groupIds live in different files, you must cross-reference both to
form a complete `groupId:artifactId:version` coordinate for `mvn dependency:copy`.

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
