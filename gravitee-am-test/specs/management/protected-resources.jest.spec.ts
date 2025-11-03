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
import fetch from "cross-fetch";
import faker from "faker";
import { afterAll, beforeAll, expect, jest } from '@jest/globals';

import {Domain} from "@management-models/Domain";
import {NewProtectedResource} from "@management-models/NewProtectedResource";
import {UpdateProtectedResource} from "@management-models/UpdateProtectedResource";
import {UpdateMcpTool} from "@management-models/UpdateMcpTool";
import {NewMcpTool} from "@management-models/NewMcpTool";
import {McpToolFeature} from "@management-models/McpToolFeature";
import {ProtectedResourceFeature} from "@management-models/ProtectedResourceFeature";

import {requestAdminAccessToken} from "@management-commands/token-management-commands";
import { deleteDomain, setupDomainForTest, waitFor } from '@management-commands/domain-management-commands';
import {createApplication, updateApplication} from "@management-commands/application-management-commands";
import {
    createProtectedResource,
    getMcpServer,
    getMcpServers,
    updateProtectedResource,
    patchProtectedResource,
    deleteProtectedResource,
    waitForProtectedResourceRemovedFromList
} from "@management-commands/protected-resources-management-commands";
import {createScope} from "@management-commands/scope-management-commands";
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token, getBase64BasicAuth } from '@gateway-commands/utils';
import {uniqueName} from "@utils-commands/misc";
import { retryUntil } from '@utils-commands/retry';

function generateValidProtectedResourceName(): string {
    return `test-${faker.datatype.number({min: 1000, max: 9999})}-${faker.helpers.slugify(faker.commerce.productName()).replace(/[^a-zA-Z0-9_-]/g, '_')}`;
}

function isMcpToolFeature(feature: ProtectedResourceFeature): feature is McpToolFeature {
    return feature.type === 'MCP_TOOL' || feature.type === 'mcp_tool';
}

globalThis.fetch = fetch;
jest.setTimeout(200000);

let accessToken: string;
let domain: Domain;
let domainTestSearch: Domain;
let openIdConfiguration: any;

beforeAll(async () => {
    accessToken = await requestAdminAccessToken();
    const domainResult = await setupDomainForTest(uniqueName('domain-protected-resources'), { accessToken, waitForStart:true });
    domain = domainResult.domain;
    openIdConfiguration = domainResult.oidcConfig;
    domainTestSearch = await setupDomainForTest(uniqueName('domain-protected-resources-search'), { accessToken, waitForStart:true}).then((it) => it.domain);
});

afterAll(async () => {
    if (domain?.id) {
        await deleteDomain(domain.id, accessToken);
    }
    if (domainTestSearch?.id) {
        await deleteDomain(domainTestSearch.id, accessToken);
    }
});

