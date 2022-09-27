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
import {createDomain, startDomain} from "@management-commands/domain-management-commands";
import {
    createApplication, deleteApplication,
    getAllApplications,
    getApplication, getApplicationPage, patchApplication, renewApplicationSecrets, updateApplication
} from "@management-commands/application-management-commands";

global.fetch = fetch;

let accessToken;
let domain;

const SIMPLE_DOMAIN_NAME = "simple-domain";
const SERVICE_APP = "serviceApp";
const WEB_APP = "webApp";

beforeAll(async () => {
    const adminTokenResponse = await requestAdminAccessToken();
    accessToken = adminTokenResponse.body.access_token;
    expect(accessToken).toBeDefined()

    const createdDomain = await createDomain(accessToken,
        SIMPLE_DOMAIN_NAME,
        faker.company.catchPhraseDescriptor()
    );
    expect(createdDomain).toBeDefined();
    expect(createdDomain.id).toBeDefined();
    domain = createdDomain;

    const domainStarted = await startDomain(domain.id, accessToken);
    expect(domainStarted).toBeDefined();
    expect(domainStarted.id).toEqual(domain.id);

    // create domain owner
    // list roles
    // assign role
});

describe("On Simple domain", () => {
    it('A Service app is created', async () => {
        const app = {
            "name": SERVICE_APP,
            "type": "service",
            "description": faker.lorem.paragraph(),
            "clientId": SERVICE_APP ,
            "clientSecret": SERVICE_APP,
            "metadata": {
                "key1": "srvmd1",
                "key2": "srvmd2"
            }
        };

        const serviceApp = await createApplication(domain.id, accessToken, app);
        expect(serviceApp).toBeDefined();
        expect(serviceApp.id).toBeDefined();
        expect(serviceApp.name).toEqual(app.name);
        expect(serviceApp.description).toEqual(app.description);
        expect(serviceApp.type).toEqual(app.type);
        
        const scopes = { 
            "settings": 
                {
                     "oauth": 
                        { 
                            "enhanceScopesWithUserPermissions": false, 
                            "scopeSettings": [{ "scope": "scim", "defaultScope": false }] 
                        } 
                }
            };

        const patchedApp = await patchApplication(domain.id, accessToken, scopes, serviceApp.id);
        expect(patchedApp).toBeDefined(); 
    });

    it('A Web app is created', async () => {
        const app = {
            "name": WEB_APP,
            "type": "web",
            "description": faker.lorem.paragraph(),
            "clientId": WEB_APP ,
            "clientSecret": WEB_APP,
            "metadata": {
                "key1": "webmd1",
                "key2": "webmd2"
            }
        };

        const webApp = await createApplication(domain.id, accessToken, app);
        expect(webApp).toBeDefined();
        expect(webApp.id).toBeDefined();
        expect(webApp.name).toEqual(app.name);
        expect(webApp.description).toEqual(app.description);
        expect(webApp.type).toEqual(app.type);
        
        const scopes = { 
            "settings": 
                {
                     "oauth": 
                        { 
                            "enhanceScopesWithUserPermissions": false, 
                            "scopeSettings": [
                                { "scope": "openid", "defaultScope": true },
                                { "scope": "email", "defaultScope": false },
                                { "scope": "profile", "defaultScope": false }  
                            ] 
                        } 
                }
            };

        const patchedApp = await patchApplication(domain.id, accessToken, scopes, webApp.id);
        expect(patchedApp).toBeDefined(); 


    // enable default idp
    // create user01
    });
});

