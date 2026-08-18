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
import { updateUser } from '@management-commands/user-management-commands';
import { setup } from '../../test-fixture';
import { decodeToken, setupTokenIdentityFixture, TokenIdentityFixture } from './fixtures/token-identity-fixture';

setup(200000);

/**
 * A long-lived session refreshes rather than signing in again. If custom claims
 * were frozen at first issue, an administrator changing a user's attributes -
 * moving them between departments, revoking an entitlement - would not take
 * effect until the user happened to log in again.
 *
 * `TokenServiceImpl.createRefreshTokenJWT` copies custom claims onto the refresh
 * token, so "are they replayed or recomputed?" is a real question. Measured
 * against a running gateway: they are recomputed.
 */
const BEFORE = 'engineering';
const AFTER = 'platform';
const DEPARTMENT_EXPRESSION = "{#context.attributes['user'].additionalInformation['department']}";

let fixture: TokenIdentityFixture;

/**
 * Rewrites the primary user's department, then waits until the gateway actually
 * serves the new value.
 *
 * A profile change does not advance the domain's `lastSync`, so `waitForSyncAfter`
 * is the wrong primitive here and simply times out. Polling the observable - a
 * freshly issued token - is both accurate and what the tests care about.
 */
async function setDepartment(value: string) {
  await updateUser(fixture.domain.id, fixture.accessToken, fixture.user.id, {
    firstName: fixture.user.firstName,
    lastName: fixture.user.lastName,
    email: fixture.user.email,
    additionalInformation: { department: value },
  });

  const deadline = Date.now() + 15_000;
  for (;;) {
    const probe = await fixture.passwordGrant('openid email profile');
    if (decodeToken(probe.access_token).payload.department === value) {
      return;
    }
    if (Date.now() > deadline) {
      throw new Error(`gateway still not serving department="${value}" after 15s`);
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
}

beforeAll(async () => {
  fixture = await setupTokenIdentityFixture({
    userAttributes: { department: BEFORE },
    tokenCustomClaims: [
      { tokenType: 'ACCESS_TOKEN', claimName: 'department', claimValue: DEPARTMENT_EXPRESSION },
      { tokenType: 'ID_TOKEN', claimName: 'department', claimValue: DEPARTMENT_EXPRESSION },
    ],
    userinfoCustomClaims: [{ claimName: 'department', claimValue: DEPARTMENT_EXPRESSION }],
  });
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Refresh claim propagation - an administrator change reaches a live session', () => {
  it('recomputes the custom claim on refresh instead of replaying the original value', async () => {
    await setDepartment(BEFORE);
    const initial = await fixture.passwordGrant('openid email profile');
    expect(decodeToken(initial.access_token).payload.department).toEqual(BEFORE);

    await setDepartment(AFTER);
    const refreshed = await fixture.refreshGrant(initial.refresh_token);

    expect(decodeToken(refreshed.access_token).payload.department).toEqual(AFTER);
  });

  it('recomputes the claim in the ID token too', async () => {
    await setDepartment(BEFORE);
    const initial = await fixture.passwordGrant('openid email profile');
    expect(decodeToken(initial.id_token).payload.department).toEqual(BEFORE);

    await setDepartment(AFTER);
    const refreshed = await fixture.refreshGrant(initial.refresh_token);

    expect(decodeToken(refreshed.id_token).payload.department).toEqual(AFTER);
  });

  it('answers userinfo from the current profile, even for a token issued earlier', async () => {
    await setDepartment(BEFORE);
    const tokens = await fixture.passwordGrant('openid email profile');
    expect((await fixture.userinfo(tokens.access_token)).department).toEqual(BEFORE);

    await setDepartment(AFTER);

    // The token is unchanged, but userinfo evaluates the expression at call time.
    expect((await fixture.userinfo(tokens.access_token)).department).toEqual(AFTER);
  });
});

describe('Refresh claim propagation - the rest of the token survives the refresh', () => {
  it('keeps the subject and audience while the custom claim moves on', async () => {
    await setDepartment(BEFORE);
    const initial = await fixture.passwordGrant('openid email profile');
    const initialToken = decodeToken(initial.access_token).payload;

    await setDepartment(AFTER);
    const refreshed = await fixture.refreshGrant(initial.refresh_token);
    const refreshedToken = decodeToken(refreshed.access_token).payload;

    expect(refreshedToken.sub).toEqual(initialToken.sub);
    expect(refreshedToken.gis).toEqual(initialToken.gis);
    expect(refreshedToken.aud).toEqual(initialToken.aud);
    expect(refreshedToken.department).toEqual(AFTER);
  });

  it('keeps the granted scopes across the refresh', async () => {
    await setDepartment(BEFORE);
    const initial = await fixture.passwordGrant('openid email profile');
    const refreshed = await fixture.refreshGrant(initial.refresh_token);

    expect(decodeToken(refreshed.access_token).payload.scope).toEqual(decodeToken(initial.access_token).payload.scope);
  });
});