describe('When creating protected resource', () => {
    it('New Protected Resource should be created', async () => {
        const request = {
            name: generateValidProtectedResourceName(),
            type: "MCP_SERVER",
            resourceIdentifiers: ["https://something.com", "https://something2.com"],
            features: [
                {
                    key: 'get_weather',
                    type: 'MCP_TOOL',
                    description: 'Description'
                },
                {
                    key: 'get_something',
                    type: 'MCP_TOOL',
                    description: 'Description 2'
                }]
        } as NewProtectedResource;

        const createdResource = await createProtectedResource(domain.id, accessToken, request);
        expect(createdResource).toBeDefined();
        expect(createdResource.id).toBeDefined();
        expect(createdResource.name).toEqual(request.name);
        expect(createdResource.clientSecret).toBeDefined();
        expect(createdResource.clientId).toBeDefined();
    });

    it('New Protected Resource should be created with set clientId', async () => {
        const request = {
            name: generateValidProtectedResourceName(),
            clientId: "client-id",
            type: "MCP_SERVER",
            resourceIdentifiers: ["https://something3.com"]
        } as NewProtectedResource;

        const createdResource = await createProtectedResource(domain.id, accessToken, request);
        expect(createdResource).toBeDefined();
        expect(createdResource.id).toBeDefined();
        expect(createdResource.name).toEqual(request.name);
        expect(createdResource.clientSecret).toBeDefined();
        expect(createdResource.clientId).toEqual(request.clientId);
    });

    it('New Protected Resource should be created with set clientSecret', async () => {
        const request = {
            name: generateValidProtectedResourceName(),
            clientId: "client-id2",
            clientSecret: "secret",
            type: "MCP_SERVER",
            resourceIdentifiers: ["https://something4.com"]
        } as NewProtectedResource;

        const createdResource = await createProtectedResource(domain.id, accessToken, request);
        expect(createdResource).toBeDefined();
        expect(createdResource.id).toBeDefined();
        expect(createdResource.name).toEqual(request.name);
        expect(createdResource.clientSecret).toEqual(request.clientSecret);
        expect(createdResource.clientId).toEqual(request.clientId);
    });

    it('Protected Resource must not be created with same clientId', async () => {
        const request = {
            name: generateValidProtectedResourceName(),
            clientId: "client-id3",
            clientSecret: "secret",
            type: "MCP_SERVER",
            resourceIdentifiers: ["https://something5.com"]
        } as NewProtectedResource;

        const createdResource = await createProtectedResource(domain.id, accessToken, request);
        expect(createdResource).toBeDefined();
        expect(createdResource.id).toBeDefined();
        expect(createdResource.name).toEqual(request.name);
        expect(createdResource.clientSecret).toEqual(request.clientSecret);
        expect(createdResource.clientId).toEqual(request.clientId);

        await createProtectedResource(domain.id, accessToken, request)
            .catch(err => expect(err.response.status).toEqual(400))
    });

    it('Protected Resource must not be created with same clientId in application', async () => {
        const appRequest = {
            name: generateValidProtectedResourceName(),
            type: 'service',
            description: faker.lorem.paragraph(),
            clientId: 'client-id4',
        };

        await createApplication(domain.id, accessToken, appRequest);

        const request = {
            name: generateValidProtectedResourceName(),
            clientId: appRequest.clientId,
            clientSecret: "secret",
            type: "MCP_SERVER",
            resourceIdentifiers: ["https://something6.com"]
        } as NewProtectedResource;

        await createProtectedResource(domain.id, accessToken, request)
            .catch(err => expect(err.response.status).toEqual(400))
    });

    it('Protected Resource must not be created without type', async () => {
        const request = {
            name: generateValidProtectedResourceName(),
            resourceIdentifiers: ["https://something7.com", "https://something8.com"]
        } as NewProtectedResource;

        await createProtectedResource(domain.id, accessToken, request)
            .catch(err => expect(err.response.status).toEqual(400))
    });

    it('Protected Resource must not be created with wrong type', async () => {
        const request = {
            name: generateValidProtectedResourceName(),
            type: 'MCP_SERVERR',
            resourceIdentifiers: ["https://something9.com", "https://something10.com"]
        } as NewProtectedResource;

        await createProtectedResource(domain.id, accessToken, request)
            .catch(err => expect(err.response.status).toEqual(400))
    });

    it('Protected Resource must not be created with wrong resource identifier', async () => {
        const request = {
            name: generateValidProtectedResourceName(),
            type: 'MCP_SERVER',
            resourceIdentifiers: ["something", "https://something11.com"]
        } as NewProtectedResource;

        await createProtectedResource(domain.id, accessToken, request)
            .catch(err => expect(err.response.status).toEqual(400))
    });

    it('Protected Resource must not be created if resourceIdentifier already exists', async () => {
        const request = {
            name: generateValidProtectedResourceName(),
            type: 'MCP_SERVER',
            resourceIdentifiers: ["https://something13.com"]
        } as NewProtectedResource;

        const first = await createProtectedResource(domain.id, accessToken, request)
        expect(first.id).toBeDefined();

        await createProtectedResource(domain.id, accessToken, request)
            .catch(err => expect(err.response.status).toEqual(400))
    });
    it('Protected Resource resource identifier http(s)://localhost is correct', async () => {
        const httpsRequest = {
            name: generateValidProtectedResourceName(),
            type: 'MCP_SERVER',
            resourceIdentifiers: ["https://localhost.com"]
        } as NewProtectedResource;

        const created1 = await createProtectedResource(domain.id, accessToken, httpsRequest);
        expect(created1.id).toBeDefined();

        const httpRequest = {
            name: generateValidProtectedResourceName(),
            type: 'MCP_SERVER',
            resourceIdentifiers: ["http://localhost.com"]
        } as NewProtectedResource;

        const created2 = await createProtectedResource(domain.id, accessToken, httpRequest);
        expect(created2.id).toBeDefined();
    });

    it('Protected Resource resource identifier and lowercased', async () => {
        const badRequest = {
            name: generateValidProtectedResourceName(),
            type: 'MCP_SERVER',
            resourceIdentifiers: [" https://toTrimDomain.com   "]
        } as NewProtectedResource;

        createProtectedResource(domain.id, accessToken, badRequest)
            .catch(err => expect(err.response.status).toEqual(400))

        const correctRequest = {
            name: generateValidProtectedResourceName(),
            type: 'MCP_SERVER',
            resourceIdentifiers: ["https://toLowerCaseDomain.com"]
        } as NewProtectedResource;

        const created = await createProtectedResource(domain.id, accessToken, correctRequest);
        expect(created.id).toBeDefined();

        const anotherCorrectRequest = {
            name: generateValidProtectedResourceName(),
            type: 'MCP_SERVER',
            resourceIdentifiers: ["https://tolowercasedomain.com"]
        } as NewProtectedResource;

        createProtectedResource(domain.id, accessToken, anotherCorrectRequest)
            .catch(err => expect(err.response.status).toEqual(400));
    });

    it('Protected Resource can introspect token', async () => {
        // Create an application which we can use to get a token from
        const application = await createApplication(domain.id, accessToken, {
            name: uniqueName('intro-app'),
            type: 'SERVICE',
            clientId: 'app',
            clientSecret: 'app',
        }).then((app) =>
            updateApplication(
                domain.id,
                accessToken,
                {
                    settings: {
                        oauth: {
                            grantTypes: ['client_credentials'],
                        },
                    },
                },
                app.id,
            ).then((updatedApp) => {
                updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
                updatedApp.settings.oauth.clientId = app.settings.oauth.clientId;
                return updatedApp;
            }),
        );
        expect(application).toBeDefined();

        await waitFor(5000);

        // First request to get a token
        const response = await performPost(openIdConfiguration.token_endpoint, '', 'grant_type=client_credentials', {
            'Content-type': 'application/x-www-form-urlencoded',
            Authorization: 'Basic ' + applicationBase64Token(application),
        }).expect(200);

        const responseJwt = response.body.access_token;
        expect(responseJwt).toBeDefined();

        // Next request to introspect to token using the Application credentials
        await performPost(openIdConfiguration.introspection_endpoint, '', `token=${responseJwt}`, {
            'Content-type': 'application/x-www-form-urlencoded',
            Authorization: 'Basic ' + applicationBase64Token(application),
        }).expect(200);

        // Now we create a Protected Resource which we can use to introspect the existing token
        const request = {
            name: generateValidProtectedResourceName(),
            type: 'MCP_SERVER',
            resourceIdentifiers: ['https://other-something.com', 'https://other-something2.com'],
        } as NewProtectedResource;

        const createdResource = await createProtectedResource(domain.id, accessToken, request);
        expect(createdResource).toBeDefined();

        // Wait for the resource to be synced
        await waitFor(5000);

        // Make the request to introspect the token using the Protected Resource credentials
        await performPost(openIdConfiguration.introspection_endpoint, '', `token=${responseJwt}`, {
            'Content-type': 'application/x-www-form-urlencoded',
            Authorization: 'Basic ' + getBase64BasicAuth(createdResource.clientId, createdResource.clientSecret),
        }).expect(200);
    });

    it('Protected Resource must not be created when feature type is unknown', async () => {
        const request = {
            name: generateValidProtectedResourceName(),
            type: 'MCP_SERVER',
            resourceIdentifiers: ["https://unkownToolName.com"],
            features: [
                {
                    key: 'key',
                    type: 'ABC' // Invalid feature type - intentionally testing validation
                }
            ]
        } as unknown as NewProtectedResource; // Type assertion needed to bypass TypeScript's enum validation for testing invalid data

        await createProtectedResource(domain.id, accessToken, request)
            .catch(err => expect(err.response.status).toEqual(400))
    });

    it('Protected Resource must not be created when feature type is missing', async () => {
        const request = {
            name: generateValidProtectedResourceName(),
            type: 'MCP_SERVER',
            resourceIdentifiers: ["https://unkownToolName.com"],
            features: [
                {
                    key: 'key',
                }
            ]
        } as NewProtectedResource;

        await createProtectedResource(domain.id, accessToken, request)
            .catch(err => expect(err.response.status).toEqual(400))
    });

    it('Protected Resource must not be created when keys are duplicated', async () => {
        const request = {
            name: generateValidProtectedResourceName(),
            type: 'MCP_SERVER',
            resourceIdentifiers: ["https://unkownToolName.com"],
            features: [
                {
                    key: 'key',
                    type: 'MCP_TOOL'
                },
                {
                    key: 'key',
                    type: 'MCP_TOOL'
                }
            ]
        } as NewProtectedResource;

        await createProtectedResource(domain.id, accessToken, request)
            .catch(err => expect(err.response.status).toEqual(400))
    });

    it('Protected Resource must not be created when key is missing', async () => {
        const request = {
            name: generateValidProtectedResourceName(),
            type: 'MCP_SERVER',
            resourceIdentifiers: ["https://unkownToolName.com"],
            features: [
                {
                    key: 'key',
                    type: 'MCP_TOOL'
                },
                {
                    type: 'MCP_TOOL'
                }
            ]
        } as NewProtectedResource;

        await createProtectedResource(domain.id, accessToken, request)
            .catch(err => expect(err.response.status).toEqual(400))
    });

    it('Protected Resource must not be created when key doesnt follow regex pattern', async () => {
        const request = {
            name: generateValidProtectedResourceName(),
            type: 'MCP_SERVER',
            resourceIdentifiers: ["https://unkownToolName.com"],
            features: [
                {
                    key: 'key$',
                    type: 'MCP_TOOL'
                },
                {
                    type: 'MCP_TOOL'
                }
            ]
        } as NewProtectedResource;

        await createProtectedResource(domain.id, accessToken, request)
            .catch(err => expect(err.response.status).toEqual(400))

        const request2 = {
            name: generateValidProtectedResourceName(),
            type: 'MCP_SERVER',
            resourceIdentifiers: ["https://unkownToolName.com"],
            features: [
                {
                    key: 'key asdsad',
                    type: 'MCP_TOOL'
                },
                {
                    type: 'MCP_TOOL'
                }
            ]
        } as NewProtectedResource;

        await createProtectedResource(domain.id, accessToken, request2)
            .catch(err => expect(err.response.status).toEqual(400))
    });
});

