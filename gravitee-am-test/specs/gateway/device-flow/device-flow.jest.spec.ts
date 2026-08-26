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
import { afterAll, beforeAll, expect } from '@jest/globals';
import { OAuth2Fixture, setupFixture } from '../oauth2/fixture/oauth2-fixture';
import { extractXsrfTokenAndActionResponse, performFormPost, performGet, performPost } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { patchApplication } from '@management-commands/application-management-commands';
import { patchDomain, waitFor, waitForDomainSync } from '@management-commands/domain-management-commands';
import { createDomainForm } from '@management-commands/form-management-commands';
import { JWT_FORMAT } from '@specs-utils/jwt-format';
import { retryImmediatelyForThisFile, setup } from '../../test-fixture';
import { getAuditApi } from '@management-commands/service/utils';
import { retryUntil } from '@utils-commands/retry';
import type { ApplicationDeviceFlowSettings } from '@management-models/ApplicationDeviceFlowSettings';

setup(200000);
retryImmediatelyForThisFile();

const DEVICE_CODE_GRANT = 'urn:ietf:params:oauth:grant-type:device_code';
const FORM_HEADERS = { 'Content-type': 'application/x-www-form-urlencoded' };

let fixture: OAuth2Fixture;
let user: { username: string; password: string };
let deviceAuthorizationEndpoint: string;
let verificationEndpoint: string;

const confidentialClientId = () => fixture.application.settings.oauth.clientId;
const publicClientId = () => fixture.anotherApplication.settings.oauth.clientId;
const confidentialAuth = () => ({ Authorization: 'Basic ' + applicationBase64Token(fixture.application) });
const verificationUri = (clientId: string) => `${verificationEndpoint}?client_id=${clientId}`;

const DEFAULT_WRONG_CODE_BUDGET = 10;

const wrongCodeBudget = async (): Promise<number> => {
  const nodeMonitoringUrl = process.env.AM_GATEWAY_NODE_MONITORING_URL;
  const adminUsername = process.env.AM_ADMIN_USERNAME;
  const adminPassword = process.env.AM_ADMIN_PASSWORD;

  if (!nodeMonitoringUrl || !adminUsername || !adminPassword) {
    return DEFAULT_WRONG_CODE_BUDGET;
  }

  const response = await fetch(`${nodeMonitoringUrl}/configuration`, {
    headers: { Authorization: `Basic ${Buffer.from(`${adminUsername}:${adminPassword}`).toString('base64')}` },
  });

  if (!response.ok) {
    return DEFAULT_WRONG_CODE_BUDGET;
  }

  const configuration = await response.json();
  const limit = Number(configuration['device_flow_rate.limit'] ?? configuration['DEVICE_FLOW_RATE_LIMIT'] ?? DEFAULT_WRONG_CODE_BUDGET);
  return Number.isNaN(limit) ? DEFAULT_WRONG_CODE_BUDGET : limit;
};

