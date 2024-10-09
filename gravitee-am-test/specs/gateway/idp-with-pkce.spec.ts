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
import { beforeAll, expect, jest } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import {
  allowHttpLocalhostRedirects,
  createDomain,
  startDomain,
  waitFor,
  waitForDomainStart,
} from '@management-commands/domain-management-commands';
import { Application, Domain } from '../../api/management/models';
import { createIdp, deleteIdp, getAllIdps, updateIdp } from '@management-commands/idp-management-commands';
import { patchApplication } from '@management-commands/application-management-commands';
import { getWellKnownOpenIdConfiguration, performGet } from '@gateway-commands/oauth-oidc-commands';
import { initiateLoginFlow, login, postConsent } from '@gateway-commands/login-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { BasicResponse, followRedirect } from '@utils-commands/misc';
const cheerio = require('cheerio');
jest.setTimeout(200000);

type ProviderPkceConfig = { forcePKCE: boolean; forceS256CodeChallengeMethod: boolean };
type UrlAssertions = (uri: string) => void;
type OidcLoginFailureAssertions = (response: any) => void;

let fixture: {
  setIdpPkceConfig: (config: ProviderPkceConfig) => Promise<any>;
  setClientPkceMethod: (method: string) => Promise<any>;
  login: (
    user: string,
    pass: string,
    oidcUrlAssertions?: UrlAssertions,
    oidcLoginFailureAssertions?: OidcLoginFailureAssertions,
  ) => Promise<any>;
  expectRedirectWithAuthorizationCode: (authorizeResponse: any) => Promise<string>;
};

const GATEWAY_SYNC_GRACE_PERIOD_MILLIS = process.env.AM_GATEWAY_SYNC_GRACE_PERIOD || 5000;

