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

import { expect } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import {
  createDomain,
  DomainOidcConfig,
  patchDomain,
  safeDeleteDomain,
  startDomain,
  waitForDomainStart,
} from '@management-commands/domain-management-commands';
import { createApplication, patchApplication, updateApplication } from '@management-commands/application-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { buildCreateAndTestUser } from '@management-commands/user-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { Application } from '@management-models/Application';
import { Domain } from '@management-models/Domain';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { Fixture } from '../../../test-fixture';
import { CIMD_REDIRECT_URI } from './cimd-authorize-fixture';

/** WireMock scenario path; metadata must declare matching client_id and grant_types including refresh_token. */
export const CIMD_REFRESH_SCENARIO = 'valid-refresh';

export interface CimdRefreshTokenFixture extends Fixture {
  accessToken: string;
  domain: Domain;
  oidc: DomainOidcConfig;
  templateApplication: Application;
  clientId: string;
  redirectUri: string;
  user: { username: string; password: string; id: string };
  revokeToken: (token: string, tokenTypeHint: 'access_token' | 'refresh_token') => Promise<void>;
}

export const setupCimdRefreshTokenFixture = async (): Promise<CimdRefreshTokenFixture> => {
  let domain: Domain | null = null;
  let accessToken: string | null = null;

  try {
    accessToken = await requestAdminAccessToken();
    expect(accessToken).toBeDefined();

    domain = await createDomain(
      accessToken,
      uniqueName('cimd-refresh-token', true),
      'CIMD refresh token flow',
    );
    expect(domain.id).toBeDefined();

    const idpSet = await getAllIdps(domain.id, accessToken);
    const defaultIdp = idpSet.values().next().value;
    expect(defaultIdp?.id).toBeDefined();

    const user = await buildCreateAndTestUser(domain.id, accessToken, 0);
    expect(user.username).toBeDefined();
    expect(user.id).toBeDefined();
    const userPassword = user.password;
    expect(userPassword).toBeDefined();

    const created = await createApplication(domain.id, accessToken, {
      name: uniqueName('cimd-template-refresh', true),
      type: 'WEB',
      redirectUris: [CIMD_REDIRECT_URI],
    });

    const templateApplication = await updateApplication(
      domain.id,
      accessToken,
      {
        identityProviders: new Set([{ identity: defaultIdp.id, priority: 0 }]),
        settings: {
          oauth: {
            redirectUris: [CIMD_REDIRECT_URI],
            grantTypes: ['authorization_code', 'refresh_token'],
            responseTypes: ['code'],
            scopeSettings: [{ scope: 'openid', defaultScope: true }],
          },
          advanced: { skipConsent: true },
        },
      },
      created.id,
    );

    await patchApplication(domain.id, accessToken, { template: true }, templateApplication.id);

    await patchDomain(domain.id, accessToken, {
      oidc: {
        cimdSettings: {
          enabled: true,
          allowUnsecuredHttpUri: true,
          allowPrivateIpAddress: true,
          allowedDomains: [],
          fetchTimeoutMs: 1500,
          maxResponseSizeKb: 10,
          cacheTtlSeconds: 3600,
          cacheMaxEntries: 500,
          templateId: templateApplication.id,
        },
      },
    });

    await startDomain(domain.id, accessToken);
    const startedDomain = await waitForDomainStart(domain);

    const clientId = `http://wiremock:8080/cimd/ENABLED_BASE/${CIMD_REFRESH_SCENARIO}`;

    return {
      accessToken,
      domain: startedDomain.domain,
      oidc: startedDomain.oidcConfig,
      templateApplication,
      clientId,
      redirectUri: CIMD_REDIRECT_URI,
      user: { username: user.username, password: userPassword as string, id: user.id },
      revokeToken: async (token: string, tokenTypeHint: 'access_token' | 'refresh_token') => {
        await performPost(
          startedDomain.oidcConfig.revocation_endpoint,
          '',
          new URLSearchParams({
            token,
            token_type_hint: tokenTypeHint,
            client_id: clientId,
          }).toString(),
          {
            'Content-Type': 'application/x-www-form-urlencoded',
          },
        ).expect(200);
      },
      cleanUp: async () => {
        await safeDeleteDomain(domain?.id, accessToken);
      },
    };
  } catch (error) {
    if (domain?.id && accessToken) {
      await safeDeleteDomain(domain.id, accessToken);
    }
    throw error;
  }
};
