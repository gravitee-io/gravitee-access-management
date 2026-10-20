#!/usr/bin/env node
/*
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Builds the migration inventory from three inputs:
 *
 *   1. The Angular route tree in `gravitee-am-ui/src/app/app-routing.module.ts`: every route with a component, its
 *      full path, permissions, licence feature, resolvers, menu entry and cloud-mode flag.
 *   2. The prototype's provenance map `gamma-baby/source-refs.json`: which prototype page was built from which
 *      Angular files.
 *   3. The previous `docs/migration/inventory.json`, if any: the curated fields (status, class, blockers, notes,
 *      reactFiles, angularCommit) are kept for every unit and page that still exists.
 *
 * Output: `docs/migration/inventory.json` (the state) and `docs/migration/migration-inventory.md` (a rendered view).
 *
 * Usage, from gravitee-am-ui-next/:
 *   node scripts/migration/build-inventory.mjs [--angular ../gravitee-am-ui/src/app] [--prototype ../../gamma-baby]
 */
import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';

// ─── Paths and arguments ─────────────────────────────────────────────────────

const here = dirname(fileURLToPath(import.meta.url));
const consoleRoot = resolve(here, '../..');
const amRoot = resolve(consoleRoot, '..');
const args = parseArgs(process.argv.slice(2));
const angularApp = resolve(consoleRoot, args.angular ?? join(amRoot, 'gravitee-am-ui/src/app'));
const prototypeRoot = resolve(consoleRoot, args.prototype ?? join(amRoot, '../gamma-baby'));
const outDir = join(consoleRoot, 'docs/migration');
const jsonPath = join(outDir, 'inventory.json');
const mdPath = join(outDir, 'migration-inventory.md');

function parseArgs(argv) {
    const out = {};
    for (let i = 0; i < argv.length; i += 1) {
        if (argv[i].startsWith('--')) {
            out[argv[i].slice(2)] = argv[i + 1];
            i += 1;
        }
    }
    return out;
}

function fail(message) {
    console.error(`build-inventory: ${message}`);
    process.exit(2);
}

if (!existsSync(join(angularApp, 'app-routing.module.ts'))) fail(`no app-routing.module.ts under ${angularApp}`);
if (!existsSync(join(prototypeRoot, 'source-refs.json'))) fail(`no source-refs.json under ${prototypeRoot}`);

function gitHead(dir) {
    try {
        return execFileSync('git', ['-C', dir, 'rev-parse', '--short=10', 'HEAD'], { encoding: 'utf8' }).trim();
    } catch {
        return undefined;
    }
}

// ─── Unit and area assignment ────────────────────────────────────────────────

/**
 * A unit is one Angular folder under `domain/settings/`, or one top-level domain feature. It is the size of one
 * agent run and one commit. An area groups units for ordering; it matches the epics in the viability report.
 */
const AREA_BY_UNIT = {
    domains: 'domains',
    applications: 'applications',
    forms: 'design',
    emails: 'design',
    texts: 'design',
    theme: 'design',
    flows: 'design',
    general: 'domain-settings',
    entrypoints: 'domain-settings',
    login: 'domain-settings',
    members: 'domain-settings',
    'web-protection': 'domain-settings',
    webauthn: 'domain-settings',
    secrets: 'domain-settings',
    'password-policies': 'domain-settings',
    'password-policy': 'domain-settings',
    account: 'domain-settings',
    'trust-domains': 'domain-settings',
    providers: 'identities',
    certificates: 'identities',
    factors: 'identities',
    'bot-detection': 'identities',
    'device-identifier': 'identities',
    resources: 'identities',
    scopes: 'protocols',
    extensionGrants: 'protocols',
    dcr: 'protocols',
    'oidc-profile': 'protocols',
    ciba: 'protocols',
    spiffe: 'protocols',
    'token-exchange': 'protocols',
    dpop: 'protocols',
    uma: 'protocols',
    cimd: 'protocols',
    saml2: 'protocols',
    users: 'users',
    groups: 'users',
    roles: 'users',
    scim: 'users',
    'self-service-account': 'users',
    audits: 'operations',
    alerts: 'operations',
    'authorization-engines': 'operations',
    'mcp-servers': 'mcp-servers',
    agents: 'agents',
    shell: 'shell',
};

