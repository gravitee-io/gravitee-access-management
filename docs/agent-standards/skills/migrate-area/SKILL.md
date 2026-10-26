---
name: migrate-area
description: Port one story of the AM console migration to React (epic AM-7349) from the Angular console to gravitee-am-ui-next, with the Gamma prototype page as the UI, the Angular code as the behaviour, and one pull request to master at the end. Use when the user runs /migrate-area <story key>, says "port AM-XXXX", "migrate <area> to React", or asks to continue the AM console migration. The story keys are in references/stories.json.
---

# Migrate one story

You port one story of epic AM-7349. A story covers one or more units, and a unit is one Angular folder with its routes
and its prototype pages. You follow the steps below in order. You do not make a design decision. Where a step says
"stop", you stop and report.

## Inputs

| File                               | Holds                                                                                                                 |
| ---------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| `references/stories.json`          | Each story: key, summary, priority, `blockedBy`, `portable`, `units`, and the exact `pages` it covers                 |
| `references/units.json`            | Each unit: its pages with the Angular route, component, file, guards, permissions and prototype pages; `spikeNotes`   |
| `references/conversion-rules.md`   | How a prototype page and an Angular page become a console page. Read sections 1 and 3 once per run                    |

`units.json` is a frozen copy of the spike inventory. `spikeNotes` holds the spike's survey and, for a unit the spike
ported, the brief and the verified Management API calls and payloads. `spikeFiles` on a page lists the spike's React
files. Read a spike file with `git show origin/spike/am-gamma:gravitee-am-ui-next/<path>`. The spike code is a
reference. You build the story again on a story branch from `master`.

Read the data with `jq`, for example:

```bash
jq '.stories[] | select(.key == "AM-7942")' references/stories.json
jq '.units.certificates' references/units.json
```

`PROTO` is a checkout of `github.com/gravitee-io/gamma-baby` on the branch `prototype/am-on-gamma`, by default
`../gamma-baby` beside this repository. Stop and ask for its path if it is not there.

## Parameter

`$ARGUMENTS` is a story key, for example `AM-7942`. With no argument, pick the story with the highest priority, then the
lowest key, that is `portable`, is not done, and has all its `blockedBy` stories done. Say which one you picked.

A story is done when `git log origin/master --grep='<key>'` finds its commit. The commit footer names the key.

## Step 0: preconditions

Work on a story branch named `<key>-<slug>`, for example `AM-7942-plugin-settings`. If you are on `master`, create
it with `git switch -c <key>-<slug> origin/master`. Then run from `gravitee-am-ui-next/`:

```bash
git fetch origin master
git -C .. status -sb
yarn typecheck && yarn lint && yarn test
```

Stop if:

- The branch is not a story branch for this key.
- The tree is not clean, or the branch does not contain `origin/master`. Rebase it first.
- A command fails.
- The story does not exist, its `portable` is `false`, or it is done. AM-7933 and AM-7934 are not portable: they build
  the conventions this skill depends on.
- A story in `blockedBy` is not merged into `master`. Name it.

If every dirty file belongs to the story, a run of that story stopped part way. Report the first step it lacks, then
ask to resume it.

Check that the shared parts exist. Stop and name the missing part if not. AM-7933 and AM-7934 create them.

- `src/lib/session/permissions.tsx`, when a unit has `permissions`.
- `src/lib/session/license.tsx`, when a unit has `licenseFeatures`.
- `src/app/components/ConfirmDialog.tsx`, when a page deletes.
- `src/app/components/PluginWizard.tsx`, when a page's Angular folder has `creation/` with `step1` and `step2`.
- `src/app/components/SaveBar.tsx`, when a page is a settings form.
- `src/app/components/MembershipsPanel.tsx`, when a page lists members.
- The `console-next` project in `gravitee-am-test/playwright.config.ts`, when a unit is class B or above.

Do not build a shared part inside the run.

## Step 1: read

Read, in this order, and do not write code before the end of the list:

1. `gravitee-am-ui-next/AGENTS.md`.
2. The story entry in `stories.json`. Then, for each unit, the entry in `units.json`: every page in the story's `pages`
   with its `path`, `angularFile`, `prototypePages`, `perms`, `effectivePerms`, `guards`, `resolvers`, `menu` and flags,
   plus `spikeNotes`. A story that covers part of a unit ports only the pages it lists.
3. For each page: the Angular component `.ts` and `.html` under `gravitee-am-ui/src/app/`, the service it injects under
   `services/`, the resolvers under `resolvers/`, and the route block in `app-routing.module.ts`.
4. For each prototype page: the file under `$PROTO/src/modules/am/` and its `prototypeNotes` in
   `$PROTO/source-refs.json`. The notes say what the prototype changed from Angular.
