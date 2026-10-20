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
import type { Certificate } from '../../lib/api/certificates';
import type { DataSource } from '../../lib/api/data-sources';

/** An AM plugin schema, written for angular-schema-form, before `adaptAmSchema`. */
type SchemaNode = Record<string, unknown>;
type Properties = Record<string, SchemaNode>;

export const MONGO_IDP_TYPE = 'mongo-am-idp';
const PINNED_STORAGE_FIELDS = ['database', 'usersCollection'];
const PINNED_STORAGE_TOGGLE = 'useSystemCluster';
const CREATION_HINT = 'The platform sets this value when "use system cluster" is selected.';
const IMMUTABLE_HINT = 'Once saved, this option cannot be changed.';

/** The OpenID Connect and LDAP schema versions that accept a client certificate for mTLS. */
const MTLS_SCHEMAS = [
    { id: 'urn:jsonschema:io:gravitee:am:identityprovider:oauth2:OAuth2GenericIdentityProvider', version: '05-2024' },
    { id: 'urn:jsonschema:com:graviteesource:am:identityprovider:ldap:LdapIdentityProviderConfiguration', version: '07-2024' },
];

export interface SchemaContext {
    readonly type: string;
    readonly certificates: readonly Certificate[];
    readonly dataSources: readonly DataSource[];
    /** The installation's `systemClusterRestricted`. */
    readonly systemClusterRestricted: boolean;
}

function propertiesOf(schema: SchemaNode): Properties {
    return (schema.properties ?? {}) as Properties;
}

function withProperties(schema: SchemaNode, properties: Properties): SchemaNode {
    return { ...schema, properties };
}

function withCertificates(schema: SchemaNode, certificates: readonly Certificate[]): SchemaNode {
    const mtls = certificates.filter(certificate => certificate.usage?.includes('mtls'));
    const supported = MTLS_SCHEMAS.some(known => known.id === schema.id && known.version === schema.version);
    const properties = propertiesOf(schema);
    if (mtls.length === 0 || !supported || !properties.clientAuthenticationCertificate) {
        return schema;
    }
    return withProperties(schema, {
        ...properties,
        clientAuthenticationCertificate: {
            ...properties.clientAuthenticationCertificate,
            enum: mtls.map(certificate => certificate.id),
            titleMap: Object.fromEntries(mtls.map(certificate => [certificate.id, certificate.name])),
            readonly: false,
        },
    });
}

function withDataSources(node: SchemaNode, name: string, dataSources: readonly DataSource[]): SchemaNode {
    let adapted = node;
    if (node.properties) {
        adapted = withProperties(
            adapted,
            Object.fromEntries(Object.entries(propertiesOf(node)).map(([key, child]) => [key, withDataSources(child, key, dataSources)])),
        );
    }
    const items = node.items as SchemaNode | undefined;
    if (node.type === 'array' && items?.properties) {
        adapted = { ...adapted, items: withDataSources(items, '', dataSources) };
    }
    if (node.widget !== 'datasource' && name !== 'datasource') {
        return adapted;
    }
    return dataSources.length > 0
        ? {
              ...adapted,
              enum: dataSources.map(dataSource => dataSource.id),
              titleMap: Object.fromEntries(dataSources.map(dataSource => [dataSource.id, dataSource.name])),
          }
        : { ...adapted, readonly: true };
}

function mapFields(schema: SchemaNode, fields: readonly string[], change: (property: SchemaNode) => SchemaNode): SchemaNode {
    const properties = propertiesOf(schema);
    const present = fields.filter(field => properties[field]);
    if (present.length === 0) {
        return schema;
    }
    return withProperties(schema, { ...properties, ...Object.fromEntries(present.map(field => [field, change(properties[field])])) });
}

/** The Angular `enrichFormWithSystemClusterDisclaimer`. Graphene renders a boolean's description, so the hint goes there. */
function withDisclaimer(schema: SchemaNode): SchemaNode {
    return mapFields(schema, [PINNED_STORAGE_TOGGLE], property => ({
        ...property,
        description: property.description ? `${String(property.description)} ${IMMUTABLE_HINT}` : IMMUTABLE_HINT,
    }));
}

/** The creation schema: the Angular step 2 enrichers, then the data source widgets. */
export function enrichCreationSchema(schema: SchemaNode, context: SchemaContext): SchemaNode {
    let enriched = withCertificates(schema, context.certificates);
    if (context.systemClusterRestricted && context.type === MONGO_IDP_TYPE) {
        enriched = mapFields(enriched, PINNED_STORAGE_FIELDS, property => ({
            ...property,
            description: property.description ? `${String(property.description)} ${CREATION_HINT}` : CREATION_HINT,
        }));
        enriched = withDisclaimer(enriched);
    }
    return withDataSources(enriched, '', context.dataSources);
}

/** The fields a MongoDB provider may not change, as the Angular `ProviderSettingsComponent.withPinnedStorage` lists them. */
export function pinnedFields(type: string, providerRestricted: boolean, installationRestricted: boolean): string[] {
    if (type !== MONGO_IDP_TYPE) {
        return [];
    }
    if (providerRestricted) {
        return [PINNED_STORAGE_TOGGLE, ...PINNED_STORAGE_FIELDS];
    }
    return installationRestricted ? [PINNED_STORAGE_TOGGLE] : [];
}

/** The settings schema: the Angular settings enrichers, the default rule, then the data source widgets. */
export function enrichSettingsSchema(schema: SchemaNode, context: SchemaContext & { readonly providerRestricted: boolean }): SchemaNode {
    let enriched = withCertificates(schema, context.certificates);
    enriched = mapFields(enriched, pinnedFields(context.type, context.providerRestricted, context.systemClusterRestricted), property => ({
        ...property,
        readonly: true,
    }));
    if (context.systemClusterRestricted && context.type === MONGO_IDP_TYPE) {
        enriched = withDisclaimer(enriched);
    }
    // Angular blanks every default, so a stored null is not replaced on edit. Booleans get theirs through `withBooleanDefaults`.
    enriched = withProperties(
        enriched,
        Object.fromEntries(Object.entries(propertiesOf(enriched)).map(([key, property]) => [key, { ...property, default: undefined }])),
    );
    return withDataSources(enriched, '', context.dataSources);
}

/** Fills an unset boolean with its schema default (AM-686). Every other unset field stays unset. */
export function withBooleanDefaults(schema: SchemaNode, configuration: Record<string, unknown>): Record<string, unknown> {
    const defaults = Object.entries(propertiesOf(schema))
        .filter(
            ([key, property]) =>
                property.type === 'boolean' && property.default && (configuration[key] === null || configuration[key] === undefined),
        )
        .map(([key, property]) => [key, property.default]);
    return { ...configuration, ...Object.fromEntries(defaults) };
}
