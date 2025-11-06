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
import { createDomain, safeDeleteDomain, startDomain, waitForDomainStart } from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import {
  createApplication,
  getApplicationFlows,
  updateApplication,
  updateApplicationFlows,
} from '@management-commands/application-management-commands';

import fetch from 'cross-fetch';
import { buildCreateAndTestUser, deleteUser, getUserFactors } from '@management-commands/user-management-commands';
import { createResource } from '@management-commands/resource-management-commands';
import {
  extractXsrfTokenAndActionResponse,
  logoutUser,
  performDelete,
  performGet,
  performPost,
} from '@gateway-commands/oauth-oidc-commands';
import { clearEmails, getLastEmail } from '@utils-commands/email-commands';
import { TOTP } from 'otpauth';
import * as faker from 'faker';
import { initiateLoginFlow, login, loginUserNameAndPassword } from '@gateway-commands/login-commands';
import { uniqueName } from '@utils-commands/misc';
import { lookupFlowAndResetPolicies } from '@management-commands/flow-management-commands';
import { FlowEntityTypeEnum } from '../../../api/management/models';
import { extractSharedSecret, extractSmsCode } from './fixture/mfa-extract-fixture';
import {
  createCallFactor,
  createEmailFactor,
  createMockFactor,
  createOtpFactor,
  createRecoveryCodeFactor,
  createSMSFactor,
} from './fixture/mfa-setup-fixture';

const cheerio = require('cheerio');

global.fetch = fetch;

let domain;
let accessToken;
let mockFactor;
let emailFactor;
let openIdConfiguration;
let recoveryCodeFactor;
let recoveryCodeTestApp;
let emailTestApp;
let totpFactor;
let totpApp;
let smsSfrFactor;
let smsSfrTestApp;
let mfaFlowApp;
let mfaEnrollFlowApp;
let callFactor1;
let callFactor2;

const validMFACode = '333333';
const sharedSecret = 'K546JFR2PK5CGQLLUTFG4W46IKDFWWUE';
const sfrUrl = 'http://localhost:8181';

jest.setTimeout(200000);

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();
  domain = await createDomain(accessToken, uniqueName('mfa-test-domain'), 'mfa test domain');

  mockFactor = await createMockFactor(validMFACode, domain, accessToken);
  smsSfrFactor = await createSFRResource(domain, accessToken).then((sfrResource) => createSMSFactor(domain, accessToken, sfrResource));
  emailFactor = await createSMTPResource(domain, accessToken).then((smtpResource) => createEmailFactor(smtpResource, domain, accessToken));
  callFactor1 = await createTwilioResource(domain, accessToken).then((twilioResource) =>
    createCallFactor(domain, accessToken, twilioResource, 'Call factor1'),
  );
  callFactor2 = await createTwilioResource(domain, accessToken).then((twilioResource) =>
    createCallFactor(domain, accessToken, twilioResource, 'Call factor2'),
  );
  recoveryCodeFactor = await createRecoveryCodeFactor(domain, accessToken);
  totpFactor = await createOtpFactor(domain, accessToken);

  emailTestApp = await createMfaApp(domain, accessToken, [emailFactor.id]);
  recoveryCodeTestApp = await createMfaApp(domain, accessToken, [mockFactor.id, recoveryCodeFactor.id]);
  smsSfrTestApp = await createMfaApp(domain, accessToken, [smsSfrFactor.id]);
  mfaFlowApp = await createMfaFlowApp(domain, accessToken, emailFactor.id);
  totpApp = await createMfaApp(domain, accessToken, [totpFactor.id]);
  mfaEnrollFlowApp = await createMfaEnrollFlowApp(domain, accessToken, smsSfrFactor.id, callFactor1.id, callFactor2.id);

  let started = await startDomain(domain.id, accessToken).then(waitForDomainStart);
  domain = started.domain;
  openIdConfiguration = started.oidcConfig;
});

