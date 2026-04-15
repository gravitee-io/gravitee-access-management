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
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import {
  setupDomainForTest,
  safeDeleteDomain,
  DomainOidcConfig,
} from '@management-commands/domain-management-commands';
import { waitForNextSync } from '@gateway-commands/monitoring-commands';
import { createApplication, patchApplication } from '@management-commands/application-management-commands';
import { createIdp, updateIdp } from '@management-commands/idp-management-commands';
import { createFactor } from '@management-commands/factor-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { performGet, performFormPost } from '@gateway-commands/oauth-oidc-commands';
import crossFetch from 'cross-fetch';
import { Fixture } from '../../../test-fixture';

const cheerio = require('cheerio');

export const TEST_CONSTANTS = {
  DOMAIN_NAME_PREFIX: 'mfa-type-mismatch',
  USER_PASSWORD: 'SomeP@ssw0rd!123',
  REDIRECT_URI: 'https://auth-nightly.gravitee.io/myApp/callback',
} as const;

export interface AppInfo {
  app: Application;
  clientId: string;
}

export interface TypeMismatchFixture extends Fixture {
  domain: Domain;
  oidcConfig: DomainOidcConfig;
  booleanApp: AppInfo;
  stringApp: AppInfo;
  cleanup: () => Promise<void>;
  loginAndGetRedirect: (clientId: string, username: string) => Promise<any>;
}

const wiremockAdminUrl = (process.env.SFR_URL || 'http://localhost:8181') + '/__admin/mappings';
const wiremockInternalUrl = process.env.INTERNAL_SFR_URL || 'http://wiremock:8080';

/** Register a WireMock stub that simulates an HTTP IdP auth endpoint. */
async function registerAuthStub(urlPath: string, username: string, mfaEnabledValue: boolean | string): Promise<void> {
  // Build a static response body with the correct JSON type for mfaEnabled.
  // No Handlebars templates — just fixed JSON to avoid template issues.
  const responseBody = JSON.stringify({
    id: 'fixed-user-id-123',
    email: username,
    username: username,
    securityInfo: {
      mfaEnabled: mfaEnabledValue,
    },
  });

  const response = await crossFetch(wiremockAdminUrl, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      request: { method: 'POST', url: urlPath },
      response: {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
        body: responseBody,
      },
    }),
  });
  expect(response.ok).toBe(true);
}

/** Create an HTTP IdP that authenticates against a WireMock endpoint and maps mfaEnabled. */
async function createHttpIdp(domainId: string, accessToken: string, name: string, authPath: string): Promise<any> {
  const idpName = uniqueName(name, true);
  const idp = await createIdp(domainId, accessToken, {
    name: idpName,
    type: 'http-am-idp',
    external: false,
    domainWhitelist: [],
    configuration: JSON.stringify({
      authenticationResource: {
        baseURL: `${wiremockInternalUrl}${authPath}`,
        httpMethod: 'POST',
        httpHeaders: [{ name: 'Content-Type', value: 'application/json' }],
        httpBody: '{"userName":"{#principal}","password":"{#credentials}"}',
        httpResponseErrorConditions: [
          {
            value: '{#authenticationResponse.status == 401}',
            exception: 'io.gravitee.am.common.exception.authentication.BadCredentialsException',
          },
        ],
      },
      usersResource: { enabled: false },
      connectTimeout: 10000,
      maxPoolSize: 200,
    }),
  });
  expect(idp.id).toEqual(expect.any(String));

  // Mappers can't be set during creation — update the IdP to add them
  await updateIdp(domainId, accessToken, {
    name: idpName,
    type: 'http-am-idp',
    configuration: idp.configuration,
    mappers: {
      sub: 'id',
      email: 'email',
      preferred_username: 'username',
      // Extracts mfaEnabled from external IdP response — preserves the JSON type (boolean or string).
      mfaEnabled: "{#jsonPath(#profile, '$.securityInfo.mfaEnabled')}",
    },
  }, idp.id);

  return idp;
}

/** Create a WEB application with conditional MFA enrollment checking mfaEnabled != 'true'. */
async function createConditionalMfaApp(
  domainId: string,
  accessToken: string,
  name: string,
  idpId: string,
  factorId: string,
): Promise<AppInfo> {
  const appName = uniqueName(name, true);
  const app = await createApplication(domainId, accessToken, {
    name: appName,
    type: 'WEB',
    clientId: appName,
    redirectUris: [TEST_CONSTANTS.REDIRECT_URI],
  });

  // First assign the IdP via patch
  await patchApplication(domainId, accessToken, {
    identityProviders: [{ identity: idpId, priority: 0 }],
  }, app.id);

  // Then configure MFA settings
  const updated = await patchApplication(domainId, accessToken, {
    settings: {
      oauth: {
        redirectUris: [TEST_CONSTANTS.REDIRECT_URI],
        grantTypes: ['authorization_code'],
        scopeSettings: [{ scope: 'openid', defaultScope: true }],
      },
      mfa: {
        factor: {
          defaultFactorId: factorId,
          applicationFactors: [{ id: factorId, selectionRule: '' }],
        },
        enroll: {
          active: true,
          type: 'conditional',
          forceEnrollment: true,
          // Compares against string 'true'. Boolean true != string 'true' in SpEL.
          enrollmentRule: "{#context.attributes['user']['additionalInformation']['mfaEnabled'] != 'true'}",
          enrollmentSkipActive: false,
          enrollmentSkipRule: '',
        },
        challenge: { active: true, type: 'REQUIRED' },
      },
    },
  }, app.id);
  return { app: updated, clientId: appName };
}

