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

import { requestAdminAccessToken } from '../../api/commands/management/token-management-commands';
import { createDomain, startDomain, waitForDomainSync, safeDeleteDomain, waitForOidcReady } from '../../api/commands/management/domain-management-commands';
import { waitForNextSync } from '../../api/commands/gateway/monitoring-commands';
import { getAllIdps } from '../../api/commands/management/idp-management-commands';
import { buildCreateAndTestUser } from '../../api/commands/management/user-management-commands';
import { createTestApp } from '../../api/commands/utils/application-commands';
import { applicationBase64Token } from '../../api/commands/gateway/utils';
import { Domain, Application, User } from '../../api/management/models';

import {
  OidcConfiguration,
  SubjectTokens,
  patchDomainRaw,
  obtainSubjectToken as obtainToken,
  exchangeToken as doExchange,
  introspectToken as doIntrospect,
  revokeToken as doRevoke,
  ExchangeTokenParams,
  REVOCATION_WAIT_MS,
  waitMs,
} from '../utils/token-exchange-helpers';
import { quietly, uniqueTestName } from '../utils/fixture-helpers';
import { API_USER_PASSWORD } from '../utils/test-constants';

/* ------------------------------------------------------------------ */
/*  Token Exchange test constants                                      */
/* ------------------------------------------------------------------ */

export const TOKEN_EXCHANGE_DEFAULTS = {
  GRANT_TYPES: ['password', 'refresh_token', 'urn:ietf:params:oauth:grant-type:token-exchange'],
  SCOPES: [
    { scope: 'openid', defaultScope: true },
    { scope: 'profile', defaultScope: true },
    { scope: 'offline_access', defaultScope: false },
  ],
  ALLOWED_SUBJECT_TOKEN_TYPES: [
    'urn:ietf:params:oauth:token-type:access_token',
    'urn:ietf:params:oauth:token-type:refresh_token',
    'urn:ietf:params:oauth:token-type:id_token',
    'urn:ietf:params:oauth:token-type:jwt',
  ],
  ALLOWED_REQUESTED_TOKEN_TYPES: [
    'urn:ietf:params:oauth:token-type:access_token',
    'urn:ietf:params:oauth:token-type:id_token',
  ],
  ALLOWED_ACTOR_TOKEN_TYPES: [
    'urn:ietf:params:oauth:token-type:access_token',
    'urn:ietf:params:oauth:token-type:id_token',
    'urn:ietf:params:oauth:token-type:jwt',
  ],
  REDIRECT_URI: 'https://gravitee.io/callback',
} as const;

/* ------------------------------------------------------------------ */
/*  Fixture types                                                      */
/* ------------------------------------------------------------------ */

export interface TokenExchangeConfig {
  domainNamePrefix?: string;
  grantTypes?: readonly string[];
  scopes?: readonly { scope: string; defaultScope: boolean }[];
  allowImpersonation?: boolean;
  allowDelegation?: boolean;
  maxDelegationDepth?: number;
  allowedSubjectTokenTypes?: readonly string[];
  allowedRequestedTokenTypes?: readonly string[];
  allowedActorTokenTypes?: readonly string[];
  tokenExchangeScopeHandling?: string;
}

export type TokenExchangeFixtures = {
  tokenExchangeDomain: Domain;
  tokenExchangeApp: Application;
  tokenExchangeUser: User;
  oidcConfig: OidcConfiguration;
  basicAuth: string;
  teAdminToken: string;
  obtainSubjectToken: (scope?: string) => Promise<SubjectTokens>;
  doTokenExchange: (params: ExchangeTokenParams) => import('supertest').Test;
  doIntrospect: (token: string) => Promise<Record<string, unknown>>;
  doRevoke: (token: string, hint: 'access_token' | 'refresh_token') => Promise<void>;
  revokeAndWait: (token: string, hint: 'access_token' | 'refresh_token') => Promise<void>;
};

/* ------------------------------------------------------------------ */
/*  Fixture factory                                                    */
/* ------------------------------------------------------------------ */

