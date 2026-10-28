# AGENTS.md — gravitee-am-console

Rules for agents that work under `gravitee-am-console/`, the React console of Access Management. This file adds to the
repository `AGENTS.md` at the root. On a conflict, this file wins for files under this folder. The root rules on
safety, the licence header, US English and the workflow still apply.

The console is a port of the Angular console in `../gravitee-am-ui/`, story by story. Port a story
with the `migrate-area` skill in `../docs/agent-standards/skills/migrate-area/`. Its conversion rules are in
`../docs/agent-standards/skills/migrate-area/references/conversion-rules.md`. Read them before you port a page.

## 1. Commands

Run every command from this folder. Node 24 (`.nvmrc`), Yarn 4.1.1 from `.yarn/releases/`.

```bash
yarn install
yarn serve        # dev server on :4201, proxies /management to AM_MAPI_URL (default :8093)
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

The console is not a Maven module. `mvn install` from the root does not build it. Build it with yarn. CircleCI runs the
same four commands in the `console_build` job when a push changes this folder or the generated models it imports.

## 2. Stack

| Part           | Choice                                                                                          | Where                                               |
| -------------- | ----------------------------------------------------------------------------------------------- | --------------------------------------------------- |
| UI             | React 19, `@gravitee/graphene-core` 3.23.1, Tailwind 4                                          | `package.json`, `src/styles.css`                    |
| Routing        | react-router 7, one `BrowserRouter`, nested routes, one `routes.tsx` per area                   | `src/app/App.tsx`, `src/features/<area>/routes.tsx` |
| Data           | TanStack Query 5                                                                                | `src/app/App.tsx` for the client, pages for queries |
| Forms          | react-hook-form 7 with Graphene `Field` parts                                                   | Graphene peer dependency                            |
| Editors        | Monaco through Graphene `CodeEditor`. The story that first uses it adds the worker setup        | Graphene peer dependency                            |
| Tests          | Vitest 4, Testing Library, jsdom, `src/test/setup.ts`                                           | `src/**/__tests__/`                                 |
| Build          | rsbuild, `assetPrefix: './'`, `public/` copied to `dist/`                                       | `rsbuild.config.ts`                                 |
| Runtime config | `constants.json` with `baseURL`, plus `build.json` with `version`, read before the first render | `src/config/app-config.ts`                          |
| Format         | Prettier: 4 spaces, single quotes, width 140, trailing commas                                   | `.prettierrc`                                       |

Pin Graphene to the version gamma-console uses.

## 3. Structure

```
src/
  app/              App.tsx (route tree), Shell.tsx (AM layout), navigation.ts, components/
  config/           app-config.ts
  features/<area>/  routes.tsx, pages, area helpers, __tests__/
  lib/api/          http.ts, auth.ts, paths.ts (DomainRef, domainPath), model.ts (ApiModel), management-api.ts, one file per area
  lib/routing/      lazyPage.tsx
  lib/session/      session.tsx, environment.tsx, permissions.tsx, license.tsx, preferences.ts, domains.ts
  lib/layout/       useBreadcrumbs.ts, useSectionSidebar.tsx
  lib/forms/        duration.ts
  test/             setup.ts
