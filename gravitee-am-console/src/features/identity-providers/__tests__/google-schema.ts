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
/** `GET /platform/plugins/identities/google-am-idp/schema`, copied from a local stack. */
export const GOOGLE_SCHEMA: Record<string, unknown> = {
    type: 'object',
    id: 'urn:jsonschema:io:gravitee:am:identityprovider:google:GoogleIdentityProvider',
    description:
        'Google identity provider, let you register and set up permissions and authorization options for OpenID Connect protocol.\n\nhttps://developers.google.com/identity/protocols/oauth2/openid-connect \n\n (openid, email and profile scopes are added by default)',
    properties: {
        clientId: {
            type: 'string',
            title: 'Client ID',
            description: 'OAuth 2.0 client id',
        },
        clientSecret: {
            type: 'string',
            title: 'Client Secret',
            description: 'OAuth 2.0 client secret',
            sensitive: true,
        },
        encodeRedirectUri: {
            type: 'boolean',
            title: 'Should the redirect_uri parameter be url-encoded ?',
        },
        scopes: {
            type: 'array',
            title: 'Scopes',
            description:
                'Scopes let you specify exactly what type of access you need. Scopes limit access for OAuth tokens. (openid, email and profile scopes are added by default)',
            items: {
                type: 'string',
            },
        },
        connectTimeout: {
            type: 'integer',
            default: 10000,
            minimum: 1,
            title: 'HTTP Client connect timeout',
            description: 'Duration of time in milliseconds that connects will block. (default 10000 ms)',
        },
        idleTimeout: {
            type: 'integer',
            default: 10000,
            minimum: 1,
            title: 'HTTP Client idle timeout',
            description: 'Duration of time in milliseconds that request will hold. (default 10000 ms)',
        },
        maxPoolSize: {
            type: 'integer',
            default: 200,
            minimum: 1,
            title: 'HTTP Client max pool size',
            description: 'Maximum pool of connections can grow to. (default 200)',
        },
        storeOriginalTokens: {
            type: 'boolean',
            default: false,
            title: 'Store original tokens',
        },
    },
    required: ['clientId', 'clientSecret'],
};
