import fetch from 'cross-fetch';
import { afterAll, afterEach, beforeAll, beforeEach, expect, jest } from '@jest/globals';
import { createApp, createTestUser, init, MfaTestContext, removeDomain } from './mfa-setup-fixture';
import { deleteApplication, patchApplication } from '@management-commands/application-management-commands';
import { withRetry } from '@utils-commands/retry';
import { followUpGet, get } from './mfa-flow-fixture';
import { extractDomAttr, extractDomValue } from './mfa-extract-fixture';
import { performFormPost } from '@gateway-commands/oauth-oidc-commands';
import { logoutUser } from './mfa-login-fixture';

global.fetch = fetch;
jest.setTimeout(200000);

function defaultApplicationSettings() {
  return {
    factors: [],
    settings: {
      mfa: {
        factor: {
          defaultFactorId: testContext.domain.factors[0].id,
          applicationFactors: [testContext.domain.factors[0], testContext.domain.factors[1]],
        },
        stepUpAuthenticationRule: '',
        stepUpAuthentication: { active: false, stepUpAuthenticationRule: '' },
        adaptiveAuthenticationRule: '',
        rememberDevice: { active: false, skipRememberDevice: false },
        enrollment: { forceEnrollment: false },
        enroll: { active: false, enrollmentSkipActive: false, forceEnrollment: false, type: 'required' },
        challenge: { active: true, challengeRule: '', type: 'required' },
      },
    },
  };
}

const defaultClientSettings = {
  clientId: 'test-client',
  name: 'test-client',
  id: 'test-client',
  clientSecret: 'test-client',
  redirectUris: ['https://auth-nightly.gravitee.io/myApp/callback'],
};

beforeAll(async () => {
  await init(testContext);
});

afterAll(async () => {
  await removeDomain(testContext);
});

beforeEach(async () => {
  testContext.application = defaultClientSettings;
  testContext.application.id = await createApp(testContext);
  testContext.oidc.clientAuthorizationEndpoint = `${testContext.oidc.authorizationEndpoint}?response_type=code&client_id=${testContext.application.clientId}&redirect_uri=https://auth-nightly.gravitee.io/myApp/callback`;
  testContext.domain.user = await createTestUser(testContext);
  await logoutUser(testContext);
});

afterEach(async () => {
  await deleteApplication(testContext.domain.domainId, testContext.admin.accessToken, testContext.application.id);
});

const testContext: MfaTestContext = {
  admin: {
    username: 'admin',
    password: 'adminadmin',
  },
  domain: {
    domainHrid: 'mfa-test-domain-dune',
  },
} as MfaTestContext;

describe('When Challenge REQUIRED and factor not enrolled', () =>
  it('should Enroll', async () => {
    const applicationSettings = defaultApplicationSettings();
    await patchApplication(testContext.domain.domainId, testContext.admin.accessToken, applicationSettings, testContext.application.id);

    const authResponse = await withRetry(() => get(testContext.oidc.clientAuthorizationEndpoint, 302));
    const loginPage = await withRetry(() => followUpGet(authResponse, 200));

    let xsrf = await extractDomValue(loginPage, '[name=X-XSRF-TOKEN]');
    let action = await extractDomAttr(loginPage, 'form', 'action');

    const loginPostResponse = await withRetry(() =>
      performFormPost(
        action,
        '',
        {
          'X-XSRF-TOKEN': xsrf,
          username: testContext.domain.user.username,
          password: testContext.domain.user.password,
          rememberMe: 'off',
          client_id: testContext.application.clientId,
        },
        {
          Cookie: loginPage.headers['set-cookie'],
          'Content-type': 'application/x-www-form-urlencoded',
        },
      ).expect(302),
    );

    const authResponseFinal = await withRetry(() => followUpGet(loginPostResponse, 302, 'enroll'));
    expect(authResponseFinal.headers['location']).toBeDefined();
    expect(authResponseFinal.headers['location']).toContain('/enroll');
  }));

describe('When Challenge REQUIRED and factor enrolled and valid session issued using MFA', () =>
  it('should end MFA flow', async () => {
    const applicationSettings = defaultApplicationSettings();
    await patchApplication(testContext.domain.domainId, testContext.admin.accessToken, applicationSettings, testContext.application.id);

    let session = await processMfaEndToEnd();
    const authResponse = await withRetry(() => get(testContext.oidc.clientAuthorizationEndpoint, 302, { Cookie: session.cookie }));

    expect(authResponse.headers['location']).toBeDefined();
    expect(authResponse.headers['location']).toContain('code=');
  }));

describe('When Challenge REQUIRED and factor enrolled and no session issued using MFA and RememberDevice disabled', () =>
  it('should Challenge', async () => {
    const applicationSettings = {
      ...defaultApplicationSettings(),
    };
    await patchApplication(testContext.domain.domainId, testContext.admin.accessToken, applicationSettings, testContext.application.id);

    let session = await processMfaEnrollment();
    const authResponse = await withRetry(() => get(testContext.oidc.clientAuthorizationEndpoint, 302, { Cookie: session.cookie }));

    expect(authResponse.headers['location']).toBeDefined();
    expect(authResponse.headers['location']).toContain('challenge');
  }));