describe('When admin created bunch of Protected Resources', () => {
    beforeAll(async () => {
        for (let i = 0; i < 100; i++) {
            const request = {
                name: generateValidProtectedResourceName(),
                type: "MCP_SERVER",
                resourceIdentifiers: [`https://abc${i}.com`, `https://abc${i}a${i}.com`],
                features: [
                    {
                        key: 'key',
                        type: 'MCP_TOOL'
                    }
                ]
            } as NewProtectedResource;
            await createProtectedResource(domainTestSearch.id, accessToken, request);
        }
    });

    it('Protected Resource page must not contain secret', async () => {
        const page = await getMcpServers(domainTestSearch.id, accessToken, 100, 0);
        const secretNotPresent = page.data.every(data => data['secret'] === undefined);
        expect(secretNotPresent).toBeTruthy();
    });

    it('Protected Resource page should be returned', async () => {
        const page = await getMcpServers(domainTestSearch.id, accessToken, 10, 0);
        expect(page.currentPage).toEqual(0);
        expect(page.totalCount).toEqual(100);
        expect(page.data).toHaveLength(10);
    });

    it('Protected Resource page 1 should be different than 2', async () => {
        const page1 = await getMcpServers(domainTestSearch.id, accessToken, 10, 0);
        expect(page1.currentPage).toEqual(0);
        expect(page1.totalCount).toEqual(100);
        expect(page1.data).toHaveLength(10);

        const page2 = await getMcpServers(domainTestSearch.id, accessToken, 10, 1);
        expect(page2.currentPage).toEqual(1);
        expect(page2.totalCount).toEqual(100);
        expect(page2.data).toHaveLength(10);

        expect(page1.data[0].id).not.toEqual(page2.data[0].id);
    });

    it('No data returned if page exceeds elements count', async () => {
        const page1 = await getMcpServers(domainTestSearch.id, accessToken, 10, 200);
        expect(page1.currentPage).toEqual(200);
        expect(page1.totalCount).toEqual(100);
        expect(page1.data).toHaveLength(0);
    });

    it('All data should be returned if size exceeds elements count', async () => {
        const page1 = await getMcpServers(domainTestSearch.id, accessToken, 200, 0);
        expect(page1.currentPage).toEqual(0);
        expect(page1.totalCount).toEqual(100);
        expect(page1.data).toHaveLength(100);
    });

    it('Protected Resource page should be returned sorted by name', async () => {
        const pageAsc = await getMcpServers(domainTestSearch.id, accessToken, 100, 0, 'name.asc');
        const pageDesc = await getMcpServers(domainTestSearch.id, accessToken, 100, 0, 'name.desc');
        expect(pageAsc.data[0].name).toEqual(pageDesc.data[99].name);
        expect(pageAsc.data[0].id).not.toEqual(pageDesc.data[0].id);
    });

    it('Protected Resource page should be returned sorted by updatedAt', async () => {
        const pageAsc = await getMcpServers(domainTestSearch.id, accessToken, 100, 0, 'updatedAt.asc');
        const pageDesc = await getMcpServers(domainTestSearch.id, accessToken, 100, 0, 'updatedAt.desc');
        expect(pageAsc.data[0].updatedAt).toEqual(pageDesc.data[99].updatedAt);
        expect(pageAsc.data[0].id).not.toEqual(pageDesc.data[0].id);
    });

    it('Protected Resource can be found by its id', async () => {
        const page = await getMcpServers(domainTestSearch.id, accessToken, 100, 0, 'updatedAt.asc');
        const protectedResourcePrimaryData = page.data[55];

        const fetched = await getMcpServer(domainTestSearch.id, accessToken, protectedResourcePrimaryData.id);
        expect(fetched).toEqual(protectedResourcePrimaryData);
    });

});

