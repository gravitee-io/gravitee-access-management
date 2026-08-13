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
 * An administrator writing expression language in the console will eventually
 * mistype one, or reference an attribute a particular user does not have. The
 * guarantee these tests protect is that sign-in keeps working: the bad claim is
 * simply missing, rather than the token endpoint failing and locking everyone out.
 *
 * `ExecutionContextTokenEnhancer.evaluateAndUpdate` swallows evaluation errors to
 * a debug log, which is what makes that guarantee hold.
 */
const GOOD_VALUE = 'engineering';
const MALFORMED_EXPRESSION = '{#context.attributes[';
const MISSING_ATTRIBUTE = "{#context.attributes['user'].additionalInformation['does_not_exist']}";

let fixture: TokenIdentityFixture;

beforeAll(async () => {
  fixture = await setupTokenIdentityFixture({
    userAttributes: { department: GOOD_VALUE },
    tokenCustomClaims: [
      { tokenType: 'ACCESS_TOKEN', claimName: 'at_good', claimValue: "{#context.attributes['user'].additionalInformation['department']}" },
      { tokenType: 'ACCESS_TOKEN', claimName: 'at_malformed', claimValue: MALFORMED_EXPRESSION },
      { tokenType: 'ACCESS_TOKEN', claimName: 'at_missing', claimValue: MISSING_ATTRIBUTE },
      { tokenType: 'ID_TOKEN', claimName: 'idt_good', claimValue: "{#context.attributes['user'].additionalInformation['department']}" },
      { tokenType: 'ID_TOKEN', claimName: 'idt_malformed', claimValue: MALFORMED_EXPRESSION },
    ],
    userinfoCustomClaims: [
      { claimName: 'ui_good', claimValue: "{#context.attributes['user'].additionalInformation['department']}" },
      { claimName: 'ui_malformed', claimValue: MALFORMED_EXPRESSION },
      { claimName: 'ui_missing', claimValue: MISSING_ATTRIBUTE },
    ],
  });
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Custom claim resilience - a mistyped expression does not break sign-in', () => {
  it('still issues a usable token set when a token claim cannot be evaluated', async () => {
    const tokens = await fixture.passwordGrant('openid email profile');

    expect(tokens.access_token).toEqual(expect.any(String));
    expect(tokens.id_token).toEqual(expect.any(String));
    expect(tokens.token_type.toLowerCase()).toEqual('bearer');
  });

  it('omits the broken claim while keeping the working ones in the access token', async () => {
    const tokens = await fixture.passwordGrant('openid email profile');
    const accessToken = decodeToken(tokens.access_token).payload;

    expect(accessToken.at_good).toEqual(GOOD_VALUE);
    expect(accessToken).not.toHaveProperty('at_malformed');
  });

  it('omits the broken claim while keeping the working ones in the ID token', async () => {
    const tokens = await fixture.passwordGrant('openid email profile');
    const idToken = decodeToken(tokens.id_token).payload;

    expect(idToken.idt_good).toEqual(GOOD_VALUE);
    expect(idToken).not.toHaveProperty('idt_malformed');
  });

  it('still answers userinfo with the working claims when one is broken', async () => {
    const tokens = await fixture.passwordGrant('openid email profile');
    const profile = await fixture.userinfo(tokens.access_token);

    expect(profile.ui_good).toEqual(GOOD_VALUE);
    expect(profile).not.toHaveProperty('ui_malformed');
  });
});

describe('Custom claim resilience - an attribute the user does not have', () => {
  it('omits the claim rather than emitting a null for it', async () => {
    const tokens = await fixture.passwordGrant('openid email profile');
    const accessToken = decodeToken(tokens.access_token).payload;

    expect(accessToken.at_good).toEqual(GOOD_VALUE);
    expect(accessToken).not.toHaveProperty('at_missing');
  });

  it('answers userinfo normally, omitting only the unresolvable claim', async () => {
    const tokens = await fixture.passwordGrant('openid email profile');
    const profile = await fixture.userinfo(tokens.access_token);

    expect(profile.sub).toEqual(expect.any(String));
    expect(profile.ui_good).toEqual(GOOD_VALUE);
    expect(profile).not.toHaveProperty('ui_missing');
  });

  it('affects only the user missing the attribute, not everyone else', async () => {
    // otherUser has no `department` at all, so even the "good" claim cannot resolve
    // for them - and their sign-in must still succeed.
    const tokens = await fixture.passwordGrantFor(fixture.otherUser, 'openid email profile');
    const accessToken = decodeToken(tokens.access_token).payload;

    expect(accessToken.sub).toEqual(expect.any(String));
    expect(accessToken).not.toHaveProperty('at_good');
  });
});

describe('Custom claim resilience - the failure survives a refresh', () => {
  it('keeps issuing usable tokens across a refresh with a broken claim configured', async () => {
    const initial = await fixture.passwordGrant('openid email profile');
    const refreshed = await fixture.refreshGrant(initial.refresh_token);
    const accessToken = decodeToken(refreshed.access_token).payload;

    expect(accessToken.at_good).toEqual(GOOD_VALUE);
    expect(accessToken).not.toHaveProperty('at_malformed');
  });
});
