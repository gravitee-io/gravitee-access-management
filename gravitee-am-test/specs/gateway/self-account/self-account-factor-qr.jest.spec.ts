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
import { performGet, performPost } from '@gateway-commands/oauth-oidc-commands';
import { SelfAccountFactorFixture, setupFactorFixture, getEndUserAccessToken } from './fixture/self-account-fixture';
import { setup } from '../../test-fixture';

setup(300000);

// Tests run in order: the enrolment in the third test is what moves the user between states.
let fixture: SelfAccountFactorFixture;
let token: string;

const accountUrl = (path: string) => `${process.env.AM_GATEWAY_URL}/${fixture.domain.hrid}/account/api${path}`;
const auth = () => ({ Authorization: `Bearer ${token}` });

beforeAll(async () => {
  fixture = await setupFactorFixture('self-account-factor-qr');
  token = await getEndUserAccessToken(fixture);
});

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

describe('SelfAccount - Factor QR code', () => {
  it(jira`an unknown factor has no QR code ${'AM-7652'}`, async () => {
    const response = await performGet(accountUrl('/factors/does-not-exist/qr'), '', auth());

    expect(response.status).toBe(404);
  });

  it(jira`a factor the user has not enrolled has no QR code ${'AM-7652'}`, async () => {
    // The factor exists on the application, but this user holds nothing yet — a different branch
    // from the unknown factor above, and the reason this test runs before the enrolment.
    const response = await performGet(accountUrl(`/factors/${fixture.mockFactor.id}/qr`), '', auth());

    expect(response.status).toBe(404);
  });

  it(jira`an enrolled factor returns a QR code ${'AM-7652'}`, async () => {
    await performPost(accountUrl('/factors'), '', JSON.stringify({ factorId: fixture.mockFactor.id }), {
      'Content-Type': 'application/json',
      ...auth(),
    }).expect(200);

    const response = await performGet(accountUrl(`/factors/${fixture.mockFactor.id}/qr`), '', auth());

    expect(response.status).toBe(200);
    expect(response.body.qrCode).toContain('data:image');
  });

  it(jira`the QR code is refused without a token ${'AM-7652'}`, async () => {
    const response = await performGet(accountUrl(`/factors/${fixture.mockFactor.id}/qr`), '');

    expect(response.status).toBe(401);
  });
});
