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
import { extractDefaults, type JsonSchema } from '@gravitee/graphene-core';

type SchemaNode = Record<string, unknown>;

export interface SchemaResource {
    readonly id: string;
    readonly name: string;
}

/** A top-level `widget: file` property. Graphene has no file field. */
export interface SchemaFile {
    readonly key: string;
    readonly title?: string;
    readonly description?: string;
    readonly required: boolean;
}

export interface AmSchemaOptions {
    /** The choices of a `graviteeResource` field. With none, the field is read-only. */
    readonly resources?: readonly SchemaResource[];
    /** Keyed by `itemsDictionaryEndpoint`. */
    readonly dictionaries?: Readonly<Record<string, readonly string[]>>;
}

export interface AdaptedSchema {
    readonly schema: JsonSchema;
    /** The `if`/`then` blocks of the top-level `allOf`, each as a schema. Graphene's resolver ignores them inside `allOf`. */
    readonly conditions: readonly JsonSchema[];
    readonly files: readonly SchemaFile[];
    readonly dictionaryEndpoints: readonly string[];
    /** Dotted paths of the widgets and conditions that Graphene cannot express. */
    readonly unsupported: readonly string[];
}

interface AdaptContext {
    readonly options: AmSchemaOptions;
    readonly dictionaryEndpoints: string[];
    readonly unsupported: string[];
}

const DROPPED_KEYWORDS = [
    'id',
    'widget',
    'x-schema-form',
    'titleMap',
    'htmlClass',
    'readonly',
    'sensitive',
    'sensitive-uri',
    'condition',
    'itemsDictionaryEndpoint',
    'onEmptySelectionMessage',
];

const FORMAT_BY_WIDGET: Record<string, string> = { password: 'password', textarea: 'text' };

/** angular-schema-form reads a form option from the property or from its `x-schema-form`. */
function formOption(node: SchemaNode, key: string): unknown {
    return node[key] ?? (node['x-schema-form'] as SchemaNode | undefined)?.[key];
}

function widgetOf(node: SchemaNode): string | undefined {
    return (node.widget ?? (node['x-schema-form'] as SchemaNode | undefined)?.type) as string | undefined;
}

/** The Angular json-schema-form does not render a property without a type, so its value is not in the configuration. */
function isRendered(node: SchemaNode): boolean {
    return ['type', '$ref', 'oneOf', 'anyOf', 'allOf'].some(keyword => node[keyword] !== undefined);
}

type Literal = string | number | boolean;

/** The values `!model.x` accepts. Graphene compares a `$eq` list with `includes`, so `undefined` matches an unset field. */
const FALSY = [undefined, null, '', 0, false];

const MODEL_PATH = String.raw`model\.([\w.]+)`;
const LITERAL = String.raw`'([^']*)'|"([^"]*)"|(-?\d+(?:\.\d+)?)|(true|false)`;
const TRUTHY_TERM = new RegExp(`^${MODEL_PATH}$`);
const EQUALS_TERM = new RegExp(`^${MODEL_PATH}\\s*===?\\s*(?:${LITERAL})$`);
const UNSET_OR_EQUALS_TERM = new RegExp(`^!${MODEL_PATH}\\s*\\|\\|\\s*model\\.\\1\\s*===?\\s*(?:${LITERAL})$`);

function literalOf([quoted, doubleQuoted, number, boolean]: (string | undefined)[]): Literal {
    if (quoted !== undefined || doubleQuoted !== undefined) {
        return (quoted ?? doubleQuoted)!;
    }
    return number !== undefined ? Number(number) : boolean === 'true';
}

/** The `&&` operands of an expression, split at depth 0, each without its outer parentheses. */
function conjunctionTerms(expression: string): string[] {
    const terms: string[] = [];
    let depth = 0;
    let start = 0;
    for (let i = 0; i < expression.length; i++) {
        const char = expression[i];
        depth += char === '(' ? 1 : char === ')' ? -1 : 0;
        if (depth === 0 && expression.startsWith('&&', i)) {
            terms.push(expression.slice(start, i));
            start = i + 2;
        }
    }
    terms.push(expression.slice(start));
    return terms.map(term =>
        term
            .trim()
            .replace(/^\((.*)\)$/s, '$1')
            .trim(),
    );
}