5. The reference page for each shape the story has. Use the file on `master` when it exists. Else read the spike
   file with `git show`.

   | Shape                | Reference                                                                         | Story that builds it |
   | -------------------- | --------------------------------------------------------------------------------- | -------------------- |
   | Server list          | `src/features/domains/DomainsPage.tsx`                                            | AM-7935              |
   | Client list          | `src/features/identity-providers/IdentityProvidersPage.tsx`                       | AM-7941              |
   | Detail with sections | `src/features/applications/ApplicationPage.tsx`                                   | AM-7948              |
   | Plain form           | `Settings` in `ApplicationPage.tsx`                                               | AM-7948              |
   | Plugin schema form   | `src/features/identity-providers/IdentityProviderSettingsPage.tsx`                | AM-7941              |
   | Plugin wizard        | `src/features/identity-providers/NewIdentityProviderPage.tsx` on `PluginWizard`   | AM-7941              |
   | Code editor          | `src/features/forms/LoginFormPage.tsx`                                            | AM-7955              |
   | Full-bleed editor    | `src/features/flows/FlowsPage.tsx`                                                | AM-7947              |

6. The `spikeFiles` of each page, when the spike built it.
7. `references/conversion-rules.md` sections 1 and 3. Section 2 when an Angular page has behaviour the prototype left
   out.
8. The `USAGE_GUIDE.md` section in `node_modules/@gravitee/graphene-core/` for a shape not built before.
9. The Angular Playwright spec and page object under `gravitee-am-test/playwright/`, when one names a unit.

## Step 2: brief

Write the brief into `gravitee-am-ui-next/docs/migration/<key>.md`, the story record. It has these lists:

- The pages, as `Angular route → prototype page → console file`.
- The endpoints, as `METHOD path`, from the Angular service.
- The permission strings per page and per action, with AM names such as `domain_scope_create`.
- The licence features.
- The shared parts the story uses.
- The prototype additions to leave out, each with `no endpoint` or `not in AM`.
- The Angular behaviour the prototype dropped, which you add back.

Compare the endpoints with `spikeNotes`. Stop if they differ and the Angular source does not explain it. Stop if a
question changes which pages or endpoints the story has.

## Step 3: API module

Create `src/lib/api/<unit>.ts` for each unit.

- One type per entity, without the `Am` prefix: `ApiModel<Model, requiredKeys>` over the generated model from
  `@am-models/<Model>`. Check the fields against the Angular service payload. Override a field the spec gets wrong.
- One function per Angular service method the story uses, with the same path, the same parameters and the same payload
  fields. Build the path with `domainPath(ref)` and `searchParam(query)` from `src/lib/api/paths.ts`.

Check: every endpoint in the brief has a function, and no function has an endpoint outside the brief. Run
`yarn typecheck`.

## Step 4: pages

For each page, in the order list, create, detail:

1. Copy the prototype file to `src/features/<unit>/<Name>.tsx`. Keep its JSX, copy, columns, empty states and dialogs.
   Add the licence header.
2. Apply `conversion-rules.md` section 1, rule by rule.
3. Remove every prototype addition in the brief.
4. Add every dropped Angular behaviour in the brief, with section 2 of the rules.
5. Order the branches: error, not found, empty, data.
6. Use the console's `PageHeader`, `ConfirmDialog`, `ErrorPanel` and `NotFoundPanel`.
7. Put a copy control outside every `<form>`. Graphene `CopyableText` renders a `<button>` with no `type`, so a click
   submits the form that holds it.
8. Read the plugin schema of a plugin page. Angular `json-schema-form` does not render a property without a `type`, so
   its value is not in the configuration. Graphene `extractDefaults` adds that property's default. Remove such a
   property from the schema before `adaptAmSchema`.

Check with a search over `src/features/<unit>/`: no `gamma-baby`, `useJson`, `send(`, `amUrls`, `/am/`,
`useHasPermission`, `buildEnvPath`, `useScenario`, `@mirrors`, no permission string with `-` or `:`, no `interface` as
the generic of a `useForm` that uses `jsonSchemaResolver`. Run `yarn typecheck`.

## Step 5: routes

Create `src/features/<unit>/routes.tsx` with the unit's `<Route>` elements, the Angular path segments and `lazyPage()`
for each page. Mount it in `src/features/settings/routes.tsx` for a domain setting, in
`src/features/organization/routes.tsx` for an organization unit, or in `src/app/App.tsx` for a top-level section, in
place of the placeholder. Confirm the nav key in `src/app/navigation.ts`, and add a missing one with the Angular
`menu.label`. Set `useBreadcrumbs` on pages and `useSectionSidebar` on layouts, with absolute paths from `basePath`.

Check: every page `path` of the story resolves to a route, and the placeholder is gone.

## Step 6: permissions and licence

- Wrap each route whose `guards` has `AuthGuard` in `<RequirePermission anyOf={perms}>` from
  `src/lib/session/permissions.tsx`, with that route's own `perms` list.
