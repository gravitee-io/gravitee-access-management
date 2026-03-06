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
import { Application } from '@management-models/Application';
import {
  DomainOidcConfig,
  createDomain,
  patchDomain,
  safeDeleteDomain,
  startDomain,
  waitForDomainStart,
} from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { createIdp } from '@management-commands/idp-management-commands';
import { uniqueName } from '@utils-commands/misc';
import {
  extractXsrfTokenAndActionResponse,
  performFormPost,
  performGet,
  performPost,
  introspectToken as introspectOidcToken,
} from '@gateway-commands/oauth-oidc-commands';
import { getBase64BasicAuth } from '@gateway-commands/utils';
import { Fixture } from '../../../test-fixture';
import { Response } from 'supertest';

const DEFAULT_REDIRECT_URI = 'http://localhost:4000/';
const DEFAULT_NONCE = '12345';

export interface ImplicitFlowResult {
  authorizeResponse: Response;
  postLoginResponse: Response;
  followRedirectResponse: Response;
  finalLocation: string;
  fragmentParams: URLSearchParams;
  sessionCookie: string;
  accessToken?: string;
  idToken?: string;
}

export interface LogoutAppVersionFixture extends Fixture {
  accessToken: string;
  domain: Domain;
  application: Application;
  openIdConfiguration: DomainOidcConfig;
  inlineUser: {
    username: string;
    password: string;
  };
  redirectUri: string;
  runImplicitFlow: (options: { responseType: 'token' | 'id_token'; nonce?: string }) => Promise<ImplicitFlowResult>;
  introspectAccessToken: (token: string) => Promise<any>;
  logoutWithGet: (sessionCookie: string, invalidateTokens?: boolean) => Promise<Response>;
  logoutWithPost: (sessionCookie: string, idToken: string) => Promise<Response>;
}

