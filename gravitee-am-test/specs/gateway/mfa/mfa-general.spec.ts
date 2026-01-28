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
import { afterAll, beforeAll, expect, it } from '@jest/globals';
import { Domain, enableDomain, initClient, initDomain, removeDomain, TestSuiteContext } from './fixture/mfa-setup-fixture';
import { withRetry } from '@utils-commands/retry';
import { extractXsrfTokenAndActionResponse, getWellKnownOpenIdConfiguration, performGet } from '@gateway-commands/oauth-oidc-commands';
import { extractDomValue } from './fixture/mfa-extract-fixture';
import {
  followUpGet,
  get,
  postMfaChallenge,
  processLoginFromContext,
  processMfaEndToEnd,
  processMfaEnrollment,
} from './fixture/mfa-flow-fixture';
import { waitFor } from '@management-commands/domain-management-commands';
import { setup } from '../../test-fixture';

setup(200000);
const mfaChallengeAttemptsResetTime = 1;

const domain: Domain = {
  admin: {
    username: 'admin',
    password: 'adminadmin',
  },
  domain: {
    domainHrid: 'mfa-test-domain-general',
  },
} as Domain;

function defaultApplicationSettings() {
  return {
    factors: [],
    settings: {
      mfa: {
        factor: {
          defaultFactorId: null,
          applicationFactors: [],
        },
        stepUpAuthenticationRule: '',
        stepUpAuthentication: { active: false, stepUpAuthenticationRule: '' },
        adaptiveAuthenticationRule: '',
        rememberDevice: { active: false, skipRememberDevice: false },
        enrollment: { forceEnrollment: false },
        enroll: { active: false, enrollmentSkipActive: false, forceEnrollment: false, type: 'required' },
        challenge: { active: false, challengeRule: '', type: 'required' },
      },
    },
  };
}

