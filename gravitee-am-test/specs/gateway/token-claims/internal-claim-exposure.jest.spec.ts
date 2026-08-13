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
import { decodeToken, LEAKY_PROFILE_CLAIMS, setupTokenIdentityFixture, TokenIdentityFixture } from './fixtures/token-identity-fixture';

setup(200000);

/**
 * A user profile accumulates values that belong to AM or to an upstream provider:
 * the OP's own tokens, and timing claims AM regenerates per token. Forwarding any
 * of them to a relying party would hand out credentials for another system or let
 * a stored value dictate a token's lifetime.
 *
 * `ConstantKeys.ID_TOKEN_EXCLUDED_CLAIMS` is the filter under test. Every value in
 * LEAKY_PROFILE_CLAIMS is planted on `leakyUser` before the domain starts.
 */
const UPSTREAM_PROVIDER_TOKENS = ['op_id_token', 'op_access_token'] as const;
/** Timing claims AM sets itself, so the token carries AM's value rather than the planted one. */
const AM_GENERATED_TIMING_CLAIMS = ['iat', 'exp', 'auth_time'] as const;
/**
 * Timing claims the `full_profile` path strips via ID_TOKEN_EXCLUDED_CLAIMS.
 * Not "never in an ID token" - `updated_at` is present under the narrower `profile`
 * scope, which the seconds-vs-milliseconds test lower down relies on.
 */
const TIMING_CLAIMS_STRIPPED_BY_FULL_PROFILE = ['nbf', 'updated_at'] as const;

/** Any timestamp AM generates during the run is far larger than the planted constants. */
const PLAUSIBLE_RECENT_EPOCH = 1_700_000_000;

let fixture: TokenIdentityFixture;

beforeAll(async () => {
  fixture = await setupTokenIdentityFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Internal claim exposure - upstream provider tokens stay inside AM', () => {
  it('never forwards the upstream provider tokens under narrow scopes', async () => {
    const tokens = await fixture.passwordGrantFor(fixture.leakyUser, 'openid email profile');
    const idToken = decodeToken(tokens.id_token).payload;
    const accessToken = decodeToken(tokens.access_token).payload;
    const profile = await fixture.userinfo(tokens.access_token);

    for (const claim of UPSTREAM_PROVIDER_TOKENS) {
      expect(idToken).not.toHaveProperty(claim);
      expect(accessToken).not.toHaveProperty(claim);
      expect(profile).not.toHaveProperty(claim);
    }
  });

  it('never forwards the upstream provider tokens even when the whole profile is requested', async () => {
    const tokens = await fixture.passwordGrantFor(fixture.leakyUser, 'openid full_profile');
    const idToken = decodeToken(tokens.id_token).payload;
    const profile = await fixture.userinfo(tokens.access_token);

    // Positive anchor: the profile really was copied wholesale, so the absences below mean something.
    expect(idToken.leak_probe_marker).toEqual(LEAKY_PROFILE_CLAIMS.leak_probe_marker);
    expect(profile.leak_probe_marker).toEqual(LEAKY_PROFILE_CLAIMS.leak_probe_marker);

    for (const claim of UPSTREAM_PROVIDER_TOKENS) {
      expect(idToken).not.toHaveProperty(claim);
      expect(profile).not.toHaveProperty(claim);
    }
  });
});

describe('Internal claim exposure - a stored profile cannot dictate token timing', () => {
  it('uses its own timing claims rather than the ones stored on the profile', async () => {
    const tokens = await fixture.passwordGrantFor(fixture.leakyUser, 'openid full_profile');
    const idToken = decodeToken(tokens.id_token).payload;

    expect(idToken.leak_probe_marker).toEqual(LEAKY_PROFILE_CLAIMS.leak_probe_marker);

    for (const claim of AM_GENERATED_TIMING_CLAIMS) {
      expect(idToken[claim]).not.toEqual(LEAKY_PROFILE_CLAIMS[claim]);
      expect(idToken[claim]).toBeGreaterThan(PLAUSIBLE_RECENT_EPOCH);
    }
  });

  it('strips the excluded timing claims when the whole profile is copied', async () => {
    const tokens = await fixture.passwordGrantFor(fixture.leakyUser, 'openid full_profile');
    const idToken = decodeToken(tokens.id_token).payload;

    expect(idToken.leak_probe_marker).toEqual(LEAKY_PROFILE_CLAIMS.leak_probe_marker);

    for (const claim of TIMING_CLAIMS_STRIPPED_BY_FULL_PROFILE) {
      expect(idToken).not.toHaveProperty(claim);
    }
  });

  it('would not let a stored exp extend the life of an issued token', async () => {
    const tokens = await fixture.passwordGrantFor(fixture.leakyUser, 'openid full_profile');
    const idToken = decodeToken(tokens.id_token).payload;

    // The planted exp is in 1977; a token honouring it would already be expired.
    expect(idToken.exp).not.toEqual(LEAKY_PROFILE_CLAIMS.exp);
    expect(idToken.exp).toBeGreaterThan(Math.floor(Date.now() / 1000));
    expect(idToken.exp).toBeGreaterThan(idToken.iat);
  });
});

describe('Internal claim exposure - what a relying party can see today', () => {
  it('exposes the internal subject anchor gis on every surface', async () => {
    const tokens = await fixture.passwordGrant('openid email profile');
    const accessToken = decodeToken(tokens.access_token).payload;
    const idToken = decodeToken(tokens.id_token).payload;
    const profile = await fixture.userinfo(tokens.access_token);

    const expected = `${fixture.user.source}:${fixture.user.externalId}`;
    expect(accessToken.gis).toEqual(expected);
    expect(idToken.gis).toEqual(expected);
    expect(profile.gis).toEqual(expected);
  });

  it('carries the internal domain id on the access token but not the ID token', async () => {
    const tokens = await fixture.passwordGrant('openid email profile');

    expect(decodeToken(tokens.access_token).payload.domain).toEqual(fixture.domain.id);
    expect(decodeToken(tokens.id_token).payload).not.toHaveProperty('domain');
  });

  it('reports updated_at in seconds in the ID token and milliseconds at userinfo', async () => {
    const tokens = await fixture.passwordGrant('openid email profile');
    const idTokenUpdatedAt = decodeToken(tokens.id_token).payload.updated_at;
    const userinfoUpdatedAt = (await fixture.userinfo(tokens.access_token)).updated_at;

    // The same instant, expressed in different units on the two surfaces.
    expect(userinfoUpdatedAt).toEqual(expect.any(Number));
    expect(Math.floor(userinfoUpdatedAt / 1000)).toEqual(idTokenUpdatedAt);
  });
});
