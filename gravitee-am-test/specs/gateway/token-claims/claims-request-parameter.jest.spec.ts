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
import { performGet } from '@gateway-commands/oauth-oidc-commands';
import { setup } from '../../test-fixture';
import { decodeToken, setupTokenIdentityFixture, TokenIdentityFixture } from './fixtures/token-identity-fixture';

setup(200000);

/**
 * OIDC Core 5.5 - a client may name the individual claims it wants, per delivery
 * location, instead of asking for a whole scope. A client that needs a nickname
 * for a greeting should not have to request `profile` and receive the user's
 * website, date of birth and picture along with it.
 *
 * The two sections are independent: `id_token` claims go in the ID token and
 * `userinfo` claims are returned by /oidc/userinfo.
 */
const PROFILE_ATTRIBUTES = {
  phone_number: '+44 20 7946 0000',
  website: 'https://example.com/me',
  nickname: 'testy',
};

/** Builds the `claims` authorize parameter, URL-encoded. */
function claimsParameter(request: Record<string, Record<string, null>>): string {
  return `claims=${encodeURIComponent(JSON.stringify(request))}`;
}

let fixture: TokenIdentityFixture;

beforeAll(async () => {
  fixture = await setupTokenIdentityFixture({ userAttributes: PROFILE_ATTRIBUTES });
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

/**
 * These three regressed under AM-7525: the claims parameter was re-encoded with the
 * process-global Vert.x mapper, which a `/_node*` call reconfigures with NON_NULL
 * inclusion. A voluntary claim is expressed as JSON null, so every requested claim
 * name was stripped before the parameter was stored. Fixed by giving the handler its
 * own mapper; these guard against it returning.
 */
describe('Claims request parameter - naming claims instead of a scope', () => {
  it('puts the named claims in the ID token without granting the profile scope', async () => {
    const tokens = await fixture.authorizationCodeFlow(`scope=openid&${claimsParameter({ id_token: { nickname: null, website: null } })}`);
    const idToken = decodeToken(tokens.id_token).payload;

    // Only openid was granted, so nothing arrived by way of a scope.
    expect(tokens.scope).toEqual('openid');
    expect(idToken.nickname).toEqual(PROFILE_ATTRIBUTES.nickname);
    expect(idToken.website).toEqual(PROFILE_ATTRIBUTES.website);
  });

  it('returns the named claims from userinfo without granting the phone scope', async () => {
    const tokens = await fixture.authorizationCodeFlow(`scope=openid&${claimsParameter({ userinfo: { phone_number: null } })}`);
    const profile = await fixture.userinfo(tokens.access_token);

    expect(tokens.scope).toEqual('openid');
    expect(profile.phone_number).toEqual(PROFILE_ATTRIBUTES.phone_number);
  });

  it('keeps the two sections apart, so each surface gets only its own claims', async () => {
    const tokens = await fixture.authorizationCodeFlow(
      `scope=openid&${claimsParameter({ id_token: { nickname: null }, userinfo: { phone_number: null } })}`,
    );
    const idToken = decodeToken(tokens.id_token).payload;
    const profile = await fixture.userinfo(tokens.access_token);

    expect(idToken.nickname).toEqual(PROFILE_ATTRIBUTES.nickname);
    expect(idToken).not.toHaveProperty('phone_number');

    expect(profile.phone_number).toEqual(PROFILE_ATTRIBUTES.phone_number);
    expect(profile).not.toHaveProperty('nickname');
  });
});

describe('Claims request parameter - it does not become a way round the rules', () => {
  it('still identifies the user with the subject AM derived', async () => {
    const tokens = await fixture.authorizationCodeFlow(
      `scope=openid&${claimsParameter({ id_token: { sub: null }, userinfo: { sub: null } })}`,
    );
    const idToken = decodeToken(tokens.id_token).payload;
    const profile = await fixture.userinfo(tokens.access_token);

    expect(idToken.sub).toEqual(decodeToken(tokens.access_token).payload.sub);
    expect(profile.sub).toEqual(idToken.sub);
  });

  it('still issues a valid token when the only claim asked for cannot be resolved', async () => {
    const tokens = await fixture.authorizationCodeFlow(`scope=openid&${claimsParameter({ id_token: { does_not_exist: null } })}`);
    const idToken = decodeToken(tokens.id_token).payload;

    expect(idToken.sub).toEqual(expect.any(String));
    expect(idToken.iss).toEqual(fixture.oidc.issuer);
    expect(idToken).not.toHaveProperty('does_not_exist');
  });

  /**
   * Only asserts that a token is still issued. Whether a resolvable claim
   * requested alongside an unresolvable one survives depends on HashMap
   * iteration order, so it is not something to assert on.
   */
  it('issues a usable token when an unresolvable claim is mixed in with a resolvable one', async () => {
    const tokens = await fixture.authorizationCodeFlow(
      `scope=openid&${claimsParameter({ id_token: { nickname: null, does_not_exist: null } })}`,
    );
    const idToken = decodeToken(tokens.id_token).payload;

    expect(idToken.sub).toEqual(expect.any(String));
    expect(idToken.iss).toEqual(fixture.oidc.issuer);
    expect(idToken).not.toHaveProperty('does_not_exist');
  });

  it('rejects the authorization request when the parameter is not well-formed JSON', async () => {
    // OIDC Core 5.5 requires an error when `claims` is not a valid JSON object.
    const params =
      `?response_type=code&client_id=${fixture.clientId}` +
      `&redirect_uri=${encodeURIComponent(fixture.app.settings.oauth.redirectUris[0])}` +
      '&scope=openid&claims=not-json-at-all';

    const response = await performGet(fixture.oidc.authorization_endpoint, params).expect(302);

    expect(response.headers['location']).toContain('error=invalid_request');
    expect(response.headers['location']).toContain('claims');
  });
});
