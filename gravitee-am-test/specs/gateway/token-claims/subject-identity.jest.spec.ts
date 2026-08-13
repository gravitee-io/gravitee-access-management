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
import { createHash } from 'crypto';
import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { setup } from '../../test-fixture';
import { decodeToken, setupTokenIdentityFixture, SPOOFED_PROFILE_CLAIMS, TokenIdentityFixture } from './fixtures/token-identity-fixture';

setup(200000);

/**
 * Relying parties store `sub` as the primary key for a user. If it ever changes
 * value, every RP loses its mapping at once, so these tests pin the subject
 * across grants, surfaces and sessions.
 *
 * On a V2 domain (the default) `SubjectManagerV2` derives:
 *   gis = "<source>:<externalId>"
 *   sub = UUID.nameUUIDFromBytes(gis)   // RFC 4122 v3, MD5, no namespace
 */
const UUID_V3 = /^[0-9a-f]{8}-[0-9a-f]{4}-3[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

/** Mirrors java.util.UUID.nameUUIDFromBytes - MD5 with the v3/IETF bits set. */
function nameUuidFromBytes(value: string): string {
  const md5 = createHash('md5').update(value, 'utf8').digest();
  md5[6] = (md5[6] & 0x0f) | 0x30;
  md5[8] = (md5[8] & 0x3f) | 0x80;
  const hex = md5.toString('hex');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

let fixture: TokenIdentityFixture;

beforeAll(async () => {
  fixture = await setupTokenIdentityFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Subject identity - one user is one subject', () => {
  it('gives a relying party the same subject in the access token, ID token and userinfo', async () => {
    const tokens = await fixture.passwordGrant('openid email profile');

    const accessTokenSub = decodeToken(tokens.access_token).payload.sub;
    const idTokenSub = decodeToken(tokens.id_token).payload.sub;
    const userinfoSub = (await fixture.userinfo(tokens.access_token)).sub;

    expect(accessTokenSub).toMatch(UUID_V3);
    expect(idTokenSub).toEqual(accessTokenSub);
    expect(userinfoSub).toEqual(accessTokenSub);
  });

  it('keeps the subject stable when a long-lived session refreshes its token', async () => {
    const initial = await fixture.passwordGrant('openid email profile');
    const initialSub = decodeToken(initial.access_token).payload.sub;

    const refreshed = await fixture.refreshGrant(initial.refresh_token);
    const refreshedToken = decodeToken(refreshed.access_token);

    expect(refreshedToken.payload.sub).toEqual(initialSub);
    // A genuinely new token, not the original replayed back.
    expect(refreshed.access_token).not.toEqual(initial.access_token);
    expect(refreshedToken.payload.jti).not.toEqual(decodeToken(initial.access_token).payload.jti);
  });

  it('gives the same user the same subject whichever grant they sign in with', async () => {
    const viaPassword = await fixture.passwordGrant('openid email profile');
    const viaAuthorizationCode = await fixture.authorizationCodeFlow();

    expect(decodeToken(viaAuthorizationCode.access_token).payload.sub).toEqual(decodeToken(viaPassword.access_token).payload.sub);
    expect(decodeToken(viaAuthorizationCode.id_token).payload.sub).toEqual(decodeToken(viaPassword.id_token).payload.sub);
  });

  it('gives two different users two different subjects', async () => {
    const first = await fixture.passwordGrantFor(fixture.user);
    const second = await fixture.passwordGrantFor(fixture.otherUser);

    const firstToken = decodeToken(first.access_token).payload;
    const secondToken = decodeToken(second.access_token).payload;

    expect(firstToken.sub).not.toEqual(secondToken.sub);
    expect(firstToken.gis).not.toEqual(secondToken.gis);
  });
});

describe('Subject identity - the subject is derived, not random', () => {
  it('derives the subject deterministically from the identity provider and external id', async () => {
    const tokens = await fixture.passwordGrant('openid email profile');
    const { sub, gis } = decodeToken(tokens.access_token).payload;

    // gis pins the user to a specific IdP + external identity...
    expect(gis).toEqual(`${fixture.user.source}:${fixture.user.externalId}`);
    // ...and sub is a pure function of it, so a rebuilt token store still resolves.
    expect(sub).toEqual(nameUuidFromBytes(gis));
  });

  it('issues the same subject again after a completely separate sign-in', async () => {
    const firstSession = await fixture.authorizationCodeFlow();
    const secondSession = await fixture.authorizationCodeFlow();

    const firstSub = decodeToken(firstSession.access_token).payload.sub;
    const secondSub = decodeToken(secondSession.access_token).payload.sub;

    expect(secondSub).toEqual(firstSub);
  });
});

/**
 * An upstream identity provider can return attributes called `sub`, `iss` or `aud`.
 * Those land in the stored user profile, and must never displace the values AM
 * itself asserts - otherwise a relying party can be pointed at another identity.
 * Regression guard for https://github.com/gravitee-io/issues/issues/7118
 */
describe('Subject identity - a profile attribute cannot impersonate the subject', () => {
  it('ignores a sub supplied by the identity provider when issuing a token', async () => {
    const tokens = await fixture.passwordGrantFor(fixture.spoofedUser, 'openid email profile');
    const accessToken = decodeToken(tokens.access_token).payload;
    const idToken = decodeToken(tokens.id_token).payload;

    expect(accessToken.sub).not.toEqual(SPOOFED_PROFILE_CLAIMS.sub);
    expect(idToken.sub).not.toEqual(SPOOFED_PROFILE_CLAIMS.sub);
    // AM's own derivation still holds for this user.
    expect(accessToken.sub).toEqual(nameUuidFromBytes(accessToken.gis));
  });

  it('ignores a sub supplied by the identity provider at the userinfo endpoint', async () => {
    const tokens = await fixture.passwordGrantFor(fixture.spoofedUser, 'openid email profile');
    const profile = await fixture.userinfo(tokens.access_token);

    expect(profile.sub).not.toEqual(SPOOFED_PROFILE_CLAIMS.sub);
    expect(profile.sub).toEqual(decodeToken(tokens.access_token).payload.sub);
  });

  it('keeps its own issuer and audience when the profile carries conflicting ones', async () => {
    // full_profile copies the entire stored profile into the token, so this is the
    // widest path a planted reserved claim has to reach the wire.
    const tokens = await fixture.passwordGrantFor(fixture.spoofedUser, 'openid full_profile');
    const idToken = decodeToken(tokens.id_token).payload;

    // Positive anchor first: prove the profile really was copied into the token,
    // otherwise the assertions below would pass on an empty payload.
    expect(idToken.profile_copy_marker).toEqual(SPOOFED_PROFILE_CLAIMS.profile_copy_marker);

    expect(idToken.iss).toEqual(fixture.oidc.issuer);
    expect(idToken.iss).not.toEqual(SPOOFED_PROFILE_CLAIMS.iss);
    expect(idToken.aud).toEqual(fixture.clientId);
    expect(idToken.aud).not.toEqual(SPOOFED_PROFILE_CLAIMS.aud);
    expect(idToken.sub).not.toEqual(SPOOFED_PROFILE_CLAIMS.sub);
  });
});
