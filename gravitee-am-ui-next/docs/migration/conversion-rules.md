# Conversion rules

Phase 3 output of the migration plan. Date: 2026-10-20. The rules come from the six pages built in `src/features/`,
the prototype pages under `gamma-baby/src/modules/am/`, and the Angular console under `gravitee-am-ui/src/app/`.

Three sources feed one console page:

| Source                                 | Gives                                                                                                 | Never gives                             |
| -------------------------------------- | ----------------------------------------------------------------------------------------------------- | --------------------------------------- |
| Prototype page                         | The JSX, the Graphene parts, the copy, the columns, the empty states, the dialogs                     | Endpoints, permission names, validation |
| Angular page, service, resolver, route | Endpoints, payloads, permission names, licence features, validation rules and messages, user journeys | The look                                |
| This console                           | The HTTP layer, the session scopes, the query conventions, the layout hooks, the test harness         | Page content                            |

Section 1 is the common case: a prototype page exists for the route. Section 2 applies to behaviour the prototype
simplified and to the Angular components no prototype page covers. Section 3 lists the pitfalls. Section 4 lists the
checks.

Each rule has a mode. **Automatic** means a script or a search-and-replace does it. **Semi-automatic** means the shape
is fixed and the agent fills the names. **Manual** means the agent reads the Angular source and decides.

## 1. Prototype page to console page

### 1.1 Data reads

| Prototype                                                  | Console                                                                          | Mode           |
| ---------------------------------------------------------- | -------------------------------------------------------------------------------- | -------------- |
| `const { data, loading, error, reload } = useJson<T>(url)` | `const { data, isPending, error } = useQuery({ queryKey, queryFn })`             | Semi-automatic |
| `useJson<T>(condition ? url : null)`                       | `useQuery({ ..., enabled: condition })`                                          | Semi-automatic |
| `reload()` after a write                                   | `queryClient.invalidateQueries({ queryKey })` in the mutation's `onSuccess`      | Semi-automatic |
| `amUrls.x(...)`, `protocolUrls.x(...)`, `/am/...` strings  | A function in `src/lib/api/<area>.ts` that builds the real path from `DomainRef` | Manual         |
| `Paged<T>` with `data`, `totalCount`, `page`               | `Page<T>` with `data`, `currentPage`, `totalCount` from `management-api.ts`      | Automatic      |

Example, the scopes list. Prototype:

```tsx
const { data, loading, error, reload } = useJson<Paged<AmScopeRecord>>(
    protocolUrls.scopes(domain.id, { page: page - 1, size: pageSize, q: query || undefined }),
);
```

Console:

```tsx
const { ref } = useCurrentDomain();
const { data, isFetching, error } = useQuery({
    queryKey: ['scopes', ref, page, pageSize, query],
    queryFn: () => listScopes(ref, page - 1, pageSize, query),
    placeholderData: keepPreviousData,
});
```

with, in `src/lib/api/scopes.ts`:

```ts
export function listScopes(ref: DomainRef, page: number, size: number, query = ''): Promise<Page<Scope>> {
    return http<Page<Scope>>(`${domainPath(ref)}/scopes?page=${page}&size=${size}${searchParam(query)}`);
}
```

The path and the query parameters come from the Angular `ScopeService.findByDomain` and `search`.

### 1.2 Data writes

| Prototype                                                                         | Console                                                                                   | Mode           |
| --------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------- | -------------- |
| `await send('PATCH', url, body)` inside a handler with `try`, `toast`, `reload()` | `useMutation({ mutationFn, onSuccess, onError })`, then `mutate(values)` from the handler | Semi-automatic |
| `toast.success('Scope updated')`                                                  | Same call, in `onSuccess`                                                                 | Automatic      |
| `toast.error(reason instanceof Error ? reason.message : '...')`                   | `onError: error => toast.error(\`Could not update the scope. ${error.message}\`)`         | Automatic      |
| A delete after `ConfirmDialog`                                                    | `ConfirmDialog`, then `remove.mutate(id)`                                                 | Semi-automatic |

`onSuccess` of an update sets the detail key with the response and invalidates the list key. `onSuccess` of a delete
invalidates the list key and navigates to the list. `onSuccess` of a create navigates to the new entity.

