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
import {afterAll, beforeAll, describe, expect, it, jest} from '@jest/globals';
import {requestAdminAccessToken} from '@management-commands/token-management-commands';
import {
    safeDeleteDomain,
    setupDomainForTest,
} from '@management-commands/domain-management-commands';
import {waitForSyncAfter} from '@gateway-commands/monitoring-commands';
import {uniqueName} from '@utils-commands/misc';
import {buildTestUser, createUser} from '@management-commands/user-management-commands';
import {createAuthorizationEngine, deleteAuthorizationEngine} from '@management-commands/authorization-engine-management-commands';
import {addTuple} from '@management-commands/openfga-settings-commands';
import {createScope} from '@management-commands/scope-management-commands';
import {mcpAuthorizationModel, tupleFactory, authzenFactory} from '@api-fixtures/openfga-fixtures';
import {AuthorizationEngine} from '@management-models/AuthorizationEngine';
import {createApplication, updateApplication} from '@management-commands/application-management-commands';
import {getAllIdps} from '@management-commands/idp-management-commands';
import {performPost, performGet, requestClientCredentialsToken} from '@gateway-commands/oauth-oidc-commands';
import {evaluateAccess} from '@gateway-commands/authzen-commands';
import {
    createProtectedResource,
    updateProtectedResource
} from '@management-commands/protected-resources-management-commands';
import {getBase64BasicAuth} from '@gateway-commands/utils';
import type {NewMcpTool} from '@management-models/NewMcpTool';
import type {UpdateMcpTool} from '@management-models/UpdateMcpTool';
import {login} from '@gateway-commands/login-commands';
import {UpdateProtectedResource} from '@management-models/UpdateProtectedResource';

global.fetch = fetch;

let accessToken: string; // Admin token for management API
let testDomain: any;
let testUser1: any; // Owner user
let webApp: any; // Web application for authorization code flow
let openIdConfig: any;
let mcpServer: any; // MCP server (protected resource)
let authEngine: AuthorizationEngine;
let storeId: string;
let authorizationModelId: string;

jest.setTimeout(200000);

