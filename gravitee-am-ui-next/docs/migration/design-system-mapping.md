# Design system mapping

Phase 3 output of the migration plan. Date: 2026-10-20.

Graphene is the only design system of the React console: `@gravitee/graphene-core` 3.23.1, Tier 2 with the console's
own Tailwind 4, plus `@gravitee/graphene-policy-studio` and later `@gravitee/graphene-charts`. The authoritative
documents are, in `gravitee-ui-graphene/`:

| Document                       | Holds                                                                                                                             |
| ------------------------------ | --------------------------------------------------------------------------------------------------------------------------------- |
| `packages/core/USAGE_GUIDE.md` | Exports, integration tiers, tokens, the layout composition, the list page, the context sidebar, the content width system, testing |
| `DESIGN.md`                    | Tokens, type scale, component specs, the named rules: Solaris Restraint, 14px Floor, No-Shadow-At-Rest, Semantic-Only Signal      |
| `PRODUCT.md`                   | The register and the references                                                                                                   |
| `packages/core/snippets/`      | Runnable examples, among them `data-table-list-page.tsx` and `creation-wizard-page.tsx`                                           |

The same guide ships in the console under `node_modules/@gravitee/graphene-core/USAGE_GUIDE.md`. The prototype rules in
`gamma-baby/AGENTS.md` add two that the console keeps: import every part from `@gravitee/graphene-core`, and never
create a `components/ui/` folder.

The Angular console uses Angular Material 19, `@swimlane/ngx-datatable`, `@ajsf`, CodeMirror 5, Highcharts 9, the
`gv-*` web components of `@gravitee/ui-components` 4.3.3, and the `gio-*` parts of `@gravitee/ui-particles-angular`.
Counts below are tag occurrences in `gravitee-am-ui/src/app/**/*.html`.

## 1. Forms

| Angular                                         | Count   | Graphene                                         | Note                                             |
| ----------------------------------------------- | ------- | ------------------------------------------------ | ------------------------------------------------ |
| `mat-form-field`                                | 409     | `Field`                                          | `orientation="horizontal"` for a switch row      |
| `mat-label`                                     | 296     | `FieldLabel` with `htmlFor`                      |                                                  |
| `mat-hint`                                      | 396     | `FieldDescription`                               | Keep the Angular hint text                       |
| `mat-error`                                     | 54      | `FieldError` with `errors={[error]}`             | Keep the Angular message                         |
| `input matInput`                                |         | `Input`, `Textarea`                              | `aria-invalid` when the field has an error       |
| `input type="password"`                         |         | `PasswordInput`                                  |                                                  |
| `mat-slide-toggle`                              | 124     | `Switch` with `onCheckedChange`                  | Inside `Controller`                              |
| `mat-select`, `mat-option`                      | 70, 149 | `Select`                                         | Inside `Controller`                              |
| `mat-checkbox`                                  | 29      | `Checkbox`                                       | Inside `Controller`                              |
| `mat-radio-group`, `mat-radio-button`           | 8, 14   | `RadioGroup`                                     | Inside `Controller`                              |
| `mat-autocomplete`                              | 17      | `Combobox`                                       |                                                  |
| `mat-chip-grid`, `mat-chip-row`                 | 14      | Kit tag input from `Badge` and `Input`           | No Graphene part                                 |
| `mat-datepicker`                                | 4       | `DatePicker`, `Calendar`                         |                                                  |
| `gio-save-bar`                                  | 1       | `SaveBar`                                        | In `src/app/components/`. Shows on `isDirty`     |
| `@ajsf` `json-schema-form`                      | 10      | `JsonSchemaForm` with `jsonSchemaResolver`       | Through `adaptAmSchema()`                        |
| `material-file`, `material-multiselect` widgets | 2       | None                                             | GR-A custom field story. Plain input until then  |
| `gv-expression-language`                        | 5       | `Input` or `Textarea` with `ElCheatSheetTrigger` |                                                  |
| `mat-horizontal-stepper`, `mat-step`            | 12, 26  | Kit wizard inside `PageFocused`                  | `snippets/creation-wizard-page.tsx` is the model |

Validation shows inline under the field, on touch, with the Angular message. There is no form-level error summary.

## 2. Tables and lists