The payload is the one the Angular service sends. `ScopeService.update` sends five fields. The console sends the same
five. Do not send the whole entity back.

### 1.3 Permissions

| Prototype                                              | Console                                                              | Mode           |
| ------------------------------------------------------ | -------------------------------------------------------------------- | -------------- |
| `useHasPermission({ anyOf: ['domain-scope:create'] })` | `usePermissions().has('domain_scope_create')`                        | Manual         |
| `canCreate ? <Button> : null`                          | `<Can all={['domain_scope_create']}><Button /></Can>` or the boolean | Semi-automatic |

The AM name comes from the Angular source, in this order of preference:

1. The route's `perms.only` list in `app-routing.module.ts`, which the inventory entry carries as `effectivePerms`.
2. The template's `*hasPermission="['...']"` or `*hasAnyPermission="[...]"`.
3. The component's `authService.hasPermissions([...])` call.

The prototype string is a hint for the resource and the action, nothing more. `domain-scope:create` points at
`domain_scope_create`. `domain-settings:update` points at `domain_settings_update`. Check each one.

The model lives in `src/lib/session/permissions.tsx`. A route that Angular guards with `AuthGuard` gets
`<RequirePermission anyOf={perms}>` with that route's own `perms` list from the inventory. A scope's list loads through
`LoadPermissionScope` in the scope component, as `ApplicationLayout` does for an application.

### 1.4 Session and scope

| Prototype                                              | Console                                                                                          | Mode      |
| ------------------------------------------------------ | ------------------------------------------------------------------------------------------------ | --------- |
| `const { domain, domainPath } = useDomain()`           | `const { domain, ref, basePath, domainsPath } = useCurrentDomain()`                              | Automatic |
| `useAmModule()` for the domain list                    | `useQuery` on `listDomains` in the shell, not in pages                                           | Automatic |
| `useEnvironment().environmentHrid`                     | `useCurrentEnvironment().hrids[0]`                                                               | Automatic |
| `useAmScope()` with `kind: 'domain' \| 'organization'` | Not ported yet. A domain unit ships the domain scope. The organization units add the scope later | Manual    |
| `useScenario`, `readDataProfile`                       | Delete. There is no scenario in the console                                                      | Automatic |

### 1.5 Routes and paths

| Prototype                                                      | Console                                                                 | Mode           |
| -------------------------------------------------------------- | ----------------------------------------------------------------------- | -------------- |
| `/environments/:env/am/domains/:domainId/...`                  | `/environments/:envHrid/domains/:domainHrid/...`                        | Automatic      |
| `buildEnvPath(env, 'am', 'domains', id, 'settings', 'scopes')` | `` `${basePath}/settings/scopes` ``                                     | Automatic      |
| `navigate(\`${listPath}/new\`)`                                | Same, with `listPath` built from `basePath`                             | Automatic      |
| `domain.id` in a URL                                           | `domain.hrid` in a URL, `ref.domainId` in an API call                   | Manual         |
| A route added to `src/modules/am/routes.tsx`                   | A route added to the area's `routes.tsx` and the key to `navigation.ts` | Semi-automatic |

The Angular route segment wins over the prototype segment when they differ. The inventory lists the Angular path of
every page. Examples: `bot-detection`, `device-identifier`, `extensionGrants`, `dcr`.

### 1.6 Layout

| Prototype                                                                       | Console                                                      | Mode      |
| ------------------------------------------------------------------------------- | ------------------------------------------------------------ | --------- |
| `useLayoutConfig({ breadcrumbs })` in a page                                    | `useBreadcrumbs([...])`                                      | Automatic |
| `useLayoutConfig({ viewMode: 'context', contextSidebar, leading })` in a layout | `useSectionSidebar({ title, badges, groups, base, crumbs })` | Automatic |
| `PageHeader` from `components/protocols/SettingsKit`                            | `PageHeader` from `src/app/components/PageHeader.tsx`        | Automatic |
| `LoadError`, `StateGap`                                                         | `ErrorPanel` and `NotFoundPanel` from `src/app/components/`  | Automatic |
| `ConfirmDialog` from `components/amUi`                                          | `ConfirmDialog` from `src/app/components/`                   | Automatic |
| Search `<Input>` with a 300 ms `useEffect` debounce                             | `<SearchField label onSearch />`                             | Automatic |