beforeAll(async () => {
    // 1. Get admin access token
    accessToken = await requestAdminAccessToken();
    expect(accessToken).toBeDefined();

    // 2. Create unique test domain and wait for it to be ready
    const domainSetup = await setupDomainForTest(uniqueName('agentic-flow', true), {accessToken, waitForStart: true});
    testDomain = domainSetup.domain;
    expect(testDomain).toBeDefined();

    openIdConfig = domainSetup.oidcConfig;

    // 4. Create OpenFGA store
    const storeName = `agentic-flow-store-${Date.now()}`;
    const storeResponse = await fetch(`${process.env.AM_OPENFGA_URL}/stores`, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({name: storeName}),
    });
    expect(storeResponse.status).toBe(201);
    storeId = (await storeResponse.json()).id;

    // 5. Create authorization model
    const modelResponse = await fetch(`${process.env.AM_OPENFGA_URL}/stores/${storeId}/authorization-models`, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(mcpAuthorizationModel),
    });
    expect(modelResponse.status).toBe(201);
    authorizationModelId = (await modelResponse.json()).authorization_model_id;

    // 6. Create authorization engine
    authEngine = await createAuthorizationEngine(testDomain.id, accessToken, {
        type: 'openfga',
        name: 'Agentic Flow Authorization Engine',
        configuration: JSON.stringify({
            connectionUri: process.env.AM_INTERNAL_OPENFGA_URL,
            storeId,
            authorizationModelId,
            apiToken: '',
        }),
    });
    expect(authEngine?.id).toBeDefined();

    // 7. Create OAuth scopes for MCP tools
    await createScope(testDomain.id, accessToken, {
        key: 'weather:read',
        name: 'Weather Read',
        description: 'Read access to weather tool',
    });
    await createScope(testDomain.id, accessToken, {
        key: 'calendar:read',
        name: 'Calendar Read',
        description: 'Read access to calendar tool',
    });
    await createScope(testDomain.id, accessToken, {
        key: 'calendar:write',
        name: 'Calendar Write',
        description: 'Write access to calendar tool',
    });

    // 8. Create MCP Server (Protected Resource) without scopes first
    const mcpServerResourceUri = 'https://agentic-mcp-server.example.com';

    mcpServer = await createProtectedResource(testDomain.id, accessToken, {
        name: 'Agentic MCP Server',
        type: 'MCP_SERVER',
        resourceIdentifiers: [mcpServerResourceUri],
    });
    expect(mcpServer?.id).toBeDefined();
    expect(mcpServer?.clientId).toBeDefined();
    expect(mcpServer?.clientSecret).toBeDefined();

    // Store resource identifier for later use (API response doesn't include it)
    mcpServer.resourceIdentifier = mcpServerResourceUri;

    // 9. Update MCP Server to assign scopes to tools (separate request)
    const updateToolsRequest = {
        name: mcpServer.name,
        resourceIdentifiers: [mcpServerResourceUri],
        features: [
            {
                key: 'calendar_tool',
                type: 'MCP_TOOL',
                description: 'Access calendar',
                scopes: ['calendar:read', 'calendar:write'],
            } as UpdateMcpTool,
            {
                key: 'weather_tool',
                type: 'MCP_TOOL',
                description: 'Get weather information',
                scopes: ['weather:read'],
            } as UpdateMcpTool,
        ],
    } as UpdateProtectedResource;

    await updateProtectedResource(testDomain.id, accessToken, mcpServer.id, updateToolsRequest);

    // 10. Create Web Application for authorization code flow
    const idpSet = await getAllIdps(testDomain.id, accessToken);
    const defaultIdp = idpSet.values().next().value;

    const appClientId = uniqueName('agentic-app', true);
    const appClientSecret = uniqueName('agentic-secret', true);
    webApp = await createApplication(testDomain.id, accessToken, {
        name: 'Agentic Flow Web App',
        type: 'WEB',
        clientId: appClientId,
        clientSecret: appClientSecret,
        redirectUris: ['https://callback.example.com'],
    }).then((app) =>
        updateApplication(
            testDomain.id,
            accessToken,
            {
                settings: {
                    oauth: {
                        redirectUris: ['https://callback.example.com'],
                        grantTypes: ['authorization_code'],
                        scopeSettings: [
                            {scope: 'openid', defaultScope: true},
                            {scope: 'profile', defaultScope: true}
                        ],
                    },
                    advanced: {
                        skipConsent: true,
                    },
                },
                identityProviders: [{identity: defaultIdp.id, priority: -1}],
            },
            app.id,
        ).then((updatedApp) => {
            updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
            return updatedApp;
        }),
    );

    // 11. Create test users
    const user1Data = buildTestUser(0);
    testUser1 = await createUser(testDomain.id, accessToken, user1Data);
    testUser1.password = user1Data.password;

    // 12. Add OpenFGA tuples - User1 is owner, User2 is viewer
    await waitForSyncAfter(testDomain.id,
        () => addTuple(testDomain.id, authEngine.id, accessToken, tupleFactory.ownerTuple(testUser1.username, 'weather_tool')),
    );
});

afterAll(async () => {
    if (authEngine?.id) {
        await deleteAuthorizationEngine(testDomain.id, authEngine.id, accessToken);
    }
    if (storeId) {
        await fetch(`${process.env.AM_OPENFGA_URL}/stores/${storeId}`, {
            method: 'DELETE',
        });
    }
    if (testDomain?.id) {
        await safeDeleteDomain(testDomain.id, accessToken);
    }
});

/**
 * FLOW:
 * 1. User authenticates with authorization code flow (resource parameter in auth endpoint)
 * 2. Application exchanges code for access token (resource parameter in token endpoint)
 * 3. Token contains: audience (MCP server URI) + scopes (tool permissions)
 * 4. MCP Server introspects token to validate scopes and user
 * 5. MCP Server calls AuthZEN to check fine-grained access to specific tool
 * 6. AuthZEN evaluates against OpenFGA tuples (owner/viewer relations)
 */
