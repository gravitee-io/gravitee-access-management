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
import { afterAll, beforeAll, expect, jest } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import {
    createDomain,
    safeDeleteDomain,
    startDomain, waitFor,
    waitForDomainStart,
    waitForDomainSync,
} from '@management-commands/domain-management-commands';
import { createScope } from '@management-commands/scope-management-commands';
import {
    createProtectedResource,
    patchProtectedResource,
    createMcpClientSecret, getMcpServer,
} from '@management-commands/protected-resources-management-commands';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { getBase64BasicAuth } from '@gateway-commands/utils';
import { Domain } from '../../api/management/models';
import { uniqueName } from '@utils-commands/misc';

globalThis.fetch = fetch;

let masterDomain: Domain;
let accessToken: string;
let mcpResource: any;
let openIdConfiguration: any;
let clientSecret: any;

jest.setTimeout(200000);

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();

  // 1. Create Domain
  masterDomain = await createDomain(accessToken, uniqueName('mcp-oauth2-test', true), 'test mcp oauth2');
  expect(masterDomain).toBeDefined();

  // 2. Create Scope
  await createScope(masterDomain.id, accessToken, {
    key: 'scope1',
    name: 'Scope 1',
    description: 'Test scope 1',
  });

  // 3. Create MCP Resource
  mcpResource = await createProtectedResource(masterDomain.id, accessToken, {
    name: uniqueName('mcp-resource'),
    type: 'MCP_SERVER',
    resourceIdentifiers: ['https://example.com/mcp'],
  });

  mcpResource = await getMcpServer(masterDomain.id, accessToken, mcpResource.id);

  expect(mcpResource).toBeDefined();
  // Verify default settings
  expect(mcpResource.settings).toBeDefined();
  expect(mcpResource.settings.oauth).toBeDefined();
  expect(mcpResource.settings.oauth.grantTypes).toContain('client_credentials');
  expect(mcpResource.settings.oauth.tokenEndpointAuthMethod).toEqual('client_secret_basic');

  // Create a client secret for the MCP resource
  clientSecret = await createMcpClientSecret(masterDomain.id, accessToken, mcpResource.id, { name: 'default-secret' });
  expect(clientSecret).toBeDefined();
  expect(clientSecret.secret).toBeDefined();

  // 4. Start Domain
  const masterDomainStarted = await startDomain(masterDomain.id, accessToken).then(() => waitForDomainStart(masterDomain));
  masterDomain = masterDomainStarted.domain;
  openIdConfiguration = masterDomainStarted.oidcConfig;

  // Wait for sync
  await waitForDomainSync(masterDomain.id, accessToken);
});

afterAll(async () => {
    if (masterDomain) {
        await safeDeleteDomain(masterDomain.id, accessToken);
    }
});

