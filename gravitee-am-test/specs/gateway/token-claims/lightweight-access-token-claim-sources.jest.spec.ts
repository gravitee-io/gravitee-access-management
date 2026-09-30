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
import { decodeToken, LIGHTWEIGHT_ACCESS_TOKEN_CLAIMS, setupTokenIdentityFixture, TokenIdentityFixture } from './fixtures/token-identity-fixture';

setup(200000);

const SCOPE = 'openid email profile';
const ROLE = 'lightweight-role';
const GROUP = 'lightweight-group';

let fixture: TokenIdentityFixture;

beforeAll(async () => {
  fixture = await setupTokenIdentityFixture({
    userAttributes: { department: 'engineering' },
    userRole: ROLE,
    userGroup: GROUP,
    tokenPreScript: "context.setAttribute('preTokenValue', 'from-pre-token')",
    tokenCustomClaims: [
      { tokenType: 'ACCESS_TOKEN', claimName: 'tenant', claimValue: 'acme' },
      {
        tokenType: 'ACCESS_TOKEN',
        claimName: 'department',
        claimValue: "{#context.attributes['user'].additionalInformation['department']}",
      },
      { tokenType: 'ACCESS_TOKEN', claimName: 'user_roles', claimValue: "{#context.attributes['user'].roles}" },
      { tokenType: 'ACCESS_TOKEN', claimName: 'user_groups', claimValue: "{#context.attributes['user'].groups}" },
      { tokenType: 'ACCESS_TOKEN', claimName: 'pre_token', claimValue: "{#context.attributes['preTokenValue']}" },
      { tokenType: 'ACCESS_TOKEN', claimName: 'act', claimValue: 'custom-actor' },
      { tokenType: 'ACCESS_TOKEN', claimName: 'cnf', claimValue: 'custom-cnf' },
    ],
  });
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Lightweight JWT claim sources - disabled', () => {
  it('should issue a custom claim from each source', async () => {
    await fixture.setLightweightJwt(false, 'tenant');

    const payload = decodeToken((await fixture.passwordGrant(SCOPE)).access_token).payload;

    expect(payload).toMatchObject({
      tenant: 'acme',
      department: 'engineering',
      pre_token: 'from-pre-token',
      act: 'custom-actor',
      cnf: 'custom-cnf',
    });
    expect(payload.user_roles).toEqual([ROLE]);
    expect(payload.user_groups).toHaveLength(1);
  });
});

describe('Lightweight JWT claim sources - enabled', () => {
  it('should keep only the custom claims named after a kept claim', async () => {
    await fixture.setLightweightJwt(true, 'tenant');

    const payload = decodeToken((await fixture.passwordGrant(SCOPE)).access_token).payload;

    expect(Object.keys(payload).sort()).toEqual([...LIGHTWEIGHT_ACCESS_TOKEN_CLAIMS, 'act', 'cnf'].sort());
    expect(payload).toMatchObject({ act: 'custom-actor', cnf: 'custom-cnf' });
  });
});