export function createTokenExchangeFixture(config: TokenExchangeConfig = {}) {
  return base.extend<TokenExchangeFixtures>({
    teAdminToken: async ({}, use) => {
      const token = await requestAdminAccessToken();
      await use(token);
    },

    tokenExchangeDomain: async ({ teAdminToken }, use) => {
      const {
        domainNamePrefix = 'pw-te',
        allowImpersonation = true,
        allowDelegation = false,
        maxDelegationDepth = 1,
        allowedSubjectTokenTypes = TOKEN_EXCHANGE_DEFAULTS.ALLOWED_SUBJECT_TOKEN_TYPES,
        allowedRequestedTokenTypes = TOKEN_EXCHANGE_DEFAULTS.ALLOWED_REQUESTED_TOKEN_TYPES,
        allowedActorTokenTypes = TOKEN_EXCHANGE_DEFAULTS.ALLOWED_ACTOR_TOKEN_TYPES,
        tokenExchangeScopeHandling,
      } = config;

      const name = uniqueTestName(domainNamePrefix);
      const domain = await quietly(() => createDomain(teAdminToken, name, 'Token exchange test domain'));

      const teSettings: Record<string, unknown> = {
        enabled: true,
        allowedSubjectTokenTypes,
        allowedRequestedTokenTypes,
        allowImpersonation,
        allowDelegation,
      };
      if (allowDelegation) {
        teSettings.allowedActorTokenTypes = allowedActorTokenTypes;
        teSettings.maxDelegationDepth = maxDelegationDepth;
      }
      if (tokenExchangeScopeHandling) {
        teSettings.tokenExchangeOAuthSettings = { scopeHandling: tokenExchangeScopeHandling };
      }

      await quietly(async () => {
        await patchDomainRaw(domain.id, teAdminToken, { tokenExchangeSettings: teSettings }).expect(200);
      });

      await quietly(() => startDomain(domain.id, teAdminToken));
      await quietly(() => waitForDomainSync(domain.id));

      await use(domain);

      await quietly(() => safeDeleteDomain(domain.id, teAdminToken));
    },

    tokenExchangeApp: async ({ teAdminToken, tokenExchangeDomain }, use) => {
      const {
        grantTypes = TOKEN_EXCHANGE_DEFAULTS.GRANT_TYPES,
        scopes = TOKEN_EXCHANGE_DEFAULTS.SCOPES,
      } = config;

      const idpSet = await getAllIdps(tokenExchangeDomain.id, teAdminToken);
      const defaultIdp = idpSet.values().next().value;
      if (!defaultIdp) {
        throw new Error(`No identity providers found for domain "${tokenExchangeDomain.id}". Cannot create token exchange app without an IdP.`);
      }

      const app = await quietly(() =>
        createTestApp(uniqueTestName('pw-te-app'), tokenExchangeDomain, teAdminToken, 'WEB', {
          settings: {
            oauth: {
              redirectUris: [TOKEN_EXCHANGE_DEFAULTS.REDIRECT_URI],
              grantTypes: [...grantTypes],
              scopeSettings: scopes.map((s) => ({ ...s })),
            },
          },
          identityProviders: new Set([{ identity: defaultIdp.id, priority: 0 }]),
        }),
      );

      await use(app);
    },

    tokenExchangeUser: async ({ teAdminToken, tokenExchangeDomain }, use) => {
      const user = await quietly(() => buildCreateAndTestUser(tokenExchangeDomain.id, teAdminToken, 0));
      await use(user);
    },

    oidcConfig: async ({ tokenExchangeDomain, tokenExchangeApp, tokenExchangeUser }, use) => {
      // Depend on app/user fixtures to ensure they're created before syncing.
      // The domain was started in tokenExchangeDomain, but app/user were created after that sync.
      void tokenExchangeApp;
      void tokenExchangeUser;
      await waitForNextSync(tokenExchangeDomain.id);
      const oidcRes = await waitForOidcReady(tokenExchangeDomain.hrid, { timeoutMs: 30000, intervalMs: 500 });
      const oidc = oidcRes.body as OidcConfiguration;
      await use(oidc);
    },

    basicAuth: async ({ tokenExchangeApp }, use) => {
      await use(applicationBase64Token(tokenExchangeApp));
    },

    obtainSubjectToken: async ({ oidcConfig, basicAuth, tokenExchangeUser }, use) => {
      const fn = (scope?: string) =>
        obtainToken(
          oidcConfig.token_endpoint,
          basicAuth,
          tokenExchangeUser.username,
          API_USER_PASSWORD,
          scope || 'openid profile offline_access',
        );
      await use(fn);
    },

    doTokenExchange: async ({ oidcConfig, basicAuth }, use) => {
      const fn = (params: ExchangeTokenParams) => doExchange(oidcConfig.token_endpoint, basicAuth, params);
      await use(fn);
    },

    doIntrospect: async ({ oidcConfig, basicAuth }, use) => {
      const fn = (token: string) => doIntrospect(oidcConfig.introspection_endpoint, basicAuth, token);
      await use(fn);
    },

    doRevoke: async ({ oidcConfig, basicAuth }, use) => {
      const fn = (token: string, hint: 'access_token' | 'refresh_token') =>
        doRevoke(oidcConfig.revocation_endpoint, basicAuth, token, hint);
      await use(fn);
    },

    revokeAndWait: async ({ oidcConfig, basicAuth }, use) => {
      const fn = async (token: string, hint: 'access_token' | 'refresh_token') => {
        await doRevoke(oidcConfig.revocation_endpoint, basicAuth, token, hint);
        await waitMs(REVOCATION_WAIT_MS);
      };
      await use(fn);
    },
  });
}

export const test = createTokenExchangeFixture();
export { expect } from '@playwright/test';
