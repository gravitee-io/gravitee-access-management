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
import fetch from 'cross-fetch';
import {afterAll, beforeAll, expect, jest} from '@jest/globals';
import {performPost} from '@gateway-commands/oauth-oidc-commands';
import {getBase64BasicAuth} from '@gateway-commands/utils';
import {
    McpOAuth2ResourceFixture,
    setupMcpOAuth2ResourceFixture,
} from './fixtures/mcp-oauth2-resource-fixture';

globalThis.fetch = fetch;

let fixture: McpOAuth2ResourceFixture;

jest.setTimeout(200000);

beforeAll(async () => {
    fixture = await setupMcpOAuth2ResourceFixture();

    // Verify default settings from setup
    expect(fixture.mcpResource).toBeDefined();
    expect(fixture.mcpResource.settings).toBeDefined();
    expect(fixture.mcpResource.settings.oauth).toBeDefined();
    expect(fixture.mcpResource.settings.oauth.grantTypes).toContain('client_credentials');
    expect(fixture.mcpResource.settings.oauth.tokenEndpointAuthMethod).toEqual('client_secret_basic');
    expect(fixture.clientSecret).toBeDefined();
    expect(fixture.clientSecret.secret).toBeDefined();
});

afterAll(async () => {
    if (fixture) {
        await fixture.cleanup();
    }
});