describe('When updating protected resource', () => {
    let createdResource;

    beforeAll(async () => {
        // Create test scopes for validation
        await createScope(domain.id, accessToken, {
            key: 'test_scope_1',
            name: 'Test Scope 1',
            description: 'Test scope for protected resource updates'
        });

        await createScope(domain.id, accessToken, {
            key: 'test_scope_2',
            name: 'Test Scope 2',
            description: 'Another test scope'
        });


        // Create a protected resource to update
        const request = {
            name: generateValidProtectedResourceName(),
            type: "MCP_SERVER",
            resourceIdentifiers: ["https://update-test.com"],
            description: "Original description",
            features: [
                {
                    key: 'original_tool',
                    type: 'MCP_TOOL' as const,
                    description: 'Original tool description',
                    scopes: ['test_scope_1']
                } as NewMcpTool
            ]
        } as NewProtectedResource;


        const createdSecret = await createProtectedResource(domain.id, accessToken, request);
        expect(createdSecret).toBeDefined();
        expect(createdSecret.id).toBeDefined();


        // Wait for resource to be fully synced
        await waitFor(2000);


        // Fetch the full resource details
        createdResource = await getMcpServer(domain.id, accessToken, createdSecret.id);
        expect(createdResource).toBeDefined();
        expect(createdResource.resourceIdentifiers).toBeDefined();
    });


    it('Should update protected resource name and description', async () => {
        const updateRequest = {
            name: "Updated_Name_123",
            description: "Updated description",
            resourceIdentifiers: createdResource.resourceIdentifiers,
            features: createdResource.features.map(f => ({
                key: f.key,
                description: f.description,
                type: 'MCP_TOOL',
                scopes: isMcpToolFeature(f) ? f.scopes || [] : []
            }))
        } as UpdateProtectedResource;

        const updated = await updateProtectedResource(domain.id, accessToken, createdResource.id, updateRequest);

        expect(updated).toBeDefined();
        expect(updated.id).toEqual(createdResource.id);
        expect(updated.name).toEqual("Updated_Name_123");
        expect(updated.description).toEqual("Updated description");
        expect(updated.updatedAt).not.toEqual(createdResource.updatedAt);

        createdResource = updated;
    });


    it('Should update tool name and description', async () => {
        const updateRequest = {
            name: createdResource.name,
            description: createdResource.description,
            resourceIdentifiers: createdResource.resourceIdentifiers,
            features: [
                {
                    key: 'updated_tool_name',
                    type: 'MCP_TOOL',
                    description: 'Updated tool description',
                    scopes: ['test_scope_1']
                } as UpdateMcpTool
            ]
        } as UpdateProtectedResource;

        const updated = await updateProtectedResource(domain.id, accessToken, createdResource.id, updateRequest);

        expect(updated).toBeDefined();
        expect(updated.features).toHaveLength(1);
        expect(updated.features[0].key).toEqual('updated_tool_name');
        expect(updated.features[0].description).toEqual('Updated tool description');

        createdResource = updated;
    });

    it('Should update tool scopes', async () => {
        const updateRequest = {
            name: createdResource.name,
            description: createdResource.description,
            resourceIdentifiers: createdResource.resourceIdentifiers,
            features: [
                {
                    key: 'updated_tool_name',
                    type: 'MCP_TOOL',
                    description: 'Updated tool description',
                    scopes: ['test_scope_1', 'test_scope_2']
                } as UpdateMcpTool
            ]
        } as UpdateProtectedResource;

        const updated = await updateProtectedResource(domain.id, accessToken, createdResource.id, updateRequest);

        expect(updated).toBeDefined();
        expect(updated.features[0]).toBeDefined();
        const firstFeature = updated.features[0] as McpToolFeature;
        expect(firstFeature.type).toMatch(/^mcp_tool$/i);
        expect(firstFeature.scopes).toHaveLength(2);
        expect(firstFeature.scopes).toContain('test_scope_1');
        expect(firstFeature.scopes).toContain('test_scope_2');

        createdResource = updated;
    });

    it('Should add new tool to existing resource', async () => {
        const updateRequest = {
            name: createdResource.name,
            description: createdResource.description,
            resourceIdentifiers: createdResource.resourceIdentifiers,
            features: [
                {
                    key: 'updated_tool_name',
                    type: 'MCP_TOOL',
                    description: 'Updated tool description',
                    scopes: ['test_scope_1', 'test_scope_2']
                } as UpdateMcpTool,
                {
                    key: 'second_tool',
                    type: 'MCP_TOOL',
                    description: 'Second tool',
                    scopes: ['test_scope_1']
                } as UpdateMcpTool
            ]
        } as UpdateProtectedResource;

        const updated = await updateProtectedResource(domain.id, accessToken, createdResource.id, updateRequest);

        expect(updated).toBeDefined();
        expect(updated.features).toHaveLength(2);
        // Features are returned in alphabetical order by key
        expect(updated.features[0].key).toEqual('second_tool');
        expect(updated.features[1].key).toEqual('updated_tool_name');

        createdResource = updated;
    });

    it('Should remove tool from resource', async () => {
        const updateRequest = {
            name: createdResource.name,
            description: createdResource.description,
            resourceIdentifiers: createdResource.resourceIdentifiers,
            features: [
                {
                    key: 'updated_tool_name',
                    type: 'MCP_TOOL',
                    description: 'Updated tool description',
                    scopes: ['test_scope_1', 'test_scope_2']
                } as UpdateMcpTool
            ]
        } as UpdateProtectedResource;

        const updated = await updateProtectedResource(domain.id, accessToken, createdResource.id, updateRequest);

        expect(updated).toBeDefined();
        expect(updated.features).toHaveLength(1);
        expect(updated.features[0].key).toEqual('updated_tool_name');

        createdResource = updated;
    });

    it('Should update resource identifiers', async () => {
        const updateRequest = {
            name: createdResource.name,
            description: createdResource.description,
            resourceIdentifiers: ["https://new-uri.com"],
            features: createdResource.features.map(f => ({
                key: f.key,
                description: f.description,
                type: 'MCP_TOOL',
                scopes: isMcpToolFeature(f) ? f.scopes || [] : []
            }))
        } as UpdateProtectedResource;

        const updated = await updateProtectedResource(domain.id, accessToken, createdResource.id, updateRequest);

        expect(updated).toBeDefined();
        expect(updated.resourceIdentifiers).toHaveLength(1);
        expect(updated.resourceIdentifiers[0]).toEqual("https://new-uri.com");

        createdResource = updated;
    });

    it('Should fail when updating with duplicate feature keys', async () => {
        const updateRequest = {
            name: createdResource.name,
            description: createdResource.description,
            resourceIdentifiers: createdResource.resourceIdentifiers,
            features: [
                {
                    key: 'duplicate_tool',
                    type: 'MCP_TOOL',
                    description: 'Tool 1',
                    scopes: ['test_scope_1']
                } as UpdateMcpTool,
                {
                    key: 'duplicate_tool',
                    type: 'MCP_TOOL',
                    description: 'Tool 2',
                    scopes: ['test_scope_2']
                } as UpdateMcpTool
            ]
        } as UpdateProtectedResource;

        await updateProtectedResource(domain.id, accessToken, createdResource.id, updateRequest)
            .catch(err => expect(err.response.status).toEqual(400));
    });

    it('Should fail when updating with invalid scope', async () => {
        const updateRequest = {
            name: createdResource.name,
            description: createdResource.description,
            resourceIdentifiers: createdResource.resourceIdentifiers,
            features: [
                {
                    key: 'tool_with_invalid_scope',
                    type: 'MCP_TOOL',
                    description: 'Tool with invalid scope',
                    scopes: ['non_existent_scope']
                } as UpdateMcpTool
            ]
        } as UpdateProtectedResource;

        await updateProtectedResource(domain.id, accessToken, createdResource.id, updateRequest)
            .catch(err => expect(err.response.status).toEqual(400));
    });

    it('Should fail when updating non-existent resource', async () => {
        const updateRequest = {
            name: "Test",
            resourceIdentifiers: ["https://test.com"],
            features: []
        } as UpdateProtectedResource;

        await updateProtectedResource(domain.id, accessToken, 'non-existent-id', updateRequest)
            .catch(err => expect(err.response.status).toEqual(403)); // 403 because permission check happens before existence check
    });

    it('Should fail when updating with duplicate resource identifier', async () => {
        // Create another resource first
        const otherResource = await createProtectedResource(domain.id, accessToken, {
            name: generateValidProtectedResourceName(),
            type: "MCP_SERVER",
            resourceIdentifiers: ["https://other-resource.com"],
        } as NewProtectedResource);

        expect(otherResource).toBeDefined();

        // Try to update our resource to use the same identifier
        const updateRequest = {
            name: createdResource.name,
            description: createdResource.description,
            resourceIdentifiers: ["https://other-resource.com"],
            features: createdResource.features.map(f => ({
                key: f.key,
                description: f.description,
                type: 'MCP_TOOL',
                scopes: isMcpToolFeature(f) ? f.scopes || [] : []
            }))
        } as UpdateProtectedResource;

        await updateProtectedResource(domain.id, accessToken, createdResource.id, updateRequest)
            .catch(err => expect(err.response.status).toEqual(400));
    });

    it('Should update resource identifier to lowercase', async () => {
        const updateRequest = {
            name: createdResource.name,
            description: createdResource.description,
            resourceIdentifiers: ["https://MixedCaseUri.COM"],
            features: createdResource.features.map(f => ({
                key: f.key,
                description: f.description,
                type: 'MCP_TOOL',
                scopes: isMcpToolFeature(f) ? f.scopes || [] : []
            }))
        } as UpdateProtectedResource;

        const updated = await updateProtectedResource(domain.id, accessToken, createdResource.id, updateRequest);

        expect(updated).toBeDefined();
        expect(updated.resourceIdentifiers[0]).toEqual("https://mixedcaseuri.com");
    });

    it('Should preserve createdAt when updating existing tool (same key)', async () => {
        // Get the current tool's createdAt (key is 'updated_tool_name' from previous test)
        const before = await getMcpServer(domain.id, accessToken, createdResource.id);
        const originalCreatedAt = before.features?.[0]?.createdAt ?? null;

        await waitFor(1000); // Ensure time passes

        // Update the tool WITHOUT changing the key
        const updateRequest = {
            name: createdResource.name,
            description: createdResource.description,
            resourceIdentifiers: createdResource.resourceIdentifiers,
            features: [
                {
                    key: 'updated_tool_name',  // Keep the SAME key
                    type: 'MCP_TOOL',
                    description: 'Modified description again',
                    scopes: ['test_scope_1']
                } as UpdateMcpTool
            ]
        } as UpdateProtectedResource;

        const updated = await updateProtectedResource(domain.id, accessToken, createdResource.id, updateRequest);

        expect(updated).toBeDefined();
        expect(updated.features[0].createdAt).toBeDefined();
        if (originalCreatedAt) {
            expect(updated.features[0].createdAt).toEqual(originalCreatedAt);
        }
        expect(updated.features[0].updatedAt).toBeDefined();
    });

    it('Should set new createdAt when adding new tool via update', async () => {
        const updateRequest = {
            name: createdResource.name,
            description: createdResource.description,
            resourceIdentifiers: createdResource.resourceIdentifiers,
            features: [
                {
                    key: 'updated_tool_name',
                    type: 'MCP_TOOL',
                    description: 'Existing tool',
                    scopes: ['test_scope_1']
                } as UpdateMcpTool,
                {
                    key: 'brand_new_tool',
                    type: 'MCP_TOOL',
                    description: 'This is a new tool',
                    scopes: ['test_scope_2']
                } as UpdateMcpTool
            ]
        } as UpdateProtectedResource;

        const updated = await updateProtectedResource(domain.id, accessToken, createdResource.id, updateRequest);

        expect(updated).toBeDefined();
        expect(updated.features).toHaveLength(2);

        const newTool = updated.features.find(f => f.key === 'brand_new_tool');
        expect(newTool).toBeDefined();
        expect(newTool.createdAt).toBeDefined();
        // updatedAt should always be present for features (same as createdAt)
        expect(newTool.updatedAt).toBeDefined();
    });

    it('Should fail when updating with invalid tool key pattern', async () => {
        const updateRequest = {
            name: createdResource.name,
            description: createdResource.description,
            resourceIdentifiers: createdResource.resourceIdentifiers,
            features: [
                {
                    key: 'invalid key with spaces',
                    type: 'MCP_TOOL',
                    description: 'Invalid key',
                    scopes: []
                } as UpdateMcpTool
            ]
        } as UpdateProtectedResource;

        await updateProtectedResource(domain.id, accessToken, createdResource.id, updateRequest)
            .catch(err => expect(err.response.status).toEqual(400));
    });

    it('Should fail when updating with special characters in tool key', async () => {
        const updateRequest = {
            name: createdResource.name,
            description: createdResource.description,
            resourceIdentifiers: createdResource.resourceIdentifiers,
            features: [
                {
                    key: 'tool@#$%',
                    type: 'MCP_TOOL',
                    description: 'Invalid key',
                    scopes: []
                } as UpdateMcpTool
            ]
        } as UpdateProtectedResource;

        await updateProtectedResource(domain.id, accessToken, createdResource.id, updateRequest)
            .catch(err => expect(err.response.status).toEqual(400));
    });

    it('Should allow update with empty scopes array', async () => {
        const updateRequest = {
            name: createdResource.name,
            description: createdResource.description,
            resourceIdentifiers: createdResource.resourceIdentifiers,
            features: [
                {
                    key: 'tool_no_scopes',
                    type: 'MCP_TOOL',
                    description: 'Tool without scopes',
                    scopes: []
                } as UpdateMcpTool
            ]
        } as UpdateProtectedResource;

        const updated = await updateProtectedResource(domain.id, accessToken, createdResource.id, updateRequest);

        expect(updated).toBeDefined();
        expect(updated.features[0].key).toEqual('tool_no_scopes');
        expect(updated.features[0].type).toMatch(/^mcp_tool$/i);
        const firstFeature = updated.features[0] as McpToolFeature;
        expect(Array.isArray(firstFeature.scopes)).toBeTruthy();
        expect(firstFeature.scopes?.length).toEqual(0);
    });

    it('Should update resource with multiple tools', async () => {
        const updateRequest = {
            name: createdResource.name,
            description: createdResource.description,
            resourceIdentifiers: createdResource.resourceIdentifiers,
            features: [
                {
                    key: 'tool_1',
                    type: 'MCP_TOOL',
                    description: 'First tool',
                    scopes: ['test_scope_1']
                } as UpdateMcpTool,
                {
                    key: 'tool_2',
                    type: 'MCP_TOOL',
                    description: 'Second tool',
                    scopes: ['test_scope_2']
                } as UpdateMcpTool,
                {
                    key: 'tool_3',
                    type: 'MCP_TOOL',
                    description: 'Third tool',
                    scopes: ['test_scope_1', 'test_scope_2']
                } as UpdateMcpTool
            ]
        } as UpdateProtectedResource;

        const updated = await updateProtectedResource(domain.id, accessToken, createdResource.id, updateRequest);

        expect(updated).toBeDefined();
        expect(updated.features).toHaveLength(3);
        // Features are returned in alphabetical order by key
        expect(updated.features[0].key).toEqual('tool_1');
        expect(updated.features[1].key).toEqual('tool_2');
        expect(updated.features[2].key).toEqual('tool_3');
        const tool3Feature = updated.features[2] as McpToolFeature;
        expect(tool3Feature.type).toMatch(/^mcp_tool$/i);
        expect(tool3Feature.scopes).toHaveLength(2);
        expect(tool3Feature.scopes).toContain('test_scope_1');
        expect(tool3Feature.scopes).toContain('test_scope_2');
    });

    it('Should fail when missing required name field', async () => {
        const updateRequest = {
            description: createdResource.description,
            resourceIdentifiers: createdResource.resourceIdentifiers,
            features: []
        } as any;

        await updateProtectedResource(domain.id, accessToken, createdResource.id, updateRequest)
            .catch(err => expect(err.response.status).toEqual(400));
    });

    it('Should fail when missing required resourceIdentifiers field', async () => {
        const updateRequest = {
            name: createdResource.name,
            description: createdResource.description,
            features: []
        } as any;

        await updateProtectedResource(domain.id, accessToken, createdResource.id, updateRequest)
            .catch(err => expect(err.response.status).toEqual(400));
    });

    it('Should fail when resourceIdentifier is not a valid URL', async () => {
        const updateRequest = {
            name: createdResource.name,
            description: createdResource.description,
            resourceIdentifiers: ["not-a-url"],
            features: []
        } as UpdateProtectedResource;

        await updateProtectedResource(domain.id, accessToken, createdResource.id, updateRequest)
            .catch(err => expect(err.response.status).toEqual(400));
    });
});

