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
import { setup } from '../../test-fixture';
import {
  AuthorizationDetailsFixture,
  account,
  toDetailsParam,
  expectLoginPage,
  payment,
  redirectParams,
  setupAuthorizationDetailsFixture,
} from './fixtures/authorization-details-fixture';

// RFC 9396 Section 2: valid authorization_details let the user log in and the flow complete

setup(200000);

let fixture: AuthorizationDetailsFixture;

beforeAll(async () => {
  fixture = await setupAuthorizationDetailsFixture();
});

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

describe('Authorization endpoint - valid authorization_details', () => {
  it(jira`should send a request without authorization_details to the login page ${'AM-8048'}`, async () => {
    const response = await fixture.authorize();

    expectLoginPage(response);
  });

  it(jira`should accept any object of a type without schema ${'AM-8048'}`, async () => {
    const response = await fixture.authorize({
      authorization_details: toDetailsParam(
        account({ actions: ['list_accounts'], locations: ['https://example.com/accounts'], extra: { nested: [1, true] } }),
      ),
    });

    expectLoginPage(response);
  });

  it(jira`should accept several elements of the same and of different types ${'AM-8048'}`, async () => {
    const response = await fixture.authorize({
      authorization_details: toDetailsParam(payment('EUR', '10.00'), account(), payment('USD', '20.00')),
    });

    expectLoginPage(response);
  });

  it(jira`should accept scope and authorization_details together ${'AM-8048'}`, async () => {
    const response = await fixture.authorize({ scope: 'openid', authorization_details: toDetailsParam(account()) });

    expectLoginPage(response);
  });

  it(jira`should complete the flow with a code once the user logs in ${'AM-8048'}`, async () => {
    const authorizeResponse = await fixture.authorize({ authorization_details: toDetailsParam(payment(), account()) });

    const response = await fixture.loginFrom(authorizeResponse);

    expect(redirectParams(response).get('code')).toMatch(/^[-_a-zA-Z0-9]+$/);
  });

  it(jira`should still check scope on its own ${'AM-8048'}`, async () => {
    const authorizeResponse = await fixture.authorize({ scope: 'unknown-scope', authorization_details: toDetailsParam(account()) });

    const response = await fixture.loginFrom(authorizeResponse);

    expect(redirectParams(response).get('error')).toEqual('invalid_scope');
  });
});
