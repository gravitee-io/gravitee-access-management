# Stage 3 — Scenario Validation

Mandatory validation for each HIGH finding from Stage 2 before building migration tests.

---

## S1 — Certificate settings property on downgrade

**SCENARIO:** Domain patched with `{certificateSettings: {fallbackCertificate: certId}}` on 4.11 → downgraded to 4.10 MAPI.

### Validation trace

1. **`PatchDomain.java:74`** — `private Optional<CertificateSettings> certificateSettings;` — field exists in current (4.11+) code. On a 4.10 build, **this field would not exist** in `PatchDomain.java`.

2. **`ObjectMapperResolver.java:60`** — production MAPI ObjectMapper is `new ObjectMapper()` with **default** Jackson settings. `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES` is **not disabled** (only the test `JerseySpringTest.java:121` disables it). Default Jackson behavior: **unknown properties throw `UnrecognizedPropertyException`**.

3. **Liquibase `4.11.0-update-domains-certificate-settings.yml`** — adds `certificate_settings` CLOB column to `domains` table. On 4.10 JDBC, this column **does not exist**. Even if the MAPI somehow accepted the JSON, the repository would fail to persist the field.

4. **Mongo path:** Domain document in MongoDB stores `certificateSettings` as a document field. On 4.10, the `Domain.java` model doesn't have this field → Mongo driver would ignore it during read (Mongo is schema-less), but MAPI would never set it because `PatchDomain` doesn't have the field.

### Result

**VALIDATED: yes — the failure path is real.**

Reasoning chain:
- Test/client sends `PATCH /domains/{id}` with `{certificateSettings: {...}}` → Jackson deserializes into `PatchDomain`
- `PatchDomain.java` on 4.10 has NO `certificateSettings` field → Jackson `FAIL_ON_UNKNOWN_PROPERTIES` = true (default, verified `ObjectMapperResolver.java:60`) → `UnrecognizedPropertyException` → **400 Bad Request**
- Even if the client sends data that was previously stored (e.g., GET domain returns `certificateSettings` from 4.11 DB), a 4.10 MAPI re-patching the same domain would fail
- JDBC: `certificate_settings` column absent on 4.10 (`4.11.0-update-domains-certificate-settings.yml`) → repository write would also fail
- Mongo: field ignored on read (schema-less) but MAPI can't set it (no field in model)

**Revised confidence: HIGH** — fully traced, failure mode confirmed as 400 rejection.
**Evidence type:** fully traced — `PatchDomain.java:74`, `ObjectMapperResolver.java:60`, `4.11.0-update-domains-certificate-settings.yml`

---

## S2 — Token exchange settings on downgrade

**SCENARIO:** Domain patched with `{tokenExchangeSettings: {enabled: true, ...}}` on 4.11 → downgraded to 4.10 MAPI.

### Validation trace

1. **`PatchDomain.java:73`** — `private Optional<TokenExchangeSettings> tokenExchangeSettings;` — exists in 4.11+. Absent on 4.10.

2. **`ObjectMapperResolver.java:60`** — same as S1. Default Jackson: unknown properties → 400.

3. **Liquibase `4.11.0-add-token-exchange-settings-column.yml`** — adds `token_exchange_settings` CLOB column to `domains` table. Absent on 4.10 JDBC.

4. **Gateway:** `urn:ietf:params:oauth:grant-type:token-exchange` grant type handler — on 4.10 Gateway, this grant type is not registered → token exchange requests return `unsupported_grant_type` error.

5. **Mongo path:** Same as S1 — `tokenExchangeSettings` field ignored on Mongo read, but MAPI can't set or validate it on 4.10.

### Result

**VALIDATED: yes — the failure path is real.**

Reasoning chain:
- Client sends `PATCH /domains/{id}` with `{tokenExchangeSettings: {...}}` → 4.10 `PatchDomain` has no such field → Jackson throws `UnrecognizedPropertyException` → **400 Bad Request**
- JDBC: `token_exchange_settings` column absent → repository write fails
- Gateway: `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` → `unsupported_grant_type` on 4.10 Gateway
- Domain with `tokenExchangeSettings` stored in 4.11 DB → on 4.10 GET, JDBC reader loads column that doesn't exist → likely SQL error. Mongo: field ignored.

