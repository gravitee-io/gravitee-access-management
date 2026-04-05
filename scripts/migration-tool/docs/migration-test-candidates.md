# Migration Test Candidates — AM-5325

Scenarios where persistent data from version N could **fail** when accessed by version N+1 (upgrade) or N after downgrade. Each identifies the sensitive code path, whether seed data is needed, and which existing Jest test covers the area.

## Current default pipeline

The migration tool runs `ci:migration` (`specs/migration/`) at every verify stage. Existing management/gateway tests are self-contained (create+teardown) and won't catch migration issues. Migration tests must use **persistent data** — either auto-created by upgraders or explicitly seeded.

---

## Scenario 1: Unknown enum in roles after upgrade (AM-6174)

**Status**: DONE — `specs/migration/backward-compat-enums.jest.spec.ts`

**What happens**: MAPI 4.10+ upgraders (`DefaultRoleUpgrader`, `OrganizationRolesUpgrader`) create system roles with `PROTECTED_RESOURCE` reference type. On downgrade to 4.9, `ReferenceType.valueOf("PROTECTED_RESOURCE")` throws `IllegalArgumentException`.

**Seed needed**: No — upgraders create the data at startup automatically.

**Existing test reference**: `specs/management/roles.jest.spec.ts` covers role CRUD. Our migration test verifies the roles API returns 200 despite unknown enum values.

**Confidence this fails without the fix**: 100% confirmed.

---

## Scenario 2: Certificate settings property rejected on older MAPI

**What happens**: 4.11 adds `domains.certificate_settings` column and `certificateSettings` property on the domain API. If a domain is patched with `certificateSettings` on 4.11, then the MAPI is downgraded to 4.10, the 4.10 API rejects `certificateSettings` as unknown property (`400: Property [certificateSettings] is not recognized`).

**Seed needed**: Yes — need a domain with `certificateSettings` configured. The seed should:
- Create a domain on version N+1
- Set a fallback certificate via `certificateSettings`
- After downgrade, verify the domain is still loadable (GET returns 200, `certificateSettings` may be absent but no crash)

**Existing test reference**: `specs/management/certificates/certificate-settings.jest.spec.ts` — tests fallback certificate assignment via `patchDomain` and dedicated endpoint.

**Confidence this fails**: 100% confirmed — seen in CI run on 4.10 baseline.

**Migration test would**: Seed a domain with certificate settings on 4.11+, verify domain GET still works after downgrade to 4.10 (even if `certificateSettings` is stripped).

---

## Scenario 3: Permission JSON with unknown keys after upgrade

**What happens**: `ProtectedResourcePermissionMongoUpgrader` adds `permissionAcls.PROTECTED_RESOURCE` to existing system roles. `DataPlanePermissionMongoUpgrader` adds `permissionAcls.DATA_PLANE`. These are stored as JSON keys in the role document. If older code iterates permission keys and calls `Permission.valueOf()`, it would crash.

**Seed needed**: No — upgraders modify existing role documents at startup.

**Existing test reference**: `specs/management/roles.jest.spec.ts` — role listing/querying exercises the permission deserialization path.

**Confidence this fails without AM-6174 fix**: HIGH — the fix (`EnumParsingUtils.safeValueOf`) filters unknown permission keys at the repository layer. Without the fix, any code reading `permissionAcls` crashes. The fix is in 4.10+, backported to 4.9.8/4.8.23/4.7.30.

**Migration test would**: Already covered by Scenario 1 (same fix, same code path). No separate test needed.

---

## Scenario 4: Reporter referenceType modified by upgrader

**What happens**: `ReporterReferenceMongoUpgrader` modifies existing reporter documents — sets `referenceType=DOMAIN` and `referenceId` on reporters that previously only had a `domain` field. After downgrade, old code that reads the `referenceType` field may encounter a value it doesn't expect.

**Seed needed**: No — upgrader modifies existing reporters. But reporters only exist if they were created before the upgrade (default reporter is created per domain).

**Existing test reference**: `specs/management/reporters/domain-reporter.jest.spec.ts` — reporter CRUD and configuration.

**Confidence this fails**: MEDIUM (40%) — old code reading `domain` field still works. The `referenceType` field is additive. Would only fail if old code explicitly validates `referenceType` values.

