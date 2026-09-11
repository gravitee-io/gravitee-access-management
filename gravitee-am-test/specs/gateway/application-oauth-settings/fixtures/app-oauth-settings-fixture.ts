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
import { DomainOidcConfig, safeDeleteDomain, setupDomainForTest } from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createApplication, patchApplication, updateApplication } from '@management-commands/application-management-commands';
import { createIdp, deleteIdp } from '@management-commands/idp-management-commands';
import { createScope } from '@management-commands/scope-management-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { extractXsrfTokenAndActionResponse, performFormPost, performGet, performPost } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { uniqueName } from '@utils-commands/misc';
import { Fixture } from '../../../test-fixture';

export const APP_OAUTH_SETTINGS = {
  DOMAIN_PREFIX: 'app-oauth-settings',
  REDIRECT_URI: 'https://example.com/callback',
  // Domain scopes with no expiry of their own, so only the application setting drives consent duration
  CONSENT_SCOPE: 'orders',
  TOGGLE_SCOPE: 'reports',
  SHORT_CONSENT_SECONDS: 2,
  LONG_CONSENT_SECONDS: 3600,
  REFRESH_VALIDITY_SECONDS: 5,
  DEFAULT_ACCESS_TOKEN_VALIDITY_SECONDS: 7200,
  USER_PASSWORD: '#CoMpL3X-P@SsW0Rd',
} as const;

export interface TestUser {
  username: string;
  password: string;
}

export interface AppOAuthSettingsFixture extends Fixture {
  domain: Domain;
  oidc: DomainOidcConfig;
  // authorization_code app whose consent scope carries an application-level approval duration
  consentApp: Application;
  // password-grant app whose scope list is added to and removed from by the tests
  scopeApp: Application;
  // password + refresh_token app with a deliberately short refresh token validity
  refreshApp: Application;
  users: TestUser[];
}

