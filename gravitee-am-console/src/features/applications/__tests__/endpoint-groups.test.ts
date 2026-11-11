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
import { endpointGroups } from '../endpoint-groups';

const titles = (domain: Parameters<typeof endpointGroups>[0]) => endpointGroups(domain).map(group => group.title);

describe('endpointGroups', () => {
    it('lists only the OAuth 2.0 and OpenID Connect endpoints on a plain domain', () => {
        const [oauth] = endpointGroups({});

        expect(titles({})).toEqual(['OAuth 2.0 and OpenID Connect']);
        expect(oauth.endpoints.map(endpoint => endpoint.path)).toEqual([
            '/oauth/authorize',
            '/oauth/token',
            '/oauth/introspect',
            '/oauth/revoke',
            '/oidc/userinfo',
            '/logout',
            '/oidc/.well-known/openid-configuration',
            '/oidc/.well-known/jwks.json',
        ]);
    });

    it('adds each protocol the domain has on', () => {
        expect(
            titles({ uma: { enabled: true }, oidc: { cibaSettings: { enabled: true } }, scim: { enabled: true }, saml: { enabled: true } }),
        ).toEqual(['OAuth 2.0 and OpenID Connect', 'UMA 2.0', 'Client-Initiated Backchannel Authentication', 'SCIM 2.0', 'SAML 2.0']);
        expect(titles({ uma: { enabled: false }, scim: { enabled: true } })).toEqual(['OAuth 2.0 and OpenID Connect', 'SCIM 2.0']);
    });
});
