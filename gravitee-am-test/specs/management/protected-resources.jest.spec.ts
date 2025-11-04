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
import { Domain } from '@management-models/Domain';
import { afterAll, beforeAll, expect, jest } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { deleteDomain, setupDomainForTest, waitFor, waitForDomainSync } from '@management-commands/domain-management-commands';
import { uniqueName } from '@utils-commands/misc';
import faker from 'faker';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import {
  createProtectedResource,
  getMcpServer,
  getMcpServers,
  updateProtectedResource,
} from '@management-commands/protected-resources-management-commands';
import { NewProtectedResource } from '@management-models/NewProtectedResource';
import { UpdateProtectedResource } from '@management-models/UpdateProtectedResource';
import { UpdateMcpTool } from '@management-models/UpdateMcpTool';
import { createScope } from '@management-commands/scope-management-commands';
import {  performPost } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token, getBase64BasicAuth } from '@gateway-commands/utils';

global.fetch = fetch;
jest.setTimeout(200000);

let accessToken: string;
let domain: Domain;
let domainTestSearch: Domain;
let openIdConfiguration: any;

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  var domainResult = await setupDomainForTest(uniqueName('domain-protected-resources'), { accessToken, waitForStart: true });
  domain = domainResult.domain;
  openIdConfiguration = domainResult.oidcConfig;
  domainTestSearch = await setupDomainForTest(uniqueName('domain-protected-resources-search'), { accessToken, waitForStart: true }).then(
    (it) => it.domain,
  );
});

afterAll(async () => {
  if (domain && domain.id) {
    await deleteDomain(domain.id, accessToken);
  }
  if (domainTestSearch && domainTestSearch.id) {
    await deleteDomain(domainTestSearch.id, accessToken);
  }
});