**Migration test would**: Seed a domain with a reporter on version N, upgrade to N+1 (upgrader modifies reporter), downgrade to N, verify reporter is still listed and functional.

---

## Scenario 5: Token exchange settings rejected on older MAPI

**What happens**: 4.11 adds `domains.token_exchange_settings` CLOB column and `tokenExchangeSettings` property on `PatchDomain.java:73`. If a domain is patched with `tokenExchangeSettings` on 4.11, then the MAPI is downgraded to 4.10, the 4.10 API rejects `tokenExchangeSettings` as an unknown property. This is the **same failure mechanism as Scenario 2** (certificate settings).

**Root cause (confirmed by Stage 3 deep scan)**: The production MAPI ObjectMapper (`ObjectMapperResolver.java:60`) uses default Jackson settings where `FAIL_ON_UNKNOWN_PROPERTIES = true`. On 4.10, `PatchDomain.java` has no `tokenExchangeSettings` field → Jackson throws `UnrecognizedPropertyException` → **400 Bad Request**. Additionally, the `token_exchange_settings` CLOB column does not exist on 4.10 JDBC (`4.11.0-add-token-exchange-settings-column.yml`), and the Gateway does not recognize `urn:ietf:params:oauth:grant-type:token-exchange` grant type on 4.10.

**Seed needed**: Yes — need a domain with `tokenExchangeSettings` configured. The seed should:
- Create a domain on version N+1 (4.11+)
- Enable token exchange via `tokenExchangeSettings: {enabled: true, allowedSubjectTokenTypes: [...], allowedRequestedTokenTypes: [...]}`
- After downgrade, verify the domain is still loadable (GET returns 200, `tokenExchangeSettings` may be absent but no crash)

**Existing test reference**: `specs/management/domain/token-exchange-validation.jest.spec.ts` — token exchange settings validation on domains. `specs/gateway/token-exchange/token-exchange.jest.spec.ts` — 7 fixtures covering token exchange scenarios.

**Confidence this fails**: 100% confirmed — same Jackson `FAIL_ON_UNKNOWN_PROPERTIES` mechanism as Scenario 2 (`PatchDomain.java:73`, `ObjectMapperResolver.java:60`). Traced in Stage 3 deep scan (S2).

**Migration test would**: Seed a domain with token exchange settings on 4.11+, verify domain GET still works after downgrade to 4.10 (even if `tokenExchangeSettings` is stripped).

---

## Scenario 6: Refresh token signed on old version used on new version

**What happens**: If token signing key format, algorithm, or claims change between versions, a refresh token issued on version N may not be valid on N+1. The user would get an error when trying to refresh.

**Seed needed**: Yes — need to issue a refresh token on version N and persist the token value. After upgrade to N+1, attempt to use the refresh token.

**Existing test reference**: `specs/gateway/refresh-token.jest.spec.ts` — issues refresh tokens and tests refresh flow.

**Confidence this fails**: LOW (10%) for 4.10-4.12 — no evidence of signing key or token format changes. Signing keys are stored in DB and persist across upgrades. Higher risk for major version changes.

**Migration test would**: Need cross-stage state — issue token at verify-baseline, store it, attempt refresh at verify-all. This requires test infrastructure changes (shared state file between Jest runs). HIGH effort.

---

---

## Scenario 7: Application client secrets format migration

**What happens**: `ApplicationClientSecretsUpgrader` (4.10) migrates client secrets to new `secretSettings` format. On downgrade, old code may not understand the new format — client authentication could fail.

**Seed needed**: Yes — need an application with client secret created on N. After upgrade (upgrader modifies secret storage), verify client_credentials grant still works. After downgrade, verify again.

**Existing test reference**: `specs/gateway/oauth2/oauth2-grant-client-credential.jest.spec.ts` — client credentials flow. `specs/management/client-secrets.jest.spec.ts` — secret CRUD.

**Confidence this fails**: MEDIUM (50%) — depends on whether old code can still read the migrated secret format. If the upgrader adds `secretSettings` alongside old fields (backward compatible), it won't fail. If it replaces the old format, it will.

---

## Scenario 8: Application factor settings migration