const AREA_ORDER = [
    'shell',
    'domains',
    'applications',
    'identities',
    'protocols',
    'domain-settings',
    'users',
    'design',
    'operations',
    'mcp-servers',
    'agents',
    'organization',
    'unassigned',
];

/** The unit comes from the route path, because one Angular component can serve a domain route and an organization route. */
function unitOf(routePath) {
    const segments = routePath.split('/');
    if (segments[0] === 'settings') {
        return segments[1] && !segments[1].startsWith(':') ? `organization-${segments[1]}` : 'organization';
    }
    const domainIndex = segments.indexOf('domains');
    if (domainIndex === -1) return 'shell';
    const afterDomain = segments[domainIndex + 1];
    if (!afterDomain || afterDomain === 'new') return 'domains';
    const section = segments[domainIndex + 2];
    if (!section || section === 'dashboard') return 'domains';
    if (section === 'settings') return segments[domainIndex + 3] ?? 'general';
    return section;
}

function areaOf(unit) {
    if (unit.startsWith('organization')) return 'organization';
    return AREA_BY_UNIT[unit] ?? 'unassigned';
}

// ─── Angular route tree ──────────────────────────────────────────────────────

const routingFile = join(angularApp, 'app-routing.module.ts');
const source = ts.createSourceFile(routingFile, readFileSync(routingFile, 'utf8'), ts.ScriptTarget.Latest, true);
const importedFiles = new Map();
const routeArrays = new Map();

for (const statement of source.statements) {
    if (ts.isImportDeclaration(statement) && ts.isStringLiteral(statement.moduleSpecifier)) {
        const bindings = statement.importClause?.namedBindings;
        const specifier = statement.moduleSpecifier.text;
        if (bindings && ts.isNamedImports(bindings) && specifier.startsWith('.')) {
            for (const element of bindings.elements) {
                importedFiles.set(element.name.text, `${specifier.replace(/^\.\//, '')}.ts`);
            }
        }
    }
    if (ts.isVariableStatement(statement)) {
        for (const declaration of statement.declarationList.declarations) {
            const initializer = declaration.initializer;
            if (initializer && ts.isArrayLiteralExpression(initializer) && declaration.type?.getText() === 'Routes') {
                routeArrays.set(declaration.name.getText(), initializer);
            }
        }
    }
}

const rootRoutes = routeArrays.get('routes');
if (!rootRoutes) fail('no `routes: Routes` array in app-routing.module.ts');

function property(object, name) {
    const found = object.properties.find(p => ts.isPropertyAssignment(p) && p.name.getText() === name);
    return found?.initializer;
}

function text(node) {
    return node && (ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node)) ? node.text : undefined;
}

function textArray(node) {
    return node && ts.isArrayLiteralExpression(node) ? node.elements.map(e => text(e) ?? e.getText()) : undefined;
}

function identifierArray(node) {
    return node && ts.isArrayLiteralExpression(node) ? node.elements.map(e => e.getText()) : [];
}

function objectText(node) {
    if (!node || !ts.isObjectLiteralExpression(node)) return {};
    return Object.fromEntries(node.properties.filter(ts.isPropertyAssignment).map(p => [p.name.getText(), p.initializer.getText()]));
}

function isTrue(node) {
    return node?.kind === ts.SyntaxKind.TrueKeyword;
}

const routes = [];

