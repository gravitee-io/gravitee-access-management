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
import { initiateLoginFlow, login, loginUserNameAndPassword } from '@gateway-commands/login-commands';
import { setup } from '../../test-fixture';
import { MASKED_SECRET_TEST, MaskedSecretFixture, setupMaskedSecretFixture } from './fixtures/masked-secret-fixture';

setup(200000);

let fixture: MaskedSecretFixture;

beforeAll(async () => {
  fixture = await setupMaskedSecretFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Automation API - masked secrets reach the gateway intact', () => {
  it('should still log in with the original password after the masked configuration is applied back', async () => {
    const applied = await fixture.applyMaskedIdentityProvider();
    expect(applied.status).toBe(200);
    expect(JSON.parse(applied.body.configuration).users[0].password).toEqual(MASKED_SECRET_TEST.MASK);

    const response = await loginUserNameAndPassword(
      fixture.application.settings.oauth.clientId,
      { username: MASKED_SECRET_TEST.USERNAME },
      MASKED_SECRET_TEST.PASSWORD,
      false,
      fixture.openIdConfiguration,
      fixture.domain,
      MASKED_SECRET_TEST.REDIRECT_URI,
      'code',
    );

    expect(response.headers['location']).toContain(`${MASKED_SECRET_TEST.REDIRECT_URI}?code=`);
  });

  it('should not accept the mask itself as the password after the masked configuration is applied back', async () => {
    expect((await fixture.applyMaskedIdentityProvider()).status).toBe(200);
    const clientId = fixture.application.settings.oauth.clientId;

    const authResponse = await initiateLoginFlow(
      clientId,
      fixture.openIdConfiguration,
      fixture.domain,
      'code',
      MASKED_SECRET_TEST.REDIRECT_URI,
    );
    const response = await login(authResponse, MASKED_SECRET_TEST.USERNAME, clientId, MASKED_SECRET_TEST.MASK);

    expect(response.headers['location']).toContain('error=login_failed');
  });
});