describe('When creating protected resource', () => {
  it('New Protected Resource should be created', async () => {
    const request = {
      name: faker.commerce.productName(),
      type: 'MCP_SERVER',
      resourceIdentifiers: ['https://something.com', 'https://something2.com'],
      features: [
        {
          key: 'get_weather',
          type: 'MCP_TOOL',
          description: 'Description',
        },
        {
          key: 'get_something',
          type: 'MCP_TOOL',
          description: 'Description 2',
        },
      ],
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
      name: faker.commerce.productName(),
      clientId: 'client-id',
      type: 'MCP_SERVER',
      resourceIdentifiers: ['https://something3.com'],
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
      name: faker.commerce.productName(),
      clientId: 'client-id2',
      clientSecret: 'secret',
      type: 'MCP_SERVER',
      resourceIdentifiers: ['https://something4.com'],
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
      name: faker.commerce.productName(),
      clientId: 'client-id3',
      clientSecret: 'secret',
      type: 'MCP_SERVER',
      resourceIdentifiers: ['https://something5.com'],
    } as NewProtectedResource;

    const createdResource = await createProtectedResource(domain.id, accessToken, request);
    expect(createdResource).toBeDefined();
    expect(createdResource.id).toBeDefined();
    expect(createdResource.name).toEqual(request.name);
    expect(createdResource.clientSecret).toEqual(request.clientSecret);
    expect(createdResource.clientId).toEqual(request.clientId);

    await createProtectedResource(domain.id, accessToken, request).catch((err) => expect(err.response.status).toEqual(400));
  });

  it('Protected Resource must not be created with same clientId in application', async () => {
    const appRequest = {
      name: faker.commerce.productName(),
      type: 'service',
      description: faker.lorem.paragraph(),
      clientId: 'client-id4',
    };

    await createApplication(domain.id, accessToken, appRequest);

    const request = {
      name: faker.commerce.productName(),
      clientId: appRequest.clientId,
      clientSecret: 'secret',
      type: 'MCP_SERVER',
      resourceIdentifiers: ['https://something6.com'],
    } as NewProtectedResource;

    await createProtectedResource(domain.id, accessToken, request).catch((err) => expect(err.response.status).toEqual(400));
  });

  it('Protected Resource must not be created without type', async () => {
    const request = {
      name: faker.commerce.productName(),
      resourceIdentifiers: ['https://something7.com', 'https://something8.com'],
    } as NewProtectedResource;

    await createProtectedResource(domain.id, accessToken, request).catch((err) => expect(err.response.status).toEqual(400));
  });

  it('Protected Resource must not be created with wrong type', async () => {
    const request = {
      name: faker.commerce.productName(),
      type: 'MCP_SERVERR',
      resourceIdentifiers: ['https://something9.com', 'https://something10.com'],
    } as NewProtectedResource;

    await createProtectedResource(domain.id, accessToken, request).catch((err) => expect(err.response.status).toEqual(400));
  });

  it('Protected Resource must not be created with wrong resource identifier', async () => {
    const request = {
      name: faker.commerce.productName(),
      type: 'MCP_SERVER',
      resourceIdentifiers: ['something', 'https://something11.com'],
    } as NewProtectedResource;

    await createProtectedResource(domain.id, accessToken, request).catch((err) => expect(err.response.status).toEqual(400));
  });

  it('Protected Resource must not be created if resourceIdentifier already exists', async () => {
    const request = {
      name: faker.commerce.productName(),
      type: 'MCP_SERVER',
      resourceIdentifiers: ['https://something13.com'],
    } as NewProtectedResource;

    const first = await createProtectedResource(domain.id, accessToken, request);
    expect(first.id).toBeDefined();

    await createProtectedResource(domain.id, accessToken, request).catch((err) => expect(err.response.status).toEqual(400));
  });
  it('Protected Resource resource identifier http(s)://localhost is correct', async () => {
    const httpsRequest = {
      name: faker.commerce.productName(),
      type: 'MCP_SERVER',
      resourceIdentifiers: ['https://localhost.com'],
    } as NewProtectedResource;

    const created1 = await createProtectedResource(domain.id, accessToken, httpsRequest);
    expect(created1.id).toBeDefined();

    const httpRequest = {
      name: faker.commerce.productName(),
      type: 'MCP_SERVER',
      resourceIdentifiers: ['http://localhost.com'],
    } as NewProtectedResource;

    const created2 = await createProtectedResource(domain.id, accessToken, httpRequest);
    expect(created2.id).toBeDefined();
  });

  it('Protected Resource resource identifier and lowercased', async () => {
    const badRequest = {
      name: faker.commerce.productName(),
      type: 'MCP_SERVER',
      resourceIdentifiers: [' https://toTrimDomain.com   '],
    } as NewProtectedResource;

    createProtectedResource(domain.id, accessToken, badRequest).catch((err) => expect(err.response.status).toEqual(400));

    const correctRequest = {
      name: faker.commerce.productName(),
      type: 'MCP_SERVER',
      resourceIdentifiers: ['https://toLowerCaseDomain.com'],
    } as NewProtectedResource;

    const created = await createProtectedResource(domain.id, accessToken, correctRequest);
    expect(created.id).toBeDefined();

    const anotherCorrectRequest = {
      name: faker.commerce.productName(),
      type: 'MCP_SERVER',
      resourceIdentifiers: ['https://tolowercasedomain.com'],
    } as NewProtectedResource;

    createProtectedResource(domain.id, accessToken, anotherCorrectRequest).catch((err) => expect(err.response.status).toEqual(400));
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
      name: faker.commerce.productName(),
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
      name: faker.commerce.productName(),
      type: 'MCP_SERVER',
      resourceIdentifiers: ['https://unkownToolName.com'],
      features: [
        {
          key: 'key',
          type: 'ABC',
        },
      ],
    } as NewProtectedResource;

    await createProtectedResource(domain.id, accessToken, request).catch((err) => expect(err.response.status).toEqual(400));
  });

  it('Protected Resource must not be created when feature type is missing', async () => {
    const request = {
      name: faker.commerce.productName(),
      type: 'MCP_SERVER',
      resourceIdentifiers: ['https://unkownToolName.com'],
      features: [
        {
          key: 'key',
        },
      ],
    } as NewProtectedResource;

    await createProtectedResource(domain.id, accessToken, request).catch((err) => expect(err.response.status).toEqual(400));
  });

  it('Protected Resource must not be created when keys are duplicated', async () => {
    const request = {
      name: faker.commerce.productName(),
      type: 'MCP_SERVER',
      resourceIdentifiers: ['https://unkownToolName.com'],
      features: [
        {
          key: 'key',
          type: 'MCP_TOOL',
        },
        {
          key: 'key',
          type: 'MCP_TOOL',
        },
      ],
    } as NewProtectedResource;

    await createProtectedResource(domain.id, accessToken, request).catch((err) => expect(err.response.status).toEqual(400));
  });

  it('Protected Resource must not be created when key is missing', async () => {
    const request = {
      name: faker.commerce.productName(),
      type: 'MCP_SERVER',
      resourceIdentifiers: ['https://unkownToolName.com'],
      features: [
        {
          key: 'key',
          type: 'MCP_TOOL',
        },
        {
          type: 'MCP_TOOL',
        },
      ],
    } as NewProtectedResource;

    await createProtectedResource(domain.id, accessToken, request).catch((err) => expect(err.response.status).toEqual(400));
  });

  it('Protected Resource must not be created when key doesnt follow regex pattern', async () => {
    const request = {
      name: faker.commerce.productName(),
      type: 'MCP_SERVER',
      resourceIdentifiers: ['https://unkownToolName.com'],
      features: [
        {
          key: 'key$',
          type: 'MCP_TOOL',
        },
        {
          type: 'MCP_TOOL',
        },
      ],
    } as NewProtectedResource;

    await createProtectedResource(domain.id, accessToken, request).catch((err) => expect(err.response.status).toEqual(400));

    const request2 = {
      name: faker.commerce.productName(),
      type: 'MCP_SERVER',
      resourceIdentifiers: ['https://unkownToolName.com'],
      features: [
        {
          key: 'key asdsad',
          type: 'MCP_TOOL',
        },
        {
          type: 'MCP_TOOL',
        },
      ],
    } as NewProtectedResource;

    await createProtectedResource(domain.id, accessToken, request2).catch((err) => expect(err.response.status).toEqual(400));
  });
});

