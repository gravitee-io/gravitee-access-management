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
  DomainOidcConfig,
  patchDomain,
  safeDeleteDomain,
  startDomain,
  waitFor,
  waitForDomainStart,
} from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createUser, getUser } from '@management-commands/user-management-commands';
import { retryUntil } from '@utils-commands/retry';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { getDefaultIdp } from '@management-commands/idp-management-commands';
import { extractXsrfTokenAndHref, performGet, performPost, performPut } from '@gateway-commands/oauth-oidc-commands';
import { initiateLoginFlow } from '@gateway-commands/login-commands';
import { clearEmails, waitForEmail } from '@utils-commands/email-commands';
import { uniqueName } from '@utils-commands/misc';
import { Domain } from '@management-models/Domain';
import { Fixture } from '../../../test-fixture';

/** One magic link request and everything a test needs to follow it up. */
export interface LinkRequest {
  /** Raw response to the email submission on the magic link login page. */
  response: any;
  /** Cookies from that response — the link is bound to this session. */
  cookie: any;
}

export interface MagicLinkGatewayFixture extends Fixture {
  accessToken: string;
  domain: Domain;
  oidc: DomainOidcConfig;
  app: any;
  user: any;
  userEmail: string;

  /** Submits an email on the magic link login page and returns the response plus its session. */
  requestLink: (email?: string) => Promise<LinkRequest>;
  /** Waits for the magic link email and returns the href from it. */
  lastLink: (email?: string) => Promise<string>;
  /** Follows a magic link. Omit the cookie to simulate a different browser session. */
  useLink: (link: string, cookie?: any) => Promise<any>;
  /**
   * Follows a magic link and then the redirect it issues, which is what actually completes the
   * login. Returns the final response, whose location carries the authorization code.
   */
  completeLogin: (link: string, cookie: any) => Promise<any>;
  /**
   * Waits until the login has been recorded against the user.
   *
   * A link is treated as single-use by comparing the user's last login against the token's
   * `iat`, and that write is asynchronous. A test that reuses a link before it lands sees the
   * reuse succeed, so anything asserting on a *previously used* link must wait for this first.
   */
  waitForLoginRecorded: (afterMillis?: number) => Promise<void>;
  /** Turns the domain-level magic link toggle on or off. */
  setMagicLinkEnabled: (enabled: boolean) => Promise<Domain>;
  /** The magic link login page URL for this domain's app. */
  loginPageUrl: () => string;
  /**
   * Sets the domain's MAGIC_LINK email template, whose `expiresAfter` overrides the gateway's
   * gravitee.yaml default. Returns the created template.
   */
  setDomainTemplate: (opts: { expiresAfter?: number; subject?: string }) => Promise<any>;
  /** Sets the same template on the application, which takes precedence over the domain's. */
  setApplicationTemplate: (opts: { expiresAfter?: number; subject?: string }) => Promise<any>;
  /** Waits until a freshly issued link carries the expected lifetime in seconds. */
  waitForLinkLifetime: (seconds: number) => Promise<void>;
}

// Unique per fixture. Every spec using this fixture shares the mail server, and waitForEmail
// matches on the address, so a shared address lets one spec pick up another's link.
const uniqueEmail = () => `${uniqueName('magiclink', true)}@test.com`;

