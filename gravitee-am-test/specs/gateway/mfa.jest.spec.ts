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
import { createDomain, deleteDomain, startDomain, waitFor, waitForDomainStart } from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';

import fetch from 'cross-fetch';
import { buildCreateAndTestUser, deleteUser } from '@management-commands/user-management-commands';
import { createFactor } from '@management-commands/factor-management-commands';
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
import { loginUserNameAndPassword } from '@gateway-commands/login-commands';
import { uniqueName } from '@utils-commands/misc';
const cheerio = require('cheerio');

global.fetch = fetch;

let domain;
let accessToken;
let mockFactor;
let emailFactor;
let openIdConfiguration;
let smsTestApp;
let recoveryCodeFactor;
let recoveryCodeTestApp;
let bruteForceTestApp;
let emailTestApp;
let totpFactor;
let totpApp;
let smsSfrFactor;
let smsSfrTestApp;

const mfaChallengeAttemptsResetTime = 1;
const validMFACode = '333333';
const sharedSecret = 'K546JFR2PK5CGQLLUTFG4W46IKDFWWUE';
const sfrUrl = 'http://localhost:8181';

jest.setTimeout(200000);

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();
  domain = await createDomain(accessToken, uniqueName('mfa-test-domain'), 'mfa test domain');

  mockFactor = await createMockFactor(validMFACode, domain, accessToken);
  const sfrResource = await createSFRResource(domain, accessToken);
  smsSfrFactor = await createSMSFactor(domain, accessToken, sfrResource);

  emailFactor = await createSMTPResource(domain, accessToken).then((smtpResource) => createEmailFactor(smtpResource, domain, accessToken));

  recoveryCodeFactor = await createRecoveryCodeFactor(domain, accessToken);

  smsTestApp = await createMfaApp(domain, accessToken, [mockFactor.id]);
  bruteForceTestApp = await createBruteForceTestApp(mockFactor, domain, accessToken, mfaChallengeAttemptsResetTime, [mockFactor.id]);
  emailTestApp = await createMfaApp(domain, accessToken, [emailFactor.id]);
  recoveryCodeTestApp = await createMfaApp(domain, accessToken, [mockFactor.id, recoveryCodeFactor.id]);
  smsSfrTestApp = await createMfaApp(domain, accessToken, [smsSfrFactor.id]);

  totpFactor = await createOtpFactor();
  totpApp = await createMfaApp(domain, accessToken, [totpFactor.id]);

  /*
   * it is intentional to call setTimeout after creating apps. Cannot avoid this timeout call.
   * At this point I haven't found a function which is similar to retry until specific http code is returned.
   * jest.retryTimes(numRetries, options) didn't applicable in this case.
   * */
  let started = await startDomain(domain.id, accessToken).then(waitForDomainStart);
  domain = started.domain;
  openIdConfiguration = started.oidcConfig;
});

