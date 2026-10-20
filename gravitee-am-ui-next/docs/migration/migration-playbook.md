# Migration playbook

Phase 6 output of the migration plan. Date: 2026-10-20. The `migrate-area` skill under
`docs/agent-standards/skills/` is the executable form of this playbook. This file holds the detail the skill points at.

One run ports one unit. A unit is one entry in `inventory.json`: one Angular folder, its routes, its prototype pages.
The run ends with one commit on `spike/am-gamma`.

## 1. Preconditions

Stop and report when any of these fails.

| Check                                                                                           | How                                                                                                              |
| ----------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| The branch is `spike/am-gamma` and the tree is clean                                            | `git status -sb`                                                                                                 |
| The baseline is green                                                                           | `yarn typecheck && yarn lint && yarn test` in `gravitee-am-ui-next/`                                             |
| The unit exists, its `status` is `not-started` or `in-progress`, its `blockers` list is empty   | `inventory.json`                                                                                                 |
| The kit parts the unit needs exist                                                              | `src/app/components/`: the confirm dialog, the wizard, the save bar, the panels. See `execution-plan.md` Stage 1 |
| The permission and licence hooks exist, when the unit has `effectivePerms` or `licenseFeatures` | `src/lib/session/permissions.tsx`, `src/lib/session/license.tsx`                                                 |

A missing kit part or hook is a blocker. Write it into the unit's `blockers` and stop. Do not build a kit part inside
a unit run.

## 2. Read

Read in this order. Do not write code before the list is complete.

1. `gravitee-am-ui-next/AGENTS.md`.
2. The unit entry in `docs/migration/inventory.json`: `pages[]` with `path`, `angularFile`, `prototypePages`,
   `effectivePerms`, `licenseFeature`, `resolvers`, `menu`, flags, and the unit `notes`.
3. For each page, the Angular files: the component `.ts` and `.html`, the service the component injects, the resolvers
   the route names, and the route block in `app-routing.module.ts`.
4. For each page, the prototype file under `gamma-baby/src/modules/am/` and its `prototypeNotes` in
   `gamma-baby/source-refs.json`. The notes say what the prototype changed from Angular.
5. The console reference page for each shape the unit has:

| Shape                | Reference                                                                                    |
| -------------------- | -------------------------------------------------------------------------------------------- |
| Server list          | `src/features/domains/DomainsPage.tsx`                                                       |
| Client list          | `src/features/identity-providers/IdentityProvidersPage.tsx`                                  |
| Detail with sections | `src/features/applications/ApplicationPage.tsx`                                              |
| Plain form           | `Settings` in `ApplicationPage.tsx`                                                          |
| Plugin schema form   | `src/features/identity-providers/IdentityProviderPage.tsx`                                   |
| Plugin wizard        | The kit wizard, and `node_modules/@gravitee/graphene-core/snippets/creation-wizard-page.tsx` |
| Code editor          | `src/features/forms/LoginFormPage.tsx`                                                       |
| Full-bleed editor    | `src/features/flows/FlowsPage.tsx`                                                           |

6. `conversion-rules.md` sections 1 and 3. Section 2 when the Angular page has behaviour the prototype left out.
7. The `USAGE_GUIDE.md` section for a shape not built before.
8. The Angular Playwright spec and page object under `gravitee-am-test/playwright/` when one exists for the unit.

## 3. Brief

Write the brief into the unit's `notes` in `inventory.json` before the first code change. It lists:

- The pages, as `Angular route → prototype page → console file`.
- The Management API calls, as `METHOD path`, from the Angular service.
- The permission strings per page and per action, AM names.
- The licence features.
- The kit parts the unit uses.
- The prototype additions to leave out, with the reason `no endpoint` or `not in AM`.
- The Angular behaviour the prototype left out, which the port adds back.
- Open questions. A question that changes the scope stops the run.

## 4. Build

Work in this order. Run `yarn typecheck` after each step.

### 4.1 API module

Create `src/lib/api/<unit>.ts`.

- One `interface` per entity, `readonly` fields, named without the `Am` prefix. Fields come from the Angular service
  payload, checked against `../gravitee-am-test/api/management/models/` and `../docs/mapi/openapi.yaml`.
- One function per Angular service method the unit uses: `listX(ref, page, size, query)`, `getX(ref, id)`,
  `createX(ref, body)`, `updateX(ref, id, body)`, `patchX(ref, id, patch)`, `deleteX(ref, id)`. Same path, same
  parameters, same payload fields.
- Build the path with `domainPath(ref)` and `searchParam(query)` from `src/lib/api/paths.ts`.

Check: every endpoint in the brief has a function. No function has an endpoint the brief does not list.

### 4.2 Pages

For each page, in the order list, create, detail:

1. Copy the prototype file to `src/features/<unit>/<Name>.tsx`. Keep the prototype's JSX structure, copy, columns,
   empty states and dialogs.
2. Apply `conversion-rules.md` section 1, rule by rule: reads, writes, permissions, scope, paths, layout, types,
   Graphene props, format.
3. Remove every prototype addition the brief lists.
4. Add every Angular behaviour the brief lists, with section 2 of the rules.
5. Order the branches: error, not found, empty, data.
6. Replace the prototype's `PageHeader`, `ConfirmDialog`, `LoadError` and `StateGap` with the console's parts.

Check: no import from `gamma-baby`, no `useJson`, no `send(`, no `/am/`, no `useHasPermission`, no Gamma-style
permission string, no `@mirrors`, no `interface` passed to a resolver.

