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
import { afterAll, beforeAll, expect, it, jest } from '@jest/globals';
import { createDomain, deleteDomain, startDomain, waitForDomainStart } from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import {
  createApplication,
  getApplicationFlows,
  updateApplication,
  updateApplicationFlows,
} from '@management-commands/application-management-commands';

import fetch from 'cross-fetch';
import { buildCreateAndTestUser, deleteUser, deleteUserFactor, getUserFactors } from '@management-commands/user-management-commands';
import { createResource } from '@management-commands/resource-management-commands';
import { extractXsrfTokenAndActionResponse, performDelete, performGet, performPost } from '@gateway-commands/oauth-oidc-commands';
import * as faker from 'faker';
import { loginUserNameAndPassword } from '@gateway-commands/login-commands';
import { uniqueName } from '@utils-commands/misc';
import { lookupFlowAndResetPolicies } from '@management-commands/flow-management-commands';
import { FlowEntityTypeEnum } from '../../../api/management/models';
import { extractSharedSecret, extractSmsCode } from './fixture/mfa-extract-fixture';
import { createCallFactor, createSMSFactor } from './fixture/mfa-setup-fixture';

global.fetch = fetch;

let domain;
let accessToken;
let openIdConfiguration;
let smsFactor;
let app;
let callFactor;

const sfrUrl = 'http://localhost:8181';

jest.setTimeout(200000);

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();
  domain = await createDomain(accessToken, uniqueName('mfa-test-double-enroll', true), 'mfa test domain');

  smsFactor = await createSFRResource(domain, accessToken).then((sfrResource) => createSMSFactor(domain, accessToken, sfrResource));

  callFactor = await createTwilioResource(domain, accessToken).then((twilioResource) =>
    createCallFactor(domain, accessToken, twilioResource, 'Call factor1'),
  );

  app = await createApp(domain, accessToken, smsFactor.id, callFactor.id);

  let started = await startDomain(domain.id, accessToken).then(waitForDomainStart);
  domain = started.domain;
  openIdConfiguration = started.oidcConfig;
});

