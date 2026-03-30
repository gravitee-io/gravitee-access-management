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
import { test as base } from '@playwright/test';

import crossFetch from 'cross-fetch';
globalThis.fetch = crossFetch;

import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import {
  createDomain,
  patchDomain,
  startDomain,
  waitForDomainSync,
  safeDeleteDomain,
  waitForOAuthAuthorizeRedirectsToLogin,
  waitForOidcReady,
} from '@management-commands/domain-management-commands';
import { createIdp, deleteIdp, updateIdp } from '@management-commands/idp-management-commands';
import { createRole } from '@management-commands/role-management-commands';
import { createTestApp } from '@utils-commands/application-commands';
import type { Application } from '@management-models/Application';
import type { Domain } from '@management-models/Domain';
import type { IdentityProvider } from '@management-models/IdentityProvider';

import { quietly, uniqueTestName } from '../utils/fixture-helpers';
import { API_USER_PASSWORD } from '../utils/test-constants';
import { REDIRECT_URI } from '../utils/mfa-helpers';

const MAPPED_FIRSTNAME = 'MapMe';

export type RoleMapperFlowFixtures = {
  adminToken: string;
  /** Client domain (login + user provisioning). */
  mapperDomain: Domain;
  /** Plaintext login credentials (never read from IdP API responses — configuration may be hashed). */
  mapperCredentials: { username: string; password: string };
  mapperIdp: IdentityProvider;
  /** Domain started and OIDC ready (matches Jest idp-mapper: IdP while domain is up, then app under sync). */
  mapperDomainStarted: true;
  mapperApp: Application;
  mappedRoleName: string;
  gatewayUser: { username: string; password: string };
  gatewayUrl: string;
};

export const test = base.extend<RoleMapperFlowFixtures>({
  adminToken: async ({}, use) => {
    await use(await requestAdminAccessToken());
  },

  mapperDomain: async ({ adminToken }, use) => {
    const name = uniqueTestName('pw-role-mapper');
    const domain = await quietly(() =>
      createDomain(adminToken, name, 'Phase 7 AM-2219 — IdP role mapper'),
    );
    await use(domain);
    await quietly(() => safeDeleteDomain(domain.id, adminToken));
  },

  mappedRoleName: async ({ adminToken, mapperDomain }, use) => {
    const roleName = uniqueTestName('pw_mapped_role').replace(/[^a-zA-Z0-9_-]/g, '_');
    await quietly(() =>
      createRole(mapperDomain.id, adminToken, {
        name: roleName,
        description: 'Playwright AM-2219 role mapper target',
        assignableType: 'DOMAIN',
      }),
    );
    await use(roleName);
  },

  mapperCredentials: async ({}, use) => {
    const username = `pwmap${Date.now()}`;
    await use({ username, password: API_USER_PASSWORD });
  },

  mapperIdp: async ({ adminToken, mapperDomain, mappedRoleName, mapperCredentials }, use) => {
    await quietly(() => deleteIdp(mapperDomain.id, adminToken, `default-idp-${mapperDomain.id}`));

    const idp = await quietly(() =>
      createIdp(mapperDomain.id, adminToken, {
        external: false,
        type: 'inline-am-idp',
        domainWhitelist: [],
        name: uniqueTestName('inline-mapper-idp'),
        configuration: JSON.stringify({
          users: [
            {
              firstname: MAPPED_FIRSTNAME,
              lastname: 'Test',
              username: mapperCredentials.username,
              email: `${mapperCredentials.username}@example.com`,
              password: mapperCredentials.password,
            },
          ],
        }),
      }),
    );

    // Role mapper before domain start (gateway not polling yet). Sync is applied when the domain starts below.
    await quietly(() =>
      updateIdp(mapperDomain.id, adminToken, {
        name: idp.name,
        type: idp.type,
        configuration: idp.configuration,
        mappers: {},
        roleMapper: { [mappedRoleName]: [`firstname=${MAPPED_FIRSTNAME}`] },
        groupMapper: {},
      }, idp.id),
    );

    await use(idp);
  },

  mapperDomainStarted: async ({ adminToken, mapperDomain, mapperIdp }, use) => {
    if (!mapperIdp?.id) {
      throw new Error('mapperDomainStarted: IdP not initialised');
    }
    await quietly(() => startDomain(mapperDomain.id, adminToken));
    await quietly(() => waitForDomainSync(mapperDomain.id, { timeoutMillis: 90_000, intervalMillis: 500 }));
    await waitForOidcReady(mapperDomain.hrid, { timeoutMs: 45_000, intervalMs: 500 });
    await use(true);
  },

  mapperApp: async ({ adminToken, mapperDomain, mapperIdp, mapperDomainStarted }, use) => {
    if (!mapperDomainStarted) {
      throw new Error('mapperDomainStarted fixture did not run');
    }
    const app = await quietly(() =>
      waitForSyncAfter(
        mapperDomain.id,
        () =>
          createTestApp(uniqueTestName('pw-mapper-app'), mapperDomain, adminToken, 'WEB', {
            identityProviders: new Set([{ identity: mapperIdp.id, priority: -1 }]),
            settings: {
              oauth: {
                redirectUris: [REDIRECT_URI],
                grantTypes: ['authorization_code'],
                scopeSettings: [
                  { scope: 'openid', defaultScope: true },
                  { scope: 'profile', defaultScope: true },
                ],
              },
              login: { identifierFirstEnabled: false, inherited: false },
              advanced: { skipConsent: true },
            },
          }),
        { timeoutMillis: 90_000, intervalMillis: 500 },
      ),
    );
    await use(app);
  },

  gatewayUser: async ({ mapperCredentials }, use) => {
    await use(mapperCredentials);
  },

  gatewayUrl: async ({ adminToken, mapperDomain, mapperApp, mapperDomainStarted }, use) => {
    if (!mapperDomainStarted) {
      throw new Error('mapperDomainStarted fixture did not run');
    }
    if (!mapperApp?.id) {
      throw new Error('gatewayUrl: app not initialised');
    }
    await quietly(() =>
      waitForSyncAfter(
        mapperDomain.id,
        () => patchDomain(mapperDomain.id, adminToken, { description: `Phase 7 AM-2219 post-app sync ${Date.now()}` }),
        { timeoutMillis: 90_000, intervalMillis: 500 },
      ),
    );
    await waitForOAuthAuthorizeRedirectsToLogin(mapperDomain.hrid, mapperApp.settings.oauth.clientId, REDIRECT_URI, {
      timeoutMs: 90_000,
      intervalMs: 500,
    });
    // Root cause of "No security domain matches the request URI" in the browser: `AM_GATEWAY_URL` with a trailing
    // slash produced `http://host:8092//{hrid}/oauth/...`, which does not match any domain path.
    const gatewayBase = (process.env.AM_GATEWAY_URL || 'http://localhost:8092').replace(/\/$/, '');
    await use(`${gatewayBase}/${mapperDomain.hrid}`);
  },
});

export { expect } from '@playwright/test';
export { buildAuthorizeUrl, submitLogin, handleConsentIfPresent } from '../utils/mfa-helpers';
