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

import { afterAll, beforeAll, describe, expect, jest } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
import { createDomain, deleteDomain, patchDomain, startDomain, waitForDomainSync } from '@management-commands/domain-management-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { BulkRequest } from '../../../api/gateway/models/scim/BulkRequest/BulkRequest';
import {
    getWellKnownOpenIdConfiguration,
    performGet, performPatch,
    performPost,
    performPut
} from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { BulkOperation } from '../../../api/gateway/models/scim/BulkRequest/BulkOperation';
import { BulkResponse } from '../../../api/gateway/models/scim/BulkRequest/BulkResponse';
import { Error } from '../../../api/gateway/models/scim/BulkRequest/Error';
import { random } from 'faker';

let mngAccessToken: string;
let scimAccessToken: string;
let domain: Domain;
let scimClient: Application;
let scimEndpoint: string;
let createdUser;

jest.setTimeout(200000);

beforeAll(async function () {
  mngAccessToken = await requestAdminAccessToken();
  expect(mngAccessToken).toBeDefined();

  domain = await createDomain(mngAccessToken, 'enterprise-user-scim', 'Domain used to test Enterprise User Schema Extension SCIM requests');
  expect(domain).toBeDefined();

  // enable SCIM
  await patchDomain(domain.id, mngAccessToken, {
    scim: {
      enabled: true,
      idpSelectionEnabled: false,
    },
  });

  const applicationDefinition: Application = {
    name: 'SCIM App',
    type: 'SERVICE',
    settings: {
      oauth: {
        grantTypes: ['client_credentials'],
        scopeSettings: [
          {
            scope: 'scim',
            defaultScope: true,
          },
        ],
      },
    },
  };

  scimClient = await createApplication(domain.id, mngAccessToken, applicationDefinition);
  expect(scimClient).toBeDefined();
  // we need to call update app as the CREATE order is not taking the scope settings into account
  // NOTE: we do not override the scimClient to keep reference of the clientSecret
  await updateApplication(domain.id, mngAccessToken, applicationDefinition, scimClient.id);

  await startDomain(domain.id, mngAccessToken);

  await waitForDomainSync();
  const openIdConfiguration = await getWellKnownOpenIdConfiguration(domain.hrid);

  // generate SCIM access token
  const response = await performPost(openIdConfiguration.body.token_endpoint, '', 'grant_type=client_credentials', {
    'Content-type': 'application/x-www-form-urlencoded',
    Authorization: 'Basic ' + applicationBase64Token(scimClient),
  });
  scimAccessToken = response.body.access_token;
  scimEndpoint = process.env.AM_GATEWAY_URL + `/${domain.hrid}/scim`;
});

afterAll(async function () {
  if (domain) {
    await deleteDomain(domain.id, mngAccessToken);
  }
});

