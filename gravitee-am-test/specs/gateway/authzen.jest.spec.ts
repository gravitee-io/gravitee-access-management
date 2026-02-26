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

import {afterAll, beforeAll, describe, expect, it} from '@jest/globals';
import {setup} from '../test-fixture';
import {requestAdminAccessToken} from '@management-commands/token-management-commands';
import {
    safeDeleteDomain,
    setupDomainForTest,
} from '@management-commands/domain-management-commands';
import {waitForNextSync} from '@gateway-commands/monitoring-commands';
import {uniqueName} from '@utils-commands/misc';
import {buildTestUser, createUser} from '@management-commands/user-management-commands';
import {createAuthorizationEngine, deleteAuthorizationEngine} from '@management-commands/authorization-engine-management-commands';
import {addTuple, deleteTuple} from '@management-commands/openfga-settings-commands';
import {mcpAuthorizationModel, tupleFactory, authzenFactory} from '@api-fixtures/openfga-fixtures';
import {AuthorizationEngine} from '@management-models/AuthorizationEngine';
import {createApplication, updateApplication} from '@management-commands/application-management-commands';
import {evaluateAccess, evaluateAccessExpectError, evaluateAccessUnauthenticated} from '@gateway-commands/authzen-commands';
import {getWellKnownOpenIdConfiguration, requestClientCredentialsToken} from '@gateway-commands/oauth-oidc-commands';
import {createProtectedResource, deleteProtectedResource} from '@management-commands/protected-resources-management-commands';

setup();

let accessToken: string; // Admin token for management API
let testDomain: any;
let testUser1: any;
let testUser2: any;
let authzenApp: any; // Application for AuthZEN authentication
let authzenAppAccessToken: string; // Access token for AuthZEN API authentication
let openIdConfiguration: any; // OpenID configuration
let authEngine: AuthorizationEngine;
let storeId: string;
let authorizationModelId: string;