| Angular                                   | Count | Graphene                                                     | Note                                                                |
| ----------------------------------------- | ----- | ------------------------------------------------------------ | ------------------------------------------------------------------- |
| `ngx-datatable`                           | 61    | `DataTable`                                                  | `serverSide` when the Angular table has `externalPaging`            |
| `ngx-datatable-column`                    |       | `ColumnDef<T, unknown>`                                      |                                                                     |
| `mat-table`                               | 4     | `DataTable`                                                  |                                                                     |
| `mat-paginator`                           |       | `DataTable` `pagination`                                     | 1-based                                                             |
| `app-emptystate`                          | 43    | `DataTableEmptyState`                                        | `first-use` replaces the table, `no-results` goes in `emptyMessage` |
| `mat-list`, `mat-list-item`               | 9, 18 | `Item`                                                       |                                                                     |
| Badge cell `gio-badge-*` classes          |       | `BadgeCell`, `Badge`                                         | `success`, `secondary`, `outline`                                   |
| Date cell with `humanDate`                |       | `DateCell`                                                   |                                                                     |
| Copy cell with `ngx-clipboard`            |       | `CopyableCell`, `CopyableText`                               |                                                                     |
| A search `mat-form-field` above the table |       | `SearchField` in `toolbar`                                   |                                                                     |
| Filters                                   |       | `FacetedFilter` in `toolbar`                                 |                                                                     |
| `mat-fab` add button                      |       | `Button` in `PageHeader` actions                             | The prototype moved it to the header                                |
| Row actions `mat-button` with `mat-icon`  |       | `Button variant="ghost" size="icon-sm"` with an `aria-label` |                                                                     |

## 3. Dialogs and feedback

| Angular                                                     | Count | Graphene                                        | Note                                                  |
| ----------------------------------------------------------- | ----- | ----------------------------------------------- | ----------------------------------------------------- |
| `MatDialog` with `mat-dialog-content`, `mat-dialog-actions` | 29    | `Dialog`                                        | A component per dialog with `open` and `onOpenChange` |
| `DialogService.confirm`                                     |       | `ConfirmDialog` on `Dialog`                     | `destructive` variant for delete                      |
| Side panel                                                  |       | `Sheet`                                         | The policy studio uses one for step settings          |
| `MatSnackBar` through `SnackbarService`                     |       | `toast` from Sonner, `Toaster` in `App.tsx`     | `success`, `error`                                    |
| `gio-banner-info`                                           | 1     | `Alert` with `AlertTitle`, `AlertDescription`   |                                                       |
| `matTooltip`                                                |       | `Tooltip`                                       |                                                       |
| `mat-menu`                                                  |       | `DropdownMenu`                                  |                                                       |
| `app-loader`, `gv-loader`                                   |       | `Spinner`, `Skeleton`                           | `DataTable` has its own skeleton                      |
| `mat-progress-bar`                                          |       | `Progress`                                      |                                                       |
| `gio-license-expiration-notification`                       | 1     | Kit, in the shell                               |                                                       |
| Upgrade dialog from `GioLicenseService`                     |       | GR-A shared dialog story. Kit dialog until then |                                                       |

## 4. Navigation and layout

| Angular                              | Count | Graphene                                                 | Note                                                |
| ------------------------------------ | ----- | -------------------------------------------------------- | --------------------------------------------------- |
| `gv-navbar`, `gio-top-bar`           | 1, 1  | `ContentHeader` with `AppContextBar` and `TopNavUser`    | In `Shell.tsx`                                      |
| `gv-sidenav`, `gio-menu`             | 1, 1  | `AppSidebar` with `SidebarNavigation`                    | In `Shell.tsx`                                      |
| `gv-submenu`, `gio-submenu`          | 5, 1  | `ContextSidebar` through `useSectionSidebar()`           | Settings, application, organization                 |
| `gv-tab-navbar`, `mat-tab-nav-panel` | 3, 7  | `ContextSidebar` when the tabs are routes                | The prototype turned tab bars into sidebar sections |
| `mat-tab-group`, `mat-tab`           | 6, 15 | `Tabs` when the tabs are not routes                      |                                                     |
| `app-breadcrumb`                     |       | `ContentHeader` `breadcrumbs` through `useBreadcrumbs()` |                                                     |
| `gv-page-container`                  |       | The default content container                            |                                                     |
| A creation page with a back button   |       | `PageFocused`                                            | `/new` and `/:id/edit` routes                       |
| `mat-card`                           | 19    | `Card`, `CardHeader`, `CardTitle`, `CardContent`         |                                                     |
| `mat-divider`                        | 154   | `Separator`, or `divide-border` on a list                |                                                     |
| `mat-expansion-panel`                |       | `Accordion`, `Collapsible`                               |                                                     |
| `fxLayout` from flex-layout          |       | Tailwind `flex`, `grid`, `gap-*`                         |                                                     |