/** Handles a conjunction of `model.x`, `model.x === literal`, and `(!model.x || model.x === literal)`. */
function displayIfOf(condition: unknown): { $eq: Record<string, Literal | unknown[]> } | undefined {
    const body = typeof condition === 'string' ? condition : (condition as SchemaNode | undefined)?.functionBody;
    if (typeof body !== 'string') {
        return undefined;
    }
    const $eq: Record<string, Literal | unknown[]> = {};
    for (const term of conjunctionTerms(
        body
            .trim()
            .replace(/^return\s+/, '')
            .replace(/;$/, ''),
    )) {
        let match: RegExpMatchArray | null;
        let key: string;
        let value: Literal | unknown[];
        if ((match = term.match(TRUTHY_TERM))) {
            [key, value] = [match[1], true];
        } else if ((match = term.match(EQUALS_TERM))) {
            [key, value] = [match[1], literalOf(match.slice(2))];
        } else if ((match = term.match(UNSET_OR_EQUALS_TERM))) {
            [key, value] = [match[1], [literalOf(match.slice(2)), ...FALSY]];
        } else {
            return undefined;
        }
        const path = `value.${key}`;
        if (path in $eq && $eq[path] !== value) {
            return undefined;
        }
        $eq[path] = value;
    }
    return { $eq };
}

function applyResources(adapted: SchemaNode, resources: readonly SchemaResource[]): void {
    if (resources.length === 0) {
        adapted.readOnly = true;
        return;
    }
    adapted.enum = resources.map(resource => resource.id);
    adapted.gioConfig = {
        ...(adapted.gioConfig as SchemaNode | undefined),
        enumLabelMap: Object.fromEntries(resources.map(resource => [resource.id, resource.name])),
    };
}

function applyMultiselect(node: SchemaNode, adapted: SchemaNode, context: AdaptContext): void {
    const emptySelectionMessage = formOption(node, 'onEmptySelectionMessage') as string | undefined;
    if (emptySelectionMessage) {
        adapted.description = [node.description, emptySelectionMessage].filter(Boolean).join(' ');
    }
    const endpoint = formOption(node, 'itemsDictionaryEndpoint') as string | undefined;
    if (endpoint) {
        context.dictionaryEndpoints.push(endpoint);
    }
    const items = endpoint ? (context.options.dictionaries?.[endpoint] ?? []) : ((node.enum as string[] | undefined) ?? []);
    delete adapted.enum;
    adapted.uniqueItems = true;
    adapted.items = { ...(adapted.items as SchemaNode | undefined), enum: [...items] };
}

function adaptProperties(node: SchemaNode, adapted: SchemaNode, path: string, context: AdaptContext, skip: (key: string) => boolean): void {
    const kept = Object.entries(node.properties as Record<string, SchemaNode>).filter(([key, child]) => isRendered(child) && !skip(key));
    adapted.properties = Object.fromEntries(
        kept.map(([key, child]) => [key, adaptNode(child, key, path ? `${path}.${key}` : key, context)]),
    );
    if (Array.isArray(node.required)) {
        adapted.required = node.required.filter(key => kept.some(([keptKey]) => keptKey === key));
    }
}

