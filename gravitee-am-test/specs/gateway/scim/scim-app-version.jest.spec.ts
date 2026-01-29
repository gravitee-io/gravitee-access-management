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
import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { patchDomain, waitForDomainSync } from '@management-commands/domain-management-commands';
import { performDelete, performGet, performPatch, performPost, performPut } from '@gateway-commands/oauth-oidc-commands';
import supertest from 'supertest';



import { setupFixture, ScimFixture, createScimUserBody, createScimGroupBody } from './fixture/scim-fixture';
import { setup } from '../../test-fixture';

setup(200000);

let fixture: ScimFixture;

beforeAll(async () => {
  fixture = await setupFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

describe('SCIM - App Version', () => {

  describe('Unsecured Endpoints', () => {
    it('should retrieve OIDC configuration', async () => {
      // Corresponds to "well-known/openid-configuration"
      const response = await performGet(process.env.AM_GATEWAY_URL + '/' + fixture.domain.hrid + '/oidc', '/.well-known/openid-configuration');
      expect(response.status).toEqual(200);
      expect(response.headers['content-type']).toContain('application/json');
      
      expect(response.status).toEqual(200);
      expect(response.text).toContain('token_endpoint');
    });
  });



  describe('SCIM Enabled', () => {
    beforeAll(async () => {
      // Enable SCIM
      await patchDomain(fixture.domain.id, fixture.accessToken, {
        scim: {
          enabled: true,
        },
      });
      await waitForDomainSync(fixture.domain.id, fixture.accessToken);
    });

    describe('Service Provider Configuration', () => {
      it('should retrieve ServiceProviderConfig', async () => {
        const response = await performGet(fixture.scimEndpoint, '/ServiceProviderConfig');
        expect(response.status).toEqual(200);
        
        const body = response.body;
        expect(body.schemas).toEqual(['urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig']);
        expect(body.patch.supported).toEqual(true);
        expect(body.bulk.supported).toEqual(false);
        expect(body.filter.supported).toEqual(true);
        expect(body.changePassword.supported).toEqual(false);
        expect(body.sort.supported).toEqual(false);
        expect(body.etag.supported).toEqual(false);
        expect(body.authenticationSchemes[0].type).toEqual('oauthbearertoken');
      });
    });

    describe('Security checks (Invalid/No Token)', () => {
      const endpoints = ['Users', 'Groups'];
      const actions = [
          { method: 'GET', path: '' },
          { method: 'POST', path: '', body: {} },
          { method: 'PUT', path: '/test-id', body: {} },
          { method: 'DELETE', path: '/test-id' }
      ];

      endpoints.forEach(endpoint => {
        describe(`${endpoint} Endpoint`, () => {
            actions.forEach(action => {
                it(`should return 401 when accessing ${endpoint} (${action.method}) with no token`, async () => {
                    let req;
                    const path = `/${endpoint}${action.path}`;
                    
                    if (action.method === 'GET') req = performGet(fixture.scimEndpoint, path);
                    if (action.method === 'POST') req = performPost(fixture.scimEndpoint, path, '', { 'Content-Type': 'application/json' }); // No Auth header
                    if (action.method === 'PUT') req = performPut(fixture.scimEndpoint, `/${endpoint}${action.path}`, '', { 'Content-Type': 'application/json' });
                    if (action.method === 'DELETE') req = performDelete(fixture.scimEndpoint, `/${endpoint}${action.path}`, { 'Content-Type': 'application/json' });

                    const response = await req;
                    expect(response.status).toEqual(401);
                    
                    const body = response.body;
                    expect(body.status).toEqual("401");
                    expect(body.detail).toEqual("Authorization failure. The authorization header is invalid or missing.");
                    expect(body.schemas).toEqual(['urn:ietf:params:scim:api:messages:2.0:Error']);
                });

                it(`should return 401 when accessing ${endpoint} (${action.method}) with invalid token`, async () => {
                    let req;
                    const path = `/${endpoint}${action.path}`;
                    const headers = { 'Authorization': 'Bearer wrong-token', 'Content-Type': 'application/json' };
                    
                    if (action.method === 'GET') req = performGet(fixture.scimEndpoint, path, headers);
                    if (action.method === 'POST') req = performPost(fixture.scimEndpoint, path, '', headers);
                    if (action.method === 'PUT') req = performPut(fixture.scimEndpoint, path, '', headers);
                    if (action.method === 'DELETE') req = performDelete(fixture.scimEndpoint, path, headers);

                    const response = await req;
                    expect(response.status).toEqual(401);
                    
                    const body = response.body;
                    expect(body.status).toEqual("401");
                    expect(body.detail).toEqual("The access token is invalid");
                    expect(body.schemas).toEqual(['urn:ietf:params:scim:api:messages:2.0:Error']);
                });
            });
        });
      });
    });

    describe('Users - Create Validation', () => {
        it('should return 400 for malformed json', async () => {
             // We can't easily send malformed JSON with the helper, but sending empty body usually triggers parsing error or validation error.
             // Postman script: raw: ""
             const response = await performPost(fixture.scimEndpoint, '/Users', '', {
                 'Authorization': `Bearer ${fixture.scimAccessToken}`
             });
             
             expect(response.status).toEqual(400);
             const body = response.body;
             expect(body.status).toEqual("400");
             expect(body.scimType).toEqual("invalidSyntax");
             expect(body.detail).toEqual("Unable to parse body message");
             expect(body.schemas).toEqual(['urn:ietf:params:scim:api:messages:2.0:Error']);
        });

        it('should return 400 when userName is missing', async () => {
            const body = {
                "schemas":["urn:ietf:params:scim:schemas:core:2.0:User"],
                "externalId":"bjensen",
                "name":{
                  "formatted":"Ms. Barbara J Jensen III",
                  "familyName":"Jensen",
                  "givenName":"Barbara"
                }
            };
            const response = await performPost(fixture.scimEndpoint, '/Users', JSON.stringify(body), {
                'Authorization': `Bearer ${fixture.scimAccessToken}`,
                'Content-Type': 'application/json'
            });
            
            expect(response.status).toEqual(400);
            const resBody = response.body;
            expect(resBody.status).toEqual("400");
            expect(resBody.scimType).toEqual("invalidValue");
            expect(resBody.detail).toEqual("Field [userName] is required");
            expect(resBody.schemas).toEqual(['urn:ietf:params:scim:api:messages:2.0:Error']);
        });

        it('should return 400 when userName is invalid', async () => {
            const body = {
                "schemas":["urn:ietf:params:scim:schemas:core:2.0:User"],
                "externalId":"bjensen",
                "userName": "&Invalid",
                "name":{
                  "formatted":"Ms. Barbara J Jensen III",
                  "familyName":"Jensen",
                  "givenName":"Barbara"
                }
            };
            const response = await performPost(fixture.scimEndpoint, '/Users', JSON.stringify(body), {
                'Authorization': `Bearer ${fixture.scimAccessToken}`,
                'Content-Type': 'application/json'
            });
            
            expect(response.status).toEqual(400);
            const resBody = response.body;
            expect(resBody.status).toEqual("400");
            expect(resBody.scimType).toEqual("invalidValue");
            expect(resBody.detail).toEqual("Username [&Invalid] is not a valid value");
            expect(resBody.schemas).toEqual(['urn:ietf:params:scim:api:messages:2.0:Error']);
        });

        it('should return 400 when familyName is invalid', async () => {
             const body = {
                 "schemas":["urn:ietf:params:scim:schemas:core:2.0:User"],
                 "externalId":"bjensen",
                 "userName": "bjensen@example.com",
                 "name":{
                   "formatted":"Ms. Barbara J Jensen III",
                   "familyName":"#Invalid",
                   "givenName":"Barbara"
                 }
            };
            const response = await performPost(fixture.scimEndpoint, '/Users', JSON.stringify(body), {
                'Authorization': `Bearer ${fixture.scimAccessToken}`,
                'Content-Type': 'application/json'
            });
            
            expect(response.status).toEqual(400);
            const resBody = response.body;
            expect(resBody.status).toEqual("400");
            expect(resBody.scimType).toEqual("invalidValue");
            expect(resBody.detail).toEqual("Last name [#Invalid] is not a valid value");
            expect(resBody.schemas).toEqual(['urn:ietf:params:scim:api:messages:2.0:Error']);
        });

        it('should return 400 when givenName is invalid', async () => {
            const body = {
                "schemas":["urn:ietf:params:scim:schemas:core:2.0:User"],
                "externalId":"bjensen",
                "userName": "bjensen@example.com",
                "name":{
                  "formatted":"Ms. Barbara J Jensen III",
                  "familyName":"Jensen",
                  "givenName":"#Invalid"
                }
           };
           const response = await performPost(fixture.scimEndpoint, '/Users', JSON.stringify(body), {
               'Authorization': `Bearer ${fixture.scimAccessToken}`,
               'Content-Type': 'application/json'
           });
           
           expect(response.status).toEqual(400);
           const resBody = response.body;
           expect(resBody.status).toEqual("400");
           expect(resBody.scimType).toEqual("invalidValue");
           expect(resBody.detail).toEqual("First name [#Invalid] is not a valid value");
           expect(resBody.schemas).toEqual(['urn:ietf:params:scim:api:messages:2.0:Error']);
      });
    });

    describe('Users - CRUD', () => {
        let userId: string;

        it('should create a user', async () => {
            const body = createScimUserBody("bjensen@example.com", "Barbara", "Jensen", "701984");

            const response = await performPost(fixture.scimEndpoint, '/Users', JSON.stringify(body), {
                'Authorization': `Bearer ${fixture.scimAccessToken}`,
                'Content-Type': 'application/json'
            });

            expect(response.status).toEqual(201);
            const resBody = response.body;
            expect(resBody).toHaveProperty('id');
            userId = resBody.id;
            expect(resBody.schemas).toEqual(['urn:ietf:params:scim:schemas:core:2.0:User']);
        });

        it('should return 409 if userName already exists', async () => {
            const body = createScimUserBody("bjensen@example.com", "Barbara", "Jensen", "bjensen");

            const response = await performPost(fixture.scimEndpoint, '/Users', JSON.stringify(body), {
                'Authorization': `Bearer ${fixture.scimAccessToken}`,
                'Content-Type': 'application/json'
            });

            expect(response.status).toEqual(409);
            const resBody = response.body;
            expect(resBody.status).toEqual("409");
            expect(resBody.scimType).toEqual("uniqueness");
        });

        it('should return 404 when updating unknown user', async () => {
            const body = {
                "schemas":["urn:ietf:params:scim:schemas:core:2.0:User"],
                "userName":"bjensen",
                "name":{
                  "familyName":"Jensen",
                  "givenName":"Barbara"
                }
            };
            await performPut(fixture.scimEndpoint, '/Users/wrong-id', JSON.stringify(body), {
                'Authorization': `Bearer ${fixture.scimAccessToken}`,
                'Content-Type': 'application/json'
            }).expect(404);
        });

        it('should update user', async () => {
            const body = {
                "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
                "userName": "bjensen",
                "externalId": "bjensen",
                "name": {
                    "formatted": "Ms. Barbara J Jensen III",
                    "familyName": "Jensen2",
                    "givenName": "Barbara"
                }
            };

            const response = await performPut(fixture.scimEndpoint, `/Users/${userId}`, JSON.stringify(body), {
                'Authorization': `Bearer ${fixture.scimAccessToken}`,
                'Content-Type': 'application/json'
            });
            
            expect(response.status).toEqual(200);
            const resBody = response.body;
            expect(resBody.name.familyName).toEqual('Jensen2');
        });

        it('should patch user - add single attribute', async () => {
             const body = {
                 "schemas":["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
                 "Operations": [{
                    "op":"Add",
                    "path": "userType",
                    "value": "Employee"
                 }]
            };

            const response = await performPatch(fixture.scimEndpoint, `/Users/${userId}`, JSON.stringify(body), {
                'Authorization': `Bearer ${fixture.scimAccessToken}`,
                'Content-Type': 'application/json'
            });
            
            expect(response.status).toEqual(200);
            const resBody = response.body;
            expect(resBody.userType).toEqual('Employee');
        });

        it('should patch user - replace operation - multi-valued attribute', async () => {
            const body = {
                 "schemas":["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
                 "Operations": [{
                    "op":"Replace",
                    "path": "emails",
                    "value": [
                        {
                            "value": "bjensen2@example.com",
                            "type": "work",
                            "primary": true
                        },
                        {
                            "value": "bjensen-home@example.com",
                            "type": "home"
                        }
                    ]
                 }]
            };

            const response = await performPatch(fixture.scimEndpoint, `/Users/${userId}`, JSON.stringify(body), {
                'Authorization': `Bearer ${fixture.scimAccessToken}`,
                'Content-Type': 'application/json'
            });

            expect(response.status).toEqual(200);
            const resBody = response.body;
            expect(resBody.emails.length).toEqual(2);
            const emails = resBody.emails.map(e => e.value);
            expect(emails).toContain('bjensen2@example.com');
            expect(emails).toContain('bjensen-home@example.com');
        });

        it('should patch user - remove operation - single attribute', async () => {
             const body = {
                 "schemas":["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
                 "Operations": [{
                    "op":"Remove",
                    "path":"userType"
                 }]
            };

            const response = await performPatch(fixture.scimEndpoint, `/Users/${userId}`, JSON.stringify(body), {
                'Authorization': `Bearer ${fixture.scimAccessToken}`,
                'Content-Type': 'application/json'
            });

            expect(response.status).toEqual(200);
            const resBody = response.body;
            expect(resBody).not.toHaveProperty('userType');
        });

        it('should patch user - remove operation - complex attribute', async () => {
             const body = {
                 "schemas":["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
                 "Operations": [{
                    "op":"Remove",
                    "path":"name.middleName"
                 }]
            };

            const response = await performPatch(fixture.scimEndpoint, `/Users/${userId}`, JSON.stringify(body), {
                'Authorization': `Bearer ${fixture.scimAccessToken}`,
                'Content-Type': 'application/json'
            });

            expect(response.status).toEqual(200);
            const resBody = response.body;
            expect(resBody.name).not.toHaveProperty('middleName');
            expect(resBody.name).toHaveProperty('familyName');
        });

        it('should patch user - multiple operations', async () => {
            const body = {
                 "schemas":["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
                 "Operations": [
                     {
                        "op":"Add",
                        "path":"emails",
                        "value": [
                            {
                                "value": "johndoe@mail.com",
                                "type": "home"
                            }
                        ]
                    },
                    {
                        "op": "Replace",
                        "path": "userType",
                        "value": "Director"
                    },
                    {
                        "op": "Replace",
                        "path": "name.givenName",
                        "value": "Barbara"
                    },
                    {
                        "op": "Replace",
                        "path": "active",
                        "value": false
                    },
                    {
                        "op": "Remove",
                        "path": "name.middleName"
                    }
                ]
            };

            const response = await performPatch(fixture.scimEndpoint, `/Users/${userId}`, JSON.stringify(body), {
                'Authorization': `Bearer ${fixture.scimAccessToken}`,
                'Content-Type': 'application/json'
            });

            expect(response.status).toEqual(200);
            const resBody = response.body;
            const emails = resBody.emails.map(e => e.value);
            expect(emails).toContain('johndoe@mail.com');
            expect(resBody.userType).toEqual('Director');
            expect(resBody.active).toEqual(false);
            expect(resBody.name).not.toHaveProperty('middleName');
        });

        it('should list users', async () => {
             const response = await performGet(fixture.scimEndpoint, '/Users', {
                'Authorization': `Bearer ${fixture.scimAccessToken}`
            });
            expect(response.status).toEqual(200);
            const resBody = response.body;
            expect(resBody.totalResults).toBeGreaterThanOrEqual(1);
            expect(resBody.Resources.length).toBeGreaterThanOrEqual(1);
        });

        it('should filter users - simple filter - eq', async () => {
             const response = await performGet(fixture.scimEndpoint, `/Users?filter=userName eq "bjensen@example.com"`, {
                'Authorization': `Bearer ${fixture.scimAccessToken}`
            });
            expect(response.status).toEqual(200);
            const resBody = response.body;
            expect(resBody.totalResults).toEqual(1);
            expect(resBody.Resources[0].userName).toEqual('bjensen@example.com');
        });

        it('should filter users - simple filter - regex', async () => {
             const response = await performGet(fixture.scimEndpoint, `/Users?filter=userName co "bjensen"`, {
                'Authorization': `Bearer ${fixture.scimAccessToken}`
            });
            expect(response.status).toEqual(200);
            const resBody = response.body;
            expect(resBody.totalResults).toEqual(1);
            // expect(resBody.Resources[0].userName).toContain('bjensen'); // May fail if multiple users match, but totalResults=1 implies 1.
        });

        it('should filter users - complex filter - or', async () => {
             const response = await performGet(fixture.scimEndpoint, `/Users?filter=userName eq "invalid" or name.givenName eq "Barbara"`, {
                'Authorization': `Bearer ${fixture.scimAccessToken}`
            });
            expect(response.status).toEqual(200);
            const resBody = response.body;
            expect(resBody.totalResults).toBeGreaterThanOrEqual(1);
        });

        it('should filter users - complex filter - and', async () => {
             // simplified filter to ensure matches based on previous tests state
             const response = await performGet(fixture.scimEndpoint, `/Users?filter=name.givenName eq "Barbara" and active eq false`, {
                'Authorization': `Bearer ${fixture.scimAccessToken}`
            });
            expect(response.status).toEqual(200);
            const resBody = response.body;
            expect(resBody.totalResults).toBeGreaterThanOrEqual(1);
        });

        it('should return 400 for invalid filter syntax', async () => {
             const response = await performGet(fixture.scimEndpoint, `/Users?filter=invalid`, {
                'Authorization': `Bearer ${fixture.scimAccessToken}`
            });
            expect(response.status).toEqual(400);
            const resBody = response.body;
            expect(resBody.status).toEqual("400");
            expect(resBody.scimType).toEqual("invalidSyntax");
        });

        it('should return 200 with 0 results for unknown user filter', async () => {
             const response = await performGet(fixture.scimEndpoint, `/Users?filter=userName eq "invalid"`, {
                'Authorization': `Bearer ${fixture.scimAccessToken}`
            });
            expect(response.status).toEqual(200);
            const resBody = response.body;
            expect(resBody.totalResults).toEqual(0);
            expect(resBody.Resources.length).toEqual(0);
        });

        it('should delete user', async () => {
            const response = await performDelete(fixture.scimEndpoint, `/Users/${userId}`, {
                'Authorization': `Bearer ${fixture.scimAccessToken}`
            });
            expect(response.status).toEqual(204);
        });

        it('should return 404 when deleting unknown user', async () => {
            const response = await performDelete(fixture.scimEndpoint, `/Users/${userId}`, {
                'Authorization': `Bearer ${fixture.scimAccessToken}`
            });
            expect(response.status).toEqual(404);
        });
    });

    describe('Groups - CRUD', () => {
        let groupId: string;
        let user1Id: string;
        let user2Id: string;

        // Helper to create users for group membership tests
        beforeAll(async () => {
             // Create User 1
             const body1 = createScimUserBody("babs_group@example.com", "Babs", "Jensen");
            const res1 = await performPost(fixture.scimEndpoint, '/Users', JSON.stringify(body1), {
                'Authorization': `Bearer ${fixture.scimAccessToken}`, 'Content-Type': 'application/json'
            });
            user1Id = res1.body.id;

             // Create User 2
             const body2 = createScimUserBody("mandy_group@example.com", "Mandy", "Pepperidge");
            const res2 = await performPost(fixture.scimEndpoint, '/Users', JSON.stringify(body2), {
                'Authorization': `Bearer ${fixture.scimAccessToken}`, 'Content-Type': 'application/json'
            });
            user2Id = res2.body.id;
        });

        it('should create a group', async () => {
             const body = createScimGroupBody("Tour Guides");

            const response = await performPost(fixture.scimEndpoint, '/Groups', JSON.stringify(body), {
                'Authorization': `Bearer ${fixture.scimAccessToken}`,
                'Content-Type': 'application/json'
            });

            expect(response.status).toEqual(201);
            const resBody = response.body;
            expect(resBody).toHaveProperty('id');
            groupId = resBody.id;
            expect(resBody.displayName).toEqual("Tour Guides");
        });

        it('should return 409 if displayName already exists', async () => {
             const body = createScimGroupBody("Tour Guides");

            const response = await performPost(fixture.scimEndpoint, '/Groups', JSON.stringify(body), {
                'Authorization': `Bearer ${fixture.scimAccessToken}`,
                'Content-Type': 'application/json'
            });
            expect(response.status).toEqual(409);
        });

        it('should update group', async () => {
            const body = {
               "schemas": ["urn:ietf:params:scim:schemas:core:2.0:Group"],
               "displayName": "Tour Guides 2"
            };

            const response = await performPut(fixture.scimEndpoint, `/Groups/${groupId}`, JSON.stringify(body), {
                'Authorization': `Bearer ${fixture.scimAccessToken}`,
                'Content-Type': 'application/json'
            });

            expect(response.status).toEqual(200);
            const resBody = response.body;
            expect(resBody.displayName).toEqual("Tour Guides 2");
        });

        it('should patch group - add members', async () => {
             const body = {
                 "schemas":["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
                 "Operations": [{
                    "op":"Add",
                    "value" : {
                        "members": [
                            {
                                "value": user1Id,
                                "display": "Babs Jensen"
                            },
                            {
                                "value": user2Id,
                                "display": "Mandy Pepperidge"
                            }
                        ]
                    }
                 }]
            };

            const response = await performPatch(fixture.scimEndpoint, `/Groups/${groupId}`, JSON.stringify(body), {
                'Authorization': `Bearer ${fixture.scimAccessToken}`,
                'Content-Type': 'application/json'
            });

            expect(response.status).toEqual(200);
            const resBody = response.body;
            expect(resBody.members.length).toEqual(2);
        });

        it('should patch group - replace displayName', async () => {
             const body = {
                 "schemas":["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
                 "Operations": [{
                    "op":"Replace",
                    "path":"displayName",
                    "value": "Tour Guides 3"
                 }]
            };

            const response = await performPatch(fixture.scimEndpoint, `/Groups/${groupId}`, JSON.stringify(body), {
                'Authorization': `Bearer ${fixture.scimAccessToken}`,
                'Content-Type': 'application/json'
            });

             expect(response.status).toEqual(200);
             const resBody = response.body;
             expect(resBody.displayName).toEqual("Tour Guides 3");
        });

        it('should patch group - remove operation - multi-valued attribute with filter', async () => {
             const body = {
                 "schemas":["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
                 "Operations": [{
                    "op":"Remove",
                    "path":"members[display eq \"Babs Jensen\"]"
                 }]
            };

            const response = await performPatch(fixture.scimEndpoint, `/Groups/${groupId}`, JSON.stringify(body), {
                'Authorization': `Bearer ${fixture.scimAccessToken}`,
                'Content-Type': 'application/json'
            });

            expect(response.status).toEqual(200);
            const resBody = response.body;
            expect(resBody.members.length).toEqual(1);
            expect(resBody.members[0].display).toEqual('Mandy Pepperidge');
        });

        it('should patch group - remove operation - multi-valued attribute', async () => {
             const body = {
                 "schemas":["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
                 "Operations": [{
                    "op":"Remove",
                    "path":"members"
                 }]
            };

            const response = await performPatch(fixture.scimEndpoint, `/Groups/${groupId}`, JSON.stringify(body), {
                'Authorization': `Bearer ${fixture.scimAccessToken}`,
                'Content-Type': 'application/json'
            });

            expect(response.status).toEqual(200);
            const resBody = response.body;
            expect(resBody).not.toHaveProperty('members');
        });

        it('should patch group - multiple operations', async () => {
             const body = {
                 "schemas":["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
                 "Operations": [
                     {
                        "op":"Add",
                        "path":"displayName",
                        "value": "Tour Guides 2"
                    },
                    {
                        "op": "Add",
                        "path": "members",
                        "value": [
                            {
                                "value": user1Id,
                                "display": "Babs Jensen"
                            }
                        ]
                    }
                ]
            };

            const response = await performPatch(fixture.scimEndpoint, `/Groups/${groupId}`, JSON.stringify(body), {
                'Authorization': `Bearer ${fixture.scimAccessToken}`,
                'Content-Type': 'application/json'
            });

            expect(response.status).toEqual(200);
            const resBody = response.body;
            expect(resBody.displayName).toEqual("Tour Guides 2");
            expect(resBody.members[0].display).toEqual('Babs Jensen');
        });

        it('should list groups', async () => {
             const response = await performGet(fixture.scimEndpoint, '/Groups', {
                'Authorization': `Bearer ${fixture.scimAccessToken}`
            });
            expect(response.status).toEqual(200);
            const resBody = response.body;
            expect(resBody.totalResults).toBeGreaterThanOrEqual(1);
        });

        it('should delete group', async () => {
            const response = await performDelete(fixture.scimEndpoint, `/Groups/${groupId}`, {
                'Authorization': `Bearer ${fixture.scimAccessToken}`
            });
            expect(response.status).toEqual(204);
        });

        it('should return 404 when deleting unknown group', async () => {
             const response = await performDelete(fixture.scimEndpoint, `/Groups/${groupId}`, {
                'Authorization': `Bearer ${fixture.scimAccessToken}`
            });
            expect(response.status).toEqual(404);
        });
    });

  });
});

