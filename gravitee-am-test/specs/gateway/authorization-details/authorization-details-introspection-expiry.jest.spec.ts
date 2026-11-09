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
import { delay } from '@utils-commands/misc';
import { setup } from '../../test-fixture';
import { ConsentFixture, payment, setupConsentFixture } from './fixtures/authorization-details-consent-fixture';

setup(200000);

const TOKEN_VALIDITY_SECONDS = 5;

let fixture: ConsentFixture;

beforeAll(async () => {
  fixture = await setupConsentFixture({ tokenValiditySeconds: TOKEN_VALIDITY_SECONDS });
});

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

describe('Introspection - expired tokens with authorization details', () => {
  it(jira`should report expired access and refresh tokens as inactive, without authorization_details ${'AM-8090'}`, async () => {
    const tokens = (await fixture.exchangeCode(await fixture.approve(fixture.nextUser(), [payment()]))).body;
    expect((await fixture.introspect(tokens.access_token)).authorization_details).toEqual([payment()]);

    await delay((TOKEN_VALIDITY_SECONDS + 2) * 1000);

    expect(await fixture.introspect(tokens.access_token)).toEqual({ active: false });
    expect(await fixture.introspect(tokens.refresh_token, 'refresh_token')).toEqual({ active: false });
  });
});
