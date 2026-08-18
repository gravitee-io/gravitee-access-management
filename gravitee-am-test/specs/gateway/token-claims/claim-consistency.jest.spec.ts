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
 * The access token, the ID token and /oidc/userinfo are built by three separate
 * code paths that each resolve claims their own way. A client that reads the same
 * attribute from two of them must not see two different answers.
 *
 * Covers AM-6999's fourth scenario: one attribute, configured on all three
 * surfaces, must agree within a single flow.
 */
const DEPARTMENT = 'engineering';
const CLEARANCE = 'secret';
const USER_ATTRIBUTE_EXPRESSION = "{#context.attributes['user'].additionalInformation['department']}";

let fixture: TokenIdentityFixture;

beforeAll(async () => {
  fixture = await setupTokenIdentityFixture({
    userAttributes: { department: DEPARTMENT, clearance: CLEARANCE },
    extraScopes: [{ scope: 'internal', defaultScope: false }],
    tokenCustomClaims: [
      { tokenType: 'ACCESS_TOKEN', claimName: 'shared_department', claimValue: USER_ATTRIBUTE_EXPRESSION },
      { tokenType: 'ID_TOKEN', claimName: 'shared_department', claimValue: USER_ATTRIBUTE_EXPRESSION },
      {
        tokenType: 'ACCESS_TOKEN',
        claimName: 'shared_clearance',
        claimValue:
          "{#context.attributes['tokenRequest'].scopes.contains('internal') ? #context.attributes['user'].additionalInformation['clearance'] : null}",
      },
    ],
    userinfoCustomClaims: [
      { claimName: 'shared_department', claimValue: USER_ATTRIBUTE_EXPRESSION },
      {
        claimName: 'shared_clearance',
        claimValue:
          "{#context.attributes['token'].scopes.contains('internal') ? #context.attributes['user'].additionalInformation['clearance'] : null}",
      },
    ],
  });
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Claim consistency - one attribute, three surfaces', () => {
  it('reports the same custom attribute in the access token, ID token and userinfo', async () => {
    const tokens = await fixture.passwordGrant('openid email profile');
    const accessToken = decodeToken(tokens.access_token).payload;
    const idToken = decodeToken(tokens.id_token).payload;
    const profile = await fixture.userinfo(tokens.access_token);

    expect(accessToken.shared_department).toEqual(DEPARTMENT);
    expect(idToken.shared_department).toEqual(DEPARTMENT);
    expect(profile.shared_department).toEqual(DEPARTMENT);
  });

  it('agrees on the subject across all three surfaces in the same flow', async () => {
    const tokens = await fixture.passwordGrant('openid email profile');
    const accessToken = decodeToken(tokens.access_token).payload;
    const idToken = decodeToken(tokens.id_token).payload;
    const profile = await fixture.userinfo(tokens.access_token);

    expect(idToken.sub).toEqual(accessToken.sub);
    expect(profile.sub).toEqual(accessToken.sub);
  });

  it('agrees on the standard email claim between the ID token and userinfo', async () => {
    const tokens = await fixture.passwordGrant('openid email profile');
    const idToken = decodeToken(tokens.id_token).payload;
    const profile = await fixture.userinfo(tokens.access_token);

    expect(idToken.email).toEqual(fixture.user.email);
    expect(profile.email).toEqual(idToken.email);
  });
});

describe('Claim consistency - a scope-gated attribute gates the same way everywhere', () => {
  it('releases the attribute in both the access token and userinfo when scoped', async () => {
    const tokens = await fixture.passwordGrant('openid email profile internal');
    const accessToken = decodeToken(tokens.access_token).payload;
    const profile = await fixture.userinfo(tokens.access_token);

    expect(accessToken.shared_clearance).toEqual(CLEARANCE);
    expect(profile.shared_clearance).toEqual(CLEARANCE);
  });

  it('withholds the attribute from both the access token and userinfo when unscoped', async () => {
    const tokens = await fixture.passwordGrant('openid email profile');
    const accessToken = decodeToken(tokens.access_token).payload;
    const profile = await fixture.userinfo(tokens.access_token);

    // Anchor: the unconditional claim still resolved on both surfaces.
    expect(accessToken.shared_department).toEqual(DEPARTMENT);
    expect(profile.shared_department).toEqual(DEPARTMENT);

    expect(accessToken).not.toHaveProperty('shared_clearance');
    expect(profile).not.toHaveProperty('shared_clearance');
  });
});

describe('Claim consistency - the browser flow matches the direct grant', () => {
  it('produces the same custom attribute whichever grant issued the token', async () => {
    const viaPassword = await fixture.passwordGrant('openid email profile');
    const viaAuthorizationCode = await fixture.authorizationCodeFlow();

    const fromPassword = decodeToken(viaPassword.access_token).payload.shared_department;
    const fromCode = decodeToken(viaAuthorizationCode.access_token).payload.shared_department;

    expect(fromPassword).toEqual(DEPARTMENT);
    expect(fromCode).toEqual(fromPassword);
  });
});
