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
import { adaptAmSchema, withSchemaDefaults } from '../am-schema';

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
            properties: { datasourceId: { type: 'string', title: 'Data source ID', widget: 'datasource' } },
        });

        expect(schema.properties?.datasourceId).toEqual({ type: 'string', title: 'Data source ID' });
        expect(unsupported).toEqual(['datasourceId (widget "datasource")']);
    });

    it('translates the SMTP conditions into displayIf, reading an unset value as its default', () => {
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
            displayIf: { $eq: { 'value.authentication': true, 'value.authenticationType': 'basic' } },
        });
        expect(schema.properties?.oauth2Configuration?.gioConfig).toEqual({
            displayIf: { $eq: { 'value.authentication': true, 'value.authenticationType': 'oauth2' } },
        });
        expect(unsupported).toEqual([]);
    });

    it('reports a condition displayIf cannot express and keeps the field visible', () => {
        const { schema, unsupported } = adaptAmSchema({
            type: 'object',
            properties: {
                mode: { type: 'string' },
                port: { type: 'integer', condition: { functionBody: "return model.mode !== 'local';" } },
                host: { type: 'string', condition: { functionBody: "return !model.mode || model.mode === 'remote';" } },
            },
        });

        expect(schema.properties?.port).toEqual({ type: 'integer' });
        expect(schema.properties?.host).toEqual({ type: 'string' });
        expect(unsupported).toEqual(['port (condition)', 'host (condition)']);
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
