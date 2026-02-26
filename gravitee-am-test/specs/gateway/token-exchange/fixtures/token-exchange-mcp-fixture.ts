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
  waitForDomainStart,
  waitForDomainSync,
} from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { buildCreateAndTestUser } from '@management-commands/user-management-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { createProtectedResource } from '@management-commands/protected-resources-management-commands';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token, getBase64BasicAuth } from '@gateway-commands/utils';
import { uniqueName } from '@utils-commands/misc';
import { getDomainManagerUrl } from '@management-commands/service/utils';
import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
import { IdentityProvider } from '@management-models/IdentityProvider';
import { User } from '@management-models/User';
import type { ProtectedResourceSecret } from '@management-models/ProtectedResourceSecret';
import type { NewProtectedResource } from '@management-models/NewProtectedResource';
import type { NewProtectedResourceFeature } from '@management-models/NewProtectedResourceFeature';
import type { NewMcpTool } from '@management-models/NewMcpTool';
import type { TokenExchangeSettings } from '@management-models/TokenExchangeSettings';
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
 * Fixture interface for token exchange tests with MCP Server as the exchanging client
 */
export interface TokenExchangeMcpFixture {
  domain: Domain;
  application: Application;
  mcpServer: ProtectedResourceSecret;
  user: User;
  defaultIdp: IdentityProvider;
  oidc: OidcConfiguration;
  /** Basic auth for the application (used to obtain subject tokens via password grant) */
  applicationBasicAuth: string;
  /** Basic auth for the MCP Server (used for token exchange and introspection) */
  mcpServerBasicAuth: string;
  accessToken: string;
  cleanup: () => Promise<void>;
  /** Obtain access/refresh/id tokens via password grant (use with different scopes for subject vs actor in delegation tests). */
  obtainToken: (scope?: string) => Promise<SubjectTokens>;
}

/**
 * Test constants for MCP token exchange
 */
export const TOKEN_EXCHANGE_MCP_TEST = {
  DOMAIN_NAME_PREFIX: 'token-exchange-mcp',
  DOMAIN_DESCRIPTION: 'Token exchange MCP domain',
  APP_CLIENT_NAME: 'token-exchange-mcp-app',
  MCP_SERVER_NAME: 'token-exchange-mcp-server',
  USER_PASSWORD: 'SomeP@ssw0rd',
  DEFAULT_SCOPES: [
    { scope: 'openid', defaultScope: true },
    { scope: 'profile', defaultScope: true },
    { scope: 'offline_access', defaultScope: false },
  ],
  /** Application grant types: password + refresh for obtaining subject tokens */
  APP_GRANT_TYPES: ['password', 'refresh_token'],
  /**
   * MCP Server grant types: ONLY client_credentials and token exchange.
   * MCP Servers do not support other grant types (e.g. authorization_code, password).
   */
  MCP_GRANT_TYPES: ['client_credentials', 'urn:ietf:params:oauth:grant-type:token-exchange'],
  DEFAULT_ALLOWED_SUBJECT_TOKEN_TYPES: [
    'urn:ietf:params:oauth:token-type:access_token',
    'urn:ietf:params:oauth:token-type:refresh_token',
    'urn:ietf:params:oauth:token-type:id_token',
    'urn:ietf:params:oauth:token-type:jwt',
  ],
  DEFAULT_ALLOWED_ACTOR_TOKEN_TYPES: [
    'urn:ietf:params:oauth:token-type:access_token',
    'urn:ietf:params:oauth:token-type:id_token',
    'urn:ietf:params:oauth:token-type:jwt',
  ],
  REDIRECT_URI: 'https://gravitee.io/callback',
  RESOURCE_IDENTIFIER: 'https://token-exchange-mcp.example.com',
};

/**
 * Introspects an access token with MCP Server credentials and asserts client_id and aud
 * match the MCP server client ID (the entity that performed the token exchange).
 */
export async function expectTokenIntrospectionMatchesMcpClient(
  oidc: OidcConfiguration,
  mcpServerBasicAuth: string,
  accessToken: string,
  expectedClientId: string,
): Promise<void> {
  const introspectResponse = await performPost(
    oidc.introspection_endpoint,
    '',
    `token=${accessToken}`,
    {
      'Content-type': 'application/x-www-form-urlencoded',
      Authorization: `Basic ${mcpServerBasicAuth}`,
    },
  ).expect(200);

  expect(introspectResponse.body.active).toBe(true);
  expect(introspectResponse.body.client_id).toBe(expectedClientId);
  expect(introspectResponse.body.aud).toBe(expectedClientId);
  expect(introspectResponse.body.token_type).toBe('bearer');
}