export const setupAppOAuthSettingsFixture = async (): Promise<AppOAuthSettingsFixture> => {
  const accessToken = await requestAdminAccessToken();
  let domain: Domain | null = null;
  try {
    const started = await setupDomainForTest(uniqueName(APP_OAUTH_SETTINGS.DOMAIN_PREFIX, true), { accessToken, waitForStart: true });
    domain = started.domain;

    await deleteIdp(domain.id, accessToken, 'default-idp-' + domain.id);
    const users: TestUser[] = [0, 1].map((i) => ({
      username: uniqueName(`oauth-settings-user-${i}`, true),
      password: APP_OAUTH_SETTINGS.USER_PASSWORD,
    }));
    const idp = await createIdp(domain.id, accessToken, {
      external: false,
      type: 'inline-am-idp',
      domainWhitelist: [],
      configuration: JSON.stringify({
        users: users.map((u) => ({ firstname: 'OAuth', lastname: 'Settings', username: u.username, password: u.password })),
      }),
      name: 'app-oauth-settings-idp',
    });
    const identityProviders = [{ identity: idp.id, priority: 0 }];

    for (const key of [APP_OAUTH_SETTINGS.CONSENT_SCOPE, APP_OAUTH_SETTINGS.TOGGLE_SCOPE]) {
      const scope = await createScope(domain.id, accessToken, { key, name: key, description: key });
      expect(scope.key).toEqual(key);
    }

    const consentApp = await createTestApp(domain, accessToken, 'consent-app', {
      grantTypes: ['authorization_code'],
      scopeSettings: [
        { scope: APP_OAUTH_SETTINGS.CONSENT_SCOPE, defaultScope: true, scopeApproval: APP_OAUTH_SETTINGS.SHORT_CONSENT_SECONDS },
      ],
      identityProviders,
    });
    const scopeApp = await createTestApp(domain, accessToken, 'scope-app', {
      grantTypes: ['password'],
      scopeSettings: [{ scope: 'openid', defaultScope: true }],
      identityProviders,
    });
    // The last mutation is wrapped so the gateway has picked up everything created above
    const refreshApp = await waitForSyncAfter(domain.id, () =>
      createTestApp(domain, accessToken, 'refresh-app', {
        grantTypes: ['password', 'refresh_token'],
        scopeSettings: [{ scope: 'openid', defaultScope: true }],
        refreshTokenValiditySeconds: APP_OAUTH_SETTINGS.REFRESH_VALIDITY_SECONDS,
        identityProviders,
      }),
    );

    return {
      accessToken,
      domain,
      oidc: started.oidcConfig,
      consentApp,
      scopeApp,
      refreshApp,
      users,
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

interface AppOAuthOptions {
  grantTypes: string[];
  scopeSettings: Array<{ scope: string; defaultScope: boolean; scopeApproval?: number }>;
  refreshTokenValiditySeconds?: number;
  identityProviders: Array<{ identity: string; priority: number }>;
}

async function createTestApp(domain: Domain, accessToken: string, name: string, options: AppOAuthOptions): Promise<Application> {
  const created = await createApplication(domain.id, accessToken, {
    name: uniqueName(name, true),
    type: 'WEB',
    redirectUris: [APP_OAUTH_SETTINGS.REDIRECT_URI],
  });
  const oauth: Record<string, unknown> = {
    redirectUris: [APP_OAUTH_SETTINGS.REDIRECT_URI],
    grantTypes: options.grantTypes,
    scopeSettings: options.scopeSettings,
  };
  if (options.refreshTokenValiditySeconds !== undefined) {
    oauth.refreshTokenValiditySeconds = options.refreshTokenValiditySeconds;
  }
  const updated = await updateApplication(
    domain.id,
    accessToken,
    { settings: { oauth }, identityProviders: new Set(options.identityProviders) },
    created.id,
  );
  // The PUT response omits the secret; keep the one issued on creation for Basic auth
  updated.settings.oauth.clientSecret = created.settings.oauth.clientSecret;
  return updated;
}

/** Replaces the application's scope list and waits for the gateway to pick it up. */
export const setScopeSettings = (
  fixture: AppOAuthSettingsFixture,
  app: Application,
  scopeSettings: Array<{ scope: string; defaultScope: boolean; scopeApproval?: number }>,
) =>
  waitForSyncAfter(fixture.domain.id, () =>
    patchApplication(fixture.domain.id, fixture.accessToken, { settings: { oauth: { scopeSettings } } }, app.id),
  );

export const requestPasswordToken = (fixture: AppOAuthSettingsFixture, app: Application, user: TestUser, scope?: string) =>
  performPost(
    fixture.oidc.token_endpoint,
    '',
    `grant_type=password&username=${user.username}&password=${encodeURIComponent(user.password)}` + (scope ? `&scope=${scope}` : ''),
    { 'Content-type': 'application/x-www-form-urlencoded', Authorization: 'Basic ' + applicationBase64Token(app) },
  );

export const requestRefreshedToken = (fixture: AppOAuthSettingsFixture, app: Application, refreshToken: string) =>
  performPost(fixture.oidc.token_endpoint, '', `grant_type=refresh_token&refresh_token=${refreshToken}`, {
    'Content-type': 'application/x-www-form-urlencoded',
    Authorization: 'Basic ' + applicationBase64Token(app),
  });

export interface AuthorizedSession {
  // Gateway session cookie of the signed-in user, for re-running /oauth/authorize without logging in again
  cookie: string | string[];
}

/** Starts an authorization_code request; with a session cookie the login step is skipped. */
export const authorize = (fixture: AppOAuthSettingsFixture, app: Application, session?: AuthorizedSession) => {
  const clientId = app.settings.oauth.clientId;
  const params = `?response_type=code&client_id=${clientId}&redirect_uri=${APP_OAUTH_SETTINGS.REDIRECT_URI}`;
  return performGet(fixture.oidc.authorization_endpoint, params, session ? { Cookie: session.cookie } : null).expect(302);
};

export const consentPageUrl = (fixture: AppOAuthSettingsFixture) => `${process.env.AM_GATEWAY_URL}/${fixture.domain.hrid}/oauth/consent`;

/**
 * Signs the user in, approves the requested scopes on the consent page and returns the session
 * along with the final redirect back to the application (which must carry an authorization code).
 */
export const signInAndConsent = async (fixture: AppOAuthSettingsFixture, app: Application, user: TestUser, scopes: string[]) => {
  const authResponse = await authorize(fixture, app);
  expect(authResponse.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${fixture.domain.hrid}/login`);

  const loginForm = await extractXsrfTokenAndActionResponse(authResponse);
  const postLogin = await performFormPost(
    loginForm.action,
    '',
    { 'X-XSRF-TOKEN': loginForm.token, username: user.username, password: user.password, client_id: app.settings.oauth.clientId },
    { Cookie: loginForm.headers['set-cookie'], 'Content-type': 'application/x-www-form-urlencoded' },
  ).expect(302);
  const session: AuthorizedSession = { cookie: postLogin.headers['set-cookie'] };

  const postLoginRedirect = await performGet(postLogin.headers['location'], '', { Cookie: session.cookie }).expect(302);
  expect(postLoginRedirect.headers['location']).toContain(consentPageUrl(fixture));

  const consentForm = await extractXsrfTokenAndActionResponse(postLoginRedirect);
  const approvals = Object.fromEntries(scopes.map((scope) => [`scope.${scope}`, true]));
  const postConsent = await performFormPost(
    consentForm.action,
    '',
    { 'X-XSRF-TOKEN': consentForm.token, ...approvals, user_oauth_approval: true },
    { Cookie: consentForm.headers['set-cookie'], 'Content-type': 'application/x-www-form-urlencoded' },
  ).expect(302);

  const callback = await performGet(postConsent.headers['location'], '', { Cookie: session.cookie }).expect(302);
  return { session, callback };
};

export const expectAuthorizationCodeRedirect = (response) => {
  expect(response.headers['location']).toContain(`${APP_OAUTH_SETTINGS.REDIRECT_URI}?`);
  expect(response.headers['location']).toMatch(/code=[-_a-zA-Z0-9]+/);
};
