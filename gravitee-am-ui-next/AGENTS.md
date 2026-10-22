# AGENTS.md — gravitee-am-ui-next

Rules for agents that work under `gravitee-am-ui-next/`, the React console of Access Management. This file adds to the
repository `AGENTS.md` at the root. On a conflict, this file wins for files under this folder. The root rules on
safety, the licence header, US English and the workflow still apply.

The console is a port of the Angular console in `../gravitee-am-ui/`. The port follows the migration plan and the
documents under `docs/migration/`. Read `docs/migration/migration-playbook.md` before you port a page.

## 1. Commands

Run every command from this folder. Node 20 or later, Yarn 4.1.1.

```bash
yarn install
yarn serve        # dev server on :4201, proxies /management to AM_MAPI_URL (default :8093) and /gamma to GAMMA_API_URL (default :8083)
yarn typecheck
yarn lint         # typecheck, ESLint with the Graphene config, Prettier check
yarn lint:eslint:fix && yarn lint:prettier:fix
yarn test         # Vitest, jsdom, files under src/**/__tests__/*.test.{ts,tsx}
yarn build        # typecheck, then rsbuild production build into dist/
```

Before a commit, all four must pass:

```bash
yarn typecheck && yarn lint && yarn test && yarn build
```

## 2. Stack

| Part           | Choice                                                                                               | Where                                                                               |
| -------------- | ---------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------- |
| UI             | React 19, `@gravitee/graphene-core` 3.23.1, `@gravitee/graphene-charts` 3.23.1, Tier 2 Tailwind 4    | `package.json`, `src/styles.css`                                                    |
| Routing        | react-router 7, one `BrowserRouter`, nested routes, one `routes.tsx` per area                        | `src/app/App.tsx`, `src/features/<area>/routes.tsx`                                 |
| Data           | TanStack Query 5                                                                                     | `src/app/App.tsx` for the client, pages for the queries                             |
| Forms          | react-hook-form 7 with Graphene `Field` parts. Plugin schemas through Graphene `JsonSchemaForm`      | `src/features/applications/ApplicationPage.tsx`, `src/features/identity-providers/` |
| Editors        | Monaco through Graphene `CodeEditor`, workers set in `src/monaco-setup.ts`                           | `src/features/forms/LoginFormPage.tsx`                                              |
| Flows          | `@gravitee/graphene-policy-studio` from `../spike/vendor/`, with `AM_FLOW_MODEL`                     | `src/features/flows/`                                                               |
| Tests          | Vitest 4, Testing Library, jsdom, `src/test/setup.ts`                                                | `src/**/__tests__/`                                                                 |
| Build          | rsbuild, `assetPrefix: './'`, `public/` copied to `dist/`                                            | `rsbuild.config.ts`                                                                 |
| Runtime config | `constants.json` with `baseURL` and an optional `gamma` block, plus `build.json`, read before render | `src/config/app-config.ts`                                                          |
| Format         | Prettier: 4 spaces, single quotes, width 140, trailing commas                                        | `.prettierrc`                                                                       |

Pin Graphene to the version gamma-console uses. Bump the three Graphene packages together.

## 3. Structure

```
src/
  app/            App.tsx (mounts the areas), Shell.tsx, navigation.ts, gamma-apps.tsx, components/ (PageHeader, SearchField, ErrorPanel, NotFoundPanel, ForbiddenPanel, UpgradePanel, UpgradeDialog, ConfirmDialog, SaveBar, PluginWizard, TagInput, ValueListInput, MembershipsPanel, SettingToggle, DurationInput, PlaceholderPage, DomainSwitcher)
  config/         app-config.ts
  features/<area>/  routes.tsx, pages, <area>-specific helpers, __tests__/
  lib/api/        http.ts, auth.ts, paths.ts (DomainRef, domainPath), model.ts (ApiModel), management-api.ts (user, environments, Page), one file per area, gamma-api.ts
  lib/forms/      am-schema.ts (adaptAmSchema), duration.ts (the moment duration units and conversions)
  lib/routing/    lazyPage.tsx
  lib/session/    session.tsx (user, environments), environment.tsx (:envHrid), domain.tsx (:domainHrid, ref, basePath)
  lib/layout/     useBreadcrumbs.ts, useSectionSidebar.tsx
  test/           setup.ts
```

