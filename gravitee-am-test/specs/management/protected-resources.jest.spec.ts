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
import {Domain} from "@management-models/Domain";
import {afterAll, beforeAll, expect} from "@jest/globals";
import {requestAdminAccessToken} from "@management-commands/token-management-commands";
import {deleteDomain, setupDomainForTest} from "@management-commands/domain-management-commands";
import {uniqueName} from "@utils-commands/misc";
import faker from "faker";
import {createApplication} from "@management-commands/application-management-commands";
import {createProtectedResource, getMcpServers} from "@management-commands/protected-resources-management-commands";
import {NewProtectedResource} from "@management-models/NewProtectedResource";

global.fetch = fetch;

let accessToken: string;
let domain: Domain;
let domainTestSearch: Domain;

beforeAll(async () => {
    accessToken = await requestAdminAccessToken();
    domain = await setupDomainForTest(uniqueName('domain-protected-resources'), { accessToken }).then((it) => it.domain);
    domainTestSearch = await setupDomainForTest(uniqueName('domain-protected-resources-search'), { accessToken }).then((it) => it.domain);
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
            type: "MCP_SERVER",
            resourceIdentifiers: ["https://something.com", "https://something2.com"]
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
            clientId: "client-id",
            type: "MCP_SERVER",
            resourceIdentifiers: ["https://something.com"]
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
            clientId: "client-id2",
            clientSecret: "secret",
            type: "MCP_SERVER",
            resourceIdentifiers: ["https://something.com"]
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
            clientId: "client-id3",
            clientSecret: "secret",
            type: "MCP_SERVER",
            resourceIdentifiers: ["https://something.com"]
        } as NewProtectedResource;

        const createdResource = await createProtectedResource(domain.id, accessToken, request);
        expect(createdResource).toBeDefined();
        expect(createdResource.id).toBeDefined();
        expect(createdResource.name).toEqual(request.name);
        expect(createdResource.clientSecret).toEqual(request.clientSecret);
        expect(createdResource.clientId).toEqual(request.clientId);

        createProtectedResource(domain.id, accessToken, request)
            .catch(err => expect(err.response.status).toEqual(400))
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
            clientSecret: "secret",
            type: "MCP_SERVER",
            resourceIdentifiers: ["https://something.com"]
        } as NewProtectedResource;

        createProtectedResource(domain.id, accessToken, request)
            .catch(err => expect(err.response.status).toEqual(400))
    });

    it('Protected Resource must not be created without type', async () => {
        const request = {
            name: faker.commerce.productName(),
            resourceIdentifiers: ["https://something.com", "https://something2.com"]
        } as NewProtectedResource;

        createProtectedResource(domain.id, accessToken, request)
          .catch(err => expect(err.response.status).toEqual(400))
    });

    it('Protected Resource must not be created with wrong type', async () => {
        const request = {
            name: faker.commerce.productName(),
            type: 'MCP_SERVERR',
            resourceIdentifiers: ["https://something.com", "https://something2.com"]
        } as NewProtectedResource;

        createProtectedResource(domain.id, accessToken, request)
          .catch(err => expect(err.response.status).toEqual(400))
    });

    it('Protected Resource must not be created with wrong resource identifier', async () => {
        const request = {
            name: faker.commerce.productName(),
            type: 'MCP_SERVER',
            resourceIdentifiers: ["something", "https://something2.com"]
        } as NewProtectedResource;

        createProtectedResource(domain.id, accessToken, request)
          .catch(err => expect(err.response.status).toEqual(400))
    });

    it('Protected Resource must not be created with wrong resource identifier', async () => {
        const request = {
            name: faker.commerce.productName(),
            type: 'MCP_SERVER',
            resourceIdentifiers: ["something", "https://something2.com"]
        } as NewProtectedResource;

        createProtectedResource(domain.id, accessToken, request)
          .catch(err => expect(err.response.status).toEqual(400))
    });

});

describe('When admin created bunch of Protected Resources', () => {
    beforeAll( async () => {
        for (let i = 0; i < 100; i++) {
            const request = {
                name: faker.commerce.productName(),
                type: "MCP_SERVER",
                resourceIdentifiers: [`https://abc${faker.random.alpha()}.com`, `https://abc${faker.random.alpha()}.com`]
            } as NewProtectedResource;
            await createProtectedResource(domainTestSearch.id, accessToken, request);
        }
    })

    it('Protected Resource page must not contain secret', async () => {
        const page = await getMcpServers(domainTestSearch.id, accessToken, 100, 0);
        const secretNotPresent = page.data.every(data => data['secret'] == undefined)
        expect(secretNotPresent).toBeTruthy()
    })

    it('Protected Resource page should be returned', async () => {
        const page = await getMcpServers(domainTestSearch.id, accessToken, 10, 0);
        expect(page.currentPage).toEqual(0);
        expect(page.totalCount).toEqual(100);
        expect(page.data).toHaveLength(10);
    })

    it('Protected Resource page 1 should be different than 2', async () => {
        const page1 = await getMcpServers(domainTestSearch.id, accessToken, 10, 0);
        expect(page1.currentPage).toEqual(0);
        expect(page1.totalCount).toEqual(100);
        expect(page1.data).toHaveLength(10);

        const page2 = await getMcpServers(domainTestSearch.id, accessToken, 10, 1);
        expect(page2.currentPage).toEqual(1);
        expect(page2.totalCount).toEqual(100);
        expect(page2.data).toHaveLength(10);

        expect(page1.data[0].id).not.toEqual(page2.data[0].id)
    })

    it('No data returned if page exceeds elements count', async () => {
        const page1 = await getMcpServers(domainTestSearch.id, accessToken, 10, 200);
        expect(page1.currentPage).toEqual(200);
        expect(page1.totalCount).toEqual(100);
        expect(page1.data).toHaveLength(0);
    })

    it('All data should be returned if size exceeds elements count', async () => {
        const page1 = await getMcpServers(domainTestSearch.id, accessToken, 200, 0);
        expect(page1.currentPage).toEqual(0);
        expect(page1.totalCount).toEqual(100);
        expect(page1.data).toHaveLength(100);
    })

    it('Protected Resource page should be returned sorted by name', async () => {
        const pageAsc = await getMcpServers(domainTestSearch.id, accessToken, 100, 0, 'name.asc');
        const pageDesc = await getMcpServers(domainTestSearch.id, accessToken, 100, 0, 'name.desc');
        expect(pageAsc.data[0].name).toEqual(pageDesc.data[99].name);
        expect(pageAsc.data[0].id).not.toEqual(pageDesc.data[0].id);
    })

    it('Protected Resource page should be returned sorted by updatedAt', async () => {
        const pageAsc = await getMcpServers(domainTestSearch.id, accessToken, 100, 0, 'updatedAt.asc');
        const pageDesc = await getMcpServers(domainTestSearch.id, accessToken, 100, 0, 'updatedAt.desc');
        expect(pageAsc.data[0].updatedAt).toEqual(pageDesc.data[99].updatedAt);
        expect(pageAsc.data[0].id).not.toEqual(pageDesc.data[0].id);
    })

});