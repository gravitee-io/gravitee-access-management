# Migration Test Candidates — AM-5325

Tests that could **fail** when run in a migration flow, based on concrete changes between AM 4.10 → 4.11 → 4.12.

## Current default pipeline

The migration tool's verify stages default to `ci:migration` (`specs/migration/` only). The full management and gateway suites are **not** run by default — use `--test-filter` to target specific specs when needed.

---

## Concrete breaking changes between versions

| # | Area | Version | Change | Breaks on downgrade? | Breaks on upgrade? |
|---|------|---------|--------|---------------------|-------------------|
| 1 | Enum | 4.10 | `PROTECTED_RESOURCE` added to ReferenceType | **Yes** — `IllegalArgumentException` parsing roles | No |
| 2 | Enum | 4.10 | `PROTECTED_RESOURCE` permissions added to system roles | **Yes** — permission JSON contains unknown keys | No |
| 3 | Column | 4.11 | `domains.certificate_settings` CLOB | **Yes** — API rejects unknown `certificateSettings` property on PATCH | No (nullable) |
| 4 | Column | 4.11 | `domains.token_exchange_settings` CLOB | No (nullable, ignored) | No |
| 5 | Feature | 4.11 | Token exchange endpoint (RFC 8693) | N/A (endpoint didn't exist) | No |
| 6 | Feature | 4.11 | `/_node/domains` monitoring endpoint | N/A (endpoint didn't exist) | No |
| 7 | Enum | 4.11 | `TokenExchangeScopeHandling` (DOWNSCOPING/PERMISSIVE) | Possible — if stored in domain settings | No (defaults provided) |
| 8 | Table | 4.11 | `cp_action_lease` for distributed coordination | No (old code doesn't query it) | No |
| 9 | Schema | 4.10 | `protected_resources` + related tables | No (old code doesn't query them) | No |
| 10 | Index | 4.9→4.10 | Redundant indexes swept (webauthn, uma, scope-approvals) | Slower queries on downgrade | No |
| 11 | Sync | 4.11 | New entity types in MAPI→GW sync | Old GW ignores unknown types (assumption — not verified in code) | No |

**Key insight**: Most changes are additive (new tables, nullable columns) and don't break on upgrade. The real risk is **downgrade** — older code encountering data created by newer versions.

### Upgraders that modify EXISTING data (irreversible on downgrade)

These upgraders change data in existing collections/tables. After downgrade, the modifications remain:

| Upgrader | What it modifies | Downgrade impact |
|----------|-----------------|-----------------|
| `ProtectedResourcePermissionMongoUpgrader` | Adds `permissionAcls.PROTECTED_RESOURCE` to existing system roles | Old code may crash on unknown Permission key |
| `DataPlanePermissionMongoUpgrader` | Adds `permissionAcls.DATA_PLANE` to org/env owner roles | Same — unknown Permission key |
| `ReporterReferenceMongoUpgrader` | Sets `referenceType`/`referenceId` on reporters (previously only `domain`) | Old code reading `referenceType` may not handle new values |
| `DefaultRoleUpgrader` (v4_11_0_b) | Creates/updates system roles with PROTECTED_RESOURCE assignableType | Old code crashes on unknown ReferenceType — **AM-6174 fix** |
| `OrganizationRolesUpgrader` (v4_11_0_b) | Creates PROTECTED_RESOURCE_OWNER/USER org roles | Same as above |
| JDBC Liquibase `4.10.0-protected-resource-permissions.yml` | Adds PROTECTED_RESOURCE to role permission JSON via SQL | Same as Mongo upgrader, for JDBC repos |

---

## Migration test candidates

### Already covered

| Test | Area | What it validates |
|------|------|------------------|
| `specs/migration/backward-compat-enums.jest.spec.ts` | #1, #2 (Enums) | Org roles endpoint returns 200 despite PROTECTED_RESOURCE data |

### Candidate 1: Certificate settings (breaking change #3)

**Reference**: `specs/management/certificates/certificate-settings.jest.spec.ts`

**Why it fails on migration**: The `certificateSettings` domain property was added in 4.11. When the test runs against a 4.10 MAPI at verify-baseline, it sends `PATCH /domains/{id}` with `certificateSettings` — the 4.10 MAPI returns `400: Property [certificateSettings] is not recognized`. We saw this fail in CI run `ab7072f0`.

**Migration test approach**: Write a new test in `specs/migration/` that:
- At verify-baseline (version N): verify domain PATCH without `certificateSettings` works
- At verify-mapi (version N+1): verify `certificateSettings` is available
- At verify-after-downgrade: verify domain still loads without crashing on the stored `certificateSettings` column

**Infra needed**: MAPI only, no gateway

### Candidate 2: Token exchange scope handling (breaking change #7)

**Reference**: `specs/management/domain/token-exchange-validation.jest.spec.ts`

**Why it could fail on migration**: `TokenExchangeOAuthSettings.scopeHandling` stores `PERMISSIVE` or `DOWNSCOPING` in `domains.token_exchange_settings` JSON. If a domain is configured with `PERMISSIVE` on 4.12 and you downgrade to 4.11-alpha where the enum didn't exist yet, deserialization could fail.

**Migration test approach**: Verify domain with token exchange settings loads after downgrade.

**Infra needed**: MAPI only

### Candidate 3: Protected resources across versions (breaking changes #1, #2, #9)

**Reference**: `specs/management/protected-resources.jest.spec.ts`

**Why it could fail on migration**: Protected resources are a 4.10+ feature. Creating one triggers PROTECTED_RESOURCE roles/permissions via upgraders. The test itself needs `/_node/domains` (4.11+). After downgrade to a version without the feature, the API endpoint would return 404.

**Migration test approach**: Not a downgrade test — rather validates the feature works after upgrade. The downgrade scenario is already covered by Candidate 1 (enum filtering).

**Infra needed**: MAPI + gateway + `/_node/domains` (4.11+ from-tag)

### Candidate 4: Refresh token format (potential breaking change)

**Reference**: `specs/gateway/refresh-token.jest.spec.ts`

**Why it could fail**: If the token signing key or format changes between versions, a refresh token issued on N may not be valid on N+1. This is the classic "sessions may break" scenario from AM-5325.

**Migration test approach**: Would need to split the test — issue refresh token at one stage, attempt refresh at the next. Current test is self-contained (creates and uses token in one run). **Needs adaptation** — not a simple --test-filter candidate.

**Infra needed**: MAPI + gateway + `/_node/domains`

### Candidate 5: Client credentials grant (operational continuity)

**Reference**: `specs/gateway/oauth2/oauth2-grant-client-credential.jest.spec.ts`

**Why it could fail**: If the client secret hashing algorithm or application settings format changes between versions, client_credentials grant could fail after upgrade.

**Migration test approach**: Self-contained — creates app, issues token, verifies in one run. Safe to run at every verify stage via `--test-filter`. If it passes at verify-baseline and verify-all, the grant flow works across versions.

**Infra needed**: MAPI + gateway + `/_node/domains`

---

## Confidence assessment

| Candidate | Confidence it fails | Evidence |
|-----------|-------------------|----------|
| Enum filtering (roles) | **100%** confirmed | CI run, AM-6174 Jira, `IllegalArgumentException` on 4.9. Fixed by `EnumParsingUtils.safeValueOf()` in all 4 repos |
| Certificate settings | **100%** confirmed | CI run `ab7072f0` — `400: Property [certificateSettings] is not recognized` on 4.10 |
| Permission JSON keys | **10%** low — **already fixed** | Upgraders add PROTECTED_RESOURCE + DATA_PLANE to existing role `permissionAcls`. AM-6174 fix covers all 4 repository classes (Mongo/JDBC Role + Membership). Service layer uses typed enums, not DB strings — doesn't parse. |
| Reporter referenceType | **40%** medium | `ReporterReferenceMongoUpgrader` modifies existing reporter docs. Old code reads `domain` field (still works), but code reading `referenceType` may not handle new values |
| Token exchange scope enum | **30%** theoretical | Stored in domain JSON. Jackson typically ignores unknown fields, but strict deserialization would fail |
| Refresh token format | **10%** unlikely | No evidence of format changes 4.10-4.12. Signing keys stored in DB persist across versions |
| Client credentials grant | **10%** unlikely | Self-contained test, format unchanged |

## Priority order for implementation

| Priority | Candidate | Effort | Confidence | Value |
|----------|-----------|--------|-----------|-------|
| **P1** | Certificate settings | Low — new test in specs/migration/ | 100% confirmed | HIGH |
| **P2** | Permission JSON keys | Low — already fixed at repo layer | 10% | LOW — AM-6174 covers all repos |
| **P2** | Reporter referenceType | Low — new test in specs/migration/ | 40% | MEDIUM |
| **P3** | Token exchange scope enum | Low — new test | 30% | LOW |
| **P3** | Refresh token lifecycle | High — needs test split | 10% | LOW for 4.10-4.12 range |

---

## AM-5325 area coverage assessment

| Area | Covered? | Notes |
|------|----------|-------|
| Schema / migrations | Implicit | Upgraders run at startup; Liquibase handles expand/contract. No migration test needed — verified by successful MAPI boot. |
| Enums / reference types | **Yes** | `backward-compat-enums.jest.spec.ts` covers ReferenceType filtering in roles |
| Referential / JSON blobs | **Partial** | certificate-settings is a P1 candidate (confirmed breakage). token-exchange-settings is P3 (theoretical). |
| Crypto / tokens / sessions | **No** | Refresh token lifecycle needs cross-stage test adaptation (P3). No format changes identified in 4.10-4.12. |
| Search / indexes | **Low risk** | Index changes are additive; old queries still work, just slower. No test needed. |
| Multi-component skew | **Not tested by default** | Default pipeline runs `specs/migration/` only. Gateway tests can be run via `--test-filter specs/gateway/...` but are not automatic. |
| Volume / pagination | **Low risk** | Pagination unchanged between versions. No test needed. |
| Startup vs runtime | **Implicit** | Verified by successful MAPI/gateway startup. `backward-compat-enums` test implicitly validates startup (roles load after upgraders ran). |
| Policies | **Not tested** | Flow execution unchanged between these versions. No test needed. |

---

## Dependencies for candidates

| Candidate | MAPI | Gateway | /_node/domains | SMTP | OpenFGA | Min from-tag |
|-----------|------|---------|---------------|------|---------|-------------|
| Certificate settings | Yes | No | No | No | No | 4.10 |
| Token exchange scope | Yes | No | No | No | No | 4.11 |
| Client credentials | Yes | Yes | Yes | No | No | 4.11 |
| Protected resources | Yes | Yes | Yes | No | No | 4.11 |
| Refresh token | Yes | Yes | Yes | No | No | 4.11 |