beforeAll(async () => {
  fixture = await setupFixture({
    withOpenidScope: true,
    type: 'WEB',
    grantTypes: [DEVICE_CODE_GRANT, 'refresh_token'],
  });
  user = fixture.users[0];
  deviceAuthorizationEndpoint = fixture.oidc.token_endpoint.replace('/oauth/token', '/oauth/device_authorization');
  verificationEndpoint = fixture.oidc.token_endpoint.replace('/oauth/token', '/oauth/device');

  await patchApplication(
    fixture.masterDomain.id,
    fixture.accessToken,
    { settings: { oauth: { grantTypes: [DEVICE_CODE_GRANT], tokenEndpointAuthMethod: 'none' } } } as any,
    fixture.anotherApplication.id,
  );
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

const enableDeviceFlow = async (pollingInterval = 0) => {
  await patchDomain(fixture.masterDomain.id, fixture.accessToken, {
    oidc: { deviceFlowSettings: { enabled: true, deviceCodeExpiry: 600, pollingInterval } },
  } as any);
  await waitForDomainSync();
};

const requestDeviceCode = async (body: string, headers: Record<string, string>) => {
  const response = await performPost(deviceAuthorizationEndpoint, '', body, { ...FORM_HEADERS, ...headers }).expect(200);
  return response.body;
};

const poll = (deviceCode: string) =>
  performPost(fixture.oidc.token_endpoint, '', `grant_type=${encodeURIComponent(DEVICE_CODE_GRANT)}&device_code=${deviceCode}`, {
    ...FORM_HEADERS,
    ...confidentialAuth(),
  });

const nextCookie = (response: any, fallback: any) => response.headers['set-cookie'] ?? fallback;

const signInOnSecondDevice = async (clientId: string, entryUri = verificationUri(clientId), as = user) => {
  const unauthenticated = await performGet(entryUri).expect(302);
  expect(unauthenticated.headers['location']).toContain(`/${fixture.masterDomain.hrid}/login`);

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

const reopenCodeEntryPage = async (session: any, clientId: string) => {
  const codeEntryPage = await extractXsrfTokenAndActionResponse({
    headers: { location: verificationUri(clientId), 'set-cookie': session.cookie },
  });
  return { codeEntryPage, cookie: codeEntryPage.headers['set-cookie'] ?? session.cookie };
};

const enterCode = (session: any, userCode: string) =>
  performFormPost(
    session.codeEntryPage.action,
    '',
    { 'X-XSRF-TOKEN': session.codeEntryPage.token, user_code: userCode },
    { Cookie: session.cookie, ...FORM_HEADERS },
  );

const rejectCode = (session: any, userCode: string) =>
  performFormPost(
    session.codeEntryPage.action,
    '',
    { 'X-XSRF-TOKEN': session.codeEntryPage.token, user_code: userCode, action: 'reject' },
    { Cookie: session.cookie, ...FORM_HEADERS },
  );

const enterCodeAndReachAuthorize = async (userCode: string, clientId: string) => {
  const session = await signInOnSecondDevice(clientId);
  const postCode = await enterCode(session, userCode).expect(302);
  expect(postCode.headers['location']).toContain('/oauth/authorize');
  return { session: { ...session, cookie: nextCookie(postCode, session.cookie) }, authorizeUrl: postCode.headers['location'] };
};

const approveThroughConsent = async (userCode: string, clientId: string, scope: string) => {
  const { session, authorizeUrl } = await enterCodeAndReachAuthorize(userCode, clientId);

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
    { 'X-XSRF-TOKEN': consentForm.token, [`scope.${scope}`]: true, 'scope.openid': true, user_oauth_approval: true },
    { Cookie: submitted, ...FORM_HEADERS },
  ).expect(302);

  const completion = await performGet(postConsent.headers['location'], '', { Cookie: nextCookie(postConsent, submitted) }).expect(200);
  return { completion, consentPage };
};

const approveWithRememberedConsent = async (userCode: string, clientId: string) => {
  const { session, authorizeUrl } = await enterCodeAndReachAuthorize(userCode, clientId);
  return performGet(authorizeUrl, '', { Cookie: session.cookie }).expect(200);
};

describe('OAuth2 - RFC 8628 - Device Authorization Grant', () => {
  describe('while the domain setting is off', () => {
    it('must not expose the device authorization endpoint', async () => {
      await performPost(deviceAuthorizationEndpoint, '', 'scope=openid', { ...FORM_HEADERS, ...confidentialAuth() }).expect(404);
    });

    it('must not expose the verification page', async () => {
      await performGet(verificationUri(confidentialClientId())).expect(404);
    });
  });

  describe('once the domain enables the flow', () => {
    beforeAll(async () => {
      await enableDeviceFlow();
    });

    it('must advertise the endpoint and the grant type in the discovery document', async () => {
      const discovery = await performGet(
        `${process.env.AM_GATEWAY_URL}/${fixture.masterDomain.hrid}/oidc/.well-known/openid-configuration`,
      ).expect(200);

      expect(discovery.body.device_authorization_endpoint).toEqual(deviceAuthorizationEndpoint);
      expect(discovery.body.grant_types_supported).toContain(DEVICE_CODE_GRANT);
    });

    it('must issue a well-formed device authorization response to a confidential client', async () => {
      const body = await requestDeviceCode('scope=openid%20scope1', confidentialAuth());

      expect(body.device_code).toEqual(expect.any(String));
      expect(body.device_code.length).toBeGreaterThan(0);
      expect(body.user_code).toMatch(/^[BCDFGHJKLMNPQRSTVWXZ]{4}-[BCDFGHJKLMNPQRSTVWXZ]{4}$/);
      expect(body.verification_uri).toContain(`/${fixture.masterDomain.hrid}/oauth/device`);
      expect(body.expires_in).toEqual(600);
      expect(body.interval).toEqual(0);
    });

    it('must issue the same response to a public client sending no secret', async () => {
      const body = await requestDeviceCode(`client_id=${publicClientId()}&scope=openid`, {});

      expect(body.device_code).toEqual(expect.any(String));
      expect(body.device_code.length).toBeGreaterThan(0);
      expect(body.user_code).toMatch(/^[BCDFGHJKLMNPQRSTVWXZ]{4}-[BCDFGHJKLMNPQRSTVWXZ]{4}$/);
      expect(body.verification_uri).toContain(`/${fixture.masterDomain.hrid}/oauth/device`);
      expect(body.expires_in).toEqual(600);
      expect(body.interval).toEqual(0);
    });

    it('must answer authorization_pending while the user has not approved', async () => {
      const body = await requestDeviceCode('scope=openid%20scope1', confidentialAuth());

      const pending = await poll(body.device_code).expect(400);
      expect(pending.body.error).toEqual('authorization_pending');
    });

    it('must reject an unknown device code', async () => {
      const unknown = await poll('unknown-device-code').expect(400);
      expect(unknown.body.error).toEqual('invalid_grant');
    });

    it('must reject a device code issued to another client', async () => {
      const body = await requestDeviceCode(`client_id=${publicClientId()}&scope=openid`, {});

      const rejected = await poll(body.device_code).expect(400);
      expect(rejected.body.error).toEqual('invalid_grant');
    });

    it('must deliver tokens once the user approved on a second device', async () => {
      const body = await requestDeviceCode('scope=openid%20scope1', confidentialAuth());

      const pending = await poll(body.device_code).expect(400);
      expect(pending.body.error).toEqual('authorization_pending');

      const { consentPage } = await approveThroughConsent(body.user_code, confidentialClientId(), 'scope1');
      expect(consentPage.text).toContain(fixture.application.settings.oauth.clientName ?? fixture.application.name);
      expect(consentPage.text).toContain('scope1');

      const tokens = await poll(body.device_code).expect(200);
      expect(tokens.body.access_token).toMatch(JWT_FORMAT);
      expect(tokens.body.refresh_token).toMatch(JWT_FORMAT);
      expect(tokens.body.id_token).toMatch(JWT_FORMAT);
      expect(tokens.body.token_type).toEqual('bearer');
      expect(tokens.body.scope).toContain('openid');

      const replayed = await poll(body.device_code).expect(400);
      expect(replayed.body.error).toEqual('invalid_grant');
    });

    it('must skip the consent screen on a second pairing of the same application and scopes', async () => {
      const body = await requestDeviceCode('scope=openid%20scope1', confidentialAuth());

      await approveWithRememberedConsent(body.user_code, confidentialClientId());

      const tokens = await poll(body.device_code).expect(200);
      expect(tokens.body.access_token).toMatch(JWT_FORMAT);
    });

    it('must let the user retry after an incorrect code without restarting on the device', async () => {
      const body = await requestDeviceCode('scope=openid%20scope1', confidentialAuth());
      const session = await signInOnSecondDevice(confidentialClientId());

      const wrongCode = await enterCode(session, 'ZZZZ-ZZZZ').expect(302);
      expect(wrongCode.headers['location']).toContain('/oauth/device');

      const retryPage = await performGet(wrongCode.headers['location'], '', { Cookie: session.cookie }).expect(200);
      expect(retryPage.text).toContain('user_code');

      const stillPending = await poll(body.device_code).expect(400);
      expect(stillPending.body.error).toEqual('authorization_pending');

      const retried = await enterCode(await reopenCodeEntryPage(session, confidentialClientId()), body.user_code).expect(302);
      expect(retried.headers['location']).toContain('/oauth/authorize');
    });

    it('must reject the code entry form without a CSRF token', async () => {
      const session = await signInOnSecondDevice(confidentialClientId());

      const rejected = await performFormPost(
        session.codeEntryPage.action,
        '',
        { user_code: 'BCDF-GHJK' },
        { Cookie: session.cookie, ...FORM_HEADERS },
      ).expect(302);
      expect(rejected.headers['location']).toContain('error=');
    });

    it('must answer access_denied to the device once the user refused the code', async () => {
      const body = await requestDeviceCode('scope=openid%20scope1', confidentialAuth());

      const pending = await poll(body.device_code).expect(400);
      expect(pending.body.error).toEqual('authorization_pending');

      const session = await signInOnSecondDevice(confidentialClientId());
      await rejectCode(session, body.user_code).expect(200);

      const denied = await poll(body.device_code).expect(400);
      expect(denied.body.error).toEqual('access_denied');

      const afterCleanUp = await poll(body.device_code).expect(400);
      expect(afterCleanUp.body.error).toEqual('invalid_grant');
    });

    it('must refuse to approve a code that was refused', async () => {
      const body = await requestDeviceCode('scope=openid%20scope1', confidentialAuth());

      const rejectingSession = await signInOnSecondDevice(confidentialClientId());
      await rejectCode(rejectingSession, body.user_code).expect(200);

      const approvingSession = await signInOnSecondDevice(confidentialClientId());
      const retry = await enterCode(approvingSession, body.user_code).expect(302);
      expect(retry.headers['location']).toContain('/oauth/device');
      expect(retry.headers['location']).not.toContain('/oauth/authorize');

      const denied = await poll(body.device_code).expect(400);
      expect(denied.body.error).toEqual('access_denied');
    });

    it('must offer a complete verification URI carrying the user code', async () => {
      const body = await requestDeviceCode('scope=openid%20scope1', confidentialAuth());

      expect(body.verification_uri_complete).toContain(body.verification_uri);
      expect(body.verification_uri_complete).toContain(`user_code=${body.user_code}`);
    });

    it('must pre-fill the scanned code in plain sight for an authenticated user', async () => {
      const body = await requestDeviceCode('scope=openid%20scope1', confidentialAuth());
      const session = await signInOnSecondDevice(confidentialClientId());

      const prefilled = await performGet(body.verification_uri_complete, '', { Cookie: session.cookie }).expect(200);

      expect(prefilled.text).toContain(`value="${body.user_code}"`);
      expect(prefilled.text).toContain('name="user_code"');
    });

    it('must carry the scanned code through the login redirect', async () => {
      const body = await requestDeviceCode('scope=openid%20scope1', confidentialAuth());
      const clientId = confidentialClientId();

      const unauthenticated = await performGet(body.verification_uri_complete).expect(302);
      expect(unauthenticated.headers['location']).toContain(`/${fixture.masterDomain.hrid}/login`);

      const loginPage = await extractXsrfTokenAndActionResponse(unauthenticated);
      const postLogin = await performFormPost(
        loginPage.action,
        '',
        { 'X-XSRF-TOKEN': loginPage.token, username: user.username, password: user.password, client_id: clientId },
        { Cookie: loginPage.headers['set-cookie'], ...FORM_HEADERS },
      ).expect(302);

      expect(postLogin.headers['location']).toContain(`user_code=${body.user_code}`);

      const prefilled = await performGet(postLogin.headers['location'], '', {
        Cookie: postLogin.headers['set-cookie'] ?? loginPage.headers['set-cookie'],
      }).expect(200);
      expect(prefilled.text).toContain(`value="${body.user_code}"`);
    });

    it('must not approve a scanned code without an explicit confirmation', async () => {
      const body = await requestDeviceCode('scope=openid%20scope1', confidentialAuth());

      await signInOnSecondDevice(confidentialClientId(), body.verification_uri_complete);

      const stillPending = await poll(body.device_code).expect(400);
      expect(stillPending.body.error).toEqual('authorization_pending');
    });

    it('must report the invalid outcome for a tampered code carried by the URI', async () => {
      const body = await requestDeviceCode('scope=openid%20scope1', confidentialAuth());
      const tamperedUri = body.verification_uri_complete.replace(body.user_code, 'ZZZZ-ZZZZ');

      const session = await signInOnSecondDevice(confidentialClientId(), tamperedUri);
      const submitted = await enterCode(session, 'ZZZZ-ZZZZ').expect(302);

      expect(submitted.headers['location']).toContain('/oauth/device');
      expect(submitted.headers['location']).not.toContain('/oauth/authorize');

      const stillPending = await poll(body.device_code).expect(400);
      expect(stillPending.body.error).toEqual('authorization_pending');
    });

    it('must let the user correct a pre-filled code before confirming', async () => {
      const scanned = await requestDeviceCode('scope=openid%20scope1', confidentialAuth());
      const intended = await requestDeviceCode('scope=openid%20scope1', confidentialAuth());

      const session = await signInOnSecondDevice(confidentialClientId(), scanned.verification_uri_complete);
      const corrected = await enterCode(session, intended.user_code).expect(302);

      expect(corrected.headers['location']).toContain('/oauth/authorize');

      const untouched = await poll(scanned.device_code).expect(400);
      expect(untouched.body.error).toEqual('authorization_pending');
    });

    describe('once the domain overrides both device flow forms', () => {
      beforeAll(async () => {
        await createDomainForm(fixture.masterDomain.id, fixture.accessToken, {
          enabled: true,
          template: 'DEVICE_CODE_ENTRY',
          content:
            '<!DOCTYPE html><html xmlns:th="http://www.thymeleaf.org"><body><span>overridden code entry page</span>' +
            '<form th:action="${action}" method="post">' +
            '<input type="text" name="user_code"/>' +
            '<input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/>' +
            '<button type="submit"></button></form></body></html>',
        });
        await createDomainForm(fixture.masterDomain.id, fixture.accessToken, {
          enabled: true,
          template: 'DEVICE_COMPLETION',
          content:
            '<!DOCTYPE html><html xmlns:th="http://www.thymeleaf.org"><body>' +
            '<span>overridden completion page</span><span th:text="${outcome}"></span></body></html>',
        });
        await waitForDomainSync();
      });

      it('must serve both overridden forms through an approval', async () => {
        const body = await requestDeviceCode('scope=openid%20scope1', confidentialAuth());
        const { session, authorizeUrl } = await enterCodeAndReachAuthorize(body.user_code, confidentialClientId());

        const entryPage = await performGet(verificationUri(confidentialClientId()), '', { Cookie: session.cookie }).expect(200);
        expect(entryPage.text).toContain('overridden code entry page');

        const completion = await performGet(authorizeUrl, '', { Cookie: session.cookie }).expect(200);
        expect(completion.text).toContain('overridden completion page');
        expect(completion.text).toContain('approved');
      });

      it('must report the denied outcome on the completion page', async () => {
        const body = await requestDeviceCode('scope=openid%20scope1', confidentialAuth());
        const session = await signInOnSecondDevice(confidentialClientId());

        const rejection = await rejectCode(session, body.user_code).expect(200);

        expect(rejection.text).toContain('overridden completion page');
        expect(rejection.text).toContain('denied');
      });

      it('must report the invalid outcome for a code that is not outstanding', async () => {
        const session = await signInOnSecondDevice(confidentialClientId());

        const rejection = await rejectCode(session, 'ZZZZ-ZZZZ').expect(200);

        expect(rejection.text).toContain('overridden completion page');
        expect(rejection.text).toContain('invalid');
      });
    });
  });

  describe('audit log', () => {
    const deviceFlowAudits = (type: string, from: number) =>
      retryUntil(
        () =>
          getAuditApi(fixture.accessToken).listDomainAudits({
            organizationId: process.env.AM_DEF_ORG_ID,
            environmentId: process.env.AM_DEF_ENV_ID,
            domain: fixture.masterDomain.id,
            type,
            from,
            size: 10,
          }),
        (page) => page.data?.length > 0,
        { timeoutMillis: 10_000, intervalMillis: 250 },
      );

    it('must record an approval against the user who authorized the device', async () => {
      const from = Date.now();
      const body = await requestDeviceCode('scope=openid%20scope1', confidentialAuth());

      await approveWithRememberedConsent(body.user_code, confidentialClientId());

      const audits = await deviceFlowAudits('DEVICE_FLOW_APPROVED', from);
      expect(audits.data[0]).toMatchObject({
        type: 'DEVICE_FLOW_APPROVED',
        outcome: { status: 'success' },
        actor: { alternativeId: user.username, type: 'USER' },
        target: { id: fixture.application.id, type: 'APPLICATION' },
      });
    });

    it('must record a denial against the user who refused the device', async () => {
      const from = Date.now();
      const body = await requestDeviceCode('scope=openid%20scope1', confidentialAuth());
      const session = await signInOnSecondDevice(confidentialClientId());

      await rejectCode(session, body.user_code).expect(200);

      const audits = await deviceFlowAudits('DEVICE_FLOW_DENIED', from);
      expect(audits.data[0]).toMatchObject({
        type: 'DEVICE_FLOW_DENIED',
        outcome: { status: 'success' },
        actor: { alternativeId: user.username, type: 'USER' },
        target: { id: fixture.application.id, type: 'APPLICATION' },
      });
    });

    it('must record a failed verification, and nothing at all for the code issuance itself', async () => {
      const from = Date.now();
      await requestDeviceCode('scope=openid%20scope1', confidentialAuth());
      const session = await signInOnSecondDevice(confidentialClientId());

      await enterCode(session, 'ZZZZ-ZZZZ').expect(302);

      const audits = await deviceFlowAudits('DEVICE_FLOW_VERIFICATION_FAILED', from);
      expect(audits.data[0]).toMatchObject({
        type: 'DEVICE_FLOW_VERIFICATION_FAILED',
        outcome: { status: 'failure' },
        actor: { alternativeId: user.username, type: 'USER' },
        target: { id: fixture.application.id, type: 'APPLICATION' },
      });

      const approvals = await getAuditApi(fixture.accessToken).listDomainAudits({
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        domain: fixture.masterDomain.id,
        type: 'DEVICE_FLOW_APPROVED',
        from,
        size: 10,
      });
      expect(approvals.data ?? []).toHaveLength(0);
    });
  });

  describe('per-application timings', () => {
    const overrideTimings = async (deviceFlowSettings: ApplicationDeviceFlowSettings | null) => {
      await patchApplication(
        fixture.masterDomain.id,
        fixture.accessToken,
        { settings: { oauth: { deviceFlowSettings } } } as any,
        fixture.application.id,
      );
      await waitForDomainSync();
    };

    afterAll(async () => {
      await overrideTimings(null);
    });

    it('must issue codes with the application timings and hold the device to its interval', async () => {
      await overrideTimings({ deviceCodeExpiry: 120, pollingInterval: 2 });

      const body = await requestDeviceCode('scope=openid', confidentialAuth());
      expect(body.expires_in).toEqual(120);
      expect(body.interval).toEqual(2);

      const tooFast = await poll(body.device_code).expect(400);
      expect(tooFast.body.error).toEqual('slow_down');
    });

    it('must leave an application that does not override tracking the domain', async () => {
      await overrideTimings({ deviceCodeExpiry: 120, pollingInterval: 2 });

      const inheriting = await requestDeviceCode(`client_id=${publicClientId()}&scope=openid`, {});
      expect(inheriting.expires_in).toEqual(600);
      expect(inheriting.interval).toEqual(0);
    });

    it('must return the application to the domain timings once the override is cleared', async () => {
      await overrideTimings({ deviceCodeExpiry: 120, pollingInterval: 2 });
      await overrideTimings(null);

      const body = await requestDeviceCode('scope=openid', confidentialAuth());
      expect(body.expires_in).toEqual(600);
      expect(body.interval).toEqual(0);
    });
  });

  describe('once code entry is rate limited per user', () => {
    it('must spend a user budget on wrong codes only, and refuse that user alone once it runs out', async () => {
      const budget = await wrongCodeBudget();
      const guessingUser = fixture.users[1];
      const guessingUserSession = () => signInOnSecondDevice(publicClientId(), verificationUri(publicClientId()), guessingUser);

      for (let pairing = 0; pairing < budget; pairing++) {
        const paired = await requestDeviceCode(`client_id=${publicClientId()}&scope=openid`, {});
        const rightCode = await enterCode(await guessingUserSession(), paired.user_code).expect(302);
        expect(rightCode.headers['location']).toContain('/oauth/authorize');
      }

      let session = await guessingUserSession();
      for (let guess = 0; guess < budget; guess++) {
        const wrongCode = await enterCode(session, 'ZZZZ-ZZZZ').expect(302);
        expect(wrongCode.headers['location']).toContain('/oauth/device');
        session = await reopenCodeEntryPage(session, publicClientId());
      }

      const body = await requestDeviceCode(`client_id=${publicClientId()}&scope=openid`, {});
      const afterTheBudget = await enterCode(session, body.user_code).expect(302);
      expect(afterTheBudget.headers['location']).toContain('/oauth/device');
      expect(afterTheBudget.headers['location']).not.toContain('/oauth/authorize');

      const otherUserSession = await signInOnSecondDevice(publicClientId());
      const accepted = await enterCode(otherUserSession, body.user_code).expect(302);
      expect(accepted.headers['location']).toContain('/oauth/authorize');
    });
  });

  describe('once the domain enforces a polling interval', () => {
    beforeAll(async () => {
      await enableDeviceFlow(3);
    });

    it('must serve a device that respects the interval', async () => {
      const body = await requestDeviceCode('scope=openid', confidentialAuth());
      expect(body.interval).toEqual(3);

      await waitFor(4000);

      const pending = await poll(body.device_code).expect(400);
      expect(pending.body.error).toEqual('authorization_pending');
    });

    it('must slow down a device that polls too fast, widening the interval on each offence', async () => {
      const body = await requestDeviceCode('scope=openid', confidentialAuth());

      const first = await poll(body.device_code).expect(400);
      expect(first.body.error).toEqual('slow_down');

      const second = await poll(body.device_code).expect(400);
      expect(second.body.error).toEqual('slow_down');

      await waitFor(4000);

      const third = await poll(body.device_code).expect(400);
      expect(third.body.error).toEqual('slow_down');
    });
  });
});