describe('MFA', () => {
  describe('MFA rate limit test', () => {
    let user1;
    //To run this test locally, change mfa_rate in gravitee.yml to 2 attempts in 1 minute.
    it('throw mfa_request_limit_exceed after 2 request', async () => {
      const clientId = smsTestApp.settings.oauth.clientId;

      user1 = await buildCreateAndTestUser(domain.id, accessToken, 1);
      expect(user1).toBeDefined();
      const authorize = await loginUserNameAndPassword(clientId, user1, user1.password, false, openIdConfiguration, domain);

      expect(authorize.headers['location']).toBeDefined();
      expect(authorize.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/mfa/enroll`);

      const enrollMFA = await enrollMockFactor(authorize, mockFactor, domain);
      const authorize2 = await performGet(enrollMFA.headers['location'], '', {
        Cookie: enrollMFA.headers['set-cookie'],
      }).expect(302);

      expect(authorize2.headers['location']).toBeDefined();
      expect(authorize2.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/mfa/challenge`);

      /**
       * The number of the get requests is based on the gateway gravtitee.yml 'mfa_rate' configuration
       * These assertions will fail or need to be updated if 'mfa_rate' configuration is changed
       */
      const expectedCode = [200, 200, 302];

      for (const responseCode of expectedCode) {
        const rateLimitException = await performGet(authorize2.headers['location'], '', {
          Cookie: authorize2.headers['set-cookie'],
        });
        expect(rateLimitException.status).toBe(responseCode);

        if (responseCode === 302) {
          expect(rateLimitException.headers['location']).toBeDefined();
          expect(rateLimitException.headers['location']).toContain('request_limit_error=mfa_request_limit_exceed');
        }
      }
    });

    afterAll(async () => {
      await deleteUser(domain.id, accessToken, user1.id);
    });
  });

  describe('brute force test', () => {
    let user2;

    it('should throw brute force exception', async () => {
      const clientId = bruteForceTestApp.settings.oauth.clientId;

      user2 = await buildCreateAndTestUser(domain.id, accessToken, 1);
      expect(user2).toBeDefined();

      const authorize = await loginUserNameAndPassword(clientId, user2, user2.password, false, openIdConfiguration, domain);
      expect(authorize.headers['location']).toBeDefined();
      expect(authorize.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/mfa/enroll`);

      const enrollMFA = await enrollMockFactor(authorize, mockFactor, domain);

      const authorize2 = await performGet(enrollMFA.headers['location'], '', {
        Cookie: enrollMFA.headers['set-cookie'],
      }).expect(302);

      expect(authorize2.headers['location']).toBeDefined();
      expect(authorize2.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/mfa/challenge`);

      await performGet(authorize2.headers['location'], '', {
        Cookie: authorize2.headers['set-cookie'],
      }).expect(200);

      const expectedErrorMessage = [
        { expected: 'mfa_challenge_failed' },
        { expected: 'mfa_challenge_failed' },
        { expected: 'verify_attempt_error=maximum_verify_limit' },
      ];

      //mfa challenge post
      const authResult2 = await extractXsrfTokenAndActionResponse(authorize2);
      const invalidCode = 999;
      for (let i = 0; i < expectedErrorMessage.length; i++) {
        const failedVerification = await verifyMockFactor(authResult2, invalidCode);
        expect(failedVerification.headers['location']).toBeDefined();
        expect(failedVerification.headers['location']).toContain(expectedErrorMessage[i].expected);
      }

      //now wait 1 second as per the configuration
      await waitFor(mfaChallengeAttemptsResetTime * 1000);

      const successfulVerification = await verifyMockFactor(authResult2, validMFACode);
      await logoutUser(openIdConfiguration.end_session_endpoint, successfulVerification);
    });

    afterAll(async () => {
      await deleteUser(domain.id, accessToken, user2.id);
    });
  });

  describe('TOTP authentication', () => {
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

  describe('email factor test', () => {
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
      await logoutUser(openIdConfiguration.end_session_endpoint, successfulVerification);
    });

    afterAll(async () => {
      await deleteUser(domain.id, accessToken, user3.id);
    });
  });

  describe('recovery code factor test', () => {
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
      expect(verify.headers['location']).toContain('code=');
    });
    afterAll(async () => {
      await deleteUser(domain.id, accessToken, user5.id);
      await performDelete(sfrUrl, '/__admin/requests', {});
    });
  });
});