- One folder per feature area under `src/features/`. The area name follows the Angular folder under
  `gravitee-am-ui/src/app/domain/settings/`, for example `scopes`, `certificates`, `users`.
- Shared parts used by two or more areas go in `src/app/components/` or `src/lib/`. Do not import from a sibling area.
- Each area exports its `<Route>` elements from `routes.tsx`. A page loads through `lazyPage()`, so the area lands in
  its own chunk. `App.tsx` mounts the top-level areas. `src/features/settings/routes.tsx` mounts the domain settings
  units and `src/features/organization/routes.tsx` the organization units, each in place of its placeholder. A
  `routes.tsx` may import a sibling's `routes.tsx` for that purpose. A page may not.
- Tests sit in `__tests__/` beside the code they test.
- Every new file starts with the Apache licence header, as `src/lib/api/http.ts` does.

## 4. Session, routes and paths

- The route shape is `/environments/:envHrid/domains/:domainHrid/<section>/...` and `/environments/:envHrid/organization/...`.
  `/auth/cockpit` lands on `/environments/<hrid>`. Do not change this shape. Do not add an `am` prefix.
- `SessionGate` loads `GET /user` and the environments once. `useSession()` gives `user` and `environments`.
- `EnvironmentScope` resolves `:envHrid`. `useCurrentEnvironment()` gives the environment with its `id`.
- `DomainScope` resolves `:domainHrid`. `useCurrentDomain()` gives `domain`, `ref` (`DomainRef` with organization,
  environment and domain ids), `basePath` and `domainsPath`.
- Build every link and breadcrumb from `basePath` or `domainsPath`. A relative `..` follows the route tree, not the URL.
- The login chain is server-side: `redirectToLogin()` in `src/lib/api/auth.ts`. A 401 from any query starts it through
  the `QueryCache` in `App.tsx`. Pages do not handle 401.

## 5. Data access

- API functions and entity types live in `src/lib/api/<area>.ts`, one file per area. They call `http<T>(path, init)`
  from `http.ts`, which sends the session cookie, echoes `X-Xsrf-Token`, and throws `HttpError` with the status.
- Entity types are `ApiModel<Model, requiredKeys>` from `src/lib/api/model.ts`, over a generated model imported with
  `import type` from `@am-models/<Model>`. The models are the jest SDK models in `../gravitee-am-test/api/management/models/`.
  Do not edit them: `npm run update:sdk:mapi -- ../docs/mapi/openapi.yaml` in `../gravitee-am-test` writes them.
- The spec is wrong in places. Response enums are lowercase on the wire, for example `type: 'web'`. Override such a
  field with `Omit` and a comment. Keep a type hand-written when the spec has no schema for the response.
- Paths are the Management API paths the Angular service calls, under `/organizations/:org/environments/:env/domains/:id`.
  Build them with `domainPath(ref)` and `searchParam(query)` from `src/lib/api/paths.ts`. Do not invent an endpoint. Check `../docs/mapi/openapi.yaml` when in doubt.
- Query keys: `['<entity>', ref, id]` for a detail, `['<entities>', ref, page, size, query]` for a list.
- A mutation `onSuccess` sets the detail key with the response and invalidates the list key, then `toast.success`.
  `onError` shows `toast.error` with the message. See `ApplicationPage.tsx`.
- Graphene pagination starts at 1. The Management API starts at 0. Convert in the page: `page - 1` on the call.
- Most Management API lists have no sort parameter. Do not add sortable headers to a server-side table.
- Lists search with `q=*text*`, as the Angular console does. `SearchField` debounces 300 ms.
- Order the branches: error, then not found, then empty, then data. An outage must never render the first-use empty state.
  Render `ErrorPanel` for an error and `NotFoundPanel` when `isNotFound(error)` is true, both from `src/app/components/`.
  See `ApplicationLayout` in `ApplicationPage.tsx`.

