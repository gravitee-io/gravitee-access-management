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
import { jira } from '@specs-utils/jira';
import { setup } from '../../test-fixture';
import { account, AUTHORIZATION_DETAILS_TEST, payment, toDetailsParam } from './fixtures/authorization-details-fixture';
import { expectAcknowledged, expectBackchannelError } from './fixtures/authorization-details-ciba-fixture';
import {
  AuthorizationDetailsCibaHttpFixture,
  setupAuthorizationDetailsCibaHttpFixture,
} from './fixtures/authorization-details-ciba-http-fixture';

// The HTTP notifier sends the validated authorization_details to the device app

setup(400000);

let fixture: AuthorizationDetailsCibaHttpFixture;

beforeAll(async () => {
  fixture = await setupAuthorizationDetailsCibaHttpFixture();
});

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

const parseAuthorizationDetails = (notification: URLSearchParams | undefined): unknown => {
  expect(notification).toBeDefined();
  expect(notification.has('authorization_details')).toBe(true);
  return JSON.parse(notification.get('authorization_details'));
};

describe('CIBA HTTP notifier - authorization_details', () => {
  it(jira`should send the authorization_details parameter to the device app unchanged ${'AM-8114'}`, async () => {
    const authorizationDetails = [account({ actions: ['list_accounts', 'read_balances'] }), payment('USD', '9'), account()];

    const { response, findNotification } = await fixture.authenticate({ authorization_details: toDetailsParam(...authorizationDetails) });

    expectAcknowledged(response);
    expect(parseAuthorizationDetails(await findNotification())).toEqual(authorizationDetails);
  });

  it(jira`should send the authorization_details claim of a signed request object to the device app unchanged ${'AM-8114'}`, async () => {
    const authorizationDetails = [payment(), account({ locations: ['https://bank.example.com'] })];

    const { response, findNotification } = await fixture.authenticateWithRequestObject({ authorization_details: authorizationDetails });

    expectAcknowledged(response);
    expect(parseAuthorizationDetails(await findNotification())).toEqual(authorizationDetails);
  });

  it(jira`should send the same notification as before when the request has no authorization_details ${'AM-8114'}`, async () => {
    const { response, findNotification } = await fixture.authenticate();

    expectAcknowledged(response);
    expect([...(await findNotification()).keys()].sort()).toEqual(['expire', 'message', 'scope', 'state', 'subject', 'tid']);
  });

  it(jira`should issue tokens with the authorization_details the device app approved ${'AM-8114'}`, async () => {
    const authorizationDetails = [payment(), account()];

    const { response } = await fixture.authenticate({ authorization_details: toDetailsParam(...authorizationDetails) });

    const tokenResponse = await fixture.pollTokenUntilGranted(expectAcknowledged(response));
    expect(tokenResponse.status).toBe(200);
    expect(tokenResponse.body.authorization_details).toEqual(authorizationDetails);
    expect(decodeJwt(tokenResponse.body.access_token).authorization_details).toEqual(authorizationDetails);
  });

  it(jira`should reject a type not enabled on the application without notifying the device app ${'AM-8114'}`, async () => {
    const { response, findNotification } = await fixture.authenticate({
      authorization_details: toDetailsParam({ type: AUTHORIZATION_DETAILS_TEST.NOT_ENABLED_TYPE }),
    });

    expectBackchannelError(
      response,
      'invalid_authorization_details',
      `authorization_details[0] of type '${AUTHORIZATION_DETAILS_TEST.NOT_ENABLED_TYPE}' is not allowed for this client`,
    );
    expect(await findNotification()).toBeUndefined();
  });
});
