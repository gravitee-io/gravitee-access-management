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
import {
    configurationSchema,
    extensionGrantTypeName,
    schemaGrantType,
    toConfiguration,
    toFormConfiguration,
} from '../extension-grant-types';
import { crossAppAccessSchema, jwtBearerSchema } from './render';

describe('extension grant types', () => {
    it('names the known plugins as Angular does and falls back for the others', () => {
        expect(extensionGrantTypeName('jwtbearer-am-extension-grant')).toBe('JWT Bearer');
        expect(extensionGrantTypeName('xaa-am-extension-grant')).toBe('Cross App Access');
        expect(extensionGrantTypeName('custom-am-extension-grant', 'Custom plugin')).toBe('Custom plugin');
    });

    it('reads the grant type from the schema default', () => {
        expect(schemaGrantType(jwtBearerSchema)).toBe('urn:ietf:params:oauth:grant-type:jwt-bearer');
        expect(schemaGrantType({ type: 'object', properties: {} })).toBeUndefined();
    });

    it('drops the untyped grant type property from the configuration schema', () => {
        const schema = configurationSchema(jwtBearerSchema);

        expect(Object.keys(schema.properties as object)).toEqual(['publicKeyResolver', 'publicKey', 'claimsMapper']);
        expect(schema.required).toEqual(['publicKeyResolver', 'publicKey']);
        expect(configurationSchema({ type: 'object' })).toEqual({ type: 'object' });
    });

    it('leaves out an empty array whose schema default is absent or empty, as the Angular form does', () => {
        expect(JSON.parse(toConfiguration(jwtBearerSchema, { publicKeyResolver: 'GIVEN_KEY', publicKey: 'k', claimsMapper: [] }))).toEqual({
            publicKeyResolver: 'GIVEN_KEY',
            publicKey: 'k',
        });
        expect(JSON.parse(toConfiguration(jwtBearerSchema, { claimsMapper: [{ assertion_claim: 'sub', token_claim: 'uid' }] }))).toEqual({
            claimsMapper: [{ assertion_claim: 'sub', token_claim: 'uid' }],
        });
        expect(toConfiguration(crossAppAccessSchema, { userBindingCriteria: [] })).toBe('{}');
        const withDefault = { properties: { audiences: { type: 'array', default: ['a'] } } };
        expect(toConfiguration(withDefault, { audiences: [] })).toBe('{"audiences":[]}');
    });

    it('gives the form an empty array for each array property the configuration lacks', () => {
        expect(toFormConfiguration(jwtBearerSchema, { publicKey: 'k' })).toEqual({ publicKey: 'k', claimsMapper: [] });
        expect(toFormConfiguration(jwtBearerSchema, { claimsMapper: [{ assertion_claim: 'sub' }] })).toEqual({
            claimsMapper: [{ assertion_claim: 'sub' }],
        });
    });
});