export const setupLogoutAppVersionFixture = async (): Promise<LogoutAppVersionFixture> => {
  let domain: Domain | null = null;
  let accessToken: string | null = null;

  try {
    accessToken = await requestAdminAccessToken();
    expect(accessToken).toBeDefined();

    domain = await createDomain(accessToken, uniqueName('logout-app', true), 'test logout flows');
    expect(domain).toBeDefined();
    expect(domain.id).toBeDefined();

    // Create all resources BEFORE starting domain so initial sync picks up everything
    await patchDomain(domain.id, accessToken, {
      oidc: {
        clientRegistrationSettings: {
          allowLocalhostRedirectUri: true,
          allowHttpSchemeRedirectUri: true,
          allowWildCardRedirectUri: true,
          isDynamicClientRegistrationEnabled: false,
          isOpenDynamicClientRegistrationEnabled: false,
        },
      },
    });

    const inlineUser = {
      username: 'user',
      password: '#CoMpL3X-P@SsW0Rd',
    };

    const inlineIdp = await createIdp(domain.id, accessToken, {
      external: false,
      type: 'inline-am-idp',
      domainWhitelist: [],
      configuration: JSON.stringify({
        users: [
          {
            firstname: 'my-user',
            lastname: 'my-user-lastname',
            username: inlineUser.username,
            password: inlineUser.password,
          },
          {
            firstname: 'Jensen',
            lastname: 'Barbara',
            username: 'jensen.barbara',
            email: 'jensen.barbara@mail.com',
            password: inlineUser.password,
          },
        ],
      }),
      name: 'inmemory',
    });
    expect(inlineIdp).toBeDefined();
    expect(inlineIdp.id).toBeDefined();

    const application = await createApplication(domain.id, accessToken, {
      name: 'my-client',
      type: 'WEB',
      redirectUris: [DEFAULT_REDIRECT_URI],
    }).then((app) =>
      updateApplication(
        domain.id!,
        accessToken!,
        {
          settings: {
            oauth: {
              redirectUris: [DEFAULT_REDIRECT_URI],
              grantTypes: ['implicit'],
            },
          },
          identityProviders: [{ identity: inlineIdp.id, priority: -1 }],
        },
        app.id,
      ).then((updated) => {
        updated.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
        return updated;
      }),
    );
    expect(application.settings.oauth.clientId).toBeDefined();
    expect(application.settings.oauth.clientSecret).toBeDefined();

    // Start domain — initial sync picks up all resources
    await startDomain(domain.id, accessToken);
    const started = await waitForDomainStart(domain);

    const openIdConfiguration = started.oidcConfig;
    expect(openIdConfiguration).toBeDefined();
    expect(openIdConfiguration.authorization_endpoint).toBeDefined();
    expect(openIdConfiguration.token_endpoint).toBeDefined();
    expect(openIdConfiguration.revocation_endpoint).toBeDefined();
    expect(openIdConfiguration.userinfo_endpoint).toBeDefined();
    expect(openIdConfiguration.registration_endpoint).toBeDefined();
    expect(openIdConfiguration.end_session_endpoint).toBeDefined();
    expect(openIdConfiguration.introspection_endpoint).toBeDefined();

    const basicAuth = getBase64BasicAuth(application.settings.oauth.clientId, application.settings.oauth.clientSecret);

    const runImplicitFlow = async (options: { responseType: 'token' | 'id_token'; nonce?: string }): Promise<ImplicitFlowResult> => {
      const params = new URLSearchParams({
        response_type: options.responseType,
        client_id: application.settings.oauth.clientId,
        redirect_uri: DEFAULT_REDIRECT_URI,
      });
      if (options.responseType === 'id_token') {
        params.set('nonce', options.nonce ?? DEFAULT_NONCE);
      }

      const authorizeResponse = await performGet(openIdConfiguration.authorization_endpoint, `?${params.toString()}`).expect(302);
      expect(authorizeResponse.headers['location']).toBeDefined();

      const loginResult = await extractXsrfTokenAndActionResponse(authorizeResponse);
      expect(loginResult.token).toBeDefined();
      expect(loginResult.action).toBeDefined();

      const postLoginResponse = await performFormPost(
        loginResult.action,
        '',
        {
          'X-XSRF-TOKEN': loginResult.token,
          username: inlineUser.username,
          password: inlineUser.password,
          client_id: application.settings.oauth.clientId,
        },
        {
          Cookie: loginResult.headers['set-cookie'],
          'Content-type': 'application/x-www-form-urlencoded',
        },
      ).expect(302);
      expect(postLoginResponse.headers['location']).toBeDefined();

      const followRedirectResponse = await performGet(postLoginResponse.headers['location'], '', {
        Cookie: postLoginResponse.headers['set-cookie'],
      }).expect(302);
      const finalLocation = followRedirectResponse.headers['location'];
      expect(finalLocation).toBeDefined();

      const fragmentParams = parseFragment(finalLocation);
      const accessToken = fragmentParams.get('access_token') ?? undefined;
      const idToken = fragmentParams.get('id_token') ?? undefined;

      // Use the latest cookie — Postman's cookie jar always sends the most recent one
      const sessionCookie = followRedirectResponse.headers['set-cookie'] ?? postLoginResponse.headers['set-cookie'];

      return {
        authorizeResponse,
        postLoginResponse,
        followRedirectResponse,
        finalLocation,
        fragmentParams,
        sessionCookie,
        accessToken,
        idToken,
      };
    };

    const introspectAccessToken = (token: string) => introspectOidcToken(openIdConfiguration.introspection_endpoint, token, basicAuth);

    const logoutWithGet = (sessionCookie: string, invalidateTokens = false) => {
      const query = invalidateTokens ? '?invalidate_tokens=true' : '';
      return performGet(openIdConfiguration.end_session_endpoint, query, { Cookie: sessionCookie }).expect(302);
    };

    const logoutWithPost = (sessionCookie: string, idToken: string) => {
      const body = new URLSearchParams({ id_token_hint: idToken }).toString();
      return performPostForm(openIdConfiguration.end_session_endpoint, body, sessionCookie);
    };

    return {
      accessToken,
      domain: started.domain,
      application,
      inlineUser,
      openIdConfiguration,
      redirectUri: DEFAULT_REDIRECT_URI,
      runImplicitFlow,
      introspectAccessToken,
      logoutWithGet,
      logoutWithPost,
      cleanUp: async () => {
        await safeDeleteDomain(domain!.id, accessToken!);
      },
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

const parseFragment = (location: string): URLSearchParams => {
  const hashIndex = location.indexOf('#');
  const fragment = hashIndex >= 0 ? location.substring(hashIndex + 1) : '';
  return new URLSearchParams(fragment);
};

const performPostForm = (url: string, body: string, sessionCookie: string) =>
  performPost(url, '', body, {
    'Content-type': 'application/x-www-form-urlencoded',
    Cookie: sessionCookie,
  }).expect(302);
