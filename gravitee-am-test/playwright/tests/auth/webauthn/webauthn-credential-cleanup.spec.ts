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
import {
  test,
  expect,
  loginAndRegisterWebAuthn,
  removeVirtualAuthenticator,
  VirtualAuthenticator,
} from '../../../fixtures/webauthn.fixture';
import { API_USER_PASSWORD } from '../../../utils/test-constants';
import {
  createUser,
  deleteUser,
  listUserCredentials,
} from '../../../../api/commands/management/user-management-commands';
import { uniqueTestName } from '../../../utils/fixture-helpers';

/**
 * AM-6085: WebAuthn credentials are not removed when a user is deleted.
 *
 * Verifies that after deleting a user, their WebAuthn credentials are cleaned up,
 * and a new user with the same username can register fresh credentials.
 */
test.describe('WebAuthn - Credential Cleanup on User Delete (AM-6085)', () => {
  test.use({ storageState: { cookies: [], origins: [] } });

  let auth: VirtualAuthenticator;

  test.afterEach(async () => {
    if (auth) {
      await removeVirtualAuthenticator(auth);
    }
  });

  test('user credentials exist after registration and are queryable via API (AM-6085)', async ({
    page,
    waApp,
    waUser,
    waAdminToken,
    waDomain,
    gatewayUrl,
  }) => {
    const clientId = waApp.settings.oauth.clientId;

    // Register a WebAuthn credential
    auth = await loginAndRegisterWebAuthn(page, gatewayUrl, clientId, waUser.username, API_USER_PASSWORD);

    // Verify credentials exist via management API
    const credentials = await listUserCredentials(waDomain.id, waAdminToken, waUser.id);
    expect(credentials.length).toBeGreaterThanOrEqual(1);
  });

  test('recreated user with same username can register new passwordless credential (AM-6085)', async ({
    page,
    waApp,
    waUser,
    waAdminToken,
    waDomain,
    gatewayUrl,
  }) => {
    const clientId = waApp.settings.oauth.clientId;
    const originalUsername = waUser.username;

    // Phase 1: Register a WebAuthn credential
    auth = await loginAndRegisterWebAuthn(page, gatewayUrl, clientId, originalUsername, API_USER_PASSWORD);

    // Verify credential was created
    const credsBefore = await listUserCredentials(waDomain.id, waAdminToken, waUser.id);
    expect(credsBefore.length).toBeGreaterThanOrEqual(1);

    // Phase 2: Delete the user
    await deleteUser(waDomain.id, waAdminToken, waUser.id);

    // Phase 3: Recreate user with the same username
    const newUser = await createUser(waDomain.id, waAdminToken, {
      firstName: 'WebAuthn',
      lastName: 'Recreated',
      email: `${uniqueTestName('wa-recreated')}@example.com`,
      username: originalUsername,
      password: API_USER_PASSWORD,
      preRegistration: false,
    });

    try {
      // Phase 4: The new user should have NO credentials
      const credsAfter = await listUserCredentials(waDomain.id, waAdminToken, newUser.id);
      expect(credsAfter.length).toEqual(0);

      // Phase 5: New user should be able to register a fresh credential
      await page.context().clearCookies();

      // Remove old virtual authenticator and create a fresh one
      await removeVirtualAuthenticator(auth);
      auth = await loginAndRegisterWebAuthn(page, gatewayUrl, clientId, originalUsername, API_USER_PASSWORD);

      // Verify the new user now has a credential
      const credsNew = await listUserCredentials(waDomain.id, waAdminToken, newUser.id);
      expect(credsNew.length).toBeGreaterThanOrEqual(1);
    } finally {
      // Clean up the recreated user (original waUser was already deleted)
      await deleteUser(waDomain.id, waAdminToken, newUser.id).catch(() => {});
    }
  });
});
