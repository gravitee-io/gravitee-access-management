---
name: migrate-area
description: Port one unit of the Angular AM console to the React console gravitee-am-ui-next, with the prototype page as the UI, the Angular code as the behaviour, and one commit on spike/am-gamma at the end. Use when the user runs /migrate-area <unit>, says "port <unit>", "migrate <unit> to React", or asks to continue the AM console migration. The unit id comes from gravitee-am-ui-next/docs/migration/inventory.json.
---

# Migrate one unit

You port one unit. A unit is one entry in `gravitee-am-ui-next/docs/migration/inventory.json`. You follow the steps
below in order. You do not make a design decision. Where a step says "stop", you stop and report.

The detail of every step is in `gravitee-am-ui-next/docs/migration/migration-playbook.md`. The rules are in
`gravitee-am-ui-next/docs/migration/conversion-rules.md`. Read both once per run.

## Parameter

`$ARGUMENTS` is the unit id, for example `scopes`. With no argument, pick the unit with the lowest `order` whose
`status` is `not-started` and whose `blockers` list is empty, and say which one you picked.

## Step 0: preconditions

Run from `gravitee-am-ui-next/`:

```bash
git -C .. status -sb
yarn typecheck && yarn lint && yarn test
```

Stop if the branch is not `spike/am-gamma`, the tree is not clean, or a command fails.

Read the unit entry. Stop if the unit does not exist, its `status` is `done`, or `blockers` is not empty.

Check that these exist. Stop and write the missing one into the unit's `blockers` if not:

- `src/lib/session/permissions.tsx`, when the unit has `permissions`.
- `src/lib/session/license.tsx`, when the unit has `licenseFeatures`.
- `src/app/components/ConfirmDialog.tsx`, when a page deletes.
- `src/app/components/PluginWizard.tsx`, when a page's Angular folder has `creation/` with `step1` and `step2`.
- `src/app/components/SaveBar.tsx`, when a page is a settings form.
- `src/app/components/MembershipsPanel.tsx`, when a page lists members.

## Step 1: read

Read, in this order, and do not write code before the end of the list:

1. `gravitee-am-ui-next/AGENTS.md`.
2. The unit entry: every page's `path`, `angularFile`, `prototypePages`, `effectivePerms`, `licenseFeature`,
   `resolvers`, `menu` and flags, plus the unit `notes`.
3. For each page: the Angular component `.ts` and `.html` under `gravitee-am-ui/src/app/`, the service it injects
   under `services/`, the resolvers under `resolvers/`, and the route block in `app-routing.module.ts`.
4. For each prototype page: the file under `../../gamma-baby/src/modules/am/` and its `prototypeNotes` in
   `../../gamma-baby/source-refs.json`.
5. The console reference page for each shape, from the playbook section 2 table.
6. `conversion-rules.md` sections 1 and 3.
7. The Angular Playwright spec and page object under `gravitee-am-test/playwright/`, when one names the unit.

## Step 2: brief

Write the brief into the unit's `notes` in `inventory.json`. It has six lists: pages as
`Angular route → prototype page → console file`; endpoints as `METHOD path` from the Angular service; permission
strings per page and per action, with AM names such as `domain_scope_create`; licence features; prototype additions
to leave out, each with `no endpoint` or `not in AM`; Angular behaviour the prototype dropped, which you add back.

Stop if a question changes which pages or endpoints the unit has.

## Step 3: API module

Create `src/lib/api/<unit>.ts`. One `interface` per entity with `readonly` fields and no `Am` prefix. One function
per Angular service method the unit uses, with the same path, the same parameters and the same payload fields. Build
paths with the shared `domainPath` helper.

Check: every endpoint in the brief has a function, and no function has an endpoint outside the brief. Run
`yarn typecheck`.

## Step 4: pages

For each page, in the order list, create, detail:

1. Copy the prototype file to `src/features/<unit>/<Name>.tsx`. Keep its JSX, copy, columns, empty states and
   dialogs. Add the licence header.