beforeAll(async function () {
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

  const adminTokenResponse = await requestAdminAccessToken();
  accessToken = adminTokenResponse.body.access_token;
  expect(accessToken).toBeDefined();

  clientDomain = await createDomain(accessToken, 'idp-pkce-client', 'Client domain for OIDC+PKCE tests');
  expect(clientDomain).toBeDefined();
  expect(clientDomain.id).toBeDefined();

  providerDomain = await createDomain(accessToken, 'idp-pkce-provider', 'IDP domain for OIDC+PKCE tests').then((domain) =>
    allowHttpLocalhostRedirects(domain, accessToken),
  );
  expect(providerDomain).toBeDefined();
  expect(providerDomain.id).toBeDefined();
  expect(providerDomain.id).not.toBe(clientDomain.id);

  let providerInlineIdp = await replaceDefaultIdpWithInline(providerDomain, accessToken);

  let providerIdpApplication = await createApp(
    'oidc-pkce-test-provider',
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

  await Promise.all([doStartDomain(providerDomain, accessToken), doStartDomain(clientDomain, accessToken)]);
  clientOpenIdConfiguration = await getWellKnownOpenIdConfiguration(clientDomain.hrid).then((res) => res.body);

  const navigateToOidcProviderLogin = async (response: BasicResponse, oidcUrlAssertions?: UrlAssertions) => {
    const headers = response.headers['set-cookie'] ? { Cookie: response.headers['set-cookie'] } : {};
    const result = await performGet(response.headers['location'], '', headers).expect(200);
    const dom = cheerio.load(result.text);

    const oidcProviderLogin = dom('.btn-oauth2-generic-am-idp').attr('href');
    expect(oidcProviderLogin).toBeDefined();
    if (oidcUrlAssertions) oidcUrlAssertions(oidcProviderLogin);
    return await performGet(oidcProviderLogin).expect(302);
  };

  const expectRedirectWithAuthorizationCode = async (clientAuthorizeResponse: BasicResponse) => {
    expect(clientAuthorizeResponse.status).toBe(302);
    const location = clientAuthorizeResponse.header['location'];
    expect(location).toMatch(new RegExp(/^/.source + clientApp.settings.oauth.redirectUris[0] + /\?code=.+$/.source));

    return location.match(/\?code=([^&]+)/)[0];
  };

  await waitFor(2000)
    .then(() => initiateLoginFlow(clientApp.settings.oauth.clientId, clientOpenIdConfiguration, clientDomain))
    .then((response) => navigateToOidcProviderLogin(response))
    .then((response) => login(response, 'user', providerIdpApplication.settings.oauth.clientId, '#CoMpL3X-P@SsW0Rd'))
    .then(followRedirect)
    .then(followRedirect)
    .then(postConsent) // oidc provider consent
    .then(followRedirect)
    .then(followRedirect)
    .then(followRedirect)
    .then(followRedirect)
    .then(postConsent) // client consent
    .then(followRedirect)
    .then(() => console.debug('user consents granted'));

  fixture = {
    login: async (
      user: string,
      pass: string,
      oidcUrlAssertions: UrlAssertions,
      oidcProviderFailureAssertions: OidcLoginFailureAssertions,
    ) => {
      const clientId = clientApp.settings.oauth.clientId;
      return initiateLoginFlow(clientId, clientOpenIdConfiguration, clientDomain)
        .then((response) => navigateToOidcProviderLogin(response, oidcUrlAssertions))
        .then((oidcProviderLoginResponse) => {
          if (oidcProviderFailureAssertions) {
            // assert the login failed in the expected way
            return oidcProviderFailureAssertions(oidcProviderLoginResponse);
          } else {
            // proceed with the login flow
            // login to the provider
            return login(oidcProviderLoginResponse, user, providerIdpApplication.settings.oauth.clientId, pass)
              // follow redirect to provider authorize endpoint
              .then(followRedirect)
              // follow redirect client domain callback
              .then(followRedirect)
              // follow redirect to client authorize endpoint
              .then(followRedirect);
          }
        });
    },
    expectRedirectWithAuthorizationCode,
    setIdpPkceConfig: async (pkceConfig) => {
      let patch = {
        settings: {
          oauth: {
            forcePKCE: pkceConfig.forcePKCE,
            forceS256CodeChallengeMethod: pkceConfig.forceS256CodeChallengeMethod,
          },
        },
      };
      return patchApplication(providerDomain.id, accessToken, patch, providerIdpApplication.id).then((res) =>
        waitFor(GATEWAY_SYNC_GRACE_PERIOD_MILLIS).then(() => res),
      );
    },
    setClientPkceMethod: async (challengeMethod) => {
      let newConfig = JSON.parse(clientIdentityProvider.configuration);
      newConfig.codeChallengeMethod = challengeMethod;

      let updatedIdp = {
        name: clientIdentityProvider.name,
        configuration: JSON.stringify(newConfig),
        mappers: clientIdentityProvider.mappers,
        roleMapper: clientIdentityProvider.roleMapper,
        domainWhitelist: clientIdentityProvider.domainWhitelist,
      };
      return updateIdp(clientDomain.id, accessToken, updatedIdp, clientIdentityProvider.id).then(() =>
        waitFor(GATEWAY_SYNC_GRACE_PERIOD_MILLIS),
      );
    },
  };
});

describe('The OIDC provider', () => {
  describe("When provider doesn't force PKCE", () => {
    beforeAll(async () => {
      await fixture.setIdpPkceConfig({ forcePKCE: false, forceS256CodeChallengeMethod: false });
    });
    it('should login without PKCE', async () => {
      await fixture.setClientPkceMethod(null);
      await fixture
        .login('user', '#CoMpL3X-P@SsW0Rd', expectNoCodeChallenge)
        .then(fixture.expectRedirectWithAuthorizationCode)
        .then((code) => expect(code).not.toBeNull());
    });
    it('should login with plain challenge', async () => {
      await fixture.setClientPkceMethod('plain');
      await fixture
        .login('user', '#CoMpL3X-P@SsW0Rd', expectCodeChallenge('plain'))
        .then(fixture.expectRedirectWithAuthorizationCode)
        .then((code) => expect(code).not.toBeNull());
    });
    it('should login with S256 challenge', async () => {
      await fixture.setClientPkceMethod('s256');
      await fixture
        .login('user', '#CoMpL3X-P@SsW0Rd', expectCodeChallenge('S256'))
        .then(fixture.expectRedirectWithAuthorizationCode)
        .then((code) => expect(code).not.toBeNull());
    });
    it('should fail with challenge unsupported by provider', async () => {
      await fixture.setClientPkceMethod('non-existing-method');
    });
  });

  describe('When provider forces PKCE', () => {
    beforeAll(async () => {
      await fixture.setIdpPkceConfig({ forcePKCE: true, forceS256CodeChallengeMethod: false });
    });
    it('should fail login without PKCE', async () => {
      await fixture.setClientPkceMethod(null);
      await fixture.login('user', '#CoMpL3X-P@SsW0Rd', expectNoCodeChallenge, expectErrorRedirect('Missing+parameter%3A+code_challenge'));
    });
    it('should login with plain challenge', async () => {
      await fixture.setClientPkceMethod('plain');
      await fixture
        .login('user', '#CoMpL3X-P@SsW0Rd', expectCodeChallenge('plain'))
        .then(fixture.expectRedirectWithAuthorizationCode)
        .then((code) => expect(code).not.toBeNull());
    });
    it('should login with S256 challenge', async () => {
      await fixture.setClientPkceMethod('S256');
      await fixture
        .login('user', '#CoMpL3X-P@SsW0Rd', expectCodeChallenge('S256'))
        .then(fixture.expectRedirectWithAuthorizationCode)
        .then((code) => expect(code).not.toBeNull());
    });
  });

  describe('When provider forces S256 method', () => {
    beforeAll(async () => {
      await fixture.setIdpPkceConfig({ forcePKCE: false, forceS256CodeChallengeMethod: true });
    });
    it('should login without PKCE', async () => {
      await fixture.setClientPkceMethod(null);
      await fixture
        .login('user', '#CoMpL3X-P@SsW0Rd', expectNoCodeChallenge)
        .then(fixture.expectRedirectWithAuthorizationCode)
        .then((code) => expect(code).not.toBeNull());
    });
    it('should fail login with plain challenge', async () => {
      await fixture.setClientPkceMethod('plain');
      await fixture.login(
        'user',
        '#CoMpL3X-P@SsW0Rd',
        expectCodeChallenge('plain'),
        expectErrorRedirect('Invalid+parameter%3A+code_challenge_method'),
      );
    });
    it('should login with S256 challenge', async () => {
      await fixture.setClientPkceMethod('S256');
      await fixture
        .login('user', '#CoMpL3X-P@SsW0Rd', expectCodeChallenge('S256'))
        .then(fixture.expectRedirectWithAuthorizationCode)
        .then((code) => expect(code).not.toBeNull());
    });
  });

  describe('When provider forces PKCE with S256 method', () => {
    beforeAll(async () => {
      await fixture.setIdpPkceConfig({ forcePKCE: true, forceS256CodeChallengeMethod: true });
    });
    it('should fail login without PKCE', async () => {
      await fixture.setClientPkceMethod(null);
      await fixture.login('user', '#CoMpL3X-P@SsW0Rd', expectNoCodeChallenge, expectErrorRedirect('Missing+parameter%3A+code_challenge'));
    });
    it('should fail login with plain challenge', async () => {
      await fixture.setClientPkceMethod('plain');
      await fixture.login(
        'user',
        '#CoMpL3X-P@SsW0Rd',
        expectCodeChallenge('plain'),
        expectErrorRedirect('Invalid+parameter%3A+code_challenge_method'),
      );
    });
    it('should login with S256 challenge', async () => {
      await fixture.setClientPkceMethod('S256');
      await fixture
        .login('user', '#CoMpL3X-P@SsW0Rd', expectCodeChallenge('S256'))
        .then(fixture.expectRedirectWithAuthorizationCode)
        .then((code) => expect(code).not.toBeNull());
    });
  });
});

function expectNoCodeChallenge(url: string) {
  expect(url).not.toContain('code_challenge_method');
  expect(url).not.toContain('code_challenge');
}

function expectCodeChallenge(challengeMethod: string) {
  return (url: string) => {
    expect(url).toMatch(new RegExp(/[&?]code_challenge_method=/.source + challengeMethod));
    expect(url).toMatch(/([&?])code_challenge=[^&]+&/);
  };
}

function expectErrorRedirect(description: string, error: string = 'invalid_request') {
  return (response: BasicResponse) => {
    expect(response.status).toBe(302);
    const location = response.header['location'];
    expect(location).toContain('error=' + error);
    expect(location).toContain('error_description=' + description);
  };
}

async function createApp(name: string, domain: Domain, accessToken: string, idpId: string, redirectUri: string) {
  const app = await createTestApp(name, domain, accessToken, 'web', {
    settings: {
      oauth: {
        redirectUris: [redirectUri],
        grantTypes: ['authorization_code', 'password', 'refresh_token'],
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
      users: [
        {
          firstname: 'my-user',
          lastname: 'my-user-lastname',
          username: 'user',
          password: '#CoMpL3X-P@SsW0Rd',
        },
      ],
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
  expect(idpSet.size).toEqual(0);
}

async function doStartDomain(domain: Domain, accessToken: string): Promise<Domain> {
  const startedDomain = await startDomain(domain.id, accessToken)
    .then(waitForDomainStart)
    .then((result) => result.domain);
  expect(startedDomain).toBeDefined();
  expect(startedDomain.id).toEqual(domain.id);

  return startedDomain;
}
