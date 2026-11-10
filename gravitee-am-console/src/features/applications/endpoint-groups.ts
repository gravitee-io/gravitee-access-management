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
import type { Domain } from '../../lib/api/domains';

export interface Endpoint {
    readonly key: string;
    readonly label: string;
    readonly path: string;
    readonly description: string;
}

export interface EndpointGroup {
    readonly title: string;
    readonly description: string;
    readonly endpoints: readonly Endpoint[];
}

const OAUTH_OIDC: EndpointGroup = {
    title: 'OAuth 2.0 and OpenID Connect',
    description: 'Authorize users, issue and manage tokens, and discover the configuration.',
    endpoints: [
        {
            key: 'authorize',
            label: 'Authorization endpoint',
            path: '/oauth/authorize',
            description: 'Obtains an OAuth 2.0 token with the authorization code or implicit grant.',
        },
        { key: 'token', label: 'Token endpoint', path: '/oauth/token', description: 'Obtains an OAuth 2.0 token.' },
        {
            key: 'introspect',
            label: 'Introspection endpoint',
            path: '/oauth/introspect',
            description: 'Returns information about an OAuth 2.0 token.',
        },
        {
            key: 'revoke',
            label: 'Revocation endpoint',
            path: '/oauth/revoke',
            description: 'Invalidates a previously issued refresh or access token.',
        },
        {
            key: 'userinfo',
            label: 'UserInfo endpoint',
            path: '/oidc/userinfo',
            description: 'Returns information about the authenticated user.',
        },
        { key: 'logout', label: 'End session endpoint', path: '/logout', description: "Invalidates the user's session." },
        {
            key: 'openid-configuration',
            label: 'OpenID configuration endpoint',
            path: '/oidc/.well-known/openid-configuration',
            description: 'The complete OpenID Connect configuration, for third-party systems.',
        },
        {
            key: 'jwks',
            label: 'JWKS endpoint',
            path: '/oidc/.well-known/jwks.json',
            description: 'The JSON Web Keys used to sign or encrypt the JWTs the authorization server issues.',
        },
    ],
};

const UMA: EndpointGroup = {
    title: 'UMA 2.0',
    description: 'User-Managed Access is enabled on this domain.',
    endpoints: [
        {
            key: 'uma-configuration',
            label: 'UMA 2.0 configuration endpoint',
            path: '/uma/.well-known/uma2-configuration',
            description: 'The complete UMA 2.0 configuration, for third-party systems.',
        },
        {
            key: 'uma-resource-set',
            label: 'Resource registration endpoint',
            path: '/uma/protection/resource_set',
            description: 'Lets a resource server put resources under the protection of the authorization server.',
        },
        {
            key: 'uma-permission',
            label: 'Permission endpoint',
            path: '/uma/protection/permission',
            description: 'Lets a resource server request permissions and receive a permission ticket.',
        },
        {
            key: 'uma-policies',
            label: 'User access policy endpoint',
            path: '/uma/protection/resource_set/:resourceId/policies',
            description: 'Lets the resource owner set or change access policies once a resource is registered.',
        },
    ],
};

const CIBA: EndpointGroup = {
    title: 'Client-Initiated Backchannel Authentication',
    description: 'CIBA is enabled on this domain.',
    endpoints: [
        {
            key: 'ciba',
            label: 'Backchannel authentication endpoint',
            path: '/oidc/ciba/authenticate',
            description: 'Starts an out-of-band authentication of the end user (CIBA).',
        },
    ],
};

const SCIM: EndpointGroup = {
    title: 'SCIM 2.0',
    description: 'SCIM is enabled on this domain.',
    endpoints: [
        {
            key: 'scim',
            label: 'SCIM configuration endpoint',
            path: '/scim/ServiceProviderConfig',
            description: 'The complete SCIM configuration, for third-party systems.',
        },
    ],
};

const SAML: EndpointGroup = {
    title: 'SAML 2.0',
    description: 'The SAML 2.0 identity provider is enabled on this domain.',
    endpoints: [
        {
            key: 'saml-metadata',
            label: 'SAML 2.0 IdP metadata',
            path: '/saml2/idp/metadata',
            description: "The identity provider's SAML 2.0 metadata.",
        },
        {
            key: 'saml-sso',
            label: 'Single sign-on service',
            path: '/saml2/idp/SSO',
            description: 'The SAML 2.0 authentication request endpoint.',
        },
        {
            key: 'saml-logout',
            label: 'Single logout service',
            path: '/saml2/idp/logout',
            description: 'The SAML 2.0 single logout endpoint.',
        },
    ],
};

/** The OAuth 2.0 and OpenID Connect endpoints, then each protocol the domain has on. */
export function endpointGroups(domain: Pick<Domain, 'uma' | 'oidc' | 'scim' | 'saml'>): EndpointGroup[] {
    return [
        OAUTH_OIDC,
        ...(domain.uma?.enabled ? [UMA] : []),
        ...(domain.oidc?.cibaSettings?.enabled ? [CIBA] : []),
        ...(domain.scim?.enabled ? [SCIM] : []),
        ...(domain.saml?.enabled ? [SAML] : []),
    ];
}