describe('When patching protected resource', () => {
    let createdResource;

    beforeAll(async () => {
        // Create test scopes for validation
        await createScope(domain.id, accessToken, {
            key: 'patch_scope_1',
            name: 'Patch Test Scope 1',
            description: 'Test scope for protected resource patches'
        });

        await createScope(domain.id, accessToken, {
            key: 'patch_scope_2',
            name: 'Patch Test Scope 2',
            description: 'Another test scope for patches'
        });

        // Create a protected resource to patch
        const request = {
            name: generateValidProtectedResourceName(),
            type: "MCP_SERVER",
            resourceIdentifiers: ["https://patch-test.com"],
            description: "Original description",
            features: [
                {
                    key: 'original_tool',
                    type: 'MCP_TOOL' as const,
                    description: 'Original tool description',
                    scopes: ['patch_scope_1']
                } as NewMcpTool
            ]
        } as NewProtectedResource;

        const createdSecret = await createProtectedResource(domain.id, accessToken, request);
        expect(createdSecret).toBeDefined();
        expect(createdSecret.id).toBeDefined();

        // Wait for resource to be fully synced
        await waitFor(2000);

        // Fetch the full resource details
        createdResource = await getMcpServer(domain.id, accessToken, createdSecret.id);
        expect(createdResource).toBeDefined();
        expect(createdResource.resourceIdentifiers).toBeDefined();
    });

    it('Should patch protected resource with partial fields', async () => {
        const patchRequest = {
            name: "Patched_Name_456",
            description: "Patched Description"
        };

        const patched = await patchProtectedResource(domain.id, accessToken, createdResource.id, patchRequest);

        expect(patched).toBeDefined();
        expect(patched.id).toEqual(createdResource.id);
        expect(patched.name).toEqual("Patched_Name_456");
        expect(patched.description).toEqual("Patched Description");
        expect(patched.resourceIdentifiers).toEqual(createdResource.resourceIdentifiers);
        expect(patched.updatedAt).not.toEqual(createdResource.updatedAt);

        createdResource = patched;
    });

    it('Should patch protected resource resourceIdentifiers', async () => {
        const patchRequest = {
            resourceIdentifiers: ["https://patched.com", "https://MixedCase.COM"]
        };

        const patched = await patchProtectedResource(domain.id, accessToken, createdResource.id, patchRequest);

        expect(patched).toBeDefined();
        expect(patched.resourceIdentifiers).toContain("https://patched.com");
        expect(patched.resourceIdentifiers).toContain("https://mixedcase.com");
        expect(patched.updatedAt).not.toEqual(createdResource.updatedAt);

        createdResource = patched;
    });

    it('Should fail when patching with empty request body', async () => {
        const patchRequest = {};

        await patchProtectedResource(domain.id, accessToken, createdResource.id, patchRequest)
            .catch(err => expect(err.response.status).toEqual(400));
    });

    it('Should fail when resource identifier is not a valid URL', async () => {
        const patchRequest = {
            resourceIdentifiers: ["not-a-valid-url"]
        };

        await patchProtectedResource(domain.id, accessToken, createdResource.id, patchRequest)
            .catch(err => expect(err.response.status).toEqual(400));
    });

    it('Should fail when patching with duplicate resource identifier', async () => {
        // Create another resource first
        const otherResource = await createProtectedResource(domain.id, accessToken, {
            name: generateValidProtectedResourceName(),
            type: "MCP_SERVER",
            resourceIdentifiers: ["https://duplicate-test.com"],
        } as NewProtectedResource);

        expect(otherResource).toBeDefined();

        // Try to patch our resource to use the same identifier
        const patchRequest = {
            resourceIdentifiers: ["https://duplicate-test.com"]
        };

        await patchProtectedResource(domain.id, accessToken, createdResource.id, patchRequest)
            .catch(err => expect(err.response.status).toEqual(400));
    });

    it('Should fail when patching resource from another domain', async () => {
        // Create a resource in the test search domain
        const request = {
            name: generateValidProtectedResourceName(),
            type: 'MCP_SERVER',
            resourceIdentifiers: ["https://other-domain-patch.com"],
        } as NewProtectedResource;

        const created = await createProtectedResource(domainTestSearch.id, accessToken, request);
        expect(created).toBeDefined();

        // Try to patch from the wrong domain
        const patchRequest = {
            name: "Test"
        };

        await patchProtectedResource(domain.id, accessToken, created.id, patchRequest)
            .catch(err => {
                // Cross-domain access returns 403 (Forbidden) or 404 (Not Found)
                // Permission check happens before existence check, so 403 is returned when permission fails
                expect([403, 404]).toContain(err.response.status);
            });
    });

    it('Should patch features only (update existing tool)', async () => {
        // Store original createdAt for comparison
        const originalTool = createdResource.features?.find(f => f.key === 'original_tool');
        const originalCreatedAt = originalTool?.createdAt;
        expect(originalCreatedAt).toBeDefined();

        const patchRequest = {
            features: [
                {
                    key: 'original_tool',
                    type: 'MCP_TOOL' as const,
                    description: 'Updated tool description',
                    scopes: ['patch_scope_1', 'patch_scope_2']
                } as UpdateMcpTool
            ]
        };

        const patched = await patchProtectedResource(domain.id, accessToken, createdResource.id, patchRequest);

        expect(patched).toBeDefined();
        expect(patched.features).toBeDefined();
        expect(patched.features).toHaveLength(1);

        const updatedTool = patched.features[0] as McpToolFeature;
        expect(updatedTool.key).toEqual('original_tool');
        expect(updatedTool.description).toEqual('Updated tool description');
        expect(updatedTool.type).toMatch(/^mcp_tool$/i);
        expect(Array.isArray(updatedTool.scopes)).toBeTruthy();
        expect(updatedTool.scopes).toEqual(['patch_scope_1', 'patch_scope_2']);
        expect(updatedTool.createdAt).toEqual(originalCreatedAt); // Preserved
        expect(updatedTool.updatedAt).toBeDefined();
        expect(updatedTool.updatedAt).not.toEqual(originalTool?.updatedAt || originalCreatedAt);

        createdResource = patched;
    });

    it('Should patch features (add new tool)', async () => {
        const existingTool = createdResource.features?.find(f => f.key === 'original_tool');
        const existingToolCreatedAt = existingTool?.createdAt;
        expect(existingToolCreatedAt).toBeDefined();
        expect(existingTool).toBeDefined();

        const patchRequest = {
            features: [
                {
                    key: 'original_tool',
                    type: 'MCP_TOOL' as const,
                    description: existingTool.description || 'Updated tool description',
                    scopes: existingTool.scopes || ['patch_scope_1', 'patch_scope_2']
                } as UpdateMcpTool,
                {
                    key: 'new_tool',
                    type: 'MCP_TOOL' as const,
                    description: 'New tool description',
                    scopes: ['patch_scope_2']
                } as UpdateMcpTool
            ]
        };

        const patched = await patchProtectedResource(domain.id, accessToken, createdResource.id, patchRequest);

        expect(patched).toBeDefined();
        expect(patched.features).toBeDefined();
        expect(patched.features).toHaveLength(2);

        const originalToolAfter = patched.features.find(f => f.key === 'original_tool');
        const newTool = patched.features.find(f => f.key === 'new_tool');

        expect(originalToolAfter).toBeDefined();
        expect(originalToolAfter?.createdAt).toEqual(existingToolCreatedAt); // Preserved

        expect(newTool).toBeDefined();
        expect(newTool?.key).toEqual('new_tool');
        expect(newTool?.description).toEqual('New tool description');
        expect(newTool?.type).toMatch(/^mcp_tool$/i);
        const newToolFeature = newTool as McpToolFeature;
        expect(Array.isArray(newToolFeature.scopes)).toBeTruthy();
        expect(newToolFeature.scopes).toEqual(['patch_scope_2']);
        expect(newTool?.createdAt).toBeDefined();
        // New tool's createdAt should be recent (within last few seconds)
        const now = Date.now();
        const newToolCreatedAt = new Date(newTool?.createdAt || 0).getTime();
        expect(now - newToolCreatedAt).toBeLessThan(5000); // Within 5 seconds

        createdResource = patched;
    });

    it('Should patch features (remove tool)', async () => {
        // Ensure we have multiple tools - if previous test failed, add a tool first
        if (!createdResource.features || createdResource.features.length <= 1) {
            // Add a second tool if we don't have multiple tools
            const patchRequest = {
                features: [
                    ...(createdResource.features || []).map(f => ({
                        key: f.key,
                        type: 'MCP_TOOL',
                        description: f.description || '',
                        scopes: isMcpToolFeature(f) ? f.scopes || [] : []
                    })),
                    {
                        key: 'new_tool',
                        type: 'MCP_TOOL',
                        description: 'New tool description',
                        scopes: ['patch_scope_2']
                    }
                ]
            };
            createdResource = await patchProtectedResource(domain.id, accessToken, createdResource.id, patchRequest);
        }
        // Verify we have multiple tools
        expect(createdResource.features?.length).toBeGreaterThan(1);

        const toolToKeep = createdResource.features?.find(f => f.key === 'new_tool');
        expect(toolToKeep).toBeDefined();

        const patchRequest = {
            features: [
                {
                    key: 'new_tool',
                    type: 'MCP_TOOL' as const,
                    description: toolToKeep?.description || 'New tool description',
                    scopes: toolToKeep?.scopes || ['patch_scope_2']
                } as UpdateMcpTool
            ]
        };

        const patched = await patchProtectedResource(domain.id, accessToken, createdResource.id, patchRequest);

        expect(patched).toBeDefined();
        expect(patched.features).toBeDefined();
        expect(patched.features).toHaveLength(1);
        expect(patched.features[0].key).toEqual('new_tool');

        // Verify original_tool is removed
        const originalTool = patched.features.find(f => f.key === 'original_tool');
        expect(originalTool).toBeUndefined();

        createdResource = patched;
    });

    it('Should fail when patching with duplicate feature keys', async () => {
        const patchRequest = {
            features: [
                {
                    key: 'tool1',
                    type: 'MCP_TOOL' as const,
                    description: 'First tool'
                } as UpdateMcpTool,
                {
                    key: 'tool1', // Duplicate key
                    type: 'MCP_TOOL' as const,
                    description: 'Second tool with same key'
                } as UpdateMcpTool
            ]
        };

        await patchProtectedResource(domain.id, accessToken, createdResource.id, patchRequest)
            .catch(err => expect(err.response.status).toEqual(400));
    });

    it('Should fail when patching with invalid scope in feature', async () => {
        const patchRequest = {
            features: [
                {
                    key: 'tool_with_invalid_scope',
                    type: 'MCP_TOOL' as const,
                    description: 'Tool with invalid scope',
                    scopes: ['invalid_scope_that_does_not_exist']
                } as UpdateMcpTool
            ]
        };

        await patchProtectedResource(domain.id, accessToken, createdResource.id, patchRequest)
            .catch(err => {
                // Invalid scope should return 400 (Bad Request) - InvalidProtectedResourceException
                expect(err.response.status).toEqual(400);
            });
    });

    it('Should fail when patching with invalid feature key pattern', async () => {
        const patchRequest = {
            features: [
                {
                    key: 'invalid key with spaces', // Invalid: contains spaces
                    type: 'MCP_TOOL' as const,
                    description: 'Tool with invalid key'
                } as UpdateMcpTool
            ]
        };

        await patchProtectedResource(domain.id, accessToken, createdResource.id, patchRequest)
            .catch(err => expect(err.response.status).toEqual(400));
    });
});

