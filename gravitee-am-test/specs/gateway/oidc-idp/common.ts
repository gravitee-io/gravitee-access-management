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
import {
  allowHttpLocalhostRedirects,
  createDomain,
  safeDeleteDomain,
  DomainWithOidcConfig,
  startDomain,
  waitForDomainStart,
  waitForDomainSync,
} from '@management-commands/domain-management-commands';
import { Domain } from '@management-models/Domain';
import { createTestApp } from '@utils-commands/application-commands';
import { expect } from '@jest/globals';
import { createIdp, deleteIdp, getAllIdps, updateIdp } from '@management-commands/idp-management-commands';
import { Application } from '@management-models/Application';
import { initiateLoginFlow, login, postConsent } from '@gateway-commands/login-commands';
import { patchApplication } from '@management-commands/application-management-commands';
import { BasicResponse, followRedirect, followRedirectTag, uniqueName } from '@utils-commands/misc';
import { performFormPost, performGet } from '@gateway-commands/oauth-oidc-commands';
import cheerio from 'cheerio';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import faker from 'faker';
import { IdentityProvider } from '@management-models/IdentityProvider';

export type ProviderPkceConfig = { forcePKCE: boolean; forceS256CodeChallengeMethod: boolean };
export type UrlAssertions = (uri: string) => void;
export type OidcLoginFailureAssertions = (response: any) => void;

export const TEST_USER = {
  firstname: faker.name.firstName(),
  lastname: faker.name.lastName(),
  username: 'user',
  password: '#CoMpL3X-P@SsW0Rd',
};

export type OIDCFixture = {
  providerApp: {
    setPkceConfig: (config: ProviderPkceConfig) => Promise<any>;
  };
  idpPluginInClient: {
    setPkceMethod: (method: string) => Promise<any>;
    setResponseType: (response: { type: string; mode: string }) => Promise<any>;
  };

  login: (user: string, pass: string, opts: LoginOpts) => Promise<any>;
  expectRedirectToClient: (authorizeResponse: any, additionalAssertions: UrlAssertions) => Promise<string>;
  cleanup: () => Promise<any>;
};

type LoginOpts = {
  oidcSignInUrlAssertions?: UrlAssertions;
  oidcLoginFailureAssertions?: OidcLoginFailureAssertions;
  oidcIdpFlow?: 'code' | 'implicit';
};

/**
 *
 * @param domainSuffix suffix for the domain name, should be unique for each test suite using this setup
 */
