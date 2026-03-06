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
import { afterAll, beforeAll, expect } from '@jest/globals';
import { performDelete, performGet, performPost } from '@gateway-commands/oauth-oidc-commands';
import { getApplication, patchApplication } from '@management-commands/application-management-commands';
import { deleteFactor, getFactor } from '@management-commands/factor-management-commands';
import { SelfAccountRecoveryCodeFixture, setupRecoveryCodeFixture, getEndUserAccessToken } from './fixture/self-account-fixture';
import { setup } from '../../test-fixture';

setup(180000);

let fixture: SelfAccountRecoveryCodeFixture;
let userAccessToken: string;

function accountUrl(fixture: SelfAccountRecoveryCodeFixture, path: string): string {
  return `${process.env.AM_GATEWAY_URL}/${fixture.domain.hrid}/account/api${path}`;
}

beforeAll(async () => {
  fixture = await setupRecoveryCodeFixture();
  userAccessToken = await getEndUserAccessToken(fixture);

  // Enroll recovery codes for the user
  await performPost(accountUrl(fixture, '/auth/recovery_code'), '', null, {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${userAccessToken}`,
  }).expect(200);
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('SelfAccount - Recovery Code', () => {
  it('should return 10 recovery codes after enrollment', async () => {
    const response = await performGet(accountUrl(fixture, '/auth/recovery_code'), '', {
      Authorization: `Bearer ${userAccessToken}`,
    }).expect(200);

    expect(response.body).toHaveLength(10);
  });

  it('should delete recovery codes', async () => {
    await performDelete(accountUrl(fixture, '/auth/recovery_code'), '', {
      Authorization: `Bearer ${userAccessToken}`,
    }).expect(204);
  });

  it('should return empty list after deletion', async () => {
    const response = await performGet(accountUrl(fixture, '/auth/recovery_code'), '', {
      Authorization: `Bearer ${userAccessToken}`,
    }).expect(200);

    expect(response.body).toHaveLength(0);
  });

  it('should remove recovery factor from application', async () => {
    await patchApplication(fixture.domain.id, fixture.accessToken, {
      factors: new Set<string>(),
      settings: { mfa: {} },
    }, fixture.application.id);

    const app = await getApplication(fixture.domain.id, fixture.accessToken, fixture.application.id);
    expect(Array.from(app.factors ?? [])).toHaveLength(0);
  });

  it('should delete recovery code factor from domain', async () => {
    await deleteFactor(fixture.domain.id, fixture.accessToken, fixture.recoveryCodeFactor.id);

    await expect(getFactor(fixture.domain.id, fixture.accessToken, fixture.recoveryCodeFactor.id)).rejects.toMatchObject({
      response: { status: 404 },
    });
  });
});
