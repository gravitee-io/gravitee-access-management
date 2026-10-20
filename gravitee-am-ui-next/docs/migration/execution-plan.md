# Execution plan

Phase 7 output of the migration plan. Date: 2026-10-20. The plan itself is at the workspace root,
`migration-plan.md`. The spike evidence is in `../spike/AM-7349-am-in-gamma-spike-plan.md`.

All work lands as direct commits on `spike/am-gamma`. Draft PR #8666 is the single review PR at the end. One unit or
one story per commit, the four verification commands green at each commit.

## 1. Stages

| Stage                     | Work                                                                                                          | Exit                                                  | State                   |
| ------------------------- | ------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------- | ----------------------- |
| 0. Patterns and inventory | `AGENTS.md`, the documents under `docs/migration/`, the inventory generator, the `migrate-area` skill         | Documents committed. Inventory counts agreed          | Done 2026-10-20         |
| 1. Foundation             | The stories in section 2                                                                                      | The providers unit runs end to end on the kit         | Story 1 done 2026-10-20 |
| 2. Pilot                  | The `providers` unit with the skill and nothing else                                                          | The skill and the rules updated from the measurements | Not started             |
| 3. Scale                  | The batches in section 4, parallel agents in worktrees, one kit lane                                          | Every unit `done` or `out-of-scope`                   | Not started             |
| 4. Parity                 | Organization units, the 404 page, strangler links for anything left, the Angular Playwright journeys replayed | Self-hosted switch per the viability report phasing   | Not started             |

## 2. Stage 1 stories

In order. Each is one commit with the subject `feat(ui-next): <story>`.

| #   | Story                           | Content                                                                                                                                                                                                                                         | Exit                                                               |
| --- | ------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------ |
| 1   | Route split                     | `src/features/<area>/routes.tsx` for the six built areas, mounted lazily from `App.tsx`. `src/lib/api/management-api.ts` split into `domains.ts`, `applications.ts`, `identity-providers.ts`, `forms.ts`, `flows.ts` with a shared `paths.ts`   | The six pages work. Initial JS smaller than today                  |
| 2   | Error and not-found panels      | `ErrorPanel` and `NotFoundPanel` in `src/app/components/`. `HttpError` 404 maps to `NotFoundPanel` in the scopes and pages                                                                                                                      | The three scopes and six pages use them                            |
| 3   | Confirm dialog and save bar     | `ConfirmDialog` on Graphene `Dialog` with a `destructive` variant. `SaveBar` bound to a react-hook-form `formState`                                                                                                                             | A unit test each                                                   |
| 4   | Permission model                | `src/lib/session/permissions.tsx`: loads the environment, domain, application and protected resource permission sets as `AuthGuard` does, `usePermissions()` with `has` and `hasAny`, `<RequirePermission>` route element, `<Can>` gate         | The applications pages gate their actions with the Angular strings |
| 5   | Licence model                   | `src/lib/session/license.tsx`: loads `GET /organizations/:org/license`, `useLicense()` with `isMissing(feature)`, `<RequireLicense>` route element, a click guard that opens the kit upgrade dialog. The 42 features from `gio-license-data.ts` | A unit test with a missing feature                                 |
| 6   | Plugin wizard                   | `PluginWizard` in `src/app/components/`: step 1 picks a plugin type from a list with icons and licence badges, step 2 renders the schema form through `adaptAmSchema`, inside `PageFocused`                                                     | The identity provider create page uses it                          |
| 7   | Memberships panel and tag input | `MembershipsPanel` from the Angular `components/memberships/`, `TagInput` from `Badge` and `Input`                                                                                                                                              | A unit test each                                                   |
| 8   | Playwright project              | `gravitee-am-test/playwright/` project `console-next` on `:4201`, the cockpit-mock login fixture, one spec for the domains list                                                                                                                 | The spec runs green against the local stack                        |
| 9   | Delivery                        | Docker image on the nginx pattern, `constants.json` contract, Helm value to pick the image, a CI job for the four commands                                                                                                                      | CI runs on the spike branch                                        |

Stories 1 to 3 have no dependency. Story 4 and 5 depend on 2. Story 6 depends on 3 and 5. Story 8 depends on nothing.
Story 9 can wait until Stage 3.

## 3. Pilot

The `providers` unit, order 1 in the inventory. Seven routes: list, create, and a detail with settings, mappers, roles
and groups. The list and the settings form exist. The create wizard is the pattern that certificates, factors,
resources, bot detection, device identifiers, extension grants, alert notifiers and audit reporters repeat. An Angular
Playwright page object exists at `gravitee-am-test/playwright/pages/identity-provider-settings.page.ts`.

Measure during the pilot:

| Measure                                            | Why                                             |
| -------------------------------------------------- | ----------------------------------------------- |
| Wall-clock time per page                           | The throughput estimate                         |
| Manual corrections after `copy-prototype.mjs`      | What the script misses                          |
| Test failures on first run                         | What the rules miss                             |
| Graphene props that broke between 3.3.0 and 3.23.1 | What the Graphene version check must list       |
| Prototype additions found                          | How often the notes are incomplete              |
| Angular behaviour the prototype dropped            | How much of section 2 of the rules a unit needs |

Each correction becomes a rule, a script fix, or a kit gap. Then the skill is updated and the pilot's commit stays.

## 4. Stage 3 batches

