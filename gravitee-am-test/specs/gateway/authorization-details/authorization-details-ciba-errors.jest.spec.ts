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
import { account, payment, toDetailsParam } from './fixtures/authorization-details-fixture';
import {
  AuthorizationDetailsCibaFixture,
  expectBackchannelError,
  setupAuthorizationDetailsCibaFixture,
} from './fixtures/authorization-details-ciba-fixture';

// The backchannel authentication endpoint rejects invalid authorization_details before notifying the user

setup(300000);

let fixture: AuthorizationDetailsCibaFixture;

beforeAll(async () => {
  fixture = await setupAuthorizationDetailsCibaFixture();
});

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

describe('Backchannel authentication - malformed authorization_details parameter', () => {
  it(jira`should reject a value that is not JSON ${'AM-8101'}`, async () => {
    expectBackchannelError(
      await fixture.authenticate({ authorization_details: 'not json' }),
      'invalid_request',
      'authorization_details is not valid JSON',
    );
  });

  it(jira`should reject a value that is not a non-empty array ${'AM-8101'}`, async () => {
    expectBackchannelError(
      await fixture.authenticate({ authorization_details: JSON.stringify(account()) }),
      'invalid_request',
      'authorization_details must be a non-empty JSON array',
    );
  });

  it(jira`should reject an element without a type ${'AM-8101'}`, async () => {
    expectBackchannelError(
      await fixture.authenticate({ authorization_details: toDetailsParam(account(), { actions: ['read'] }) }),
      'invalid_request',
      'authorization_details[1] must have a non-empty string type',
    );
  });
});

describe('Backchannel authentication - authorization_details parameter the client may not use', () => {
  it(jira`should reject a type the domain does not have ${'AM-8102'}`, async () => {
    expectBackchannelError(
      await fixture.authenticate({ authorization_details: toDetailsParam({ type: 'unknown_type' }) }),
      'invalid_authorization_details',
      "authorization_details[0] of type 'unknown_type' is not allowed for this client",
    );
  });

  it(jira`should reject a type not enabled on the application ${'AM-8102'}`, async () => {
    expectBackchannelError(
      await fixture.authenticate({ authorization_details: toDetailsParam({ type: 'internal_transfer' }) }),
      'invalid_authorization_details',
      "authorization_details[0] of type 'internal_transfer' is not allowed for this client",
    );
  });

  it(jira`should reject an element its schema rejects ${'AM-8102'}`, async () => {
    expectBackchannelError(
      await fixture.authenticate({ authorization_details: toDetailsParam(payment('euro')) }),
      'invalid_authorization_details',
      "authorization_details[0] of type 'payment_initiation' does not match its schema",
    );
  });
});

describe('Backchannel authentication - authorization_details claim of a signed request object', () => {
  it(jira`should reject a claim holding the array as a string ${'AM-8106'}`, async () => {
    expectBackchannelError(
      await fixture.authenticateWithRequestObject({ authorization_details: toDetailsParam(account()) }),
      'invalid_request',
      'authorization_details must be a non-empty JSON array',
    );
  });

  it(jira`should reject an element without a type ${'AM-8106'}`, async () => {
    expectBackchannelError(
      await fixture.authenticateWithRequestObject({ authorization_details: [{ actions: ['read'] }] }),
      'invalid_request',
      'authorization_details[0] must have a non-empty string type',
    );
  });

  it(jira`should reject a type not enabled on the application ${'AM-8106'}`, async () => {
    expectBackchannelError(
      await fixture.authenticateWithRequestObject({ authorization_details: [account(), { type: 'internal_transfer' }] }),
      'invalid_authorization_details',
      "authorization_details[1] of type 'internal_transfer' is not allowed for this client",
    );
  });

  it(jira`should reject an element its schema rejects ${'AM-8106'}`, async () => {
    expectBackchannelError(
      await fixture.authenticateWithRequestObject({
        authorization_details: [{ type: 'payment_initiation', instructedAmount: { currency: 'EUR', amount: 12 } }],
      }),
      'invalid_authorization_details',
      "authorization_details[0] of type 'payment_initiation' does not match its schema",
    );
  });
});

describe('Backchannel authentication - scope with authorization_details', () => {
  it(jira`should still require openid in scope ${'AM-8103'}`, async () => {
    expectBackchannelError(
      await fixture.authenticate({ scope: 'profile', authorization_details: toDetailsParam(account()) }),
      'invalid_scope',
      "scope is missing or doesn't contain 'openid'",
    );
  });
});