describe('When admin created bunch of Protected Resources', () => {
  beforeAll(async () => {
    for (let i = 0; i < 100; i++) {
      const request = {
        name: faker.commerce.productName(),
        type: 'MCP_SERVER',
        resourceIdentifiers: [`https://abc${i}.com`, `https://abc${i}a${i}.com`],
        features: [
          {
            key: 'key',
            type: 'MCP_TOOL',
          },
        ],
      } as NewProtectedResource;
      await createProtectedResource(domainTestSearch.id, accessToken, request);
    }
  });

  it('Protected Resource page must not contain secret', async () => {
    const page = await getMcpServers(domainTestSearch.id, accessToken, 100, 0);
    const secretNotPresent = page.data.every((data) => data['secret'] == undefined);
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
    let testScope1;
    let testScope2;

    beforeAll(async () => {
        // Create test scopes for validation
        testScope1 = await createScope(domain.id, accessToken, {
            key: 'test_scope_1',
            name: 'Test Scope 1',
            description: 'Test scope for protected resource updates',
        });

        testScope2 = await createScope(domain.id, accessToken, {
            key: 'test_scope_2',
            name: 'Test Scope 2',
            description: 'Another test scope',
        });

        // Create a protected resource to update
        const request = {
            name: faker.commerce.productName(),
            type: 'MCP_SERVER',
            resourceIdentifiers: ['https://update-test.com'],
            description: 'Original description',
            features: [
                {
                    key: 'original_tool',
                    type: 'MCP_TOOL',
                    description: 'Original tool description',
                },
            ],
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
            name: 'Updated Name',
            description: 'Updated description',
            resourceIdentifiers: createdResource.resourceIdentifiers,
            features: createdResource.features.map((f) => ({
                key: f.key,
                description: f.description,
                type: 'MCP_TOOL',
                scopes: (f as any).scopes || [],
            })),
        } as UpdateProtectedResource;

        const updated = await updateProtectedResource(domain.id, accessToken, createdResource.id, updateRequest);

        expect(updated).toBeDefined();
        expect(updated.id).toEqual(createdResource.id);
        expect(updated.name).toEqual('Updated Name');
        expect(updated.description).toEqual('Updated description');
        expect(updated.updatedAt).not.toEqual(createdResource.updatedAt);

        // Update local reference for next tests
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
                    scopes: ['test_scope_1'],
                } as UpdateMcpTool,
            ],
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
                    scopes: ['test_scope_1', 'test_scope_2'],
                } as UpdateMcpTool,
            ],
        } as UpdateProtectedResource;

        const updated = await updateProtectedResource(domain.id, accessToken, createdResource.id, updateRequest);

        expect(updated).toBeDefined();
        const scopes = updated.features[0]['scopes'];
        if (scopes) {
            expect(scopes).toHaveLength(2);
            expect(scopes).toContain('test_scope_1');
            expect(scopes).toContain('test_scope_2');
        }

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
                    scopes: ['test_scope_1', 'test_scope_2'],
                } as UpdateMcpTool,
                {
                    key: 'second_tool',
                    type: 'MCP_TOOL',
                    description: 'Second tool',
                    scopes: ['test_scope_1'],
                } as UpdateMcpTool,
            ],
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
                    scopes: ['test_scope_1', 'test_scope_2'],
                } as UpdateMcpTool,
            ],
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
            resourceIdentifiers: ['https://new-uri.com'],
            features: createdResource.features.map((f) => ({
                key: f.key,
                description: f.description,
                type: 'MCP_TOOL',
                scopes: (f as any).scopes || [],
            })),
        } as UpdateProtectedResource;

        const updated = await updateProtectedResource(domain.id, accessToken, createdResource.id, updateRequest);

        expect(updated).toBeDefined();
        expect(updated.resourceIdentifiers).toHaveLength(1);
        expect(updated.resourceIdentifiers[0]).toEqual('https://new-uri.com');

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
                    scopes: ['test_scope_1'],
                } as UpdateMcpTool,
                {
                    key: 'duplicate_tool',
                    type: 'MCP_TOOL',
                    description: 'Tool 2',
                    scopes: ['test_scope_2'],
                } as UpdateMcpTool,
            ],
        } as UpdateProtectedResource;

        await updateProtectedResource(domain.id, accessToken, createdResource.id, updateRequest).catch((err) =>
            expect(err.response.status).toEqual(400),
        );
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
                    scopes: ['non_existent_scope'],
                } as UpdateMcpTool,
            ],
        } as UpdateProtectedResource;

        await updateProtectedResource(domain.id, accessToken, createdResource.id, updateRequest).catch((err) =>
            expect(err.response.status).toEqual(500),
        );
    });

    it('Should fail when updating non-existent resource', async () => {
        const updateRequest = {
            name: 'Test',
            resourceIdentifiers: ['https://test.com'],
            features: [],
        } as UpdateProtectedResource;

        await updateProtectedResource(domain.id, accessToken, 'non-existent-id', updateRequest).catch((err) =>
            expect(err.response.status).toEqual(403),
        ); // 403 because permission check happens before existence check
    });

    it('Should fail when updating with duplicate resource identifier', async () => {
        // Create another resource first
        const otherResource = await createProtectedResource(domain.id, accessToken, {
            name: faker.commerce.productName(),
            type: 'MCP_SERVER',
            resourceIdentifiers: ['https://other-resource.com'],
        } as NewProtectedResource);

        expect(otherResource).toBeDefined();

        // Try to update our resource to use the same identifier
        const updateRequest = {
            name: createdResource.name,
            description: createdResource.description,
            resourceIdentifiers: ['https://other-resource.com'],
            features: createdResource.features.map((f) => ({
                key: f.key,
                description: f.description,
                type: 'MCP_TOOL',
                scopes: (f as any).scopes || [],
            })),
        } as UpdateProtectedResource;

        await updateProtectedResource(domain.id, accessToken, createdResource.id, updateRequest).catch((err) =>
            expect(err.response.status).toEqual(400),
        );
    });

    it('Should update resource identifier to lowercase', async () => {
        const updateRequest = {
            name: createdResource.name,
            description: createdResource.description,
            resourceIdentifiers: ['https://MixedCaseUri.COM'],
            features: createdResource.features.map((f) => ({
                key: f.key,
                description: f.description,
                type: 'MCP_TOOL',
                scopes: (f as any).scopes || [],
            })),
        } as UpdateProtectedResource;

        const updated = await updateProtectedResource(domain.id, accessToken, createdResource.id, updateRequest);

        expect(updated).toBeDefined();
        expect(updated.resourceIdentifiers[0]).toEqual('https://mixedcaseuri.com');
    });

    it('Should preserve createdAt when updating existing tool (same key)', async () => {
        // Get the current tool's createdAt (key is 'updated_tool_name' from previous test)
        const before = await getMcpServer(domain.id, accessToken, createdResource.id);
        const originalCreatedAt = before.features && before.features[0] ? before.features[0].createdAt : null;

        await waitFor(1000); // Ensure time passes

        // Update the tool WITHOUT changing the key
        const updateRequest = {
            name: createdResource.name,
            description: createdResource.description,
            resourceIdentifiers: createdResource.resourceIdentifiers,
            features: [
                {
                    key: 'updated_tool_name', // Keep the SAME key
                    type: 'MCP_TOOL',
                    description: 'Modified description again',
                    scopes: ['test_scope_1'],
                } as UpdateMcpTool,
            ],
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
                    scopes: ['test_scope_1'],
                } as UpdateMcpTool,
                {
                    key: 'brand_new_tool',
                    type: 'MCP_TOOL',
                    description: 'This is a new tool',
                    scopes: ['test_scope_2'],
                } as UpdateMcpTool,
            ],
        } as UpdateProtectedResource;

        const updated = await updateProtectedResource(domain.id, accessToken, createdResource.id, updateRequest);

        expect(updated).toBeDefined();
        expect(updated.features).toHaveLength(2);

        const newTool = updated.features.find((f) => f.key === 'brand_new_tool');
        expect(newTool).toBeDefined();
        expect(newTool.createdAt).toBeDefined();
        // updatedAt might not be in the API response model, so make it optional
        if (newTool.updatedAt !== undefined) {
            expect(newTool.updatedAt).toBeDefined();
        }
    });

    it('Should fail when updating with invalid tool key formats', async () => {
        // Test with spaces in key
        const requestWithSpaces = {
            name: createdResource.name,
            description: createdResource.description,
            resourceIdentifiers: createdResource.resourceIdentifiers,
            features: [
                {
                    key: 'invalid key with spaces',
                    type: 'MCP_TOOL',
                    description: 'Invalid key',
                    scopes: [],
                } as UpdateMcpTool,
            ],
        } as UpdateProtectedResource;

        await updateProtectedResource(domain.id, accessToken, createdResource.id, requestWithSpaces).catch((err) =>
            expect(err.response.status).toEqual(400),
        );
    });
    it('Should fail when updating with special characters in tool key', async () => {
        // Test with special characters in key
        const requestWithSpecialChars = {
            name: createdResource.name,
            description: createdResource.description,
            resourceIdentifiers: createdResource.resourceIdentifiers,
            features: [
                {
                    key: 'tool@#$%',
                    type: 'MCP_TOOL',
                    description: 'Invalid key',
                    scopes: [],
                } as UpdateMcpTool,
            ],
        } as UpdateProtectedResource;

        await updateProtectedResource(domain.id, accessToken, createdResource.id, requestWithSpecialChars).catch((err) =>
            expect(err.response.status).toEqual(400),
        );
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
                    scopes: [],
                } as UpdateMcpTool,
            ],
        } as UpdateProtectedResource;

        const updated = await updateProtectedResource(domain.id, accessToken, createdResource.id, updateRequest);

        expect(updated).toBeDefined();
        expect(updated.features[0].key).toEqual('tool_no_scopes');
        // Scopes might be undefined if empty, so check for both cases
        const scopes = updated.features[0]['scopes'];
        expect(scopes === undefined || scopes.length === 0).toBeTruthy();
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
                    scopes: ['test_scope_1'],
                } as UpdateMcpTool,
                {
                    key: 'tool_2',
                    type: 'MCP_TOOL',
                    description: 'Second tool',
                    scopes: ['test_scope_2'],
                } as UpdateMcpTool,
                {
                    key: 'tool_3',
                    type: 'MCP_TOOL',
                    description: 'Third tool',
                    scopes: ['test_scope_1', 'test_scope_2'],
                } as UpdateMcpTool,
            ],
        } as UpdateProtectedResource;

        const updated = await updateProtectedResource(domain.id, accessToken, createdResource.id, updateRequest);

        expect(updated).toBeDefined();
        expect(updated.features).toHaveLength(3);
        // Features are returned in alphabetical order by key
        expect(updated.features[0].key).toEqual('tool_1');
        expect(updated.features[1].key).toEqual('tool_2');
        expect(updated.features[2].key).toEqual('tool_3');
        const tool3Scopes = updated.features[2]['scopes'];
        if (tool3Scopes) {
            expect(tool3Scopes).toHaveLength(2);
        }
    });

    it('Should validate required name field', async () => {
        const missingName = {
            description: createdResource.description,
            resourceIdentifiers: createdResource.resourceIdentifiers,
            features: [],
        } as any;

        await updateProtectedResource(domain.id, accessToken, createdResource.id, missingName).catch((err) =>
            expect(err.response.status).toEqual(400),
        );
    });

    it('Should validate required resourceIdentifiers field', async () => {
        const missingIdentifiers = {
            name: createdResource.name,
            description: createdResource.description,
            features: [],
        } as any;

        await updateProtectedResource(domain.id, accessToken, createdResource.id, missingIdentifiers).catch((err) =>
            expect(err.response.status).toEqual(400),
        );
    });

    it('Should fail when resourceIdentifier is not a valid URL', async () => {
        const updateRequest = {
            name: createdResource.name,
            description: createdResource.description,
            resourceIdentifiers: ['not-a-url'],
            features: [],
        } as UpdateProtectedResource;

        await updateProtectedResource(domain.id, accessToken, createdResource.id, updateRequest).catch((err) =>
            expect(err.response.status).toEqual(400),
        );
    });

    describe('When deleting tools from protected resource', () => {
        let resourceWithTools;
        let testScope1;
        let testScope2;

        beforeAll(async () => {
            // Create test scopes
            testScope1 = await createScope(domain.id, accessToken, {
                key: 'delete_test_scope_1',
                name: 'Delete Test Scope 1',
                description: 'Scope for delete tests'
            });

            testScope2 = await createScope(domain.id, accessToken, {
                key: 'delete_test_scope_2',
                name: 'Delete Test Scope 2',
                description: 'Another scope for delete tests'
            });

            // Create a protected resource with multiple tools
            const request = {
                name: faker.commerce.productName(),
                type: "MCP_SERVER",
                resourceIdentifiers: ["https://delete-test.com"],
                description: "Resource for delete testing",
                features: [
                    {
                        key: 'tool_to_keep',
                        type: 'MCP_TOOL',
                        description: 'This tool will be kept',
                        scopes: ['delete_test_scope_1']
                    } as any,
                    {
                        key: 'tool_to_delete_1',
                        type: 'MCP_TOOL',
                        description: 'This tool will be deleted',
                        scopes: ['delete_test_scope_2']
                    } as any,
                    {
                        key: 'tool_to_delete_2',
                        type: 'MCP_TOOL',
                        description: 'This tool will also be deleted',
                        scopes: ['delete_test_scope_1', 'delete_test_scope_2']
                    } as any
                ]
            } as NewProtectedResource;

            const created = await createProtectedResource(domain.id, accessToken, request);
            expect(created).toBeDefined();
            expect(created.id).toBeDefined();

            await waitFor(2000);

            resourceWithTools = await getMcpServer(domain.id, accessToken, created.id);
            expect(resourceWithTools).toBeDefined();
            expect(resourceWithTools.features).toHaveLength(3);
        });

        it('Should delete a single tool by filtering it out', async () => {
            // Delete 'tool_to_delete_1' by sending update without it
            const updateRequest = {
                name: resourceWithTools.name,
                description: resourceWithTools.description,
                resourceIdentifiers: resourceWithTools.resourceIdentifiers,
                features: resourceWithTools.features
                    .filter(f => f.key !== 'tool_to_delete_1')
                    .map(f => ({
                        key: f.key,
                        description: f.description,
                        type: 'MCP_TOOL',
                        scopes: (f as any).scopes || []
                    }))
            } as UpdateProtectedResource;

            const updated = await updateProtectedResource(domain.id, accessToken, resourceWithTools.id, updateRequest);

            expect(updated).toBeDefined();
            expect(updated.features).toHaveLength(2);
            expect(updated.features.find(f => f.key === 'tool_to_delete_1')).toBeUndefined();
            expect(updated.features.find(f => f.key === 'tool_to_keep')).toBeDefined();
            expect(updated.features.find(f => f.key === 'tool_to_delete_2')).toBeDefined();
            expect(updated.updatedAt).not.toEqual(resourceWithTools.updatedAt);
        });

        it('Should delete multiple tools at once', async () => {
            // Delete both remaining deletable tools, keeping only 'tool_to_keep'
            const updateRequest = {
                name: resourceWithTools.name,
                description: resourceWithTools.description,
                resourceIdentifiers: resourceWithTools.resourceIdentifiers,
                features: resourceWithTools.features
                    .filter(f => f.key === 'tool_to_keep')
                    .map(f => ({
                        key: f.key,
                        description: f.description,
                        type: 'MCP_TOOL',
                        scopes: (f as any).scopes || []
                    }))
            } as UpdateProtectedResource;

            const updated = await updateProtectedResource(domain.id, accessToken, resourceWithTools.id, updateRequest);

            expect(updated).toBeDefined();
            expect(updated.features).toHaveLength(1);
            expect(updated.features[0].key).toEqual('tool_to_keep');
        });

        it('Should delete the last tool leaving empty features list', async () => {
            // Delete the last tool
            const updateRequest = {
                name: resourceWithTools.name,
                description: resourceWithTools.description,
                resourceIdentifiers: resourceWithTools.resourceIdentifiers,
                features: []
            } as UpdateProtectedResource;

            const updated = await updateProtectedResource(domain.id, accessToken, resourceWithTools.id, updateRequest);

            expect(updated).toBeDefined();
            expect(updated.features).toHaveLength(0);
            expect(updated.updatedAt).not.toEqual(resourceWithTools.updatedAt);
        });

        it('Should maintain integrity and allow adding tools after clearing all', async () => {
            // Verify the resource still exists and can be fetched after all tools deleted
            const fetched = await getMcpServer(domain.id, accessToken, resourceWithTools.id);

            expect(fetched).toBeDefined();
            expect(fetched.id).toEqual(resourceWithTools.id);
            expect(fetched.name).toEqual(resourceWithTools.name);
            expect(fetched.resourceIdentifiers).toEqual(resourceWithTools.resourceIdentifiers);
            expect(fetched.features).toHaveLength(0);

            // Add a new tool to the resource that has no tools
            const updateRequest = {
                name: resourceWithTools.name,
                description: resourceWithTools.description,
                resourceIdentifiers: resourceWithTools.resourceIdentifiers,
                features: [
                    {
                        key: 'new_tool_after_delete',
                        type: 'MCP_TOOL',
                        description: 'Tool added after all were deleted',
                        scopes: ['delete_test_scope_1']
                    } as UpdateMcpTool
                ]
            } as UpdateProtectedResource;

            const updated = await updateProtectedResource(domain.id, accessToken, resourceWithTools.id, updateRequest);

            expect(updated).toBeDefined();
            expect(updated.features).toHaveLength(1);
            expect(updated.features[0].key).toEqual('new_tool_after_delete');
        });

        it('Should preserve timestamps correctly when deleting tools', async () => {
            // Create a new resource with multiple tools for timestamp testing
            const request = {
                name: faker.commerce.productName(),
                type: "MCP_SERVER",
                resourceIdentifiers: ["https://timestamp-delete-test.com"],
                features: [
                    {
                        key: 'timestamp_tool_1',
                        type: 'MCP_TOOL',
                        description: 'Tool 1',
                        scopes: []
                    } as any,
                    {
                        key: 'timestamp_tool_2',
                        type: 'MCP_TOOL',
                        description: 'Tool 2',
                        scopes: []
                    } as any
                ]
            } as NewProtectedResource;

            const created = await createProtectedResource(domain.id, accessToken, request);
            await waitFor(2000);

            const before = await getMcpServer(domain.id, accessToken, created.id);
            const originalUpdatedAt = before.updatedAt;
            const tool1CreatedAt = before.features.find(f => f.key === 'timestamp_tool_1')?.createdAt;

            await waitFor(500); // Ensure time difference

            // Delete timestamp_tool_2
            const updateRequest = {
                name: before.name,
                description: before.description,
                resourceIdentifiers: before.resourceIdentifiers,
                features: [
                    {
                        key: 'timestamp_tool_1',
                        type: 'MCP_TOOL',
                        description: 'Tool 1',
                        scopes: []
                    } as UpdateMcpTool
                ]
            } as UpdateProtectedResource;

            const updated = await updateProtectedResource(domain.id, accessToken, created.id, updateRequest);

            expect(updated).toBeDefined();
            expect(updated.features).toHaveLength(1);
            expect(updated.updatedAt).not.toEqual(originalUpdatedAt);

            // The remaining tool should keep its original createdAt
            const remainingTool = updated.features.find(f => f.key === 'timestamp_tool_1');
            expect(remainingTool).toBeDefined();
            if (tool1CreatedAt && remainingTool.createdAt) {
                expect(remainingTool.createdAt).toEqual(tool1CreatedAt);
            }
        });

        it('Should delete tool with special scope configurations', async () => {
            // Create resource with tool that has multiple scopes
            const request = {
                name: faker.commerce.productName(),
                type: "MCP_SERVER",
                resourceIdentifiers: ["https://scope-delete-test.com"],
                features: [
                    {
                        key: 'tool_with_many_scopes',
                        type: 'MCP_TOOL',
                        description: 'Tool with multiple scopes',
                        scopes: ['delete_test_scope_1', 'delete_test_scope_2']
                    } as any,
                    {
                        key: 'tool_with_no_scopes',
                        type: 'MCP_TOOL',
                        description: 'Tool with no scopes',
                        scopes: []
                    } as any
                ]
            } as NewProtectedResource;

            const createdSecret = await createProtectedResource(domain.id, accessToken, request);
            await waitFor(1000);

            const created = await getMcpServer(domain.id, accessToken, createdSecret.id);

            // Delete the tool with multiple scopes
            const updateRequest = {
                name: created.name,
                description: created.description,
                resourceIdentifiers: created.resourceIdentifiers,
                features: [
                    {
                        key: 'tool_with_no_scopes',
                        type: 'MCP_TOOL',
                        description: 'Tool with no scopes',
                        scopes: []
                    } as UpdateMcpTool
                ]
            } as UpdateProtectedResource;

            const updated = await updateProtectedResource(domain.id, accessToken, created.id, updateRequest);

            expect(updated).toBeDefined();
            expect(updated.features).toHaveLength(1);
            expect(updated.features[0].key).toEqual('tool_with_no_scopes');
            expect(updated.features.find(f => f.key === 'tool_with_many_scopes')).toBeUndefined();
        });

        it('Should validate remaining tools after deletion', async () => {
            // Create resource with valid and a tool to delete
            const request = {
                name: faker.commerce.productName(),
                type: "MCP_SERVER",
                resourceIdentifiers: ["https://validation-delete-test.com"],
                features: [
                    {
                        key: 'valid_tool',
                        type: 'MCP_TOOL',
                        description: 'Valid tool',
                        scopes: ['delete_test_scope_1']
                    } as any,
                    {
                        key: 'tool_to_remove',
                        type: 'MCP_TOOL',
                        description: 'Will be removed',
                        scopes: ['delete_test_scope_2']
                    } as any
                ]
            } as NewProtectedResource;

            const createdSecret = await createProtectedResource(domain.id, accessToken, request);
            await waitFor(1000);

            const created = await getMcpServer(domain.id, accessToken, createdSecret.id);

            // Delete tool_to_remove but accidentally use invalid scope for remaining tool
            const updateRequest = {
                name: created.name,
                description: created.description,
                resourceIdentifiers: created.resourceIdentifiers,
                features: [
                    {
                        key: 'valid_tool',
                        type: 'MCP_TOOL',
                        description: 'Valid tool',
                        scopes: ['non_existent_scope'] // Invalid scope
                    } as UpdateMcpTool
                ]
            } as UpdateProtectedResource;

            // Should fail because remaining tool has invalid scope
            await updateProtectedResource(domain.id, accessToken, created.id, updateRequest)
                .catch(err => expect(err.response.status).toEqual(500));
        });

        it('Should not allow duplicate keys when deleting and re-adding', async () => {
            // Create resource with two tools
            const request = {
                name: faker.commerce.productName(),
                type: "MCP_SERVER",
                resourceIdentifiers: ["https://duplicate-delete-test.com"],
                features: [
                    {
                        key: 'tool_a',
                        type: 'MCP_TOOL',
                        description: 'Tool A',
                        scopes: []
                    } as any,
                    {
                        key: 'tool_b',
                        type: 'MCP_TOOL',
                        description: 'Tool B',
                        scopes: []
                    } as any
                ]
            } as NewProtectedResource;

            const createdSecret = await createProtectedResource(domain.id, accessToken, request);
            await waitFor(1000);

            const created = await getMcpServer(domain.id, accessToken, createdSecret.id);

            // Try to "delete" tool_b but add it back with duplicate key
            const updateRequest = {
                name: created.name,
                description: created.description,
                resourceIdentifiers: created.resourceIdentifiers,
                features: [
                    {
                        key: 'tool_a',
                        type: 'MCP_TOOL',
                        description: 'Tool A',
                        scopes: []
                    } as UpdateMcpTool,
                    {
                        key: 'tool_a', // Duplicate!
                        type: 'MCP_TOOL',
                        description: 'Duplicate Tool A',
                        scopes: []
                    } as UpdateMcpTool
                ]
            } as UpdateProtectedResource;

            // Should fail due to duplicate keys
            await updateProtectedResource(domain.id, accessToken, created.id, updateRequest)
                .catch(err => expect(err.response.status).toEqual(400));
        });
    });
});

