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
  AUTHORIZATION_DETAILS_TEST,
  AuthorizationDetailsFixture,
  CIMD_EMPTY_TYPES_CLIENT_ID,
  CIMD_UNDECLARED_TYPES_CLIENT_ID,
  CIMD_UNDECLARED_TYPES_REDIRECT_URI,
  account,
  expectClientError,
  expectLoginPage,
  payment,
  setupAuthorizationDetailsFixture,
  toDetailsParam,
} from './fixtures/authorization-details-fixture';

// RFC 9396 Section 10: a CIMD client may use the types its metadata document declares and its template enables.

setup(200000);

let fixture: AuthorizationDetailsFixture;

beforeAll(async () => {
  fixture = await setupAuthorizationDetailsFixture({ cimd: true });
});

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

describe('Authorization endpoint - CIMD client authorization_details_types', () => {
  it(jira`should accept a type the document declares and the template enables ${'AM-8154'}`, async () => {
    const response = await fixture.authorize({ authorization_details: toDetailsParam(payment()) });

    expectLoginPage(response);
  });

  it(jira`should reject a type the template enables but the document does not declare ${'AM-8154'}`, async () => {
    const response = await fixture.authorize({ authorization_details: toDetailsParam(account()) });

    expectClientError(
      response,
      'invalid_authorization_details',
      `authorization_details[0] of type '${AUTHORIZATION_DETAILS_TEST.ACCOUNT_TYPE}' is not allowed for this client`,
    );
  });

  it(jira`should reject a domain type the template does not enable ${'AM-8154'}`, async () => {
    const response = await fixture.authorize({
      authorization_details: toDetailsParam({ type: AUTHORIZATION_DETAILS_TEST.NOT_ENABLED_TYPE }),
    });

    expectClientError(
      response,
      'invalid_authorization_details',
      `authorization_details[0] of type '${AUTHORIZATION_DETAILS_TEST.NOT_ENABLED_TYPE}' is not allowed for this client`,
    );
  });

  it.each([
    ['a payment', payment()],
    ['an account', account()],
  ])(jira`should accept %s type the template enables when the document declares no types ${'AM-8154'}`, async (_type, detail) => {
    const response = await fixture.authorize({
      client_id: CIMD_UNDECLARED_TYPES_CLIENT_ID,
      redirect_uri: CIMD_UNDECLARED_TYPES_REDIRECT_URI,
      authorization_details: toDetailsParam(detail),
    });

    expectLoginPage(response);
  });

  it(jira`should reject every type when the document declares an empty list ${'AM-8154'}`, async () => {
    const response = await fixture.authorize({ client_id: CIMD_EMPTY_TYPES_CLIENT_ID, authorization_details: toDetailsParam(payment()) });

    expectClientError(
      response,
      'invalid_authorization_details',
      `authorization_details[0] of type '${AUTHORIZATION_DETAILS_TEST.PAYMENT_TYPE}' is not allowed for this client`,
    );
  });
});

describe('Authorization endpoint - CIMD client after a template change', () => {
  beforeAll(async () => {
    await fixture.enableTypes([AUTHORIZATION_DETAILS_TEST.ACCOUNT_TYPE]);
  });

  afterAll(async () => {
    await fixture.enableTypes([AUTHORIZATION_DETAILS_TEST.PAYMENT_TYPE, AUTHORIZATION_DETAILS_TEST.ACCOUNT_TYPE]);
  });

  it(jira`should reject a declared type once the template stops enabling it ${'AM-8154'}`, async () => {
    const response = await fixture.authorize({ authorization_details: toDetailsParam(payment()) });

    expectClientError(
      response,
      'invalid_authorization_details',
      `authorization_details[0] of type '${AUTHORIZATION_DETAILS_TEST.PAYMENT_TYPE}' is not allowed for this client`,
    );
  });
});
