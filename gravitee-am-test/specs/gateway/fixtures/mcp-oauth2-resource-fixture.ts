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
import {expect} from '@jest/globals';
import {requestAdminAccessToken} from '@management-commands/token-management-commands';
import {
    createDomain,
    safeDeleteDomain,
    startDomain, waitFor,
    waitForDomainStart,
    waitForDomainSync,
} from '@management-commands/domain-management-commands';
import {createScope} from '@management-commands/scope-management-commands';
import {
    createProtectedResource,
    patchProtectedResource,
    createMcpClientSecret, getMcpServer,
} from '@management-commands/protected-resources-management-commands';
import {Domain} from '@management-models/Domain';
import {uniqueName} from '@utils-commands/misc';

export interface McpOAuth2ResourceFixture {
    domain: Domain;
    mcpResource: any;
    clientSecret: any;
    openIdConfiguration: any;
    accessToken: string;
    cleanup: () => Promise<void>;
    updateMcpResourceSettings: (settings: any) => Promise<void>;
    refreshMcpResource: () => Promise<void>;
}

export const setupMcpOAuth2ResourceFixture = async (settings: any = {}): Promise<McpOAuth2ResourceFixture> => {
    const accessToken = await requestAdminAccessToken();
    expect(accessToken).toBeDefined();

    // 1. Create Domain
    let domain = await createDomain(accessToken, uniqueName('mcp-oauth2-test', true), 'test mcp oauth2');
    expect(domain).toBeDefined();

    // 2. Create Scope
    await createScope(domain.id, accessToken, {
        key: 'scope1',
        name: 'Scope 1',
        description: 'Test scope 1',
    });

    // 3. Create MCP Resource
    let mcpResource = await createProtectedResource(domain.id, accessToken, {
        name: uniqueName('mcp-resource'),
        type: 'MCP_SERVER',
        resourceIdentifiers: ['https://example.com/mcp'],
    });

    mcpResource = await getMcpServer(domain.id, accessToken, mcpResource.id);
    expect(mcpResource).toBeDefined();

    // Create a client secret for the MCP resource
    const clientSecret = await createMcpClientSecret(domain.id, accessToken, mcpResource.id, {name: 'default-secret'});
    expect(clientSecret).toBeDefined();

    // If settings are provided, apply them before starting the domain
    if (Object.keys(settings).length > 0) {
        await patchProtectedResource(domain.id, accessToken, mcpResource.id, {
            settings: settings
        });
        // No need to wait for sync here as domain is not started yet.
        // Refresh mcpResource
        mcpResource = await getMcpServer(domain.id, accessToken, mcpResource.id);
    }

    // 4. Start Domain
    const domainStarted = await startDomain(domain.id, accessToken).then(() => waitForDomainStart(domain));
    domain = domainStarted.domain;
    const openIdConfiguration = domainStarted.oidcConfig;

    // Wait for sync
    await waitForDomainSync(domain.id, accessToken);

    const cleanup = async () => {
        if (domain) {
            await safeDeleteDomain(domain.id, accessToken);
        }
    };

    const updateMcpResourceSettings = async (settings: any) => {
        await patchProtectedResource(domain.id, accessToken, mcpResource.id, {
            settings: settings
        });
        await waitForDomainSync(domain.id, accessToken);
        await waitFor(2500);

        // Refresh local mcpResource to ensure it's up to date if needed, though often we just need the resource ID
        mcpResource = await getMcpServer(domain.id, accessToken, mcpResource.id);
    };

    const refreshMcpResource = async () => {
        mcpResource = await getMcpServer(domain.id, accessToken, mcpResource.id);
    }

    return {
        domain,
        get mcpResource() {
            return mcpResource;
        },
        clientSecret,
        openIdConfiguration,
        accessToken,
        cleanup,
        updateMcpResourceSettings,
        refreshMcpResource
    };
};