### 1.7 Types

| Prototype                                                      | Console                                                                                                                                                               | Mode      |
| -------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------- |
| `import type { AmScopeRecord } from '../../lib/protocols/api'` | `import type { Scope } from '../../lib/api/scopes'`                                                                                                                   | Manual    |
| Fields invented for the mock                                   | Fields the Management API returns. Check the Angular service, the generated model under `../gravitee-am-test/api/management/models/`, and `../docs/mapi/openapi.yaml` | Manual    |
| `interface` for a form value shape                             | `type` alias                                                                                                                                                          | Automatic |

Drop the `Am` prefix. The console's types live in the console's namespace.

### 1.8 Graphene version

The prototype pins Graphene 3.3.0. The console pins 3.23.1. After the copy, run `yarn typecheck`. A removed or renamed
prop fails there. A changed behaviour does not. Check in the browser: `DataTable` pagination, `Select` with
`Controller`, `Dialog` open state, `Tabs` value.

### 1.9 Format

The prototype uses 2-space indentation and `(event) =>`. The console uses 4 spaces, `event =>`, width 140. Run
`yarn lint:prettier:fix` on the copied files.

## 2. Angular to React

Apply these where the prototype left the behaviour out, and for the Angular components with no prototype page. The
uncovered list is in `migration-inventory.md`.

### 2.1 Structure

| Angular                                          | React                                                                 | Mode           |
| ------------------------------------------------ | --------------------------------------------------------------------- | -------------- |
| `@Component` class with a template file          | A function component in one `.tsx` file                               | Semi-automatic |
| `@Input() x`                                     | A prop `x` in a `readonly` props type                                 | Automatic      |
| `@Output() change = new EventEmitter()`          | A callback prop `onChange`                                            | Automatic      |
| `ngOnInit` that reads `route.snapshot.data['x']` | `useQuery` for `x`, or `useOutletContext()` when the layout loaded it | Semi-automatic |
| `ngOnInit` that calls a service                  | `useQuery`                                                            | Semi-automatic |
| `ngOnDestroy` with `unsubscribe`                 | Nothing. TanStack Query cancels on unmount                            | Automatic      |
| `*ngIf="x"`                                      | `{x && ...}` or a ternary                                             | Automatic      |
| `*ngFor="let row of rows"`                       | `rows.map(row => ...)` with a `key`                                   | Automatic      |
| `[ngClass]="{ a: cond }"`                        | `cn('base', cond && 'a')` from `@gravitee/graphene-core`              | Automatic      |
| `{{ value \| humanDate }}`                       | `<DateCell value={new Date(value)} />` or `Intl.DateTimeFormat`       | Automatic      |
| `{{ value \| date }}`                            | Same                                                                  | Automatic      |
| `{{ obs$ \| async }}`                            | The query's `data`                                                    | Automatic      |
| `{{ map \| mapToIterable }}`                     | `Object.entries(map)`                                                 | Automatic      |
| `[routerLink]="[row.id]"`                        | `<Link to={row.id}>` relative to the list route                       | Automatic      |
| `router.navigate(['..'], { relativeTo: route })` | `navigate(\`${basePath}/...\`)` with an absolute path                 | Manual         |

### 2.2 Services and dependency injection

| Angular                                                                        | React                                                           | Mode           |
| ------------------------------------------------------------------------------ | --------------------------------------------------------------- | -------------- |
| `XService` with `http.get(this.base + domainId + '/x')`                        | `listX(ref)` in `src/lib/api/x.ts` with the same path           | Semi-automatic |
| `AppConfig.settings.domainBaseURL` with `:organizationId` and `:environmentId` | `domainPath(ref)`                                               | Automatic      |
| `SnackbarService.open(msg)`                                                    | `toast.success(msg)` or `toast.error(msg)`                      | Automatic      |
| `DialogService.confirm(title, text)` then `filter(res => res)`                 | `ConfirmDialog` with `onConfirm`                                | Semi-automatic |
| `AuthService.hasPermissions([...])`                                            | `usePermissions().has([...])`                                   | Semi-automatic |
| `AuthService.hasAnyPermissions([...])`                                         | `usePermissions().hasAny([...])`                                | Semi-automatic |
| `DomainStoreService.current`                                                   | `useCurrentDomain().domain`                                     | Automatic      |
| `EnvironmentService.getCurrentEnvironment()`                                   | `useCurrentEnvironment()`                                       | Automatic      |
| `UserPreferencesService`                                                       | A query on `GET /user/preferences` in the shell, not ported yet | Manual         |
| A resolver that calls `findByDomain(domainId, 0, 10)`                          | The list query with `page: 1, pageSize: 10`                     | Semi-automatic |
| `combineLatest([a$, b$])`                                                      | Two `useQuery` calls, or `useQueries`                           | Semi-automatic |
| `switchMap` chains inside `subscribe`                                          | `async` `mutationFn` with `await`                               | Semi-automatic |

