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
import { getDomainManagerUrl } from '@management-commands/service/utils';
import { createTestApp } from '@utils-commands/application-commands';
import {
  extractXsrfTokenAndActionResponse,
  introspectToken as introspectOidcToken,
  performFormPost,
  performGet,
  performPost,
} from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { uniqueName } from '@utils-commands/misc';
import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
import { IdentityProvider } from '@management-models/IdentityProvider';
import { User } from '@management-models/User';
import request from 'supertest';

export interface OidcConfiguration {
  issuer: string;
  authorization_endpoint: string;
  token_endpoint: string;
  introspection_endpoint: string;
  userinfo_endpoint: string;
  revocation_endpoint: string;
  end_session_endpoint: string;
  jwks_uri: string;
}

export interface SubjectTokens {
  accessToken: string;
  refreshToken?: string;
  idToken?: string;
  expiresIn: number;
}

export interface RevocationFixture {
  domain: Domain;
  application: Application;
  user: User;
  defaultIdp: IdentityProvider;
  oidc: OidcConfiguration;
  basicAuth: string;
  accessToken: string;
  cleanup: () => Promise<void>;
  obtainAuthorizationCodeTokens: (scope?: string) => Promise<SubjectTokens>;
  introspectToken: (token: string) => Promise<any>;
  exchangeToken: (subjectToken: string, subjectTokenType: 'access_token' | 'refresh_token') => Promise<string>;
}

const REVOCATION_TEST = {
  DOMAIN_NAME_PREFIX: 'revoke-consents',
  DOMAIN_DESCRIPTION: 'Revoke user consents domain',
  CLIENT_NAME: 'revoke-consents-client',
  USER_PASSWORD: 'SomeP@ssw0rd',
  REDIRECT_URI: 'https://gravitee.io/callback',
  DEFAULT_SCOPES: [
    { scope: 'openid', defaultScope: true },
    { scope: 'profile', defaultScope: true },
    { scope: 'offline_access', defaultScope: false },
  ],
  DEFAULT_GRANT_TYPES: ['authorization_code', 'refresh_token', 'urn:ietf:params:oauth:grant-type:token-exchange'],
  ALLOWED_SUBJECT_TOKEN_TYPES: [
    'urn:ietf:params:oauth:token-type:access_token',
    'urn:ietf:params:oauth:token-type:refresh_token',
  ],
  ALLOWED_REQUESTED_TOKEN_TYPES: ['urn:ietf:params:oauth:token-type:access_token'],
};

const enableTokenExchange = async (domainId: string, token: string): Promise<void> => {
  await request(getDomainManagerUrl(domainId))
    .patch('')
    .set('Authorization', `Bearer ${token}`)
    .set('Content-Type', 'application/json')
    .send({
      tokenExchangeSettings: {
        enabled: true,
        allowedSubjectTokenTypes: REVOCATION_TEST.ALLOWED_SUBJECT_TOKEN_TYPES,
        allowedRequestedTokenTypes: REVOCATION_TEST.ALLOWED_REQUESTED_TOKEN_TYPES,
        allowImpersonation: true,
        allowDelegation: false,
      },
    })
    .expect(200);
};

