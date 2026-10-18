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
import {
  createDomain,
  DomainOidcConfig,
  patchDomain,
  safeDeleteDomain,
  startDomain,
  waitForDomainStart,
} from '@management-commands/domain-management-commands';
import { patchApplication } from '@management-commands/application-management-commands';
import { createScope } from '@management-commands/scope-management-commands';
import { getDefaultIdp } from '@management-commands/idp-management-commands';
import { createUser } from '@management-commands/user-management-commands';
import { createDomainForm } from '@management-commands/form-management-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { extractXsrfTokenAndActionResponse, performFormPost, performGet, performPost } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { createTestApp } from '@utils-commands/application-commands';
import { uniqueName } from '@utils-commands/misc';
import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
import type { ApplicationDeviceFlowSettings } from '@management-models/ApplicationDeviceFlowSettings';
import { Fixture } from '../../../test-fixture';

export const DEVICE_FLOW_TEST = {
  DOMAIN_NAME_PREFIX: 'device-flow',
  CONFIDENTIAL_APP_NAME: 'device-confidential',
  PUBLIC_APP_NAME: 'device-public',
  USER_NAME_PREFIX: 'device-user',
  USER_PASSWORD: '#CoMpL3X-P@SsW0Rd',
  REDIRECT_URI: 'https://gravitee.io/callback',
  SCOPE: 'scope1',
  DEFAULT_SCOPE: 'openid scope1',
  DEVICE_CODE_EXPIRY: 600,
  DEVICE_CODE_GRANT: 'urn:ietf:params:oauth:grant-type:device_code',
  USER_CODE_FORMAT: /^[BCDFGHJKLMNPQRSTVWXZ]{4}-[BCDFGHJKLMNPQRSTVWXZ]{4}$/,
  UNKNOWN_USER_CODE: 'ZZZZ-ZZZZ',
  DEFAULT_WRONG_CODE_BUDGET: 10,
} as const;

const FORM_HEADERS = { 'Content-type': 'application/x-www-form-urlencoded' };

const OVERRIDDEN_CODE_ENTRY_FORM =
  '<!DOCTYPE html><html xmlns:th="http://www.thymeleaf.org"><body><span>overridden code entry page</span>' +
  '<form th:action="${action}" method="post">' +
  '<input type="text" name="user_code"/>' +
  '<input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>' +
  '<button type="submit"></button></form></body></html>';

const OVERRIDDEN_COMPLETION_FORM =
  '<!DOCTYPE html><html xmlns:th="http://www.thymeleaf.org"><body>' +
  '<span>overridden completion page</span><span th:text="${outcome}"></span></body></html>';

export interface DeviceFlowFixtureOptions {
  deviceFlowEnabled?: boolean;
  pollingInterval?: number;
  withOverriddenForms?: boolean;
}

export interface DeviceFlowUser {
  username: string;
  password: string;
}

export interface DeviceAuthorization {
  device_code: string;
  user_code: string;
  verification_uri: string;
  verification_uri_complete: string;
  expires_in: number;
  interval: number;
}

export interface VerificationSession {
  codeEntryPage: { headers: any; token: any; action: string };
  cookie: any;
}

export interface DeviceFlowFixture extends Fixture {
  domain: Domain;
  oidc: DomainOidcConfig;
  confidentialApp: Application;
  publicApp: Application;
  confidentialClientId: string;
  publicClientId: string;
  user: DeviceFlowUser;
  deviceAuthorizationEndpoint: string;
  verificationEndpoint: string;
  confidentialAuth: () => Record<string, string>;
  verificationUri: (clientId: string) => string;
  createUser: () => Promise<DeviceFlowUser>;
  requestDeviceCode: (scope?: string) => Promise<DeviceAuthorization>;
  requestPublicDeviceCode: (scope?: string) => Promise<DeviceAuthorization>;
  poll: (deviceCode: string) => any;
  pollPublic: (deviceCode: string) => any;
  signInOnSecondDevice: (clientId: string, entryUri?: string, as?: DeviceFlowUser) => Promise<VerificationSession>;
  reopenCodeEntryPage: (session: VerificationSession, clientId: string) => Promise<VerificationSession>;
  enterCode: (session: VerificationSession, userCode: string) => any;
  rejectCode: (session: VerificationSession, userCode: string) => any;
  enterCodeAndReachAuthorize: (
    userCode: string,
    clientId: string,
    as?: DeviceFlowUser,
  ) => Promise<{ session: VerificationSession; authorizeUrl: string }>;
  approveThroughConsent: (
    userCode: string,
    clientId: string,
    as: DeviceFlowUser,
  ) => Promise<{ session: VerificationSession; consentPage: any; completion: any }>;
  approveWithRememberedConsent: (userCode: string, clientId: string, as: DeviceFlowUser) => Promise<any>;
  setApplicationTimings: (deviceFlowSettings: ApplicationDeviceFlowSettings | null) => Promise<void>;
}

