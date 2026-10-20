# AM in Gamma: viability report outline and epic breakdown

Epic: [AM-7349](https://gravitee.atlassian.net/browse/AM-7349). Spike: [AM-7769](https://gravitee.atlassian.net/browse/AM-7769).
Companion to [AM-7349-am-in-gamma-spike-plan.md](AM-7349-am-in-gamma-spike-plan.md), which holds the evidence. This file holds the two outputs that close the spike.

Date: 2026-09-24. Author: Stuart Clark. Status: working draft, to continue on 2026-09-25.

## Part 1. The viability report

### 1.1 Audience and purpose

The report answers one question for the AM team lead, product, and the Cloud team: can AM join Gamma through a React console that reuses Graphene, and at what cost? It must give a verdict, the evidence, the cost, and the decisions that other teams own.

The report is a separate document from the spike plan. The plan is the lab notebook; the report is the summary a reader acts on. Target length: 4 to 6 pages.

### 1.2 Draft verdict

Viable, with conditions. The spike built every layer of the path and ran it end to end in a browser, in both directions. No step needed a change to AM code. The flows editor needed one opt-in prop in Graphene's policy studio (1.8). The cost is the console port, which is large but known work. Three conditions sit outside the AM team: the production signing key, the user provisioning model, and the hosting topology.

### 1.3 Report sections

| # | Section | Content | Source | State |
|---|---|---|---|---|
| 1 | Verdict | One paragraph: viable with conditions, the cost bracket, the three conditions | 1.2 above | Draft ready |
| 2 | Question and scope | The ask from AM-7769, the agreed approach, what was in and out of the spike | Plan sections 1 and 2 | Ready |
| 3 | What we built | The demo in 5 steps, with screenshots: Gamma Home tile, Gamma switcher, AM landing, AM switcher, return to Gamma | Plan step results, `scratchpad/s4b-*.png`, `s4-*.png`, `s2-*.png` | Screenshots exist. A GIF of the round trip is missing |
| 4 | Architecture | Target flow diagram, the component list per repo, the token contract | Plan section 2, step 4b diagram | Needs one clean diagram that merges both |
| 5 | Evidence table | Each claim, how it was proven, confidence | Plan section 10 | Update from the step results |
| 6 | What was not proven | The gap list | Plan section 9 | Ready |
| 7 | Cost | Port estimate per area, the Stream B and Gamma-side work, total bracket | Part 2 of this file | **Missing: calibrated per-area numbers** |
| 8 | Risks | Risk, detail, mitigation, owner | Plan section 7 plus the step 4b findings | Add owners |
| 9 | Decisions needed | Decision, options, recommendation, owner, deadline | 1.4 below | Ready to fill |
| 10 | Dependencies on other teams | What each team must deliver and when | 1.5 below | Needs Cloud team input |
| 11 | Recommendation and phasing | Phases with exit criteria | 1.6 below | Draft ready |
| 12 | Cross-cutting concerns | Permissions, licences, the policy studio, plugin form generation | 1.8 below | Draft ready. Policy studio proven for domain and application flows |

### 1.4 Decisions the report must request

| Decision | Options | Recommendation | Owner |
|---|---|---|---|
| Who signs the SSO token | A key pair provisioned for the `am` plugin, or a Cloud backend endpoint that signs with the Cockpit key | Cloud backend delegation: the key already exists there and never leaves Cloud | Cloud team |
| User provisioning in AM | Cloud sends USER and MEMBERSHIP commands for every Gamma user ahead of time, or AM creates the user on first SSO from profile claims with a default role | Create on first SSO, with a default role per org. Commands still set elevated roles | AM and Cloud |
| Token `sub` | APIM technical user id, or Cloud user id | Cloud user id, read from the APIM user's source id | AM and Cloud |
| Token contract | Claims, `aud`, `iss`, TTL, user source `cockpit` or `gamma` | Keep Cockpit's shape, add `aud=am-console`, 30 s TTL, source `cockpit` | AM |
| Hosting topology | Console and MAPI on one origin, or on two hosts in one site | One origin behind the ingress: the spike proved it needs no AM change | Cloud SRE |
| Self-hosted | React console for Gamma Cloud only until parity, or both from day one | Gamma Cloud first, self-hosted switches at parity | Product |
| Angular feature freeze | A date after which new console features are built in React only | Set it when phase 2 starts | Product |
| Licence feature key | Which `feature` in the `identity-and-access-management` pack gates the tile | Confirm the key name | Licensing owner |
| External module SPI | `externalUrl` on `GammaModuleDefinition`, or keep the host's id check | `externalUrl`, so the next external product needs no host edit | Gamma platform team |
| Plugin schema approach | Keep AM's schemas and translate them in the browser, or move AM plugin schemas to APIM's style (draft-07 with `gioConfig`) | The adapter now. Move each schema when its plugin next releases, then delete the adapter | AM |
| Policy studio for AM flows | Extend Graphene's policy studio with an AM flow model, or wrap the current `gv-policy-studio` web component in React | Extend Graphene with the opt-in `FlowModel` prop. Spike step 5b edited and saved AM flows with it, and APIM behaviour did not change. The wrapper is no longer needed | AM and Graphene |

### 1.5 Dependencies on other teams

| Team | Deliverable | Needed by |
|---|---|---|
| Cloud | Signing delegation endpoint, or a key for the `am` plugin | Phase 1 |
| Cloud | `return_to` on the Gamma login, so an AM deep link survives login | Phase 1 |
| Cloud | User provisioning decision, and USER and MEMBERSHIP commands if chosen | Phase 1 |
| Cloud | Gamma environment to AM environment mapping when an account has several | Phase 2 |
| Cloud | Per-user module entitlement, for the switcher filter | Later, optional |
| Cloud SRE | Ingress that serves the AM console and `/management` on one origin | Phase 1 |
| Gamma platform | `externalUrl` in the module SPI, the `am` plugin in the distribution bundle | Phase 1 |
| Graphene | Custom field extension for AM widgets, the Monaco peer fix, the policy studio `FlowModel` release, a React licence check and upgrade dialog | Phase 2 |

### 1.6 Phasing for the report

| Phase | Content | Exit criteria |
|---|---|---|
| 0. Spike | This work | Report accepted, decisions 1.4 made or owned |
| 1. Gamma entry | Stream B in the MAPI, the `am` plugin in production shape, gamma-console host edits, React console foundation and shell, domains and applications | A Gamma Cloud user opens AM from the picker, lands signed in, manages domains and applications. The Angular console still serves everything else through the strangler links |
| 2. Domain parity | The domain feature areas in port order | Every domain page used in Gamma Cloud runs in React |
| 3. Full parity | Org settings, remaining areas, self-hosted switch, Angular removal | Self-hosted ships the React console. Angular module deleted |

### 1.7 Work to finish before writing the report

1. Calibrate the port cost: time one page per pattern (list, tabbed detail, schema form, code editor) from the spike, then apply it to the component counts per area in Part 2.
2. Survey the EE plugin schemas: factors, reporters, certificates, bot detection, policies. Count custom widgets that need a Graphene field. Check that Graphene's form handles `if`/`then` and `allOf`.
3. ~~Run the short flows and policies spike.~~ Done in spike steps 5a and 5b: AM flows are editable in Graphene's policy studio with one opt-in prop. See the spike notes.
4. Record bundle and build metrics for the report: initial JS, gzipped size, build time, test count.
5. Record a GIF of the round trip.
6. Get the Cloud team's answer on the signing key, provisioning, and `return_to`.

### 1.8 Cross-cutting concerns from review

Review raised four concerns: permission management, licence management, the policy studio, and plugin form generation. The report must give each one a section with its plan.

#### Permission management

The Angular console checks permissions on 139 routes, with route guards and the `hasPermission` directives in 103 files. The React console calls the AM Management API, so it keeps AM's permission model. The APIM permission model in `@gravitee/gamma-modules-sdk` does not apply.

- The AM-A story "Permission model" ports the guards and the action checks.
- A Gamma user needs an AM role at first sign-in. Decision 1.4 on user provisioning sets it.
- Gamma activates a module per installation, from the licence. It cannot hide the tile from one user, so a user without AM rights still sees the tile. The Cloud per-user entitlement in 1.5 closes this later.

#### Licence management

Licensing has two layers.

1. The Gamma tile: the `feature` line in `plugin.properties`. Decision 1.4 names the key.
2. Features inside the console: AM defines 42 licence features, for example `AM_IDP_SAML`, `AM_MFA_FIDO` and `AM_ENTERPRISE`. The `licenseGuard` directive gates them in 21 templates. It uses `GioLicenseService` from `ui-particles-angular`, which is Angular only.

The React console needs a licence check hook and the upgrade dialog. The AM-A story "Licence checks" covers the console side. The GR-A story "Licence check and upgrade dialog" asks Graphene for a shared dialog, so gamma modules show one upsell.

#### Policy studio

Graphene's policy studio is APIM-shaped: API types, plans, and the REQUEST, RESPONSE, PUBLISH and SUBSCRIBE phases. AM flows have flow types such as ROOT, LOGIN, CONSENT and REGISTER, each with pre and post steps. The Angular console uses `gv-policy-studio` from `@gravitee/ui-components` 4.3.3, a web component.

The flows spike (1.7 item 3) compared two options:

- Extend Graphene's policy studio with an AM flow model. This is the long-term target and needs Graphene team capacity.
- Wrap `gv-policy-studio` in React. React 19 supports custom elements. This unblocks AM-J without Graphene work.

Decision 1.4 records the choice.

**Spike result (steps 5a and 5b).** Graphene's organization scope already takes flows with a request and a response phase, which is AM's pre and post shape. One opt-in prop, `FlowModel`, covered the rest: AM phase text and actors, one sidebar group per flow type, a condition-only flow form, and step names. The save output returns the flows per group. With it, the React console edited, added and removed AM flows and steps and saved them through `PUT .../flows`. All 509 existing policy studio tests passed unchanged. A new flow model in Graphene is not needed, and AM-J drops to medium risk. The Graphene change is in draft PR [#470](https://github.com/gravitee-io/gravitee-ui-graphene/pull/470) for the Graphene team to review.

**Application flows.** The same page edits one application's flows through `PUT .../applications/{id}/flows`. The page header has the inherit switch, which sets `settings.advanced.flowsInherited`. As in AM, a service application shows only the token flow. Graphene drops any flow whose group the flow model does not list. The page therefore adds the hidden flows back before it saves, so a save keeps them. A gateway run of flows saved from the React console is not checked yet.

#### Plugin form generation and schema versions

The JSON Schema draft is a small problem. The form keywords are the real gap.

| | AM plugin schemas (35 in the AM repo) | APIM plugin schemas |
|---|---|---|
| `$schema` | Not declared | draft-07 |
| Draft style | Jackson output, draft-04 `id: urn:jsonschema:…` | draft-07 |
| Form keywords | angular-schema-form: `widget` (36), `sensitive` (32), `x-schema-form` (22), `titleMap` (17), `readonly` (4) | Graphene's `gioConfig` and `format` |

Graphene validates with Ajv 8 and `strict: false`. It already turns the draft-04 `id` keyword into a no-op, because many Gravitee plugin schemas come from Jackson. AM schemas therefore validate today. The spike's `am-schema.ts` translates the form keywords in about 70 lines and ran the JDBC identity provider form end to end.

- Decision 1.4 "Plugin schema approach" chooses between the adapter and a schema move.
- EE plugins live in other repositories and are not surveyed (1.7 item 2).
- 4 AM schemas use `if` and 3 use `allOf`. Graphene resolves `anyOf` and `oneOf` variants. Support for `if`/`then` is not confirmed.

## Part 2. Epics and stories per repository

Sizes: S is up to 3 developer days, M up to 10, L up to 20, XL above 20. Port epics carry component counts from `gravitee-am-ui/src/app` as the size input; item 1 of 1.7 turns them into days. When these become Jira tickets, each one is written as requirements only, without the notes and file references below.

### 2.0 Epic list

The work splits into 3 spikes, 14 epics owned by the AM team, and 1 epic owned by the Graphene team. The 13 port areas (AM-D to AM-P) merge into 7 port epics, because several areas hold only 2 or 3 stories and ship together. Sections 2.1 to 2.5 hold the story detail. The Source column maps each epic to its working code there.

#### Spikes before the epics

Run these under AM-7349 first. Their results change the scope and size of the epics.

| Spike | Output | Unblocks |
|---|---|---|
| ~~Flows and policy studio~~ | Done in spike steps 5a and 5b: extend Graphene with `FlowModel` | Epic 11 |
| Port cost calibration | Time one page per pattern, then days per port epic (1.7 item 1) | Cost section of the report, epics 6 to 14 |
| EE plugin schema survey | Custom widget count, `if`/`then` and `allOf` usage (1.7 item 2) | Epic 1 schema adapter, Graphene custom fields |

#### Phase 1: open AM from Gamma

| # | Epic | Repo | Source | Stories |
|---|---|---|---|---|
| 1 | React console foundation | `gravitee-access-management` | AM-A | 11 |
| 2 | Gamma mode in the Management API | `gravitee-access-management` | AM-B | 11 |
| 3 | Gamma-mode shell | `gravitee-access-management` | AM-C | 4 |
| 4 | Access Management tile plugin | `gravitee-gamma-module-am` (new) | GM-A | 7 |
| 5 | External module support in Gamma | `gravitee-api-management` | GA-A | 5 |
| 6 | Port: domains and applications | `gravitee-access-management` | AM-D, AM-E | By area |

#### Phase 2: domain parity

| # | Epic | Source | Graphene risk |
|---|---|---|---|
| 7 | Port: identity providers, users, groups and roles | AM-F, AM-G | Low |
| 8 | Port: OAuth and OIDC settings, scopes, CIBA, UMA, certificates | AM-H, AM-I | Low |
| 9 | Port: MFA, bot detection and device identifiers | AM-K | Medium |
| 10 | Port: login pages, forms, emails and themes | AM-L | Low |
| 11 | Port: flows and policies | AM-J | Medium. Needs the Graphene `FlowModel` change merged |
| 12 | Port: audits, analytics, reporters and alerts | AM-M, AM-N | Medium |
| 13 | Port: MCP servers, agents and authorization engines | AM-O | Medium |

#### Phase 3: full parity and cut-over

| # | Epic | Source |
|---|---|---|
| 14 | Organization settings, self-hosted switch and removal of the Angular console | AM-P plus the cut-over work |

#### Graphene team

| Epic | Source | Stories |
|---|---|---|
| Graphene gaps for AM | GR-A | 8 |

Epic 1 needs the Monaco peer fix, custom schema fields, `if`/`then` support and the upgrade dialog in phase 1. Epic 11 needs the `FlowModel` prop released in the policy studio.

#### Other teams

The Cloud, Cloud SRE and licensing items in 2.5 are not AM epics. Each one is a ticket in the owning team's project, linked to the epic it blocks:

- Signing delegation, user provisioning and `return_to` block epics 2 and 4.
- The same-origin ingress blocks the phase 1 release.
- The licence feature key blocks epic 4.

#### Order of work

- Epics 1, 2, 4 and 5 can run in parallel.
- Epic 3 depends on epic 2.
- Epic 6 depends on epic 1.
- In phase 2, epics 7, 8, 10, 12 and 13 can run in parallel. Epic 9 waits for the schema survey. Epic 11 waits for the Graphene `FlowModel` release.

### 2.1 `gravitee-access-management`

#### Epic AM-A: React console foundation (`gravitee-am-ui-next`)

| Story | Detail | Size |
|---|---|---|
| Module and build | Maven module, rsbuild, React 19, Graphene pinned to gamma-console's version, lint, vitest. Add to the root pom. Import Graphene's CSS last from `styles.css`. Spike code is the starting point | S |
| Delivery | Docker image on the existing nginx pattern, `constants.json` contract, Helm values to pick the image, CI build and publish jobs | M |
| Auth and HTTP layer | Login and logout redirect chain, callback, CSRF echo, 401 handling, error toasts. Exists in the spike; needs review and tests | S |
| Permission model | Load user permissions, route guards, permission-gated actions, matching Angular's 139 guarded routes and the `hasPermission` directives in 103 files | M |
| Licence checks | Load the org licence, a hook to check one of the 42 AM features, the upgrade dialog on a missing feature. Replaces `licenseGuard` and `GioLicenseService` | M |
| Lazy routes | One chunk per feature area, a size budget in CI | S |
| Shared page patterns | Server list with 0-to-1 page conversion, tabbed detail, save bar, delete confirm, empty and error states | M |
| Plugin schema adapter | `am-schema.ts` for all plugin types, custom widget registry, `if`/`then` and `allOf` support, tests per schema | M |
| Monaco setup | Worker configuration, shared editor wrapper | S |
| Strangler links | nginx serves both bundles on one host; unported routes deep-link into the Angular bundle | M |
| End-to-end tests | Playwright project for the React console, the Cockpit SSO scenario with cockpit-mock | M |

#### Epic AM-B: Gamma mode in the Management API (Stream B)

| Story | Detail | Size |
|---|---|---|
| `gamma.*` settings | `gamma.enabled`, `gamma.login.url` in `gravitee.yml`, Helm values | S |
| Installation payload | `GET /platform/configuration/installation` returns `gammaCloud {enabled, loginUrl, consoleUrl}` | S |
| Login redirect | `/auth/login` redirects to `gamma.login.url` with `return_to` in Gamma mode. Fixes the dead managed-mode login | M |
| Org-admin refusal | `checkNotGammaMode()` on org-scoped admin resources | M |
| SSO hardening | `aud` and `iss` checks, `redirect_uri` allowlist, profile claims (`email`, `given_name`, `family_name`, `preferred_username`) | M |
| User creation on first SSO | If decision 1.4 picks it: create the org user from claims, give the org's default role | M |
| Org Gamma reference | `gammaManagementApiUrl`, `gammaOrganizationId`, `gammaConsoleUrl` on org settings, the Cockpit ORGANIZATION command payload | M |
| Module list endpoint | `GET /organizations/{org}/gamma/modules`, a server-side call to the public Gamma `/modules` endpoint | S |
| Helm keystore path | Fix the keystore mount path mismatch with `cloud.enabled` | S |
| API docs | OpenAPI spec updates, `check-oas` | S |
| Tests | Jest API tests with cockpit-mock for each flow above | M |

#### Epic AM-C: Gamma-mode shell

| Story | Detail | Size |
|---|---|---|
| Switcher from the MAPI | Replace the spike's `constants.json` Gamma block with the installation payload and the module list endpoint | S |
| Shared module catalog | Labels, icons, order from one source shared with gamma-console, or display data from the module list | S |
| Hide the org surface | Gamma mode hides org menu entries and org routes | S |
| Environment mapping | Map the Gamma environment to the AM environment for the Home link and the landing route | S |

#### Port areas AM-D to AM-P, in port order

The epic list in 2.0 merges these 13 areas into 7 port epics.

Counts are Angular components from the size survey. Area names follow `gravitee-am-ui/src/app/domain/`.

| Epic | Area | Components | Graphene risk | Notes |
|---|---|---|---|---|
| AM-D | Domains list, creation, dashboard | part of 229 in `domain` | Low | Spike built the list, the domain switcher and the prototype's navigation |
| AM-E | Applications | 44 | Low | 30 child routes in Angular. Spike built the detail layout with the prototype's context sidebar: Overview, General and Flows. The other sections are placeholders |
| AM-F | Identity providers | part of `settings` 132 | Low | Spike built the JDBC form |
| AM-G | Users, groups, roles | part of `settings` | Low | |
| AM-H | OIDC and OAuth settings, scopes, CIBA, UMA | part of `settings` | Low | |
| AM-I | Certificates | part of `settings` | Low | |
| AM-J | Flows and policies | part of `settings` | Medium | Spike built the domain and application flows pages on Graphene's studio with `FlowModel`, with inheritance. A gateway run is not checked |
| AM-K | MFA factors, bot detection, device identifiers | part of `settings` | Medium | Custom widgets likely |
| AM-L | Login, forms, emails, themes, branding | part of `settings` | Low | Spike built the login form |
| AM-M | Audits, analytics, reporters | part of `settings` | Medium | Charts move to `graphene-charts` |
| AM-N | Alerts | 8 | Medium | |
| AM-O | MCP servers, agents, authorization engines | 12 + 3 + 5 | Medium | OpenFGA UI has no Graphene match |
| AM-P | Organization settings | 14 | Low | Self-hosted only; last |

The shared `components` folder (22 in `domain`, 30 at the root) spreads across these epics as each area needs a part.

### 2.2 `gravitee-gamma-module-am` (new repository)

Epic GM-A: Access Management tile plugin.

| Story | Detail | Size |
|---|---|---|
| Repository | Create the repo from the AuthZ module: pom, CI, release, renovate, CODEOWNERS. Move the spike folder in | S |
| SSO resource | `GET /sso` returns the AM console URL. Production error handling: no connection, issuer failure, unknown user | S |
| Token issuer | Call the Cloud signing endpoint, or sign with a provisioned key, per decision 1.4 | M |
| Subject mapping | `sub` from the Cloud user id of the APIM user | S |
| Settings | Console URL and issuer URL from `gravitee.yml`, or from `AmConnection` | S |
| Licence gate | `feature` in `plugin.properties` | S |
| Tests and docs | Unit tests, a Jersey resource test, OpenAPI, README | S |

### 2.3 `gravitee-api-management`

Epic GA-A: Gamma support for an external module.

| Story | Detail | Size |
|---|---|---|
| External module SPI | `externalUrl` on `GammaModuleDefinition`; `hasUi` accepts it; remove the host's id check | M |
| Host edits | Catalog entry, icon, Home card, `AccessManagementRedirect` route. Done on the spike branch; needs review and tests | S |
| Distribution | Bundle the `am` plugin zip in the distribution pom, like the other Gamma modules | S |
| AM connection for SSO | Let SSO use a connection without a service-account token, or document that it needs one | S |
| Module display data | Optional: return label, tagline and icon from `/modules`, so AM and gamma-console share one catalog | M |

### 2.4 `gravitee-ui-graphene`

Epic GR-A: gaps found by the AM port.

| Story | Detail | Size |
|---|---|---|
| Monaco as a true optional peer | The root entry imports the code editor, so a build without `monaco-editor` fails | S |
| Custom schema fields | An extension point in `JsonSchemaForm` for AM widgets such as `datasource` | M |
| Policy studio `FlowModel` | Review and release the opt-in `FlowModel` prop from draft PR #470: host phase text, host groups, condition-only form, step names, `flowGroups` in the save output | M |
| Policy studio robustness | Escape the step-validity key, so a flow id with `:` does not crash the studio. Say why Save is disabled when a step in another flow is invalid. Keep the flows whose group the host model does not list, or document that the host must keep them | S |
| Licence check and upgrade dialog | A React upgrade dialog shared by gamma modules, fed by a licence feature list | M |
| Conditional schemas | Confirm or add `if`/`then` support in `JsonSchemaForm` | S |
| Lint rule | Flag `divide-y` and `border` without a colour token under Tailwind 4 | S |
| Docs | Form value types must be `type` aliases for the resolvers. Warn that bundler code splitting can load `graphene-core/styles` before the app's Tailwind CSS; import it last from the app's CSS | S |

### 2.5 Other teams, outside our repositories

| Team | Items |
|---|---|
| Cloud (`gravitee-cockpit`, private) | Signing delegation, `return_to` on Gamma login, user provisioning, environment mapping, per-user entitlement |
| Cloud SRE | Same-origin ingress for the AM console and `/management`, the new console image in the Cloud deployment |
| Licensing | The feature key for the AM tile |

## Part 3. Where to resume on 2026-09-25

1. Review Part 1 and Part 2 of this file together and adjust the epic list.
2. Choose which of the six items in 1.7 to do before the report. Calibrating the cost matters most. The flows spike is done.
3. Draft the report itself from 1.3, in a shareable place.
4. After the report is accepted, create the Jira epics and stories from Part 2 under AM-7349.

The running stacks and how to restart them are in the plan, section "Current state and how to resume (2026-09-24, after step 4b)".