function walk(array, parent) {
    for (const element of array.elements) {
        if (!ts.isObjectLiteralExpression(element)) continue;
        const segment = text(property(element, 'path')) ?? '';
        const path = [parent.path, segment].filter(Boolean).join('/');
        const data = property(element, 'data');
        const menu = data && ts.isObjectLiteralExpression(data) ? property(data, 'menu') : undefined;
        const perms = data && ts.isObjectLiteralExpression(data) ? property(data, 'perms') : undefined;
        const licence = data && ts.isObjectLiteralExpression(data) ? property(data, 'licenseOptions') : undefined;
        const types = data && ts.isObjectLiteralExpression(data) ? property(data, 'types') : undefined;
        const ownPerms = perms && ts.isObjectLiteralExpression(perms) ? (textArray(property(perms, 'only')) ?? []) : [];
        const menuEntry =
            menu && ts.isObjectLiteralExpression(menu)
                ? {
                      label: text(property(menu, 'label')),
                      section: text(property(menu, 'section')),
                      level: text(property(menu, 'level')),
                      hideInCloudMode: isTrue(property(menu, 'hideInCloudMode')) || undefined,
                  }
                : undefined;
        const hideInCloudMode = parent.hideInCloudMode || !!menuEntry?.hideInCloudMode;
        const effectivePerms = [...new Set([...parent.effectivePerms, ...ownPerms])];
        const childrenNode = property(element, 'children');
        const childrenArray =
            childrenNode && (ts.isArrayLiteralExpression(childrenNode) ? childrenNode : routeArrays.get(childrenNode.getText()));
        const component = property(element, 'component')?.getText();

        if (component) {
            routes.push({
                path,
                component,
                componentFile: importedFiles.get(component),
                role: childrenArray ? 'layout' : 'page',
                guards: identifierArray(property(element, 'canActivate')),
                resolvers: objectText(property(element, 'resolve')),
                perms: ownPerms,
                effectivePerms,
                licenseFeature: licence && ts.isObjectLiteralExpression(licence) ? property(licence, 'feature')?.getText() : undefined,
                menu: menuEntry,
                applicationTypes: types && ts.isObjectLiteralExpression(types) ? textArray(property(types, 'only')) : undefined,
                protocol: data && ts.isObjectLiteralExpression(data) ? text(property(data, 'protocol')) : undefined,
                requiresAgentIdentity:
                    data && ts.isObjectLiteralExpression(data) && isTrue(property(data, 'requiresAgentIdentity')) ? true : undefined,
                hideInCloudMode: hideInCloudMode || undefined,
            });
        }
        if (childrenArray) {
            walk(childrenArray, { path, effectivePerms, hideInCloudMode });
        }
    }
}

walk(rootRoutes, { path: '', effectivePerms: [], hideInCloudMode: false });

// ─── Angular components on disk ──────────────────────────────────────────────

function listComponents(dir, out = []) {
    for (const entry of readdirSync(dir)) {
        const full = join(dir, entry);
        if (statSync(full).isDirectory()) listComponents(full, out);
        else if (entry.endsWith('.component.ts')) out.push(relative(angularApp, full));
    }
    return out;
}

const angularComponents = listComponents(angularApp).sort();

// ─── Prototype provenance ────────────────────────────────────────────────────

const sourceRefs = JSON.parse(readFileSync(join(prototypeRoot, 'source-refs.json'), 'utf8'));
const prototypePages = sourceRefs.modules?.am?.pages ?? {};
const prototypePin = sourceRefs.sources?.[sourceRefs.modules?.am?.source]?.commit;
const prototypeBySourceFile = new Map();
for (const [name, entry] of Object.entries(prototypePages)) {
    for (const file of entry.sourceFiles ?? []) {
        if (!prototypeBySourceFile.has(file)) prototypeBySourceFile.set(file, []);
        prototypeBySourceFile.get(file).push(name);
    }
}

// ─── Previous inventory, for the curated fields ──────────────────────────────

const previous = existsSync(jsonPath) ? JSON.parse(readFileSync(jsonPath, 'utf8')) : { units: [] };
const previousUnits = new Map(previous.units.map(unit => [unit.id, unit]));
// Two routes can share a path, for example a layout and its index child, so the key carries the component too.
const pageKey = page => `${page.path}::${page.angularComponent}`;
const previousPages = new Map(previous.units.flatMap(unit => unit.pages.map(page => [pageKey(page), page])));

const UNIT_CURATED = ['status', 'class', 'order', 'blockers', 'notes', 'angularCommit'];
const PAGE_CURATED = ['status', 'class', 'reactFiles', 'blockers', 'notes', 'outOfScope'];

function curated(previousEntry, keys, defaults) {
    const out = { ...defaults };
    for (const key of keys) {
        if (previousEntry && previousEntry[key] !== undefined) out[key] = previousEntry[key];
    }
    return out;
}

// ─── Assemble the units ──────────────────────────────────────────────────────

const unitsById = new Map();
const routeComponentFiles = new Set();