export const wrongCodeBudget = async (): Promise<number> => {
  const nodeMonitoringUrl = process.env.AM_GATEWAY_NODE_MONITORING_URL;
  const adminUsername = process.env.AM_ADMIN_USERNAME;
  const adminPassword = process.env.AM_ADMIN_PASSWORD;

  if (!nodeMonitoringUrl || !adminUsername || !adminPassword) {
    return DEVICE_FLOW_TEST.DEFAULT_WRONG_CODE_BUDGET;
  }

  const response = await fetch(`${nodeMonitoringUrl}/configuration`, {
    headers: { Authorization: `Basic ${Buffer.from(`${adminUsername}:${adminPassword}`).toString('base64')}` },
  });

  if (!response.ok) {
    return DEVICE_FLOW_TEST.DEFAULT_WRONG_CODE_BUDGET;
  }

  const configuration = await response.json();
  const limit = Number(
    configuration['device_flow_rate.limit'] ?? configuration['DEVICE_FLOW_RATE_LIMIT'] ?? DEVICE_FLOW_TEST.DEFAULT_WRONG_CODE_BUDGET,
  );
  return Number.isNaN(limit) ? DEVICE_FLOW_TEST.DEFAULT_WRONG_CODE_BUDGET : limit;
};

const nextCookie = (response: any, fallback: any) => response.headers['set-cookie'] ?? fallback;

const scopeSettings = [
  { scope: DEVICE_FLOW_TEST.SCOPE, defaultScope: true },
  { scope: 'openid', defaultScope: true },
];

async function configureDomain(domain: Domain, accessToken: string, options: Required<DeviceFlowFixtureOptions>) {
  await createScope(domain.id, accessToken, {
    key: DEVICE_FLOW_TEST.SCOPE,
    name: DEVICE_FLOW_TEST.SCOPE,
    description: DEVICE_FLOW_TEST.SCOPE,
  });

  if (options.deviceFlowEnabled) {
    const patched = await patchDomain(domain.id, accessToken, {
      oidc: {
        deviceFlowSettings: {
          enabled: true,
          deviceCodeExpiry: DEVICE_FLOW_TEST.DEVICE_CODE_EXPIRY,
          pollingInterval: options.pollingInterval,
        },
      },
    });
    expect(patched.oidc.deviceFlowSettings.enabled).toBe(true);
  }

  if (options.withOverriddenForms) {
    await createDomainForm(domain.id, accessToken, { enabled: true, template: 'DEVICE_CODE_ENTRY', content: OVERRIDDEN_CODE_ENTRY_FORM });
    await createDomainForm(domain.id, accessToken, { enabled: true, template: 'DEVICE_COMPLETION', content: OVERRIDDEN_COMPLETION_FORM });
  }
}

async function createApplications(domain: Domain, accessToken: string) {
  const defaultIdp = await getDefaultIdp(domain.id, accessToken);
  const identityProviders = new Set([{ identity: defaultIdp.id, priority: 0 }]);

  const confidentialApp = await createTestApp(uniqueName(DEVICE_FLOW_TEST.CONFIDENTIAL_APP_NAME, true), domain, accessToken, 'WEB', {
    settings: {
      oauth: {
        redirectUris: [DEVICE_FLOW_TEST.REDIRECT_URI],
        grantTypes: [DEVICE_FLOW_TEST.DEVICE_CODE_GRANT, 'refresh_token'],
        scopeSettings,
      },
    },
    identityProviders,
  });

  const publicApp = await createTestApp(uniqueName(DEVICE_FLOW_TEST.PUBLIC_APP_NAME, true), domain, accessToken, 'WEB', {
    settings: {
      oauth: {
        redirectUris: [DEVICE_FLOW_TEST.REDIRECT_URI],
        grantTypes: [DEVICE_FLOW_TEST.DEVICE_CODE_GRANT],
        tokenEndpointAuthMethod: 'none',
        scopeSettings,
      },
    },
    identityProviders,
  });

  return { confidentialApp, publicApp };
}

