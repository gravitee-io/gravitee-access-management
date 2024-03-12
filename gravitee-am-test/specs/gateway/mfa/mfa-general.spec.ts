import { afterAll, afterEach, beforeAll, beforeEach, expect, jest } from '@jest/globals';
import { createApp, init, MfaTestContext, removeDomain } from './mfa-setup-fixture';
import { withRetry } from '@utils-commands/retry';
import { deleteApplication, patchApplication } from '@management-commands/application-management-commands';
import { performFormPost } from '@gateway-commands/oauth-oidc-commands';
import fetch from 'cross-fetch';
import { extractDomAttr, extractDomValue } from './mfa-extract-fixture';
import { followUpGet, get } from './mfa-flow-fixture';
import { logoutUser } from './mfa-login-fixture';

global.fetch = fetch;
jest.setTimeout(200000);

const testContext: MfaTestContext = {
  admin: {
    username: 'admin',
    password: 'adminadmin',
  },
  domain: {
    domainHrid: 'mfa-test-domain',
  },
} as MfaTestContext;

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
  await logoutUser(testContext);
});

afterEach(async () => {
  await deleteApplication(testContext.domain.domainId, testContext.admin.accessToken, testContext.application.id);
});

describe('With disabled factors', () => {
  it('should omit MFA flow', async () => {
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

    const authResponseFinal = await withRetry(() => followUpGet(loginPostResponse, 302));

    expect(authResponseFinal.headers['location']).toBeDefined();
    expect(authResponseFinal.headers['location']).toContain(testContext.application.redirectUris[0]);
    expect(authResponseFinal.headers['location']).toContain('code=');
  });
});

describe('With one enabled factor should omit MFA flow', () => {
  it('when stepUp=false', async () => {
    const applicationSettings = {
      ...defaultApplicationSettings(),
      settings: {
        mfa: {
          factor: {
            defaultFactorId: testContext.domain.factors[0].id,
            applicationFactors: [testContext.domain.factors[0]],
          },
          stepUpAuthenticationRule: '{{ false }}',
          stepUpAuthentication: { active: true, stepUpAuthenticationRule: '{{ false }}' },
        },
      },
    };

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

    const authResponseFinal = await withRetry(() => followUpGet(loginPostResponse, 302));

    expect(authResponseFinal.headers['location']).toBeDefined();
    expect(authResponseFinal.headers['location']).toContain(testContext.application.redirectUris[0]);
    expect(authResponseFinal.headers['location']).toContain('code=');
  });
  it('when stepUp is disabled', async () => {
    const applicationSettings = {
      ...defaultApplicationSettings(),
      settings: {
        mfa: {
          factor: {
            defaultFactorId: testContext.domain.factors[0].id,
            applicationFactors: [testContext.domain.factors[0]],
          },
          stepUpAuthenticationRule: '',
          stepUpAuthentication: { active: false, stepUpAuthenticationRule: '' },
        },
      },
    };

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

    const authResponseFinal = await withRetry(() => followUpGet(loginPostResponse, 302));

    expect(authResponseFinal.headers['location']).toBeDefined();
    expect(authResponseFinal.headers['location']).toContain(testContext.application.redirectUris[0]);
    expect(authResponseFinal.headers['location']).toContain('code=');
  });
});

describe('With enrolled factor should redirect to Enrollment and Challenge', () => {
  it('when stepUp is not false', async () => {
    const applicationSettings = { ...defaultApplicationSettings() };
    applicationSettings.settings.mfa.factor = {
      defaultFactorId: testContext.domain.factors[0].id,
      applicationFactors: [testContext.domain.factors[0]],
    };
    applicationSettings.settings.mfa.stepUpAuthenticationRule = '{{ true }}';
    applicationSettings.settings.mfa.stepUpAuthentication = { active: true, stepUpAuthenticationRule: '{{ true }}' };
    applicationSettings.settings.mfa.enrollment = { forceEnrollment: true };
    applicationSettings.settings.mfa.enroll = { active: true, enrollmentSkipActive: false, forceEnrollment: true, type: 'required' };
    applicationSettings.settings.mfa.challenge = { active: true, challengeRule: '', type: 'required' };

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

    const authResponse3 = await withRetry(() =>
      get(testContext.oidc.clientAuthorizationEndpoint, 302, { Cookie: enrollmentPostResponse.headers['set-cookie'] }, 'challenge'),
    );

    expect(authResponse3.headers['location']).toBeDefined();
    expect(authResponse3.headers['location']).toContain('/challenge');
  });
});

describe('With enabled factors and disabled enrollment and disabled challange', () => {
  it('should stop MFA flow', async () => {
    const applicationSettings = { ...defaultApplicationSettings() };
    applicationSettings.settings.mfa.factor = {
      defaultFactorId: testContext.domain.factors[0].id,
      applicationFactors: [testContext.domain.factors[0]],
    };

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

    const authResponseFinal = await withRetry(() => followUpGet(loginPostResponse, 302, 'code='));

    expect(authResponseFinal.headers['location']).toBeDefined();
    expect(authResponseFinal.headers['location']).toContain(testContext.application.redirectUris[0]);
    expect(authResponseFinal.headers['location']).toContain('code=');
  });
});

describe('With enabled factors and but failing selection rule', () => {
  it('only default factor should be visible', async () => {
    const applicationSettings = {
      ...defaultApplicationSettings(),
      settings: {
        mfa: {
          factor: {
            defaultFactorId: testContext.domain.factors[0].id,
            applicationFactors: [
              { id: testContext.domain.factors[0].id, selectionRule: '{{ false }}' },
              { id: testContext.domain.factors[1].id, selectionRule: '{{ false }}' },
            ],
          },
          enroll: { active: true, enrollmentSkipActive: false, forceEnrollment: false, type: 'required' },
        },
      },
    };

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
    const enrollmentPage = await withRetry(() => followUpGet(authResponseFinal, 200));

    const factorId = await extractDomValue(enrollmentPage, '[name=factorId]');

    expect(factorId).toBe(testContext.domain.factors[0].id);
  });
});
