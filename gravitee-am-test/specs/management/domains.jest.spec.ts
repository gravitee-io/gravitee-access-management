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
import * as faker from 'faker';
import {afterAll, beforeAll, expect} from '@jest/globals';
import {requestAdminAccessToken} from "@management-commands/token-management-commands";
import {createDomain, deleteDomain, getDomain, startDomain} from "@management-commands/domain-management-commands";

global.fetch = fetch;

let accessToken;
let domain;

beforeAll(async () => {
    const adminTokenResponse = await requestAdminAccessToken();
    accessToken = adminTokenResponse.body.access_token;
    expect(accessToken).toBeDefined()

    const createdDomain = await createDomain(accessToken,
        "domain-domain",
        faker.company.catchPhraseDescriptor()
    );
    expect(createdDomain).toBeDefined();
    expect(createdDomain.id).toBeDefined();
    domain = createdDomain;
});

describe("when using the domains commands", () => {
    it('Must Find a domain by id', async () => {
        const foundDomain = await getDomain(domain.id, accessToken);
        expect(foundDomain).toBeDefined();
        expect(foundDomain.id).toEqual(foundDomain.id);
    });

    it('Must Start a domain', async () => {
        const domainStarted = await startDomain(domain.id, accessToken);
        expect(domainStarted).toBeDefined();
        expect(domainStarted.id).toEqual(domain.id);
    });
});

afterAll(async () => {
    if (domain && domain.id) {
        await deleteDomain(domain.id, accessToken);
    }
});
