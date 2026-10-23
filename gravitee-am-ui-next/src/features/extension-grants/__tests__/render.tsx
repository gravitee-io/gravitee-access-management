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
import { LayoutSlotsProvider, TooltipProvider } from '@gravitee/graphene-core';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter, Routes } from 'react-router-dom';
import { LicenseScope } from '../../../lib/session/license';
import { PermissionScope } from '../../../lib/session/permissions';

export const ref = { organizationId: 'DEFAULT', environmentId: 'env-1', domainId: 'd1' };
export const extensionGrantsPath = '/environments/dev/domains/acme/settings/extensionGrants';

/** The schema of `jwtbearer-am-extension-grant`, as `GET /platform/plugins/extensionGrants/:type/schema` returns it. */
export const jwtBearerSchema = {
    type: 'object',
    id: 'urn:jsonschema:io:gravitee:am:tokengranter:jwtbearer:JwtBearerTokenGranterConfiguration',
    description:
        'RFC 7523, Section 2.1, Using JWTs as a means for requesting an OAuth 2.0 access token.\nThe following example demonstrates an access token request with a JWT as an authorization grant :\n\nPOST /token HTTP/1.1\ngrant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=|signed-JWT-token|',
    properties: {
        grantType: {
            title: 'Grant Type',
            description:
                'OAuth 2.0 grant type. The client uses an extension grant type by specifying the grant type value of the "grant_type" parameter of the token endpoint.',
            default: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
            readOnly: true,
        },
        publicKeyResolver: {
            title: 'Public Key resolver',
            description: 'Define how token (JWT) will be verify',
            type: 'string',
            default: 'GIVEN_KEY',
            enum: ['GIVEN_KEY', 'JWKS_URL'],
            'x-schema-form': {
                type: 'select',
                titleMap: {
                    GIVEN_KEY: 'GIVEN_KEY: You must provide a signature key (PEM format or SSH key format [ssh-rsa, ecdsa, ssh-dsa])',
                    JWKS_URL: "JWKS_URL: Retrieve JWKS from URL (Basically, URL ending with '/.well-known/jwks.json')",
                },
            },
        },
        publicKey: {
            title: 'Resolver parameter',
            description:
                'Set the signature key <i><b>(GIVEN_KEY)</b></i> or a JWKS URL <i><b>(JWKS_URL)</b></i> following selected resolver. If selected <i><b>(GIVEN_KEY)</b></i>, must start with key type (e.g. ssh-rsa).',
            type: 'string',
            widget: 'textarea',
        },
        claimsMapper: {
            title: 'Claims mapper',
            type: 'array',
            items: {
                type: 'object',
                properties: {
                    assertion_claim: {
                        title: 'Assertion claim',
                        description: 'Assertion payload claim',
                        type: 'string',
                    },
                    token_claim: {
                        title: 'Token claim',
                        description: 'Claim that will be added to End-User claims',
                        type: 'string',
                    },
                },
            },
        },
    },
    required: ['publicKeyResolver', 'publicKey'],
};

/** The schema of `xaa-am-extension-grant`, as the same call returns it. */
export const crossAppAccessSchema = {
    type: 'object',
    id: 'urn:jsonschema:com:graviteesource:am:extensiongrant:xaa:XAAExtensionGrantConfiguration',
    description:
        'Cross App Access (draft-ietf-oauth-identity-assertion-authz-grant). An agent presents an identity assertion authorization grant (ID-JAG) minted by a trusted identity provider through the jwt-bearer grant type :\n\nPOST /token HTTP/1.1\ngrant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=|ID-JAG|',
    properties: {
        grantType: {
            title: 'Grant Type',
            description:
                'OAuth 2.0 grant type. The client uses an extension grant type by specifying the grant type value of the "grant_type" parameter of the token endpoint.',
            default: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
            readOnly: true,
        },
        userBindingCriteria: {
            title: 'User binding rules',
            description:
                'Used when "Check user" is enabled. Each rule matches a user profile attribute against an expression evaluated over the verified assertion claims, exposed as token (e.g. {#token[\'email\']}). All rules must match, and exactly one user must match them. Leave empty to bind the user whose external id is the assertion subject and whose source is the identity provider trusted for the assertion issuer.',
            type: 'array',
            default: [],
            items: {
                type: 'object',
                title: 'Rule',
                properties: {
                    attribute: {
                        title: 'User attribute',
                        description: 'User profile attribute to match (e.g. emails.value, userName, additionalInformation.employee_id)',
                        type: 'string',
                    },
                    expression: {
                        title: 'Expression',
                        description: "Expression over the verified assertion claims (e.g. {#token['email']})",
                        type: 'string',
                        'x-schema-form': {
                            'expression-language': true,
                        },
                    },
                },
                required: ['attribute', 'expression'],
            },
        },
    },
};

export function currentDomain() {
    return {
        domain: { id: 'd1', hrid: 'acme', name: 'Acme', enabled: true },
        ref,
        basePath: '/environments/dev/domains/acme',
        domainsPath: '/environments/dev/domains',
    };
}

/** Renders the routes at a path, with the permissions and the license features given. */
export function renderRoutes(path: string, routes: ReactNode, permissions: string[], features: string[] = []) {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return render(
        <QueryClientProvider client={client}>
            <LicenseScope license={{ tier: 'oss', features }}>
                <PermissionScope permissions={permissions}>
                    <MemoryRouter initialEntries={[path]}>
                        <LayoutSlotsProvider>
                            <TooltipProvider>
                                <Routes>{routes}</Routes>
                            </TooltipProvider>
                        </LayoutSlotsProvider>
                    </MemoryRouter>
                </PermissionScope>
            </LicenseScope>
        </QueryClientProvider>,
    );
}