```

- One folder per feature area under `src/features/`. The area name follows the Angular folder, for example `scopes`,
  `certificates`, `users`.
- Shared parts used by two or more areas go in `src/app/components/` or `src/lib/`. Do not import from a sibling area.
- Each area exports its `<Route>` elements from `routes.tsx`. A page loads through `lazyPage()`, so the area lands in
  its own chunk. `App.tsx` mounts each area in place of its `PlaceholderPage`.
- Tests sit in `__tests__/` beside the code they test.
- Every new file starts with the Apache licence header. The root `license:check` scans this folder.

## 4. Session, routes and paths

- The route shape is `/environments/:envHrid/domains/:domainHrid/<section>/...` and
  `/environments/:envHrid/organization/...`. Do not add an `am` prefix. A URL holds the domain `hrid`. An API call
  uses the domain `id`.
- `/` opens the default domain on the first visit, else the environment. `/environments/:envHrid` opens the first
  domain, else shows the creation empty state. `/404` and any unknown path show the 404 page.
- `SessionGate` loads `GET /user`, the environments and the licence once. `useSession()` gives `user` and `environments`.
- `EnvironmentScope` resolves `:envHrid` and loads the environment permissions. `useCurrentEnvironment()` gives the
  environment with its `id`.
- The domain scope (`:domainHrid`, `useCurrentDomain()`) comes with the domains story.
- The login chain is server-side: `redirectToLogin()` in `src/lib/api/auth.ts`. A 401 from any query starts it
  through the `QueryCache` in `App.tsx`. Pages do not handle 401.

## 5. Data access

- API functions and entity types live in `src/lib/api/<area>.ts`, one file per area. They call `http<T>(path, init)`
  from `http.ts`, which sends the session cookie, echoes `X-Xsrf-Token`, and throws `HttpError` with the status.
- Entity types are `ApiModel<Model, requiredKeys>` from `src/lib/api/model.ts`, over a generated model imported with
  `import type` from `@am-models/<Model>`. The models are the Jest SDK models in
  `../gravitee-am-test/api/management/models/`. Do not edit them.
- The generated models fail `noUnusedLocals` and import `cross-fetch`. So `tsconfig.json` turns off the two unused
  checks, ESLint reports unused code in `src/` instead, and `src/global.d.ts` declares `cross-fetch`.
- The generated models are the source of truth for shapes. Do not read `../docs/mapi/openapi.yaml` for them. Where a
  model is wrong, override the field in the console type with `Omit` and a comment, as `src/lib/api/license.ts` does.
  Response enums are lowercase on the wire, and a `uniqueItems` array is typed as a `Set`.
- Paths are the Management API paths the Angular service calls. Build them with `environmentPath()`, `domainPath(ref)`
  and `searchParam(query)` from `src/lib/api/paths.ts`. Do not invent an endpoint. Check the generated API classes in
  `../gravitee-am-test/api/management/apis/`.
- Order the branches: error, then not found, then empty, then data. Render `ErrorPanel` for an error and
  `NotFoundPanel` when `isNotFound(error)` is true.

## 6. Layout and design system

- Import every UI part from `@gravitee/graphene-core` and icons from `@gravitee/graphene-core/icons`. Never copy a
  Graphene component into this folder. Compose from Graphene primitives.
- Read `node_modules/@gravitee/graphene-core/USAGE_GUIDE.md` before you build a page shape you have not built before.
- A single page sets its breadcrumbs with `useBreadcrumbs([...])`. A section layout calls `useSectionSidebar()`.
- The main menu is `MAIN_NAV_GROUPS` in `src/app/navigation.ts`. A section with its own menu adds its groups there.
- Tailwind 4: write `border-border` and `divide-border`. Bare `border` and `divide-y` paint with `currentColor`.
- `src/styles.css` imports Graphene's CSS last. Do not import `@gravitee/graphene-core/styles` from TypeScript.
- Page titles are a plain `<h1>` inside `PageHeader`.
- A settings form saves through `SaveBar` from `src/app/components/`. Do not put a Save button at the foot of the form.

## 7. Permissions and licence

- Permission strings are AM names with underscores, from the Angular route `perms.only` list or the `hasPermission`
  directive, for example `domain_create`. Gamma-style strings such as `domain-scope:create` are not AM names.
- `src/lib/session/permissions.tsx`: `usePermissions()` gives `has(...names)` (all-of) and `hasAny(...names)`
  (any-of). `<RequirePermission anyOf>` wraps a route that Angular guards with `AuthGuard`. `<Can all>` gates a control.
  A scope loads its list through `LoadPermissionScope` before its pages render.
- `src/lib/session/license.tsx`: `useLicense().isMissing(feature)`, `<RequireLicense feature>` for a route,
  `<LicenseGuard feature>` for a control. Feature names are the `AmFeature` values in `license-features.ts`.

## 8. Tests

- Render a page under `QueryClientProvider` with `retry: false` and `MemoryRouter`. Mock the area's API module and the
  session hooks with `vi.mock`. Reference: `src/features/environment/__tests__/EnvironmentPage.test.tsx`.
- Assert the Angular behaviour: the function called, its arguments, the rendered content, the empty states, the toast.
  Do not assert a prototype-only behaviour.
- Query by role and accessible name. Do not query by class.
- End-to-end specs go in `../gravitee-am-test/playwright/tests/console-next/`. Start `yarn serve` on `:4201`, then run
  `npm run pw:next` in `../gravitee-am-test`.

## 9. Commits

- One story per branch and per pull request to `master`, named `<key>-<slug>`.
- The four verification commands pass at every commit.
- Subject in the house style, for example `feat(console): <story summary>`. Ticket key in the footer.