### 2.3 Routing and guards

| Angular                                                          | React                                                             | Mode           |
| ---------------------------------------------------------------- | ----------------------------------------------------------------- | -------------- |
| A route with `component`                                         | A `<Route>` with `lazyPage()` in the area's `routes.tsx`          | Semi-automatic |
| `canActivate: [AuthGuard]` with `data.perms.only`                | `<RequirePermission anyOf={perms}>` with the route's own list     | Semi-automatic |
| `canActivate: [LicenseGuard]` with `data.licenseOptions.feature` | `<RequireLicense feature>` with the same feature                  | Semi-automatic |
| `canActivate: [CloudModeGuard]`, `menu.hideInCloudMode`          | Hide the route and the nav item when the installation is managed  | Semi-automatic |
| `data.types.only: ['WEB', ...]` on an application section        | Filter the section nav groups by `application.type` in the layout | Manual         |
| `data.protocol: 'SAML'`                                          | Show the section when `domain.saml?.enabled`                      | Manual         |
| `data.requiresAgentIdentity: true`                               | Show the section when `application.type === 'agent'`              | Manual         |
| `data.menu.label`, `section`, `level`                            | An item in the area's nav group with the same label               | Automatic      |
| `data.breadcrumb.label: 'x.name'`                                | The entity name in the page's `useBreadcrumbs`                    | Automatic      |
| `redirectTo: 'general', pathMatch: 'full'`                       | `<Route index element={<Navigate to="general" replace />} />`     | Automatic      |
| `route.paramMap.get('scopeId')`                                  | `useParams()` with the same name                                  | Automatic      |

### 2.4 Forms

| Angular                                                                  | React                                                                                        | Mode           |
| ------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------- | -------------- |
| `[(ngModel)]="entity.name"` with `#f="ngForm"`                           | `useForm({ defaultValues })` and `register('name')`                                          | Semi-automatic |
| `required` on an input                                                   | `register('name', { required: 'Name is required' })`                                         | Semi-automatic |
| `pattern`, `minlength`, `maxlength`, `email`                             | The matching `register` rule with the Angular message                                        | Semi-automatic |
| A `FormGroup` with `Validators.x`                                        | `register` rules, or a resolver for cross-field rules                                        | Manual         |
| A custom validator function                                              | The same function inside a resolver                                                          | Manual         |
| `(ngSubmit)="save()"` with `[disabled]="!f.valid \|\| !f.dirty"`         | `handleSubmit(values => save.mutate(values))` with `disabled={!isDirty \|\| save.isPending}` | Automatic      |
| `mat-form-field` with `mat-label`, `mat-hint`, `mat-error`               | `Field`, `FieldLabel`, `FieldDescription`, `FieldError`                                      | Automatic      |
| `mat-slide-toggle` with `(change)`                                       | `Controller` with `Switch` and `onCheckedChange`                                             | Automatic      |
| `mat-select` with `mat-option`                                           | `Controller` with `Select`                                                                   | Automatic      |
| `mat-checkbox`                                                           | `Controller` with `Checkbox`                                                                 | Automatic      |
| `mat-radio-group`                                                        | `Controller` with `RadioGroup`                                                               | Automatic      |
| `mat-autocomplete`                                                       | `Combobox`                                                                                   | Semi-automatic |
| `mat-chip-grid` for a string list                                        | `TagInput` from `src/app/components/`                                                        | Semi-automatic |
| `mat-datepicker`                                                         | `DatePicker`                                                                                 | Automatic      |
| `formDirective.resetForm(entity)` after save                             | `form.reset(response)`                                                                       | Automatic      |
| `@ajsf` `json-schema-form` with `[schema]` and `(onChanges)`             | `JsonSchemaForm` with `adaptAmSchema(schema)` and `jsonSchemaResolver`                       | Automatic      |
| `app-form` custom schema widgets `material-file`, `material-multiselect` | A Graphene custom field, once GR-A ships it. Until then a plain input and an `Alert`         | Manual         |

