# Existing patterns in the React console

Phase 0 output of the migration plan. Date: 2026-10-20. Source: `gravitee-am-ui-next/` at commit `a3055466e4` on
`spike/am-gamma`, 43 source files, 6 test files, 17 tests.

This document records what the console does today. It proposes no replacement. `AGENTS.md` in this folder holds the
condensed rules. The conversion rules are in `conversion-rules.md`.

## 1. Shell and host integration

| Concern         | Pattern                                                                                                                                 | File                                                 |
| --------------- | --------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------- |
| Entry           | `loadConfig()` reads `constants.json` and `build.json`, then renders `<App />` under `StrictMode`                                       | `src/main.tsx`                                       |
| Providers       | `QueryClientProvider`, `TooltipProvider`, `LayoutSlotsProvider`, `BrowserRouter`, `Toaster` bottom right                                | `src/app/App.tsx`                                    |
| Shell           | `AppLayout` with `AppSidebar`, `ContentHeader`, `AppContextBar`, `TopNavUser`. Slots come from `useLayoutSlots()`                       | `src/app/Shell.tsx`                                  |
| Main menu       | `MAIN_NAV_GROUPS`: Domain items then Organization. The menu acts on the domain in the URL, else the last visited, else the first        | `src/app/navigation.ts`, `Shell.tsx`                 |
| Domain switcher | `DomainSwitcher` above the menu. A switch keeps the active section                                                                      | `src/app/components/DomainSwitcher.tsx`              |
| Gamma switcher  | `buildApps()` merges the Gamma module list with a local product catalog. A click leaves for gamma-console with `window.location.assign` | `src/app/gamma-apps.tsx`, `src/lib/api/gamma-api.ts` |
| Gamma config    | Optional `gamma` block in `constants.json`: `consoleUrl`, `apiUrl`, `organizationId`, `environmentHrid`. Absent means AM alone          | `src/config/app-config.ts`, `public/constants.json`  |
| Placeholders    | Every menu item without a page renders `PlaceholderPage` or `DomainPlaceholderPage`                                                     | `src/app/components/PlaceholderPage.tsx`             |

The host, gamma-console, sends the browser to `/management/auth/cockpit?token=...`. The Management API sets the session
cookie and redirects to `/environments/<hrid>`. The console must keep that route.

## 2. Routing

One route tree. `App.tsx` mounts each area's `routes.tsx`, and pages load through `lazyPage()`. No route objects.

```
/login, /login/callback, /logout, /logout/callback
/*  → SessionGate
  /environments/:envHrid                → EnvironmentScope
    (Shell)
      index → domains
      domains                           → DomainsPage
      domains/:domainHrid               → DomainScope
        index → dashboard
        dashboard, agents, mcp-servers, authorization, alerts   → placeholders
        applications                    → ApplicationsPage
        applications/:applicationId     → ApplicationLayout
          overview, design/flows, settings/general, + placeholders from APPLICATION_NAV_GROUPS
        settings                        → DomainSettingsLayout
          providers, providers/:id, forms, flows, + placeholders from DOMAIN_SETTINGS_NAV_GROUPS
      organization                      → OrganizationLayout, all placeholders
  /*  → RootRedirect (first environment)
```

Conventions:

- Route params: `envHrid`, `domainHrid`, `applicationId`, `identityProviderId`. Domains resolve by hrid, entities by id.
- The settings and application sections derive their routes from the nav groups. A built page is listed in a `Set`
  and the rest fall through to a placeholder.
- Navigation keys with a slash, such as `settings/general`, are nested paths under the section base.

## 3. Session scopes

| Scope              | Loads                                                      | Exposes                                                      | File                              |
| ------------------ | ---------------------------------------------------------- | ------------------------------------------------------------ | --------------------------------- |
| `SessionGate`      | `GET /user`, then `GET /organizations/:org/environments`   | `useSession(): { user, environments }`                       | `src/lib/session/session.tsx`     |
| `EnvironmentScope` | Nothing. Matches `:envHrid` against `environments[].hrids` | `useCurrentEnvironment(): Environment`                       | `src/lib/session/environment.tsx` |
| `DomainScope`      | `GET .../domains/_hrid/:hrid`                              | `useCurrentDomain(): { domain, ref, basePath, domainsPath }` | `src/lib/session/domain.tsx`      |

