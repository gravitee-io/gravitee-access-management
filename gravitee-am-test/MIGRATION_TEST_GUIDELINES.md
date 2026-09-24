# Migration Test Guidelines

How to write a **migration test**: seed data with one AM version, upgrade (and optionally
downgrade) the Management API and the gateways, and verify the data and behaviour survived.

Read this before adding anything under `migration-seeding/` or `specs/migration/`. General Jest
conventions still come from [GUIDELINES.md](GUIDELINES.md); the tool that runs the pipeline is
documented in [scripts/migration-tool/README.md](../scripts/migration-tool/README.md).

Worked example used throughout: the trusted-issuer token exchange test
(`migration-seeding/token-exchange-seed.ts`, `specs/migration/token-exchange.jest.spec.ts`).

---

## 1. How a migration run works

The migration tool (`scripts/migration-test.mjs`) drives a K8s stack through these stages:

| # | Stage | Management API | Gateways | What runs from this directory |
|---|-------|----------------|----------|-------------------------------|
| 1 | `deploy-from` | from | from | – |
| 2 | `seed-alpha` | from | from | `versions/<from major.minor>/seed.ts`, label `alpha` |
| 3 | `verify-alpha` | from | from | `specs/migration`, label `alpha` |
| 4 | `upgrade-mapi` | **to** | from | – |
| 5 | `seed-beta` | to | from | `versions/<to major.minor>/seed.ts`, label `beta` |
| 6 | `verify-alpha`, `verify-beta` | to | from | `specs/migration` per label |
| 7 | `upgrade-gw` | to | **to** | – |
| 8 | `verify-alpha`, `verify-beta` | to | to | `specs/migration` per label |
| 9 | `downgrade-gw` + verify both *(`--with-downgrade`)* | to | **from** | |
| 10 | `downgrade-mapi` + verify both *(`--with-downgrade`)* | **from** | from | |

Consequences every test author must keep in mind:

- There are two **channels** of data. `alpha` is seeded by the **from** version, `beta` by the
  **to** version. The same spec file verifies both, several times.
- An assertion can face **old data on a new component** (alpha after an upgrade) and **new data on
  an old component** (beta before `upgrade-gw`, or after a downgrade).
- Seeding always runs from the **current checkout**, never from the tag. `4.13.0-alpha.4` seeds
  `versions/4.13`; pre-release suffixes are ignored. There is **no fallback**: if
  `versions/<major.minor>` is missing, the seed stage fails.

## 2. Where things go

```
gravitee-am-test/
├── migration-seeding/
│   ├── bootstrap.ts              # entry point: --version <major.minor> --label <channel>
│   ├── seed.ts                   # core data set + shared helpers (labels, data planes, naming)
│   ├── <feature>-seed.ts         # one file per feature data set (e.g. token-exchange-seed.ts)
│   └── versions/<major.minor>/seed.ts   # what a given version seeds: calls the feature seeds
└── specs/migration/
    ├── <feature>.jest.spec.ts    # the verification
    └── fixture/
        ├── <feature>-fixture.ts  # finds the seeded entities, exposes actions
        └── migration-versions.ts # version gates (when a feature exists)
```

## 3. Writing the seed

### 3.1 Feature seed file

Create `migration-seeding/<feature>-seed.ts`. It exports:

- the seed function, `seed<Feature>Data(channelLabel, options)`;
- the **name builders** for every entity it creates (`get<Feature>DomainName(label)`, …);
- the **constants** the spec asserts against (scopes, mappings, expected values).

The spec imports the names and constants from this file, so seed and verification can never drift.

Rules:

- **Name everything from the label, never the version.** Use
  `migration-seeded-<feature>-<entity>-${normalizeForName(label)}` (see `seed.ts`). The same
  version is seeded twice (`alpha` and `beta`) on a patch migration and must not collide.
- **Loop over the data planes.** Seed once per `getDataPlaneTargets()` entry, with
  `getInstanceLabel(channelLabel, target.id)` as the label. Each data plane gets its own isolated
  entities.
- **Be idempotent.** Every write is get-or-create, or converges on the current payload. A seed stage
  can be re-run on the same environment.