### 4.3 Routes and navigation

- Create `src/features/<unit>/routes.tsx` that exports the unit's `<Route>` elements, with the Angular path segments
  and `lazyPage()` for each page.
- Mount it in `src/features/settings/routes.tsx` for a domain setting, in `src/features/organization/routes.tsx` for an
  organization unit, or in `src/app/App.tsx` for a top-level section, in place of the placeholder routes for the unit's
  keys.
- Check `src/app/navigation.ts`: the unit's nav key and title exist. Add a missing one with the Angular `menu.label`.
- Set `useBreadcrumbs` on a page and `useSectionSidebar` on a layout, with absolute paths from `basePath`.

Check: every `path` in the unit's inventory entry resolves to a React route. The placeholder for the unit is gone.

### 4.4 Permissions and licence

- Wrap each route whose `guards` has `AuthGuard` in `<RequirePermission anyOf={perms}>`, with that route's own `perms`
  list. The parent routes carry their own, so `effectivePerms` is for reading, not for the element.
- Gate each create, update and delete action with `<Can all={[...]}>` or `usePermissions().has()` and the AM
  permission from the brief.
- When the unit introduces a new scope, such as a protected resource, load its list with `LoadPermissionScope` in the
  scope component.
- Wrap each route with a `licenseFeature` with the licence route element. Guard the gated action's click with the
  licence click guard.
- Hide a route and its nav item when the page has `hideInCloudMode` and the installation is managed.

Check: a test renders the page with a user that lacks the permission and asserts the action is absent.

### 4.5 Tests

Under `src/features/<unit>/__tests__/`, with the harness of `DomainsPage.test.tsx`:

| Test         | Asserts                                                                           |
| ------------ | --------------------------------------------------------------------------------- |
| List renders | The rows from the mocked list function, the call arguments `(ref, 0, 25, '')`     |
| Search       | The call arguments with the search text, the no-results state                     |
| First use    | The first-use empty state when the total is 0                                     |
| Save         | The mocked update function receives the Angular payload fields, the success toast |
| Gated action | The action is absent without the permission and present with it                   |
| Delete       | The confirm dialog, then the mocked delete function, then the list invalidation   |
| Helpers      | Any mapping or adapter function in the unit, as plain unit tests                  |

Do not assert a behaviour the prototype invented.

## 5. Verify

```bash
cd gravitee-am-ui-next
yarn typecheck && yarn lint && yarn test && yarn build
```

Then in the browser, against the local stack:

1. `yarn serve`, sign in, open each page of the unit.
2. Run the journeys: list, search, create, edit, save, delete.
3. Open the network panel. For each action, note the Management API calls.
4. Open the Angular console on `:4200` for the same domain and run the same actions. Compare the calls: method, path,
   payload fields.
5. For class B and above, add or run the unit's Playwright spec in the console's Playwright project.
6. For class D, run the `graphene-review` skill on `src/features/<unit>/`.

Record the result in the unit's `notes`: the pages checked, the calls compared, any difference and its reason.

## 6. Record

In `inventory.json`:

- Each page: `status: done`, `reactFiles`, or `status: out-of-scope` with `outOfScope` text.
- The unit: `status`, `angularCommit` set to the AM checkout's HEAD, `notes` with the brief and the verification
  result, `blockers` empty.

Then `node scripts/migration/build-inventory.mjs` to render the markdown.

## 7. Review and commit

1. Run the `code-review` skill on the diff. Fix what it finds.
2. Run the `commit` skill. Subject `feat(ui-next): port <unit>`. Body only for a decision the diff does not show.
   The inventory files are in the same commit.
3. Push.

## 8. Code organisation

```
src/features/<unit>/
  routes.tsx              the unit's <Route> elements
  <Entities>Page.tsx      list
  <Entity>Page.tsx        detail, or a layout plus one file per section
  Create<Entity>Page.tsx  create, on the kit wizard when the Angular page is a stepper
  <helper>.ts             mappings, adapters, constants
  __tests__/
src/lib/api/<unit>.ts     types and functions
```

- One component per file, named as the file.
- A part used by two units moves to `src/app/components/` in its own commit, before the second unit uses it.
- No import from another unit's folder.
- The licence header on every file.

## 9. Definition of done

- [ ] Every Angular route of the unit has a React route with the same segments.
- [ ] Every `effectivePerms` list is enforced on its route. Every gated action uses the AM permission name.
- [ ] Every `licenseFeature` is enforced.
- [ ] Every Management API call of the Angular service has a function with the same path and payload.
- [ ] No prototype addition without an endpoint is in the code. Each one is recorded.
- [ ] The tests of section 4.5 exist and pass.
- [ ] `yarn typecheck && yarn lint && yarn test && yarn build` pass.
- [ ] The browser check is done and recorded. The network calls match Angular.
- [ ] The inventory entry is updated and the markdown regenerated.
- [ ] The `code-review` skill ran. Class D: the `graphene-review` skill ran.
- [ ] One commit on `spike/am-gamma`, pushed.

## 10. Output

The final message to the user holds:

- The unit id and the commit SHA.
- The pages ported, as a table: route, console file, status.
- The endpoints used.
- The permission strings and licence features enforced.
- The prototype additions left out, with reasons.
- The verification evidence: the four commands, the browser journeys, the network comparison, the Playwright result.
- Anything the run could not do, and why.