`ref` is a `DomainRef`: `organizationId`, `environmentId`, `domainId`. API functions take it instead of the
`:organizationId` and `:environmentId` placeholders the Angular interceptor rewrites.

Each scope renders a `Spinner` while it loads and a one-line `text-destructive` paragraph on error. An unknown
environment navigates to `/`.

## 4. Authentication

- `/login` calls `redirectToLogin()`: it stores the return path in `sessionStorage` and sends the browser to
  `{baseURL}/auth/authorize?redirect_uri=.../login/callback`.
- `/login/callback` calls `takeReturnTo()` and navigates there.
- `/logout` sends the browser to `{baseURL}/auth/logout?target_url=.../logout/callback`.
- The `QueryCache` `onError` in `App.tsx` calls `redirectToLogin()` on any `HttpError` 401. `SessionGate` keeps its
  spinner while the browser leaves.
- Queries do not retry on a 4xx. They retry once on a 5xx. `refetchOnWindowFocus` is off.

`src/lib/session/permissions.tsx` holds the permission model. `SessionGate` grants `CurrentUser.permissions` from
`GET /user`. `EnvironmentScope`, `DomainScope` and `ApplicationLayout` each load their `/members/permissions` list
through `LoadPermissionScope` before their pages render, as the Angular `AuthGuard` does. `usePermissions()`,
`<RequirePermission>` and `<Can>` read the union. The protected resource list is not loaded yet.
`src/lib/session/license.tsx` loads the licence once in `SessionGate`. `useLicense()`, `<RequireLicense>` and
`<LicenseGuard>` read it, with the feature names in `license-features.ts`.

## 5. HTTP and API layer

`src/lib/api/http.ts`:

- `http<T>(path, init)` prefixes `appConfig().baseURL`, sends `credentials: 'include'`, sets `Content-Type` for a
  body, echoes the last `X-Xsrf-Token`, and throws `HttpError(status, message)` on a non-2xx. A 204 returns
  `undefined`.
- `httpText(path)` returns the body as text, for AsciiDoc documentation.

`src/lib/api/` holds one file per area: `domains.ts`, `applications.ts`, `identity-providers.ts`, `forms.ts` and
`flows.ts`, each with its types and functions. `management-api.ts` keeps the user, the environments and `Page<T>`.
`paths.ts` holds `DomainRef`, `domainPath(ref)` and `searchParam(query)`. Functions are plain `async` functions that
return the payload. Paths follow the Angular services, under `/organizations/:org/environments/:env/domains/:id`.

Conventions in the functions:

- A list takes `(ref, page, size, query)` and sends `page`, `size` and `q=*text*`. Pages are 0-based here.
- An update sends only the fields the Angular service sends, for example `updateIdentityProvider` picks seven fields.
- A create-or-update pair is one function that branches on `id`, as `saveForm` does.
- `getIdentityProviderSchema(type)` and `getPolicySchema(id)` return the raw plugin schema for the adapter.

Entity types come from the jest SDK models in `../gravitee-am-test/api/management/models/`, generated from
`../docs/mapi/openapi.yaml`. The console imports them as `@am-models/<Model>`. Each model starts with `@ts-nocheck`,
because its generated code fails the console's `noUnusedLocals` check. The spec marks almost
every response field optional, so `ApiModel<Model, 'id' | 'name'>` from `src/lib/api/model.ts` makes the fields the API
always sends required. Where the spec is wrong, the type overrides the field with `Omit`, with a comment: response enums
are lowercase on the wire, and some endpoints name the wrong schema. A type stays hand-written when the spec has no
schema, as for the plugin lists and `GET /user`, or when the model has an `any` index, as `ClientMetadata` has.
Decision: generated models per area, checked against the Angular service payload.

## 6. Data fetching and mutations