describe('SCIM Users endpoint with enterprise schema', () => {
    it('should create SCIM user with enterprise information', async () => {
        const request = {
            schemas: [
                'urn:ietf:params:scim:schemas:extension:enterprise:2.0:User',
                'urn:ietf:params:scim:schemas:core:2.0:User'],
            externalId: '70198412321242223922423',
            userName: 'barbara',
            password: null,
            name: {
                formatted: 'Ms. Barbara J Jensen, III',
                familyName: 'Jensen',
                givenName: 'Barbara',
                middleName: 'Jane',
                honorificPrefix: 'Ms.',
                honorificSuffix: 'III',
            },
            displayName: 'Babs Jensen',
            nickName: 'Babs',
            emails: [
                {
                    value: 'user@user.com',
                    type: 'work',
                    primary: true,
                },
            ],
            userType: 'Employee',
            title: 'Tour Guide',
            preferredLanguage: 'en-US',
            locale: 'en-US',
            timezone: 'America/Los_Angeles',
            active: true,
            'urn:ietf:params:scim:schemas:extension:enterprise:2.0:User': {
                employeeNumber: '701984',
                costCenter: '4130',
                manager: {
                    value: '12345',
                    $ref: 'ref12345',
                    displayName: 'TEST USER'
                }
            }
        };

        const response = await performPost(scimEndpoint, '/Users', JSON.stringify(request), {
            'Content-type': 'application/json',
            Authorization: `Bearer ${scimAccessToken}`,
        }).expect(201);

        createdUser = response.body;
        expect(createdUser).toBeDefined();
        expect(createdUser.enabled).toBeFalsy();
        expect(createdUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User']).toBeDefined();
        expect(createdUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'].employeeNumber).toBeDefined();
        expect(createdUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'].employeeNumber).toBe('701984');
        expect(createdUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'].costCenter).toBeDefined();
        expect(createdUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'].costCenter).toBe('4130');
        expect(createdUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'].manager).toBeDefined();
        expect(createdUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'].manager.value).toBe('12345');
        expect(createdUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'].manager.$ref).toBe('ref12345');
        expect(createdUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'].manager.displayName).toBe('TEST USER');
    });

    it('should update SCIM user with enterprise information', async () => {
        const request = {
            schemas: [
                'urn:ietf:params:scim:schemas:extension:enterprise:2.0:User',
                'urn:ietf:params:scim:schemas:core:2.0:User'],
            externalId: '70198412321242223922423',
            userName: 'barbara',
            password: null,
            name: {
                formatted: 'Ms. Barbara J Jensen, III',
                familyName: 'Jensen',
                givenName: 'Barbara',
                middleName: 'Jane',
                honorificPrefix: 'Ms.',
                honorificSuffix: 'III',
            },
            displayName: 'Babs Jensen',
            nickName: 'Babs',
            emails: [
                {
                    value: 'user@user.com',
                    type: 'work',
                    primary: true,
                },
            ],
            userType: 'Employee',
            title: 'Tour Guide',
            preferredLanguage: 'en-US',
            locale: 'en-US',
            timezone: 'America/Los_Angeles',
            active: true,
            'urn:ietf:params:scim:schemas:extension:enterprise:2.0:User': {
                employeeNumber: '701984',
                costCenter: '4130',
                manager: {
                    value: '12346',
                    displayName: 'TEST USER 2'
                }
            }
        };

        const response = await performPut(scimEndpoint, '/Users/' + createdUser.id, JSON.stringify(request), {
            'Content-type': 'application/json',
            Authorization: `Bearer ${scimAccessToken}`,
        }).expect(200);

        let updatedUser = response.body;
        expect(updatedUser).toBeDefined();
        expect(updatedUser.enabled).toBeFalsy();
        expect(updatedUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User']).toBeDefined();
        expect(updatedUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'].employeeNumber).toBeDefined();
        expect(updatedUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'].employeeNumber).toBe('701984');
        expect(updatedUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'].costCenter).toBeDefined();
        expect(updatedUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'].costCenter).toBe('4130');
        expect(updatedUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'].manager).toBeDefined();
        expect(updatedUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'].manager.value).toBe('12346');
        expect(updatedUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'].manager.$ref).not.toBeDefined();
        expect(updatedUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'].manager.displayName).toBe('TEST USER 2');
    });

    it('should update SCIM user with enterprise information and custom information', async () => {
        const request = {
            schemas: [
                'urn:ietf:params:scim:schemas:extension:enterprise:2.0:User',
                'urn:ietf:params:scim:schemas:extension:custom:2.0:User',
                'urn:ietf:params:scim:schemas:core:2.0:User'],
            externalId: '70198412321242223922423',
            userName: 'barbara',
            password: null,
            name: {
                formatted: 'Ms. Barbara J Jensen, III',
                familyName: 'Jensen',
                givenName: 'Barbara',
                middleName: 'Jane',
                honorificPrefix: 'Ms.',
                honorificSuffix: 'III',
            },
            displayName: 'Babs Jensen',
            nickName: 'Babs',
            emails: [
                {
                    value: 'user@user.com',
                    type: 'work',
                    primary: true,
                },
            ],
            userType: 'Employee',
            title: 'Tour Guide',
            preferredLanguage: 'en-US',
            locale: 'en-US',
            timezone: 'America/Los_Angeles',
            active: true,
            'urn:ietf:params:scim:schemas:extension:enterprise:2.0:User': {
                employeeNumber: '701984',
                costCenter: '4130',
                manager: {
                    value: '12346',
                    displayName: 'TEST USER 2'
                }
            },
            'urn:ietf:params:scim:schemas:extension:custom:2.0:User': {
                claim1: "claim1Value"
            }
        };

        const response = await performPut(scimEndpoint, '/Users/' + createdUser.id, JSON.stringify(request), {
            'Content-type': 'application/json',
            Authorization: `Bearer ${scimAccessToken}`,
        }).expect(200);

        let updatedUser = response.body;
        expect(updatedUser).toBeDefined();
        expect(updatedUser.enabled).toBeFalsy();
        expect(updatedUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User']).toBeDefined();
        expect(updatedUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'].employeeNumber).toBeDefined();
        expect(updatedUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'].employeeNumber).toBe('701984');
        expect(updatedUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'].costCenter).toBeDefined();
        expect(updatedUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'].costCenter).toBe('4130');
        expect(updatedUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'].manager).toBeDefined();
        expect(updatedUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'].manager.value).toBe('12346');
        expect(updatedUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'].manager.$ref).not.toBeDefined();
        expect(updatedUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'].manager.displayName).toBe('TEST USER 2');
        expect(updatedUser['urn:ietf:params:scim:schemas:extension:custom:2.0:User']).toBeDefined();
        expect(updatedUser['urn:ietf:params:scim:schemas:extension:custom:2.0:User'].claim1).toBe('claim1Value');
    });

    it('should patch SCIM user with enterprise information (add)', async () => {
        const request = {
            schemas: ['urn:ietf:params:scim:api:messages:2.0:PatchOp'],
            Operations: [{
                op : 'Add',
                path: 'urn:ietf:params:scim:schemas:extension:enterprise:2.0:User',
                value: {
                    manager: {
                        $ref: 'ref12347'
                    }
                }
            }]
        };

        const response = await performPatch(scimEndpoint, '/Users/' + createdUser.id, JSON.stringify(request), {
            'Content-type': 'application/json',
            Authorization: `Bearer ${scimAccessToken}`,
        }).expect(200);

        let updatedUser = response.body;
        expect(updatedUser).toBeDefined();
        expect(updatedUser.enabled).toBeFalsy();
        expect(updatedUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User']).toBeDefined();
        expect(updatedUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'].employeeNumber).toBeDefined();
        expect(updatedUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'].employeeNumber).toBe('701984');
        expect(updatedUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'].costCenter).toBeDefined();
        expect(updatedUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'].costCenter).toBe('4130');
        expect(updatedUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'].manager).toBeDefined();
        expect(updatedUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'].manager.$ref).toBeDefined();
        expect(updatedUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'].manager.$ref).toBe('ref12347');
    });

    it('should patch SCIM user with enterprise information (replace)', async () => {
        const request = {
            schemas: ['urn:ietf:params:scim:api:messages:2.0:PatchOp'],
            Operations: [{
                op : 'Replace',
                path: 'urn:ietf:params:scim:schemas:extension:enterprise:2.0:User',
                value: {
                    employeeNumber: '701988',
                    costCenter: '4138',
                    manager: {
                        $ref: 'ref12348'
                    }
                }
            }]
        };

        const response = await performPatch(scimEndpoint, '/Users/' + createdUser.id, JSON.stringify(request), {
            'Content-type': 'application/json',
            Authorization: `Bearer ${scimAccessToken}`,
        }).expect(200);

        let updatedUser = response.body;
        expect(updatedUser).toBeDefined();
        expect(updatedUser.enabled).toBeFalsy();
        expect(updatedUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User']).toBeDefined();
        expect(updatedUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'].employeeNumber).toBeDefined();
        expect(updatedUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'].employeeNumber).toBe('701988');
        expect(updatedUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'].costCenter).toBeDefined();
        expect(updatedUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'].costCenter).toBe('4138');
        expect(updatedUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'].manager).toBeDefined();
        expect(updatedUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'].manager.$ref).toBeDefined();
        expect(updatedUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User'].manager.$ref).toBe('ref12348');
    });

    it('should patch SCIM user with enterprise information (remove)', async () => {
        const request = {
            schemas: ['urn:ietf:params:scim:api:messages:2.0:PatchOp'],
            Operations: [
                {
                    op : 'Remove',
                    path: 'urn:ietf:params:scim:schemas:extension:enterprise:2.0:User.employeeNumber',
                },
                {
                    op : 'Remove',
                    path: 'urn:ietf:params:scim:schemas:extension:enterprise:2.0:User.costCenter',
                },
                {
                    op : 'Remove',
                    path: 'urn:ietf:params:scim:schemas:extension:enterprise:2.0:User.manager',
                }
            ]
        };

        const response = await performPatch(scimEndpoint, '/Users/' + createdUser.id, JSON.stringify(request), {
            'Content-type': 'application/json',
            Authorization: `Bearer ${scimAccessToken}`,
        }).expect(200);

        let updatedUser = response.body;
        expect(updatedUser).toBeDefined();
        expect(updatedUser.enabled).toBeFalsy();
        expect(updatedUser['urn:ietf:params:scim:schemas:extension:enterprise:2.0:User']).not.toBeDefined();
    });
});


