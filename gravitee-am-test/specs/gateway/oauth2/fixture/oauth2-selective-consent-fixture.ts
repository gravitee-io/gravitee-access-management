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
import * as cheerio from 'cheerio';
import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
import { DomainOidcConfig, safeDeleteDomain, setupDomainForTest } from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { createIdp, deleteIdp, defaultIdpId } from '@management-commands/idp-management-commands';
import { createScope } from '@management-commands/scope-management-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { extractXsrfTokenAndActionResponse, performFormPost, performGet, performPost } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { uniqueName } from '@utils-commands/misc';
import { Fixture } from '../../../test-fixture';

export const SELECTIVE_CONSENT = {
  DOMAIN_PREFIX: 'oauth2-selective-consent',
  REDIRECT_URI: 'https://example.com/callback',
  REQUIRED_SCOPE: 'accounts',
  OPTIONAL_SCOPES: ['orders', 'payments', 'reports'],
  USER_COUNT: 8,
  USER_PASSWORD: '#CoMpL3X-P@SsW0Rd',
} as const;

export const ALL_SCOPES: string[] = [SELECTIVE_CONSENT.REQUIRED_SCOPE, ...SELECTIVE_CONSENT.OPTIONAL_SCOPES];

export interface TestUser {
  username: string;
  password: string;
}

export interface SelectiveConsentFixture extends Fixture {
  domain: Domain;
  oidc: DomainOidcConfig;
  // authorization_code app with opt-in scope selection, one required scope and several optional ones
  app: Application;
  // hands out a user that no other test has consented with
  nextUser: () => TestUser;
}

export const setupSelectiveConsentFixture = async (): Promise<SelectiveConsentFixture> => {
  const accessToken = await requestAdminAccessToken();
  let domain: Domain | null = null;
  try {
    const started = await setupDomainForTest(uniqueName(SELECTIVE_CONSENT.DOMAIN_PREFIX, true), { accessToken, waitForStart: true });
    domain = started.domain;

    await deleteIdp(domain.id, accessToken, defaultIdpId(domain.id));
    const users: TestUser[] = Array.from({ length: SELECTIVE_CONSENT.USER_COUNT }, (_, i) => ({
      username: uniqueName(`selective-consent-user-${i}`, true),
      password: SELECTIVE_CONSENT.USER_PASSWORD,
    }));
    const idp = await createIdp(domain.id, accessToken, {
      external: false,
      type: 'inline-am-idp',
      domainWhitelist: [],
      configuration: JSON.stringify({
        users: users.map((u) => ({ firstname: 'Selective', lastname: 'Consent', username: u.username, password: u.password })),
      }),
      name: 'selective-consent-idp',
    });

    for (const key of ALL_SCOPES) {
      const scope = await createScope(domain.id, accessToken, { key, name: key, description: key });
      expect(scope.key).toEqual(key);
    }

    const created = await createApplication(domain.id, accessToken, {
      name: uniqueName('selective-consent-app', true),
      type: 'WEB',
      redirectUris: [SELECTIVE_CONSENT.REDIRECT_URI],
    });
    // The last mutation is wrapped so the gateway has picked up everything created above
    const app = await waitForSyncAfter(domain.id, () =>
      updateApplication(
        domain.id,
        accessToken,
        {
          settings: {
            oauth: {
              redirectUris: [SELECTIVE_CONSENT.REDIRECT_URI],
              grantTypes: ['authorization_code'],
              optInScopeSelection: true,
              scopeSettings: [
                { scope: SELECTIVE_CONSENT.REQUIRED_SCOPE, defaultScope: true, requiredScope: true },
                ...SELECTIVE_CONSENT.OPTIONAL_SCOPES.map((scope) => ({ scope, defaultScope: false })),
              ],
            },
          },
          identityProviders: new Set([{ identity: idp.id, priority: 0 }]),
        },
        created.id,
      ),
    );
    // The PUT response omits the secret; keep the one issued on creation for Basic auth
    app.settings.oauth.clientSecret = created.settings.oauth.clientSecret;

    let handedOut = 0;
    return {
      accessToken,
      domain,
      oidc: started.oidcConfig,
      app,
      nextUser: () => {
        if (handedOut >= users.length) {
          throw new Error(`All ${users.length} fixture users are in use; raise SELECTIVE_CONSENT.USER_COUNT`);
        }
        return users[handedOut++];
      },
      cleanUp: async () => {
        if (domain?.id && accessToken) {
          await safeDeleteDomain(domain.id, accessToken);
        }
      },
    };
  } catch (error) {
    if (domain?.id && accessToken) {
      await safeDeleteDomain(domain.id, accessToken).catch((e) => console.error('Cleanup failed:', e));
    }
    throw error;
  }
};

/** Merges Set-Cookie lists into one Cookie header; later lists win for cookies with the same name. */
const mergeCookies = (...lists: Array<string | string[] | undefined>): string => {
  const jar = new Map<string, string>();
  for (const list of lists) {
    for (const cookie of [list ?? []].flat()) {
      const pair = cookie.split(';')[0];
      jar.set(pair.substring(0, pair.indexOf('=')), pair);
    }
  }
  return [...jar.values()].join('; ');
};

export interface UserSession {
  // Cookie header carrying the signed-in user's gateway session
  cookie: string;
}

export const consentPageUrl = (fixture: SelectiveConsentFixture) => `${process.env.AM_GATEWAY_URL}/${fixture.domain.hrid}/oauth/consent`;