A batch is a set of units that share no kit dependency with each other. Units in a batch run in parallel, one agent
per unit, each in its own worktree. The kit lane lands before the batch starts. The `order` field in the inventory
gives the sequence inside a batch.

| Batch | Units                                                                                                                                  | Class       | Needs                                                                   | Model                                         |
| ----- | -------------------------------------------------------------------------------------------------------------------------------------- | ----------- | ----------------------------------------------------------------------- | --------------------------------------------- |
| 1     | `providers`                                                                                                                            | B           | Stage 1                                                                 | Fable, high                                   |
| 2     | `certificates`, `factors`, `resources`, `bot-detection`, `device-identifier`, `extensionGrants`                                        | B           | The wizard, from the pilot                                              | Sonnet or Opus, medium                        |
| 3     | `scopes`, `dcr`, `oidc-profile`, `spiffe`, `token-exchange`, `dpop`, `uma`, `cimd`, `saml2`, `ciba`                                    | A, `ciba` B | Nothing new                                                             | Sonnet or Opus, medium                        |
| 4     | `general`, `entrypoints`, `login`, `web-protection`, `webauthn`, `account`, `secrets`, `password-policies`, `trust-domains`, `members` | A and B     | The save bar, the client secret dialogs, the memberships panel          | Sonnet or Opus, medium                        |
| 5     | `roles`, `groups`, `scim`, `self-service-account`                                                                                      | A and B     | The memberships panel                                                   | Sonnet or Opus, medium                        |
| 6     | `users`                                                                                                                                | C           | The user dialogs                                                        | Fable, high                                   |
| 7     | `emails`, `texts`                                                                                                                      | B, C        | The code editor, the dictionary dialog                                  | Opus, medium                                  |
| 8     | `domains`, `applications` as sub-units per section                                                                                     | C           | The wizard, the client secret dialogs, `graphene-charts`                | Fable, high                                   |
| 9     | `audits`, `alerts`                                                                                                                     | B           | The licence click guard, the wizard, `graphene-charts` for audit charts | Opus, medium                                  |
| 10    | `mcp-servers`, `agents`                                                                                                                | C           | Batch 8                                                                 | Fable, high                                   |
| 11    | `theme`, `authorization-engines`                                                                                                       | D           | A design decision each                                                  | Fable, high, with the `graphene-review` skill |
| 12    | `organization`, `organization-*`                                                                                                       | A and B     | The organization scope, the domain units they reuse                     | Sonnet or Opus, medium                        |
| 13    | `shell` 404, strangler links, parity                                                                                                   | A           | Everything above                                                        | Opus, medium                                  |

The kit lane is one agent on the main checkout. It takes a kit gap from a unit's `blockers`, builds the part, commits,
and clears the blocker. Unit worktrees rebase onto `spike/am-gamma` before they land.

## 5. Quality gates

| Class | Before the commit                                                         |
| ----- | ------------------------------------------------------------------------- |
| A     | The four commands. The tests of playbook 4.5. A browser check             |
| B     | A, plus a Playwright spec for create and delete                           |
| C     | B, plus the Angular journey replayed, and the network comparison recorded |
| D     | C, plus the `graphene-review` skill and a Graphene ticket for each gap    |

Every class runs the `code-review` skill before the commit.

## 6. Models and effort

| Work                                                                   | Model                  | Effort |
| ---------------------------------------------------------------------- | ---------------------- | ------ |
| Stage 1 stories, the pilot, class C and D units, the kit lane, reviews | Fable 5.1              | high   |
| Class A and B units after the pilot                                    | Sonnet 5.5 or Opus 5.5 | medium |
| Stage 4 parity replay                                                  | Opus 5.5               | medium |

The skill is written for the smaller model: explicit steps, a check after each, no inferred decision.

## 7. Open decisions

| Decision                   | Options                                                                                    | Recommendation                                                                                  | Owner   |
| -------------------------- | ------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------- | ------- |
| The domain forms page      | Keep `src/features/forms/LoginFormPage.tsx`, which has no Angular domain route, or drop it | Keep until the `login` unit is ported, then decide with the Angular login settings page in view | Stuart  |
| Prototype additions        | Port them when an endpoint exists, or never                                                | Never in a unit run. Collect them in the inventory for a product decision                       | Product |
| Graphene gaps              | Wait for GR-A stories, or ship a kit part                                                  | Kit part now, replace when Graphene ships                                                       | AM team |
| Organization scope         | Port the organization units as separate pages, or add an `AmScope` as the prototype does   | The prototype's scope, in Stage 4                                                               | AM team |
| `agents` and `mcp-servers` | Reuse the application section pages with a type gate, as Angular does, or separate pages   | Reuse, as Angular does                                                                          | AM team |

## 8. Risks

| Risk                                                       | Mitigation                                                                                  |
| ---------------------------------------------------------- | ------------------------------------------------------------------------------------------- |
| Angular keeps changing under ported units                  | `drift.mjs` per unit, and the Angular feature freeze date from the viability report         |
| Graphene 3.3.0 to 3.23.1 prop changes hide in copied pages | Typecheck plus the browser check, and the pilot's list of broken props                      |
| A prototype addition slips into a unit                     | The brief lists them before the code. `check-unit.mjs` flags endpoints with no API function |
| Parallel agents collide on shared files                    | The route split in Stage 1 story 1, and the kit lane                                        |
| The permission sets load per domain and per application    | Load them in the scopes once, as `AuthGuard` does, and cache by key                         |