- Gate each create, update and delete action with `<Can all={[...]}>` or `usePermissions().has()` and the AM name from
  the brief.
- Wrap each route with a `licenseFeature` in `<RequireLicense feature>`, and each control that needs a plugin feature
  in `<LicenseGuard feature>`, both from `src/lib/session/license.tsx`.
- Hide a `hideInCloudMode` page when the installation is managed.

Check: a test renders the page without the permission and asserts the action is absent.

## Step 7: tests

Under `src/features/<unit>/__tests__/`, with the harness of `src/features/domains/__tests__/DomainsPage.test.tsx`:

| Test         | Asserts                                                                           |
| ------------ | --------------------------------------------------------------------------------- |
| List renders | The rows from the mocked list function, the call arguments `(ref, 0, 25, '')`     |
| Search       | The call arguments with the search text, the no-results state                     |
| First use    | The first-use empty state when the total is 0                                     |
| Save         | The mocked update function receives the Angular payload fields, the success toast |
| Gated action | The action is absent without the permission and present with it                   |
| Delete       | The confirm dialog, then the mocked delete function, then the list invalidation   |
| Helpers      | Any mapping or adapter function, as plain unit tests                              |

Assert the Angular behaviour, never a prototype addition.

For a plugin page, copy the real schema into the mock. Do not write a schema by hand. Get it from the local stack (see
the `local-stack` skill):

```bash
T=$(curl -s -X POST -u admin:adminadmin http://localhost:8093/management/auth/token | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
curl -s -H "Authorization: Bearer $T" http://localhost:8093/management/platform/plugins/<kind>/<pluginId>/schema
```

The platform path has no organization segment. Assert the parsed `configuration` with `toEqual`, so an extra key fails.

## Step 8: verify

```bash
yarn typecheck && yarn lint && yarn test && yarn build
```

Stop if one fails and you cannot fix it inside the story.

Then in the browser against the local stack:

1. Run `yarn serve`, sign in, and open each page of the story.
2. Run list, search, create, edit, save and delete.
3. Record the Management API calls per action.
4. Run the same actions on the Angular console on `:4200`, and compare method, path and payload fields. `configuration`
   is a JSON string: parse it on both sides and compare its keys. If the Angular console is not running, compare with
   the Angular service code and the calls in `spikeNotes`, and say so.

A 504 on `/gamma/organizations/DEFAULT/modules` means that `:8083` is down. It does not come from the page.

Class B and above: add `gravitee-am-test/playwright/tests/console-next/<unit>.spec.ts` in the shape of the spike's
`console-next/resources.spec.ts`, with the helpers in `gravitee-am-test/api/commands/management/`. After a Save, wait for
the PUT response. A disabled Save is not proof, because `SaveBar` disables Save while the save runs. Run it with
`npx playwright test --project=console-next <spec>` from `gravitee-am-test/`.

Class D: review the pages against the Graphene `USAGE_GUIDE.md` and its design rules, and list each gap.

Append the verification to the story record: the pages checked, the calls compared, each difference and its reason.

## Step 9: review and commit

1. Run `git add -N` on the new files, because the `code-review` skill reads `git diff` and does not see untracked files.
2. Run the `code-review` skill on the diff and fix what it finds.
3. Ask the user before the commit. Commit once on the story branch, with the subject
   `feat(ui-next): <story summary>` and the footer `fixes <key>`. The story record is in the same commit.
4. Ask the user before the push and the pull request.
5. Push the branch and open a pull request to `master` with `docs/pull_request_template.md`. Put the story key in the
   issue section, and the brief and the verification from the story record in the description.

## Parallel runs

Each story has its own branch, so two runs can go at the same time in two worktrees.

1. Create the worktree from the repository root:
   `git worktree add -b <key>-<slug> .claude/worktrees/<key> origin/master`.
2. Run `yarn install` in the worktree. Serve on another port with `yarn serve --port 4202`. The `console-next`
   Playwright project is fixed to `:4201`, so run the spec after the other run stops serving.
3. When `master` moves before the merge, rebase the story branch onto `origin/master`. `settings/routes.tsx` conflicts
   when two stories add a mount line: keep both. Run the four checks again.
4. After the merge, remove the worktree with `git worktree remove`.

## Do not

- Redesign beyond the prototype page.
- Change an endpoint, a payload, a validation rule or a permission.
- Add a dependency without approval.
- Build a shared part inside the run.
- Use a Gamma-style permission string, a `/am` route prefix, `useJson`, or a prototype stub.
- Copy a Graphene component, or replace one with raw HTML.
- Touch a page outside the story.
- Skip a check, or commit with a failing command.

## Report

End with:

- The story key and the commit SHA.
- A table of the pages with route, console file and status.
- The endpoints.
- The permission strings and licence features enforced.
- The prototype additions left out, with reasons.
- The verification evidence.
- Anything you could not do, with the reason.
