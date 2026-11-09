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
import {
  account,
  CONSENT_TEST,
  ConsentFixture,
  expectTokenError,
  payment,
  setupConsentFixture,
} from './fixtures/authorization-details-consent-fixture';

// RFC 9396 §6: a client_credentials client requests authorization details for itself at the token endpoint

setup(200000);

let fixture: ConsentFixture;

beforeAll(async () => {
  fixture = await setupConsentFixture();
});

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

describe('Authorization details on the token endpoint - client_credentials grant', () => {
  it(jira`should issue the requested details unchanged, without a refresh token ${'AM-8082'}`, async () => {
    const details = [account({ actions: ['list_accounts'], locations: ['https://bank.example.com'] }), payment('GBP', '7.50'), account()];

    const response = await fixture.requestClientCredentialsToken({ authorization_details: toDetailsParam(...details) });

    expect(response.status).toBe(200);
    expect(response.body.authorization_details).toEqual(details);
    expect(decodeJwt(response.body.access_token).authorization_details).toEqual(details);
    expect(response.body).not.toHaveProperty('refresh_token');
  });

  it(jira`should issue a token without authorization_details when none are requested ${'AM-8082'}`, async () => {
    const response = await fixture.requestClientCredentialsToken();

    expect(response.status).toBe(200);
    expect(response.body).not.toHaveProperty('authorization_details');
    expect(decodeJwt(response.body.access_token)).not.toHaveProperty('authorization_details');
  });

  it(jira`should reject a type the application does not enable ${'AM-8082'}`, async () => {
    const response = await fixture.requestClientCredentialsToken({ authorization_details: toDetailsParam(payment(), { type: 'unknown' }) });

    expectTokenError(
      response,
      'invalid_authorization_details',
      "authorization_details[1] of type 'unknown' is not allowed for this client",
    );
  });

  it(jira`should reject an element its schema rejects ${'AM-8082'}`, async () => {
    const response = await fixture.requestClientCredentialsToken({ authorization_details: toDetailsParam(payment('eur')) });

    expectTokenError(
      response,
      'invalid_authorization_details',
      "authorization_details[0] of type 'payment_initiation' does not match its schema",
    );
  });

  it(jira`should reject malformed details ${'AM-8082'}`, async () => {
    const response = await fixture.requestClientCredentialsToken({ authorization_details: '[]' });

    expectTokenError(response, 'invalid_request', 'authorization_details must be a non-empty JSON array');
  });

  it(jira`should check the scope and the details separately ${'AM-8083'}`, async () => {
    const details = [payment()];

    const granted = await fixture.requestClientCredentialsToken({
      scope: CONSENT_TEST.SCOPE,
      authorization_details: toDetailsParam(...details),
    });
    expect(granted.status).toBe(200);
    expect(granted.body.scope).toEqual(CONSENT_TEST.SCOPE);
    expect(granted.body.authorization_details).toEqual(details);

    const invalidScope = await fixture.requestClientCredentialsToken({
      scope: 'unknown',
      authorization_details: toDetailsParam(...details),
    });
    expect(invalidScope.status).toBe(400);
    expect(invalidScope.body.error).toEqual('invalid_scope');
  });
});

describe('Authorization details on the token endpoint - other grants', () => {
  it(jira`should ignore authorization_details on the password grant ${'AM-8084'}`, async () => {
    const user = fixture.nextUser();

    const response = await fixture.requestToken({
      grant_type: 'password',
      username: user.username,
      password: user.password,
      authorization_details: 'not json',
    });

    expect(response.status).toBe(200);
    expect(response.body).not.toHaveProperty('authorization_details');
    expect(decodeJwt(response.body.access_token)).not.toHaveProperty('authorization_details');
  });
});