describe('MFA', () => {
  describe('TOTP factor test', () => {
    let totpUser;
    let sharedSecret;

    it('should login using authenticator code', async () => {
      const clientId = totpApp.settings.oauth.clientId;
      totpUser = await buildCreateAndTestUser(domain.id, accessToken, 1);

      const authorize = await loginUserNameAndPassword(clientId, totpUser, totpUser.password, false, openIdConfiguration, domain);
      expect(authorize.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/mfa/enroll`);

      const enrolOtp = await enrollOtpFactor(totpUser, authorize, totpFactor, domain);
      const authorize2 = await performGet(enrolOtp.headers['location'], '', {
        Cookie: enrolOtp.headers['set-cookie'],
      }).expect(302);
      expect(authorize2.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/mfa/challenge`);

      sharedSecret = enrolOtp.sharedSecret;
      const totp = new TOTP({ issuer: 'Gravitee.io', secret: sharedSecret });
      const totpToken = totp.generate();
      await verifyFactorFailure(authorize2, totpFactor);
      const successfulVerification = await verifyFactor(authorize2, totpToken, totpFactor);
      await logoutUser(openIdConfiguration.end_session_endpoint, successfulVerification);
    });

    it('should issue challenge when factor already enrolled', async () => {
      const clientId = totpApp.settings.oauth.clientId;

      const authorize = await loginUserNameAndPassword(clientId, totpUser, totpUser.password, false, openIdConfiguration, domain);
      expect(authorize.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/mfa/challenge`);

      const totp = new TOTP({ issuer: 'Gravitee.io', secret: sharedSecret });
      const totpToken = totp.generate();
      await verifyFactorFailure(authorize, totpFactor);
      const successfulVerification = await verifyFactor(authorize, totpToken, totpFactor);
      await logoutUser(openIdConfiguration.end_session_endpoint, successfulVerification);
    });
    afterAll(async () => {
      await deleteUser(domain.id, accessToken, totpUser.id);
    });
  });

  describe('Email factor test', () => {
    let user3;

    it('should send email', async () => {
      const clientId = emailTestApp.settings.oauth.clientId;

      user3 = await buildCreateAndTestUser(domain.id, accessToken, 1);
      expect(user3).toBeDefined();

      const authorize = await loginUserNameAndPassword(clientId, user3, user3.password, false, openIdConfiguration, domain);
      expect(authorize.headers['location']).toBeDefined();
      expect(authorize.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/mfa/enroll`);

      const enrollMFA = await enrollEmailFactor(user3, authorize, emailFactor, domain);

      const authorize2 = await performGet(enrollMFA.headers['location'], '', {
        Cookie: enrollMFA.headers['set-cookie'],
      }).expect(302);

      expect(authorize2.headers['location']).toBeDefined();
      expect(authorize2.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/mfa/challenge`);

      const successfulVerification = await verifyFactorOnEmailEnrollment(authorize2, emailFactor);
      expect(successfulVerification.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/oauth/authorize`);
      await logoutUser(openIdConfiguration.end_session_endpoint, successfulVerification);
    });

    afterAll(async () => {
      await deleteUser(domain.id, accessToken, user3.id);
    });
  });

  describe('Recovery code factor test', () => {
    let user4;
    let validRecoveryCode;

    it('recovery code factor should be present', async () => {
      const clientId = recoveryCodeTestApp.settings.oauth.clientId;

      user4 = await buildCreateAndTestUser(domain.id, accessToken, 1);
      expect(user4).toBeDefined();

      const loginResponse = await loginUserNameAndPassword(clientId, user4, user4.password, false, openIdConfiguration, domain);
      expect(loginResponse.headers['location']).toBeDefined();
      expect(loginResponse.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/mfa/enroll`);

      const enrollMFA = await enrollMockFactor(loginResponse, mockFactor, domain);

      const authorize2 = await performGet(enrollMFA.headers['location'], '', {
        Cookie: enrollMFA.headers['set-cookie'],
      }).expect(302);

      expect(authorize2.headers['location']).toBeDefined();
      expect(authorize2.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/mfa/challenge`);

      const successfulVerification = await verifyFactor(authorize2, validMFACode, mockFactor);

      const recoveryCodeRedirectUri = await performGet(successfulVerification.headers['location'], '', {
        Cookie: successfulVerification.headers['set-cookie'],
      }).expect(302);

      const recoveryCodeResponse = await performGet(recoveryCodeRedirectUri.headers['location'], '', {
        Cookie: recoveryCodeRedirectUri.headers['set-cookie'],
      }).expect(200);

      expect(recoveryCodeResponse.text).toBeDefined();
      validRecoveryCode = getARecoveryCode(recoveryCodeResponse.text);
      expect(validRecoveryCode).toBeDefined();

      const authorize3 = await postRecoveryCodeForm(recoveryCodeRedirectUri, recoveryCodeResponse);
      const callbackGetResponse = await performGet(authorize3.headers['location'], '', {
        Cookie: authorize3.headers['set-cookie'],
      }).expect(302);

      const callbackResponse = await extractXsrfTokenAndActionResponse(callbackGetResponse);
      const response = await performGet(callbackResponse.action, '', {
        Cookie: callbackResponse.headers['set-cookie'],
      }).expect(200);

      expect(response).toBeDefined();

      await logoutUser(openIdConfiguration.end_session_endpoint, response);
    });

    it('login using recovery code', async () => {
      const clientId = recoveryCodeTestApp.settings.oauth.clientId;

      const loginResponse = await loginUserNameAndPassword(clientId, user4, user4.password, false, openIdConfiguration, domain);
      expect(loginResponse.headers['location']).toBeDefined();
      expect(loginResponse.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/mfa/challenge`);

      const mfaChallenge = await performGet(loginResponse.headers['location'], '', {
        Cookie: loginResponse.headers['set-cookie'],
      }).expect(200);

      const alternativeUrl = await getAlternativeUrl(mfaChallenge);
      expect(alternativeUrl).toBeDefined();

      await performGet(alternativeUrl, '', {
        Cookie: mfaChallenge.headers['set-cookie'],
      }).expect(200);

      const recoveryCodeChallenge = await postAlternativeMFAUrl(alternativeUrl, mfaChallenge, recoveryCodeFactor);
      expect(recoveryCodeChallenge).toBeDefined();

      const successfulVerification = await verifyFactor(recoveryCodeChallenge, validRecoveryCode, recoveryCodeFactor);
      await logoutUser(openIdConfiguration.end_session_endpoint, successfulVerification);
    });

    afterAll(async () => {
      await deleteUser(domain.id, accessToken, user4.id);
    });
  });

  describe('SMS factor test', () => {
    let user5;
    it('Should enroll and authenticate using SMS factor via SFR', async () => {
      user5 = await buildCreateAndTestUser(domain.id, accessToken, 5);
      expect(user5).toBeDefined();
      const loginResponse = await loginUserNameAndPassword(
        smsSfrTestApp.settings.oauth.clientId,
        user5,
        user5.password,
        false,
        openIdConfiguration,
        domain,
      );
      const enroll = await enrollSmsFactor(loginResponse, smsSfrFactor, domain);
      const authorize = await performGet(enroll.headers['location'], '', {
        Cookie: enroll.headers['set-cookie'],
      }).expect(302);
      expect(authorize.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/mfa/challenge`);

      const verify = await verifySmsSfrFactor(authorize, smsSfrFactor);

      expect(verify.status).toBe(302);
      expect(verify.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/oauth/authorize`);
    });
    afterAll(async () => {
      await deleteUser(domain.id, accessToken, user5.id);
    });
  });

  describe('MFA flow test', () => {
    let user6;
    it('Should enroll and challenge MFA before WebAuthN - webApp', async () => {
      const clientId = mfaFlowApp.settings.oauth.clientId;

      user6 = await buildCreateAndTestUser(domain.id, accessToken, 6);
      expect(user6).toBeDefined();

      const authResponse = await initiateLoginFlow(clientId, openIdConfiguration, domain);
      const postLogin = await login(authResponse, user6.username, clientId);
      const authorize = await performGet(postLogin.headers['location'], '', {
        Cookie: postLogin.headers['set-cookie'],
      }).expect(302);

      expect(authorize.headers['location']).toBeDefined();
      expect(authorize.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/webauthn/register`);

      const webauthn = await performGet(authorize.headers['location'], '', {
        Cookie: authorize.headers['set-cookie'],
      }).expect(302);
      expect(webauthn.headers['location']).toContain('webauthn');
      expect(webauthn.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/mfa/challenge`);

      const successfulVerification = await verifyFactorOnEmailEnrollment(webauthn, emailFactor);
      expect(successfulVerification.status).toBe(302);
      expect(successfulVerification.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/webauthn/register`);
      const webauthn2 = await performGet(successfulVerification.headers['location'], '', {
        Cookie: successfulVerification.headers['set-cookie'],
      });
      expect(webauthn2.status).toBe(200);
      await logoutUser(openIdConfiguration.end_session_endpoint, successfulVerification);
    });
    afterAll(async () => {
      await deleteUser(domain.id, accessToken, user6.id);
    });
  });

  describe('MFA enrollment flow test', () => {
    let user7;
    it('Should enroll factor from POST CHALLENGE and POST ENROLLMENT flow', async () => {
      user7 = await buildCreateAndTestUser(domain.id, accessToken, 7);
      expect(user7).toBeDefined();

      const loginResponse = await loginUserNameAndPassword(
        mfaEnrollFlowApp.settings.oauth.clientId,
        user7,
        user7.password,
        false,
        openIdConfiguration,
        domain,
      );
      const enroll = await enrollSmsFactor(loginResponse, smsSfrFactor, domain);
      const authorize = await performGet(enroll.headers['location'], '', {
        Cookie: enroll.headers['set-cookie'],
      }).expect(302);
      expect(authorize.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/mfa/challenge`);

      const verify = await verifySmsSfrFactor(authorize, smsSfrFactor);

      expect(verify.status).toBe(302);
      expect(verify.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/oauth/authorize`);

      const userFactors = await getUserFactors(domain.id, accessToken, user7.id);
      expect(userFactors.some((f) => f.id === smsSfrFactor.id)).toBe(true);
      expect(userFactors.some((f) => f.id === callFactor1.id)).toBe(true);
      expect(userFactors.some((f) => f.id === callFactor2.id)).toBe(true);
    });
    afterAll(async () => {
      await deleteUser(domain.id, accessToken, user7.id);
    });
  });
});

