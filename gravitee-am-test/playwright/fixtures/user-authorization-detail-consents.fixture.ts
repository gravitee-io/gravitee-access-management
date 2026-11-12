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
import type { Page } from '@playwright/test';
import { test as base } from './base.fixture';

import {
  createDomain,
  safeDeleteDomain,
  startDomain,
  waitForDomainSync,
  waitForOidcReady,
} from '@management-commands/domain-management-commands';
import { createIdp, defaultIdpId, deleteIdp } from '@management-commands/idp-management-commands';
import { createAuthorizationDetailType } from '@management-commands/authorization-detail-type-management-commands';
import { listUsers } from '@management-commands/user-management-commands';
import { createTestApp } from '@utils-commands/application-commands';
import type { Application } from '@management-models/Application';
import type { Domain } from '@management-models/Domain';

import { getGatewayBaseUrl, oauthWebSettings, quietly, uniqueTestName } from '../utils/fixture-helpers';
import { API_USER_PASSWORD } from '../utils/test-constants';
import { REDIRECT_URI, reachOAuthAuthorizationCallback, submitLogin } from '../utils/mfa-helpers';

export const PAYMENT_TYPE = 'payment_initiation';
export const PAYMENT_TYPE_NAME = 'Payment initiation';
export const PAYMENT_DETAIL = { type: PAYMENT_TYPE, instructedAmount: { currency: 'EUR', amount: '10.00' } };
export const ACCOUNT_TYPE = 'account_information';
export const ACCOUNT_DETAIL = { type: ACCOUNT_TYPE, actions: ['list_accounts'] };
const USERNAME = 'pw_rar_consent_user';

export type UserAuthorizationDetailConsentsBundle = {
  domain: Domain;
  gatewayUrl: string;
  /** Enables the payment and account types, and requests no scope by default. */
  detailApp: Application;
  /** Enables no type. */
  scopeApp: Application;
  /** The user's id, which exists once the user has signed in on the gateway. */
  userId: () => Promise<string>;
};

/** Signs the user in on the gateway and approves every consent screen until the callback. */
export async function signInAndConsent(page: Page, gatewayUrl: string, app: Application, params: Record<string, string>): Promise<void> {
  const query = new URLSearchParams({
    response_type: 'code',
    client_id: app.settings.oauth.clientId,
    redirect_uri: REDIRECT_URI,
    ...params,
  });
  await page.goto(`${gatewayUrl}/oauth/authorize?${query.toString()}`);
  await submitLogin(page, USERNAME, API_USER_PASSWORD);
  await reachOAuthAuthorizationCallback(page);
}

export const test = base.extend<{ consentsBundle: UserAuthorizationDetailConsentsBundle }>({
  consentsBundle: async ({ adminToken }, use) => {
    const domain = await quietly(() =>
      createDomain(adminToken, uniqueTestName('pw-rar-consents'), 'Playwright user authorization detail consents'),
    );
    await quietly(() => deleteIdp(domain.id, adminToken, defaultIdpId(domain.id)));
    const idp = await quietly(() =>
      createIdp(domain.id, adminToken, {
        external: false,
        type: 'inline-am-idp',
        domainWhitelist: [],
        name: uniqueTestName('pw-rar-consents-idp'),
        configuration: JSON.stringify({ users: [{ firstname: 'Rar', lastname: 'User', username: USERNAME, password: API_USER_PASSWORD }] }),
      }),
    );
    await quietly(() => createAuthorizationDetailType(domain.id, adminToken, { type: PAYMENT_TYPE, name: PAYMENT_TYPE_NAME }));
    await quietly(() => createAuthorizationDetailType(domain.id, adminToken, { type: ACCOUNT_TYPE, name: 'Account information' }));
    const identityProviders = new Set([{ identity: idp.id, priority: 0 }]);
    const detailApp = await quietly(() =>
      createTestApp(uniqueTestName('pw-rar-detail-app'), domain, adminToken, 'WEB', {
        identityProviders,
        settings: {
          oauth: {
            redirectUris: [REDIRECT_URI],
            grantTypes: ['authorization_code'],
            scopeSettings: [{ scope: 'openid', defaultScope: false }],
            authorizationDetailTypes: [{ type: PAYMENT_TYPE }, { type: ACCOUNT_TYPE }],
          },
        },
      }),
    );
    const scopeApp = await quietly(() =>
      createTestApp(uniqueTestName('pw-rar-scope-app'), domain, adminToken, 'WEB', {
        identityProviders,
        settings: { oauth: oauthWebSettings(REDIRECT_URI) },
      }),
    );

    await quietly(() => startDomain(domain.id, adminToken));
    await quietly(() => waitForDomainSync(domain.id));
    await waitForOidcReady(domain.hrid, { timeoutMs: 30000, intervalMs: 500 });

    await use({
      domain,
      gatewayUrl: `${getGatewayBaseUrl()}/${domain.hrid}`,
      detailApp,
      scopeApp,
      userId: async () => (await listUsers(domain.id, adminToken, USERNAME)).data[0].id,
    });

    await quietly(() => safeDeleteDomain(domain.id, adminToken));
  },
});

export { expect } from '@playwright/test';
