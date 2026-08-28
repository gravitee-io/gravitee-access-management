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
import { jira } from '@specs-utils/jira';
import { performDelete, performGet, getWellKnownOpenIdConfiguration } from '@gateway-commands/oauth-oidc-commands';
import { waitFor } from '@management-commands/domain-management-commands';
import { getDomainManagerUrl } from '@management-commands/service/utils';
import { Domain, initClient, initDomain, enableDomain, removeDomain, TestSuiteContext } from './fixture/mfa-setup-fixture';
import { processLoginFromContext, processMfaEndToEnd } from './fixture/mfa-flow-fixture';
import { setup } from '../../test-fixture';

setup(300000);

/**
 * AM-2218 / UC-AM45 — a remembered device stops being trusted.
 *
 * Every test remembers a device and confirms the challenge is skipped before doing anything else,
 * so a later challenge means the trust ended rather than remember-device never having worked.
 */
const EXPIRATION_SECONDS = 10;

/** Long enough past `expiresAt` that a slow gateway cannot make the result ambiguous. */
const WAIT_PAST_EXPIRY_MS = (EXPIRATION_SECONDS + 3) * 1000;

const domain = {
  admin: { username: 'admin', password: 'adminadmin' },
  domain: { domainHrid: 'mfa-remember-device-am2218' },
} as Domain;

function rememberDeviceSettings(dom: Domain) {
  return {
    factors: [],
    settings: {
      mfa: {
        factor: {
          defaultFactorId: dom.domain.factors[0].id,
          applicationFactors: [dom.domain.factors[0], dom.domain.factors[1]],
        },
        stepUpAuthenticationRule: '',
        stepUpAuthentication: { active: false, stepUpAuthenticationRule: '' },
        adaptiveAuthenticationRule: '',
        rememberDevice: {
          active: true,
          skipRememberDevice: false,
          expirationTimeSeconds: EXPIRATION_SECONDS,
          deviceIdentifierId: dom.domain.devices[0].id,
        },
        enrollment: { forceEnrollment: false },
        enroll: { active: false, enrollmentSkipActive: false, forceEnrollment: false, type: 'required' },
        challenge: { active: true, challengeRule: '', type: 'required' },
      },
    },
  };
}

let client: any;
let authorizationEndpoint: string;

const adminHeaders = () => ({ Authorization: `Bearer ${domain.admin.accessToken}` });
const userDevicesUrl = (userId: string) => `${getDomainManagerUrl(domain.domain.domainId)}/users/${userId}/devices`;

const findUserId = async (username: string) => {
  const res = await performGet(`${getDomainManagerUrl(domain.domain.domainId)}/users?q=${username}`, '', adminHeaders());
  const id = res.body?.data?.[0]?.id;
  if (!id) {
    throw new Error(`No user found for ${username}`);
  }
  return id;
};

/** The devices shown against the user, which is where a remembered device appears. */
const listDevices = async (userId: string) => {
  const res = await performGet(userDevicesUrl(userId), '', adminHeaders()).expect(200);
  return res.body;
};

/** Signs in on `deviceId` and reports whether the challenge was skipped. */
const signInOn = async (ctx: TestSuiteContext, deviceId: string) => {
  const response = await processLoginFromContext(ctx, true, deviceId);
  const location = response.headers['location'];
  expect(location).toBeDefined();
  return { response, location, skippedChallenge: location.includes('code=') };
};

/** Remembers `deviceId` for a user and returns their id once the device is trusted. */
const rememberDeviceFor = async (userIndex: number, deviceId: string) => {
  const ctx = new TestSuiteContext(domain, client, domain.domain.users[userIndex], authorizationEndpoint);
  ctx.session = await processMfaEndToEnd(ctx, true, deviceId);
  const userId = await findUserId(ctx.user.username);

  // The anchor for everything below: this device is trusted right now.
  const first = await signInOn(ctx, deviceId);
  expect(first.skippedChallenge).toBe(true);

  return { ctx, userId };
};

beforeAll(async () => {
  await initDomain(domain, 3);
  client = await initClient(domain, 'remember-device', rememberDeviceSettings(domain));
  await enableDomain(domain);
  await waitFor(3000);
  const oidc = await getWellKnownOpenIdConfiguration(domain.domain.domainHrid).expect(200);
  authorizationEndpoint = oidc.body.authorization_endpoint;
});

afterAll(async () => {
  await removeDomain(domain);
});

describe('Remember device stops being trusted', () => {
  it(jira`the device is challenged again once the remembered period passes ${'AM-2218'}`, async () => {
    const { ctx, userId } = await rememberDeviceFor(0, 'am2218-device-expiry');

    const [device] = await listDevices(userId);
    expect(device.deviceId).toEqual('am2218-device-expiry');
    // The period is what decides the trust, so it is read back rather than assumed.
    expect(device.expiresAt - device.createdAt).toEqual(EXPIRATION_SECONDS * 1000);

    await waitFor(WAIT_PAST_EXPIRY_MS);

    const afterExpiry = await signInOn(ctx, 'am2218-device-expiry');
    expect(afterExpiry.skippedChallenge).toBe(false);
    expect(afterExpiry.location).toContain('/mfa/challenge');

    // The record is removed once it lapses rather than left behind unused.
    expect(await listDevices(userId)).toEqual([]);
  });

  it(jira`another device is still challenged ${'AM-2218'}`, async () => {
    const { ctx, userId } = await rememberDeviceFor(1, 'am2218-device-known');

    const other = await signInOn(ctx, 'am2218-device-other');
    expect(other.skippedChallenge).toBe(false);
    expect(other.location).toContain('/mfa/challenge');

    // Being challenged did not add the second device to the trusted ones.
    const devices = await listDevices(userId);
    expect(devices.map((d) => d.deviceId)).toEqual(['am2218-device-known']);
  });

  it(jira`deleting the remembered device challenges the user again ${'AM-2218'}`, async () => {
    const { ctx, userId } = await rememberDeviceFor(2, 'am2218-device-deleted');

    const [device] = await listDevices(userId);
    await performDelete(`${userDevicesUrl(userId)}/${device.id}`, '', adminHeaders()).expect(204);
    expect(await listDevices(userId)).toEqual([]);

    const afterDelete = await signInOn(ctx, 'am2218-device-deleted');
    expect(afterDelete.skippedChallenge).toBe(false);
    expect(afterDelete.location).toContain('/mfa/challenge');

    // The user can trust the device again, so removing it is not a one-way door.
    const challengePage = await performGet(afterDelete.location, '', {
      Cookie: afterDelete.response.headers['set-cookie'],
    }).expect(200);
    expect(challengePage.text).toContain('name="rememberDeviceConsent"');
  });
});
