# Checking Schemas from External Plugin Repos

If the repo you're working in depends on plugins from other repos (e.g. EE plugins distributed as
ZIPs), you can compare their schemas against any baseline manually.

## Manual single-schema check

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

## Resolving Maven artifacts

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

## Gravitee AM — EE (Enterprise) plugin schemas

- The automated `schema-compat-check.sh` covers schemas tracked in this git repository.
- The automated `ee-schema-compat-check.sh` covers schemas in EE plugins that are distributed as closed-source ZIPs.

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

### How EE plugin coordinates are structured in this repo

| What | Where |
|---|---|
| Version properties (e.g. `gravitee-am-factor-call.version`) | Root `pom.xml` `<properties>` |
| GroupId + artifactId + version property reference | Distribution `pom.xml` `<dependencies>` and `<artifactItems>` |
| GroupId prefix for all EE plugins | `com.graviteesource.am.*` |
| Built ZIPs for the current version | `gravitee-am-gateway/.../target/distribution/plugins/` and `gravitee-am-management-api/.../target/distribution/plugins/` |

Because version properties and groupIds live in different files, you must cross-reference both to
form a complete `groupId:artifactId:version` coordinate for `mvn dependency:copy`.