TanStack Query 5 throughout. No other state store.

| Case            | Pattern                                                                                                                                              | Example                                             |
| --------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------- |
| Server list     | `useQuery` keyed by `['domains', org, env, page, pageSize, query]` with `placeholderData: keepPreviousData`                                          | `DomainsPage.tsx`                                   |
| Detail          | `useQuery` keyed by `['application', ref, id]`                                                                                                       | `ApplicationPage.tsx`                               |
| Static data     | `staleTime: Infinity` for schemas, policies, the module list                                                                                         | `IdentityProviderSettingsPage.tsx`, `FlowsPage.tsx` |
| Dependent query | `enabled: !provider.system`                                                                                                                          | `IdentityProviderSettingsPage.tsx`                  |
| Save            | `useMutation`; `onSuccess` sets the detail key, invalidates the list key, resets the form, `toast.success`; `onError` `toast.error` with the message | `ApplicationPage.tsx`                               |
| Toggle          | A `useMutation` per switch, `disabled={isPending}`                                                                                                   | `ApplicationFlowsPage`                              |
| Replace list    | `setQueryData` with the PUT response                                                                                                                 | `FlowsPage.tsx`                                     |

Query keys include `ref` as an object. Keys are compared structurally, so this is stable.

## 7. Pages

### 7.1 Server list

`DomainsPage.tsx` is the reference. Parts: `PageHeader`, `DataTable` with `columns: ColumnDef<T, unknown>[]`,
`serverSide`, `loading={isFetching}`, `skeletonCount`, `enableColumnVisibility`, `toolbar={<SearchField />}`,
`emptyMessage` with a `no-results` `DataTableEmptyState`, and `pagination` with 1-based `page`. When
`totalCount === 0` and there is no search text, a `first-use` `DataTableEmptyState` replaces the table. Cells use
`BadgeCell`, `DateCell`, `CopyableCell` and `Link`.

`ApplicationsPage.tsx` follows the same shape. `IdentityProvidersPage.tsx` filters in the browser because the API
returns one list.

### 7.2 Detail with sections

`ApplicationPage.tsx` is the reference. A layout component loads the entity, then a child component calls
`useSectionSidebar({ title, badges, groups, base, crumbs })` and renders `<Outlet context={entity} />`. Each section
page reads the entity with `useOutletContext<T>()` and renders `PageHeader` plus its content. Sections live in the same
file today. A larger area should keep one file per section page.

### 7.3 Plain form

The `Settings` section in `ApplicationPage.tsx`: `useForm` with `defaultValues`, `register` with a `required`
message, `Controller` for `Switch`, `Field` with `data-invalid`, `FieldLabel`, `FieldError`, the save button disabled
until dirty, `form.reset` after save.

### 7.4 Plugin schema form

`IdentityProviderSettingsPage.tsx`: the raw schema goes through `adaptAmSchema()` and the result feeds `JsonSchemaForm` with
`name="config"`. The resolver is `composeResolvers(nameResolver, jsonSchemaResolver(schema, { basePath: 'config' }))`.
Values are `{ name, config }` as a `type` alias. The configuration string is parsed on load and stringified on save.
Unsupported widgets are listed in an `Alert`. System providers show a read-only note.

`src/lib/forms/am-schema.ts` drops the angular-schema-form keywords, maps `widget` to `format`, `sensitive` to `password`,
`readonly` to `readOnly`, and `titleMap` to `gioConfig.enumLabelMap`. It recurses into `properties`, `items` and the
`oneOf`, `anyOf` and `allOf` branches.

### 7.5 Plugin creation

`NewIdentityProviderPage.tsx` on `PluginWizard`: step 1 lists the plugins with their icons, a filter on the name,
the display name and the labels, and a lock on a plugin whose `feature` the licence lacks. A click on a locked plugin
opens the upgrade dialog. Step 2 renders the name, the host fields and the schema form. The page POSTs, toasts and
opens the new entity.

### 7.6 Code editor

`LoginFormPage.tsx`: Graphene `CodeEditor` in HTML mode with a `Switch` for `enabled`. The first save POSTs, later saves
PUT.

