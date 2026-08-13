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
import { setup } from '../../test-fixture';
import { decodeToken, setupTokenIdentityFixture, TokenIdentityFixture } from './fixtures/token-identity-fixture';

setup(200000);

/**
 * OIDC Core 5.4 - a scope is a request for a specific set of claims. What a client
 * asks for decides what it is told about the user, so this is the data-minimisation
 * boundary: a client granted `email` must not also receive a home address.
 *
 * The projection algebra itself is unit-tested in UserInfoEndpointHandlerTest; these
 * tests confirm the wiring holds end to end on a real gateway.
 */
const PROFILE_ATTRIBUTES = {
  address: { formatted: '1 Test Street, London', country: 'UK' },
  phone_number: '+44 20 7946 0000',
  phone_number_verified: true,
  website: 'https://example.com/me',
  nickname: 'testy',
};

let fixture: TokenIdentityFixture;

beforeAll(async () => {
  fixture = await setupTokenIdentityFixture({ userAttributes: PROFILE_ATTRIBUTES });
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Scope to claim projection - a client is told only what it asked for', () => {
  it('discloses nothing beyond the subject when only openid was requested', async () => {
    const tokens = await fixture.passwordGrant('openid');
    const profile = await fixture.userinfo(tokens.access_token);

    // Anchor: the call succeeded and identified the user.
    expect(profile.sub).toEqual(expect.any(String));

    for (const claim of ['email', 'address', 'phone_number', 'given_name', 'nickname', 'website']) {
      expect(profile).not.toHaveProperty(claim);
    }
  });

  it('discloses the profile set, and nothing from email, address or phone', async () => {
    const tokens = await fixture.passwordGrant('openid profile');
    const profile = await fixture.userinfo(tokens.access_token);

    expect(profile.given_name).toEqual(fixture.user.firstName);
    expect(profile.family_name).toEqual(fixture.user.lastName);
    expect(profile.nickname).toEqual(PROFILE_ATTRIBUTES.nickname);
    expect(profile.website).toEqual(PROFILE_ATTRIBUTES.website);

    expect(profile).not.toHaveProperty('email');
    expect(profile).not.toHaveProperty('address');
    expect(profile).not.toHaveProperty('phone_number');
  });

  it('discloses the email address only when the email scope was requested', async () => {
    const tokens = await fixture.passwordGrant('openid email');
    const profile = await fixture.userinfo(tokens.access_token);

    expect(profile.email).toEqual(fixture.user.email);
    expect(profile).not.toHaveProperty('given_name');
    expect(profile).not.toHaveProperty('address');
  });

  it('discloses the home address only when the address scope was requested', async () => {
    const tokens = await fixture.passwordGrant('openid address');
    const profile = await fixture.userinfo(tokens.access_token);

    expect(profile.address).toEqual(PROFILE_ATTRIBUTES.address);
    expect(profile).not.toHaveProperty('email');
    expect(profile).not.toHaveProperty('phone_number');
  });

  it('discloses the phone number and its verification flag together', async () => {
    const tokens = await fixture.passwordGrant('openid phone');
    const profile = await fixture.userinfo(tokens.access_token);

    expect(profile.phone_number).toEqual(PROFILE_ATTRIBUTES.phone_number);
    expect(profile.phone_number_verified).toEqual(PROFILE_ATTRIBUTES.phone_number_verified);
    expect(profile).not.toHaveProperty('address');
  });

  it('combines the sets when several scopes are requested together', async () => {
    const tokens = await fixture.passwordGrant('openid profile email address phone');
    const profile = await fixture.userinfo(tokens.access_token);

    expect(profile.email).toEqual(fixture.user.email);
    expect(profile.address).toEqual(PROFILE_ATTRIBUTES.address);
    expect(profile.phone_number).toEqual(PROFILE_ATTRIBUTES.phone_number);
    expect(profile.given_name).toEqual(fixture.user.firstName);
  });
});

describe('Scope to claim projection - the ID token projects the same way', () => {
  it('carries the email claim only when the email scope was requested', async () => {
    const withEmail = decodeToken((await fixture.passwordGrant('openid email')).id_token).payload;
    const withoutEmail = decodeToken((await fixture.passwordGrant('openid')).id_token).payload;

    expect(withEmail.email).toEqual(fixture.user.email);
    expect(withoutEmail).not.toHaveProperty('email');
  });

  it('carries the address claim only when the address scope was requested', async () => {
    const withAddress = decodeToken((await fixture.passwordGrant('openid address')).id_token).payload;
    const withoutAddress = decodeToken((await fixture.passwordGrant('openid profile')).id_token).payload;

    expect(withAddress.address).toEqual(PROFILE_ATTRIBUTES.address);
    expect(withoutAddress).not.toHaveProperty('address');
  });
});

describe('Scope to claim projection - full_profile', () => {
  it('discloses every attribute at once', async () => {
    const tokens = await fixture.passwordGrant('openid full_profile');
    const profile = await fixture.userinfo(tokens.access_token);

    expect(profile.email).toEqual(fixture.user.email);
    expect(profile.address).toEqual(PROFILE_ATTRIBUTES.address);
    expect(profile.phone_number).toEqual(PROFILE_ATTRIBUTES.phone_number);
    expect(profile.website).toEqual(PROFILE_ATTRIBUTES.website);
    expect(profile.nickname).toEqual(PROFILE_ATTRIBUTES.nickname);
  });

  it('drops updated_at, which the narrower profile scope keeps', async () => {
    // The two paths differ: the scope-driven copy passes updated_at through, while
    // full_profile runs the ID_TOKEN_EXCLUDED_CLAIMS filter and removes it.
    const viaProfile = decodeToken((await fixture.passwordGrant('openid profile')).id_token).payload;
    const viaFullProfile = decodeToken((await fixture.passwordGrant('openid full_profile')).id_token).payload;

    expect(viaProfile.updated_at).toEqual(expect.any(Number));
    expect(viaFullProfile).not.toHaveProperty('updated_at');
  });
});
