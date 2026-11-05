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
  AUTHORIZATION_DETAILS_TEST,
  AuthorizationDetailsFixture,
  account,
  toDetailsParam,
  expectClientError,
  payment,
  setupAuthorizationDetailsFixture,
} from './fixtures/authorization-details-fixture';

// RFC 9396 Section 5: an element the client may not use, or that its type rejects, is invalid_authorization_details, before login

setup(200000);

let fixture: AuthorizationDetailsFixture;

beforeAll(async () => {
  fixture = await setupAuthorizationDetailsFixture();
});

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

describe('Authorization endpoint - authorization_details the client may not use', () => {
  it(jira`should reject a type the domain does not have ${'AM-8046'}`, async () => {
    const response = await fixture.authorize({ authorization_details: toDetailsParam(account(), { type: 'unknown_type' }) });

    expectClientError(
      response,
      'invalid_authorization_details',
      "authorization_details[1] of type 'unknown_type' is not allowed for this client",
    );
  });

  it(jira`should match types case-sensitively ${'AM-8046'}`, async () => {
    const response = await fixture.authorize({ authorization_details: toDetailsParam({ type: 'Account_Information' }) });

    expectClientError(
      response,
      'invalid_authorization_details',
      "authorization_details[0] of type 'Account_Information' is not allowed for this client",
    );
  });

  it(jira`should reject a domain type the application does not enable ${'AM-8046'}`, async () => {
    const response = await fixture.authorize({
      authorization_details: toDetailsParam({ type: AUTHORIZATION_DETAILS_TEST.NOT_ENABLED_TYPE }),
    });

    expectClientError(
      response,
      'invalid_authorization_details',
      "authorization_details[0] of type 'internal_transfer' is not allowed for this client",
    );
  });

  it(jira`should reject an element its schema rejects without repeating its values ${'AM-8046'}`, async () => {
    const response = await fixture.authorize({ authorization_details: toDetailsParam(account(), payment('euros', '987.65')) });

    expectClientError(
      response,
      'invalid_authorization_details',
      "authorization_details[1] of type 'payment_initiation' does not match its schema",
    );
    expect(response.headers.location).not.toMatch(/euros|987/);
  });

  it(jira`should name only the position of a type that cannot appear in an error description ${'AM-8046'}`, async () => {
    const response = await fixture.authorize({ authorization_details: toDetailsParam({ type: 'pay"ment' }) });

    expectClientError(response, 'invalid_authorization_details', 'authorization_details[0] is not allowed for this client');
  });
});

describe('Authorization endpoint - authorization_details error delivery', () => {
  it(jira`should return the error in the fragment for an implicit request ${'AM-8047'}`, async () => {
    const response = await fixture.authorize({ response_type: 'token', authorization_details: toDetailsParam({ type: 'unknown_type' }) });

    expectClientError(
      response,
      'invalid_authorization_details',
      "authorization_details[0] of type 'unknown_type' is not allowed for this client",
      '#',
    );
  });

  it(jira`should return the error in the fragment when response_mode asks for it ${'AM-8047'}`, async () => {
    const response = await fixture.authorize({
      response_mode: 'fragment',
      authorization_details: toDetailsParam({ type: 'unknown_type' }),
    });

    expectClientError(
      response,
      'invalid_authorization_details',
      "authorization_details[0] of type 'unknown_type' is not allowed for this client",
      '#',
    );
  });

  it(jira`should show the error page when the redirect_uri is not registered ${'AM-8047'}`, async () => {
    const response = await fixture.authorize({
      redirect_uri: 'https://attacker.example.com/callback',
      authorization_details: toDetailsParam({ type: 'unknown_type' }),
    });

    const location = new URL(response.headers.location);
    expect(location.origin).toEqual(process.env.AM_GATEWAY_URL);
    expect(location.pathname).toEqual(`/${fixture.domain.hrid}/oauth/error`);
  });
});