for (const route of routes) {
    const unitId = unitOf(route.path);
    if (!unitsById.has(unitId)) {
        unitsById.set(unitId, { id: unitId, area: areaOf(unitId), pages: [] });
    }
    if (route.componentFile) routeComponentFiles.add(route.componentFile);
    const previousPage = previousPages.get(`${route.path}::${route.component}`);
    unitsById.get(unitId).pages.push({
        path: route.path,
        role: route.role,
        angularComponent: route.component,
        angularFile: route.componentFile,
        prototypePages: route.componentFile ? (prototypeBySourceFile.get(route.componentFile) ?? []) : [],
        menu: route.menu,
        perms: route.perms,
        effectivePerms: route.effectivePerms,
        licenseFeature: route.licenseFeature,
        guards: route.guards,
        resolvers: route.resolvers,
        applicationTypes: route.applicationTypes,
        protocol: route.protocol,
        requiresAgentIdentity: route.requiresAgentIdentity,
        hideInCloudMode: route.hideInCloudMode,
        ...curated(previousPage, PAGE_CURATED, { status: 'not-started', class: null, reactFiles: [], blockers: [], notes: '' }),
    });
}

const units = [...unitsById.values()]
    .map(unit => {
        const previousUnit = previousUnits.get(unit.id);
        const prototypeSet = new Set(unit.pages.flatMap(page => page.prototypePages));
        const angularSet = new Set(unit.pages.map(page => page.angularFile).filter(Boolean));
        return {
            ...unit,
            angularFiles: [...angularSet].sort(),
            prototypePages: [...prototypeSet].sort(),
            permissions: [...new Set(unit.pages.flatMap(page => page.effectivePerms))].sort(),
            licenseFeatures: [...new Set(unit.pages.map(page => page.licenseFeature).filter(Boolean))].sort(),
            ...curated(previousUnit, UNIT_CURATED, {
                status: 'not-started',
                class: null,
                order: null,
                blockers: [],
                notes: '',
                angularCommit: null,
            }),
        };
    })
    .sort((a, b) => AREA_ORDER.indexOf(a.area) - AREA_ORDER.indexOf(b.area) || a.id.localeCompare(b.id));

// Angular components that no route and no prototype page references: the shared-kit candidates.
const referencedByPrototype = new Set(prototypeBySourceFile.keys());
const uncoveredComponents = angularComponents.filter(file => !routeComponentFiles.has(file) && !referencedByPrototype.has(file));

// Prototype entries whose Angular sources match no route component, for example layouts and the landing page.
const routedPrototypePages = new Set(units.flatMap(unit => unit.prototypePages));
const unroutedPrototypePages = Object.entries(prototypePages)
    .filter(([name]) => !routedPrototypePages.has(name))
    .map(([name, entry]) => ({
        name,
        prototypeFile: entry.prototypeFile,
        role: entry.role ?? 'page',
        sourceFiles: entry.sourceFiles ?? [],
    }));

const inventory = {
    generatedAt: new Date().toISOString().slice(0, 10),
    angularCommit: gitHead(amRoot),
    prototypeCommit: gitHead(prototypeRoot),
    prototypePin,
    counts: {
        angularRoutesWithComponent: routes.length,
        angularComponents: angularComponents.length,
        prototypeEntries: Object.keys(prototypePages).length,
        units: units.length,
        uncoveredComponents: uncoveredComponents.length,
    },
    units,
    uncoveredComponents,
    unroutedPrototypePages,
};

// ─── Write ───────────────────────────────────────────────────────────────────

mkdirSync(outDir, { recursive: true });
writeFileSync(jsonPath, `${JSON.stringify(inventory, null, 4)}\n`);
writeFileSync(mdPath, renderMarkdown(inventory));
console.log(
    `build-inventory: ${routes.length} routes, ${units.length} units, ${uncoveredComponents.length} uncovered components → ${relative(consoleRoot, jsonPath)}, ${relative(consoleRoot, mdPath)}`,
);

// ─── Markdown view ───────────────────────────────────────────────────────────

function cell(value) {
    if (value === undefined || value === null || value === '') return '';
    if (Array.isArray(value)) return value.join(', ');
    return String(value).replace(/\|/g, '\\|');
}

function code(value) {
    return value ? `\`${value}\`` : '';
}

