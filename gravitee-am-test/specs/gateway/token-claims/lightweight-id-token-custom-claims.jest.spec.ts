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
import { decodeToken, LIGHTWEIGHT_ID_TOKEN_CLAIMS, setupTokenIdentityFixture, TokenIdentityFixture } from './fixtures/token-identity-fixture';

setup(200000);

const SCOPE = 'openid email profile';

let fixture: TokenIdentityFixture;

beforeAll(async () => {
  fixture = await setupTokenIdentityFixture({
    lightweightJwtSettings: { enabled: true },
    tokenCustomClaims: [
      { tokenType: 'ID_TOKEN', claimName: 'sub', claimValue: 'custom-subject' },
      { tokenType: 'ID_TOKEN', claimName: 'acr', claimValue: 'custom-acr' },
      { tokenType: 'ID_TOKEN', claimName: 'tenant', claimValue: 'acme' },
    ],
  });
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Lightweight JWT - ID token custom claims', () => {
  it('should keep only the custom claims named after a kept claim', async () => {
    const payload = decodeToken((await fixture.passwordGrant(SCOPE)).id_token).payload;

    expect(Object.keys(payload).sort()).toEqual([...LIGHTWEIGHT_ID_TOKEN_CLAIMS, 'acr'].sort());
    expect(payload).toMatchObject({ sub: 'custom-subject', acr: 'custom-acr' });
  });
});
