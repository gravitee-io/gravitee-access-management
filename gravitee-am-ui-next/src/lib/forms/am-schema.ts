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

export interface AdaptedSchema {
    readonly schema: JsonSchema;
    /** Dotted paths of fields that use a widget Graphene cannot render; they fall back to a plain input. */
    readonly unsupported: string[];
}

// angular-schema-form keywords with no Graphene meaning, dropped after translation.
const DROPPED_KEYWORDS = ['id', 'widget', 'x-schema-form', 'titleMap', 'htmlClass', 'readonly', 'sensitive', 'sensitive-uri', 'condition'];

const FORMAT_BY_WIDGET: Record<string, string> = { password: 'password', textarea: 'text' };

function titleMapOf(node: SchemaNode): Record<string, string> | undefined {
    const formOptions = node['x-schema-form'] as SchemaNode | undefined;
    return (formOptions?.titleMap ?? node.titleMap) as Record<string, string> | undefined;
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

/**
 * The angular-schema-form `condition` as a Graphene `displayIf`, or undefined when Graphene cannot express it. A
 * condition is a conjunction of `model.x`, `model.x === literal`, and `(!model.x || model.x === literal)`.
 */
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

function adaptNode(node: SchemaNode, path: string, unsupported: string[]): SchemaNode {
    const adapted: SchemaNode = Object.fromEntries(Object.entries(node).filter(([keyword]) => !DROPPED_KEYWORDS.includes(keyword)));

    const widget = node.widget as string | undefined;
    if (widget && FORMAT_BY_WIDGET[widget]) {
        adapted.format = FORMAT_BY_WIDGET[widget];
    } else if (widget) {
        unsupported.push(`${path} (widget "${widget}")`);
    }
    if (node.sensitive === true && adapted.format === undefined) {
        adapted.format = 'password';
    }
    if (node.readonly === true) {
        adapted.readOnly = true;
    }
    const titleMap = titleMapOf(node);
    if (titleMap) {
        adapted.gioConfig = { ...(adapted.gioConfig as SchemaNode | undefined), enumLabelMap: titleMap };
    }
    if (node.condition !== undefined) {
        const displayIf = displayIfOf(node.condition);
        if (displayIf) {
            adapted.gioConfig = { ...(adapted.gioConfig as SchemaNode | undefined), displayIf };
        } else {
            unsupported.push(`${path} (condition)`);
        }
    }

    if (node.properties) {
        adapted.properties = Object.fromEntries(
            Object.entries(node.properties as Record<string, SchemaNode>).map(([key, child]) => [
                key,
                adaptNode(child, path ? `${path}.${key}` : key, unsupported),
            ]),
        );
    }
    if (node.items && typeof node.items === 'object' && !Array.isArray(node.items)) {
        adapted.items = adaptNode(node.items as SchemaNode, `${path}[]`, unsupported);
    }
    return adapted;
}

/**
 * Translates an AM plugin schema, written for angular-schema-form, into the dialect of Graphene's JsonSchemaForm:
 * `widget: password|textarea` becomes a `format`, `titleMap` becomes `gioConfig.enumLabelMap`, `readonly` becomes
 * `readOnly`, `sensitive` fields render as passwords, and a `condition` becomes `gioConfig.displayIf`.
 */
export function adaptAmSchema(schema: SchemaNode): AdaptedSchema {
    const unsupported: string[] = [];
    return { schema: adaptNode(schema, '', unsupported) as JsonSchema, unsupported };
}

/**
 * The configuration over the schema defaults, as the Angular form fills them. An undefined value, which the wizard form
 * holds for an untouched field, does not hide its default.
 */
export function withSchemaDefaults(schema: JsonSchema, configuration: Record<string, unknown>): Record<string, unknown> {
    return {
        ...(extractDefaults(schema) as Record<string, unknown>),
        ...Object.fromEntries(Object.entries(configuration).filter(([, value]) => value !== undefined)),
    };
}
