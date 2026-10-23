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
export const CROSS_APP_ACCESS = 'xaa-am-extension-grant';

/** The Angular `displayType` map of the list and step 1. Another plugin shows the fallback: its id, or its catalog name. */
const TYPE_NAMES: Record<string, string> = {
    'jwtbearer-am-extension-grant': 'JWT Bearer',
    [CROSS_APP_ACCESS]: 'Cross App Access',
};

/** The Angular hint of the read-only grant type field. */
export const GRANT_TYPE_DESCRIPTION =
    'OAuth 2.0 grant type. The client uses an extension grant type by specifying the grant type value of the "grant_type" parameter of the token endpoint.';

export function extensionGrantTypeName(type: string, fallback = type): string {
    return TYPE_NAMES[type] ?? fallback;
}

/** The grant type the plugin schema declares as the default of `properties.grantType`, as the Angular step 2 reads it. */
export function schemaGrantType(schema: Record<string, unknown>): string | undefined {
    const properties = schema.properties as Record<string, { default?: unknown } | undefined> | undefined;
    const value = properties?.grantType?.default;
    return typeof value === 'string' ? value : undefined;
}

/**
 * The plugin schema without `properties.grantType`. The Angular form does not render that untyped property, so the
 * configuration it sends has no `grantType` key. The page shows the grant type in a field of its own.
 */
export function configurationSchema(schema: Record<string, unknown>): Record<string, unknown> {
    const { grantType: _grantType, ...properties } = (schema.properties ?? {}) as Record<string, unknown>;
    return schema.properties ? { ...schema, properties } : schema;
}

/**
 * The configuration as the Angular form sends it. The Angular form leaves out an empty array whose schema default is
 * absent or empty, where the Graphene form holds it as an empty array. A cleared list with a non-empty default stays.
 */
export function toConfiguration(schema: { readonly properties?: unknown }, config: Record<string, unknown>): string {
    const properties = (schema.properties ?? {}) as Record<string, { default?: unknown } | undefined>;
    const untouched = (key: string, value: unknown) => {
        const fallback = properties[key]?.default;
        return Array.isArray(value) && value.length === 0 && (fallback === undefined || (Array.isArray(fallback) && fallback.length === 0));
    };
    return JSON.stringify(Object.fromEntries(Object.entries(config).filter(([key, value]) => !untouched(key, value))));
}

/** The configuration with an empty array for each array property it lacks, as the Graphene form holds it. */
export function toFormConfiguration(schema: { readonly properties?: unknown }, config: Record<string, unknown>): Record<string, unknown> {
    const properties = (schema.properties ?? {}) as Record<string, { type?: unknown } | undefined>;
    const arrays = Object.keys(properties).filter(key => properties[key]?.type === 'array' && config[key] === undefined);
    return { ...Object.fromEntries(arrays.map(key => [key, []])), ...config };
}