describe('MCP OAuth2 Resource', () => {
    describe('Client Credentials Flow', () => {
        it('should obtain access token using client_secret_basic (default)', async () => {
            await fixture.updateMcpResourceSettings({
                oauth: {
                    tokenEndpointAuthMethod: 'client_secret_basic'
                }
            });

            const response = await performPost(
                fixture.openIdConfiguration.token_endpoint,
                '',
                'grant_type=client_credentials',
                {
                    'Content-type': 'application/x-www-form-urlencoded',
                    Authorization: 'Basic ' + getBase64BasicAuth(fixture.mcpResource.clientId, fixture.clientSecret.secret),
                }
            );

            expect(response.body.access_token).toBeDefined();
            expect(response.body.token_type).toEqual('bearer');
        });

        it('should fail using client_secret_post when BASIC is configured', async () => {
             await fixture.updateMcpResourceSettings({
                oauth: {
                    tokenEndpointAuthMethod: 'client_secret_basic'
                }
            });

            await performPost(
                fixture.openIdConfiguration.token_endpoint,
                '',
                `grant_type=client_credentials&client_id=${fixture.mcpResource.clientId}&client_secret=${fixture.clientSecret.secret}`,
                {
                    'Content-type': 'application/x-www-form-urlencoded',
                }
            ).expect(401);
        });

        it('should change auth method to client_secret_post and succeed', async () => {
            await fixture.updateMcpResourceSettings({
                oauth: {
                    tokenEndpointAuthMethod: 'client_secret_post'
                }
            });

            expect(fixture.mcpResource.settings.oauth.tokenEndpointAuthMethod).toEqual('client_secret_post');

            const response = await performPost(
                fixture.openIdConfiguration.token_endpoint,
                '',
                `grant_type=client_credentials&client_id=${fixture.mcpResource.clientId}&client_secret=${fixture.clientSecret.secret}`,
                {
                    'Content-type': 'application/x-www-form-urlencoded'
                }
            ).expect(200);
            expect(response.body.access_token).toBeDefined();

        });

        it('should fail using client_secret_basic when POST is configured', async () => {
            await fixture.updateMcpResourceSettings({
                oauth: {
                    tokenEndpointAuthMethod: 'client_secret_post'
                }
            });

            await performPost(
                fixture.openIdConfiguration.token_endpoint,
                '',
                'grant_type=client_credentials',
                {
                    'Content-type': 'application/x-www-form-urlencoded',
                    Authorization: 'Basic ' + getBase64BasicAuth(fixture.mcpResource.clientId, fixture.clientSecret.secret),
                }
            ).expect(401);
        });
    });

    describe('Scope Restrictions', () => {
        beforeAll(async () => {
            await fixture.updateMcpResourceSettings({
                oauth: {
                    tokenEndpointAuthMethod: 'client_secret_basic',
                    scopeSettings: [
                        {scope: 'scope1', defaultScope: false}
                    ]
                }
            });

            expect(fixture.mcpResource.settings.oauth.scopeSettings).toBeDefined();
        });

        it('should succeed requesting allowed scope', async () => {
            const response = await performPost(
                fixture.openIdConfiguration.token_endpoint,
                '',
                'grant_type=client_credentials&scope=scope1',
                {
                    'Content-type': 'application/x-www-form-urlencoded',
                    Authorization: 'Basic ' + getBase64BasicAuth(fixture.mcpResource.clientId, fixture.clientSecret.secret),
                }
            ).expect(200);

            expect(response.body.scope).toContain('scope1');
        });

        it('should fail requesting unknown scope', async () => {
            await performPost(
                fixture.openIdConfiguration.token_endpoint,
                '',
                'grant_type=client_credentials&scope=unknown_scope',
                {
                    'Content-type': 'application/x-www-form-urlencoded',
                    Authorization: 'Basic ' + getBase64BasicAuth(fixture.mcpResource.clientId, fixture.clientSecret.secret),
                }
            ).expect(400); // invalid_scope
        });
    });

    describe('Custom Claims', () => {
        beforeAll(async () => {
            // Add a custom claim
            await fixture.updateMcpResourceSettings({
                oauth: {
                    tokenEndpointAuthMethod: 'client_secret_basic',
                    tokenCustomClaims: [
                        {
                            claimName: 'test-custom-claim',
                            claimValue: 'custom-claim-value',
                            tokenType: 'ACCESS_TOKEN'
                        }
                    ]
                }
            });
        });

        it('should include custom claim in access token', async () => {
            const response = await performPost(
                fixture.openIdConfiguration.token_endpoint,
                '',
                'grant_type=client_credentials',
                {
                    'Content-type': 'application/x-www-form-urlencoded',
                    Authorization: 'Basic ' + getBase64BasicAuth(fixture.mcpResource.clientId, fixture.clientSecret.secret),
                }
            ).expect(200);

            const accessToken = response.body.access_token
            const parts = accessToken.split('.');
            const payload = JSON.parse(Buffer.from(parts[1], 'base64').toString());

            expect(payload['test-custom-claim']).toBeDefined();
            expect(payload['test-custom-claim']).toEqual('custom-claim-value');
        });
    });


    describe('Token Validity', () => {
        beforeAll(async () => {
            // Set short token validity
            await fixture.updateMcpResourceSettings({
                oauth: {
                    accessTokenValiditySeconds: 5,
                    tokenEndpointAuthMethod: 'client_secret_basic'
                }
            });

            expect(fixture.mcpResource.settings.oauth.accessTokenValiditySeconds).toEqual(5);
        });

        it('should respect access token validity', async () => {
            const response = await performPost(
                fixture.openIdConfiguration.token_endpoint,
                '',
                'grant_type=client_credentials',
                {
                    'Content-type': 'application/x-www-form-urlencoded',
                    Authorization: 'Basic ' + getBase64BasicAuth(fixture.mcpResource.clientId, fixture.clientSecret.secret),
                }
            ).expect(200);

            const accessToken = response.body.access_token;
            const parts = accessToken.split('.');
            const payload = JSON.parse(Buffer.from(parts[1], 'base64').toString());

            expect(payload.exp).toBeDefined();
            expect(payload.iat).toBeDefined();
            // Allow small drift but ensure it's around 5 seconds
            expect(payload.exp - payload.iat).toBeLessThanOrEqual(5 + 2);
            expect(payload.exp - payload.iat).toBeGreaterThanOrEqual(5 - 2);
        });
    });

    describe('Invalid Grant Types', () => {
        beforeAll(async () => {
            // Ensure only client_credentials is allowed
            await fixture.updateMcpResourceSettings({
                oauth: {
                    tokenEndpointAuthMethod: 'client_secret_basic'
                }
            });
        });

        it('should reject password grant type', async () => {
            await performPost(
                fixture.openIdConfiguration.token_endpoint,
                '',
                'grant_type=password&username=user&password=password', // Attempt password grant
                {
                    'Content-type': 'application/x-www-form-urlencoded',
                    Authorization: 'Basic ' + getBase64BasicAuth(fixture.mcpResource.clientId, fixture.clientSecret.secret),
                }
            ).expect(400); // Should be unsupported_grant_type or unauthorized_client
        });
    });
});