beforeAll(async () => {
  await initDomain(domain, 9);

  let settings;

  settings = defaultApplicationSettings();
  const noFactorClient = await initClient(domain, 'no-factors-client', settings);

  settings = {
    ...defaultApplicationSettings(),
    settings: {
      mfa: {
        factor: {
          defaultFactorId: domain.domain.factors[0].id,
          applicationFactors: [domain.domain.factors[0]],
        },
        stepUpAuthenticationRule: '{{ false }}',
        stepUpAuthentication: { active: true, stepUpAuthenticationRule: '{{ false }}' },
      },
    },
  };
  const stepUpNegativeClient1 = await initClient(domain, 'step-up-positive-1', settings);

  settings = {
    ...defaultApplicationSettings(),
    settings: {
      mfa: {
        factor: {
          defaultFactorId: domain.domain.factors[0].id,
          applicationFactors: [domain.domain.factors[0]],
        },
        stepUpAuthenticationRule: '',
        stepUpAuthentication: { active: false, stepUpAuthenticationRule: '' },
      },
    },
  };
  const stepUpOffClient = await initClient(domain, 'step-up-off-1', settings);

  settings = {
    ...defaultApplicationSettings(),
    settings: {
      mfa: {
        factor: {
          defaultFactorId: domain.domain.factors[0].id,
          applicationFactors: [domain.domain.factors[0]],
        },
        stepUpAuthenticationRule: '{{ true }}',
        stepUpAuthentication: { active: true, stepUpAuthenticationRule: '{{ true }}' },
        enrollment: { forceEnrollment: true },
        enroll: { active: true, enrollmentSkipActive: false, forceEnrollment: true, type: 'required' },
        challenge: { active: true, challengeRule: '', type: 'required' },
      },
    },
  };
  const stepUpTrueClient2 = await initClient(domain, 'step-up-positive-2', settings);

  settings = {
    ...defaultApplicationSettings(),
    settings: {
      mfa: {
        factor: {
          defaultFactorId: domain.domain.factors[0].id,
          applicationFactors: [domain.domain.factors[0]],
        },
      },
    },
  };
  const withFactorsClient = await initClient(domain, 'with-factors-client-1', settings);

  settings = {
    ...defaultApplicationSettings(),
    settings: {
      mfa: {
        factor: {
          defaultFactorId: domain.domain.factors[0].id,
          applicationFactors: [
            { id: domain.domain.factors[0].id, selectionRule: '{{ false }}' },
            { id: domain.domain.factors[1].id, selectionRule: '{{ false }}' },
          ],
        },
        enroll: { active: true, enrollmentSkipActive: false, forceEnrollment: false, type: 'required' },
      },
    },
  };
  const enrollmentTrueClient = await initClient(domain, 'enrollment-true-1', settings);

  settings = {
    ...defaultApplicationSettings(),
    settings: {
      mfa: {
        factor: {
          defaultFactorId: domain.domain.factors[0].id,
          applicationFactors: [{ id: domain.domain.factors[0].id, selectionRule: '{{ false }}' }],
        },
        enroll: { active: true, enrollmentSkipActive: false, forceEnrollment: true, type: 'required' },
        challenge: { active: true, challengeRule: '', type: 'required' },
      },
      account: {
        inherited: false,
        mfaChallengeAttemptsDetectionEnabled: true,
        mfaChallengeMaxAttempts: 2,
        mfaChallengeAttemptsResetTime: mfaChallengeAttemptsResetTime,
      },
    },
  };
  const rateLimitClient = await initClient(domain, 'rate-limit-1', settings);

  const bruteForceClient = await initClient(domain, 'brut-force-1', settings);

  settings = defaultApplicationSettings();
  settings.settings.mfa.factor = {
    defaultFactorId: domain.domain.factors[0].id,
    applicationFactors: [domain.domain.factors[0]],
  };
  settings.settings.mfa.stepUpAuthentication = { active: true, stepUpAuthenticationRule: '{{ true }}' };
  settings.settings.mfa.stepUpAuthenticationRule = '{{ true }}';
  const stepUpTrueClient3 = await initClient(domain, 'step-up-positive-3', settings);

  const oidc = await enableDomain(domain)
    .then(() => waitFor(3000))
    .then(() => withRetry(() => getWellKnownOpenIdConfiguration(domain.domain.domainHrid).expect(200)));

  noFactorsCtx = new TestSuiteContext(domain, noFactorClient, domain.domain.users[0], oidc.body.authorization_endpoint);
  stepUpNegativeCtx = new TestSuiteContext(domain, stepUpNegativeClient1, domain.domain.users[1], oidc.body.authorization_endpoint);
  stepUpOffCtx = new TestSuiteContext(domain, stepUpOffClient, domain.domain.users[2], oidc.body.authorization_endpoint);
  stepUpPositiveCtx2 = new TestSuiteContext(domain, stepUpTrueClient2, domain.domain.users[3], oidc.body.authorization_endpoint);
  withFactorsCtx = new TestSuiteContext(domain, withFactorsClient, domain.domain.users[4], oidc.body.authorization_endpoint);
  enrollmentTrueCtx = new TestSuiteContext(domain, enrollmentTrueClient, domain.domain.users[5], oidc.body.authorization_endpoint);
  stepUpPositiveChallengeDisabledCtx = new TestSuiteContext(
    domain,
    stepUpTrueClient3,
    domain.domain.users[6],
    oidc.body.authorization_endpoint,
  );
  rateLimitCtx = new TestSuiteContext(domain, rateLimitClient, domain.domain.users[7], oidc.body.authorization_endpoint);
  bruteForceCtx = new TestSuiteContext(domain, bruteForceClient, domain.domain.users[8], oidc.body.authorization_endpoint);
});

let noFactorsCtx: TestSuiteContext;
let stepUpNegativeCtx: TestSuiteContext;
let stepUpOffCtx: TestSuiteContext;
let stepUpPositiveCtx2: TestSuiteContext;
let withFactorsCtx: TestSuiteContext;
let enrollmentTrueCtx: TestSuiteContext;
let stepUpPositiveChallengeDisabledCtx: TestSuiteContext;
let rateLimitCtx: TestSuiteContext;
let bruteForceCtx: TestSuiteContext;

afterAll(async () => {
  await removeDomain(domain);
});

describe('With disabled factors', () => {
  it('should omit MFA flow', async () => {
    const ctx = noFactorsCtx;

    const authResponseFinal = await processLoginFromContext(ctx);

    expect(authResponseFinal.headers['location']).toBeDefined();
    expect(authResponseFinal.headers['location']).toContain(ctx.client.redirectUris[0]);
    expect(authResponseFinal.headers['location']).toContain('code=');
  });
});