describe('When deleting protected resource', () => {
    it('Should delete an existing MCP server and remove from list', async () => {
        const request = {
            name: generateValidProtectedResourceName(),
            type: 'MCP_SERVER',
            resourceIdentifiers: ["https://delete-me.com"],
        } as NewProtectedResource;

        const created = await createProtectedResource(domain.id, accessToken, request);
        expect(created).toBeDefined();

        // Delete the resource (management API is synchronous)
        await deleteProtectedResource(domain.id, accessToken, created.id, 'MCP_SERVER');

        // Verify it no longer appears in list (poll until removed)
        await waitForProtectedResourceRemovedFromList(domain.id, accessToken, created.id);
        const page = await getMcpServers(domain.id, accessToken, 100, 0);
        const exists = page.data.some(r => r.id === created.id);
        expect(exists).toBeFalsy();
    });

    it('Protected Resource must not be deleted when resource belongs to another domain', async () => {
        // Create a resource in the test search domain
        const request = {
            name: generateValidProtectedResourceName(),
            type: 'MCP_SERVER',
            resourceIdentifiers: ["https://other-domain.com"],
        } as NewProtectedResource;

        const created = await createProtectedResource(domainTestSearch.id, accessToken, request);
        expect(created).toBeDefined();

        // Try to delete from the wrong domain (no wait needed for management API operations)
        await deleteProtectedResource(domain.id, accessToken, created.id, 'MCP_SERVER')
            .catch(err => {
                // Cross-domain access returns 403 (Forbidden) or 404 (Not Found)
                // Permission check happens before existence check, so 403 is returned when permission fails
                expect([403, 404]).toContain(err.response.status);
            });
    });

    it('Should delete protected resource with client secrets and features', async () => {
        const request = {
            name: generateValidProtectedResourceName(),
            type: 'MCP_SERVER',
            resourceIdentifiers: ["https://with-children.com"],
            features: [
                {
                    key: 'test_tool_1',
                    type: 'MCP_TOOL',
                    description: 'Test tool'
                }
            ]
        } as NewProtectedResource;

        const created = await createProtectedResource(domain.id, accessToken, request);
        expect(created).toBeDefined();
        expect(created.clientSecret).toBeDefined();

        // Verify features exist
        const fetched = await getMcpServer(domain.id, accessToken, created.id);
        expect(fetched).toBeDefined();
        expect(fetched.features).toBeDefined();
        expect(fetched.features.length).toBeGreaterThan(0);

        // Delete the resource (this should cascade delete client secrets and features)
        await deleteProtectedResource(domain.id, accessToken, created.id, 'MCP_SERVER');

        // Verify resource is removed from list (poll until removed)
        await waitForProtectedResourceRemovedFromList(domain.id, accessToken, created.id);
        const page = await getMcpServers(domain.id, accessToken, 100, 0);
        const exists = page.data.some(r => r.id === created.id);
        expect(exists).toBeFalsy();

        // Verify we cannot get it by ID (resource is completely deleted)
        await getMcpServer(domain.id, accessToken, created.id)
            .catch(err => expect(err.response.status).toBe(404));
    });

    it('Should delete protected resources when domain is deleted', async () => {
        const testDomain = await setupDomainForTest(uniqueName('domain-delete-cascade', true), { accessToken, waitForStart: true });

        // Create protected resources in the test domain
        const resources = [];
        for (let i = 0; i < 3; i++) {
            const request = {
                name: generateValidProtectedResourceName(),
                type: 'MCP_SERVER',
                resourceIdentifiers: [`https://cascade-test-${i}.com`],
                features: [
                    {
                        key: `tool_${i}`,
                        type: 'MCP_TOOL',
                        description: `Test tool ${i}`
                    }
                ]
            } as NewProtectedResource;
            const created = await createProtectedResource(testDomain.domain.id, accessToken, request);
            resources.push(created);
        }

        // Verify resources exist
        let page = await getMcpServers(testDomain.domain.id, accessToken, 100, 0);
        expect(page.data.length).toBeGreaterThanOrEqual(3);
        for (const resource of resources) {
            expect(page.data.some(r => r.id === resource.id)).toBeTruthy();
        }

        // Delete the domain (should cascade delete protected resources)
        await deleteDomain(testDomain.domain.id, accessToken);

        // Verify protected resources no longer exist by trying to list them
        // Since the domain is deleted, checkDomainExists will throw DomainNotFoundException (404)
        await getMcpServers(testDomain.domain.id, accessToken, 100, 0)
            .catch(err => {
                expect(err.response.status).toBe(404);
            });
    });

    it('Deleted protected resource cannot introspect token', async () => {
        // Create an application to mint a token
        const application = await createApplication(domain.id, accessToken, {
            name: uniqueName('introspect-app'),
            type: 'SERVICE',
            clientId: 'introspect-app',
            clientSecret: 'introspect-app',
        }).then((app) =>
            updateApplication(
                domain.id,
                accessToken,
                { settings: { oauth: { grantTypes: ['client_credentials'] } } },
                app.id,
            ).then((updatedApp) => {
                updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
                updatedApp.settings.oauth.clientId = app.settings.oauth.clientId;
                return updatedApp;
            }),
        );

        // Poll token endpoint until application is synced to gateway
        const token = await retryUntil(
            () => performPost(openIdConfiguration.token_endpoint, '', 'grant_type=client_credentials', {
                'Content-type': 'application/x-www-form-urlencoded',
                Authorization: 'Basic ' + applicationBase64Token(application),
            }),
            (resp: any) => resp.status === 200,
            {
                timeoutMillis: 10000,
                intervalMillis: 250,
                onDone: () => console.log('application synced, token obtained'),
            }
        ).then((resp: any) => resp.body.access_token);
        expect(token).toBeDefined();

        // Create Protected Resource and wait for it to be synced to gateway
        const created = await createProtectedResource(domain.id, accessToken, {
            name: uniqueName('pr-introspect'),
            type: 'MCP_SERVER',
            resourceIdentifiers: ['https://deleted-after.com'],
        } as NewProtectedResource);
        expect(created).toBeDefined();

        // Poll introspection until resource is synced to gateway (proves it exists and is usable)
        await retryUntil(
            () => performPost(openIdConfiguration.introspection_endpoint, '', `token=${token}`, {
                'Content-type': 'application/x-www-form-urlencoded',
                Authorization: 'Basic ' + getBase64BasicAuth(created.clientId, created.clientSecret),
            }),
            (resp: any) => resp.status === 200,
            {
                timeoutMillis: 10000,
                intervalMillis: 250,
                onDone: () => console.log('protected resource synced to gateway'),
            }
        );

        // Delete the resource
        await deleteProtectedResource(domain.id, accessToken, created.id, 'MCP_SERVER');
        await waitForProtectedResourceRemovedFromList(domain.id, accessToken, created.id);

        // Poll introspection until deletion is synced (should fail with 401)
        await retryUntil(
            () => performPost(openIdConfiguration.introspection_endpoint, '', `token=${token}`, {
                'Content-type': 'application/x-www-form-urlencoded',
                Authorization: 'Basic ' + getBase64BasicAuth(created.clientId, created.clientSecret),
            }).catch((err: any) => ({ status: err.response?.status || 401 })),
            (resp: any) => resp.status === 401,
            {
                timeoutMillis: 10000,
                intervalMillis: 250,
                onDone: () => console.log('deletion synced, introspection correctly fails'),
            }
        );
    });
});