describe('MFA double enrollment scenario', () => {
  let user;

  it('When user enrolls and verify SMS factor, the CALL factor is added along', async () => {
    user = await buildCreateAndTestUser(domain.id, accessToken, 1);
    expect(user).toBeDefined();

    const loginResponse = await loginUserNameAndPassword(
      app.settings.oauth.clientId,
      user,
      user.password,
      false,
      openIdConfiguration,
      domain,
    );
    const phoneNumber = '+33780763733';
    const enroll = await enrollSmsFactor(loginResponse, smsFactor, domain, phoneNumber);
    const authorize = await performGet(enroll.headers['location'], '', {
      Cookie: enroll.headers['set-cookie'],
    }).expect(302);
    expect(authorize.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/mfa/challenge`);

    const verify = await verifySmsSfrFactor(authorize, smsFactor);

    expect(verify.status).toBe(302);
    expect(verify.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/oauth/authorize`);

    const userFactors = await getUserFactors(domain.id, accessToken, user.id);
    expect(userFactors.some((f) => f.id === smsFactor.id)).toBe(true);
    expect(userFactors.some((f) => f.id === callFactor.id)).toBe(true);
  });

  it('When user has already enrolled factor, no new factor is added', async () => {
    await deleteUserFactor(domain.id, accessToken, user.id, callFactor.id);

    const loginResponse = await loginUserNameAndPassword(
      app.settings.oauth.clientId,
      user,
      user.password,
      false,
      openIdConfiguration,
      domain,
    );

    const verify = await verifySmsSfrFactor(loginResponse, smsFactor);

    expect(verify.status).toBe(302);
    expect(verify.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/oauth/authorize`);

    const userFactors = await getUserFactors(domain.id, accessToken, user.id);
    expect(userFactors.some((f) => f.id === smsFactor.id)).toBe(true);
    expect(userFactors.some((f) => f.id === callFactor.id)).toBe(false);
  });

  afterAll(async () => {
    await deleteUser(domain.id, accessToken, user.id);
  });
});

afterAll(async () => {
  if (domain && domain.id) {
    await deleteDomain(domain.id, accessToken);
    await performDelete(sfrUrl, '/__admin/requests', {});
  }
});

const enrollSmsFactor = async (authResponse, factor, domain, phoneNumber) => {
  const result = await performGet(authResponse.headers['location'], '', { Cookie: authResponse.headers['set-cookie'] }).expect(200);
  const extractedResult = extractSharedSecret(result);

  const enrollMfa = await performPost(
    extractedResult.action,
    '',
    {
      factorId: factor.id,
      sharedSecret: extractedResult.sharedSecret,
      user_mfa_enrollment: true,
      phone: phoneNumber,
      'X-XSRF-TOKEN': extractedResult.token,
    },
    {
      Cookie: result.headers['set-cookie'],
      'Content-type': 'application/x-www-form-urlencoded',
    },
  );

  expect(enrollMfa.headers['location']).toBeDefined();
  expect(enrollMfa.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/oauth/authorize`);

  return enrollMfa;
};

const createSFRResource = async (domain, accessToken) => {
  const sfrResource = await createResource(domain.id, accessToken, {
    name: 'sfr',
    type: 'sfr-am-resource',
    configuration: JSON.stringify({
      serviceHost: sfrUrl + '/sfr',
      serviceId: '1',
      servicePassword: '1',
      spaceId: '1',
      connectTimeout: 10000,
      idleTimeout: 10000,
      maxPoolSize: 200,
    }),
  });
  expect(sfrResource).toBeDefined();
  expect(sfrResource).not.toBeNull();
  expect(sfrResource.id).not.toBeNull();
  return sfrResource;
};

const createTwilioResource = async (domain, accessToken) => {
  const twilioResource = await createResource(domain.id, accessToken, {
    name: 'twilio',
    type: 'twilio-verify-am-resource',
    configuration: JSON.stringify({
      accountSid: 'test-account-sid',
      sid: 'test-sid',
      authToken: 'test-auth-token',
      useSystemProxy: false,
    }),
  });
  expect(twilioResource).toBeDefined();
  expect(twilioResource.id).not.toBeNull();
  return twilioResource;
};

const createApp = async (domain, accessToken, smsFactorId, callFactorId) => {
  const application = await createApplication(domain.id, accessToken, {
    name: 'mfaEnrollFlowApp',
    type: 'WEB',
    clientId: faker.internet.domainWord(),
    redirectUris: ['https://auth-nightly.gravitee.io/myApp/callback'],
  }).then((app) =>
    updateApplication(
      domain.id,
      accessToken,
      {
        settings: {
          oauth: {
            redirectUris: ['https://auth-nightly.gravitee.io/myApp/callback'],
            scopeSettings: [{ scope: 'openid', defaultScope: true }],
          },
          mfa: {
            factor: {
              defaultFactorId: smsFactorId,
              applicationFactors: [
                { id: smsFactorId, selectionRule: '' },
                { id: callFactorId, selectionRule: '' },
              ],
            },
            enroll: {
              active: true,
              forceEnrollment: true,
              type: 'REQUIRED',
            },
            challenge: {
              active: true,
              type: 'REQUIRED',
            },
          },
          advanced: {
            flowsInherited: false,
          },
        },
        identityProviders: [{ identity: `default-idp-${domain.id}`, priority: -1 }],
      },
      app.id,
    ),
  );

  const flows = await getApplicationFlows(domain.id, accessToken, application.id);

  lookupFlowAndResetPolicies(flows, FlowEntityTypeEnum.MfaChallenge, 'post', [
    {
      name: 'enrollMFAPostChallenge1',
      policy: 'policy-am-enroll-mfa',
      description: 'Auto enroll call factor',
      condition: "{#context.attributes['enrolledFactorInit']}",
      enabled: true,
      configuration: JSON.stringify({
        primary: false,
        refresh: false,
        factorId: callFactorId,
        value: "{#context.attributes['user'].enrolledFactorsByType['SMS'].channel.target}",
      }),
    },
    {
      name: 'enrollMFAPostChallenge2',
      policy: 'policy-am-enroll-mfa',
      description: 'Auto enroll sms factor',
      condition: "{#context.attributes['enrolledFactorInit']}",
      enabled: true,
      configuration: JSON.stringify({
        primary: false,
        refresh: false,
        factorId: callFactorId,
        value: "{#context.attributes['user'].enrolledFactorsByType['CALL'].channel.target}",
      }),
    },
  ]);

  await updateApplicationFlows(domain.id, accessToken, application.id, flows);
  return application;
};
const verifySmsSfrFactor = async (challenge, factor) => {
  const challengeResponse = await extractXsrfTokenAndActionResponse(challenge);
  const code = await extractSmsCode(sfrUrl);
  return await postFactor(challengeResponse, factor, code);
};

const postFactor = async (response, factor, code): Promise<any> => {
  return await performPost(
    response.action,
    '',
    {
      factorId: factor.id,
      code: code,
      'X-XSRF-TOKEN': response.token,
    },
    {
      Cookie: response.headers['set-cookie'],
      'Content-type': 'application/x-www-form-urlencoded',
    },
  ).expect(302);
};