afterAll(async () => {
  if (domain && domain.id) {
    await safeDeleteDomain(domain.id, accessToken);
    await performDelete(sfrUrl, '/__admin/requests', {});
  }
});

const enrollMockFactor = async (authorize, factor, domain) => {
  const authResult = await extractXsrfTokenAndActionResponse(authorize);
  const enrollMFA = await performPost(
    authResult.action,
    '',
    {
      factorId: factor.id,
      user_mfa_enrollment: true,
      'X-XSRF-TOKEN': authResult.token,
    },
    {
      Cookie: authResult.headers['set-cookie'],
      'Content-type': 'application/x-www-form-urlencoded',
    },
  ).expect(302);

  expect(enrollMFA.headers['location']).toBeDefined();
  expect(enrollMFA.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/oauth/authorize`);

  return enrollMFA;
};

const createSMTPResource = async (domain, accessToken) => {
  const smtp = await createResource(domain.id, accessToken, {
    type: 'smtp-am-resource',
    configuration: '{"host":"localhost","port":5025,"from":"admin@test.com","protocol":"smtp","authentication":false,"startTls":false}',
    name: 'FakeSmtp',
  });

  expect(smtp.id).not.toBeNull();

  return smtp;
};

const enrollEmailFactor = async (user, authorize, emailFactor, domain) => {
  const authResult = await extractXsrfTokenAndActionResponse(authorize);
  const enrollMFA = await performPost(
    authResult.action,
    '',
    {
      factorId: emailFactor.id,
      email: user.email,
      sharedSecret: sharedSecret,
      user_mfa_enrollment: true,
      'X-XSRF-TOKEN': authResult.token,
    },
    {
      Cookie: authResult.headers['set-cookie'],
      'Content-type': 'application/x-www-form-urlencoded',
    },
  ).expect(302);

  expect(enrollMFA.headers['location']).toBeDefined();
  expect(enrollMFA.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/oauth/authorize`);

  return enrollMFA;
};

