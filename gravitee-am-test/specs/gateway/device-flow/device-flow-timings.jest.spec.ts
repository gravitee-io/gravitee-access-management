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
import { waitFor } from '@management-commands/domain-management-commands';
import { setup } from '../../test-fixture';
import { DEVICE_FLOW_TEST, DeviceFlowFixture, setupDeviceFlowFixture } from './fixtures/device-flow-fixture';

setup(200000);

const DOMAIN_POLLING_INTERVAL = 3;
const PAST_DOMAIN_INTERVAL_MS = 4000;
const APPLICATION_TIMINGS = { deviceCodeExpiry: 120, pollingInterval: 2 };

let fixture: DeviceFlowFixture;

beforeAll(async () => {
  fixture = await setupDeviceFlowFixture({ pollingInterval: DOMAIN_POLLING_INTERVAL });
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('OAuth2 - RFC 8628 - Device Authorization Grant - domain polling interval', () => {
  it('must serve a device that respects the interval', async () => {
    const body = await fixture.requestPublicDeviceCode();
    expect(body.interval).toEqual(DOMAIN_POLLING_INTERVAL);

    await waitFor(PAST_DOMAIN_INTERVAL_MS);

    const pending = await fixture.pollPublic(body.device_code).expect(400);
    expect(pending.body.error).toEqual('authorization_pending');
  });

  it('must slow down a device that polls too fast, widening the interval on each offence', async () => {
    const body = await fixture.requestPublicDeviceCode();

    const first = await fixture.pollPublic(body.device_code).expect(400);
    expect(first.body.error).toEqual('slow_down');

    const second = await fixture.pollPublic(body.device_code).expect(400);
    expect(second.body.error).toEqual('slow_down');

    await waitFor(PAST_DOMAIN_INTERVAL_MS);

    const third = await fixture.pollPublic(body.device_code).expect(400);
    expect(third.body.error).toEqual('slow_down');
  });
});

describe('OAuth2 - RFC 8628 - Device Authorization Grant - per-application timings', () => {
  it('must issue codes with the application timings and hold the device to its interval', async () => {
    await fixture.setApplicationTimings(APPLICATION_TIMINGS);

    const body = await fixture.requestDeviceCode('openid');
    expect(body.expires_in).toEqual(APPLICATION_TIMINGS.deviceCodeExpiry);
    expect(body.interval).toEqual(APPLICATION_TIMINGS.pollingInterval);

    const tooFast = await fixture.poll(body.device_code).expect(400);
    expect(tooFast.body.error).toEqual('slow_down');
  });

  it('must leave an application that does not override tracking the domain', async () => {
    await fixture.setApplicationTimings(APPLICATION_TIMINGS);

    const inheriting = await fixture.requestPublicDeviceCode();
    expect(inheriting.expires_in).toEqual(DEVICE_FLOW_TEST.DEVICE_CODE_EXPIRY);
    expect(inheriting.interval).toEqual(DOMAIN_POLLING_INTERVAL);
  });

  it('must return the application to the domain timings once the override is cleared', async () => {
    await fixture.setApplicationTimings(APPLICATION_TIMINGS);
    await fixture.setApplicationTimings(null);

    const body = await fixture.requestDeviceCode('openid');
    expect(body.expires_in).toEqual(DEVICE_FLOW_TEST.DEVICE_CODE_EXPIRY);
    expect(body.interval).toEqual(DOMAIN_POLLING_INTERVAL);
  });
});
