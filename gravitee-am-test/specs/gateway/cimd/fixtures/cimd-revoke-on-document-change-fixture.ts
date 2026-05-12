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
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { getBase64BasicAuth } from '@gateway-commands/utils';
import { Application } from '@management-models/Application';
import { Domain } from '@management-models/Domain';
import { Fixture } from '../../../test-fixture';
import { CIMD_REDIRECT_URI } from './cimd-authorize-fixture';

export const CIMD_REVOKE_ON_CHANGE_SCENARIO = 'revoke-on-change';

export interface CimdRevokeOnDocumentChangeFixture extends Fixture {
  accessToken: string;
  domain: Domain;
  oidc: DomainOidcConfig;
  templateApplication: Application;
  preRegisteredApplication: Application;
  clientId: string;
  redirectUri: string;
  user: { username: string; password: string; id: string };
  introspectToken: (token: string) => Promise<{ active: boolean }>;
}

export const setupCimdRevokeOnDocumentChangeFixture = async (): Promise<CimdRevokeOnDocumentChangeFixture> => {
  let domain: Domain | null = null;
  let accessToken: string | null = null;

  try {
    accessToken = await requestAdminAccessToken();
    expect(accessToken).toBeDefined();

    domain = await createDomain(
      accessToken,
      uniqueName('cimd-revoke-doc-change', true),
      'CIMD revoke on document change',
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

    const createdTemplate = await createApplication(domain.id, accessToken, {
      name: uniqueName('cimd-template-revoke', true),
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
            grantTypes: ['authorization_code'],
            responseTypes: ['code'],
            scopeSettings: [{ scope: 'openid', defaultScope: true }],
          },
          advanced: { skipConsent: true },
        },
      },
      createdTemplate.id,
    );

    await patchApplication(domain.id, accessToken, { template: true }, templateApplication.id);

    const createdPreRegistered = await createApplication(domain.id, accessToken, {
      name: uniqueName('cimd-pre-registered-revoke', true),
      type: 'WEB',
      redirectUris: [CIMD_REDIRECT_URI],
    });

    const preRegisteredApplication = await updateApplication(
      domain.id,
      accessToken,
      {
        identityProviders: new Set([{ identity: defaultIdp.id, priority: 0 }]),
        settings: {
          oauth: {
            redirectUris: [CIMD_REDIRECT_URI],
            grantTypes: ['authorization_code'],
            responseTypes: ['code'],
            scopeSettings: [{ scope: 'openid', defaultScope: true }],
          },
        },
      },
      createdPreRegistered.id,
    );
    preRegisteredApplication.settings.oauth.clientSecret = createdPreRegistered.settings.oauth.clientSecret;

    await patchDomain(domain.id, accessToken, {
      oidc: {
        cimdSettings: {
          enabled: true,
          allowUnsecuredHttpUri: true,
          allowPrivateIpAddress: true,
          allowedDomains: [],
          fetchTimeoutMs: 1500,
          maxResponseSizeKb: 10,
          cacheTtlSeconds: 1,
          cacheMaxEntries: 500,
          revokeOnDocumentChange: true,
          templateId: templateApplication.id,
        },
      },
    });

    await startDomain(domain.id, accessToken);
    const startedDomain = await waitForDomainStart(domain);

    const clientId = `http://wiremock:8080/cimd/ENABLED_BASE/${CIMD_REVOKE_ON_CHANGE_SCENARIO}`;

    const introspectToken = async (token: string): Promise<{ active: boolean }> => {
      const callerClientId = preRegisteredApplication.settings.oauth.clientId;
      const callerClientSecret = preRegisteredApplication.settings.oauth.clientSecret;
      const response = await performPost(
        startedDomain.oidcConfig.introspection_endpoint,
        '',
        `token=${token}&token_type_hint=access_token`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
          Authorization: 'Basic ' + getBase64BasicAuth(callerClientId, callerClientSecret),
        },
      ).expect(200);
      return response.body;
    };

    return {
      accessToken,
      domain: startedDomain.domain,
      oidc: startedDomain.oidcConfig,
      templateApplication,
      preRegisteredApplication,
      clientId,
      redirectUri: CIMD_REDIRECT_URI,
      user: { username: user.username, password: userPassword as string, id: user.id },
      introspectToken,
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
