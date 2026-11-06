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
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { safeDeleteDomain, setupDomainForTest } from '@management-commands/domain-management-commands';
import { createApplication, patchApplication } from '@management-commands/application-management-commands';
import { createIdp, defaultIdpId, deleteIdp } from '@management-commands/idp-management-commands';
import { createScope } from '@management-commands/scope-management-commands';
import { createAuthorizationDetailType } from '@management-commands/authorization-detail-type-management-commands';
import { auditIdsOfType, auditsOfType, AuditEntry } from '@management-commands/audit-management-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { extractXsrfTokenAndActionResponse, performFormPost, performGet, performPost } from '@gateway-commands/oauth-oidc-commands';
import { getBase64BasicAuth } from '@gateway-commands/utils';
import { retryUntil } from '@utils-commands/retry';
import { uniqueName } from '@utils-commands/misc';
import { Application } from '@management-models/Application';
import { Domain } from '@management-models/Domain';
import { TokenClaim } from '@management-models/TokenClaim';
import { Fixture } from '../../../test-fixture';
import { AUTHORIZATION_DETAILS_TEST, account, paymentSchema, payment } from './authorization-details-fixture';

export { account, payment };

export const CONSENT_TEST = {
  DOMAIN_NAME_PREFIX: 'authz-details-consent',
  REDIRECT_URI: AUTHORIZATION_DETAILS_TEST.REDIRECT_URI,
  STATE: 'authz-details-consent-state',
  /** A scope the user has to consent to. */
  SCOPE: 'orders',
  USER_COUNT: 16,
  PASSWORD: '#CoMpL3X-P@SsW0Rd',
  CONSENTED: 'USER_CONSENT_CONSENTED',
} as const;

export interface TestUser {
  username: string;
  password: string;
}

export interface ConsentFixture extends Fixture {
  domain: Domain;
  accessToken: string;
  application: Application;
  clientId: string;
  /** Hands out a user that no other test has consented with. */
  nextUser: () => TestUser;
  /** Sends an authorization request; with a cookie, the user is already signed in. */
  authorize: (params: Record<string, string>, cookie?: string) => Promise<Step>;
  /** Signs the user in and returns their session cookie and the redirect that follows login. */
  signIn: (user: TestUser, params: Record<string, string>) => Promise<Step>;
  setSkipConsent: (skipConsent: boolean) => Promise<void>;
  /** Replaces the application's custom token claims. */
  setTokenCustomClaims: (tokenCustomClaims: TokenClaim[]) => Promise<void>;
  /** Sends a token request authenticated as the application. */
  requestToken: (params: Record<string, string>) => Promise<any>;
  /** Exchanges the code a callback carries for tokens, expecting success. */
  exchangeCode: (callback: Step) => Promise<any>;
  /** USER_CONSENT_CONSENTED audit ids, as a baseline before an action. */
  consentAuditIds: () => Promise<Set<string>>;
  /** Waits until the USER_CONSENT_CONSENTED audits not in `knownIds` reach `until` in number, or satisfy it, and returns them. */
  newConsentAudits: (knownIds: Set<string>, until: number | ((audits: AuditEntry[]) => boolean)) => Promise<AuditEntry[]>;
}

/** A gateway response along with the session cookie to send with the next request. */
export interface Step {
  response: any;
  cookie: string;
}

export interface ConsentPage {
  kind: 'scopes' | 'authorization-details';
  action: string;
  token: string;
  cookie: string;
  /** Scope consent: the scopes offered. */
  scopes: string[];
  /** Authorization detail consent: each detail's type name, type description and the names of its other fields. */
  details: { name: string; description: string; fields: string[] }[];
  /** Names of the form's inputs and buttons. */
  fieldNames: string[];
}

export interface ConsentFixtureOptions {
  skipConsent?: boolean;
}

