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
import {createProtectedResource} from "@management-commands/protected-resources-management-commands";
import {NewProtectedResource} from "@management-models/NewProtectedResource";

global.fetch = fetch;

let accessToken: string;
let domain: Domain;

beforeAll(async () => {
    accessToken = await requestAdminAccessToken();
    domain = await setupDomainForTest(uniqueName('domain-protected-resources'), { accessToken }).then((it) => it.domain);
});

afterAll(async () => {
    if (domain && domain.id) {
        await deleteDomain(domain.id, accessToken);
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
});