/**
 * Enable token exchange for a domain (optionally with delegation)
 */
async function enableTokenExchange(
  domainId: string,
  token: string,
  allowedSubjectTokenTypes: string[] = TOKEN_EXCHANGE_MCP_TEST.DEFAULT_ALLOWED_SUBJECT_TOKEN_TYPES,
  delegation?: { allowDelegation: boolean; allowedActorTokenTypes: string[]; maxDelegationDepth: number },
): Promise<void> {
  const tokenExchangeSettings: TokenExchangeSettings = {
    enabled: true,
    allowedSubjectTokenTypes,
  };
  if (delegation?.allowDelegation) {
    tokenExchangeSettings.allowDelegation = true;
    tokenExchangeSettings.allowedActorTokenTypes = delegation.allowedActorTokenTypes;
    tokenExchangeSettings.maxDelegationDepth = delegation.maxDelegationDepth;
  }
  await request(getDomainManagerUrl(domainId))
    .patch('')
    .set('Authorization', `Bearer ${token}`)
    .set('Content-Type', 'application/json')
    .send({ tokenExchangeSettings })
    .expect(200);
}

/** Config for MCP token exchange fixture. MCP Server grant types are fixed to client_credentials and token exchange only. */
export interface TokenExchangeMcpFixtureConfig {
  domainNamePrefix?: string;
  domainDescription?: string;
  appClientName?: string;
  mcpServerName?: string;
  appGrantTypes?: string[];
  scopes?: { scope: string; defaultScope: boolean }[];
  allowedSubjectTokenTypes?: string[];
  /** When set, the protected resource is created with an MCP tool feature with these scopes. */
  mcpToolScopes?: string[];
  /** When set, the MCP client (protected resource OAuth client) is created with these scope settings. */
  mcpClientScopes?: { scope: string; defaultScope: boolean }[];
  /** When true, enables delegation (obtainToken with different scopes used for subject vs actor). */
  allowDelegation?: boolean;
  allowedActorTokenTypes?: string[];
  maxDelegationDepth?: number;
  /** Scope handling settings for the MCP Server. When set, inherited is false. Defaults to domain-inherited DOWNSCOPING. */
  tokenExchangeOAuthSettings?: { inherited?: boolean; scopeHandling?: string };
}

/**
 * Setup token exchange test fixture with MCP Server as the client performing token exchange.
 * An Application (with password grant) is used to obtain subject tokens; the MCP Server
 * uses ONLY client_credentials and token exchange grant types to perform the exchange.
 */
