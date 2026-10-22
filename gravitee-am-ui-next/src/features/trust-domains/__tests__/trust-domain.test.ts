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
    deriveNameFromIssuer,
    fromTrustedDomain,
    keyMaterialErrors,
    keyMaterialSourceLabel,
    toTrustedDomainRequest,
} from '../trust-domain';

describe('trust-domain', () => {
    it('flattens the API shape, with the lowercase key source in upper case', () => {
        expect(
            fromTrustedDomain({
                id: 'td-1',
                name: 'acme',
                domainIdentifier: 'https://sso.acme.com',
                keyMaterial: { source: 'jwks_url', jwksUrl: 'https://sso.acme.com/jwks', refreshIntervalSeconds: 60 },
                tokenExchange: { enabled: true, scopeMappings: { read: 'openid' } },
            }),
        ).toEqual({
            id: 'td-1',
            name: 'acme',
            description: undefined,
            spiffeTrustDomain: undefined,
            issuer: 'https://sso.acme.com',
            tokenExchangeEnabled: true,
            keyMaterial: { source: 'JWKS_URL', jwksUrl: 'https://sso.acme.com/jwks', refreshIntervalSeconds: 60 },
            refreshIntervalSeconds: 60,
            allowedAlgorithms: [],
            scopeMappings: { read: 'openid' },
            userBindingEnabled: false,
            userBindingCriteria: undefined,
            crossAppAccess: undefined,
        });
    });

    it('builds the request with the refresh interval inside the key material', () => {
        expect(
            toTrustedDomainRequest({
                name: 'acme',
                issuer: 'https://sso.acme.com',
                tokenExchangeEnabled: true,
                keyMaterial: { source: 'PEM', certificate: 'cert' },
                refreshIntervalSeconds: 300,
            }),
        ).toEqual({
            name: 'acme',
            description: undefined,
            keyMaterial: { source: 'PEM', certificate: 'cert', refreshIntervalSeconds: 300 },
            domainIdentifier: 'https://sso.acme.com',
            spiffe: { spiffeTrustDomain: '', allowedAlgorithms: [] },
            tokenExchange: { enabled: true, scopeMappings: {}, userBindingEnabled: false, userBindingCriteria: [] },
            crossAppAccess: { enabled: false },
        });
    });

    it('derives a name from the issuer, and labels and checks the key source', () => {
        expect(deriveNameFromIssuer('https://SSO.acme.com/realms/x')).toBe('https-sso.acme.com-realms-x');
        expect(keyMaterialSourceLabel('jwk_set')).toBe('JWK Set');
        expect(keyMaterialErrors(undefined)).toEqual(['A key source is required.']);
        expect(keyMaterialErrors({ source: 'JWK_SET', jwkSet: { keys: [] } })).toEqual(['The JWK set must contain at least one key.']);
    });
});