function countBy(items, key) {
    const out = {};
    for (const item of items) out[item[key]] = (out[item[key]] ?? 0) + 1;
    return out;
}

function renderMarkdown(data) {
    const lines = [];
    lines.push('# Migration inventory');
    lines.push('');
    lines.push(
        `Generated by \`scripts/migration/build-inventory.mjs\` on ${data.generatedAt}. Do not edit this file. Edit the curated fields in \`inventory.json\` and run the script again.`,
    );
    lines.push('');
    lines.push('| Input | Value |');
    lines.push('|---|---|');
    lines.push(`| Angular checkout | \`${data.angularCommit ?? '?'}\` |`);
    lines.push(`| Prototype checkout | \`${data.prototypeCommit ?? '?'}\` |`);
    lines.push(`| Prototype pinned to Angular commit | \`${data.prototypePin ?? '?'}\` |`);
    lines.push(`| Angular routes with a component | ${data.counts.angularRoutesWithComponent} |`);
    lines.push(`| Angular components on disk | ${data.counts.angularComponents} |`);
    lines.push(`| Prototype entries | ${data.counts.prototypeEntries} |`);
    lines.push(`| Units | ${data.counts.units} |`);
    lines.push(`| Uncovered Angular components | ${data.counts.uncoveredComponents} |`);
    lines.push('');

    const allPages = data.units.flatMap(unit => unit.pages);
    const byStatus = countBy(allPages, 'status');
    lines.push('## Status');
    lines.push('');
    lines.push('| Status | Pages |');
    lines.push('|---|---|');
    for (const [status, count] of Object.entries(byStatus).sort()) lines.push(`| ${status} | ${count} |`);
    lines.push('');

    lines.push('## Units');
    lines.push('');
    lines.push('| Area | Unit | Routes | Prototype pages | Permissions | Licence | Class | Status | Order | Blockers |');
    lines.push('|---|---|---|---|---|---|---|---|---|---|');
    for (const unit of data.units) {
        lines.push(
            `| ${unit.area} | ${code(unit.id)} | ${unit.pages.length} | ${unit.prototypePages.length} | ${unit.permissions.length} | ${cell(unit.licenseFeatures)} | ${cell(unit.class)} | ${unit.status} | ${cell(unit.order)} | ${cell(unit.blockers)} |`,
        );
    }
    lines.push('');

    for (const unit of data.units) {
        lines.push(`### ${unit.area} / ${unit.id}`);
        lines.push('');
        if (unit.notes) {
            lines.push(unit.notes);
            lines.push('');
        }
        lines.push('| Route | Angular component | Prototype pages | Effective permissions | Licence | Flags | Class | Status |');
        lines.push('|---|---|---|---|---|---|---|---|');
        for (const page of unit.pages) {
            const flags = [
                page.role === 'layout' ? 'layout' : '',
                page.hideInCloudMode ? 'hidden in cloud' : '',
                page.applicationTypes ? `types: ${page.applicationTypes.join('/')}` : '',
                page.protocol ? `protocol: ${page.protocol}` : '',
                page.requiresAgentIdentity ? 'agent only' : '',
                page.outOfScope ? `out of scope: ${page.outOfScope}` : '',
            ].filter(Boolean);
            lines.push(
                `| ${code(page.path)} | ${code(page.angularFile ?? page.angularComponent)} | ${cell(page.prototypePages)} | ${cell(page.effectivePerms)} | ${cell(page.licenseFeature)} | ${cell(flags)} | ${cell(page.class)} | ${page.status} |`,
            );
        }
        lines.push('');
    }

    lines.push('## Uncovered Angular components');
    lines.push('');
    lines.push('Components that no route and no prototype page references. Most are shared parts for the kit.');
    lines.push('');
    for (const file of data.uncoveredComponents) lines.push(`- \`${file}\``);
    lines.push('');

    lines.push('## Prototype entries with no Angular route');
    lines.push('');
    lines.push('| Prototype | Role | Angular sources |');
    lines.push('|---|---|---|');
    for (const entry of data.unroutedPrototypePages) {
        lines.push(`| ${code(entry.prototypeFile)} | ${entry.role} | ${cell(entry.sourceFiles)} |`);
    }
    lines.push('');
    return `${lines.join('\n')}\n`;
}
