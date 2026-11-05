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
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { safeDeleteDomain, setupDomainForTest } from '@management-commands/domain-management-commands';
import { createApplication, patchApplication } from '@management-commands/application-management-commands';
import { createUser } from '@management-commands/user-management-commands';
import { getDefaultIdp } from '@management-commands/idp-management-commands';
import { createAuthorizationDetailType } from '@management-commands/authorization-detail-type-management-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { getWellKnownOpenIdConfiguration, performGet } from '@gateway-commands/oauth-oidc-commands';
import { login } from '@gateway-commands/login-commands';
import { uniqueName } from '@utils-commands/misc';
import { Application } from '@management-models/Application';
import { ApplicationAuthorizationDetailTypeSettings } from '@management-models/ApplicationAuthorizationDetailTypeSettings';
import { AuthorizationDetailType } from '@management-models/AuthorizationDetailType';
import { Domain } from '@management-models/Domain';
import { NewAuthorizationDetailType } from '@management-models/NewAuthorizationDetailType';
import { Fixture } from '../../../test-fixture';

export const AUTHORIZATION_DETAILS_TEST = {
  DOMAIN_NAME_PREFIX: 'authz-details',
  REDIRECT_URI: 'https://example.com/callback',
  STATE: 'authz-details-state',
  USERNAME: 'authzdetailsuser',
  PASSWORD: 'AuthzDetailsP@ssw0rd123!',
  /** Has a schema, advertised in discovery, enabled on the application. */
  PAYMENT_TYPE: 'payment_initiation',
  /** No schema, advertised in discovery, enabled on the application. */
  ACCOUNT_TYPE: 'account_information',
  /** No schema, not advertised, not enabled on the application. */
  NOT_ENABLED_TYPE: 'internal_transfer',
} as const;

export const paymentSchema = (type: string): string =>
  JSON.stringify({
    type: 'object',
    required: ['type', 'instructedAmount'],
    properties: {
      type: { const: type },
      instructedAmount: {
        type: 'object',
        required: ['currency', 'amount'],
        properties: { currency: { type: 'string', pattern: '^[A-Z]{3}$' }, amount: { type: 'string' } },
      },
    },
  });

export const payment = (currency = 'EUR', amount = '123.50') => ({
  type: AUTHORIZATION_DETAILS_TEST.PAYMENT_TYPE,
  instructedAmount: { currency, amount },
});

export const account = (extra: Record<string, unknown> = {}) => ({ type: AUTHORIZATION_DETAILS_TEST.ACCOUNT_TYPE, ...extra });

export interface AuthorizationDetailsFixture extends Fixture {
  domain: Domain;
  accessToken: string;
  application: Application;
  clientId: string;
  authorizationEndpoint: string;
  /** Sends an authorization request with the application's defaults, overridden by `params`. */
  authorize: (params?: Record<string, string>) => Promise<any>;
  /** Logs the user in from an authorization response that sent them to the login page. */
  loginFrom: (authorizeResponse: any) => Promise<any>;
  discovery: () => Promise<Record<string, unknown>>;
  createType: (type: NewAuthorizationDetailType) => Promise<AuthorizationDetailType>;
  /** Replaces the types enabled on the application and waits for the gateway to apply them. */
  enableTypes: (types: string[]) => Promise<void>;
}

export interface AuthorizationDetailsFixtureOptions {
  /** Create the three reference types and enable two of them on the application. Defaults to true. */
  withTypes?: boolean;
}

