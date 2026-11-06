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
import { jira } from '@specs-utils/jira';
import { decodeJwt } from '@utils-commands/jwt';
import { setup } from '../../test-fixture';
import { toDetailsParam } from './fixtures/authorization-details-fixture';
import { ConsentFixture, expectCode, payment, setupConsentFixture } from './fixtures/authorization-details-consent-fixture';

setup(200000);

let fixture: ConsentFixture;

beforeAll(async () => {
  fixture = await setupConsentFixture({ skipConsent: true });
  await fixture.setTokenCustomClaims([{ tokenType: 'ACCESS_TOKEN', claimName: 'authorization_details', claimValue: 'custom-value' }]);
});

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

describe('Authorization details in tokens - custom token claim', () => {
  it(jira`should keep the granted details in the access token and after a refresh ${'AM-8067'}`, async () => {
    const details = [payment()];
    const callback = await fixture.signIn(fixture.nextUser(), { authorization_details: toDetailsParam(...details) });
    expectCode(callback);
    const tokens = (await fixture.exchangeCode(callback)).body;
    expect(decodeJwt(tokens.access_token).authorization_details).toEqual(details);

    const refreshed = await fixture.requestToken({ grant_type: 'refresh_token', refresh_token: tokens.refresh_token });

    expect(refreshed.status).toBe(200);
    expect(decodeJwt(refreshed.body.access_token).authorization_details).toEqual(details);
  });
});
