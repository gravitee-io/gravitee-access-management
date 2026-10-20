# Automation plan

Phase 4 output of the migration plan. Date: 2026-10-20.

The prototype already produced the JSX for 200 of the 281 Angular components. The repeated work left per unit is
plumbing: imports, hooks, paths, permission strings, the inventory record, and the checks. Scripts cover that. The
judgement stays with the agent: which Angular behaviour the prototype dropped, which prototype addition has no
endpoint, which permission string a page needs.

All scripts live in `scripts/migration/`, run with Node from `gravitee-am-ui-next/`, and use only what the console
already installs. `typescript` is the parser.

## 1. Built

### `build-inventory.mjs`

Reads `gravitee-am-ui/src/app/app-routing.module.ts` with the TypeScript compiler and `gamma-baby/source-refs.json`.
Writes `docs/migration/inventory.json` and renders `migration-inventory.md`. It keeps the curated fields of the
previous JSON: `status`, `class`, `order`, `blockers`, `notes`, `angularCommit`, `reactFiles`, `outOfScope`.

Run it after every change to the Angular routes, the prototype map, or the curated fields.

## 2. To build, in order

### `copy-prototype.mjs <unit>`

Copies the prototype pages of a unit into `src/features/<unit>/` and applies the automatic rules of
`conversion-rules.md` section 1.

| Step           | Detail                                                                                                                                                                                                                                                                                                                                                                                                                           |
| -------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Source         | The `prototypePages` of the unit entry, resolved through `source-refs.json` to `prototypeFile`                                                                                                                                                                                                                                                                                                                                   |
| Target         | `src/features/<unit>/<Name>.tsx`. Refuses to overwrite                                                                                                                                                                                                                                                                                                                                                                           |
| Import rewrite | A table: `../../lib/amApi` → `@tanstack/react-query` and `../../lib/api/<unit>`; `../../stubs/permissions` → `../../lib/session/permissions`; `../DomainLayout` `useDomain` → `../../lib/session/domain` `useCurrentDomain`; `../../../../shared/LoadError` → the kit panel; `../../components/amUi` `ConfirmDialog` → the kit dialog; `../../components/protocols/SettingsKit` `PageHeader` → `../../app/components/PageHeader` |
| Markers        | `// TODO(migrate): permission` above every `useHasPermission`; `// TODO(migrate): endpoint` above every `useJson` and `send(`; `// TODO(migrate): path` on every `buildEnvPath` and `/am/` string; `// TODO(migrate): type` on every import from `mocks/data`                                                                                                                                                                    |
| Header         | Prepends the licence header                                                                                                                                                                                                                                                                                                                                                                                                      |
| Format         | Runs Prettier on the copied files                                                                                                                                                                                                                                                                                                                                                                                                |
| Report         | Prints the marker count per file. The agent resolves every marker                                                                                                                                                                                                                                                                                                                                                                |

Size: S. Value: removes the mechanical half of step 4.2 for every unit.

### `check-unit.mjs <unit>`

Fails with a list when a unit breaks a rule that a regex or the route tree can see.

| Check               | Rule                                                                                                                                                        |
| ------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Routes              | Every `path` of the unit's pages in `inventory.json` matches a `<Route path>` under `src/features/<unit>/routes.tsx`, with the parent prefix from `App.tsx` |
| Markers             | No `TODO(migrate)` left in `src/features/<unit>/`                                                                                                           |
| Prototype residue   | No `useJson`, `send(`, `amUrls`, `/am/`, `useHasPermission`, `buildEnvPath`, `useScenario`, `@mirrors`, `gamma-baby` in the unit                            |
| Permission names    | Every string passed to the permission hook or gate matches `^[a-z_]+$` and appears in the unit's `effectivePerms` or in the Angular templates of the unit   |
| Permission coverage | Every `effectivePerms` list of a page appears on its route element                                                                                          |
| Licence coverage    | Every `licenseFeature` appears on its route element                                                                                                         |
| Form types          | No `interface` name passed as the generic of `useForm` when a `jsonSchemaResolver` or `composeResolvers` call is in the file                                |
| Borders             | No `border` or `divide-y` class without a colour token in the unit                                                                                          |
| Inventory           | The unit's pages are `done` or `out-of-scope`, `angularCommit` is set, the markdown is regenerated                                                          |

Size: S to M. Value: the Definition of Done becomes a command. It runs in the playbook step 5 and in CI later.

### `drift.mjs [unit]`

For each unit with an `angularCommit`, lists the unit's `angularFiles` that changed in `gravitee-am-ui/src/app`
between that commit and the AM checkout's HEAD, with the `git diff` command to read. Marks the unit `stale` in the
report, not in the inventory.

Size: S. Value: Angular keeps moving until the feature freeze. The report says which ported units need a second look.

### `parity-record.mjs`, optional

A Playwright script that runs one journey on the Angular console on `:4200` and on the React console on `:4201`,
records the Management API requests as `METHOD path` with the payload keys, and prints the two lists side by side.

Size: M. Value: the browser comparison in playbook step 5 becomes repeatable. Build it when the first class C unit
starts.

## 3. Not built

| Candidate                                | Reason                                                                                                  |
| ---------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| AST codemods for Angular templates       | The prototype already wrote the JSX. The uncovered 81 components are shared parts, written once by hand |
| Component scaffolding                    | The prototype page is the scaffold                                                                      |
| Screenshot comparison with Angular       | The look changes by design                                                                              |
| Screenshot comparison with the prototype | Both consoles run on different data. A structural check in the test is cheaper                          |
| A migration framework                    | Three scripts and a skill cover the repeated work                                                       |

## 4. Verification automation

| Layer                     | Tool                                                                                                               | State                                  |
| ------------------------- | ------------------------------------------------------------------------------------------------------------------ | -------------------------------------- |
| Types, lint, tests, build | `yarn typecheck && yarn lint && yarn test && yarn build`                                                           | In place                               |
| Unit rules                | `check-unit.mjs`                                                                                                   | To build                               |
| Behaviour                 | Playwright project for the React console under `gravitee-am-test/playwright/`, with the cockpit-mock login fixture | Stage 1 story                          |
| Parity                    | `parity-record.mjs`                                                                                                | Optional                               |
| Design                    | The `graphene-review` skill                                                                                        | In place                               |
| Review                    | The `code-review` skill                                                                                            | In place                               |
| CI                        | A `ui_next_tests` job that runs the four commands and `check-unit.mjs` for every unit marked `done`                | Stage 1 story, with the delivery story |

## 5. Agent instructions

| Instruction                       | Where                                                                                                    | State              |
| --------------------------------- | -------------------------------------------------------------------------------------------------------- | ------------------ |
| Rules for every run in the folder | `gravitee-am-ui-next/AGENTS.md`                                                                          | In place           |
| The reusable prompt               | `docs/agent-standards/skills/migrate-area/SKILL.md`, exposed as `/migrate-area` through `.claude/skills` | In place           |
| Per-unit instructions             | The unit's `notes` in `inventory.json`                                                                   | Written by the run |
