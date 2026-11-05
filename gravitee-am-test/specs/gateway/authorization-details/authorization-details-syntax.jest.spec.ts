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
import { afterAll, beforeAll, describe, it } from '@jest/globals';
import { jira } from '@specs-utils/jira';
import { setup } from '../../test-fixture';
import {
  AuthorizationDetailsFixture,
  account,
  toDetailsParam,
  expectClientError,
  setupAuthorizationDetailsFixture,
} from './fixtures/authorization-details-fixture';

// RFC 9396 Section 2: a malformed authorization_details is an invalid_request, before login

setup(200000);

let fixture: AuthorizationDetailsFixture;

beforeAll(async () => {
  fixture = await setupAuthorizationDetailsFixture();
});

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

describe('Authorization endpoint - malformed authorization_details', () => {
  it.each([
    ['is not JSON', 'not json', 'authorization_details is not valid JSON'],
    ['repeats a member', '[{"type":"account_information","type":"account_information"}]', 'authorization_details is not valid JSON'],
    ['is an object', JSON.stringify(account()), 'authorization_details must be a non-empty JSON array'],
    ['is an empty array', '[]', 'authorization_details must be a non-empty JSON array'],
    [
      'has an element that is not an object',
      toDetailsParam(account(), 'account_information'),
      'authorization_details[1] must be a JSON object',
    ],
    ['has an element without type', toDetailsParam({ actions: ['read'] }), 'authorization_details[0] must have a non-empty string type'],
    ['has an element with an empty type', toDetailsParam({ type: '' }), 'authorization_details[0] must have a non-empty string type'],
    ['has an element with a non-string type', toDetailsParam({ type: 42 }), 'authorization_details[0] must have a non-empty string type'],
  ])(jira`should reject a value that %s ${'AM-8045'}`, async (_case, authorizationDetails, description) => {
    const response = await fixture.authorize({ authorization_details: authorizationDetails });

    expectClientError(response, 'invalid_request', description);
  });
});