/** Starts an authorization_code request for the given scopes; with a session the login step is skipped. */
export const authorize = (fixture: SelectiveConsentFixture, scopes: string[], session?: UserSession) => {
  const params =
    `?response_type=code&client_id=${fixture.app.settings.oauth.clientId}` +
    `&redirect_uri=${encodeURIComponent(SELECTIVE_CONSENT.REDIRECT_URI)}&scope=${encodeURIComponent(scopes.join(' '))}`;
  return performGet(fixture.oidc.authorization_endpoint, params, session ? { Cookie: session.cookie } : null).expect(302);
};

/** Signs the user in for the given scopes and returns the session plus the redirect that follows login. */
export const signIn = async (fixture: SelectiveConsentFixture, user: TestUser, scopes: string[]) => {
  const authResponse = await authorize(fixture, scopes);
  const loginForm = await extractXsrfTokenAndActionResponse(authResponse);
  const postLogin = await performFormPost(
    loginForm.action,
    '',
    {
      'X-XSRF-TOKEN': loginForm.token,
      username: user.username,
      password: user.password,
      client_id: fixture.app.settings.oauth.clientId,
    },
    { Cookie: loginForm.headers['set-cookie'], 'Content-type': 'application/x-www-form-urlencoded' },
  ).expect(302);
  const session: UserSession = { cookie: mergeCookies(loginForm.headers['set-cookie'], postLogin.headers['set-cookie']) };
  const postLoginRedirect = await performGet(postLogin.headers['location'], '', { Cookie: session.cookie }).expect(302);
  return { session, postLoginRedirect };
};

export interface ConsentPage {
  action: string;
  token: string;
  cookie: string;
  // scopes rendered as checkboxes
  presentedScopes: string[];
  // value of the Allow button's data-allow-empty attribute
  allowEmpty: string | undefined;
}

/** Opens the consent page a /oauth/authorize redirect points to. */
export const openConsentPage = async (session: UserSession, redirect): Promise<ConsentPage> => {
  const cookie = mergeCookies(session.cookie.split('; '), redirect.headers['set-cookie']);
  const page = await performGet(redirect.headers['location'], '', { Cookie: cookie }).expect(200);
  const dom = cheerio.load(page.text);
  const presentedScopes = dom('input.scope-consent-checkbox')
    .map((_, el) =>
      dom(el)
        .attr('name')
        .replace(/^scope\./, ''),
    )
    .get();
  return {
    action: dom('form').attr('action'),
    token: dom('[name=X-XSRF-TOKEN]').val() as string,
    cookie: mergeCookies(cookie.split('; '), page.headers['set-cookie']),
    presentedScopes,
    allowEmpty: dom('#allowButton').attr('data-allow-empty'),
  };
};

/** Submits the consent form approving `approvedScopes` (or denying), then follows the redirect through /oauth/authorize. */
export const submitConsent = async (page: ConsentPage, approvedScopes: string[], approval: boolean = true) => {
  const fields = Object.fromEntries(approvedScopes.map((scope) => [`scope.${scope}`, true]));
  const postConsent = await performFormPost(
    page.action,
    '',
    { 'X-XSRF-TOKEN': page.token, ...fields, user_oauth_approval: approval },
    { Cookie: page.cookie, 'Content-type': 'application/x-www-form-urlencoded' },
  ).expect(302);
  // the gateway session lives in the cookie, so the consent outcome arrives in the POST's Set-Cookie
  const cookie = mergeCookies(page.cookie.split('; '), postConsent.headers['set-cookie']);
  const callback = await performGet(postConsent.headers['location'], '', { Cookie: cookie }).expect(302);
  return { callback, cookie: mergeCookies(cookie.split('; '), callback.headers['set-cookie']) };
};

/** Signs a user in and approves `approvedScopes` out of `requestedScopes`, returning the session. */
export const grantInitialConsent = async (
  fixture: SelectiveConsentFixture,
  user: TestUser,
  requestedScopes: string[],
  approvedScopes: string[],
): Promise<UserSession> => {
  const { session, postLoginRedirect } = await signIn(fixture, user, requestedScopes);
  const page = await openConsentPage(session, postLoginRedirect);
  const { callback, cookie } = await submitConsent(page, approvedScopes);
  expectAuthorizationCode(callback);
  return { cookie };
};

export const expectAuthorizationCode = (response) => {
  expect(response.headers['location']).toContain(`${SELECTIVE_CONSENT.REDIRECT_URI}?`);
  expect(response.headers['location']).toMatch(/[?&]code=[-_a-zA-Z0-9]+/);
};

export const expectAccessDenied = (response) => {
  expect(response.headers['location']).toContain(`${SELECTIVE_CONSENT.REDIRECT_URI}?`);
  expect(response.headers['location']).toContain('error=access_denied');
  expect(response.headers['location']).not.toMatch(/[?&]code=/);
};

/** Exchanges the callback's code and returns the access token's scopes, sorted. */
export const exchangeCodeForScopes = async (fixture: SelectiveConsentFixture, callback): Promise<string[]> => {
  const code = new URL(callback.headers['location']).searchParams.get('code');
  const tokenResponse = await performPost(
    fixture.oidc.token_endpoint,
    '',
    `grant_type=authorization_code&code=${code}&redirect_uri=${encodeURIComponent(SELECTIVE_CONSENT.REDIRECT_URI)}`,
    { 'Content-type': 'application/x-www-form-urlencoded', Authorization: 'Basic ' + applicationBase64Token(fixture.app) },
  ).expect(200);
  return (tokenResponse.body.scope as string).split(' ').sort();
};