2. Apply `conversion-rules.md` section 1, rule by rule.
3. Remove every prototype addition in the brief.
4. Add every dropped Angular behaviour in the brief, with section 2 of the rules.
5. Order the branches: error, not found, empty, data.
6. Use the console's `PageHeader`, `ConfirmDialog`, `ErrorPanel` and `NotFoundPanel`.

Check with a search over `src/features/<unit>/`: no `gamma-baby`, `useJson`, `send(`, `amUrls`, `/am/`,
`useHasPermission`, `buildEnvPath`, `useScenario`, `@mirrors`, no permission string with `-` or `:`, no `interface`
as the generic of a `useForm` that uses `jsonSchemaResolver`. Run `yarn typecheck`.

## Step 5: routes

Create `src/features/<unit>/routes.tsx` with the unit's `<Route>` elements, the Angular path segments and `lazyPage()`
for each page. Mount it in `src/features/settings/routes.tsx` for a domain setting, in
`src/features/organization/routes.tsx` for an organization unit, or in `src/app/App.tsx` for a top-level section, in
place of the unit's placeholder. Confirm the nav key in `src/app/navigation.ts`.
Set `useBreadcrumbs` on pages and `useSectionSidebar` on layouts, with absolute paths from `basePath`.

Check: every `path` of the unit's inventory entry resolves to a route, and the placeholder is gone.

## Step 6: permissions and licence

Wrap each route whose `guards` has `AuthGuard` in `<RequirePermission anyOf={perms}>` from
`src/lib/session/permissions.tsx`, with that route's own `perms` list. Gate each create, update and delete action with
`<Can all={[...]}>` or `usePermissions().has()` and the AM name from the brief. Wrap each route with a `licenseFeature` in the licence route
element and guard the action's click. Hide a `hideInCloudMode` page when the installation is managed.

Check: a test renders the page without the permission and asserts the action is absent.

## Step 7: tests

Under `src/features/<unit>/__tests__/`, with the harness of `src/features/domains/__tests__/DomainsPage.test.tsx`:
list renders with the call arguments, search, first use, save with the Angular payload fields, gated action, delete
through the confirm dialog, and plain tests for any helper. Assert the Angular behaviour, never a prototype addition.

## Step 8: verify

```bash
yarn typecheck && yarn lint && yarn test && yarn build
```

Stop if one fails and you cannot fix it inside the unit.

Then in the browser against the local stack: `yarn serve`, open each page, run list, search, create, edit, save and
delete. Record the Management API calls per action. Run the same actions on the Angular console on `:4200` and compare
method, path and payload fields. Write the result into the unit's `notes`.

Class B and above: run or add the unit's Playwright spec. Class D: run the `graphene-review` skill on the unit.

## Step 9: record

In `inventory.json`: each page `status: done` with `reactFiles`, or `out-of-scope` with `outOfScope`; the unit
`status`, `angularCommit` from `git -C .. rev-parse --short=10 HEAD`, `notes`, empty `blockers`. Then:

```bash
node scripts/migration/build-inventory.mjs
```

## Step 10: review and commit

Run the `code-review` skill on the diff and fix what it finds. Run the `commit` skill with the subject
`feat(ui-next): port <unit>`, the inventory files included. Push.

## Do not

- Redesign beyond the prototype page.
- Change an endpoint, a payload, a validation rule or a permission.
- Add a dependency.
- Build a kit part or a hook inside the run. Record it as a blocker and stop.
- Use a Gamma-style permission string, a `/am` route prefix, `useJson`, or a prototype stub.
- Copy a Graphene component, or replace one with raw HTML.
- Touch a built page outside the unit.
- Skip a check, or commit with a failing command.

## Report

End with: the unit id and the commit SHA; a table of pages with route, console file and status; the endpoints; the
permission strings and licence features enforced; the prototype additions left out with reasons; the verification
evidence; and anything you could not do, with the reason.
