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
import { performGet, performPatch, performPut } from '@gateway-commands/oauth-oidc-commands';
import { uniqueName } from '@utils-commands/misc';
import { SelfAccountFixture, setupFixture, getEndUserAccessToken } from './fixture/self-account-fixture';
import { setup } from '../../test-fixture';

setup(120000);

const SELF_ACCOUNT_SETTINGS = {
  selfServiceAccountManagementSettings: {
    enabled: true,
    resetPassword: {
      oldPasswordRequired: false,
      tokenAge: 0,
    },
  },
};

let fixture: SelfAccountFixture;
let userAccessToken: string;

function accountUrl(fixture: SelfAccountFixture, path: string): string {
  return `${process.env.AM_GATEWAY_URL}/${fixture.domain.hrid}/account/api${path}`;
}

beforeAll(async () => {
  fixture = await setupFixture(SELF_ACCOUNT_SETTINGS);
  userAccessToken = await getEndUserAccessToken(fixture);
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('SelfAccount - Profile', () => {
  it('should return user profile with username and displayName', async () => {
    const response = await performGet(accountUrl(fixture, '/profile'), '', {
      Authorization: `Bearer ${userAccessToken}`,
    }).expect(200);

    expect(response.body).toHaveProperty('username');
    expect(response.body).toHaveProperty('displayName');
    expect(response.body.username).toBe(fixture.user.username);
    expect(response.body.displayName).toBe('SelfAccount User');
  });

  it('should update profile fields', async () => {
    const response = await performPut(accountUrl(fixture, '/profile'), '', JSON.stringify({
      name: 'Donald Hope Courtney Jr',
      given_name: 'Donald',
      family_name: 'Courtney',
      middle_name: 'Hope',
      nickname: 'Dj',
      picture: 'https://example.com/photo.jpg',
      website: 'https://example.com',
      email: 'donald.courtney@example.com',
      gender: 'male',
      birthdate: '1992-12-13',
      zoneinfo: 'UTC-05:00',
      locale: 'en-US',
      phone_number: '+1 (352) 226-3641',
      address: {
        street_address: '123 Main St',
        locality: 'Odessa',
        region: 'Florida',
        postal_code: '33556',
        country: 'USA',
      },
    }), {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${userAccessToken}`,
    }).expect(200);

    expect(response.body).toHaveProperty('status');
    expect(response.body.status).toBe('OK');
  });

  it('should reflect updated profile fields after update', async () => {
    const response = await performGet(accountUrl(fixture, '/profile'), '', {
      Authorization: `Bearer ${userAccessToken}`,
    }).expect(200);

    expect(response.body).toHaveProperty('username');
    expect(response.body.username).toBe(fixture.user.username);
    expect(response.body.additionalInformation['middle_name']).toBe('Hope');
    expect(response.body).toHaveProperty('nickName');
    expect(response.body.nickName).toBe('Dj');
    expect(response.body).toHaveProperty('picture');
    expect(response.body.picture).toBe('https://example.com/photo.jpg');
    expect(response.body.additionalInformation.picture).toBe('https://example.com/photo.jpg');
  });

  it('should respond with redirect when requesting change password URL', async () => {
    // performGet does not follow redirects — expects the raw 302
    await performGet(accountUrl(fixture, '/changePassword'), '', {
      Authorization: `Bearer ${userAccessToken}`,
    }).expect(302);
  });
});

describe('SelfAccount - Activity', () => {
  it('should return paginated activity list', async () => {
    const response = await performGet(accountUrl(fixture, '/activity'), '', {
      Authorization: `Bearer ${userAccessToken}`,
    }).expect(200);

    expect(response.body).toHaveProperty('data');
    expect(response.body).toHaveProperty('currentPage');
    expect(response.body).toHaveProperty('totalCount');
    expect(response.body.currentPage).toBe(0);
    expect(response.body.data).not.toBeNull();
  });
});

describe('SelfAccount - Update Username', () => {
  const newUsername = uniqueName('updatedusername', true);

  it('should update username', async () => {
    const response = await performPatch(
      accountUrl(fixture, '/profile/username'),
      '',
      JSON.stringify({ username: newUsername }),
      {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${userAccessToken}`,
      },
    ).expect(200);

    expect(response.body).toHaveProperty('status');
    expect(response.body.status).toBe('OK');
  });

  it('should reflect new username in profile', async () => {
    const response = await performGet(accountUrl(fixture, '/profile'), '', {
      Authorization: `Bearer ${userAccessToken}`,
    }).expect(200);

    expect(response.body).toHaveProperty('username');
    expect(response.body.username).toBe(newUsername);
  });
});