**Revised confidence: HIGH** — fully traced, failure mode confirmed as 400 rejection (MAPI) + unsupported_grant_type (Gateway).
**Evidence type:** fully traced — `PatchDomain.java:73`, `ObjectMapperResolver.java:60`, `4.11.0-add-token-exchange-settings-column.yml`

---

## S3 — Protected resources / authorization engines tables absent on 4.9

**SCENARIO:** Protected Resources and Authorization Engines created on 4.10+ → downgrade to 4.9.

### Validation trace

1. **Liquibase `4.10.0-protected-resources-table.yml`** — creates `protected_resources` table with columns (`id`, `name`, `type`, `domain_id`, `client_id`, `client_secret`, etc.). On 4.9, table does not exist.

2. **Liquibase `4.10.0-add-authorization-engines-table.yml`** — creates `authorization_engines` table. On 4.9, table does not exist.

3. **JDBC path:** Any `SELECT`, `INSERT`, `UPDATE` against `protected_resources` or `authorization_engines` on 4.9 → SQL error (`table does not exist`).

4. **Mongo path:** Collections are created implicitly on first write. On 4.9, the Java code for these repositories doesn't exist → no reads or writes attempted. The collections would persist in Mongo but be orphaned (never accessed by 4.9 code).

5. **Liquibase `4.10.0-protected-resource-permissions.yml`** (JDBC) — updates `roles.permission_acls` for owner roles to include `PROTECTED_RESOURCE` permissions. This is the JDBC equivalent of `ProtectedResourcePermissionMongoUpgrader`.

### Result

**VALIDATED: yes — the failure path is real, but only on downgrade below 4.10.**

Reasoning chain:
- 4.10 Liquibase creates `protected_resources` + `authorization_engines` tables
- 4.9 has no code to access these tables → SQL error if accidentally queried
- Within 4.10-4.12 range: tables exist at all versions → **no regression**
- This is a **version floor** (AM ≥ 4.10), not a migration risk between 4.10 and 4.11/4.12

**Revised confidence: HIGH for 4.9 downgrade, NOT APPLICABLE within 4.10-4.12.**
**Evidence type:** fully traced — Liquibase changelogs confirmed for both JDBC and Mongo paths.

---

## S4 — PROTECTED_RESOURCE enum unknown on downgrade

**SCENARIO:** System roles contain `referenceType=PROTECTED_RESOURCE` after 4.10+ upgraders run → downgrade to older version.

### Validation trace

1. **`ReferenceType.java:28`** — `PROTECTED_RESOURCE` enum value added in 4.10.

2. **`ProtectedResourcePermissionMongoUpgrader.java:44-57`** (Mongo) — adds `permissionAcls.PROTECTED_RESOURCE` to owner/primary-owner system roles. **`4.10.0-protected-resource-permissions.yml`** (JDBC) — same semantics.

3. **`MongoRoleRepository.java:214`** — `ReferenceType referenceType = EnumParsingUtils.safeValueOf(ReferenceType.class, roleMongo.getReferenceType(), ...)` — **guard present**. Unknown enum values return `null` and log a warning. Role is filtered out of results.

4. **`JdbcRoleRepository.java:116`** — same `safeValueOf` guard.

5. **`EnumParsingUtils.java:44-53`** — catches `IllegalArgumentException` from `Enum.valueOf()`, returns `null`, logs "Unknown {EnumClass} value '{value}' for {entityId}.{fieldName}".

6. **Existing migration test:** `specs/migration/backward-compat-enums.jest.spec.ts` — already validates that roles endpoint returns 200 despite unknown enum values.

### Result

**VALIDATED: no — the failure is PREVENTED by AM-6174 `safeValueOf` guard.**

