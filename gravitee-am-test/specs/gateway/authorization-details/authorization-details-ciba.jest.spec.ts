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
import { decodeJwt } from 'jose';
import { CIBA_UPSTREAM_REJECTING_LOGIN_HINT } from '@utils-commands/ciba-upstream-op-mock';
import { jira } from '@specs-utils/jira';
import { setup } from '../../test-fixture';
import { account, payment, toDetailsParam } from './fixtures/authorization-details-fixture';
import {
  AuthorizationDetailsCibaFixture,
  expectAcknowledged,
  setupAuthorizationDetailsCibaFixture,
} from './fixtures/authorization-details-ciba-fixture';

// Validated authorization_details are kept with the CIBA request, unchanged, until the CIBA grant issues tokens

setup(400000);

let fixture: AuthorizationDetailsCibaFixture;

beforeAll(async () => {
  fixture = await setupAuthorizationDetailsCibaFixture();
});

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

const expectTokensWith = (tokenResponse: any, authorizationDetails: unknown[]): void => {
  expect(tokenResponse.status).toBe(200);
  expect(tokenResponse.body.authorization_details).toEqual(authorizationDetails);
  expect(decodeJwt(tokenResponse.body.access_token).authorization_details).toEqual(authorizationDetails);
};

const expectTokensWithout = (tokenResponse: any): void => {
  expect(tokenResponse.status).toBe(200);
  expect(tokenResponse.body).not.toHaveProperty('authorization_details');
  expect(decodeJwt(tokenResponse.body.access_token)).not.toHaveProperty('authorization_details');
};

describe('CIBA grant - authorization_details', () => {
  it(jira`should issue tokens without authorization_details when the request has none ${'AM-8104'}`, async () => {
    const authReqId = expectAcknowledged(await fixture.authenticate());

    expectTokensWithout(await fixture.pollTokenUntilGranted(authReqId));
  });

  it(jira`should put the authorization_details parameter in the tokens unchanged ${'AM-8104'}`, async () => {
    const authorizationDetails = [account({ actions: ['list_accounts', 'read_balances'] }), payment('USD', '9'), account()];

    const authReqId = expectAcknowledged(await fixture.authenticate({ authorization_details: toDetailsParam(...authorizationDetails) }));

    expectTokensWith(await fixture.pollTokenUntilGranted(authReqId), authorizationDetails);
  });

  it(jira`should put the authorization_details claim of a signed request object in the tokens unchanged ${'AM-8106'}`, async () => {
    const authorizationDetails = [payment(), account({ locations: ['https://bank.example.com'] })];

    const authReqId = expectAcknowledged(await fixture.authenticateWithRequestObject({ authorization_details: authorizationDetails }));

    expectTokensWith(await fixture.pollTokenUntilGranted(authReqId), authorizationDetails);
  });

  it(jira`should return access_denied when the user rejects a request with authorization_details ${'AM-8104'}`, async () => {
    const authReqId = expectAcknowledged(
      await fixture.authenticate({ login_hint: CIBA_UPSTREAM_REJECTING_LOGIN_HINT, authorization_details: toDetailsParam(account()) }),
    );

    await expect(fixture.pollTokenUntilGranted(authReqId)).rejects.toThrow(/access_denied/);
  });
});