describe('With one enabled factor should omit MFA flow', () => {
  it('when stepUp=false', async () => {
    const ctx = stepUpNegativeCtx;

    const authResponseFinal = await processLoginFromContext(ctx);

    expect(authResponseFinal.headers['location']).toBeDefined();
    expect(authResponseFinal.headers['location']).toContain(ctx.client.redirectUris[0]);
    expect(authResponseFinal.headers['location']).toContain('code=');
  });

  it('when stepUp is disabled', async () => {
    const ctx = stepUpOffCtx;
    const authResponseFinal = await processLoginFromContext(ctx);

    expect(authResponseFinal.headers['location']).toBeDefined();
    expect(authResponseFinal.headers['location']).toContain(ctx.client.redirectUris[0]);
    expect(authResponseFinal.headers['location']).toContain('code=');
  });
});

describe('With active session, when stepUp is true, on authorization', () => {
  it('should Challenge', async () => {
    const ctx = stepUpPositiveCtx2;

    const session = await processMfaEndToEnd(ctx);
    const authResponse = await get(ctx.clientAuthUrl, 302, { Cookie: session.cookie });

    expect(authResponse.headers['location']).toBeDefined();
    expect(authResponse.headers['location']).toContain('/challenge');
  });
});

describe('With enabled factors and disabled enrollment and disabled challenge', () => {
  it('should stop MFA flow', async () => {
    const ctx = withFactorsCtx;

    const finalLocationResponse = await processLoginFromContext(ctx);

    expect(finalLocationResponse.headers['location']).toBeDefined();
    expect(finalLocationResponse.headers['location']).toContain(ctx.client.redirectUris[0]);
    expect(finalLocationResponse.headers['location']).toContain('code=');
  });
});

describe('With enabled factors and but failing selection rule', () => {
  it('only default factor should be visible', async () => {
    const enrollLocationResponse = await processLoginFromContext(enrollmentTrueCtx);
    const enrollmentPage = await followUpGet(enrollLocationResponse, 200);

    const factorId = extractDomValue(enrollmentPage, '[name=factorId]');

    expect(factorId).toBe(domain.domain.factors[0].id);
  });
});

describe('With active session, when stepUp is true, with challenge disabled, on authorization', () => {
  it('should Challenge', async () => {
    const ctx = stepUpPositiveChallengeDisabledCtx;
    const session = await processMfaEndToEnd(ctx);

    const authResponse = await get(ctx.clientAuthUrl, 302, { Cookie: session.cookie });
    expect(authResponse.headers['location']).toBeDefined();
    expect(authResponse.headers['location']).toContain('challenge');
  });
});

describe('MFA rate limit test', () => {
  //To run this test locally, change mfa_rate in gravitee.yml to 2 attempts in 1 minute.
  it('Should throw mfa_request_limit_exceed after 2 request', async () => {
    const ctx = rateLimitCtx;
    const enrollMFA = await processMfaEnrollment(ctx);
    const authorize2 = await performGet(enrollMFA.location, '', {
      Cookie: enrollMFA.cookie,
    }).expect(302);

    expect(authorize2.headers['location']).toBeDefined();
    expect(authorize2.headers['location']).toContain('/mfa/challenge');

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
});
describe('Brute force test', () => {
  it('Should throw brute force exception', async () => {
    const ctx = bruteForceCtx;

    const enrollMFA = await processMfaEnrollment(ctx);

    const authorize2 = await performGet(enrollMFA.location, '', {
      Cookie: enrollMFA.cookie,
    }).expect(302);

    expect(authorize2.headers['location']).toBeDefined();
    expect(authorize2.headers['location']).toContain('/mfa/challenge');

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
      const failedVerification = await postMfaChallenge(ctx, authResult2, invalidCode);
      expect(failedVerification.headers['location']).toBeDefined();
      expect(failedVerification.headers['location']).toContain(expectedErrorMessage[i].expected);
    }

    //now wait 1 second as per the configuration
    await waitFor(mfaChallengeAttemptsResetTime * 1000);

    const successfulVerification = await postMfaChallenge(ctx, authResult2, 1234);
    expect(successfulVerification.headers['location']).toBeDefined();
    expect(successfulVerification.headers['location']).not.toContain('error');
  });
});
