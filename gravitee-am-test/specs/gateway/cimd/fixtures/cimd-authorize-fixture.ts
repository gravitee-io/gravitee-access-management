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
  patchDomain,
  safeDeleteDomain,
  startDomain,
  waitForDomainStart,
} from '@management-commands/domain-management-commands';
import {
  createApplication,
  patchApplication,
  updateApplication,
} from '@management-commands/application-management-commands';
import { performGet } from '@gateway-commands/oauth-oidc-commands';
import { uniqueName } from '@utils-commands/misc';
import { Application } from '@management-models/Application';
import { Domain } from '@management-models/Domain';
import { PatchCIMDSettings } from '@management-models/PatchCIMDSettings';
import { Fixture } from '../../../test-fixture';

export const CIMD_REDIRECT_URI = 'https://client.example.com/callback';
const PRE_REGISTERED_URL_CLIENT_ID = 'http://wiremock:8080/cimd/static-one';

export type CimdAuthorizeProfile = 'ENABLED_BASE' | 'ENABLED_TIMEOUT' | 'ENABLED_MAXSIZE' | 'DISABLED_BASE';


const PROFILE_SETTINGS: Record<CimdAuthorizeProfile, PatchCIMDSettings> = {
  ENABLED_BASE: {
    enabled: true,
    allowUnsecuredHttpUri: true,
    allowPrivateIpAddress: true,
    allowedDomains: [],
    fetchTimeoutMs: 1500,
    maxResponseSizeKb: 10,
  },
  ENABLED_TIMEOUT: {
    enabled: true,
    allowUnsecuredHttpUri: true,
    allowPrivateIpAddress: true,
    allowedDomains: [],
    fetchTimeoutMs: 1,
    maxResponseSizeKb: 10,
  },
  ENABLED_MAXSIZE: {
    enabled: true,
    allowUnsecuredHttpUri: true,
    allowPrivateIpAddress: true,
    allowedDomains: [],
    fetchTimeoutMs: 1500,
    maxResponseSizeKb: 1,
  },
  DISABLED_BASE: {
    enabled: false,
    allowUnsecuredHttpUri: true,
    allowPrivateIpAddress: true,
    allowedDomains: [],
    fetchTimeoutMs: 1500,
    maxResponseSizeKb: 10,
  },
};

type OAuthAuthorizeError = {
  error: string | null;
  errorDescription: string | null;
  location: string;
};

export interface CimdAuthorizeFixture extends Fixture {
  profile: CimdAuthorizeProfile;
  accessToken: string;
  domain: Domain;
  openIdConfiguration: any;
  redirectUri: string;
  templateApplication: Application;
  preRegisteredApplication: Application;
  preRegisteredUrlApplication: Application;
  buildClientId: (clientId: string) => string;
  buildAuthorizeUrl: (clientId: string, redirectUri?: string) => string;
  authorize: (clientId: string, redirectUri?: string) => Promise<any>;
  readOAuthError: (response: any) => OAuthAuthorizeError;
  expectInvalidClientMetadata: (response: any, messagePart: string) => void;
  expectInvalidRequest: (response: any, messagePart: string) => void;
  expectLoginRedirect: (response: any) => void;
}

const buildClientId = (profile: CimdAuthorizeProfile, scenario: string): string =>
  `http://wiremock:8080/cimd/${profile}/${scenario}`;

const buildAuthorizeUrl = (endpoint: string, clientId: string, redirectUri: string): string => {
  const params = new URLSearchParams({
    response_type: 'code',
    client_id: clientId,
    redirect_uri: redirectUri,
    scope: 'openid',
    state: 'cimd-state',
  });

  return `${endpoint}?${params.toString()}`;
};

const readOAuthError = (response: any): OAuthAuthorizeError => {
  const location = response.headers.location as string;
  expect(location).toBeDefined();
  const parsed = new URL(location, process.env.AM_GATEWAY_URL);

  return {
    location,
    error: parsed.searchParams.get('error'),
    errorDescription: parsed.searchParams.get('error_description'),
  };
};

async function createOAuthApplication(domainId: string, accessToken: string, namePrefix: string, clientId?: string): Promise<Application> {
  const created = await createApplication(domainId, accessToken, {
    name: uniqueName(namePrefix, true),
    type: 'WEB',
    clientId,
    redirectUris: [CIMD_REDIRECT_URI],
  });

  return updateApplication(
    domainId,
    accessToken,
    {
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
    created.id,
  );
}

export const setupCimdAuthorizeFixture = async (profile: CimdAuthorizeProfile): Promise<CimdAuthorizeFixture> => {
  let domain: Domain | null = null;
  let accessToken: string | null = null;

  try {
    accessToken = await requestAdminAccessToken();
    expect(accessToken).toBeDefined();

    domain = await createDomain(
      accessToken,
      uniqueName(`cimd-${profile.toLowerCase()}`, true),
      `CIMD authorize ${profile}`,
    );
    expect(domain.id).toBeDefined();

    const templateApplication = await createOAuthApplication(domain.id, accessToken, 'cimd-template');
    await patchApplication(domain.id, accessToken, { template: true }, templateApplication.id);

    const cimdSettings = {
      ...PROFILE_SETTINGS[profile],
      templateId: templateApplication.id,
    };

    await patchDomain(domain.id, accessToken, {
      oidc: {
        cimdSettings,
      },
    });

    const preRegisteredApplication = await createOAuthApplication(domain.id, accessToken, 'cimd-pre-registered');
    const preRegisteredUrlApplication = await createOAuthApplication(
      domain.id,
      accessToken,
      'cimd-pre-registered-url',
      PRE_REGISTERED_URL_CLIENT_ID,
    );

    await startDomain(domain.id, accessToken);
    const startedDomain = await waitForDomainStart(domain);

    const authorize = async (clientId: string, redirectUri = CIMD_REDIRECT_URI) =>
      performGet(buildAuthorizeUrl(startedDomain.oidcConfig.authorization_endpoint, clientId, redirectUri)).expect(302);

    const expectInvalidClientMetadata = (response: any, messagePart: string): void => {
      const error = readOAuthError(response);
      expect(error.error).toBe('invalid_client_metadata');
      expect(error.errorDescription).toContain(messagePart);
    };

    const expectInvalidRequest = (response: any, messagePart: string): void => {
      const error = readOAuthError(response);
      expect(error.error).toBe('invalid_request');
      expect(error.errorDescription).toContain(messagePart);
    };

    const expectLoginRedirect = (response: any): void => {
      const location = response.headers.location as string;
      expect(location).toBeDefined();
      expect(location).toContain('/login');
      expect(location).not.toContain('error=');
    };

    return {
      profile,
      accessToken,
      domain: startedDomain.domain,
      openIdConfiguration: startedDomain.oidcConfig,
      redirectUri: CIMD_REDIRECT_URI,
      templateApplication,
      preRegisteredApplication,
      preRegisteredUrlApplication,
      buildClientId: (clientId: string) => buildClientId(profile, clientId),
      buildAuthorizeUrl: (clientId: string, redirectUri = CIMD_REDIRECT_URI) =>
        buildAuthorizeUrl(startedDomain.oidcConfig.authorization_endpoint, clientId, redirectUri),
      authorize,
      readOAuthError,
      expectInvalidClientMetadata,
      expectInvalidRequest,
      expectLoginRedirect,
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