Content width follows the USAGE_GUIDE content width system. A list page and a settings page use the default container.
An editor such as the policy studio uses `contentVariant: 'full-bleed'`.

## 5. Icons

| Angular                         | Count | Graphene                                                                                                               |
| ------------------------------- | ----- | ---------------------------------------------------------------------------------------------------------------------- |
| `mat-icon` with a Material name | 429   | A Lucide icon from `@gravitee/graphene-core/icons`, for example `SettingsIcon`, `Trash2Icon`, `PlusIcon`, `SearchIcon` |
| `mat-icon svgIcon="gio:x"`      |       | A `Gio*` icon from the same module when a product icon is meant, else a Lucide icon                                    |
| `identiconHash`, jdenticon      | 3     | `Avatar` with initials                                                                                                 |

Pick the icon the prototype page uses. Where the prototype has none, pick the Lucide icon whose name matches the
Material name. Size 16 inline, with `aria-hidden` next to a label.

## 6. Editors, studios, charts

| Angular                             | Count   | Graphene                                                | Note                                       |
| ----------------------------------- | ------- | ------------------------------------------------------- | ------------------------------------------ |
| `ngx-codemirror`                    | 13      | `CodeEditor` from `@gravitee/graphene-core/code-editor` | Monaco, workers from `src/monaco-setup.ts` |
| `gv-policy-studio`                  | 1       | `PolicyStudio` from `@gravitee/graphene-policy-studio`  | With `AM_FLOW_MODEL`, draft PR #470        |
| `gv-design`                         | 1       | None. The theme editor preview                          | Class D                                    |
| `gv-dashboard`, `gv-widget-*`       | 2, 5    | `@gravitee/graphene-charts`                             | Not installed. Class D                     |
| `highcharts-angular`                | 9 files | `@gravitee/graphene-charts`                             |                                            |
| `vis-network` for the OpenFGA model |         | None                                                    | Class D                                    |
| `asciidoctor`                       |         | The policy studio documentation tab                     |                                            |

## 7. Tokens

The console writes Tailwind utilities with Graphene's semantic tokens. No raw colours, no pixel sizes outside the
scale.

| Use             | Class                                                       |
| --------------- | ----------------------------------------------------------- |
| Body text       | default                                                     |
| Secondary text  | `text-muted-foreground`                                     |
| Error text      | `text-destructive`                                          |
| Border          | `border-border`, `divide-border`, `ring-foreground/10`      |
| Code            | `font-mono text-xs`                                         |
| Page title      | `<h1>` inside `PageHeader`, no class                        |
| Vertical rhythm | `flex flex-col gap-6` for a page, `gap-4` for a form        |
| Form width      | `max-w-2xl` for a plain form, `max-w-3xl` for a schema form |

Dark mode comes from `AppLayout` `defaultTheme="system"`. No page sets a colour by hand.

## 8. Parts with no Graphene match

| Need                  | Used by                                                             | Plan                                            |
| --------------------- | ------------------------------------------------------------------- | ----------------------------------------------- |
| Wizard                | 12 steppers: plugin creation, domain creation, application creation | Kit, on `PageFocused` with the snippet as model |
| Confirm dialog        | Every delete                                                        | Built: `ConfirmDialog` on `Dialog`              |
| Save bar              | Settings forms                                                      | Built: `SaveBar`                                |
| Tag input             | 14 chip grids                                                       | Kit                                             |
| Memberships panel     | Domain, application, MCP server, organization members               | Kit                                             |
| Client secret dialogs | Applications, MCP servers, domain secrets                           | Kit                                             |
| Custom schema widgets | `datasource`, file, multiselect                                     | GR-A custom field story                         |
| Upgrade dialog        | 21 `licenseGuard` sites                                             | GR-A shared dialog story                        |
| Charts                | Dashboard, analytics, audits                                        | `graphene-charts`                               |
| OpenFGA model editor  | Authorization engines                                               | Design decision, class D                        |
| Theme preview         | Theme                                                               | Design decision, class D                        |

The kit parts live in `src/app/components/`. Each is composed from Graphene primitives and takes no styling of its own
beyond layout utilities.

## 9. Review

The `graphene-review` skill scores a feature against the USAGE_GUIDE, the `DESIGN.md` named rules, the `PRODUCT.md`
register, accessibility, strict TypeScript and the React structure. Run it on every class D unit and on the first unit
of each shape.