Reasoning chain:
- 4.10+ upgrader adds `PROTECTED_RESOURCE` referenceType to system roles
- On downgrade to 4.9 (without AM-6174 backport): `ReferenceType.valueOf("PROTECTED_RESOURCE")` → `IllegalArgumentException` → 500 crash
- On 4.10+ (with AM-6174): `safeValueOf` catches exception → returns null → role filtered → API returns 200
- AM-6174 backported to 4.9.8/4.8.23/4.7.30 → crash prevented on patched 4.9 too
- Migration test already exists and passes

**Revised confidence: MITIGATED — failure prevented by code fix. Migration test DONE.**
**Evidence type:** fully traced — upgrader, repository, guard, and existing migration test all confirmed.

---

## S5 — /_node/domains endpoint absent on 4.10 Gateway

**SCENARIO:** `GET /_node/domains` called against 4.10 Gateway → 404.

### Validation trace

1. **`DomainReadinessEndpoint.java`** in `gravitee-am-gateway-services-sync/src/main/java/.../api/` — class implements `ManagementEndpoint` and `Probe`. Registers at `/_node/domains` path.

2. **Gateway services module:** This class is in the `gravitee-am-gateway-services-sync` module which is loaded at Gateway startup. On 4.10, this class **does not exist** → endpoint not registered → 404.

3. **Empirical confirmation:** During Kind testing on 4.10.0, `GET /_node/domains` returned 404 (confirmed in earlier session).

4. **Nature of risk:** This is a **test code compatibility** issue. The test calls a Gateway API that doesn't exist on 4.10. It is NOT a data migration issue — no persisted data is affected.

### Result

**VALIDATED: yes — the endpoint is absent on 4.10 Gateway.**

Reasoning chain:
- `DomainReadinessEndpoint.java` added in 4.11 development branch
- 4.10 Gateway has no handler for `/_node/domains` → 404
- Confirmed empirically on Kind 4.10.0
- This affects test code execution, not persisted data integrity

**Revised confidence: HIGH for test code compatibility. NOT APPLICABLE for data migration.**
**Evidence type:** fully traced + empirically confirmed.

---

## Stage 3 Summary

| Scenario | Validated? | Failure Mode | Revised Confidence | Action |
|----------|-----------|-------------|-------------------|--------|
| **S1** Certificate settings | **YES** — failure path real | 400 Bad Request (Jackson rejects unknown `certificateSettings` property on 4.10 PatchDomain) + JDBC column absent | **HIGH** | **Build T1 migration test** |
| **S2** Token exchange settings | **YES** — failure path real | 400 Bad Request (Jackson rejects unknown `tokenExchangeSettings` on 4.10) + JDBC column absent + Gateway rejects grant type | **HIGH** | **Build T2 migration test** |
| **S3** Protected resources tables | **YES** — but version floor only | SQL error (tables missing on 4.9). No regression within 4.10-4.12. | **HIGH (4.9 downgrade) / N/A (4.10-4.12)** | No migration test needed for 4.10-4.12 range |
| **S4** PROTECTED_RESOURCE enum | **NO** — prevented by AM-6174 | `safeValueOf` guard returns null for unknown enum. Test already DONE. | **MITIGATED** | No action — test exists |
| **S5** /_node/domains endpoint | **YES** — 404 on 4.10 Gateway | Endpoint class doesn't exist on 4.10. Test code compat, not data. | **HIGH (test compat) / N/A (data migration)** | No migration test — document as version requirement |

### Key finding: Jackson FAIL_ON_UNKNOWN_PROPERTIES

The **production** MAPI ObjectMapper (`ObjectMapperResolver.java:60`) uses **default** Jackson settings where `FAIL_ON_UNKNOWN_PROPERTIES = true`. This means:
- **Any property added in 4.11** (like `certificateSettings`, `tokenExchangeSettings`) sent to a **4.10 MAPI** will be **rejected with 400**, not silently ignored.
- This confirms that S1 and S2 are **real migration risks** — not theoretical.
- The 400 rejection happens at the **deserialization layer** before any business logic or DB write.

### Ready to build

**T1** (certificate settings migration test) and **T2** (token exchange settings migration test) are now validated and ready for implementation in `specs/migration/`.