describe('When Challenge REQUIRED and factor enrolled and no session issued using MFA and RememberDevice enabled and user has NOT associated device', () =>
  it('should Challenge', async () => {
    const applicationSettings = {
      ...defaultApplicationSettings(),
      settings: {
        mfa: {
          rememberDevice: {
            active: true,
            skipRememberDevice: false,
            expirationTimeSeconds: 360000,
            deviceIdentifierId: testContext.domain.devices[0].id,
          },
        },
      },
    };
    await patchApplication(testContext.domain.domainId, testContext.admin.accessToken, applicationSettings, testContext.application.id);

    let session = await processMfaEnrollment();
    const authResponse = await withRetry(() => get(testContext.oidc.clientAuthorizationEndpoint, 302, { Cookie: session.cookie }));

    expect(authResponse.headers['location']).toBeDefined();
    expect(authResponse.headers['location']).toContain('challenge');
  }));

describe('When Challenge REQUIRED and factor enrolled and no session issued using MFA and RememberDevice enabled and user associated device', () =>
  it('should End MFA flow', async () => {
    const applicationSettings = {
      ...defaultApplicationSettings(),
      settings: {
        mfa: {
          rememberDevice: {
            active: true,
            skipRememberDevice: false,
            expirationTimeSeconds: 360000,
            deviceIdentifierId: testContext.domain.devices[0].id,
          },
        },
      },
    };
    await patchApplication(testContext.domain.domainId, testContext.admin.accessToken, applicationSettings, testContext.application.id);

    let session = await processMfaEndToEnd(true);
    const authResponse = await withRetry(() => get(testContext.oidc.clientAuthorizationEndpoint, 302, { Cookie: session.cookie }));

    expect(authResponse.headers['location']).toBeDefined();
    expect(authResponse.headers['location']).toContain('code=');
  }));

async function processMfaEnrollment() {
  const authResponse = await withRetry(() => get(testContext.oidc.clientAuthorizationEndpoint, 302));
  const loginPage = await withRetry(() => followUpGet(authResponse, 200));

  let xsrf = await extractDomValue(loginPage, '[name=X-XSRF-TOKEN]');
  let action = await extractDomAttr(loginPage, 'form', 'action');

  const loginPostResponse = await withRetry(() =>
    performFormPost(
      action,
      '',
      {
        'X-XSRF-TOKEN': xsrf,
        username: testContext.domain.user.username,
        password: testContext.domain.user.password,
        rememberMe: 'off',
        client_id: testContext.application.clientId,
      },
      {
        Cookie: loginPage.headers['set-cookie'],
        'Content-type': 'application/x-www-form-urlencoded',
      },
    ).expect(302),
  );

  const authResponse2 = await withRetry(() => followUpGet(loginPostResponse, 302, 'enroll'));
  const enrollmentPage = await withRetry(() => followUpGet(authResponse2, 200));

  xsrf = await extractDomValue(enrollmentPage, '[name=X-XSRF-TOKEN]');
  action = await extractDomAttr(enrollmentPage, 'form', 'action');
  let factorId = await extractDomValue(enrollmentPage, '[name=factorId]');

  const enrollmentPostResponse = await withRetry(() =>
    performFormPost(
      action,
      '',
      {
        'X-XSRF-TOKEN': xsrf,
        factorId: factorId,
        ser_mfa_enrollment: true,
      },
      {
        Cookie: enrollmentPage.headers['set-cookie'],
        'Content-type': 'application/x-www-form-urlencoded',
      },
    ).expect(302),
  );

  return {
    cookie: enrollmentPostResponse.headers['set-cookie'],
    location: enrollmentPostResponse.headers['location'],
    factorId: factorId,
  };
}

async function processMfaEndToEnd(rememberDevice: boolean = false) {
  const enrollmentPostResponse = await processMfaEnrollment();

  const authResponse3 = await withRetry(() =>
    get(enrollmentPostResponse.location, 302, { Cookie: enrollmentPostResponse.cookie }, 'challenge'),
  );
  const challengePage = await withRetry(() => followUpGet(authResponse3, 200));

  let xsrf = await extractDomValue(challengePage, '[name=X-XSRF-TOKEN]');
  let action = await extractDomAttr(challengePage, 'form', 'action');

  const challengePostResponse = await withRetry(() =>
    performFormPost(
      action,
      '',
      {
        'X-XSRF-TOKEN': xsrf,
        factorId: enrollmentPostResponse.factorId,
        code: '1234',
        rememberDeviceConsent: rememberDevice ? 'on' : 'off',
      },
      {
        Cookie: challengePage.headers['set-cookie'],
        'Content-type': 'application/x-www-form-urlencoded',
      },
    ).expect(302),
  );

  const authResponse4 = await withRetry(() => followUpGet(challengePostResponse, 302, 'code='));
  expect(authResponse4.headers['location']).toBeDefined();
  expect(authResponse4.headers['location']).toContain('code=');

  return {
    cookie: authResponse4.headers['set-cookie'],
  };
}
