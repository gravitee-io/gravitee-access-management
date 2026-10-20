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
import { enrichCreationSchema, enrichSettingsSchema, pinnedFields, withBooleanDefaults } from '../provider-schema';

const context = { type: 'mongo-am-idp', certificates: [], dataSources: [], systemClusterRestricted: false };

describe('enrichCreationSchema', () => {
    it('offers the mTLS certificates to a supported OpenID Connect schema', () => {
        const schema = {
            id: 'urn:jsonschema:io:gravitee:am:identityprovider:oauth2:OAuth2GenericIdentityProvider',
            version: '05-2024',
            properties: { clientAuthenticationCertificate: { type: 'string', readonly: true } },
        };
        const certificates = [
            { id: 'c1', name: 'mTLS cert', type: 'pkcs12', usage: ['mtls'] },
            { id: 'c2', name: 'Signing cert', type: 'pkcs12', usage: ['sig'] },
        ];

        const enriched = enrichCreationSchema(schema, { ...context, type: 'oauth2-generic-am-idp', certificates });

        expect(enriched.properties).toEqual({
            clientAuthenticationCertificate: { type: 'string', readonly: false, enum: ['c1'], titleMap: { c1: 'mTLS cert' } },
        });
    });

    it('turns a data source widget into a choice, or a read-only field when there is no data source', () => {
        const schema = { properties: { datasource: { type: 'string', widget: 'datasource' } } };

        expect(enrichCreationSchema(schema, { ...context, dataSources: [{ id: 'ds', name: 'Main' }] }).properties).toEqual({
            datasource: { type: 'string', widget: 'datasource', enum: ['ds'], titleMap: { ds: 'Main' } },
        });
        expect(enrichCreationSchema(schema, context).properties).toEqual({
            datasource: { type: 'string', widget: 'datasource', readonly: true },
        });
    });

    it('hints the pinned storage fields of a MongoDB provider when the platform restricts the system cluster', () => {
        const schema = { properties: { database: { type: 'string' }, useSystemCluster: { type: 'boolean', title: 'Use system cluster' } } };

        const enriched = enrichCreationSchema(schema, { ...context, systemClusterRestricted: true });

        expect(enriched.properties).toEqual({
            database: { type: 'string', description: 'The platform sets this value when "use system cluster" is selected.' },
            useSystemCluster: { type: 'boolean', title: 'Use system cluster', description: 'Once saved, this option cannot be changed.' },
        });
    });
});

describe('enrichSettingsSchema', () => {
    it('makes the pinned fields read-only and blanks the defaults', () => {
        const schema = {
            properties: {
                database: { type: 'string', default: 'gravitee' },
                usersCollection: { type: 'string' },
                useSystemCluster: { type: 'boolean', default: true },
            },
        };

        const enriched = enrichSettingsSchema(schema, { ...context, providerRestricted: true });

        expect(enriched.properties).toEqual({
            database: { type: 'string', default: undefined, readonly: true },
            usersCollection: { type: 'string', default: undefined, readonly: true },
            useSystemCluster: { type: 'boolean', default: undefined, readonly: true },
        });
    });
});

describe('pinnedFields', () => {
    it('pins the toggle and the storage of a restricted provider, and only the toggle under a restricted installation', () => {
        expect(pinnedFields('mongo-am-idp', true, false)).toEqual(['useSystemCluster', 'database', 'usersCollection']);
        expect(pinnedFields('mongo-am-idp', false, true)).toEqual(['useSystemCluster']);
        expect(pinnedFields('ldap-am-idp', true, true)).toEqual([]);
    });
});

describe('withBooleanDefaults', () => {
    it('fills an unset boolean with its default and leaves the other fields as stored', () => {
        const schema = { properties: { enabled: { type: 'boolean', default: true }, host: { type: 'string', default: 'localhost' } } };

        expect(withBooleanDefaults(schema, { host: null })).toEqual({ enabled: true, host: null });
        expect(withBooleanDefaults(schema, { enabled: false })).toEqual({ enabled: false });
    });
});