export const setupAuthorizationDetailsFixture = async (
  options: AuthorizationDetailsFixtureOptions = {},
): Promise<AuthorizationDetailsFixture> => {
  const { withTypes = true } = options;
  let domain: Domain | null = null;
  let accessToken: string | null = null;
  try {
    accessToken = await requestAdminAccessToken();
    const setupResult = await setupDomainForTest(uniqueName(AUTHORIZATION_DETAILS_TEST.DOMAIN_NAME_PREFIX, true), {
      accessToken,
      waitForStart: true,
    });
    domain = setupResult.domain;
    const oidcConfig = setupResult.oidcConfig;
    const token = accessToken;
    const domainId = domain.id;
    const domainHrid = domain.hrid;

    const defaultIdp = await getDefaultIdp(domainId, token);
    const clientId = uniqueName('authz-details-app', true);
    const application = await createApplication(domainId, token, {
      name: clientId,
      type: 'WEB',
      clientId,
      redirectUris: [AUTHORIZATION_DETAILS_TEST.REDIRECT_URI],
    });

    const createType = (type: NewAuthorizationDetailType) => createAuthorizationDetailType(domainId, token, type);
    if (withTypes) {
      await createType({
        type: AUTHORIZATION_DETAILS_TEST.PAYMENT_TYPE,
        name: 'Payment initiation',
        schema: paymentSchema(AUTHORIZATION_DETAILS_TEST.PAYMENT_TYPE),
        discovery: true,
      });
      await createType({ type: AUTHORIZATION_DETAILS_TEST.ACCOUNT_TYPE, name: 'Account information', discovery: true });
      await createType({ type: AUTHORIZATION_DETAILS_TEST.NOT_ENABLED_TYPE, name: 'Internal transfer', discovery: false });
    }

    const typeSettings = (types: string[]): ApplicationAuthorizationDetailTypeSettings[] => types.map((type) => ({ type }));
    await waitForSyncAfter(domainId, () =>
      patchApplication(
        domainId,
        token,
        {
          settings: {
            oauth: {
              redirectUris: [AUTHORIZATION_DETAILS_TEST.REDIRECT_URI],
              grantTypes: ['authorization_code', 'implicit'],
              responseTypes: ['code', 'token'],
              scopeSettings: [{ scope: 'openid', defaultScope: true }],
              authorizationDetailTypes: withTypes
                ? typeSettings([AUTHORIZATION_DETAILS_TEST.PAYMENT_TYPE, AUTHORIZATION_DETAILS_TEST.ACCOUNT_TYPE])
                : [],
            },
            advanced: { skipConsent: true },
          },
          identityProviders: new Set([{ identity: defaultIdp.id, priority: 0 }]),
        },
        application.id,
      ),
    );

    await createUser(domainId, token, {
      firstName: 'Authorization',
      lastName: 'Details',
      email: 'authz.details@test.com',
      username: AUTHORIZATION_DETAILS_TEST.USERNAME,
      password: AUTHORIZATION_DETAILS_TEST.PASSWORD,
      client: application.id,
      source: defaultIdp.id,
      preRegistration: false,
    });

    const authorize = (params: Record<string, string> = {}) => {
      const query = new URLSearchParams({
        response_type: 'code',
        client_id: clientId,
        redirect_uri: AUTHORIZATION_DETAILS_TEST.REDIRECT_URI,
        state: AUTHORIZATION_DETAILS_TEST.STATE,
        ...params,
      });
      return performGet(oidcConfig.authorization_endpoint, `?${query.toString()}`).expect(302);
    };

    return {
      domain,
      accessToken: token,
      application,
      clientId,
      authorizationEndpoint: oidcConfig.authorization_endpoint,
      authorize,
      loginFrom: async (authorizeResponse) => {
        const loginResponse = await login(
          authorizeResponse,
          AUTHORIZATION_DETAILS_TEST.USERNAME,
          clientId,
          AUTHORIZATION_DETAILS_TEST.PASSWORD,
        );
        return performGet(loginResponse.headers.location, '', { Cookie: loginResponse.headers['set-cookie'] }).expect(302);
      },
      discovery: async () => (await getWellKnownOpenIdConfiguration(domainHrid).expect(200)).body,
      createType: (type) => waitForSyncAfter(domainId, () => createType(type)),
      enableTypes: async (types) => {
        await waitForSyncAfter(domainId, () =>
          patchApplication(domainId, token, { settings: { oauth: { authorizationDetailTypes: typeSettings(types) } } }, application.id),
        );
      },
      cleanUp: async () => {
        await safeDeleteDomain(domainId, token);
      },
    };
  } catch (error) {
    if (domain && accessToken) {
      try {
        await safeDeleteDomain(domain.id, accessToken);
      } catch (cleanupError) {
        console.error('Failed to clean up after fixture setup error:', cleanupError);
      }
    }
    throw error;
  }
};

/** The query (or fragment) parameters of a redirect back to the client. */
export const redirectParams = (response: any, separator: '?' | '#' = '?'): URLSearchParams => {
  const location: string = response.headers.location;
  expect(location.startsWith(`${AUTHORIZATION_DETAILS_TEST.REDIRECT_URI}${separator}`)).toBe(true);
  return new URLSearchParams(location.substring(location.indexOf(separator) + 1));
};

export const expectClientError = (response: any, error: string, description: string, separator: '?' | '#' = '?'): void => {
  const params = redirectParams(response, separator);
  expect(Object.fromEntries(params)).toEqual({ error, error_description: description, state: AUTHORIZATION_DETAILS_TEST.STATE });
};

export const expectLoginPage = (response: any): void => {
  const location = new URL(response.headers.location);
  expect(location.pathname).toMatch(/\/login$/);
  expect(location.searchParams.has('error')).toBe(false);
};

export const toDetailsParam = (...elements: unknown[]): string => JSON.stringify(elements);