export async function setupOidcProviderTest(domainSuffix: string): Promise<OIDCFixture> {
  /*
   * Test setup:
   * We create two domains, idp-pkce-provider & idp-pkce-client:
   * idp-pkce-provider contains:
   *  - inline provider with a single user
   *  - a web application, using the inline provider - it will act as the OIDC IDP
   * idp-pkce-client contains:
   *  - an OIDC provider configured to use the web app from idp-pkce-provider domain
   *  - a web application configured with this IDP for triggering the test
   * The user gets logged in to get through the consents, then each test runs with fresh set of cookies.
   * Each test configures a combination of provider & client PKCE configs via the fixture and checks whether login succeeds/fails in the expected manner
   */

  let accessToken: string;
  let providerDomain: Domain;
  let clientDomain: Domain;
  let clientOpenIdConfiguration: any;

  accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();

  clientDomain = await createDomain(accessToken, `idp-client-${domainSuffix}`, 'Client domain for OIDC+PKCE tests');
  expect(clientDomain).toBeDefined();
  expect(clientDomain.id).toBeDefined();

  providerDomain = await createDomain(accessToken, `idp-provider-${domainSuffix}`, 'IDP domain for OIDC+PKCE tests').then((domain) =>
    allowHttpLocalhostRedirects(domain, accessToken),
  );
  expect(providerDomain).toBeDefined();
  expect(providerDomain.id).toBeDefined();
  expect(providerDomain.id).not.toBe(clientDomain.id);

  let providerInlineIdp = await replaceDefaultIdpWithInline(providerDomain, accessToken);

  // Generate unique application name to avoid conflicts in parallel execution
  const providerAppName = uniqueName('oidc-pkce-test-provider', true);
  let providerIdpApplication = await createApp(
    providerAppName,
    providerDomain,
    accessToken,
    providerInlineIdp.id,
    process.env.AM_GATEWAY_URL + '/' + clientDomain.hrid + '/login/callback',
  );

  let clientIdentityProvider = await createOidcProvider(clientDomain, providerDomain, accessToken, providerIdpApplication);

  let clientApp = await createApp(
    'oidc-pkce-test-client',
    clientDomain,
    accessToken,
    clientIdentityProvider.id,
    'https://auth-nightly.gravitee.io/myApp/callback',
  );

  const startProviderDomain = doStartDomain(providerDomain, accessToken);
  const startClientDomain = doStartDomain(clientDomain, accessToken).then((started) => {
    clientOpenIdConfiguration = started.oidcConfig;
  });

  await Promise.all([startProviderDomain, startClientDomain]);

  const navigateToOidcProviderLogin = async (response: BasicResponse, oidcUrlAssertions?: UrlAssertions) => {
    const headers = response.headers['set-cookie'] ? { Cookie: response.headers['set-cookie'] } : {};
    const result = await performGet(response.headers['location'], '', headers).expect(200);
    const dom = cheerio.load(result.text);

    const oidcProviderLoginUrl = dom('.btn-oauth2-generic-am-idp').attr('href');
    expect(oidcProviderLoginUrl).toBeDefined();
    if (oidcUrlAssertions) {
      oidcUrlAssertions(oidcProviderLoginUrl);
    }
    return await performGet(oidcProviderLoginUrl).expect(302);
  };

  const submitFragmentForm = async (response: BasicResponse) => {
    const dom = cheerio.load(response.text);
    const action = dom('#callbackForm').attr('action');
    const url = action.startsWith('http:') ? action : `${process.env.AM_GATEWAY_URL}/${clientDomain.hrid}/login/` + action;
    const headers = response.header['set-cookie'] ? { Cookie: response.header['set-cookie'] } : {};
    return performFormPost(url, '', { urlHash: new URL(response.request.url).hash }, headers);
  };

  const expectRedirectToClient = async (clientAuthorizeResponse: BasicResponse, otherAssertions: UrlAssertions) => {
    expect(clientAuthorizeResponse.status).toBe(302);
    const location = clientAuthorizeResponse.header['location'];
    expect(location).toMatch(new RegExp(/^/.source + clientApp.settings.oauth.redirectUris[0]));
    otherAssertions(location);

    const codeMatch = /\?code=([^&]+)/.exec(location);
    return codeMatch ? codeMatch[1] : '';
  };

  // Wait for both domains to sync before starting login flow
  await Promise.all([
    waitForDomainSync(providerDomain.id, accessToken),
    waitForDomainSync(clientDomain.id, accessToken),
  ]).then(() => initiateLoginFlow(clientApp.settings.oauth.clientId, clientOpenIdConfiguration, clientDomain))
    .then((response) => navigateToOidcProviderLogin(response))
    .then((response) => login(response, TEST_USER.username, providerIdpApplication.settings.oauth.clientId, TEST_USER.password))
    .then(followRedirectTag('1'))
    .then(followRedirectTag('2'))
    .then(postConsent) // oidc provider consent
    .then(followRedirectTag('3'))
    .then(followRedirectTag('4'))
    .then(followRedirectTag('5'))
    .then(followRedirectTag('6'))
    .then(postConsent) // client consent
    .then(followRedirectTag('7'))
    .then(() => console.debug('user consents granted'));

  return {
    login: async (user: string, pass: string, opts: LoginOpts = {}) => {
      const oidcUrlAssertions = opts.oidcSignInUrlAssertions || undefined;
      const oidcLoginFailureAssertions = opts.oidcLoginFailureAssertions || undefined;
      const flow = opts.oidcIdpFlow || 'code';
      const clientId = clientApp.settings.oauth.clientId;
      return initiateLoginFlow(clientId, clientOpenIdConfiguration, clientDomain)
        .then((response) => navigateToOidcProviderLogin(response, oidcUrlAssertions))
        .then((oidcProviderLoginResponse) => {
          if (oidcLoginFailureAssertions) {
            // assert the login failed in the expected way
            return oidcLoginFailureAssertions(oidcProviderLoginResponse);
          } else {
            // proceed with the login flow
            // login to the provider
            return (
              login(oidcProviderLoginResponse, user, providerIdpApplication.settings.oauth.clientId, pass)
                // follow redirect to provider authorize endpoint
                .then(followRedirectTag('login-1'))
                // follow redirect client domain callback
                // .then(followRedirectTag('login-2'))
                .then((response) => {
                  if (flow == 'code') {
                    return followRedirectTag('login-code')(response).then(followRedirect);
                  } else {
                    return followRedirectTag('login-implicit-1')(response)
                      .then(submitFragmentForm)
                      .then(followRedirectTag('login-implicit-2'));
                  }
                })
            );
          }
        });
    },
    expectRedirectToClient,
    providerApp: {
      setPkceConfig: async (pkceConfig) => {
        let patch = {
          settings: {
            oauth: {
              forcePKCE: pkceConfig.forcePKCE,
              forceS256CodeChallengeMethod: pkceConfig.forceS256CodeChallengeMethod,
            },
          },
        };
        return patchApplication(providerDomain.id, accessToken, patch, providerIdpApplication.id).then(async (res) => {
          await waitForDomainSync(providerDomain.id, accessToken);
          return res;
        });
      },
    },
    idpPluginInClient: {
      setPkceMethod: async (challengeMethod) => {
        let newConfig = JSON.parse(clientIdentityProvider.configuration);
        newConfig.codeChallengeMethod = challengeMethod;
        await updateIdpConfiguration(clientIdentityProvider, newConfig, clientDomain, accessToken);
      },
      setResponseType: async (response) => {
        let newConfig = JSON.parse(clientIdentityProvider.configuration);
        newConfig.responseType = response.type;
        newConfig.responseMode = response.mode;
        await updateIdpConfiguration(clientIdentityProvider, newConfig, clientDomain, accessToken);
      },
    },
    cleanup: async () => {
      console.log(`Cleaning up domains: ${clientDomain.hrid}, ${providerDomain.hrid}`);
      return Promise.all([safeDeleteDomain(clientDomain.id, accessToken), safeDeleteDomain(providerDomain.id, accessToken)]).then((ok) =>
        console.log('Cleanup complete'),
      );
    },
  };
}