**What happens**: `ApplicationFactorSettingsUpgrader` (4.10) migrates MFA factor config to new structure. On downgrade, old code reading factor settings from the application may not parse the new structure.

**Seed needed**: Yes — need an application with MFA factor configured on N. After upgrade, verify MFA flow. After downgrade, verify factor config is still readable.

**Existing test reference**: `specs/gateway/mfa/mfa-factor.spec.ts` — MFA factor enrollment and challenge.

**Confidence this fails**: MEDIUM (40%) — depends on whether the migration is additive or destructive.

---

## Scenario 9: Domain dataplane assignment

**What happens**: `DomainDataPlaneUpgrader` (4.10) assigns `dataPlaneId` to existing domains. `IdentityProviderDataPlaneUpgrader` assigns `dataPlaneId` to IdPs. On downgrade, old code that doesn't know `dataPlaneId` ignores it — but gateway sync may be affected if the old gateway doesn't filter by dataplane.

**Seed needed**: No — upgrader modifies existing domains/IdPs.

**Existing test reference**: `specs/management/domains.jest.spec.ts` — domain CRUD. `specs/management/identity-provider/identity-provider.jest.spec.ts` — IdP CRUD.

**Confidence this fails**: LOW (20%) — `dataPlaneId` is an additive field. Old code ignores unknown fields during deserialization.

---

## Scenario 10: Password policy migration

**What happens**: `DomainPasswordPoliciesUpgrader` converts legacy password settings to new `PasswordPolicy` objects. On downgrade, old code expecting the legacy format may not find it.

**Seed needed**: Yes — need a domain with password policy configured on N. After upgrade (policy migrated), downgrade, verify password validation still works.

**Existing test reference**: `specs/gateway/password-policy/password-policy.jest.spec.ts` — password policy enforcement. `specs/management/password-policy-management.jest.spec.ts` — policy CRUD.

**Confidence this fails**: MEDIUM (40%) — if the upgrader replaces legacy settings with new PasswordPolicy objects, old code looking for the legacy format won't find it.

---

## Scenario 11: IdP config JSON modified by upgrader

**What happens**: `NonBCryptIterationsRoundsUpgrader` modifies IdP configuration JSON — removes `iterations`/`rounds` fields for non-BCrypt algorithms. On downgrade, old code that expects these fields may use different defaults, changing password hashing behavior silently (no crash, but wrong hashing).

**Seed needed**: Yes — need an IdP with non-BCrypt config (e.g. SHA-256 with iterations). After upgrade (fields removed), downgrade, verify user authentication still works.

**Existing test reference**: `specs/management/identity-provider/identity-provider.jest.spec.ts` — IdP CRUD. `specs/gateway/login-flow/login-flow.jest.spec.ts` — login with IdP.

**Confidence this fails**: MEDIUM (40%) — won't crash, but could silently change hashing behavior. Users created on N+1 may not authenticate on N if hashing differs.

---

## Summary: what to build next

| # | Scenario | Seed? | Effort | Confidence | Recommendation |
|---|----------|-------|--------|-----------|----------------|
| 1 | Enum filtering | No | Done | 100% | **DONE** |
| 2 | Certificate settings | Yes | LOW | 100% | **BUILD NEXT** — confirmed breakage |
| 3 | Permission JSON keys | No | N/A | HIGH | Covered by #1 |
| 4 | Reporter referenceType | No (upgrader) | LOW | 40% | Worth testing |
| 5 | Token exchange settings | Yes | LOW | 100% | **BUILD NEXT** — confirmed breakage (same as #2) |
| 6 | Refresh token cross-version | Yes + shared state | HIGH | 10% | Defer — needs infrastructure |
| 7 | Client secrets format | Yes | MEDIUM | 50% | **Worth testing** — client auth is critical path |
| 8 | Factor settings migration | Yes | MEDIUM | 40% | Worth testing if MFA is in scope |
| 9 | Domain dataplane assignment | No | LOW | 20% | Low priority — additive field |
| 10 | Password policy migration | Yes | MEDIUM | 40% | Worth testing — silent behavior change risk |
| 11 | IdP config modification | Yes | MEDIUM | 40% | Worth testing — silent hashing change risk |
