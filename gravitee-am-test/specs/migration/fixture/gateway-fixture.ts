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
  extractXsrfTokenAndActionResponse,
  getWellKnownOpenIdConfiguration,
  performFormPost,
  performGet,
  performPost,
} from '@gateway-commands/oauth-oidc-commands';
import { getBase64BasicAuth } from '@gateway-commands/utils';
import { initiateLoginFlow, login } from '@gateway-commands/login-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { expect } from '@jest/globals';
import { TOTP } from 'otpauth';
import { extractSharedSecret } from '../../gateway/mfa/fixture/mfa-extract-fixture';
import { getApplicationApi, getDomainApi, getUserApi } from '../../../api/commands/management/service/utils';
import { Application } from '../../../api/management/models/Application';
import { Domain } from '../../../api/management/models/Domain';
import {
  getApplicationName,
  getApplicationWithCustomIdpName,
  getConsentApplicationName,
  getCustomIdpUserName,
  getDefaultStoreUserName,
  getDomainName,
  getServiceApplicationName,
  JWT_BEARER_GRANT_TYPE,
} from '../../../migration-seeding/seed';

export type GatewayFixtureOptions = {
  applicationName?: string;
  userName?: string;
  password?: string;
};

const DEFAULT_SEED_PASSWORD = 'SomeP@ssw0rd';

export type GatewayFixture = {
  application: Application;
  domain: Domain;
  loginUser: () => Promise<any>;
  loginUserWithMfa: () => Promise<any>;
  loginUserAndConsent: () => Promise<any>;
  requestToken: (finalAuthorizeResponse: any) => Promise<any>;
  requestJwtBearerToken: (assertion: string) => Promise<any>;
  introspectToken: (accessToken: string) => Promise<any>;
};

export function createCustomIdpGatewayFixture(label: string): Promise<GatewayFixture> {
  return createGatewayFixture(label, {
    applicationName: getApplicationWithCustomIdpName(label),
    userName: getCustomIdpUserName(label),
  });
}

export function createConsentGatewayFixture(label: string): Promise<GatewayFixture> {
  return createGatewayFixture(label, {
    applicationName: getConsentApplicationName(label),
  });
}

export function createExtensionGrantGatewayFixture(label: string): Promise<GatewayFixture> {
  return createGatewayFixture(label, {
    applicationName: getServiceApplicationName(label),
  });
}