function adaptNode(node: SchemaNode, key: string, path: string, context: AdaptContext): SchemaNode {
    const adapted: SchemaNode = Object.fromEntries(Object.entries(node).filter(([keyword]) => !DROPPED_KEYWORDS.includes(keyword)));

    const widget = widgetOf(node);
    if (node.items && typeof node.items === 'object' && !Array.isArray(node.items)) {
        adapted.items = adaptNode(node.items as SchemaNode, '', `${path}[]`, context);
    }
    // Only a top-level property matches by name.
    if (widget === 'graviteeResource' || (key === 'graviteeResource' && path === key)) {
        applyResources(adapted, context.options.resources ?? []);
    } else if (widget === 'graviteeMultiselect') {
        applyMultiselect(node, adapted, context);
    } else if (widget && FORMAT_BY_WIDGET[widget]) {
        adapted.format = FORMAT_BY_WIDGET[widget];
    } else if (widget && widget !== 'select') {
        context.unsupported.push(`${path} (widget "${widget}")`);
    }
    if (node.sensitive === true && adapted.format === undefined) {
        adapted.format = 'password';
    }
    if (node.readonly === true) {
        adapted.readOnly = true;
    }
    const enumItems = adapted.type === 'array' && Array.isArray((adapted.items as SchemaNode | undefined)?.enum);
    // angular-schema-form renders an array of enum items as checkboxes. Graphene has no checkbox group, so the field is a multiselect.
    if (enumItems) {
        adapted.uniqueItems = true;
    }
    const titleMap = formOption(node, 'titleMap') as Record<string, string> | undefined;
    if (titleMap) {
        // Graphene reads the labels of a multiselect from its items.
        const target = enumItems ? (adapted.items as SchemaNode) : adapted;
        target.gioConfig = { ...(target.gioConfig as SchemaNode | undefined), enumLabelMap: titleMap };
    }
    if (node.condition !== undefined) {
        const displayIf = displayIfOf(node.condition);
        if (displayIf) {
            adapted.gioConfig = { ...(adapted.gioConfig as SchemaNode | undefined), displayIf };
        } else {
            context.unsupported.push(`${path} (condition)`);
        }
    }
    if (node.properties) {
        adaptProperties(node, adapted, path, context, () => false);
    }
    return adapted;
}

/** Translates an angular-schema-form plugin schema into the Graphene `JsonSchemaForm` dialect. */
export function adaptAmSchema(schema: SchemaNode, options: AmSchemaOptions = {}): AdaptedSchema {
    const context: AdaptContext = { options, dictionaryEndpoints: [], unsupported: [] };
    const properties = (schema.properties ?? {}) as Record<string, SchemaNode>;
    const required = Array.isArray(schema.required) ? (schema.required as string[]) : [];
    const files: SchemaFile[] = Object.entries(properties)
        .filter(([, property]) => widgetOf(property) === 'file')
        .map(([key, property]) => ({
            key,
            title: property.title as string | undefined,
            description: property.description as string | undefined,
            required: required.includes(key),
        }));

    const adapted: SchemaNode = Object.fromEntries(Object.entries(schema).filter(([keyword]) => !DROPPED_KEYWORDS.includes(keyword)));
    if (schema.properties) {
        adaptProperties(schema, adapted, '', context, key => files.some(file => file.key === key));
    }
    const allOf = Array.isArray(schema.allOf) ? (schema.allOf as SchemaNode[]) : [];
    const conditions = allOf.filter(entry => entry.if !== undefined).map(entry => ({ type: 'object', ...entry }) as JsonSchema);
    if (conditions.length > 0) {
        const rest = allOf.filter(entry => entry.if === undefined);
        if (rest.length > 0) {
            adapted.allOf = rest;
        } else {
            delete adapted.allOf;
        }
    }
    return {
        schema: adapted as JsonSchema,
        conditions,
        files,
        dictionaryEndpoints: context.dictionaryEndpoints,
        unsupported: context.unsupported,
    };
}

/** An undefined value keeps its default. */
export function withSchemaDefaults(schema: JsonSchema, configuration: Record<string, unknown>): Record<string, unknown> {
    return {
        ...(extractDefaults(schema) as Record<string, unknown>),
        ...Object.fromEntries(Object.entries(configuration).filter(([, value]) => value !== undefined)),
    };
}

/** The category `any` matches every resource. */
export function compatibleResources<R extends SchemaResource & { readonly type: string }>(
    category: string,
    resources: readonly R[],
    resourcePlugins: readonly { readonly id: string; readonly categories?: readonly string[] }[],
): R[] {
    return resources.filter(
        resource =>
            category === 'any' || (resourcePlugins.find(plugin => plugin.id === resource.type)?.categories ?? []).includes(category),
    );
}