The save bar `gio-save-bar` becomes `SaveBar`: it shows when `isDirty`, submits the form, and resets on
discard.

### 2.5 Tables

| Angular                                                                      | React                                                                                  | Mode           |
| ---------------------------------------------------------------------------- | -------------------------------------------------------------------------------------- | -------------- |
| `ngx-datatable` with `[rows]`, `[columnMode]="'flex'"`                       | `DataTable` with `columns`                                                             | Automatic      |
| `[externalPaging]="true"`, `[count]`, `[offset]`, `(page)="setPage($event)"` | `serverSide` with `pagination={{ page, pageSize, totalCount, onPageChange }}`, 1-based | Automatic      |
| `ngx-datatable-column` with a cell template                                  | A `ColumnDef` with `cell: ({ row }) => ...`                                            | Semi-automatic |
| `[sorts]` on a client-side table                                             | `enableSorting` on the column                                                          | Automatic      |
| `[sorts]` on a server-side table                                             | Nothing. The Management API list has no sort parameter                                 | Automatic      |
| Row detail toggle, `ngx-datatable-row-detail`                                | A `Collapsible` row or a detail page                                                   | Manual         |
| `mat-table`                                                                  | `DataTable`                                                                            | Semi-automatic |
| `app-emptystate` with `message`, `subMessage`, `icon`                        | `DataTableEmptyState` with `variant="first-use"`                                       | Automatic      |
| A `mat-fab` add button bottom right                                          | A `Button` in `PageHeader` actions, as the prototype does                              | Automatic      |

### 2.6 Dialogs, menus, feedback

| Angular                                            | React                                                                                                | Mode           |
| -------------------------------------------------- | ---------------------------------------------------------------------------------------------------- | -------------- |
| `MatDialog.open(XDialogComponent, { data })`       | A `Dialog` component in the area with `open` and `onOpenChange` props                                | Semi-automatic |
| `dialogRef.afterClosed().subscribe(result => ...)` | An `onConfirm(result)` callback prop                                                                 | Semi-automatic |
| `mat-menu`                                         | `DropdownMenu`                                                                                       | Automatic      |
| `mat-tab-group`, `gv-tab-navbar`                   | `Tabs`, or the context sidebar when the tabs are routes                                              | Semi-automatic |
| `mat-horizontal-stepper` with `mat-step`           | `PluginWizard` for a plugin creation. Else one step component per `mat-step`, `Button` next and back | Semi-automatic |
| `matTooltip`                                       | `Tooltip`                                                                                            | Automatic      |
| `ngx-clipboard` `cbContent`                        | `CopyableText` or `CopyableCell`                                                                     | Automatic      |
| `app-user-avatar`, `identiconHash`                 | `Avatar` with initials                                                                               | Automatic      |

### 2.7 Editors and studios

| Angular                                                   | React                                                                          | Mode           |
| --------------------------------------------------------- | ------------------------------------------------------------------------------ | -------------- |
| `ngx-codemirror` with `[options]="{ mode: 'htmlmixed' }"` | `CodeEditor` from `@gravitee/graphene-core/code-editor` with `language="html"` | Automatic      |
| `gv-expression-language`                                  | `Input` or `Textarea` with `ElCheatSheetTrigger`                               | Semi-automatic |
| `gv-policy-studio`                                        | `PolicyStudio` with `AM_FLOW_MODEL`, as `FlowsPage.tsx` does                   | Done           |
| `asciidoctor` rendering                                   | The policy studio documentation tab takes AsciiDoc                             | Done           |

### 2.8 Charts and dashboards