export const setupConsentFixture = async (options: ConsentFixtureOptions = {}): Promise<ConsentFixture> => {
  const accessToken = await requestAdminAccessToken();
  let domain: Domain | null = null;
  try {
    const started = await setupDomainForTest(uniqueName(CONSENT_TEST.DOMAIN_NAME_PREFIX, true), { accessToken, waitForStart: true });
    domain = started.domain;
    const domainId = domain.id;
    const oidcConfig = started.oidcConfig;

    await deleteIdp(domainId, accessToken, defaultIdpId(domainId));
    const users: TestUser[] = Array.from({ length: CONSENT_TEST.USER_COUNT }, (_, i) => ({
      username: uniqueName(`authz-consent-user-${i}`, true),
      password: CONSENT_TEST.PASSWORD,
    }));
    const idp = await createIdp(domainId, accessToken, {
      external: false,
      type: 'inline-am-idp',
      domainWhitelist: [],
      configuration: JSON.stringify({
        users: users.map((u) => ({ firstname: 'Consent', lastname: 'User', username: u.username, password: u.password })),
      }),
      name: 'authz-consent-idp',
    });
    await createScope(domainId, accessToken, { key: CONSENT_TEST.SCOPE, name: CONSENT_TEST.SCOPE, description: CONSENT_TEST.SCOPE });
    await createAuthorizationDetailType(domainId, accessToken, {
      type: AUTHORIZATION_DETAILS_TEST.PAYMENT_TYPE,
      name: 'Payment initiation',
      description: 'Initiate a payment from your account',
      schema: paymentSchema(AUTHORIZATION_DETAILS_TEST.PAYMENT_TYPE),
      discovery: true,
    });
    await createAuthorizationDetailType(domainId, accessToken, {
      type: AUTHORIZATION_DETAILS_TEST.ACCOUNT_TYPE,
      name: 'Account information',
      discovery: true,
    });

    const clientId = uniqueName('authz-consent-app', true);
    const clientSecret = uniqueName('authz-consent-secret', true);
    const application = await createApplication(domainId, accessToken, {
      name: clientId,
      type: 'WEB',
      clientId,
      clientSecret,
      redirectUris: [CONSENT_TEST.REDIRECT_URI],
    });
    const patchAdvanced = (skipConsent: boolean) =>
      waitForSyncAfter(domainId, () =>
        patchApplication(domainId, accessToken, { settings: { advanced: { skipConsent } } }, application.id),
      );
    await waitForSyncAfter(domainId, () =>
      patchApplication(
        domainId,
        accessToken,
        {
          settings: {
            oauth: {
              redirectUris: [CONSENT_TEST.REDIRECT_URI],
              grantTypes: ['authorization_code', 'implicit', 'refresh_token'],
              responseTypes: ['code', 'token', 'code token'],
              // not a default scope, so a request without scope requests none
              scopeSettings: [{ scope: CONSENT_TEST.SCOPE, defaultScope: false }],
              authorizationDetailTypes: [
                { type: AUTHORIZATION_DETAILS_TEST.PAYMENT_TYPE },
                { type: AUTHORIZATION_DETAILS_TEST.ACCOUNT_TYPE },
              ],
            },
            advanced: { skipConsent: options.skipConsent ?? false },
          },
          identityProviders: new Set([{ identity: idp.id, priority: 0 }]),
        },
        application.id,
      ),
    );

    const authorize = async (params: Record<string, string>, cookie?: string): Promise<Step> => {
      const query = new URLSearchParams({
        response_type: 'code',
        client_id: clientId,
        redirect_uri: CONSENT_TEST.REDIRECT_URI,
        state: CONSENT_TEST.STATE,
        ...params,
      });
      const response = await performGet(oidcConfig.authorization_endpoint, `?${query.toString()}`, cookie ? { Cookie: cookie } : null);
      expect(response.status).toBe(302);
      return { response, cookie: mergeCookies(cookie, response.headers['set-cookie']) };
    };

    const requestToken = (params: Record<string, string>) =>
      performPost(oidcConfig.token_endpoint, '', new URLSearchParams(params).toString(), {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + getBase64BasicAuth(clientId, clientSecret),
      });

    let handedOut = 0;
    return {
      domain,
      accessToken,
      application,
      clientId,
      nextUser: () => {
        if (handedOut >= users.length) {
          throw new Error(`All ${users.length} fixture users are in use; raise CONSENT_TEST.USER_COUNT`);
        }
        return users[handedOut++];
      },
      authorize,
      signIn: async (user, params) => {
        const loginForm = await extractXsrfTokenAndActionResponse((await authorize(params)).response);
        const postLogin = await performFormPost(
          loginForm.action,
          '',
          { 'X-XSRF-TOKEN': loginForm.token, username: user.username, password: user.password, client_id: clientId },
          { Cookie: loginForm.headers['set-cookie'], 'Content-type': 'application/x-www-form-urlencoded' },
        ).expect(302);
        const cookie = mergeCookies(loginForm.headers['set-cookie'], postLogin.headers['set-cookie']);
        return follow({ response: postLogin, cookie });
      },
      setSkipConsent: async (skipConsent) => {
        await patchAdvanced(skipConsent);
      },
      setTokenCustomClaims: async (tokenCustomClaims) => {
        await waitForSyncAfter(domainId, () =>
          patchApplication(domainId, accessToken, { settings: { oauth: { tokenCustomClaims } } }, application.id),
        );
      },
      requestToken,
      exchangeCode: async (callback) => {
        const response = await requestToken({
          grant_type: 'authorization_code',
          code: callbackParams(callback).get('code'),
          redirect_uri: CONSENT_TEST.REDIRECT_URI,
        });
        expect(response.status).toBe(200);
        return response;
      },
      consentAuditIds: () => auditIdsOfType(domainId, accessToken, CONSENT_TEST.CONSENTED),
      newConsentAudits: (knownIds, until) =>
        retryUntil(
          async () => (await auditsOfType(domainId, accessToken, CONSENT_TEST.CONSENTED)).filter((audit) => !knownIds.has(audit.id)),
          typeof until === 'number' ? (audits) => audits.length >= until : until,
          { timeoutMillis: 30000, intervalMillis: 500 },
        ),
      cleanUp: async () => {
        await safeDeleteDomain(domainId, accessToken);
      },
    };
  } catch (error) {
    if (domain?.id) {
      await safeDeleteDomain(domain.id, accessToken).catch((e) => console.error('Cleanup failed:', e));
    }
    throw error;
  }
};