## 6. Forms

- Plain forms: `useForm` with `defaultValues` from the entity, `register` for inputs, `Controller` for `Switch` and
  `Select`. `SaveBar` from `src/app/components/` gives Save and Discard, bound to `isDirty`. After a save, `form.reset` with the response.
- Validation rules and messages come from the Angular component or its reactive validators. Keep the messages.
- Plugin configuration: `adaptAmSchema()` from `src/lib/forms/am-schema.ts` translates the AM
  schema keywords, then `JsonSchemaForm` renders it with `jsonSchemaResolver`. Show the adapter's `unsupported` list
  in an `Alert`.
- A create page whose Angular folder has `creation/` with `step1` and `step2` uses `PluginWizard` from
  `src/app/components/`. The page loads the plugin list and the schema, and owns the POST. Host fields such as the
  domain whitelist go in `extraFields`. Reference: `src/features/identity-providers/NewIdentityProviderPage.tsx`.
- A string list, the Angular `mat-chip-grid`, uses `TagInput`.
- A string list with an input, an Add button and a table of rows, such as the Angular post-logout redirect URIs, uses
  `ValueListInput`. Pass `onRemoveRequest` when Angular confirms a removal, and open `ConfirmDialog` from it.
- An administrative roles page, the Angular `app-memberships-list` with its dialog, renders `MembershipsPanel`. The
  page passes the list, save and remove functions of its resource from `src/lib/api/<area>.ts`, maps the list through
  `toMembers()` from `src/lib/api/memberships.ts`, and passes the three `*_member_*` permissions as `canCreate`,
  `canUpdate` and `canDelete`.
- Form value shapes that a Graphene resolver validates must be `type` aliases. An `interface` does not satisfy
  `Record<string, unknown>`.

## 7. Layout and design system

- Import every UI part from `@gravitee/graphene-core` and icons from `@gravitee/graphene-core/icons`. Never copy a
  Graphene component into this folder. Never create a `components/ui/` folder. Compose from Graphene primitives.
- Read `node_modules/@gravitee/graphene-core/USAGE_GUIDE.md` for the list page, the context sidebar, the content width
  and the test harness before you build a page shape you have not built before.
- A list page: `PageHeader`, then `DataTable` with `serverSide`, `toolbar`, `pagination` and a `DataTableEmptyState`
  for no results. A first-use empty state replaces the table when the total is 0 and there is no search text.
  Reference: `src/features/domains/DomainsPage.tsx`.
- A detail with sections: a layout that calls `useSectionSidebar()` and renders `<Outlet context={entity} />`. Each
  section page reads the entity with `useOutletContext()`. Reference: `src/features/applications/ApplicationPage.tsx`.
- A single page sets its breadcrumbs with `useBreadcrumbs([...])`. A detail page below a section item sets its own.
- Tailwind 4: write `border-border` and `divide-border`. Bare `border` and `divide-y` paint with `currentColor`.
- `src/styles.css` imports Graphene's CSS last. Do not import `@gravitee/graphene-core/styles` from TypeScript.
- Do not add an `@source` line for a Graphene package to `styles.css`.
  `graphene-charts` ships no CSS, so `styles.css` lists the chart utilities in an `@source inline(...)` line.
  When you bump `graphene-charts`, check its `fill-*` and `stroke-*` classes against that list.
- Page titles are a plain `<h1>` inside `PageHeader`. Graphene styles it.

## 8. Permissions and licence

- Permission strings are AM names with underscores, taken from the Angular route `perms.only` list or the template
  `hasPermission` directive, for example `domain_scope_create`. The prototype's Gamma-style strings such as
  `domain-scope:create` are not AM names.