| Angular                           | React                                                   | Mode           |
| --------------------------------- | ------------------------------------------------------- | -------------- |
| `gv-dashboard` with `gv-widget-*` | `@gravitee/graphene-charts`, not installed yet. Class D | Manual         |
| `highcharts-angular`              | `graphene-charts`                                       | Manual         |
| `app-widget-count`                | A `Card` with a number                                  | Semi-automatic |

### 2.9 Lifecycle and state

| Angular                                      | React                                                    | Mode           |
| -------------------------------------------- | -------------------------------------------------------- | -------------- |
| A field set in `ngOnInit` from a resolver    | `const { data } = useQuery(...)` or `useOutletContext()` | Semi-automatic |
| A field set from a form event                | `useState` or the form state                             | Automatic      |
| A `BehaviorSubject` shared through a service | A query key shared through TanStack Query, or a context  | Manual         |
| `setTimeout` for a debounce                  | `SearchField`, or a `useEffect` with a timer             | Automatic      |
| `ChangeDetectorRef.detectChanges()`          | Nothing                                                  | Automatic      |

## 3. Pitfalls

| Pitfall                                                           | What happens                                                    | Rule                                                                                                  |
| ----------------------------------------------------------------- | --------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------- |
| A prototype `hybrid` addition                                     | The page shows a column, a filter or an action AM does not have | Read `prototypeNotes` in `source-refs.json`. Leave the addition out. Record it in the inventory entry |
| A mock endpoint with no Management API counterpart                | A 404 at runtime                                                | Check the Angular service. If it has no call, the feature is out of scope                             |
| A Gamma-style permission string                                   | The gate never opens, because `GET /user` returns AM names      | Map every string through the Angular source                                                           |
| A prototype URL with the domain id                                | The route does not match, because the console uses the hrid     | Use `domain.hrid` in URLs, `ref.domainId` in API calls                                                |
| `domain.id` used as a cache key in one place and `ref` in another | Two caches for one entity                                       | Key by `ref` and the entity id                                                                        |
| A removed Graphene prop                                           | A typecheck error, or a silent no-op for a renamed behaviour    | Typecheck, then check in the browser                                                                  |
| An `interface` for form values                                    | A type error in `jsonSchemaResolver` or `composeResolvers`      | Use a `type` alias                                                                                    |
| `border` or `divide-y` without a colour                           | A black border under Tailwind 4                                 | Write `border-border`, `divide-border`                                                                |
| Graphene CSS imported from TypeScript                             | The app's Tailwind preflight overrides Graphene's base rules    | Import it last in `styles.css` only                                                                   |
| A `..` relative path in a breadcrumb or link                      | It follows the route tree, not the URL                          | Build absolute paths from `basePath`                                                                  |
| 0-based page sent to `DataTable`                                  | The first page shows as page 0                                  | Convert at the query call                                                                             |
| An Angular resolver's first page size kept                        | The list shows 10 rows and the table says 25                    | Use the console's page size and fetch in the page                                                     |
| A `hasPermission` on a column                                     | The column hides and the table layout shifts                    | Hide the action inside the cell, keep the column                                                      |
| A test that asserts the prototype's invented behaviour            | The test passes and the page is wrong                           | Assert the Angular endpoint, payload and permission                                                   |
| A flow id with `:`                                                | The policy studio crashes                                       | AM uses `~`                                                                                           |

## 4. Checks

| Check      | Command or action                                                                                  | Proves                                                       |
| ---------- | -------------------------------------------------------------------------------------------------- | ------------------------------------------------------------ |
| Types      | `yarn typecheck`                                                                                   | Graphene props, API types                                    |
| Lint       | `yarn lint`                                                                                        | Graphene ESLint rules, Prettier                              |
| Unit tests | `yarn test`                                                                                        | The API calls, the rows, the empty states, the gated actions |
| Build      | `yarn build`                                                                                       | The bundle compiles                                          |
| Browser    | `yarn serve` against the local stack, then the page                                                | Behaviour and look                                           |
| Network    | The Management API calls in the browser's network panel, compared with the Angular page on `:4200` | Endpoint and payload parity                                  |
| Playwright | The area's test in the console's Playwright project                                                | The journey, for class B and above                           |
| Design     | The `graphene-review` skill                                                                        | Graphene conformance, for class D                            |
