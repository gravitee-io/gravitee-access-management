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
import type { Resource, ResourcePlugin } from '../../lib/api/resources';

type SchemaNode = Record<string, unknown>;

/** The Angular step 1 names of the built-in factor plugins. */
const PLUGIN_NAMES: Record<string, string> = {
    'otp-am-factor': 'Generic OTP Factor',
    'email-am-factor': 'Email Factor',
    'sms-am-factor': 'SMS Factor',
    'call-am-factor': 'Call Factor',
    'http-am-factor': 'HTTP Factor',
    'recovery-code-am-factor': 'Recovery Code Factor',
    'fido2-am-factor': 'FIDO2 Factor',
    'mock-am-factor': 'MOCK Factor',
};

export function factorPluginName(id: string, fallback: string): string {
    return PLUGIN_NAMES[id] ?? fallback;
}

export function isFido2(type: string): boolean {
    return type.includes('fido2-am-factor');
}

/** The `factorType` default of a factor schema, which Angular copies into the factor. */
export function schemaFactorType(schema: SchemaNode): string | undefined {
    const properties = schema.properties as Record<string, SchemaNode> | undefined;
    return properties?.factorType?.default as string | undefined;
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

function applyForGraviteeResource(property: SchemaNode, usable: readonly Resource[], name?: string): void {
    if (property.widget !== 'graviteeResource' && name !== 'graviteeResource') {
        return;
    }
    // The select replaces the widget, so the schema adapter does not report it as unsupported.
    delete property.widget;
    if (usable.length > 0) {
        property.enum = usable.map(resource => resource.id);
        property['x-schema-form'] = { type: 'select', titleMap: Object.fromEntries(usable.map(resource => [resource.id, resource.name])) };
    } else {
        property.readonly = true;
    }
}

function applyResourceSelection(property: SchemaNode, usable: readonly Resource[], name?: string): void {
    const items = property.items as SchemaNode | undefined;
    if (property.type === 'array' && items?.properties) {
        for (const child of Object.values(items.properties as Record<string, SchemaNode>)) {
            applyResourceSelection(child, usable);
        }
    }
    applyForGraviteeResource(property, usable, name);
}

/**
 * The Angular `applyForGraviteeResource`: a `graviteeResource` field becomes a select of the domain resources whose
 * plugin serves the factor's category, and is read-only when there are none. Returns a copy of the schema.
 */
export function withResourceSelect(
    schema: SchemaNode,
    factorCategory: string | undefined,
    resources: readonly Resource[],
    resourcePlugins: readonly ResourcePlugin[],
): SchemaNode {
    const categories = new Map(resourcePlugins.map(plugin => [plugin.id, plugin.categories ?? []]));
    const usable = resources.filter(
        resource => factorCategory === 'any' || (categories.get(resource.type) ?? []).some(category => category === factorCategory),
    );
    const copy = structuredClone(schema);
    for (const [name, property] of Object.entries((copy.properties ?? {}) as Record<string, SchemaNode>)) {
        applyResourceSelection(property, usable, name);
    }
    return copy;
}
