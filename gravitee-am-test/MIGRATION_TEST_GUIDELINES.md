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
| 2 | `seed-alpha` | from | from | seeds covering the from version, label `alpha` |
| 3 | `verify-alpha` | from | from | `specs/migration`, label `alpha` |
| 4 | `upgrade-mapi` | **to** | from | – |
| 5 | `seed-beta` | to | from | seeds covering the to version, label `beta` |
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
- Seeding always runs from the **current checkout**, never from the tag. A version seeds every data
  set whose declared version range covers it, compared on major.minor (`4.13.0-alpha.4` is 4.13).
  A version no data set covers fails the seed stage.

## 2. Where things go

```
gravitee-am-test/
├── migration-seeding/
│   ├── bootstrap.ts              # entry point: --version <major.minor> --label <channel>
│   ├── version-range.ts          # VersionRange, migrationSeed(), requirements (shared with the specs)
│   ├── seeds.ts                  # registry of every data set, in seeding order
│   ├── seed.ts                   # core data set (MAPI_DATA_SEED) + shared helpers (labels, data planes, naming)
│   └── <feature>-seed.ts         # one file per feature data set and its declaration (e.g. TOKEN_EXCHANGE_SEED)
└── specs/migration/
    ├── <feature>.jest.spec.ts    # the verification
    └── fixture/
        ├── <feature>-fixture.ts  # finds the seeded entities, exposes actions, declares the requirements
        └── migration-versions.ts # reads the versions of the current stage: describeWhen / itWhen
```

## 3. Writing the seed

### 3.1 Feature seed file

Create `migration-seeding/<feature>-seed.ts`. It exports:

- the seed function, `seed<Feature>Data(channelLabel, options)`;
- its **declaration**, `<FEATURE>_SEED = migrationSeed({...})` (see 3.3);
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
  'trusted-domain'`). Each variant of the declaration picks the option its Management API supports.

### 3.2 Version-specific payloads

The generated SDK (`api/management`) matches the **current** Management API. For a shape the SDK
no longer models (older versions) or does not model yet, call the Management API over raw HTTP
(`supertest`/`fetch` on `getDomainManagerUrl(domainId)`), as `patchDomainRaw` does in
`token-exchange-seed.ts`. Never rely on the SDK to send a field it does not declare: it drops it
silently.

Only use endpoints and fields that exist in the versions a variant covers. For example, 4.12 has no
`/trusted-domains` endpoint, so the variant covering 4.12 writes trusted issuers through the inline list.

### 3.3 Declare the versions it covers

A data set declares, next to its seed function, the ranges of versions it is seeded from and the
options each range needs. A range is `{ from?, until? }` on major.minor: `from` inclusive, `until`
exclusive, either bound optional (`{}` covers every version, `{ from: '4.13' }` 4.13 onwards,
unreleased versions included).

```ts
// token-exchange-seed.ts
export const TOKEN_EXCHANGE_SEED = migrationSeed<TokenExchangeSeedOptions>({
  name: 'token exchange trusted issuer',
  variants: [
    { range: { from: '4.12', until: '4.13' }, options: { trustedIssuerApi: 'inline', keyRetrievalApi: 'legacy-spiffe' } },
    { range: { from: '4.13' }, options: { trustedIssuerApi: 'trusted-domain', keyRetrievalApi: 'key-retrieval-settings' } },
  ],
  seed: seedTokenExchangeData,
});
```

Then add it to `MIGRATION_SEEDS` in `seeds.ts`. For a version V, the seed stage runs every
registered data set one of whose variants covers V, with that variant's options, in registration
order.

- Variants must not overlap: `migrationSeed()` throws when two ranges share a version.
- A version no variant covers does not seed the data set: that is how a data set starts at the
  version introducing the feature (`from`), or stops where its API was removed (`until`).
- A new minor version needs no change as long as the write path does not change. When it does,
  close the current variant with `until` and add one for the new write path.

## 4. Writing the verification

### 4.1 Fixture

`specs/migration/fixture/<feature>-fixture.ts` takes the instance label (and the data plane
target when it talks to a gateway). It:

- declares the spec's **requirements** (see section 5);

- finds the seeded entities **by the names exported from the seed file**, and fails with a clear
  assertion when they are missing;
- exposes the actions the spec performs (`mintIssuerToken()`, `exchange(token)`, …).

It never creates data: everything it needs was created by the seed.

### 4.2 Spec

```ts
setup(120000);

