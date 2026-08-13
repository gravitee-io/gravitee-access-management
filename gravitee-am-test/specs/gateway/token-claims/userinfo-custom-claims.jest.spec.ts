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
import { setupTokenIdentityFixture, TokenIdentityFixture } from './fixtures/token-identity-fixture';

setup(200000);

/**
 * Userinfo claims are configured per application and evaluated when /oidc/userinfo
 * is called, using the access token's own scopes. Covers the manual cases
 * AM-6998, AM-6999, AM-7000 and AM-7001.
 *
 * `internal` is a non-default scope, so a client only receives the clearance claim
 * when it explicitly asks for it - the pattern customers use to keep sensitive
 * attributes out of tokens issued for ordinary sign-in.
 */
const DEPARTMENT = 'engineering';
const CLEARANCE = 'secret';

let fixture: TokenIdentityFixture;

beforeAll(async () => {
  fixture = await setupTokenIdentityFixture({
    userAttributes: { department: DEPARTMENT, clearance: CLEARANCE },
    extraScopes: [{ scope: 'internal', defaultScope: false }],
    userinfoCustomClaims: [
      { claimName: 'ui_static', claimValue: 'static-value' },
      { claimName: 'ui_department', claimValue: "{#context.attributes['user'].additionalInformation['department']}" },
      { claimName: 'ui_client', claimValue: "{#context.attributes['client']['name']}" },
      {
        claimName: 'ui_clearance',
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

describe('Userinfo custom claims - what the client receives', () => {
  it('returns a static claim alongside the standard profile claims', async () => {
    const tokens = await fixture.passwordGrant('openid email profile');
    const profile = await fixture.userinfo(tokens.access_token);

    expect(profile.ui_static).toEqual('static-value');
    // The standard claims are still there - a custom claim must not displace them.
    expect(profile.email).toEqual(fixture.user.email);
    expect(profile.preferred_username).toEqual(fixture.user.username);
    expect(profile.sub).toEqual(expect.any(String));
  });

  it('resolves an expression against the stored user profile', async () => {
    const tokens = await fixture.passwordGrant('openid email profile');
    const profile = await fixture.userinfo(tokens.access_token);

    expect(profile.ui_department).toEqual(DEPARTMENT);
  });

  it('resolves an expression against the calling application', async () => {
    const tokens = await fixture.passwordGrant('openid email profile');
    const profile = await fixture.userinfo(tokens.access_token);

    expect(profile.ui_client).toEqual(fixture.app.name);
  });
});

describe('Userinfo custom claims - a claim gated on scope', () => {
  it('releases the sensitive attribute when the client asked for the scope', async () => {
    const tokens = await fixture.passwordGrant('openid email profile internal');
    expect(tokens.scope.split(' ')).toContain('internal');

    const profile = await fixture.userinfo(tokens.access_token);

    expect(profile.ui_clearance).toEqual(CLEARANCE);
  });

  it('withholds the sensitive attribute when the client did not ask for the scope', async () => {
    const tokens = await fixture.passwordGrant('openid email profile');
    expect(tokens.scope.split(' ')).not.toContain('internal');

    const profile = await fixture.userinfo(tokens.access_token);

    // Positive anchor - the other custom claims still resolved, so this is a
    // genuine withholding rather than the whole enhancement having been skipped.
    expect(profile.ui_department).toEqual(DEPARTMENT);
    expect(profile).not.toHaveProperty('ui_clearance');
  });

  it('gates the same way for a token obtained through the browser flow', async () => {
    const tokens = await fixture.authorizationCodeFlow('scope=openid%20internal');
    const profile = await fixture.userinfo(tokens.access_token);

    expect(profile.ui_clearance).toEqual(CLEARANCE);
  });
});

describe('Userinfo custom claims - access control still applies', () => {
  it('refuses an unauthenticated call rather than returning claims', async () => {
    await performGet(fixture.oidc.userinfo_endpoint, '', {}).expect(401);
  });

  it('refuses a garbled bearer token rather than returning claims', async () => {
    await performGet(fixture.oidc.userinfo_endpoint, '', {
      Authorization: 'Bearer not-a-real-token',
    }).expect(401);
  });
});