afterAll(async () => {
  if (domain && domain.id) {
    await deleteDomain(domain.id, accessToken);
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

const createEmailFactor = async (smtpResource, domain, accessToken) => {
  const factor = await createFactor(domain.id, accessToken, {
    type: 'email-am-factor',
    factorType: 'EMAIL',
    configuration: `{\"graviteeResource\":\"${smtpResource.id}\",\"returnDigits\":6}`,
    name: 'Email',
  });

  expect(factor).toBeDefined();
  expect(factor).not.toBeNull();

  return factor;
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

const createOtpFactor = async () => {
  return await createFactor(domain.id, accessToken, {
    type: 'otp-am-factor',
    factorType: 'TOTP',
    configuration: '{"issuer":"Gravitee.io","algorithm":"HmacSHA1","timeStep":"30","returnDigits":"6"}',
    name: 'totp Factor',
  });
};

const enrollOtpFactor = async (user, authResponse, otpFactor, domain) => {
  const headers = authResponse.headers['set-cookie'] ? { Cookie: authResponse.headers['set-cookie'] } : {};
  const result = await performGet(authResponse.headers['location'], '', headers).expect(200);
  const dom = cheerio.load(result.text);
  const xsrfToken = dom('[name=X-XSRF-TOKEN]').val();
  const action = dom('form').attr('action');

  expect(xsrfToken).toBeDefined();
  expect(action).toBeDefined();

  const factors = dom('script')
    .text()
    .split('\n')
    .find((line) => line.trim().startsWith('const factors'));
  const totpSharedSecret = JSON.parse(factors.substring(factors.indexOf('=') + 1).replaceAll(';', ''))[0].enrollment.key;

  const enrollMfa = await performPost(
    action,
    '',
    {
      factorId: otpFactor.id,
      sharedSecret: totpSharedSecret,
      user_mfa_enrollment: true,
      'X-XSRF-TOKEN': xsrfToken,
    },
    {
      Cookie: result.headers['set-cookie'],
      'Content-type': 'application/x-www-form-urlencoded',
    },
  );
  expect(enrollMfa.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/oauth/authorize`);

  return { headers: enrollMfa.headers, sharedSecret: totpSharedSecret };
};

const enrollSmsFactor = async (authResponse, factor, domain) => {
  const result = await performGet(authResponse.headers['location'], '', { Cookie: authResponse.headers['set-cookie'] }).expect(200);
  const dom = cheerio.load(result.text);
  const xsrfToken = dom('[name=X-XSRF-TOKEN]').val();
  const action = dom('form').attr('action');

  expect(xsrfToken).toBeDefined();
  expect(action).toBeDefined();

  const factors = dom('script')
    .text()
    .split('\n')
    .find((line) => line.trim().startsWith('const factors'));
  const sharedSecret = JSON.parse(factors.substring(factors.indexOf('=') + 1).replaceAll(';', ''))[0].enrollment.key;
  console.log(sharedSecret);
  console.log(factor.id);

  const enrollMfa = await performPost(
    action,
    '',
    {
      factorId: factor.id,
      sharedSecret: sharedSecret,
      user_mfa_enrollment: true,
      phone: '+33780763733',
      'X-XSRF-TOKEN': xsrfToken,
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

const createSMSFactor = async (domain, accessToken, sfrResource) => {
  const smsFactor = await createFactor(domain.id, accessToken, {
    name: 'sms-factor',
    factorType: 'SMS',
    type: 'sms-am-factor',
    configuration: JSON.stringify({
      countryCodes: 'fr',
      graviteeResource: sfrResource.id,
      messageBody: "{#context.attributes['code']}",
      returnDigits: 6,
      expiresAfter: 300,
    }),
  });
  expect(smsFactor).toBeDefined();
  expect(smsFactor).not.toBeNull();
  expect(smsFactor.id).not.toBeNull();
  return smsFactor;
};

const createMockFactor = async (code, domain, accessToken) => {
  const factor = await createFactor(domain.id, accessToken, {
    type: 'mock-am-factor',
    factorType: 'MOCK',
    configuration: `{\"code\":\"${code}\"}`,
    name: 'Mock Factor',
  });

  expect(factor).toBeDefined();
  expect(factor).not.toBeNull();

  return factor;
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
        account: {
          inherited: false,
          mfaChallengeAttemptsDetectionEnabled: true,
          mfaChallengeMaxAttempts: 2,
          mfaChallengeAttemptsResetTime: mfaChallengeAttemptsResetTime,
        },
        identityProviders: [{ identity: `default-idp-${domain.id}`, priority: -1 }],
        factors: factors,
      },
      app.id,
    ).then((updatedApp) => {
      // restore the clientSecret coming from the create order
      updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
      return updatedApp;
    }),
  );
  expect(application.settings.oauth.clientId).toBeDefined();
  return application;
};

const createBruteForceTestApp = async (smsFactor, domain, accessToken, mfaChallengeAttemptsResetTime, factors: Array<number>) => {
  const application = await createApplication(domain.id, accessToken, {
    name: 'mfa-bruteforce-test',
    type: 'WEB',
    clientId: 'mfa-bruteforce-test-id',
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
          account: {
            inherited: false,
            mfaChallengeAttemptsDetectionEnabled: true,
            mfaChallengeMaxAttempts: 2,
            mfaChallengeAttemptsResetTime: mfaChallengeAttemptsResetTime,
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
        factors: [smsFactor.id],
      },
      app.id,
    ).then((updatedApp) => {
      // restore the clientSecret coming from the create order
      updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
      return updatedApp;
    }),
  );

  await waitFor(1000);
  expect(application).toBeDefined();
  expect(application.settings.oauth.clientId).toBeDefined();

  return application;
};

const createRecoveryCodeFactor = async (domain, accessToken) => {
  const factor = await createFactor(domain.id, accessToken, {
    type: 'recovery-code-am-factor',
    factorType: 'Recovery Code',
    configuration: '{"digit":5,"count":6}',
    name: 'Recovery Code',
  });

  expect(factor).toBeDefined();
  expect(factor).not.toBeNull();

  return factor;
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
  const successfulVerification = await performPost(
    challengeResponse.action,
    '',
    {
      factorId: factor.id,
      code: code,
      'X-XSRF-TOKEN': challengeResponse.token,
    },
    {
      Cookie: challengeResponse.headers['set-cookie'],
      'Content-type': 'application/x-www-form-urlencoded',
    },
  ).expect(302);

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

  const successfulVerification = await performPost(
    challengeResponse.action,
    '',
    {
      factorId: factor.id,
      code: verificationCode,
      'X-XSRF-TOKEN': challengeResponse.token,
    },
    {
      Cookie: challengeResponse.headers['set-cookie'],
      'Content-type': 'application/x-www-form-urlencoded',
    },
  ).expect(302);

  expect(successfulVerification.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${domain.hrid}/oauth/authorize`);
  return successfulVerification;
};

const verifyFactorFailure = async (challenge, factor) => {
  const challengeResponse = await extractXsrfTokenAndActionResponse(challenge);
  const failedVerification = await performPost(
    challengeResponse.action,
    '',
    {
      factorId: factor.id,
      code: 123456,
      'X-XSRF-TOKEN': challengeResponse.token,
    },
    {
      Cookie: challengeResponse.headers['set-cookie'],
      'Content-type': 'application/x-www-form-urlencoded',
    },
  ).expect(302);

  expect(failedVerification.headers['location']).toContain('error=mfa_challenge_failed');
  return failedVerification;
};

const verifyMockFactor = async (authResult, code) => {
  return await performPost(
    authResult.action,
    '',
    {
      factorId: mockFactor.id,
      code: code,
      'X-XSRF-TOKEN': authResult.token,
    },
    {
      Cookie: authResult.headers['set-cookie'],
      'Content-type': 'application/x-www-form-urlencoded',
    },
  ).expect(302);
};

const verifySmsSfrFactor = async (challenge, factor) => {
  const challengeResponse = await extractXsrfTokenAndActionResponse(challenge);
  const code = getSMSCode();
  return await performPost(
    challengeResponse.action,
    '',
    {
      factorId: factor.id,
      code: code,
      'X-XSRF-TOKEN': challengeResponse.token,
    },
    {
      Cookie: challengeResponse.headers['set-cookie'],
      'Content-type': 'application/x-www-form-urlencoded',
    },
  ).expect(302);
};

const getSMSCode = async () => {
  const requests = await performGet(sfrUrl, '/__admin/requests');
  expect(requests.status).toBe(200);
  const body = decodeURIComponent(requests.body.requests[0].request.body);
  const params = new URLSearchParams(body);
  const messageUnitaireRaw = params.get('messageUnitaire');
  const messageUnitaireJson = JSON.parse(decodeURIComponent(messageUnitaireRaw));
  const code = messageUnitaireJson.msgContent;

  expect(code).toBeDefined();
  return code;
};
