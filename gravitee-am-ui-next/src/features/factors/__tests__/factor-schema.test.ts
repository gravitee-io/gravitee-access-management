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
import { schemaFactorType, withResourceSelect, withSchemaDefaults } from '../factor-schema';

const resources = [
    { id: 'r-sms', name: 'Twilio', type: 'twilio-resource' },
    { id: 'r-smtp', name: 'Mail', type: 'smtp-resource' },
];
const resourcePlugins = [
    { id: 'twilio-resource', name: 'Twilio', categories: ['sms', 'call'] },
    { id: 'smtp-resource', name: 'SMTP', categories: ['email'] },
];
const schema = {
    type: 'object',
    properties: {
        factorType: { default: 'SMS', readOnly: true },
        graviteeResource: { type: 'string', title: 'Resource', widget: 'graviteeResource' },
        countryCodes: { type: 'string' },
    },
};

describe('withResourceSelect', () => {
    it('makes the resource field a select of the resources of the factor category', () => {
        const enriched = withResourceSelect(schema, 'sms', resources, resourcePlugins);

        expect((enriched.properties as Record<string, unknown>).graviteeResource).toEqual({
            type: 'string',
            title: 'Resource',
            enum: ['r-sms'],
            'x-schema-form': { type: 'select', titleMap: { 'r-sms': 'Twilio' } },
        });
        expect(schema.properties.graviteeResource.widget).toBe('graviteeResource');
    });

    it('lists every resource for the any category', () => {
        const enriched = withResourceSelect(schema, 'any', resources, resourcePlugins);

        expect((enriched.properties as Record<string, { enum?: string[] }>).graviteeResource.enum).toEqual(['r-sms', 'r-smtp']);
    });

    it('makes the field read-only when no resource fits', () => {
        const enriched = withResourceSelect(schema, 'http', resources, resourcePlugins);

        expect((enriched.properties as Record<string, unknown>).graviteeResource).toEqual({
            type: 'string',
            title: 'Resource',
            readonly: true,
        });
    });

    it('enriches a resource field inside the items of an array', () => {
        const enriched = withResourceSelect(
            {
                properties: {
                    providers: {
                        type: 'array',
                        items: { properties: { resource: { type: 'string', widget: 'graviteeResource' } } },
                    },
                },
            },
            'email',
            resources,
            resourcePlugins,
        );

        expect(enriched).toEqual({
            properties: {
                providers: {
                    type: 'array',
                    items: {
                        properties: {
                            resource: {
                                type: 'string',
                                enum: ['r-smtp'],
                                'x-schema-form': { type: 'select', titleMap: { 'r-smtp': 'Mail' } },
                            },
                        },
                    },
                },
            },
        });
    });
});

describe('schemaFactorType', () => {
    it('reads the factorType default', () => {
        expect(schemaFactorType(schema)).toBe('SMS');
        expect(schemaFactorType({ properties: {} })).toBeUndefined();
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
