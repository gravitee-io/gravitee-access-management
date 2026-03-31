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
  allowHttpLocalhostRedirects,
  createDomain,
  patchDomain,
  startDomain,
  waitForDomainSync,
  safeDeleteDomain,
  waitForOAuthAuthorizeRedirectsToLogin,
  waitForOidcReady,
} from '@management-commands/domain-management-commands';
import { createIdp, deleteIdp } from '@management-commands/idp-management-commands';
import { createTestApp } from '@utils-commands/application-commands';
import type { Application } from '@management-models/Application';
import type { Domain } from '@management-models/Domain';
import type { IdentityProvider } from '@management-models/IdentityProvider';

import { getGatewayBaseUrl, quietly, uniqueTestName } from '../utils/fixture-helpers';
import { API_USER_PASSWORD } from '../utils/test-constants';
import { REDIRECT_URI } from '../utils/mfa-helpers';

export type ExternalOidcBundle = {
  adminToken: string;
  clientDomain: Domain;
  providerDomain: Domain;
  clientApp: Application;
  providerApp: Application;
  clientOidcIdp: IdentityProvider;
  /** User on the provider (IdP) inline store — sign in on provider login page. */
  providerUser: { username: string; password: string };
  /** e.g. http://localhost:8092/{clientHrid} */
  clientGatewayUrl: string;
};

export const test = base.extend<{ externalOidcBundle: ExternalOidcBundle; hideLoginForm: boolean }>({
  hideLoginForm: [false, { option: true }],

  externalOidcBundle: async ({ hideLoginForm }, use) => {
    const adminToken = await requestAdminAccessToken();
    const gatewayBase = getGatewayBaseUrl();

    const clientDomain = await quietly(() =>
      createDomain(adminToken, uniqueTestName('pw-oidc-client'), 'Phase 7 AM-2207 client domain'),
    );

    let providerDomain = await quietly(() =>
      createDomain(adminToken, uniqueTestName('pw-oidc-provider'), 'Phase 7 AM-2207 provider domain'),
    );
    providerDomain = await quietly(() => allowHttpLocalhostRedirects(providerDomain, adminToken));

    await quietly(() => deleteIdp(providerDomain.id, adminToken, `default-idp-${providerDomain.id}`));

    const providerUsername = uniqueTestName('oidc_prov_user');
    const providerInlineIdp = await quietly(() =>
      createIdp(providerDomain.id, adminToken, {
        external: false,
        type: 'inline-am-idp',
        domainWhitelist: [],
        name: uniqueTestName('provider-inline'),
        configuration: JSON.stringify({
          users: [
            {
              firstname: 'Ext',
              lastname: 'User',
              username: providerUsername,
              email: `${providerUsername}@example.com`,
              password: API_USER_PASSWORD,
            },
          ],
        }),
      }),
    );

    const providerCallback = `${gatewayBase}/${clientDomain.hrid}/login/callback`;

    const providerApp = await quietly(() =>
      createTestApp(uniqueTestName('pw-provider-app'), providerDomain, adminToken, 'WEB', {
        settings: {
          oauth: {
            redirectUris: [providerCallback],
            grantTypes: ['authorization_code', 'password', 'refresh_token'],
            scopeSettings: [{ scope: 'openid', defaultScope: true }],
          },
          advanced: { skipConsent: true },
        },
        identityProviders: new Set([{ identity: providerInlineIdp.id, priority: 0 }]),
      }),
    );

    await quietly(() => deleteIdp(clientDomain.id, adminToken, `default-idp-${clientDomain.id}`));

    const clientOidcIdp = await quietly(() =>
      createIdp(clientDomain.id, adminToken, {
        name: uniqueTestName('oauth2-generic-am'),
        external: true,
        type: 'oauth2-generic-am-idp',
        configuration: JSON.stringify({
          clientId: providerApp.settings.oauth.clientId,
          clientSecret: providerApp.settings.oauth.clientSecret,
          clientAuthenticationMethod: 'client_secret_basic',
          wellKnownUri: `${gatewayBase}/${providerDomain.hrid}/oidc/.well-known/openid-configuration`,
          responseType: 'code',
          encodeRedirectUri: false,
          useIdTokenForUserInfo: false,
          signature: 'RSA_RS256',
          publicKeyResolver: 'GIVEN_KEY',
          scopes: ['openid'],
          connectTimeout: 10000,
          idleTimeout: 10000,
          maxPoolSize: 200,
          storeOriginalTokens: false,
          codeChallengeMethod: 'S256',
          responseMode: 'default',
        }),
      }),
    );

    const clientApp = await quietly(() =>
      createTestApp(uniqueTestName('pw-client-app'), clientDomain, adminToken, 'WEB', {
        settings: {
          oauth: {
            redirectUris: [REDIRECT_URI],
            grantTypes: ['authorization_code'],
            scopeSettings: [
              { scope: 'openid', defaultScope: true },
              { scope: 'profile', defaultScope: true },
            ],
          },
          advanced: { skipConsent: true },
          ...(hideLoginForm ? { login: { hideForm: true, inherited: false } } : {}),
        },
        identityProviders: new Set([{ identity: clientOidcIdp.id, priority: 0 }]),
      }),
    );

    await quietly(() => startDomain(providerDomain.id, adminToken));
    await quietly(() => startDomain(clientDomain.id, adminToken));
    await quietly(() => waitForDomainSync(providerDomain.id));
    await quietly(() => waitForDomainSync(clientDomain.id));
    await waitForOidcReady(providerDomain.hrid, { timeoutMs: 45000, intervalMs: 500 });
    await waitForOidcReady(clientDomain.hrid, { timeoutMs: 45000, intervalMs: 500 });

    // Nudge a sync after all IdP + app wiring so the gateway builds social authorize URLs before /login.
    await quietly(() =>
      waitForSyncAfter(
        clientDomain.id,
        () => patchDomain(clientDomain.id, adminToken, { description: `Phase 7 AM-2207 gateway sync ${Date.now()}` }),
        { timeoutMillis: 90_000, intervalMillis: 500 },
      ),
    );
    await waitForOAuthAuthorizeRedirectsToLogin(clientDomain.hrid, clientApp.settings.oauth.clientId, REDIRECT_URI, {
      timeoutMs: 90_000,
      intervalMs: 500,
    });

    const bundle: ExternalOidcBundle = {
      adminToken,
      clientDomain,
      providerDomain,
      clientApp,
      providerApp,
      clientOidcIdp,
      providerUser: { username: providerUsername, password: API_USER_PASSWORD },
      clientGatewayUrl: `${gatewayBase}/${clientDomain.hrid}`,
    };

    await use(bundle);

    await quietly(() => safeDeleteDomain(clientDomain.id, adminToken));
    await quietly(() => safeDeleteDomain(providerDomain.id, adminToken));
  },
});

export { expect } from '@playwright/test';
export { buildAuthorizeUrl, submitLogin, handleConsentIfPresent } from '../utils/mfa-helpers';