export const setupTokenExchangeMcpFixture = async (
  config: TokenExchangeMcpFixtureConfig = {},
): Promise<TokenExchangeMcpFixture> => {
  let domain: Domain | null = null;
  let accessToken: string | null = null;

  try {
    const {
      domainNamePrefix = TOKEN_EXCHANGE_MCP_TEST.DOMAIN_NAME_PREFIX,
      domainDescription = TOKEN_EXCHANGE_MCP_TEST.DOMAIN_DESCRIPTION,
      appClientName = TOKEN_EXCHANGE_MCP_TEST.APP_CLIENT_NAME,
      mcpServerName = TOKEN_EXCHANGE_MCP_TEST.MCP_SERVER_NAME,
      appGrantTypes = TOKEN_EXCHANGE_MCP_TEST.APP_GRANT_TYPES,
      scopes = TOKEN_EXCHANGE_MCP_TEST.DEFAULT_SCOPES,
      allowedSubjectTokenTypes = TOKEN_EXCHANGE_MCP_TEST.DEFAULT_ALLOWED_SUBJECT_TOKEN_TYPES,
      mcpToolScopes,
      mcpClientScopes,
      allowDelegation = false,
      allowedActorTokenTypes = TOKEN_EXCHANGE_MCP_TEST.DEFAULT_ALLOWED_ACTOR_TOKEN_TYPES,
      maxDelegationDepth = 1,
      tokenExchangeOAuthSettings,
    } = config;

    accessToken = await requestAdminAccessToken();
    expect(accessToken).toBeDefined();

    const createdDomain = await createDomain(accessToken, uniqueName(domainNamePrefix, true), domainDescription);
    expect(createdDomain).toBeDefined();
    expect(createdDomain.id).toBeDefined();
    domain = createdDomain;

    const idpSet = await getAllIdps(createdDomain.id, accessToken);
    const defaultIdp = idpSet.values().next().value;
    expect(defaultIdp).toBeDefined();

    await enableTokenExchange(createdDomain.id, accessToken, allowedSubjectTokenTypes, allowDelegation
      ? { allowDelegation: true, allowedActorTokenTypes, maxDelegationDepth }
      : undefined);

    // Application: used only to obtain subject tokens (password + refresh_token)
    const application = await createTestApp(uniqueName(appClientName, true), createdDomain, accessToken, 'WEB', {
      settings: {
        oauth: {
          redirectUris: [TOKEN_EXCHANGE_MCP_TEST.REDIRECT_URI],
          grantTypes: appGrantTypes,
          scopeSettings: scopes,
        },
      },
      identityProviders: new Set([{ identity: defaultIdp.id, priority: 0 }]),
    });
    expect(application).toBeDefined();
    expect(application.id).toBeDefined();

    // MCP Server: ONLY client_credentials and token exchange (performs the token exchange)
    const mcpServerBody: NewProtectedResource = {
      name: uniqueName(mcpServerName, true),
      type: 'MCP_SERVER',
      resourceIdentifiers: [TOKEN_EXCHANGE_MCP_TEST.RESOURCE_IDENTIFIER],
      settings: {
        oauth: {
          grantTypes: TOKEN_EXCHANGE_MCP_TEST.MCP_GRANT_TYPES,
          ...(mcpClientScopes != null && mcpClientScopes.length > 0 && { scopeSettings: mcpClientScopes }),
          ...(tokenExchangeOAuthSettings != null && { tokenExchangeOAuthSettings }),
        },
      },
    };
    if (mcpToolScopes != null && mcpToolScopes.length > 0) {
      const tool: NewMcpTool = { key: 'default', type: 'MCP_TOOL', scopes: mcpToolScopes };
      mcpServerBody.features = [tool] as NewProtectedResourceFeature[];
    }
    const mcpServer = await createProtectedResource(createdDomain.id, accessToken, mcpServerBody);
    expect(mcpServer).toBeDefined();
    expect(mcpServer.clientId).toBeDefined();
    expect(mcpServer.clientSecret).toBeDefined();

    const startedDomain = await startDomain(createdDomain.id, accessToken);
    expect(startedDomain).toBeDefined();

    const domainReady = await waitForDomainStart(startedDomain);
    await waitForDomainSync(domainReady.domain.id);
    domain = domainReady.domain;

    const oidc = (domainReady.oidcConfig ?? {}) as OidcConfiguration;
    expect(oidc.token_endpoint).toBeDefined();
    expect(oidc.introspection_endpoint).toBeDefined();

    const user = await buildCreateAndTestUser(domain.id, accessToken, 0);
    expect(user).toBeDefined();

    const applicationBasicAuth = applicationBase64Token(application);
    const mcpServerBasicAuth = getBase64BasicAuth(mcpServer.clientId!, mcpServer.clientSecret!);

    const obtainToken = async (scope: string = 'openid%20profile%20offline_access'): Promise<SubjectTokens> => {
      const response = await performPost(
        oidc.token_endpoint,
        '',
        `grant_type=password&username=${user.username}&password=${TOKEN_EXCHANGE_MCP_TEST.USER_PASSWORD}&scope=${scope}`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
          Authorization: `Basic ${applicationBasicAuth}`,
        },
      ).expect(200);

      return {
        accessToken: response.body.access_token,
        refreshToken: response.body.refresh_token,
        idToken: response.body.id_token,
        expiresIn: response.body.expires_in,
      };
    };

    const cleanup = async () => {
      if (domain?.id && accessToken) {
        await safeDeleteDomain(domain.id, accessToken);
      }
    };

    return {
      domain,
      application,
      mcpServer,
      user,
      defaultIdp,
      oidc,
      applicationBasicAuth,
      mcpServerBasicAuth,
      accessToken,
      obtainToken,
      cleanup,
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