/** Perform an authorization code login flow and return the last 302 redirect after login POST. */
async function doLoginAndGetRedirect(
  domainHrid: string,
  clientId: string,
  username: string,
  password: string,
): Promise<any> {
  const authUrl = `${process.env.AM_GATEWAY_URL}/${domainHrid}/oauth/authorize?response_type=code&client_id=${clientId}&redirect_uri=${encodeURIComponent(TEST_CONSTANTS.REDIRECT_URI)}`;

  // Follow redirects until we reach the login page (200)
  let resp = await performGet(authUrl, '').expect(302);
  let loginPage = resp;
  for (let i = 0; i < 5 && resp.status === 302; i++) {
    loginPage = await performGet(resp.headers['location'], '', {
      Cookie: resp.headers['set-cookie'],
    });
    if (loginPage.status !== 302) break;
    resp = loginPage;
  }
  expect(loginPage.status).toBe(200);

  const dom = cheerio.load(loginPage.text);
  const xsrf = dom('[name=X-XSRF-TOKEN]').val();
  const action = dom('form').attr('action');
  expect(xsrf).toEqual(expect.any(String));
  expect(action).toEqual(expect.any(String));

  const loginPost = await performFormPost(action, '', {
    'X-XSRF-TOKEN': xsrf,
    username,
    password,
    client_id: clientId,
  }, {
    Cookie: loginPage.headers['set-cookie'],
    'Content-type': 'application/x-www-form-urlencoded',
  }).expect(302);

  // Follow the redirect chain after login until the final 302 before a 200 page.
  // This tells us where the user ends up: /mfa/enroll, /mfa/challenge, or callback with code=.
  let current = loginPost;
  for (let i = 0; i < 10; i++) {
    const next = await performGet(current.headers['location'], '', {
      Cookie: current.headers['set-cookie'],
    });
    if (next.status !== 302) {
      // We reached a page (200). Return the last 302 that brought us here.
      return current;
    }
    current = next;
  }
  return current;
}

/** Clean up all WireMock stubs and request logs. */
async function cleanupWireMock(): Promise<void> {
  const wiremockUrl = process.env.SFR_URL || 'http://localhost:8181';
  try {
    await crossFetch(`${wiremockUrl}/__admin/mappings`, { method: 'DELETE' });
    await crossFetch(`${wiremockUrl}/__admin/requests`, { method: 'DELETE' });
  } catch {
    // best-effort
  }
}

export const setupTypeMismatchFixture = async (): Promise<TypeMismatchFixture> => {
  let domain: Domain | null = null;
  let accessToken: string | null = null;

  try {
    accessToken = await requestAdminAccessToken();

    const domainResult = await setupDomainForTest(
      uniqueName(TEST_CONSTANTS.DOMAIN_NAME_PREFIX, true),
      { accessToken, waitForStart: true },
    );
    domain = domainResult.domain;
    const oidcConfig = domainResult.oidcConfig;

    // WireMock stubs: one returns boolean true, the other string "true"
    await registerAuthStub('/idp/auth-boolean', 'boolean-user@test.com', true);
    await registerAuthStub('/idp/auth-string', 'string-user@test.com', 'true');

    const booleanIdp = await createHttpIdp(domain.id, accessToken, 'idp-boolean', '/idp/auth-boolean');
    const stringIdp = await createHttpIdp(domain.id, accessToken, 'idp-string', '/idp/auth-string');

    const mockFactor = await createFactor(domain.id, accessToken, {
      type: 'mock-am-factor',
      factorType: 'MOCK',
      configuration: '{"code": "1234"}',
      name: uniqueName('mock-factor', true),
    });
    expect(mockFactor.id).toEqual(expect.any(String));

    const booleanApp = await createConditionalMfaApp(domain.id, accessToken, 'app-boolean', booleanIdp.id, mockFactor.id);
    const stringApp = await createConditionalMfaApp(domain.id, accessToken, 'app-string', stringIdp.id, mockFactor.id);

    // Apps, IdPs, and factors were created after domain start — wait for two sync cycles
    // to ensure the gateway fully deploys the HTTP IdPs (deployment happens async after sync).
    await waitForNextSync(domain.id);
    await waitForNextSync(domain.id);

    const cleanup = async () => {
      if (domain?.id && accessToken) {
        await safeDeleteDomain(domain.id, accessToken);
      }
      await cleanupWireMock();
    };

    const loginAndGetRedirect = (clientId: string, username: string) =>
      doLoginAndGetRedirect(domain.hrid, clientId, username, TEST_CONSTANTS.USER_PASSWORD);

    return {
      domain,
      accessToken,
      oidcConfig,
      booleanApp,
      stringApp,
      cleanup,
      loginAndGetRedirect,
    };
  } catch (error) {
    if (domain?.id && accessToken) {
      try { await safeDeleteDomain(domain.id, accessToken); } catch { /* cleanup best-effort */ }
    }
    await cleanupWireMock();
    throw error;
  }
};