### 7.7 Full-bleed editor

`FlowsPage.tsx`: `PolicyStudio` at organization scope with `flowModel={AM_FLOW_MODEL}`. `am-flows.ts` maps AM flows to
studio flows and back. The page takes `applicationId`, `types`, `description` and `actions`, so the application flows
page reuses it with the inherit switch. Hidden flow types are merged back before the PUT.

## 8. Layout hooks

- `useBreadcrumbs(segments)` calls `useLayoutConfig({ breadcrumbs })` with `buildLinearBreadcrumbs(navigate, ...)`.
  Dependencies are `JSON.stringify(segments)`.
- `useSectionSidebar(options)` sets `viewMode: 'context'`, the `ContextSidebar` with a header of title and badges,
  the `ContextToggleButton` as `leading`, and the breadcrumbs unless a detail page below an item owns them. The active
  item is the longest key that prefixes the path.
- `PageHeader` renders `<h1>` and an optional description and actions row.
- `SearchField` is an `InputGroup` with a 300 ms debounce.

## 9. Styling

- `src/styles.css`: `@import 'tailwindcss'`, the Graphene `tailwind-theme`, then the Graphene `styles` last, then
  `@source './**/*.{ts,tsx}'`. The order matters: a JavaScript import of the Graphene CSS lands in the vendors chunk
  and loads before the app CSS.
- Tailwind utility classes inline. No CSS modules, no styled components, no SCSS.
- Colour tokens through Graphene classes: `text-muted-foreground`, `text-destructive`, `border-border`,
  `ring-foreground/10`.
- Fonts from `@gravitee/graphene-core/fonts` in `main.tsx`.

## 10. Error and loading states

- A scope or a page shows `<Spinner />` while its query has no data and no error.
- A failed query renders `ErrorPanel` from `src/app/components/`, with a sentence that names what did not load and the
  `HttpError` message under it. A list page renders it in place of the table.
- A detail page or scope renders `NotFoundPanel` when `isNotFound(error)` from `http.ts` is true, with a link back to
  the list. Reference: `ApplicationLayout` in `src/features/applications/ApplicationPage.tsx`.
- A mutation error shows a `toast.error` with the `HttpError` message.
- There is no 404 route for an unknown path.

## 11. Tests

- Vitest 4 with `globals: true`, jsdom, `TZ=UTC`, files under `src/**/__tests__/*.test.{ts,tsx}`.
- `src/test/setup.ts` adds `jest-dom`, a `ResizeObserver` mock, pointer capture and `scrollIntoView` no-ops, and
  `matchMedia`.
- A page test renders under `QueryClientProvider` with `retry: false`, `MemoryRouter`, `LayoutSlotsProvider` and
  `TooltipProvider`. It mocks the API module and the session hooks with `vi.mock`. It queries by role and name and
  asserts the API call arguments.
- Helpers have plain unit tests: `am-schema.test.ts`, `am-flows.test.ts`, `auth.test.ts`, `http.test.ts`,
  `gamma-apps.test.tsx`.

## 12. Lint, format, build

- ESLint flat config: the Graphene config plus `eslint-config-prettier`. It ignores the config files.
- Prettier: 4 spaces, single quotes, width 140, trailing commas, `arrowParens: avoid`.
- `tsconfig.json` extends `@gravitee/graphene-core/tsconfig`, strict, `moduleResolution: bundler`.
- rsbuild with `pluginReact`, `assetPrefix: './'`, `public/` copied. Dev server on `:4201` proxies `/management`
  with `changeOrigin: false` and `/gamma` with `changeOrigin: true`.
- Maven: `pom.xml` runs `yarn install` and `yarn build`, and `assembly.xml` zips `dist/`, as `gravitee-am-ui` does.

## 13. Gaps that block a factory

| Gap                 | Effect today                  |
| ------------------- | ----------------------------- |
| One navigation file | Parallel agents collide on it |
| No README           | Setup lives in the spike plan |

These are the Stage 1 stories in `execution-plan.md`.