const enrollOtpFactor = async (user, authResponse, otpFactor, domain) => {
  const headers = authResponse.headers['set-cookie'] ? { Cookie: authResponse.headers['set-cookie'] } : {};
  const result = await performGet(authResponse.headers['location'], '', headers).expect(200);
  const extractedResult = extractSharedSecret(result, 'TOTP');

  const enrollMfa = await performPost(
    extractedResult.action,
    '',
    {
      factorId: otpFactor.id,
      sharedSecret: extractedResult.sharedSecret,
      user_mfa_enrollment: true,
      'X-XSRF-TOKEN': extractedResult.token,
    },
    {
      Cookie: result.headers['set-cookie'],
      'Content-type': 'application/x-www-form-urlencoded',
    },
  );
  expect(enrollMfa.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/oauth/authorize`);

  return { headers: enrollMfa.headers, sharedSecret: extractedResult.sharedSecret };
};

const enrollSmsFactor = async (authResponse, factor, domain) => {
  const result = await performGet(authResponse.headers['location'], '', { Cookie: authResponse.headers['set-cookie'] }).expect(200);
  const extractedResult = extractSharedSecret(result);

  const enrollMfa = await performPost(
    extractedResult.action,
    '',
    {
      factorId: factor.id,
      sharedSecret: extractedResult.sharedSecret,
      user_mfa_enrollment: true,
      phone: '+33780763733',
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

const createMfaEnrollFlowApp = async (domain, accessToken, smsFactorId, callFactorId1, callFactorId2) => {
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
                { id: callFactorId1, selectionRule: '' },
                { id: callFactorId2, selectionRule: '' },
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

  lookupFlowAndResetPolicies(flows, FlowEntityTypeEnum.MfaEnrollment, 'post', [
    {
      name: 'enrollMFAPostEnroll',
      policy: 'policy-am-enroll-mfa',
      description: 'Auto enroll call factor',
      condition: '',
      enabled: true,
      configuration: JSON.stringify({
        primary: false,
        refresh: false,
        factorId: callFactorId1,
        value: '+33780763733',
      }),
    },
  ]);

  lookupFlowAndResetPolicies(flows, FlowEntityTypeEnum.MfaChallenge, 'post', [
    {
      name: 'enrollMFAPostChallenge',
      policy: 'policy-am-enroll-mfa',
      description: 'Auto enroll call factor',
      condition: '',
      enabled: true,
      configuration: JSON.stringify({
        primary: false,
        refresh: false,
        factorId: callFactorId2,
        value: '+33780763733',
      }),
    },
  ]);

  await updateApplicationFlows(domain.id, accessToken, application.id, flows);
  return application;
};

const createMfaFlowApp = async (domain, accessToken, factor) => {
  const application = await createApplication(domain.id, accessToken, {
    name: faker.company.bsBuzz(),
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
            scopeSettings: [
              { scope: 'openid', defaultScope: true },
              {
                scope: 'openid',
                defaultScope: true,
              },
            ],
          },
          mfa: {
            factor: {
              defaultFactorId: factor,
              applicationFactors: [
                {
                  id: factor,
                  selectionRule: '',
                },
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
          login: {
            inherited: false,
            passwordlessEnabled: true,
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
  lookupFlowAndResetPolicies(flows, FlowEntityTypeEnum.Login, 'post', [
    {
      name: 'MFA-enroll',
      policy: 'policy-am-enroll-mfa',
      description: '',
      condition: '',
      enabled: true,
      configuration: JSON.stringify({ primary: false, refresh: false, factorId: factor, value: 'test@example.com' }),
    },
  ]);
  lookupFlowAndResetPolicies(flows, FlowEntityTypeEnum.WebauthnRegister, 'pre', [
    {
      name: 'MFA-challenge',
      policy: 'am-policy-mfa-challenge',
      description: '',
      condition: '',
      enabled: true,
      configuration: JSON.stringify({ forceEnrollFactor: true }),
    },
  ]);
  await updateApplicationFlows(domain.id, accessToken, application.id, flows);
  return application;
};

const createMfaApp = async (domain, accessToken, factors: Array<number>) => {
  const application = await createApplication(domain.id, accessToken, {
    name: faker.company.bsBuzz(),
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
            scopeSettings: [
              { scope: 'openid', defaultScope: true },
              {
                scope: 'openid',
                defaultScope: true,
              },
            ],
          },
          mfa: {
            factor: {
              defaultFactorId: factors[0],
              applicationFactors: factors.map((i) => {
                return {
                  id: i,
                  selectionRule: '',
                } as any;
              }),
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
        },
        identityProviders: [{ identity: `default-idp-${domain.id}`, priority: -1 }],
        factors: factors,
      },
      app.id,
    ),
  );
  expect(application.settings.oauth.clientId).toBeDefined();
  return application;
};

const getARecoveryCode = (text) => {
  const dom = cheerio.load(text);
  return dom('.code-item').get(0).childNodes[0].data;
};

const postRecoveryCodeForm = async (recoveryCodeRedirectUri, recoveryCodeResponse) => {
  const dom = cheerio.load(recoveryCodeResponse.text);
  const xsrfToken = dom('[name=X-XSRF-TOKEN]').val();
  const callback = await performPost(
    recoveryCodeRedirectUri.headers['location'],
    '',
    {
      'X-XSRF-TOKEN': xsrfToken,
    },
    {
      Cookie: recoveryCodeResponse.headers['set-cookie'],
      'Content-type': 'application/x-www-form-urlencoded',
    },
  ).expect(302);

  expect(callback.headers['location']).toBeDefined();
  expect(callback.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/oauth/authorize`);

  return callback;
};

