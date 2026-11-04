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
import { adaptAmSchema, compatibleResources, withSchemaDefaults } from '../am-schema';

describe('adaptAmSchema', () => {
    it('translates angular-schema-form widgets and title maps into the Graphene dialect', () => {
        const { schema, unsupported } = adaptAmSchema({
            type: 'object',
            id: 'urn:jsonschema:io:gravitee:am:identityprovider:jdbc:JdbcIdentityProviderConfiguration',
            properties: {
                password: { type: 'string', widget: 'password', sensitive: true },
                query: { type: 'string', widget: 'textarea' },
                encoder: {
                    type: 'string',
                    enum: ['BCrypt', 'None'],
                    'x-schema-form': { type: 'select', titleMap: { BCrypt: 'BCrypt', None: 'None' } },
                },
                uri: { type: 'string', 'sensitive-uri': true },
                version: { type: 'string', readonly: true },
                options: { type: 'object', htmlClass: 'resource', properties: { rounds: { type: 'number', titleMap: { 10: 'Ten' } } } },
            },
        });

        expect(schema).toEqual({
            type: 'object',
            properties: {
                password: { type: 'string', format: 'password' },
                query: { type: 'string', format: 'text' },
                encoder: { type: 'string', enum: ['BCrypt', 'None'], gioConfig: { enumLabelMap: { BCrypt: 'BCrypt', None: 'None' } } },
                uri: { type: 'string' },
                version: { type: 'string', readOnly: true },
                options: { type: 'object', properties: { rounds: { type: 'number', gioConfig: { enumLabelMap: { 10: 'Ten' } } } } },
            },
        });
        expect(unsupported).toEqual([]);
    });

    it('reports widgets it cannot translate and keeps the field as a plain input', () => {
        const { schema, unsupported } = adaptAmSchema({
            type: 'object',
            properties: {
                datasourceId: { type: 'string', title: 'Data source ID', widget: 'datasource' },
                certificate: { type: 'string', widget: 'graviteeCertificate' },
            },
        });

        expect(schema.properties?.datasourceId).toEqual({ type: 'string', title: 'Data source ID' });
        expect(unsupported).toEqual(['datasourceId (widget "datasource")', 'certificate (widget "graviteeCertificate")']);
    });

    it('translates the SMTP conditions into displayIf, and an unset value passes the unset-or-equals term', () => {
        const { schema, unsupported } = adaptAmSchema({
            type: 'object',
            properties: {
                authentication: { type: 'boolean', default: false },
                authenticationType: { type: 'string', default: 'basic', enum: ['basic', 'oauth2'], condition: 'model.authentication' },
                username: {
                    type: 'string',
                    condition: {
                        functionBody: "return model.authentication && (!model.authenticationType || model.authenticationType === 'basic');",
                    },
                },
                oauth2Configuration: {
                    type: 'object',
                    condition: { functionBody: "return model.authentication && model.authenticationType === 'oauth2';" },
                    properties: { tokenEndpoint: { type: 'string' } },
                },
            },
        });

        expect(schema.properties?.authenticationType?.gioConfig).toEqual({ displayIf: { $eq: { 'value.authentication': true } } });
        expect(schema.properties?.username?.gioConfig).toEqual({
            displayIf: { $eq: { 'value.authentication': true, 'value.authenticationType': ['basic', undefined, null, '', 0, false] } },
        });
        expect(schema.properties?.oauth2Configuration?.gioConfig).toEqual({
            displayIf: { $eq: { 'value.authentication': true, 'value.authenticationType': 'oauth2' } },
        });
        expect(unsupported).toEqual([]);
    });

    it('reports a condition with two values for one field', () => {
        const { schema, unsupported } = adaptAmSchema({
            type: 'object',
            properties: {
                mode: { type: 'string' },
                port: { type: 'integer', condition: "model.mode === 'a' && model.mode === 'b'" },
            },
        });

        expect(schema.properties?.port).toEqual({ type: 'integer' });
        expect(unsupported).toEqual(['port (condition)']);
    });

    it('reports a condition displayIf cannot express and keeps the field visible', () => {
        const { schema, unsupported } = adaptAmSchema({
            type: 'object',
            properties: {
                mode: { type: 'string' },
                port: { type: 'integer', condition: { functionBody: "return model.mode !== 'local';" } },
            },
        });

        expect(schema.properties?.port).toEqual({ type: 'integer' });
        expect(unsupported).toEqual(['port (condition)']);
    });

    it('drops a property without a type, as the Angular json-schema-form does not render it', () => {
        const { schema } = adaptAmSchema({
            type: 'object',
            required: ['factorType', 'returnDigits'],
            properties: {
                factorType: { title: 'Factor type', default: 'EMAIL' },
                returnDigits: { type: 'integer', default: 6 },
            },
        });

        expect(schema).toEqual({
            type: 'object',
            required: ['returnDigits'],
            properties: { returnDigits: { type: 'integer', default: 6 } },
        });
    });

    it('renders a graviteeResource field as a select of the resource ids, labelled with their names', () => {
        const { schema } = adaptAmSchema(
            {
                type: 'object',
                properties: {
                    graviteeResource: { type: 'string', title: 'Resource' },
                    smtp: { type: 'string', widget: 'graviteeResource' },
                },
            },
            {
                resources: [
                    { id: 'r1', name: 'Mail server' },
                    { id: 'r2', name: 'Backup mail server' },
                ],
            },
        );

        const select = { type: 'string', enum: ['r1', 'r2'], gioConfig: { enumLabelMap: { r1: 'Mail server', r2: 'Backup mail server' } } };
        expect(schema.properties).toEqual({ graviteeResource: { ...select, title: 'Resource' }, smtp: select });
    });

    it('matches the graviteeResource property name only at the top level, as Angular does', () => {
        const { schema } = adaptAmSchema({
            type: 'object',
            properties: { mail: { type: 'object', properties: { graviteeResource: { type: 'string' } } } },
        });

        expect(schema.properties?.mail?.properties?.graviteeResource).toEqual({ type: 'string' });
    });

    it('makes a graviteeResource field read-only when no resource fits', () => {
        const { schema } = adaptAmSchema({ type: 'object', properties: { graviteeResource: { type: 'string' } } }, { resources: [] });

        expect(schema.properties?.graviteeResource).toEqual({ type: 'string', readOnly: true });
    });

    it('renders a graviteeMultiselect field as a multiselect of its dictionary items', () => {
        const raw = {
            type: 'object',
            properties: {
                events: {
                    type: 'array',
                    title: 'Events',
                    widget: 'graviteeMultiselect',
                    itemsDictionaryEndpoint: '/platform/audits/events',
                    description: 'The events to report.',
                    onEmptySelectionMessage: 'All events',
                    items: { type: 'string' },
                },
                levels: { type: 'array', widget: 'graviteeMultiselect', enum: ['INFO', 'WARN'], items: { type: 'string' } },
            },
        };

        expect(adaptAmSchema(raw).dictionaryEndpoints).toEqual(['/platform/audits/events']);
        expect(
            adaptAmSchema(raw, { dictionaries: { '/platform/audits/events': ['USER_CREATED', 'USER_DELETED'] } }).schema.properties,
        ).toEqual({
            events: {
                type: 'array',
                title: 'Events',
                description: 'The events to report. All events',
                uniqueItems: true,
                items: { type: 'string', enum: ['USER_CREATED', 'USER_DELETED'] },
            },
            levels: { type: 'array', uniqueItems: true, items: { type: 'string', enum: ['INFO', 'WARN'] } },
        });
    });

    it('renders an array of enum items as a multiselect labelled by its title map, as the PKCS#12 certificate declares it', () => {
        const { schema } = adaptAmSchema({
            type: 'object',
            properties: {
                use: {
                    type: 'array',
                    title: 'Usage',
                    items: { type: 'string', enum: ['sig', 'enc', 'mtls'] },
                    'x-schema-form': { titleMap: { sig: 'Signature', enc: 'Encryption', mtls: 'mTLS' } },
                },
            },
        });

        expect(schema.properties?.use).toEqual({
            type: 'array',
            title: 'Usage',
            uniqueItems: true,
            items: {
                type: 'string',
                enum: ['sig', 'enc', 'mtls'],
                gioConfig: { enumLabelMap: { sig: 'Signature', enc: 'Encryption', mtls: 'mTLS' } },
            },
        });
    });

    it('reads the widget and its options from x-schema-form, as the Kafka reporter declares them', () => {
        const { schema, dictionaryEndpoints, unsupported } = adaptAmSchema(
            {
                type: 'object',
                properties: {
                    auditTypes: {
                        type: 'array',
                        title: 'Events to report',
                        items: { type: 'string' },
                        'x-schema-form': {
                            type: 'graviteeMultiselect',
                            itemsDictionaryEndpoint: '/platform/audits/events',
                            onEmptySelectionMessage: 'Reporter accepts all event types',
                        },
                    },
                    acks: { type: 'string', enum: ['0', '1'], 'x-schema-form': { type: 'select', titleMap: { 0: 'None', 1: 'Leader' } } },
                    script: { type: 'string', 'x-schema-form': { type: 'codemirror' } },
                },
            },
            { dictionaries: { '/platform/audits/events': ['USER_CREATED'] } },
        );

        expect(dictionaryEndpoints).toEqual(['/platform/audits/events']);
        expect(schema.properties).toEqual({
            auditTypes: {
                type: 'array',
                title: 'Events to report',
                description: 'Reporter accepts all event types',
                uniqueItems: true,
                items: { type: 'string', enum: ['USER_CREATED'] },
            },
            acks: { type: 'string', enum: ['0', '1'], gioConfig: { enumLabelMap: { 0: 'None', 1: 'Leader' } } },
            script: { type: 'string' },
        });
        expect(unsupported).toEqual(['script (widget "codemirror")']);
    });

    it('takes the if/then blocks out of allOf, as a schema each', () => {
        const ifBasic = { properties: { authentication: { const: true } } };
        const thenBasic = { required: ['username'] };
        const { schema, conditions } = adaptAmSchema({
            type: 'object',
            properties: { authentication: { type: 'boolean' }, username: { type: 'string' } },
            allOf: [{ if: ifBasic, then: thenBasic }, { required: ['authentication'] }],
        });

        expect(conditions).toEqual([{ type: 'object', if: ifBasic, then: thenBasic }]);
        expect(schema.allOf).toEqual([{ required: ['authentication'] }]);
    });

    it('takes a file field out of the schema, so the console renders it beside the schema form', () => {
        const { schema, files } = adaptAmSchema({
            type: 'object',
            required: ['content', 'storepass'],
            properties: {
                content: { type: 'string', title: 'PKCS#12 file', description: 'The keystore', widget: 'file' },
                storepass: { type: 'string', widget: 'password' },
            },
        });

        expect(files).toEqual([{ key: 'content', title: 'PKCS#12 file', description: 'The keystore', required: true }]);
        expect(schema).toEqual({
            type: 'object',
            required: ['storepass'],
            properties: { storepass: { type: 'string', format: 'password' } },
        });
    });
});

