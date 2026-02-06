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
import { afterAll, beforeAll, expect } from '@jest/globals';
import { patchDomain, waitForOidcReady } from '@management-commands/domain-management-commands';
import { waitForNextSync, waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { SelfAccountFixture, setupFixture } from './fixture/sef-account-fixture';
import { setup } from '../../test-fixture';

setup(200000);

const PATCH_DOMAIN_SEFLACCOUNT_SETTINGS = {
  selfServiceAccountManagementSettings: {
    enabled: true,
    resetPassword: {
      oldPasswordRequired: false,
      tokenAge: 0,
    },
  },
};

let fixture: SelfAccountFixture;

beforeAll(async () => {
  fixture = await setupFixture(PATCH_DOMAIN_SEFLACCOUNT_SETTINGS);
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('SelfAccount - Change Password', () => {
  let currentPassword: string;

  beforeAll(() => {
    // Initialize currentPassword after originalPassword is set in outer beforeAll
    // This ensures currentPassword is set before any tests run
    currentPassword = fixture.user.password;
    expect(currentPassword).toBeDefined();
    expect(currentPassword).toBe(fixture.user.password);
  });

  describe('With default settings', () => {
    describe('End User', () => {
      it('must be able to change his password', async () => {
        // Generate token with original password (user was created with this password)
        const tokenResponse = await performPost(
          fixture.oidc.token_endpoint,
          '',
          `grant_type=password&username=${fixture.user.username}&password=${fixture.user.password}`,
          {
            'Content-type': 'application/x-www-form-urlencoded',
            Authorization: 'Basic ' + applicationBase64Token(fixture.application),
          },
        ).expect(200);
        const endUserAccessToken = tokenResponse.body.access_token;

        currentPassword = 'SomeP@ssw0rd!';

        await performPost(
          `${process.env.AM_GATEWAY_URL}/${fixture.domain.hrid}/account/api/changePassword`,
          '',
          `{"password": "${currentPassword}"}`,
          {
            'Content-type': 'application/json',
            Authorization: `Bearer ${endUserAccessToken}`,
          },
        ).expect(204);

        // Wait for password change to sync to gateway
        await waitForNextSync(fixture.domain.id);
      });

      it('must be able to sign in using new password', async () => {
        const response = await performPost(
          fixture.oidc.token_endpoint,
          '',
          `grant_type=password&username=${fixture.user.username}&password=${currentPassword}`,
          {
            'Content-type': 'application/x-www-form-urlencoded',
            Authorization: 'Basic ' + applicationBase64Token(fixture.application),
          },
        ).expect(200);
        expect(response.body.access_token).toBeDefined();
      });
    });
  });

  describe('When oldPassword is requried', () => {
    beforeAll(async () => {
      // Update domain settings once for this test group
      PATCH_DOMAIN_SEFLACCOUNT_SETTINGS.selfServiceAccountManagementSettings.resetPassword.oldPasswordRequired = true;
      await waitForSyncAfter(fixture.domain.id,
        () => patchDomain(fixture.domain.id, fixture.accessToken, PATCH_DOMAIN_SEFLACCOUNT_SETTINGS),
      );
      // Wait for HTTP routes to be live after domain redeploy
      await waitForOidcReady(fixture.domain.hrid, { timeoutMs: 5000, intervalMs: 200 });
    });

    describe('EndUser ', () => {
      it('must NOT be able to change his password if old one is missing', async () => {
        // Generate token with current password
        const tokenResponse = await performPost(
          fixture.oidc.token_endpoint,
          '',
          `grant_type=password&username=${fixture.user.username}&password=${currentPassword}`,
          {
            'Content-type': 'application/x-www-form-urlencoded',
            Authorization: 'Basic ' + applicationBase64Token(fixture.application),
          },
        ).expect(200);
        const endUserAccessToken = tokenResponse.body.access_token;

        await performPost(
          `${process.env.AM_GATEWAY_URL}/${fixture.domain.hrid}/account/api/changePassword`,
          '',
          `{"password": "TestWithoutOldPassword"}`,
          {
            'Content-type': 'application/json',
            Authorization: `Bearer ${endUserAccessToken}`,
          },
        ).expect(400);
      });

      it('must NOT be able to change his password if old one is invalid', async () => {
        // Generate token with current password
        const tokenResponse = await performPost(
          fixture.oidc.token_endpoint,
          '',
          `grant_type=password&username=${fixture.user.username}&password=${currentPassword}`,
          {
            'Content-type': 'application/x-www-form-urlencoded',
            Authorization: 'Basic ' + applicationBase64Token(fixture.application),
          },
        ).expect(200);
        const endUserAccessToken = tokenResponse.body.access_token;

        await performPost(
          `${process.env.AM_GATEWAY_URL}/${fixture.domain.hrid}/account/api/changePassword`,
          '',
          `{"password": "TestPassword123!", "oldPassword": "invalidpassword"}`,
          {
            'Content-type': 'application/json',
            Authorization: `Bearer ${endUserAccessToken}`,
          },
        ).expect(401);
      });

      it('must be able to change his password if old one is present', async () => {
        // Generate token with current password
        const tokenResponse = await performPost(
          fixture.oidc.token_endpoint,
          '',
          `grant_type=password&username=${fixture.user.username}&password=${currentPassword}`,
          {
            'Content-type': 'application/x-www-form-urlencoded',
            Authorization: 'Basic ' + applicationBase64Token(fixture.application),
          },
        ).expect(200);
        const endUserAccessToken = tokenResponse.body.access_token;

        const oldPassword = currentPassword;
        currentPassword = 'TestW1thOldP@ssw0rd';

        await performPost(
          `${process.env.AM_GATEWAY_URL}/${fixture.domain.hrid}/account/api/changePassword`,
          '',
          `{"password": "${currentPassword}", "oldPassword": "${oldPassword}"}`,
          {
            'Content-type': 'application/json',
            Authorization: `Bearer ${endUserAccessToken}`,
          },
        ).expect(204);

        // Wait for password change to sync to gateway
        await waitForNextSync(fixture.domain.id);
      });

      it('must be able to sign in using new password', async () => {
        const response = await performPost(
          fixture.oidc.token_endpoint,
          '',
          `grant_type=password&username=${fixture.user.username}&password=${currentPassword}`,
          {
            'Content-type': 'application/x-www-form-urlencoded',
            Authorization: 'Basic ' + applicationBase64Token(fixture.application),
          },
        ).expect(200);
        expect(response.body.access_token).toBeDefined();
      });
    });
  });

  describe('When access_token is too old', () => {
    beforeAll(async () => {
      // Update domain settings once for this test group
      PATCH_DOMAIN_SEFLACCOUNT_SETTINGS.selfServiceAccountManagementSettings.resetPassword.tokenAge = 10;
      await waitForSyncAfter(fixture.domain.id,
        () => patchDomain(fixture.domain.id, fixture.accessToken, PATCH_DOMAIN_SEFLACCOUNT_SETTINGS),
      );
      // Wait for HTTP routes to be live after domain redeploy
      await waitForOidcReady(fixture.domain.hrid, { timeoutMs: 5000, intervalMs: 200 });
    });

    describe('EndUser ', () => {
      it('must be able to change his password if old one is present', async () => {
        // Generate token and wait for it to age (tokenAge = 10 seconds)
        const tokenResponse = await performPost(
          fixture.oidc.token_endpoint,
          '',
          `grant_type=password&username=${fixture.user.username}&password=${currentPassword}`,
          {
            'Content-type': 'application/x-www-form-urlencoded',
            Authorization: 'Basic ' + applicationBase64Token(fixture.application),
          },
        ).expect(200);
        const endUserAccessToken = tokenResponse.body.access_token;

        // Wait for token to become older than tokenAge (10 seconds)
        await new Promise((resolve) => setTimeout(resolve, 11000)); // 10 seconds + 1 second buffer

        const oldPassword = currentPassword;
        await performPost(
          `${process.env.AM_GATEWAY_URL}/${fixture.domain.hrid}/account/api/changePassword`,
          '',
          `{"password": "fake", "oldPassword": "${oldPassword}"}`,
          {
            'Content-type': 'application/json',
            Authorization: `Bearer ${endUserAccessToken}`,
          },
        ).expect(401);
      });

      it('must be able to sign in using current password', async () => {
        const response = await performPost(
          fixture.oidc.token_endpoint,
          '',
          `grant_type=password&username=${fixture.user.username}&password=${currentPassword}`,
          {
            'Content-type': 'application/x-www-form-urlencoded',
            Authorization: 'Basic ' + applicationBase64Token(fixture.application),
          },
        ).expect(200);
        expect(response.body.access_token).toBeDefined();
      });
    });
  });
});