export const setupFixture = async (domainPrefix = 'magic-link-gw'): Promise<MagicLinkGatewayFixture> => {
  const accessToken = await requestAdminAccessToken();
  const USER_EMAIL = uniqueEmail();

  const created = await createDomain(accessToken, uniqueName(domainPrefix, true), 'Magic Link gateway coverage');
  await patchDomain(created.id, accessToken, { loginSettings: { magicLinkAuthEnabled: true } });
  const started = await startDomain(created.id, accessToken).then(waitForDomainStart);
  const domain = started.domain;
  const oidc = started.oidcConfig;

  const defaultIdp = await getDefaultIdp(domain.id, accessToken);

  const app = await createApplication(domain.id, accessToken, {
    name: uniqueName('magic-link-app', true),
    type: 'WEB',
    redirectUris: ['https://test'],
  }).then((created) =>
    updateApplication(
      domain.id,
      accessToken,
      {
        settings: { oauth: { redirectUris: ['https://test'], grantTypes: ['authorization_code'] } },
        identityProviders: [{ identity: defaultIdp.id, priority: -1 }],
      },
      created.id,
    ),
  );

  const user = await createUser(domain.id, accessToken, {
    firstName: 'Magic',
    lastName: 'Link',
    email: USER_EMAIL,
    username: uniqueName('magiclink', true),
    password: 'Password123!',
    client: app.id,
    source: defaultIdp.id,
  });

  // The app and user have to reach the gateway before the login page will offer magic link.
  await waitFor(5000);

  const clientId = app.settings.oauth.clientId;
  const managementBase = `${process.env.AM_MANAGEMENT_URL}/management/organizations/DEFAULT/environments/DEFAULT`;

  /**
   * Creates the MAGIC_LINK template at the given collection, or updates it when one is already
   * there. The API rejects a second create for the same template with a 400.
   */
  const upsertTemplate = async (collectionUrl: string, { expiresAfter, subject }: { expiresAfter?: number; subject?: string }) => {
    const headers = { 'Content-type': 'application/json', Authorization: `Bearer ${accessToken}` };
    const body = {
      template: 'MAGIC_LINK',
      enabled: true,
      from: 'no-reply@test.com',
      subject: subject ?? 'Sign in',
      content: '<a href="${url}">link</a>',
      ...(expiresAfter !== undefined ? { expiresAfter } : {}),
    };

    const created = await performPost(collectionUrl, '', body, headers);
    if (created.status === 201) return created.body;

    const existing = await performGet(`${collectionUrl}?template=MAGIC_LINK`, '', headers);
    const id = existing.body?.id;
    expect(id).toBeDefined();
    // The update endpoint takes UpdateEmail, which has no template or id and requires
    // expiresAfter, so the create body cannot be reused as-is.
    const updated = await performPut(
      `${collectionUrl}/${id}`,
      '',
      {
        content: body.content,
        enabled: true,
        expiresAfter: expiresAfter ?? existing.body?.expiresAfter ?? 900,
        from: body.from,
        subject: body.subject,
      },
      headers,
    );
    expect(updated.status).toEqual(200);
    return updated.body;
  };

  return {
    accessToken,
    domain,
    oidc,
    app,
    user,
    userEmail: USER_EMAIL,

    loginPageUrl: () =>
      `${process.env.AM_GATEWAY_URL}/${domain.hrid}/magic-link/login` +
      `?client_id=${encodeURIComponent(clientId)}&response_type=code&redirect_uri=${encodeURIComponent('https://test')}`,

    setDomainTemplate: async ({ expiresAfter, subject = 'Sign in' }) =>
      upsertTemplate(`${managementBase}/domains/${domain.id}/emails`, { expiresAfter, subject }),

    setApplicationTemplate: async ({ expiresAfter, subject = 'Sign in' }) =>
      upsertTemplate(`${managementBase}/domains/${domain.id}/applications/${app.id}/emails`, { expiresAfter, subject }),

    requestLink: async (email = USER_EMAIL) => {
      await clearEmails(email);
      const authResponse = await initiateLoginFlow(clientId, oidc, domain, 'code', 'https://test');
      const loginPage = await extractXsrfTokenAndHref(authResponse, 'magicLinkLinkButton');
      const response = await performPost(loginPage.action, '', `email=${encodeURIComponent(email)}`, {
        Cookie: loginPage.headers['set-cookie'],
      });
      return { response, cookie: response.headers['set-cookie'] };
    },

    lastLink: async (email = USER_EMAIL) => (await waitForEmail(email)).extractLink(),

    useLink: async (link: string, cookie?: any) => performGet(link, '', cookie ? { Cookie: cookie } : {}),

    waitForLinkLifetime: async (seconds: number) => {
      // A new template has to reach the gateway before it affects the links it issues.
      await retryUntil(
        async () => {
          const email = uniqueEmail();
          await clearEmails(USER_EMAIL);
          const authResponse = await initiateLoginFlow(clientId, oidc, domain, 'code', 'https://test');
          const loginPage = await extractXsrfTokenAndHref(authResponse, 'magicLinkLinkButton');
          await performPost(loginPage.action, '', `email=${encodeURIComponent(USER_EMAIL)}`, {
            Cookie: loginPage.headers['set-cookie'],
          });
          const link = (await waitForEmail(USER_EMAIL)).extractLink();
          const payload = JSON.parse(Buffer.from(new URL(link).searchParams.get('token').split('.')[1], 'base64url').toString());
          return payload.exp - payload.iat;
        },
        (lifetime) => lifetime === seconds,
        { timeoutMillis: 60000, intervalMillis: 2000 },
      );
    },

    waitForLoginRecorded: async (afterMillis = 0) => {
      await retryUntil(
        async () => getUser(domain.id, accessToken, user.id),
        (u: any) => !!u.loggedAt && new Date(u.loggedAt).getTime() >= afterMillis,
        { timeoutMillis: 30000, intervalMillis: 500 },
      );
    },

    completeLogin: async (link: string, cookie: any) => {
      const verified = await performGet(link, '', { Cookie: cookie });
      return performGet(verified.headers['location'], '', { Cookie: verified.headers['set-cookie'] });
    },

    setMagicLinkEnabled: async (enabled: boolean) =>
      patchDomain(domain.id, accessToken, { loginSettings: { magicLinkAuthEnabled: enabled } }),

    cleanUp: async () => {
      if (domain?.id && accessToken) {
        await safeDeleteDomain(domain.id, accessToken);
      }
    },
  };
};

/** The `iat` of a magic link's token, in milliseconds. */
export const tokenIatMillis = (link: string): number => {
  const token = new URL(link).searchParams.get('token');
  const payload = JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString());
  return payload.iat * 1000;
};

/** Pulls the error_description out of a gateway redirect or response. */
export const errorDescriptionOf = (response: any): string | null => {
  const raw = response.body?.url ?? response.headers?.location ?? response.request?.url;
  if (!raw) return null;
  return new URL(String(raw), 'http://localhost').searchParams.get('error_description');
};

/** Asserts a response is a redirect carrying the given error_description. */
export const expectErrorDescription = (response: any, expected: string): void => {
  const description = errorDescriptionOf(response);
  expect(description).toEqual(expected);
};