describe('withSchemaDefaults', () => {
    it('fills the defaults under the configuration, and an undefined value keeps its default', () => {
        const otp: JsonSchema = {
            type: 'object',
            properties: {
                issuer: { type: 'string', default: 'Gravitee.io' },
                timeStep: { type: 'string', default: '30' },
                label: { type: 'string' },
            },
        };

        expect(withSchemaDefaults(otp, { issuer: 'Acme', timeStep: undefined, label: 'x' })).toEqual({
            issuer: 'Acme',
            timeStep: '30',
            label: 'x',
        });
    });
});

describe('compatibleResources', () => {
    const resources = [
        { id: 'r1', name: 'SMTP', type: 'smtp-am-resource' },
        { id: 'r2', name: 'Twilio', type: 'twilio-am-resource' },
    ];
    const resourcePlugins = [
        { id: 'smtp-am-resource', categories: ['email'] },
        { id: 'twilio-am-resource', categories: ['sms', 'call'] },
    ];

    it('keeps the resources whose plugin has the category', () => {
        expect(compatibleResources('sms', resources, resourcePlugins)).toEqual([resources[1]]);
    });

    it('keeps every resource for the any category', () => {
        expect(compatibleResources('any', resources, resourcePlugins)).toEqual(resources);
    });
});
