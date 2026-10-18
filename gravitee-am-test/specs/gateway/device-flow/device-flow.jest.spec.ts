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
import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { performFormPost, performGet } from '@gateway-commands/oauth-oidc-commands';
import { JWT_FORMAT } from '@specs-utils/jwt-format';
import { setup } from '../../test-fixture';
import { DEVICE_FLOW_TEST, DeviceFlowFixture, setupDeviceFlowFixture } from './fixtures/device-flow-fixture';

setup(200000);

let fixture: DeviceFlowFixture;

beforeAll(async () => {
  fixture = await setupDeviceFlowFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('OAuth2 - RFC 8628 - Device Authorization Grant - device authorization', () => {
  it('must advertise the endpoint and the grant type in the discovery document', async () => {
    const discovery = await performGet(`${process.env.AM_GATEWAY_URL}/${fixture.domain.hrid}/oidc/.well-known/openid-configuration`).expect(
      200,
    );

    expect(discovery.body.device_authorization_endpoint).toEqual(fixture.deviceAuthorizationEndpoint);
    expect(discovery.body.grant_types_supported).toContain(DEVICE_FLOW_TEST.DEVICE_CODE_GRANT);
  });

  it('must issue a well-formed device authorization response to a confidential client', async () => {
    const body = await fixture.requestDeviceCode();

    expect(body.device_code).toEqual(expect.any(String));
    expect(body.device_code.length).toBeGreaterThan(0);
    expect(body.user_code).toMatch(DEVICE_FLOW_TEST.USER_CODE_FORMAT);
    expect(body.verification_uri).toContain(`/${fixture.domain.hrid}/oauth/device`);
    expect(body.expires_in).toEqual(DEVICE_FLOW_TEST.DEVICE_CODE_EXPIRY);
    expect(body.interval).toEqual(0);
  });

  it('must issue the same response to a public client sending no secret', async () => {
    const body = await fixture.requestPublicDeviceCode();

    expect(body.device_code).toEqual(expect.any(String));
    expect(body.device_code.length).toBeGreaterThan(0);
    expect(body.user_code).toMatch(DEVICE_FLOW_TEST.USER_CODE_FORMAT);
    expect(body.verification_uri).toContain(`/${fixture.domain.hrid}/oauth/device`);
    expect(body.expires_in).toEqual(DEVICE_FLOW_TEST.DEVICE_CODE_EXPIRY);
    expect(body.interval).toEqual(0);
  });

  it('must offer a complete verification URI carrying the user code', async () => {
    const body = await fixture.requestDeviceCode();

    expect(body.verification_uri_complete).toContain(body.verification_uri);
    expect(body.verification_uri_complete).toContain(`user_code=${body.user_code}`);
  });
});

describe('OAuth2 - RFC 8628 - Device Authorization Grant - polling', () => {
  it('must answer authorization_pending while the user has not approved', async () => {
    const body = await fixture.requestDeviceCode();

    const pending = await fixture.poll(body.device_code).expect(400);
    expect(pending.body.error).toEqual('authorization_pending');
  });

  it('must reject an unknown device code', async () => {
    const unknown = await fixture.poll('unknown-device-code').expect(400);
    expect(unknown.body.error).toEqual('invalid_grant');
  });

  it('must reject a device code issued to another client', async () => {
    const body = await fixture.requestPublicDeviceCode();

    const rejected = await fixture.poll(body.device_code).expect(400);
    expect(rejected.body.error).toEqual('invalid_grant');
  });
});

describe('OAuth2 - RFC 8628 - Device Authorization Grant - verification', () => {
  it('must deliver tokens once the user approved on a second device', async () => {
    const approvingUser = await fixture.createUser();
    const body = await fixture.requestDeviceCode();

    const pending = await fixture.poll(body.device_code).expect(400);
    expect(pending.body.error).toEqual('authorization_pending');

    const { consentPage } = await fixture.approveThroughConsent(body.user_code, fixture.confidentialClientId, approvingUser);
    expect(consentPage.text).toContain(fixture.confidentialApp.settings.oauth.clientName ?? fixture.confidentialApp.name);
    expect(consentPage.text).toContain(DEVICE_FLOW_TEST.SCOPE);

    const tokens = await fixture.poll(body.device_code).expect(200);
    expect(tokens.body.access_token).toMatch(JWT_FORMAT);
    expect(tokens.body.refresh_token).toMatch(JWT_FORMAT);
    expect(tokens.body.id_token).toMatch(JWT_FORMAT);
    expect(tokens.body.token_type).toEqual('bearer');
    expect(tokens.body.scope).toContain('openid');

    const replayed = await fixture.poll(body.device_code).expect(400);
    expect(replayed.body.error).toEqual('invalid_grant');
  });

  it('must skip the consent screen on a second pairing of the same application and scopes', async () => {
    const approvingUser = await fixture.createUser();
    const first = await fixture.requestDeviceCode();
    await fixture.approveThroughConsent(first.user_code, fixture.confidentialClientId, approvingUser);
    await fixture.poll(first.device_code).expect(200);

    const second = await fixture.requestDeviceCode();
    await fixture.approveWithRememberedConsent(second.user_code, fixture.confidentialClientId, approvingUser);

    const tokens = await fixture.poll(second.device_code).expect(200);
    expect(tokens.body.access_token).toMatch(JWT_FORMAT);
  });

  it('must let the user retry after an incorrect code without restarting on the device', async () => {
    const body = await fixture.requestDeviceCode();
    const session = await fixture.signInOnSecondDevice(fixture.confidentialClientId);

    const wrongCode = await fixture.enterCode(session, DEVICE_FLOW_TEST.UNKNOWN_USER_CODE).expect(302);
    expect(wrongCode.headers['location']).toContain('/oauth/device');

    const retryPage = await performGet(wrongCode.headers['location'], '', { Cookie: session.cookie }).expect(200);
    expect(retryPage.text).toContain('user_code');

    const stillPending = await fixture.poll(body.device_code).expect(400);
    expect(stillPending.body.error).toEqual('authorization_pending');

    const reopened = await fixture.reopenCodeEntryPage(session, fixture.confidentialClientId);
    const retried = await fixture.enterCode(reopened, body.user_code).expect(302);
    expect(retried.headers['location']).toContain('/oauth/authorize');
  });

  it('must reject the code entry form without a CSRF token', async () => {
    const session = await fixture.signInOnSecondDevice(fixture.confidentialClientId);

    const rejected = await performFormPost(
      session.codeEntryPage.action,
      '',
      { user_code: 'BCDF-GHJK' },
      { Cookie: session.cookie, 'Content-type': 'application/x-www-form-urlencoded' },
    ).expect(302);
    expect(rejected.headers['location']).toContain('error=');
  });

  it('must answer access_denied to the device once the user refused the code', async () => {
    const body = await fixture.requestDeviceCode();

    const pending = await fixture.poll(body.device_code).expect(400);
    expect(pending.body.error).toEqual('authorization_pending');

    const session = await fixture.signInOnSecondDevice(fixture.confidentialClientId);
    await fixture.rejectCode(session, body.user_code).expect(200);

    const denied = await fixture.poll(body.device_code).expect(400);
    expect(denied.body.error).toEqual('access_denied');

    const afterCleanUp = await fixture.poll(body.device_code).expect(400);
    expect(afterCleanUp.body.error).toEqual('invalid_grant');
  });

  it('must refuse to approve a code that was refused', async () => {
    const body = await fixture.requestDeviceCode();

    const rejectingSession = await fixture.signInOnSecondDevice(fixture.confidentialClientId);
    await fixture.rejectCode(rejectingSession, body.user_code).expect(200);

    const approvingSession = await fixture.signInOnSecondDevice(fixture.confidentialClientId);
    const retry = await fixture.enterCode(approvingSession, body.user_code).expect(302);
    expect(retry.headers['location']).toContain('/oauth/device');
    expect(retry.headers['location']).not.toContain('/oauth/authorize');

    const denied = await fixture.poll(body.device_code).expect(400);
    expect(denied.body.error).toEqual('access_denied');
  });
});
