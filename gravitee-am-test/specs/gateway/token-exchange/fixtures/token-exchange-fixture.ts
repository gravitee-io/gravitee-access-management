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
import {
  createDomain,
  safeDeleteDomain,
  startDomain,
  waitFor,
  waitForOidcReady
} from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { buildCreateAndTestUser } from '@management-commands/user-management-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { introspectToken as introspectOidcToken, performPost } from '@gateway-commands/oauth-oidc-commands';
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

  // Helper method to obtain actor tokens (alias for obtainSubjectToken for readability)
  obtainActorToken: (scope?: string) => Promise<SubjectTokens>;

  // Helper method to introspect token
  introspectToken: (token: string) => Promise<any>;

  // Helper method to revoke token and wait for introspection consistency window
  revokeToken: (token: string, tokenTypeHint: 'access_token' | 'refresh_token') => Promise<void>;

  // Helper method to exchange token (impersonation/delegation)
  exchangeToken: (
    subjectToken: string,
    subjectTokenType: 'access_token' | 'refresh_token',
    actorToken?: string,
  ) => Promise<string>;
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
  DEFAULT_ALLOWED_REQUESTED_TOKEN_TYPES: [
    'urn:ietf:params:oauth:token-type:access_token',
    'urn:ietf:params:oauth:token-type:id_token',
  ],
  DEFAULT_ALLOWED_ACTOR_TOKEN_TYPES: [
    'urn:ietf:params:oauth:token-type:access_token',
    'urn:ietf:params:oauth:token-type:id_token',
    'urn:ietf:params:oauth:token-type:jwt',
  ],
  // 0 = unlimited (no depth check)
  DEFAULT_MAX_DELEGATION_DEPTH: 0,
  REDIRECT_URI: 'https://gravitee.io/callback',
};

/**
 * Token exchange settings for domain configuration
 */
interface TokenExchangeSettingsConfig {
  allowedSubjectTokenTypes?: string[];
  allowImpersonation?: boolean;
  allowDelegation?: boolean;
  allowedActorTokenTypes?: string[];
  allowedRequestedTokenTypes?: string[];
  maxDelegationDepth?: number;
}

/**
 * Enable token exchange for a domain
 */
async function enableTokenExchange(
  domainId: string,
  token: string,
  config: TokenExchangeSettingsConfig = {},

): Promise<void> {
  const {
    allowedSubjectTokenTypes = TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_SUBJECT_TOKEN_TYPES,
    allowImpersonation = true,
    allowDelegation = false,
    allowedActorTokenTypes = TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_ACTOR_TOKEN_TYPES,
    allowedRequestedTokenTypes = TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_REQUESTED_TOKEN_TYPES,
    maxDelegationDepth = TOKEN_EXCHANGE_TEST.DEFAULT_MAX_DELEGATION_DEPTH,
  } = config;

  const tokenExchangeSettings: Record<string, unknown> = {
    enabled: true,
    allowedSubjectTokenTypes,
    allowImpersonation,
    allowedRequestedTokenTypes,
    allowDelegation,
  };

  if (allowDelegation) {
    tokenExchangeSettings.allowedActorTokenTypes = allowedActorTokenTypes;
    tokenExchangeSettings.maxDelegationDepth = maxDelegationDepth;
  }

  await request(getDomainManagerUrl(domainId))
    .patch('')
    .set('Authorization', `Bearer ${token}`)
    .set('Content-Type', 'application/json')
    .send({ tokenExchangeSettings })
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
  // Delegation settings
  allowImpersonation?: boolean;
  allowDelegation?: boolean;
  allowedActorTokenTypes?: string[];
  maxDelegationDepth?: number;
  allowedRequestedTokenTypes?: string[];
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
      allowedRequestedTokenTypes = TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_REQUESTED_TOKEN_TYPES,
      allowImpersonation = true,
      allowDelegation = false,
      allowedActorTokenTypes = TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_ACTOR_TOKEN_TYPES,
      maxDelegationDepth = TOKEN_EXCHANGE_TEST.DEFAULT_MAX_DELEGATION_DEPTH,
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
    await enableTokenExchange(createdDomain.id, accessToken, {
      allowedSubjectTokenTypes,
      allowedRequestedTokenTypes,
      allowImpersonation,
      allowDelegation,
      allowedActorTokenTypes,
      maxDelegationDepth,
    });

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

    const introspectToken = (token: string): Promise<any> => introspectOidcToken(oidc.introspection_endpoint, token, basicAuth);

    const revokeToken = async (token: string, tokenTypeHint: 'access_token' | 'refresh_token'): Promise<void> => {
      await performPost(
        oidc.revocation_endpoint,
        '',
        `token=${token}&token_type_hint=${tokenTypeHint}`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
          Authorization: `Basic ${basicAuth}`,
        },
      ).expect(200);

      await waitFor(11000); // because of OFFLINE_VERIFICATION_TIMER_SECONDS = 10 seconds
    };

    const exchangeToken = async (
      subjectToken: string,
      subjectTokenType: 'access_token' | 'refresh_token',
      actorToken?: string,
    ): Promise<string> => {
      const actorParams = actorToken
        ? `&actor_token=${actorToken}&actor_token_type=urn:ietf:params:oauth:token-type:access_token`
        : '';

      const response = await performPost(
        oidc.token_endpoint,
        '',
        `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
        `&subject_token=${subjectToken}` +
        `&subject_token_type=urn:ietf:params:oauth:token-type:${subjectTokenType}` +
        actorParams,
        {
          'Content-type': 'application/x-www-form-urlencoded',
          Authorization: `Basic ${basicAuth}`,
        },
      ).expect(200);

      const exchangedToken = response.body.access_token;
      expect(exchangedToken).toBeDefined();
      return exchangedToken;
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
      obtainActorToken: obtainSubjectToken,
      introspectToken,
      revokeToken,
      exchangeToken,
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