async function updateIdpConfiguration(idp: IdentityProvider, newConfig: any, domain: Domain, accessToken: string) {
  let updatedIdp = {
    name: idp.name,
    type: idp.type,
    configuration: JSON.stringify(newConfig),
    mappers: idp.mappers,
    roleMapper: idp.roleMapper,
    domainWhitelist: idp.domainWhitelist,
  };
  return updateIdp(domain.id, accessToken, updatedIdp, idp.id).then(() => waitForDomainSync(domain.id, accessToken));
}

async function createApp(name: string, domain: Domain, accessToken: string, idpId: string, redirectUri: string) {
  const app = await createTestApp(name, domain, accessToken, 'web', {
    settings: {
      oauth: {
        redirectUris: [redirectUri],
        grantTypes: ['authorization_code', 'password', 'refresh_token', 'implicit'],
        scopeSettings: [
          {
            scope: 'openid',
            defaultScope: true,
          },
        ],
      },
    },
    identityProviders: new Set([{ identity: idpId, priority: -1 }]),
  });
  expect(app).toBeDefined();
  return app;
}

async function replaceDefaultIdpWithInline(domain: Domain, accessToken: string) {
  await ensureDefaultIdpIsDeleted(domain, accessToken);

  return createIdp(domain.id, accessToken, {
    external: false,
    type: 'inline-am-idp',
    domainWhitelist: [],
    configuration: JSON.stringify({
      users: [TEST_USER],
    }),
    name: 'inmemory',
  }).then((newIdp) => {
    expect(newIdp).toBeDefined();
    return newIdp;
  });
}

async function createOidcProvider(clientDomain: Domain, providerDomain: Domain, accessToken: string, providerIdpApplication: Application) {
  console.debug('creating oidc provider using app', providerIdpApplication);
  let request = {
    name: 'oidc-provider',
    type: 'oauth2-generic-am-idp',
    configuration: JSON.stringify({
      clientId: providerIdpApplication.settings.oauth.clientId,
      clientSecret: providerIdpApplication.settings.oauth.clientSecret,
      clientAuthenticationMethod: 'client_secret_basic',
      wellKnownUri: process.env.AM_GATEWAY_URL + '/' + providerDomain.hrid + '/oidc/.well-known/openid-configuration',
      responseType: 'code',
      encodeRedirectUri: false,
      useIdTokenForUserInfo: false,
      signature: 'RSA_RS256',
      publicKeyResolver: 'GIVEN_KEY',
      scopes: ['openid'],
      connectTimeout: 10000,
      idleTimeout: 10000,
      maxPoolSize: 200,
      storeOriginalTokens: false,
      codeChallengeMethod: 'S256',
      responseMode: 'default',
    }),
    external: true,
  };
  return createIdp(clientDomain.id, accessToken, request).then((newIdp) => {
    expect(newIdp).toBeDefined();
    return newIdp;
  });
}

async function ensureDefaultIdpIsDeleted(domain: Domain, accessToken: string) {
  await deleteIdp(domain.id, accessToken, 'default-idp-' + domain.id);
  const idpSet = await getAllIdps(domain.id, accessToken);
  expect(idpSet).toHaveLength(0);
}

async function doStartDomain(domain: Domain, accessToken: string): Promise<DomainWithOidcConfig> {
  const started = await startDomain(domain.id, accessToken).then(waitForDomainStart);

  expect(started).toBeDefined();
  expect(started.oidcConfig).toBeDefined();
  expect(started.domain.id).toEqual(domain.id);

  return started;
}