describe('Agentic Flow - User → Application → Token → MCP Server → AuthZEN', () => {
    it('should complete the full agentic flow with dual protection (scopes + authzen)', async () => {
        // Step 1: User authenticates with resource parameter in authorization request
        const redirect_uri = webApp.settings.oauth.redirectUris[0];
        const clientId = webApp.settings.oauth.clientId;

        // Build authorization URL with resource parameter
        const authParams = new URLSearchParams({
            response_type: 'code',
            client_id: clientId,
            redirect_uri: redirect_uri,
            resource: mcpServer.resourceIdentifier,
            scope: 'calendar:read calendar:write weather:read openid profile',
        });
        const authUrl = `${openIdConfig.authorization_endpoint}?${authParams.toString()}`;

        // Initiate authorization request
        const authResponse = await performGet(authUrl).expect(302);
        expect(authResponse.headers['location']).toContain('/login');

        // User logs in
        const loginResponse = await login(authResponse, testUser1.username, clientId, testUser1.password, false, false);

        // Complete authorization
        const authorizeResponse = await performGet(loginResponse.headers['location'], '', {
            Cookie: loginResponse.headers['set-cookie'],
        }).expect(302);

        // Step 2: Extract authorization code from redirect
        const redirectUrl = authorizeResponse.headers['location'];
        expect(redirectUrl).toContain(redirect_uri);
        expect(redirectUrl).toContain('code=');

        const codePattern = /code=([-_a-zA-Z0-9]+)&?/;
        const authorizationCode = redirectUrl.match(codePattern)[1];
        expect(authorizationCode).toBeDefined();

        // Step 3: Exchange authorization code for access token with resource parameter
        const tokenParams = new URLSearchParams({
            grant_type: 'authorization_code',
            code: authorizationCode,
            redirect_uri: redirect_uri,
            resource: mcpServer.resourceIdentifier,
        });

        const tokenResponse = await performPost(
            openIdConfig.token_endpoint,
            '',
            tokenParams.toString(),
            {
                'Content-Type': 'application/x-www-form-urlencoded',
                Authorization: 'Basic ' + getBase64BasicAuth(webApp.settings.oauth.clientId, webApp.settings.oauth.clientSecret),
            },
        );

        expect(tokenResponse.status).toBe(200);
        expect(tokenResponse.body.access_token).toBeDefined();
        expect(tokenResponse.body.token_type).toBe('bearer');

        const userAccessToken = tokenResponse.body.access_token;

        // Step 4: MCP Server introspects the token to validate it
        const introspectionResponse = await performPost(
            openIdConfig.introspection_endpoint,
            '',
            `token=${userAccessToken}&token_type_hint=access_token`,
            {
                'Content-Type': 'application/x-www-form-urlencoded',
                Authorization: 'Basic ' + getBase64BasicAuth(mcpServer.clientId, mcpServer.clientSecret),
            },
        );

        expect(introspectionResponse.status).toBe(200);
        expect(introspectionResponse.body.active).toBe(true);
        expect(introspectionResponse.body.client_id).toBeDefined();
        expect(introspectionResponse.body.username).toBe(testUser1.username);
        expect(introspectionResponse.body.sub).toBeDefined();
        // Verify token audience contains MCP server resource identifier
        expect(introspectionResponse.body.aud).toContain(mcpServer.resourceIdentifier);
        // Verify token contains correct scopes
        expect(introspectionResponse.body.scope).toContain('calendar:read');
        expect(introspectionResponse.body.scope).toContain('calendar:write');
        expect(introspectionResponse.body.scope).toContain('weather:read');

        // Step 5: MCP Server calls AuthZEN to check fine-grained access to specific tool
        // MCP Server obtains an access token using client_credentials grant
        const mcpServerAccessToken = await requestClientCredentialsToken(
            mcpServer.clientId,
            mcpServer.clientSecret,
            openIdConfig,
        );

        const authzenRequest = authzenFactory.canAccess(
            introspectionResponse.body.username,
            'weather_tool',
            'tool',
        );

        const authzenResponse = await evaluateAccess(
            testDomain.hrid,
            mcpServerAccessToken,
            authzenRequest,
        );

        // Step 6: AuthZEN evaluates against OpenFGA tuples (owner/viewer relations)
        expect(authzenResponse.decision).toBe(true);
    });
});