/**
 * Merges Cookie headers (strings) and Set-Cookie lists (arrays) into one Cookie header; later ones win for cookies
 * with the same name.
 */
export const mergeCookies = (...sources: Array<string | string[] | undefined>): string => {
  const jar = new Map<string, string>();
  for (const source of sources) {
    const pairs = Array.isArray(source) ? source.map((cookie) => cookie.split(';')[0]) : (source ?? '').split('; ');
    for (const pair of pairs.filter((value) => value.includes('='))) {
      jar.set(pair.substring(0, pair.indexOf('=')), pair);
    }
  }
  return [...jar.values()].join('; ');
};

/** Follows a redirect within the gateway, keeping the session cookie. */
export const follow = async (step: Step): Promise<Step> => {
  const response = await performGet(step.response.headers['location'], '', { Cookie: step.cookie });
  return { response, cookie: mergeCookies(step.cookie, response.headers['set-cookie']) };
};

export const isConsentPage = (step: Step): boolean => new URL(step.response.headers['location']).pathname.endsWith('/oauth/consent');

/** Opens the consent page a redirect points to. */
export const openConsentPage = async (step: Step): Promise<ConsentPage> => {
  expect(isConsentPage(step)).toBe(true);
  const { response, cookie } = await follow(step);
  expect(response.status).toBe(200);
  const dom = cheerio.load(response.text);
  const details = dom('table.authorization-detail-consent-table')
    .map((_, table) => ({
      name: dom(table).find('.authorization-detail-consent-type > span').first().text(),
      description: dom(table).find('.authorization-detail-consent-type-description').text(),
      fields: dom(table)
        .find('.scope-consent-cell-name')
        .map((__, cell) => dom(cell).text())
        .get(),
    }))
    .get();
  return {
    kind: details.length > 0 ? 'authorization-details' : 'scopes',
    action: dom('form').attr('action'),
    token: dom('[name=X-XSRF-TOKEN]').val() as string,
    cookie,
    scopes: dom('input.scope-consent-checkbox')
      .map((_, el) =>
        dom(el)
          .attr('name')
          .replace(/^scope\./, ''),
      )
      .get(),
    details,
    fieldNames: dom('form [name]')
      .map((_, el) => dom(el).attr('name'))
      .get(),
  };
};

/** Submits a consent page and returns the redirect that follows. */
export const submitConsent = async (page: ConsentPage, approval: boolean, scopes: string[] = [], action = page.action): Promise<Step> => {
  const scopeFields = Object.fromEntries(scopes.map((scope) => [`scope.${scope}`, true]));
  const response = await performFormPost(
    action,
    '',
    { 'X-XSRF-TOKEN': page.token, ...scopeFields, user_oauth_approval: approval },
    { Cookie: page.cookie, 'Content-type': 'application/x-www-form-urlencoded' },
  ).expect(302);
  return { response, cookie: mergeCookies(page.cookie, response.headers['set-cookie']) };
};

/** The parameters of the redirect back to the client, from the query or the fragment. */
export const callbackParams = (step: Step, separator: '?' | '#' = '?'): URLSearchParams => {
  const location: string = step.response.headers['location'];
  expect(location.startsWith(`${CONSENT_TEST.REDIRECT_URI}${separator}`)).toBe(true);
  return new URLSearchParams(location.substring(location.indexOf(separator) + 1));
};

export const expectCode = (step: Step): void => {
  const params = callbackParams(step);
  expect(params.get('code')).toMatch(/^[-_a-zA-Z0-9]+$/);
  expect(params.get('state')).toEqual(CONSENT_TEST.STATE);
};

export const expectError = (step: Step, error: string, separator: '?' | '#' = '?'): void => {
  const params = callbackParams(step, separator);
  expect(params.get('error')).toEqual(error);
  expect(params.get('state')).toEqual(CONSENT_TEST.STATE);
  expect(params.has('code')).toBe(false);
};

/** The consent recorded by a USER_CONSENT_CONSENTED audit, read from its outcome. */
export const recordedConsent = (audit: AuditEntry): any[] => JSON.parse(audit.outcome.message).map((change) => change.value);

/** Replaces the authorization_details parameter of a gateway URL. */
export const withAuthorizationDetails = (url: string, authorizationDetails: string): string => {
  const parsed = new URL(url);
  parsed.searchParams.set('authorization_details', authorizationDetails);
  return parsed.toString();
};
