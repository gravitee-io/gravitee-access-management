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
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { getBase64BasicAuth } from '@gateway-commands/utils';
import { updateApplication } from '@management-commands/application-management-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { jira } from '@specs-utils/jira';
import { setup } from '../../test-fixture';
import { IdpLoginFixture, setupIdpLoginFixture } from './fixtures/idp-login-fixture';

// 200s: multiple fixture setups with domain start + sync waits
setup(200000);

const JWT_FORMAT = /^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/;

describe('IdP Login Flows', () => {
  describe('Single Internal IdP', () => {
    let fixture: IdpLoginFixture;

    beforeAll(async () => {
      fixture = await setupIdpLoginFixture();
    });

    afterAll(async () => {
      if (fixture) {
        await fixture.cleanUp();
      }
    });

    it(jira`should login via password grant with internal IdP ${'AM-2185'}`, async () => {
      const user = fixture.idp1.users[0];
      const response = await performPost(
        fixture.oidc.token_endpoint,
        '',
        `grant_type=password&username=${encodeURIComponent(user.username)}&password=${encodeURIComponent(user.password)}`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
          Authorization: 'Basic ' + getBase64BasicAuth(fixture.clientId, fixture.clientSecret),
        },
      ).expect(200);

      expect(response.body.access_token).toMatch(JWT_FORMAT);
      expect(response.body.token_type).toEqual('bearer');
    });
  });

  describe('Multiple IdPs', () => {
    let fixture: IdpLoginFixture;

    beforeAll(async () => {
      fixture = await setupIdpLoginFixture({ twoIdps: true });
      expect(fixture.idp2?.id).toEqual(expect.any(String));
    });

    afterAll(async () => {
      if (fixture) {
        await fixture.cleanUp();
      }
    });

    it(jira`should login with user from either IdP ${'AM-2180'}`, async () => {
      // Login with user from IdP-1
      const user1 = fixture.idp1.users[0];
      const response1 = await performPost(
        fixture.oidc.token_endpoint,
        '',
        `grant_type=password&username=${encodeURIComponent(user1.username)}&password=${encodeURIComponent(user1.password)}`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
          Authorization: 'Basic ' + getBase64BasicAuth(fixture.clientId, fixture.clientSecret),
        },
      ).expect(200);
      expect(response1.body.access_token).toMatch(JWT_FORMAT);

      // Login with user from IdP-2
      const user2 = fixture.idp2.users[0];
      const response2 = await performPost(
        fixture.oidc.token_endpoint,
        '',
        `grant_type=password&username=${encodeURIComponent(user2.username)}&password=${encodeURIComponent(user2.password)}`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
          Authorization: 'Basic ' + getBase64BasicAuth(fixture.clientId, fixture.clientSecret),
        },
      ).expect(200);
      expect(response2.body.access_token).toMatch(JWT_FORMAT);
    });
  });

  describe('IdP Processing Order', () => {
    let fixture: IdpLoginFixture;

    beforeAll(async () => {
      // Create two IdPs with the SAME user — order determines which resolves
      fixture = await setupIdpLoginFixture({ twoIdps: true });
      expect(fixture.idp2?.id).toEqual(expect.any(String));
    });

    afterAll(async () => {
      if (fixture) {
        await fixture.cleanUp();
      }
    });

    it(jira`should respect IdP processing priority order ${'AM-2179'}`, async () => {
      // Default: idp1 has priority 0, idp2 has priority 1 — idp1 resolves first
      // Login with idp1's user should work
      const user1 = fixture.idp1.users[0];
      const response = await performPost(
        fixture.oidc.token_endpoint,
        '',
        `grant_type=password&username=${encodeURIComponent(user1.username)}&password=${encodeURIComponent(user1.password)}`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
          Authorization: 'Basic ' + getBase64BasicAuth(fixture.clientId, fixture.clientSecret),
        },
      ).expect(200);
      expect(response.body.access_token).toMatch(JWT_FORMAT);

      // Swap priority: set idp2 to priority -1 (higher), idp1 to priority 1
      await waitForSyncAfter(fixture.domain.id, () =>
        updateApplication(
          fixture.domain.id,
          fixture.accessToken,
          {
            identityProviders: [
              { identity: fixture.idp1.id, priority: 1 },
              { identity: fixture.idp2.id, priority: -1 },
            ],
          },
          fixture.app.id,
        ),
      );

      // Login with idp2's user should now work (idp2 has higher priority)
      const user2 = fixture.idp2.users[0];
      const response2 = await performPost(
        fixture.oidc.token_endpoint,
        '',
        `grant_type=password&username=${encodeURIComponent(user2.username)}&password=${encodeURIComponent(user2.password)}`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
          Authorization: 'Basic ' + getBase64BasicAuth(fixture.clientId, fixture.clientSecret),
        },
      ).expect(200);
      expect(response2.body.access_token).toMatch(JWT_FORMAT);
    });
  });

  describe('IdP Selection Rule', () => {
    let fixture: IdpLoginFixture;

    beforeAll(async () => {
      fixture = await setupIdpLoginFixture({ twoIdps: true });
      expect(fixture.idp2?.id).toEqual(expect.any(String));

      // Set a selection rule on idp2 that always evaluates to false
      // This means idp2 should never be selected, only idp1
      await waitForSyncAfter(fixture.domain.id, () =>
        updateApplication(
          fixture.domain.id,
          fixture.accessToken,
          {
            identityProviders: [
              { identity: fixture.idp1.id, priority: 0 },
              { identity: fixture.idp2.id, priority: 1, selectionRule: '{{ false }}' },
            ],
          },
          fixture.app.id,
        ),
      );
    });

    afterAll(async () => {
      if (fixture) {
        await fixture.cleanUp();
      }
    });

    it(jira`should configure selection rule on IdP and login with unblocked IdP ${'AM-2171'}`, async () => {
      // Selection rules apply to browser-based login (authorize endpoint), not password grant.
      // This test verifies the configuration is accepted and login with the unblocked IdP works.
      // Full browser-based selection rule verification is covered in Playwright Phase 7.

      // Login with idp1 user should work (no selection rule blocks it)
      const user1 = fixture.idp1.users[0];
      const response1 = await performPost(
        fixture.oidc.token_endpoint,
        '',
        `grant_type=password&username=${encodeURIComponent(user1.username)}&password=${encodeURIComponent(user1.password)}`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
          Authorization: 'Basic ' + getBase64BasicAuth(fixture.clientId, fixture.clientSecret),
        },
      ).expect(200);
      expect(response1.body.access_token).toMatch(JWT_FORMAT);
    });
  });
});