export const setupDeviceFlowFixture = async (options: DeviceFlowFixtureOptions = {}): Promise<DeviceFlowFixture> => {
  const resolvedOptions: Required<DeviceFlowFixtureOptions> = {
    deviceFlowEnabled: options.deviceFlowEnabled ?? true,
    pollingInterval: options.pollingInterval ?? 0,
    withOverriddenForms: options.withOverriddenForms ?? false,
  };

  let domain: Domain | null = null;
  let accessToken: string | null = null;

  try {
    accessToken = await requestAdminAccessToken();
    domain = await createDomain(accessToken, uniqueName(DEVICE_FLOW_TEST.DOMAIN_NAME_PREFIX, true), 'Device authorization grant');
    expect(domain.id).toEqual(expect.any(String));

    await configureDomain(domain, accessToken, resolvedOptions);
    const { confidentialApp, publicApp } = await createApplications(domain, accessToken);

    await startDomain(domain.id, accessToken);
    const { oidcConfig: oidc } = await waitForDomainStart(domain);

    const domainId = domain.id;
    const token = accessToken;
    const confidentialClientId = confidentialApp.settings.oauth.clientId;
    const publicClientId = publicApp.settings.oauth.clientId;
    const deviceAuthorizationEndpoint = oidc.token_endpoint.replace('/oauth/token', '/oauth/device_authorization');
    const verificationEndpoint = oidc.token_endpoint.replace('/oauth/token', '/oauth/device');

    const confidentialAuth = () => ({ Authorization: 'Basic ' + applicationBase64Token(confidentialApp) });
    const verificationUri = (clientId: string) => `${verificationEndpoint}?client_id=${clientId}`;

    const createDeviceFlowUser = async (): Promise<DeviceFlowUser> => {
      const username = uniqueName(DEVICE_FLOW_TEST.USER_NAME_PREFIX, true);
      const created = await createUser(domainId, token, {
        firstName: 'Device',
        lastName: 'User',
        email: `${username}@example.com`,
        username,
        password: DEVICE_FLOW_TEST.USER_PASSWORD,
        preRegistration: false,
      });
      expect(created.username).toEqual(username);
      return { username, password: DEVICE_FLOW_TEST.USER_PASSWORD };
    };

    const user = await createDeviceFlowUser();

    const requestDeviceCodeWith = async (body: string, headers: Record<string, string>): Promise<DeviceAuthorization> => {
      const response = await performPost(deviceAuthorizationEndpoint, '', body, { ...FORM_HEADERS, ...headers }).expect(200);
      return response.body;
    };

    const requestDeviceCode = (scope: string = DEVICE_FLOW_TEST.DEFAULT_SCOPE) =>
      requestDeviceCodeWith(`scope=${encodeURIComponent(scope)}`, confidentialAuth());

    const requestPublicDeviceCode = (scope: string = 'openid') =>
      requestDeviceCodeWith(`client_id=${publicClientId}&scope=${encodeURIComponent(scope)}`, {});

    const pollWith = (body: string, headers: Record<string, string>) =>
      performPost(oidc.token_endpoint, '', `grant_type=${encodeURIComponent(DEVICE_FLOW_TEST.DEVICE_CODE_GRANT)}&${body}`, {
        ...FORM_HEADERS,
        ...headers,
      });

    const poll = (deviceCode: string) => pollWith(`device_code=${deviceCode}`, confidentialAuth());

    const pollPublic = (deviceCode: string) => pollWith(`client_id=${publicClientId}&device_code=${deviceCode}`, {});

    const signInOnSecondDevice = async (
      clientId: string,
      entryUri = verificationUri(clientId),
      as = user,
    ): Promise<VerificationSession> => {
      const unauthenticated = await performGet(entryUri).expect(302);
      expect(unauthenticated.headers['location']).toContain(`/${domain!.hrid}/login`);

      const loginPage = await extractXsrfTokenAndActionResponse(unauthenticated);
      const postLogin = await performFormPost(
        loginPage.action,
        '',
        { 'X-XSRF-TOKEN': loginPage.token, username: as.username, password: as.password, client_id: clientId },
        { Cookie: loginPage.headers['set-cookie'], ...FORM_HEADERS },
      ).expect(302);
      expect(postLogin.headers['location']).toContain('/oauth/device');

      const codeEntryPage = await extractXsrfTokenAndActionResponse(postLogin);
      return { codeEntryPage, cookie: codeEntryPage.headers['set-cookie'] ?? postLogin.headers['set-cookie'] };
    };

    const reopenCodeEntryPage = async (session: VerificationSession, clientId: string): Promise<VerificationSession> => {
      const codeEntryPage = await extractXsrfTokenAndActionResponse({
        headers: { location: verificationUri(clientId), 'set-cookie': session.cookie },
      });
      return { codeEntryPage, cookie: codeEntryPage.headers['set-cookie'] ?? session.cookie };
    };

    const submitCode = (session: VerificationSession, fields: Record<string, string>) =>
      performFormPost(
        session.codeEntryPage.action,
        '',
        { 'X-XSRF-TOKEN': session.codeEntryPage.token, ...fields },
        { Cookie: session.cookie, ...FORM_HEADERS },
      );

    const enterCode = (session: VerificationSession, userCode: string) => submitCode(session, { user_code: userCode });

    const rejectCode = (session: VerificationSession, userCode: string) => submitCode(session, { user_code: userCode, action: 'reject' });

    const enterCodeAndReachAuthorize = async (userCode: string, clientId: string, as = user) => {
      const session = await signInOnSecondDevice(clientId, verificationUri(clientId), as);
      const postCode = await enterCode(session, userCode).expect(302);
      expect(postCode.headers['location']).toContain('/oauth/authorize');
      return { session: { ...session, cookie: nextCookie(postCode, session.cookie) }, authorizeUrl: postCode.headers['location'] };
    };

    const approveThroughConsent = async (userCode: string, clientId: string, as: DeviceFlowUser) => {
      const { session, authorizeUrl } = await enterCodeAndReachAuthorize(userCode, clientId, as);

      const toConsent = await performGet(authorizeUrl, '', { Cookie: session.cookie }).expect(302);
      expect(toConsent.headers['location']).toContain('/oauth/consent');
      const atConsent = nextCookie(toConsent, session.cookie);

      const consentPage = await performGet(toConsent.headers['location'], '', { Cookie: atConsent }).expect(200);
      const consentForm = await extractXsrfTokenAndActionResponse({
        headers: { location: toConsent.headers['location'], 'set-cookie': atConsent },
      });
      const submitted = nextCookie(consentForm, atConsent);
      const postConsent = await performFormPost(
        consentForm.action,
        '',
        {
          'X-XSRF-TOKEN': consentForm.token,
          [`scope.${DEVICE_FLOW_TEST.SCOPE}`]: true,
          'scope.openid': true,
          user_oauth_approval: true,
        },
        { Cookie: submitted, ...FORM_HEADERS },
      ).expect(302);

      const completedCookie = nextCookie(postConsent, submitted);
      const completion = await performGet(postConsent.headers['location'], '', { Cookie: completedCookie }).expect(200);
      return { session: { ...session, cookie: completedCookie }, consentPage, completion };
    };

    const approveWithRememberedConsent = async (userCode: string, clientId: string, as: DeviceFlowUser) => {
      const { session, authorizeUrl } = await enterCodeAndReachAuthorize(userCode, clientId, as);
      return performGet(authorizeUrl, '', { Cookie: session.cookie }).expect(200);
    };

    const setApplicationTimings = async (deviceFlowSettings: ApplicationDeviceFlowSettings | null) => {
      await waitForSyncAfter(domainId, () =>
        patchApplication(domainId, token, { settings: { oauth: { deviceFlowSettings } } }, confidentialApp.id),
      );
    };

    const cleanUp = async () => {
      await safeDeleteDomain(domainId, token);
    };

    return {
      domain,
      accessToken,
      oidc,
      confidentialApp,
      publicApp,
      confidentialClientId,
      publicClientId,
      user,
      deviceAuthorizationEndpoint,
      verificationEndpoint,
      confidentialAuth,
      verificationUri,
      createUser: createDeviceFlowUser,
      requestDeviceCode,
      requestPublicDeviceCode,
      poll,
      pollPublic,
      signInOnSecondDevice,
      reopenCodeEntryPage,
      enterCode,
      rejectCode,
      enterCodeAndReachAuthorize,
      approveThroughConsent,
      approveWithRememberedConsent,
      setApplicationTimings,
      cleanUp,
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
