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
import type { JsonSchema } from '@gravitee/graphene-core';

type SchemaNode = Record<string, unknown>;

export interface AdaptedSchema {
    readonly schema: JsonSchema;
    /** Dotted paths of fields that use a widget Graphene cannot render; they fall back to a plain input. */
    readonly unsupported: string[];
}

// angular-schema-form keywords with no Graphene meaning, dropped after translation.
const DROPPED_KEYWORDS = ['id', 'widget', 'x-schema-form', 'titleMap', 'htmlClass', 'readonly', 'sensitive', 'sensitive-uri'];

const FORMAT_BY_WIDGET: Record<string, string> = { password: 'password', textarea: 'text' };

function titleMapOf(node: SchemaNode): Record<string, string> | undefined {
    const formOptions = node['x-schema-form'] as SchemaNode | undefined;
    return (formOptions?.titleMap ?? node.titleMap) as Record<string, string> | undefined;
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
 * `readOnly`, and `sensitive` fields render as passwords.
 */
export function adaptAmSchema(schema: SchemaNode): AdaptedSchema {
    const unsupported: string[] = [];
    return { schema: adaptNode(schema, '', unsupported) as JsonSchema, unsupported };
}