- `src/lib/session/permissions.tsx` holds the model. `usePermissions()` gives `has(...names)` for all-of and
  `hasAny(...names)` for any-of. `<RequirePermission anyOf={[...]}>` wraps a route that Angular guards with `AuthGuard`,
  with that route's own `perms.only` list. `<Can all={[...]}>` gates a button or a column, as `*hasPermission` does. A
  scope's list loads through `LoadPermissionScope` before its pages render. Reference: `ApplicationPage.tsx`.
- `src/lib/session/license.tsx` holds the licence, loaded once in `SessionGate`. `useLicense().isMissing(feature)` is
  false for no feature, which means an OSS plugin. `<RequireLicense feature>` wraps a route that Angular guards with
  `LicenseGuard`. `<LicenseGuard feature>` wraps a control, as the Angular `licenseGuard` directive does: a click opens
  `UpgradeDialog` instead of the control's handler.
- Licence features are the `AmFeature` names in `src/lib/session/license-features.ts`, generated from the Angular
  `components/gio-license/gio-license-data.ts`. A plugin's `feature` field names one.

## 9. Porting a page

The UI comes from the Gamma prototype page under `../../gamma-baby/src/modules/am/`. The behaviour comes from the
Angular files listed for that page in `../../gamma-baby/source-refs.json`. The plumbing comes from this console.

Replace in every prototype page:

| Prototype                                            | Console                                                                          |
| ---------------------------------------------------- | -------------------------------------------------------------------------------- |
| `useJson`, `send`, `amUrls`, `*Urls`                 | `useQuery`, `useMutation`, functions in `src/lib/api/`                           |
| `useHasPermission` and Gamma-style strings           | `usePermissions()` or `<Can>` and AM strings                                     |
| `useDomain()` from `DomainLayout`                    | `useCurrentDomain()`                                                             |
| `useEnvironment()` stub, `buildEnvPath()`            | `useCurrentEnvironment()`, `basePath`, `domainsPath`                             |
| `/environments/:env/am/domains/:domainId`            | `/environments/:envHrid/domains/:domainHrid`                                     |
| `LoadError`, `StateGap`, `ConfirmDialog` from `amUi` | The shared parts in `src/app/components/`                                        |
| Types from `mocks/data/am.ts`                        | Types in `src/lib/api/` over `@am-models/*`, checked against the Angular service |
| Graphene 3.3.0 props                                 | Graphene 3.23.1 props                                                            |

Leave out a prototype addition that has no Management API endpoint. Record it in the inventory entry.

Do not:

- Redesign beyond the prototype page.
- Change an endpoint, a payload, a validation rule or a permission.
- Add a dependency without approval.
- Refactor a built page outside the unit you port.
- Replace a Graphene part with raw HTML or a third-party component.

## 10. Tests

- Render a page under `QueryClientProvider` with `retry: false`, `MemoryRouter`, `LayoutSlotsProvider` and
  `TooltipProvider`. Mock the area's API module with `vi.mock`. Mock the session hooks. Reference:
  `src/features/domains/__tests__/DomainsPage.test.tsx`.
- Assert the Angular behaviour: the function called, its arguments, the rendered rows, the empty states, the toast.
  Do not assert a prototype-only behaviour.
- Cover per page: the list renders, the search calls the API with the text, one save sends the right payload, one
  gated action hides without the permission.
- Query by role and accessible name. Do not query by class.
- Pure helpers such as `am-schema.ts` and `am-flows.ts` get plain unit tests.
- End-to-end specs for this console go in `../gravitee-am-test/playwright/tests/console-next/`. The `console-next`
  project runs them against `yarn serve` on `:4201`, which must be up first: `npm run pw:next` in `../gravitee-am-test`. Follow
  `../gravitee-am-test/playwright/GUIDELINES.md`.

## 11. Commits

- Work lands on `spike/am-gamma` as direct commits. One ported unit or one foundation story per commit.
- The four verification commands pass at every commit.
- Subject in the house style: `feat(ui-next): port <unit>` or `feat(ui-next): <story>`. Ticket in the footer.
- The inventory entry under `docs/migration/` changes in the same commit as the code.