describe('MCP OAuth2 Resource', () => {

    describe('Client Credentials Flow', () => {

        it('should obtain access token using client_secret_basic (default)', async () => {
            const response = await performPost(
                openIdConfiguration.token_endpoint,
                '',
                'grant_type=client_credentials',
                {
                    'Content-type': 'application/x-www-form-urlencoded',
                    Authorization: 'Basic ' + getBase64BasicAuth(mcpResource.clientId, clientSecret.secret),
                }
            );

            expect(response.body.access_token).toBeDefined();
            expect(response.body.token_type).toEqual('bearer');
        });

        it('should fail using client_secret_post when BASIC is configured', async () => {
            await performPost(
                openIdConfiguration.token_endpoint,
                '',
                `grant_type=client_credentials&client_id=${mcpResource.clientId}&client_secret=${clientSecret.secret}`,
                {
                    'Content-type': 'application/x-www-form-urlencoded',
                }
            ).expect(401);
        });

        it('should change auth method to client_secret_post and succeed', async () => {
            // Update MCP Resource settings
            await patchProtectedResource(masterDomain.id, accessToken, mcpResource.id, {
            settings: {
                oauth: {
                    tokenEndpointAuthMethod: 'client_secret_post'
                }
            }
            });

            await waitForDomainSync(masterDomain.id, accessToken);

            mcpResource = await getMcpServer(masterDomain.id, accessToken, mcpResource.id);
            expect(mcpResource.settings.oauth.tokenEndpointAuthMethod).toEqual('client_secret_post');

            const response = await performPost(
                openIdConfiguration.token_endpoint,
                '',
                `grant_type=client_credentials&client_id=${mcpResource.clientId}&client_secret=${clientSecret.secret}`,
                {
                    'Content-type': 'application/x-www-form-urlencoded'
                }
            ).expect(200);
            expect(response.body.access_token).toBeDefined();

        });

         it('should fail using client_secret_basic when POST is configured', async () => {
            await performPost(
                openIdConfiguration.token_endpoint,
                '',
                'grant_type=client_credentials',
                {
                    'Content-type': 'application/x-www-form-urlencoded',
                    Authorization: 'Basic ' + getBase64BasicAuth(mcpResource.clientId, clientSecret.secret),
                }
            ).expect(401);
        });
    });

    describe('Scope Restrictions', () => {
        beforeAll(async () => {
             // Reset to BASIC auth for easier testing and set allowed scopes
             await patchProtectedResource(masterDomain.id, accessToken, mcpResource.id, {
                settings: {
                    oauth: {
                        tokenEndpointAuthMethod: 'client_secret_basic',
                        scopeSettings: [
                            { scope: 'scope1', defaultScope: false }
                        ]
                    }
                }
            });

            await waitForDomainSync(masterDomain.id, accessToken);

            mcpResource = await getMcpServer(masterDomain.id, accessToken, mcpResource.id);
            expect(mcpResource.settings.oauth.scopeSettings).toBeDefined();

            await waitFor(5000);
        });

        it('should succeed requesting allowed scope', async () => {
             const response = await performPost(
                openIdConfiguration.token_endpoint,
                '',
                'grant_type=client_credentials&scope=scope1',
                {
                    'Content-type': 'application/x-www-form-urlencoded',
                    Authorization: 'Basic ' + getBase64BasicAuth(mcpResource.clientId, clientSecret.secret),
                }
            ).expect(200);
            
            expect(response.body.scope).toContain('scope1');
        });

        it('should fail requesting unknown scope', async () => {
             await performPost(
                openIdConfiguration.token_endpoint,
                '',
                'grant_type=client_credentials&scope=unknown_scope',
                {
                    'Content-type': 'application/x-www-form-urlencoded',
                    Authorization: 'Basic ' + getBase64BasicAuth(mcpResource.clientId, clientSecret.secret),
                }
            ).expect(400); // invalid_scope
        });
    });

    describe('Custom Claims', () => {
         beforeAll(async () => {
             // Add a custom claim
             await patchProtectedResource(masterDomain.id, accessToken, mcpResource.id, {
                settings: {
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
                }
            });
            await waitForDomainSync(masterDomain.id, accessToken);
        });

        it('should include custom claim in access token', async () => {
            const response = await performPost(
                openIdConfiguration.token_endpoint,
                '',
                'grant_type=client_credentials',
                {
                    'Content-type': 'application/x-www-form-urlencoded',
                    Authorization: 'Basic ' + getBase64BasicAuth(mcpResource.clientId, clientSecret.secret),
                }
            ).expect(200);

            const accessToken = response.body.access_token;
            // Decode token without library if possible, or assume success implies presence if we could check payload
            // For checking payload in test, we need a simple JWT decoder
            const parts = accessToken.split('.');
            const payload = JSON.parse(Buffer.from(parts[1], 'base64').toString());

            expect(payload['test-custom-claim']).toBeDefined();
            expect(payload['test-custom-claim']).toEqual('custom-claim-value');
        });
    });


    describe('Token Validity', () => {
        beforeAll(async () => {
             // Set short token validity
             await patchProtectedResource(masterDomain.id, accessToken, mcpResource.id, {
                settings: {
                    oauth: {
                        accessTokenValiditySeconds: 5,
                        tokenEndpointAuthMethod: 'client_secret_basic'
                    }
                }
            });
            await waitForDomainSync(masterDomain.id, accessToken);

            mcpResource = await getMcpServer(masterDomain.id, accessToken, mcpResource.id);
            expect(mcpResource.settings.oauth.accessTokenValiditySeconds).toEqual(5);

            await waitFor(5000);
        });

        it('should respect access token validity', async () => {
            const response = await performPost(
                openIdConfiguration.token_endpoint,
                '',
                'grant_type=client_credentials',
                {
                    'Content-type': 'application/x-www-form-urlencoded',
                    Authorization: 'Basic ' + getBase64BasicAuth(mcpResource.clientId, clientSecret.secret),
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
            await patchProtectedResource(masterDomain.id, accessToken, mcpResource.id, {
               settings: {
                   oauth: {
                       tokenEndpointAuthMethod: 'client_secret_basic'
                   }
               }
           });
           await waitForDomainSync(masterDomain.id, accessToken);
       });

        it('should reject password grant type', async () => {
             await performPost(
                openIdConfiguration.token_endpoint,
                '',
                'grant_type=password&username=user&password=password', // Attempt password grant
                {
                    'Content-type': 'application/x-www-form-urlencoded',
                    Authorization: 'Basic ' + getBase64BasicAuth(mcpResource.clientId, clientSecret.secret),
                }
            ).expect(400); // Should be unsupported_grant_type or unauthorized_client
        });
    });
});