export const setupRevocationFixture = async (): Promise<RevocationFixture> => {
  let domain: Domain | null = null;
  let accessToken: string | null = null;

  try {
    accessToken = await requestAdminAccessToken();
    expect(accessToken).toBeDefined();

    const createdDomain = await createDomain(accessToken, uniqueName(REVOCATION_TEST.DOMAIN_NAME_PREFIX, true), REVOCATION_TEST.DOMAIN_DESCRIPTION);
    expect(createdDomain).toBeDefined();
    expect(createdDomain.id).toBeDefined();
    domain = createdDomain;

    const idpSet = await getAllIdps(createdDomain.id, accessToken);
    const defaultIdp = idpSet.values().next().value;
    expect(defaultIdp).toBeDefined();

    await enableTokenExchange(createdDomain.id, accessToken);

    const application = await createTestApp(uniqueName(REVOCATION_TEST.CLIENT_NAME, true), createdDomain, accessToken, 'WEB', {
      settings: {
        oauth: {
          redirectUris: [REVOCATION_TEST.REDIRECT_URI],
          grantTypes: REVOCATION_TEST.DEFAULT_GRANT_TYPES,
          scopeSettings: REVOCATION_TEST.DEFAULT_SCOPES,
        },
      },
      identityProviders: new Set([{ identity: defaultIdp.id, priority: 0 }]),
    });
    expect(application).toBeDefined();

    const startedDomain = await startDomain(createdDomain.id, accessToken);
    expect(startedDomain).toBeDefined();
    domain = startedDomain;

    const oidcResponse = await waitForOidcReady(startedDomain.hrid, { timeoutMs: 30000, intervalMs: 500 });
    expect(oidcResponse.status).toBe(200);
    const oidc = oidcResponse.body as OidcConfiguration;

    const user = await buildCreateAndTestUser(startedDomain.id, accessToken, 0);
    expect(user).toBeDefined();

    const basicAuth = applicationBase64Token(application);

    const obtainAuthorizationCodeTokens = async (scope: string = 'openid%20profile%20offline_access'): Promise<SubjectTokens> => {
      const clientId = application.settings.oauth.clientId;
      const authorizationRequestParams = `?response_type=code&client_id=${clientId}&redirect_uri=${encodeURIComponent(REVOCATION_TEST.REDIRECT_URI)}&scope=${scope}`;

      const authResponse = await performGet(oidc.authorization_endpoint, authorizationRequestParams).expect(302);
      const loginResult = await extractXsrfTokenAndActionResponse(authResponse);

      const postLogin = await performFormPost(
        loginResult.action,
        '',
        {
          'X-XSRF-TOKEN': loginResult.token,
          username: user.username,
          password: REVOCATION_TEST.USER_PASSWORD,
          client_id: clientId,
        },
        {
          Cookie: loginResult.headers['set-cookie'],
          'Content-type': 'application/x-www-form-urlencoded',
        },
      ).expect(302);

      const postLoginRedirect = await performGet(postLogin.headers['location'], '', {
        Cookie: postLogin.headers['set-cookie'],
      }).expect(302);

      let redirectWithCodeLocation = postLoginRedirect.headers['location'];

      if (redirectWithCodeLocation.includes('/oauth/consent')) {
        const consentResult = await extractXsrfTokenAndActionResponse(postLoginRedirect);
        const postConsent = await performFormPost(
          consentResult.action,
          '',
          {
            'X-XSRF-TOKEN': consentResult.token,
            'scope.openid': true,
            'scope.profile': true,
            'scope.offline_access': true,
            user_oauth_approval: true,
          },
          {
            Cookie: consentResult.headers['set-cookie'],
            'Content-type': 'application/x-www-form-urlencoded',
          },
        ).expect(302);

        const postConsentRedirect = await performGet(postConsent.headers['location'], '', {
          Cookie: postConsent.headers['set-cookie'] || consentResult.headers['set-cookie'],
        }).expect(302);

        redirectWithCodeLocation = postConsentRedirect.headers['location'];
      }

      const codeMatch = redirectWithCodeLocation.match(/[?&]code=([-_a-zA-Z0-9]+)&?/);
      expect(codeMatch).toBeDefined();
      const authorizationCode = codeMatch![1];

      const response = await performPost(
        oidc.token_endpoint,
        '',
        `grant_type=authorization_code&code=${authorizationCode}&redirect_uri=${encodeURIComponent(REVOCATION_TEST.REDIRECT_URI)}`,
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

    const exchangeToken = async (subjectToken: string, subjectTokenType: 'access_token' | 'refresh_token'): Promise<string> => {
      const response = await performPost(
        oidc.token_endpoint,
        '',
        `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
          `&subject_token=${subjectToken}` +
          `&subject_token_type=urn:ietf:params:oauth:token-type:${subjectTokenType}`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
          Authorization: `Basic ${basicAuth}`,
        },
      ).expect(200);

      const exchangedToken = response.body.access_token;
      expect(exchangedToken).toBeDefined();
      return exchangedToken;
    };

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
      obtainAuthorizationCodeTokens,
      introspectToken,
      exchangeToken,
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
