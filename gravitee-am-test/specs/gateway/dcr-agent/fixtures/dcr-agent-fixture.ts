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
import { Domain } from '@management-models/Domain';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createDomain, patchDomain, safeDeleteDomain, startDomain, waitForDomainStart } from '@management-commands/domain-management-commands';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { getBase64BasicAuth } from '@gateway-commands/utils';
import { uniqueName } from '@utils-commands/misc';
import faker from 'faker';
import { Fixture } from '../../../test-fixture';

export interface DcrAgentFixture extends Fixture {
  domain: Domain;
  accessToken: string;
  cleanup: () => Promise<void>;
  registerAgent: (body: Record<string, any>) => Promise<any>;
  getTokenWithClientCredentials: (clientId: string, clientSecret: string) => Promise<any>;
}

export const DCR_AGENT_TEST = {
  DOMAIN_NAME_PREFIX: 'dcr-agent',
  REDIRECT_URI: 'https://example.com/callback',
} as const;

export const setupDcrAgentFixture = async (): Promise<DcrAgentFixture> => {
  let domain: Domain | null = null;
  let accessToken: string | null = null;

  try {
    accessToken = await requestAdminAccessToken();
    expect(accessToken).toBeDefined();

    // Create domain (not started yet)
    domain = await createDomain(accessToken, uniqueName(DCR_AGENT_TEST.DOMAIN_NAME_PREFIX, true), faker.company.catchPhraseDescriptor());
    expect(domain).toBeDefined();

    // Enable open DCR BEFORE starting — initial sync picks up everything
    domain = await patchDomain(domain.id, accessToken, {
      oidc: {
        clientRegistrationSettings: {
          allowLocalhostRedirectUri: true,
          allowHttpSchemeRedirectUri: true,
          allowWildCardRedirectUri: true,
          isDynamicClientRegistrationEnabled: true,
          isOpenDynamicClientRegistrationEnabled: true,
        },
      },
    });

    // Now start the domain and wait for OIDC readiness
    await startDomain(domain.id, accessToken);
    const domainResult = await waitForDomainStart(domain);
    domain = domainResult.domain;

    const gatewayUrl = process.env.AM_GATEWAY_URL;
    const domainHrid = domain.hrid;

    const registerAgent = async (body: Record<string, any>) => {
      return performPost(`${gatewayUrl}/${domainHrid}/oidc/register`, '', body, {
        'Content-type': 'application/json',
      });
    };

    const getTokenWithClientCredentials = async (clientId: string, clientSecret: string) => {
      return performPost(
        `${gatewayUrl}/${domainHrid}/oauth/token`,
        '',
        'grant_type=client_credentials',
        {
          'Content-type': 'application/x-www-form-urlencoded',
          Authorization: `Basic ${getBase64BasicAuth(clientId, clientSecret)}`,
        },
      );
    };

    const cleanup = async () => {
      if (domain?.id && accessToken) {
        await safeDeleteDomain(domain.id, accessToken);
      }
    };

    return {
      domain,
      accessToken,
      cleanup,
      registerAgent,
      getTokenWithClientCredentials,
    };
  } catch (error) {
    if (domain?.id && accessToken) {
      try {
        await safeDeleteDomain(domain.id, accessToken);
      } catch (cleanupError) {
        console.error('Failed to cleanup domain after setup failure:', cleanupError);
      }
    }
    throw error;
  }
};
