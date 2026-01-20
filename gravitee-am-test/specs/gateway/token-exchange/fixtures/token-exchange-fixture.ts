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
import { createDomain, safeDeleteDomain, startDomain, waitForOidcReady } from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { buildCreateAndTestUser } from '@management-commands/user-management-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { uniqueName } from '@utils-commands/misc';
import { getDomainManagerUrl } from '@management-commands/service/utils';
import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
import { IdentityProvider } from '@management-models/IdentityProvider';
import { User } from '@management-models/User';
import request from 'supertest';

/**
 * OIDC configuration returned from well-known endpoint
 */
export interface OidcConfiguration {
  issuer: string;
  authorization_endpoint: string;
  token_endpoint: string;
  introspection_endpoint: string;
  userinfo_endpoint: string;
  revocation_endpoint: string;
  end_session_endpoint: string;
  jwks_uri: string;
  grant_types_supported: string[];
  response_types_supported: string[];
  subject_types_supported: string[];
  id_token_signing_alg_values_supported: string[];
  scopes_supported: string[];
  token_endpoint_auth_methods_supported: string[];
  claims_supported: string[];
  code_challenge_methods_supported: string[];
}

/**
 * Subject tokens returned from password grant
 */
export interface SubjectTokens {
  accessToken: string;
  refreshToken?: string;
  idToken?: string;
  expiresIn: number;
}

/**
 * Fixture interface for token exchange tests
 */
export interface TokenExchangeFixture {
  domain: Domain;
  application: Application;
  user: User;
  defaultIdp: IdentityProvider;
  oidc: OidcConfiguration;
  basicAuth: string;
  accessToken: string;
  cleanup: () => Promise<void>;

  // Helper method to obtain subject tokens
  obtainSubjectToken: (scope?: string) => Promise<SubjectTokens>;
}

/**
 * Test constants
 */
export const TOKEN_EXCHANGE_TEST = {
  DOMAIN_NAME_PREFIX: 'token-exchange',
  DOMAIN_DESCRIPTION: 'Token exchange domain',
  CLIENT_NAME: 'token-exchange-client',
  USER_PASSWORD: 'SomeP@ssw0rd',
  DEFAULT_SCOPES: [
    { scope: 'openid', defaultScope: true },
    { scope: 'profile', defaultScope: true },
    { scope: 'offline_access', defaultScope: false },
  ],
  DEFAULT_GRANT_TYPES: ['password', 'refresh_token', 'urn:ietf:params:oauth:grant-type:token-exchange'],
  DEFAULT_ALLOWED_SUBJECT_TOKEN_TYPES: [
    'urn:ietf:params:oauth:token-type:access_token',
    'urn:ietf:params:oauth:token-type:refresh_token',
    'urn:ietf:params:oauth:token-type:id_token',
    'urn:ietf:params:oauth:token-type:jwt',
  ],
  REDIRECT_URI: 'https://gravitee.io/callback',
};

/**
 * Enable token exchange for a domain
 */
async function enableTokenExchange(
  domainId: string,
  token: string,
  allowedSubjectTokenTypes: string[] = TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_SUBJECT_TOKEN_TYPES,
): Promise<void> {
  await request(getDomainManagerUrl(domainId))
    .patch('')
    .set('Authorization', `Bearer ${token}`)
    .set('Content-Type', 'application/json')
    .send({
      tokenExchangeSettings: {
        enabled: true,
        allowedSubjectTokenTypes,
      },
    })
    .expect(200);
}

/**
 * Configuration options for token exchange fixture
 */
export interface TokenExchangeFixtureConfig {
  domainNamePrefix?: string;
  domainDescription?: string;
  clientName?: string;
  grantTypes?: string[];
  scopes?: { scope: string; defaultScope: boolean }[];
  allowedSubjectTokenTypes?: string[];
}

/**
 * Setup token exchange test fixture
 */
export const setupTokenExchangeFixture = async (
  config: TokenExchangeFixtureConfig = {},
): Promise<TokenExchangeFixture> => {
  let domain: Domain | null = null;
  let accessToken: string | null = null;

  try {
    // Setup configuration with defaults
    const {
      domainNamePrefix = TOKEN_EXCHANGE_TEST.DOMAIN_NAME_PREFIX,
      domainDescription = TOKEN_EXCHANGE_TEST.DOMAIN_DESCRIPTION,
      clientName = TOKEN_EXCHANGE_TEST.CLIENT_NAME,
      grantTypes = TOKEN_EXCHANGE_TEST.DEFAULT_GRANT_TYPES,
      scopes = TOKEN_EXCHANGE_TEST.DEFAULT_SCOPES,
      allowedSubjectTokenTypes = TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_SUBJECT_TOKEN_TYPES,
    } = config;

    // Get admin access token
    accessToken = await requestAdminAccessToken();
    expect(accessToken).toBeDefined();

    // Create domain
    const createdDomain = await createDomain(accessToken, uniqueName(domainNamePrefix, true), domainDescription);
    expect(createdDomain).toBeDefined();
    expect(createdDomain.id).toBeDefined();
    domain = createdDomain;

    // Get default IDP
    const idpSet = await getAllIdps(createdDomain.id, accessToken);
    const defaultIdp = idpSet.values().next().value;
    expect(defaultIdp).toBeDefined();

    // Enable token exchange
    await enableTokenExchange(createdDomain.id, accessToken, allowedSubjectTokenTypes);

    // Create application
    const application = await createTestApp(uniqueName(clientName, true), createdDomain, accessToken, 'WEB', {
      settings: {
        oauth: {
          redirectUris: [TOKEN_EXCHANGE_TEST.REDIRECT_URI],
          grantTypes,
          scopeSettings: scopes,
        },
      },
      identityProviders: new Set([{ identity: defaultIdp.id, priority: 0 }]),
    });
    expect(application).toBeDefined();
    expect(application.id).toBeDefined();

    // Start domain
    const startedDomain = await startDomain(createdDomain.id, accessToken);
    expect(startedDomain).toBeDefined();
    domain = startedDomain;

    // Wait for OIDC configuration
    const oidcResponse = await waitForOidcReady(startedDomain.hrid, { timeoutMs: 30000, intervalMs: 500 });
    expect(oidcResponse.status).toBe(200);
    const oidc = oidcResponse.body as OidcConfiguration;

    // Create user
    const user = await buildCreateAndTestUser(startedDomain.id, accessToken, 0);
    expect(user).toBeDefined();

    // Generate basic auth
    const basicAuth = applicationBase64Token(application);

    // Helper method to obtain subject tokens
    const obtainSubjectToken = async (scope: string = 'openid%20profile%20offline_access'): Promise<SubjectTokens> => {
      const response = await performPost(
        oidc.token_endpoint,
        '',
        `grant_type=password&username=${user.username}&password=${TOKEN_EXCHANGE_TEST.USER_PASSWORD}&scope=${scope}`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
          Authorization: `Basic ${basicAuth}`,
        },
      ).expect(200);

      return {
        accessToken: response.body.access_token,
        refreshToken: response.body.refresh_token,
        idToken: response.body.id_token,
        expiresIn: response.body.expires_in,
      };
    };

    // Cleanup function
    const cleanup = async () => {
      if (domain?.id && accessToken) {
        await safeDeleteDomain(domain.id, accessToken);
      }
    };

    return {
      domain: startedDomain,
      application,
      user,
      defaultIdp,
      oidc,
      basicAuth,
      accessToken,
      obtainSubjectToken,
      cleanup,
    };
  } catch (error) {
    // Cleanup on failure
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