const getAlternativeUrl = async (mfaChallenge) => {
  const dom = cheerio.load(mfaChallenge.text);
  return dom('a').attr('href');
};

const postAlternativeMFAUrl = async (alternativeUrl, mfaChallenge, factor) => {
  const dom = cheerio.load(mfaChallenge.text);
  const xsrfToken = dom('[name=X-XSRF-TOKEN]').val();
  const challenge = await performPost(
    alternativeUrl,
    '',
    {
      factorId: factor.id,
      'X-XSRF-TOKEN': xsrfToken,
    },
    {
      Cookie: mfaChallenge.headers['set-cookie'],
      'Content-type': 'application/x-www-form-urlencoded',
    },
  ).expect(302);

  expect(challenge.headers['location']).toBeDefined();
  expect(challenge.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/mfa/challenge`);

  return challenge;
};

const verifyFactor = async (challenge, code, factor) => {
  const challengeResponse = await extractXsrfTokenAndActionResponse(challenge);
  const successfulVerification = await postFactor(challengeResponse, factor, code);

  expect(successfulVerification.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/oauth/authorize`);
  return successfulVerification;
};

const verifyFactorOnEmailEnrollment = async (challenge, factor) => {
  const challengeResponse = await extractXsrfTokenAndActionResponse(challenge);

  const email = await getLastEmail();
  expect(email).toBeDefined();
  const verificationCode = email.contents[0].data.match('.*class="otp-code".*<span[^>]*>.([0-9]{6}).<\\/span>')[1];
  expect(verificationCode).toBeDefined();
  await clearEmails();
  return postFactor(challengeResponse, factor, verificationCode);
};

const verifyFactorFailure = async (challenge, factor) => {
  const challengeResponse = await extractXsrfTokenAndActionResponse(challenge);
  const failedVerification = await postFactor(challengeResponse, factor, 'invalid-code');

  expect(failedVerification.headers['location']).toContain('error=mfa_challenge_failed');
  return failedVerification;
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