export async function createGatewayFixture(label: string, options: GatewayFixtureOptions = {}): Promise<GatewayFixture> {
  const domainName = getDomainName(label);
  const applicationName = options.applicationName ?? getApplicationName(label);
  const userName = options.userName ?? getDefaultStoreUserName(label);
  const userPassword = options.password ?? DEFAULT_SEED_PASSWORD;
  const accessToken = await requestAdminAccessToken();
  const domainApi = getDomainApi(accessToken);
  const applicationApi = getApplicationApi(accessToken);
  const userApi = getUserApi(accessToken);
  const organizationId = process.env.AM_DEF_ORG_ID;
  const environmentId = process.env.AM_DEF_ENV_ID;
  const domains = await domainApi.listDomains({ organizationId, environmentId, q: domainName });
  const domain = domains.data?.find((candidate) => candidate.name === domainName);

  expect(domain).toBeDefined();
  const applications = await applicationApi.listApplications({
    organizationId,
    environmentId,
    domain: domain.id,
    q: applicationName,
  });
  const matchedApplication = applications.data?.find((candidate) => candidate.name === applicationName);

  expect(matchedApplication).toBeDefined();
  const application = await applicationApi.findApplication({
    organizationId,
    environmentId,
    domain: domain.id,
    application: matchedApplication.id,
  });
  const openIdConfiguration = (await getWellKnownOpenIdConfiguration(domain.hrid)).body;

  async function loginUser(scope?: string) {
    const authResponse = await startAuthorize(scope);

    return login(authResponse, userName, application.settings.oauth.clientId, userPassword);
  }

  async function startAuthorize(scope?: string) {
    if (!scope) {
      return initiateLoginFlow(application.settings.oauth.clientId, openIdConfiguration, domain);
    }
    const clientId = application.settings.oauth.clientId;
    const redirectUri = application.settings.oauth.redirectUris[0];
    const params = `?response_type=code&client_id=${clientId}&redirect_uri=${redirectUri}&scope=${encodeURIComponent(scope)}`;

    return performGet(openIdConfiguration.authorization_endpoint, params).expect(302);
  }

  async function loginUserWithMfa() {
    const afterMfaResponse = await loginAndCompleteMfa();

    expect(afterMfaResponse.headers['location']).toContain('code=');
    return afterMfaResponse;
  }

  async function loginUserAndConsent() {
    await revokeUserConsents();
    const postLogin = await loginUser('openid email');
    const consentLocationResponse = await performGet(postLogin.headers['location'], '', {
      Cookie: postLogin.headers['set-cookie'],
    }).expect(302);

    expect(consentLocationResponse.headers['location']).toContain('/oauth/consent');
    const consentResult = await extractXsrfTokenAndActionResponse(consentLocationResponse);
    const consentResponse = await performFormPost(
      consentResult.action,
      '',
      {
        'scope.openid': true,
        'scope.email': true,
        user_oauth_approval: true,
        'X-XSRF-TOKEN': consentResult.token,
      },
      {
        Cookie: consentResult.headers['set-cookie'],
        'Content-type': 'application/x-www-form-urlencoded',
      },
    ).expect(302);
    const finalAuthorizeResponse = await performGet(consentResponse.headers['location'], '', {
      Cookie: consentResponse.headers['set-cookie'],
    }).expect(302);

    expect(finalAuthorizeResponse.headers['location']).toContain('code=');
    return finalAuthorizeResponse;
  }

  async function loginAndCompleteMfa() {
    await resetUserFactors();
    const postLogin = await loginUser();
    const enrollLocationResponse = await performGet(postLogin.headers['location'], '', {
      Cookie: postLogin.headers['set-cookie'],
    }).expect(302);

    expect(enrollLocationResponse.headers['location']).toContain('/mfa/enroll');
    const enrollPage = await performGet(enrollLocationResponse.headers['location'], '', {
      Cookie: enrollLocationResponse.headers['set-cookie'],
    }).expect(200);
    const enrollResult = extractSharedSecret(enrollPage, 'TOTP');
    const enrollResponse = await performFormPost(
      enrollResult.action,
      '',
      {
        factorId: getFactorId(),
        sharedSecret: enrollResult.sharedSecret,
        user_mfa_enrollment: true,
        'X-XSRF-TOKEN': enrollResult.token,
      },
      {
        Cookie: enrollPage.headers['set-cookie'],
        'Content-type': 'application/x-www-form-urlencoded',
      },
    ).expect(302);
    const challengeLocationResponse = await performGet(enrollResponse.headers['location'], '', {
      Cookie: enrollResponse.headers['set-cookie'],
    }).expect(302);

    return completeMfaChallenge(challengeLocationResponse, enrollResult.sharedSecret);
  }

  async function completeMfaChallenge(challengeLocationResponse: any, sharedSecret: string) {
    const challengeResult = await extractXsrfTokenAndActionResponse(challengeLocationResponse);
    const challengeResponse = await performFormPost(
      challengeResult.action,
      '',
      {
        factorId: getFactorId(),
        code: new TOTP({ issuer: 'Gravitee.io', secret: sharedSecret }).generate(),
        'X-XSRF-TOKEN': challengeResult.token,
      },
      {
        Cookie: challengeResult.headers['set-cookie'],
        'Content-type': 'application/x-www-form-urlencoded',
      },
    ).expect(302);

    return performGet(challengeResponse.headers['location'], '', {
      Cookie: challengeResponse.headers['set-cookie'],
    }).expect(302);
  }

  async function requestToken(finalAuthorizeResponse: any) {
    const authorizationCode = new URL(finalAuthorizeResponse.headers['location']).searchParams.get('code');

    expect(authorizationCode).toBeDefined();
    return performPost(
      openIdConfiguration.token_endpoint,
      `?grant_type=authorization_code&code=${authorizationCode}&redirect_uri=${application.settings.oauth.redirectUris[0]}`,
      null,
      {
        Authorization: `Basic ${getBase64BasicAuth(application.settings.oauth.clientId, application.settings.oauth.clientId)}`,
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    ).expect(200);
  }

  async function requestJwtBearerToken(assertion: string) {
    return performPost(openIdConfiguration.token_endpoint, `?grant_type=${JWT_BEARER_GRANT_TYPE}&assertion=${assertion}`, null, {
      Authorization: `Basic ${getBase64BasicAuth(application.settings.oauth.clientId, application.settings.oauth.clientId)}`,
      'Content-Type': 'application/x-www-form-urlencoded',
    }).expect(200);
  }

  async function introspectToken(accessToken: string) {
    const response = await performPost(openIdConfiguration.introspection_endpoint, `?token=${accessToken}`, null, {
      Authorization: `Basic ${getBase64BasicAuth(application.settings.oauth.clientId, application.settings.oauth.clientId)}`,
      'Content-Type': 'application/x-www-form-urlencoded',
    }).expect(200);

    return response.body;
  }

  async function findUser() {
    const users = await userApi.listUsers({ organizationId, environmentId, domain: domain.id, q: userName });
    const user = users.data?.find((candidate) => candidate.username === userName);

    expect(user).toBeDefined();
    return user;
  }

  async function resetUserFactors() {
    const user = await findUser();
    const enrolledFactors = await userApi.listUserEnrolledFactors({ organizationId, environmentId, domain: domain.id, user: user.id });
    await Promise.all(
      enrolledFactors.map((factor) =>
        userApi.deleteUserFactor({ organizationId, environmentId, domain: domain.id, user: user.id, factor: factor.id }),
      ),
    );
  }

  async function revokeUserConsents() {
    const user = await findUser();
    await userApi.revokeUserConsents({ organizationId, environmentId, domain: domain.id, user: user.id });
  }

  function getFactorId() {
    const factorId = application.settings?.mfa?.factor?.defaultFactorId;

    expect(factorId).toBeDefined();
    return factorId;
  }

  return {
    application,
    domain,
    loginUser,
    loginUserWithMfa,
    loginUserAndConsent,
    requestToken,
    requestJwtBearerToken,
    introspectToken,
  };
}