const channelLabel = currentChannel();
const requires = FEATURE_REQUIREMENTS;

describeWhen(requires.seeded, channelLabel).each(getDataPlaneTargets())('migration <feature> [data plane $id]', (target) => {
  const label = getInstanceLabel(channelLabel, target.id);
  let fixture: FeatureMigrationFixture;

  beforeAll(async () => {
    fixture = await createFeatureMigrationFixture(label, target);
  });

  itWhen(requires.managementApiReadsIt)('still reports <the configuration>', async () => { /* Management API read */ });
  itWhen(requires.gatewayRunsIt)('still <performs the flow>', async () => { /* gateway call */ });
});
```

- Read the channel with `currentChannel()` (`AM_MIGRATION_TEST_LABEL`); the tool sets it per stage.
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

A spec's fixture declares what each assertion needs as a `MigrationRequirement`, which the spec
passes to `describeWhen` / `itWhen` (`specs/migration/fixture/migration-versions.ts`): the test runs
when the requirement holds for the current stage, and is skipped otherwise. A requirement answers
two questions, each optional:

1. **Does the channel hold the data?** `seed: <FEATURE>_SEED`: the version that seeded the channel
   must be covered by the data set (section 3.3). Used at suite level.
2. **Does the component under test know the feature?** `managementApi: { from: '4.13' }` for a
   Management API assertion, `gateway: { from: '4.11' }` for a gateway assertion, checked against
   the deployed version. Any `VersionRange`, so `until` expresses an API that was removed.

The second question matters on downgrade: `beta` data seeded by 4.13 is still verified after the
gateways go back to an older version.

```ts
// token-exchange-fixture.ts
export const TOKEN_EXCHANGE_REQUIREMENTS = migrationRequirements({
  seeded: { seed: TOKEN_EXCHANGE_SEED },
  managementApiReportsTrustedIssuer: { managementApi: { from: '4.11' } },
  managementApiServesTrustedDomains: { managementApi: { from: '4.13' } },
  gatewayExchangesTokens: { gateway: { from: '4.11' } },
});
```

When a variable is absent (spec run by hand, outside the tool), every requirement holds, so a plain
run against a current stack asserts everything.

Example (`token-exchange.jest.spec.ts`):

| Test | Requirement |
|------|-------------|
| whole suite | channel seeded by a version `TOKEN_EXCHANGE_SEED` covers (4.12 onwards) |
| issuer still reported on the domain | Management API `{ from: '4.11' }` |
| issuer exposed as a trusted domain | Management API `{ from: '4.13' }` |
| exchange succeeds / tampered token refused | gateway `{ from: '4.11' }` |

## 6. Running it

Type check and run the seeding unit tests first (no stack needed):

```bash
npx tsc --noEmit -p gravitee-am-test
npm --prefix gravitee-am-test run test -- migration-seeding
```

Against a stack deployed by the tool (from the repository root, see the tool README):

```bash
./scripts/migration-test.mjs run --provider k8s --from-tag 4.12.7 --stage seed-alpha
./scripts/migration-test.mjs run --provider k8s --from-tag 4.12.7 --stage verify-alpha
```

The full pipeline, locally or through CircleCI (`trigger` command), is the only way to exercise the
upgrade and downgrade stages.

Running `npm run migration:seed` / `npm run ci:migration` by hand bypasses the version variables,
so every requirement holds.

## 7. Checklist

- [ ] Feature seed file exports the seed function, its `migrationSeed` declaration, the entity name builders and the asserted constants
- [ ] Every entity name is derived from the label (`normalizeForName`), none from the version
- [ ] The seed loops over the data planes and is idempotent
- [ ] The declaration's variants cover the versions from the introducing one onward, without overlap, and the data set is registered in `seeds.ts`
- [ ] Payloads only use endpoints and fields that exist in the seeded version (raw HTTP where the SDK disagrees)
- [ ] The fixture finds entities by the exported names and creates nothing
- [ ] The spec iterates over data planes and reads the channel from `AM_MIGRATION_TEST_LABEL`
- [ ] Requirements declared in the fixture: suite gated on the seed, each test on the Management API or gateway version it talks to
- [ ] Positive flows have a negative control
- [ ] `tsc --noEmit` is clean