beforeAll(async () => {
  // 1. Get admin access token
  accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();

  // 2. Create unique test domain
  testDomain = await setupDomainForTest(uniqueName('openfga-authzen', true), { accessToken, waitForStart: true }).then((it) => it.domain);
  expect(testDomain).toBeDefined();

  // 3. Create OpenFGA store
  const storeName = `test-store-${Date.now()}`;
  const storeResponse = await fetch(`${process.env.AM_OPENFGA_URL}/stores`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name: storeName }),
  });
  expect(storeResponse.status).toBe(201);
  storeId = (await storeResponse.json()).id;

  // 4. Create authorization model
  const modelResponse = await fetch(`${process.env.AM_OPENFGA_URL}/stores/${storeId}/authorization-models`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(mcpAuthorizationModel),
  });
  expect(modelResponse.status).toBe(201);
  authorizationModelId = (await modelResponse.json()).authorization_model_id;

  // 5. Create authorization engine
  authEngine = await createAuthorizationEngine(testDomain.id, accessToken, {
    type: 'openfga',
    name: 'OpenFGA+AuthZen Test Engine',
    configuration: JSON.stringify({
      connectionUri: process.env.AM_INTERNAL_OPENFGA_URL,
      storeId,
      authorizationModelId,
      apiToken: '',
    }),
  });
  expect(authEngine?.id).toBeDefined();

  // 6. Create application for AuthZEN API authentication
  const appClientId = uniqueName('authzen-app-client', true);
  const appClientSecret = uniqueName('authzen-app-secret', true);
  authzenApp = await createApplication(testDomain.id, accessToken, {
    name: 'AuthZEN Test Application',
    type: 'SERVICE',
    clientId: appClientId,
    clientSecret: appClientSecret,
  }).then((app) =>
    updateApplication(
      testDomain.id,
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
  expect(authzenApp?.id).toBeDefined();
  expect(authzenApp?.settings?.oauth?.clientId).toBeDefined();
  expect(authzenApp?.settings?.oauth?.clientSecret).toBeDefined();

  // 7. Create test users
  const user1Data = buildTestUser(0);
  const user2Data = buildTestUser(1);
  testUser1 = await createUser(testDomain.id, accessToken, user1Data);
  testUser1.password = user1Data.password;
  testUser2 = await createUser(testDomain.id, accessToken, user2Data);
  testUser2.password = user2Data.password;

  // 8. Wait for users and application to sync to gateway before getting OIDC config
  await waitForNextSync(testDomain.id);

  // 9. Get OpenID configuration for token endpoint
  const openIdConfigResponse = await getWellKnownOpenIdConfiguration(testDomain.hrid).expect(200);
  openIdConfiguration = openIdConfigResponse.body;
  expect(openIdConfiguration?.token_endpoint).toBeDefined();

  // 10. Obtain access token for AuthZEN API using client_credentials grant
  authzenAppAccessToken = await requestClientCredentialsToken(
    authzenApp.settings.oauth.clientId,
    authzenApp.settings.oauth.clientSecret,
    openIdConfiguration,
  );
  expect(authzenAppAccessToken).toBeDefined();
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

describe('AuthZen API - Basic Authorization Checks', () => {
    const authzenServerId = 'authzen-server-' + Date.now();

    beforeAll(async () => {
      // Setup: user1 is owner, user2 is viewer
      await addTuple(testDomain.id, authEngine.id, accessToken, tupleFactory.ownerTuple(testUser1.username, authzenServerId));
      await addTuple(testDomain.id, authEngine.id, accessToken, tupleFactory.viewerTuple(testUser2.username, authzenServerId));
    });

    it('should return true when owner evaluates can_access via AuthZen API', async () => {
      const result = await evaluateAccess(testDomain.hrid, authzenAppAccessToken, authzenFactory.canAccess(testUser1.username, authzenServerId));
      expect(result.decision).toBe(true);
    });

    it('should return true when owner evaluates can_manage via AuthZen API', async () => {
      const result = await evaluateAccess(testDomain.hrid, authzenAppAccessToken, authzenFactory.canManage(testUser1.username, authzenServerId));
      expect(result.decision).toBe(true);
    });

    it('should return true for can_access and false for can_manage when viewer evaluates via AuthZen API', async () => {
      const accessResult = await evaluateAccess(testDomain.hrid, authzenAppAccessToken, authzenFactory.canAccess(testUser2.username, authzenServerId));
      expect(accessResult.decision).toBe(true);

      const manageResult = await evaluateAccess(testDomain.hrid, authzenAppAccessToken, authzenFactory.canManage(testUser2.username, authzenServerId));
      expect(manageResult.decision).toBe(false);
    });

    it('should return false when evaluating access without relationship tuple', async () => {
      const unauthorizedServerId = 'authzen-unauthorized-' + Date.now();
      const result = await evaluateAccess(testDomain.hrid, authzenAppAccessToken, authzenFactory.canAccess(testUser1.username, unauthorizedServerId));
      expect(result.decision).toBe(false);
    });
  });

  describe('AuthZen API - Request Validation', () => {
    const validationServerId = 'validation-server-' + Date.now();

    beforeAll(async () => {
      await addTuple(testDomain.id, authEngine.id, accessToken, tupleFactory.ownerTuple(testUser1.username, validationServerId));
    });

    it('should return 400 error when request is missing subject field', async () => {
      await evaluateAccessExpectError(
        testDomain.hrid,
        authzenAppAccessToken,
        {
          resource: { type: 'mcp_server', id: validationServerId },
          action: { name: 'can_access' },
        },
        400,
      );
    });

    it('should return 400 error when request has empty subject id', async () => {
      await evaluateAccessExpectError(
        testDomain.hrid,
        authzenAppAccessToken,
        {
          subject: { type: 'user', id: '' },
          resource: { type: 'mcp_server', id: validationServerId },
          action: { name: 'can_access' },
        },
        400,
      );
    });

    it('should return 400 error when request is missing resource field', async () => {
      await evaluateAccessExpectError(
        testDomain.hrid,
        authzenAppAccessToken,
        {
          subject: { type: 'user', id: testUser1.username },
          action: { name: 'can_access' },
        },
        400,
      );
    });

    it('should return 400 error when request is missing action field', async () => {
      await evaluateAccessExpectError(
        testDomain.hrid,
        authzenAppAccessToken,
        {
          subject: { type: 'user', id: testUser1.username },
          resource: { type: 'mcp_server', id: validationServerId },
        },
        400,
      );
    });

    it('should return 401 error when request is unauthenticated', async () => {
      await evaluateAccessUnauthenticated(testDomain.hrid, authzenFactory.canAccess(testUser1.username, validationServerId), 401);
    });
  });

  describe('AuthZen API - Properties Support', () => {
    const propsServerId = 'props-server-' + Date.now();

    beforeAll(async () => {
      await addTuple(testDomain.id, authEngine.id, accessToken, tupleFactory.ownerTuple(testUser1.username, propsServerId));
    });

    it('should successfully evaluate request when subject properties are included', async () => {
      const result = await evaluateAccess(
        testDomain.hrid,
        authzenAppAccessToken,
        authzenFactory.canAccess(testUser1.username, propsServerId, 'tool', {
          subject: { department: 'engineering', role: 'admin' },
        }),
      );
      expect(result.decision).toBe(true);
    });

    it('should successfully evaluate request when resource properties are included', async () => {
      const result = await evaluateAccess(
        testDomain.hrid,
        authzenAppAccessToken,
        authzenFactory.canAccess(testUser1.username, propsServerId, 'tool', {
          resource: { environment: 'production' },
        }),
      );
      expect(result.decision).toBe(true);
    });

    it('should successfully evaluate request when action properties are included', async () => {
      const result = await evaluateAccess(
        testDomain.hrid,
        authzenAppAccessToken,
        authzenFactory.canAccess(testUser1.username, propsServerId, 'tool', {
          action: { method: 'POST' },
        }),
      );
      expect(result.decision).toBe(true);
    });

    it('should successfully evaluate request when context properties are included', async () => {
      const result = await evaluateAccess(
        testDomain.hrid,
        authzenAppAccessToken,
        authzenFactory.canAccess(testUser1.username, propsServerId, 'tool', {
          context: { ip_address: '192.168.1.1', time_of_day: 'business_hours' },
        }),
      );
      expect(result.decision).toBe(true);
    });
  });

  describe('AuthZen API - Dynamic Permission Changes', () => {
    const dynamicServerId = 'dynamic-' + Date.now();

    it('should return false when evaluating access before relationship tuple exists', async () => {
      const result = await evaluateAccess(testDomain.hrid, authzenAppAccessToken, authzenFactory.canAccess(testUser1.username, dynamicServerId));
      expect(result.decision).toBe(false);
    });

    it('should return true when evaluating access after relationship tuple is added', async () => {
      await addTuple(testDomain.id, authEngine.id, accessToken, tupleFactory.ownerTuple(testUser1.username, dynamicServerId));

      const result = await evaluateAccess(testDomain.hrid, authzenAppAccessToken, authzenFactory.canAccess(testUser1.username, dynamicServerId));
      expect(result.decision).toBe(true);
    });

    it('should return false when evaluating access after relationship tuple is removed', async () => {
      await deleteTuple(testDomain.id, authEngine.id, accessToken, tupleFactory.ownerTuple(testUser1.username, dynamicServerId));

      const result = await evaluateAccess(testDomain.hrid, authzenAppAccessToken, authzenFactory.canAccess(testUser1.username, dynamicServerId));
      expect(result.decision).toBe(false);
    });

    it('should reflect permission changes when relationship transitions from owner to viewer', async () => {
      const roleChangeServerId = 'role-change-' + Date.now();

      // Start as owner
      await addTuple(testDomain.id, authEngine.id, accessToken, tupleFactory.ownerTuple(testUser1.username, roleChangeServerId));

      let result = await evaluateAccess(testDomain.hrid, authzenAppAccessToken, authzenFactory.canManage(testUser1.username, roleChangeServerId));
      expect(result.decision).toBe(true);

      // Change to viewer
      await deleteTuple(testDomain.id, authEngine.id, accessToken, tupleFactory.ownerTuple(testUser1.username, roleChangeServerId));
      await addTuple(testDomain.id, authEngine.id, accessToken, tupleFactory.viewerTuple(testUser1.username, roleChangeServerId));

      result = await evaluateAccess(testDomain.hrid, authzenAppAccessToken, authzenFactory.canManage(testUser1.username, roleChangeServerId));
      expect(result.decision).toBe(false);

      result = await evaluateAccess(testDomain.hrid, authzenAppAccessToken, authzenFactory.canAccess(testUser1.username, roleChangeServerId));
      expect(result.decision).toBe(true);
    });
  });

  describe('AuthZen API - Edge Cases', () => {
    const edgeServerId = 'edge-server-' + Date.now();

    beforeAll(async () => {
      await addTuple(testDomain.id, authEngine.id, accessToken, tupleFactory.ownerTuple(testUser1.username, edgeServerId));
    });

    it('should successfully evaluate when subject id contains special characters', async () => {
      const specialServerId = 'special-server-' + Date.now();
      await addTuple(testDomain.id, authEngine.id, accessToken, tupleFactory.ownerTuple('user@example.com', specialServerId));

      const request = authzenFactory.canAccess('user@example.com', specialServerId);
      const result = await evaluateAccess(testDomain.hrid, authzenAppAccessToken, request);

      expect(result.decision).toBe(true);
    });

    it('should successfully evaluate when resource id contains special characters', async () => {
      const specialResourceId = 'server-with_special.chars-123';
      await addTuple(testDomain.id, authEngine.id, accessToken, tupleFactory.ownerTuple(testUser1.username, specialResourceId));

      const request = authzenFactory.canAccess(testUser1.username, specialResourceId);
      const result = await evaluateAccess(testDomain.hrid, authzenAppAccessToken, request);

      expect(result.decision).toBe(true);
    });

    it('should successfully evaluate when resource id is very long', async () => {
      const longResourceId = 'a'.repeat(200);
      await addTuple(testDomain.id, authEngine.id, accessToken, tupleFactory.ownerTuple(testUser1.username, longResourceId));

      const request = authzenFactory.canAccess(testUser1.username, longResourceId);
      const result = await evaluateAccess(testDomain.hrid, authzenAppAccessToken, request);

      expect(result.decision).toBe(true);
    });

    it('should return false when checking non-existent relation type', async () => {
      const request = authzenFactory.custom('user', testUser1.username, 'tool', edgeServerId, 'non_existent_relation');
      const result = await evaluateAccess(testDomain.hrid, authzenAppAccessToken, request);

      expect(result.decision).toBe(false);
    });
  });

  describe('AuthZen API - Bearer Token Authentication with Protected Resources', () => {
    let protectedResource: any;
    let protectedResourceAccessToken: string;

    beforeAll(async () => {
      // Given: Protected Resource exists
      protectedResource = await createProtectedResource(testDomain.id, accessToken, {
        name: 'AuthZen Test MCP Server',
        type: 'MCP_SERVER',
        resourceIdentifiers: [`resource://authzen-test-mcp-server-${Date.now()}`],
      });
      expect(protectedResource?.id).toBeDefined();
      expect(protectedResource?.clientId).toBeDefined();
      expect(protectedResource?.clientSecret).toBeDefined();

      // Wait for Protected Resource to sync to gateway
      await waitForNextSync(testDomain.id);

      // Obtain access token using client_credentials WITHOUT resource parameter
      // Token aud = Protected Resource clientId (for AuthZen PDP authentication, not resource access)
      protectedResourceAccessToken = await requestClientCredentialsToken(
        protectedResource.clientId,
        protectedResource.clientSecret,
        openIdConfiguration,
      );
    });

    afterAll(async () => {
      if (protectedResource?.id) {
        await deleteProtectedResource(testDomain.id, accessToken, protectedResource.id, 'MCP_SERVER');
      }
    });

    it('should authenticate using Protected Resource Bearer token for can_access evaluation', async () => {
      // Given: Protected Resource token with aud = Protected Resource clientId
      //        User has owner relationship to test server
      const testServerId = 'protected-resource-test-server-' + Date.now();
      await addTuple(testDomain.id, authEngine.id, accessToken, tupleFactory.ownerTuple(testUser1.username, testServerId));

      // When: AuthZen API is called with Protected Resource Bearer token for can_access action
      const result = await evaluateAccess(
        testDomain.hrid,
        protectedResourceAccessToken,
        authzenFactory.canAccess(testUser1.username, testServerId),
      );

      // Then: Request succeeds and returns true
      expect(result.decision).toBe(true);
    });

    it('should authenticate using Protected Resource Bearer token for can_manage evaluation (viewer cannot manage)', async () => {
      // Given: Protected Resource token with aud = Protected Resource clientId
      //        User has viewer relationship to test server (viewers cannot manage per authorization model)
      const testServerId = 'protected-resource-manage-test-' + Date.now();
      await addTuple(testDomain.id, authEngine.id, accessToken, tupleFactory.viewerTuple(testUser2.username, testServerId));

      // When: AuthZen API is called with Protected Resource Bearer token for can_manage action
      const result = await evaluateAccess(
        testDomain.hrid,
        protectedResourceAccessToken,
        authzenFactory.canManage(testUser2.username, testServerId),
      );

      // Then: Request succeeds but returns false (viewer cannot manage, only owner can)
      expect(result.decision).toBe(false);
    });
  });