- **Leave the environment settled.** When the gateway has to pick a change up, wait for it (e.g.
  poll the domain's `/.well-known/openid-configuration`) before returning.
- **Parameterise by API shape, not by version number.** When the way to write the data changed
  between versions, take an option that names the write path (`trustedIssuerApi: 'inline' |
  'trusted-domain'`). Each version module picks the option its Management API supports.

### 3.2 Version-specific payloads

The generated SDK (`api/management`) matches the **current** Management API. For a shape the SDK
no longer models (older versions) or does not model yet, call the Management API over raw HTTP
(`supertest`/`fetch` on `getDomainManagerUrl(domainId)`), as `patchDomainRaw` does in
`token-exchange-seed.ts`. Never rely on the SDK to send a field it does not declare: it drops it
silently.

Only use endpoints and fields that exist in the version the module seeds. For example, 4.12 has no
`/trusted-domains` endpoint, so the 4.12 module writes trusted issuers through the inline list.

### 3.3 Register the feature in the version modules

Call the feature seed from **every** `versions/<major.minor>/seed.ts` from the version that
introduces the feature onward, with that version's options:

```ts
// versions/4.12/seed.ts
await seedMapiData(label);
await seedTokenExchangeData(label, { trustedIssuerApi: 'inline', keyRetrievalApi: 'legacy-spiffe' });

// versions/4.13/seed.ts
await seedMapiData(label);
await seedTokenExchangeData(label, { trustedIssuerApi: 'trusted-domain', keyRetrievalApi: 'key-retrieval-settings' });
```

When a new minor version is released, add its `versions/<major.minor>/seed.ts`, calling every
feature seed that applies. Without it, a run with that version as `--from-tag` or `--to-tag` fails.

## 4. Writing the verification

### 4.1 Fixture

`specs/migration/fixture/<feature>-fixture.ts` takes the instance label (and the data plane
target when it talks to a gateway). It:

- finds the seeded entities **by the names exported from the seed file**, and fails with a clear
  assertion when they are missing;
- exposes the actions the spec performs (`mintIssuerToken()`, `exchange(token)`, …).

It never creates data: everything it needs was created by the seed.

### 4.2 Spec

```ts
setup(120000);

const channelLabel = process.env.AM_MIGRATION_TEST_LABEL || 'alpha';
const suite = channelHoldsFeatureSeed(channelLabel) ? describe : describe.skip;

suite.each(getDataPlaneTargets())('migration <feature> [data plane $id]', (target) => {
  const label = getInstanceLabel(channelLabel, target.id);
  let fixture: FeatureMigrationFixture;

  beforeAll(async () => {
    fixture = await createFeatureMigrationFixture(label, target);
  });

  const managementApiTest = managementApiSupports(FEATURE_VERSION) ? it : it.skip;
  const gatewayTest = gatewaySupports(FEATURE_VERSION) ? it : it.skip;

  managementApiTest('still reports <the configuration>', async () => { /* Management API read */ });
  gatewayTest('still <performs the flow>', async () => { /* gateway call */ });
});
```

- Read the channel from `AM_MIGRATION_TEST_LABEL`; the tool sets it per stage.
- Iterate over `getDataPlaneTargets()` (gateway specs) or `getDataPlaneIds()` (Management API only).
- Assert **behaviour**, not only presence: a flow that must still work after the upgrade should be
  executed through the gateway.
- Add a **negative control** for positive flows (e.g. a tampered token is refused), so the positive
  test cannot pass because a check was dropped.
- Write each assertion so it holds at **every stage** it is not skipped for. If an assertion only
  holds on one side of the upgrade, gate it (next section) instead of weakening it.

## 5. Version gates

The tool exposes the versions of the current stage to the specs:

| Variable | Meaning |
|----------|---------|
| `AM_MIGRATION_FROM_VERSION` | version that seeded the `alpha` channel (major.minor) |
| `AM_MIGRATION_TO_VERSION` | version that seeded the `beta` channel (major.minor) |
| `AM_MIGRATION_MAPI_VERSION` | Management API tag deployed now |
| `AM_MIGRATION_GW_VERSION` | gateway tag deployed now |

`specs/migration/fixture/migration-versions.ts` turns them into gates. Declare the version a
feature appeared in, then gate on **both** questions:

1. **Does the channel hold the data?** Suite level, from the seed version:
   `isAtLeast(seedVersionOf(channelLabel), FEATURE_SEED_VERSION)`.
2. **Does the component under test know the feature?** Per test, from the deployed version:
   `managementApiSupports(FEATURE_VERSION)` for a Management API assertion,
   `gatewaySupports(FEATURE_VERSION)` for a gateway assertion.

The second gate matters on downgrade: `beta` data seeded by 4.13 is still verified after the
gateways go back to 4.10, which has no token exchange at all.

When a variable is absent (spec run by hand, outside the tool), every gate answers "yes", so a plain
run against a current stack asserts everything.

Example (`token-exchange.jest.spec.ts`):

| Test | Gate |
|------|------|
| whole suite | channel seeded by ≥ 4.12 (`TOKEN_EXCHANGE_SEED_VERSION`) |
| issuer still reported on the domain | Management API ≥ 4.11 (`TOKEN_EXCHANGE_VERSION`) |
| issuer exposed as a trusted domain | Management API ≥ 4.13 (`TRUSTED_DOMAIN_ENTITY_VERSION`) |
| exchange succeeds / tampered token refused | gateway ≥ 4.11 (`TOKEN_EXCHANGE_VERSION`) |

## 6. Running it

Type check first:

```bash
npx tsc --noEmit -p gravitee-am-test
```

Against a stack deployed by the tool (from the repository root, see the tool README):

```bash
./scripts/migration-test.mjs run --provider k8s --from-tag 4.12.7 --stage seed-alpha
./scripts/migration-test.mjs run --provider k8s --from-tag 4.12.7 --stage verify-alpha
```

The full pipeline, locally or through CircleCI (`trigger` command), is the only way to exercise the
upgrade and downgrade stages.

Running `npm run migration:seed` / `npm run ci:migration` by hand bypasses the version variables,
so no gate applies.

## 7. Checklist

- [ ] Feature seed file exports the seed function, the entity name builders and the asserted constants
- [ ] Every entity name is derived from the label (`normalizeForName`), none from the version
- [ ] The seed loops over the data planes and is idempotent
- [ ] Each version module from the introducing version onward calls the feature seed with options valid for that version
- [ ] Payloads only use endpoints and fields that exist in the seeded version (raw HTTP where the SDK disagrees)
- [ ] The fixture finds entities by the exported names and creates nothing
- [ ] The spec iterates over data planes and reads the channel from `AM_MIGRATION_TEST_LABEL`
- [ ] Suite gated on the seed version; each test gated on the Management API or gateway version it talks to
- [ ] Positive flows have a negative control
- [ ] `tsc --noEmit` is clean
