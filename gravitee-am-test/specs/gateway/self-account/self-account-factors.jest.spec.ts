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
import { performDelete, performGet, performPost } from '@gateway-commands/oauth-oidc-commands';
import { SelfAccountFactorFixture, setupFactorFixture, getEndUserAccessToken } from './fixture/self-account-fixture';
import { setup } from '../../test-fixture';

setup(180000);

let fixture: SelfAccountFactorFixture;
let userAccessToken: string;

function accountUrl(fixture: SelfAccountFactorFixture, path: string): string {
  return `${process.env.AM_GATEWAY_URL}/${fixture.domain.hrid}/account/api${path}`;
}

beforeAll(async () => {
  fixture = await setupFactorFixture();
  userAccessToken = await getEndUserAccessToken(fixture);
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('SelfAccount - Factors - Invalid Input', () => {
  it('should reject enroll request with malformed JSON', async () => {
    const response = await performPost(accountUrl(fixture, '/factors'), '', null, {
      Authorization: `Bearer ${userAccessToken}`,
    }).expect(400);

    expect(response.body.http_status).toBe(400);
    expect(response.body.message).toBe('Unable to parse body message');
  });

  it('should require factorId on enroll request', async () => {
    const response = await performPost(accountUrl(fixture, '/factors'), '', '{}', {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${userAccessToken}`,
    }).expect(400);

    expect(response.body.http_status).toBe(400);
    expect(response.body.message).toBe('Field [factorId] is required');
  });

  it('should return 404 when enrolling an unknown factor', async () => {
    const response = await performPost(
      accountUrl(fixture, '/factors'),
      '',
      JSON.stringify({ factorId: 'unknown' }),
      {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${userAccessToken}`,
      },
    ).expect(404);

    expect(response.body.http_status).toBe(404);
    expect(response.body.message).toBe('Factor [unknown] can not be found.');
  });

  it('should reject verify request with malformed JSON', async () => {
    const response = await performPost(accountUrl(fixture, '/factors/test/verify'), '', null, {
      Authorization: `Bearer ${userAccessToken}`,
    }).expect(400);

    expect(response.body.http_status).toBe(400);
    expect(response.body.message).toBe('Unable to parse body message');
  });

  it('should require code on verify request', async () => {
    const response = await performPost(accountUrl(fixture, '/factors/test/verify'), '', '{}', {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${userAccessToken}`,
    }).expect(400);

    expect(response.body.http_status).toBe(400);
    expect(response.body.message).toBe('Field [code] is required');
  });

  it('should return 404 when verifying an unknown factor', async () => {
    const response = await performPost(
      accountUrl(fixture, '/factors/unknown/verify'),
      '',
      JSON.stringify({ code: '123456' }),
      {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${userAccessToken}`,
      },
    ).expect(404);

    expect(response.body.http_status).toBe(404);
    expect(response.body.message).toBe('Factor [unknown] can not be found.');
  });
});

describe('SelfAccount - Factors - Nominal', () => {
  it('should list available factors in catalog', async () => {
    const response = await performGet(accountUrl(fixture, '/factors/catalog'), '', {
      Authorization: `Bearer ${userAccessToken}`,
    }).expect(200);

    expect(response.body).toHaveLength(1);
  });

  it('should enroll a factor', async () => {
    await performPost(
      accountUrl(fixture, '/factors'),
      '',
      JSON.stringify({ factorId: fixture.mockFactor.id }),
      {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${userAccessToken}`,
      },
    ).expect(200);
  });

  it('should list enrolled factors', async () => {
    const response = await performGet(accountUrl(fixture, '/factors'), '', {
      Authorization: `Bearer ${userAccessToken}`,
    }).expect(200);

    expect(response.body).toHaveLength(1);
  });

  it('should remove an enrolled factor', async () => {
    await performDelete(accountUrl(fixture, `/factors/${fixture.mockFactor.id}`), '', {
      Authorization: `Bearer ${userAccessToken}`,
    }).expect(204);
  });

  it('should return empty list after factor removal', async () => {
    const response = await performGet(accountUrl(fixture, '/factors'), '', {
      Authorization: `Bearer ${userAccessToken}`,
    }).expect(200);

    expect(response.body).toHaveLength(0);
  });
});
