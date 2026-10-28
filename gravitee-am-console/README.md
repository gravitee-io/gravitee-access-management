# Gravitee.io AM - Console (React)

`gravitee-am-console` is the React console of Gravitee Access Management. It replaces the Angular console in
[`../gravitee-am-ui/`](../gravitee-am-ui/). The team ports the Angular console to this folder one story at a time,
under the epic [AM-7349](https://gravitee.atlassian.net/browse/AM-7349).

## Prerequisites

- Node 24. The version is in `.nvmrc`.
- Yarn 4.1.1. The release is in `.yarn/releases/`.
- An AM Management API on `http://localhost:8093`, for `yarn serve`. The `local-stack` script in
  [`../docker/local-stack/`](../docker/local-stack/) can start one.

## Get started

Run each command from this folder.

```bash
yarn install
yarn serve
```

Then open `http://localhost:4201`. The dev server proxies `/management` to the Management API. The Management API
shows its login form, then sends you back to the console.

If the Management API runs on a different URL, set `AM_MAPI_URL`:

```bash
AM_MAPI_URL=http://localhost:9093 yarn serve
```

## Commands

| Command                                          | Action                                              |
| ------------------------------------------------ | --------------------------------------------------- |
| `yarn serve`                                     | Start the dev server on port 4201                   |
| `yarn typecheck`                                 | Run `tsc` with no output                            |
| `yarn lint`                                      | Run the typecheck, ESLint, and the Prettier check   |
| `yarn lint:eslint:fix && yarn lint:prettier:fix` | Fix the ESLint and Prettier issues                  |
| `yarn test`                                      | Run the Vitest tests one time                       |
| `yarn test:watch`                                | Run the Vitest tests in watch mode                  |
| `yarn build`                                     | Run the typecheck, then build the production bundle |

Before you commit, make sure that all four checks pass:

```bash
yarn typecheck && yarn lint && yarn test && yarn build
```

The console is not a Maven module, so `mvn install` from the repository root does not build it. CircleCI runs the
four checks in the `console_build` job.

## Stack

| Part    | Choice                                          |
| ------- | ----------------------------------------------- |
| UI      | React 19, `@gravitee/graphene-core`, Tailwind 4 |
| Routing | react-router 7                                  |
| Data    | TanStack Query 5                                |
| Forms   | react-hook-form 7 with Graphene `Field` parts   |
| Editors | Monaco through the Graphene `CodeEditor`        |
| Tests   | Vitest 4, Testing Library, jsdom                |
| Build   | rsbuild                                         |

## Project layout

```
src/
  app/              route tree (App.tsx), AM layout (Shell.tsx), main menu, shared components
  config/           runtime config loader
  features/<area>/  one folder per feature area: routes.tsx, pages, tests
  lib/api/          Management API client, one file per area
  lib/routing/      lazyPage() for code-split pages
  lib/session/      user, environment, permissions, licence, preferences
  lib/layout/       breadcrumbs and section sidebar hooks
  lib/forms/        form value helpers, for example durations
  test/             Vitest setup
public/             files copied as-is into dist/
```

The entity types come from the generated Management API models in
[`../gravitee-am-test/api/management/models/`](../gravitee-am-test/api/management/models/). Code imports them as
`@am-models/<Model>`.

## Runtime config

The console reads two files from `public/` before the first render:

- `constants.json` holds `baseURL`, the root of the Management API. The default is `/management`.
- `build.json` holds `version`.

To point a deployed console at a different Management API, edit `constants.json` in `dist/`. You do not need a new
build.

## Contribute

- Read [`AGENTS.md`](AGENTS.md) for the rules on structure, data access, layout, permissions, and tests.
- To port a page from the Angular console, follow the `migrate-area` skill in
  [`../docs/agent-standards/skills/migrate-area/`](../docs/agent-standards/skills/migrate-area/).
- Start each new file with the Apache licence header.
- Open one pull request to `master` for each story. Name the branch `<key>-<slug>`.

## Component kit

The shared parts in `src/app/components/` have a page that shows each part with its live state. Use it to try a part
or to test it by hand.

1. Start the AM local stack with `../docker/local-stack/local-stack.sh up`. The stack runs the Management API, but not
   this console.
2. Start the dev server with `yarn serve`.
3. Sign in as `admin` / `adminadmin`, then open `http://localhost:4201/environments/default/dev/kit`.

Only the dev server serves this page. The production build drops the route.

## End-to-end tests

The Playwright specs for this console go in `../gravitee-am-test/playwright/tests/console-next/`. The
`console-next` Playwright project runs them against `http://localhost:4201`.

1. Start the dev server with `yarn serve`.
2. In `../gravitee-am-test`, run `npm run pw:next`.

## Licence

Apache License 2.0. See the [`LICENSE.txt`](../LICENSE.txt) file at the repository root.
