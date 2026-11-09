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
  expectCode,
  expectTokenError,
  payment,
  setupConsentFixture,
} from './fixtures/authorization-details-consent-fixture';

// RFC 9396 §6: a client that sends authorization_details with a code or refresh token must send exactly what was granted

setup(200000);

const MISMATCH = 'authorization_details do not match the granted authorization details';
const NONE_GRANTED = 'no authorization_details were granted';

let fixture: ConsentFixture;

beforeAll(async () => {
  fixture = await setupConsentFixture();
});

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

const GRANTED = [payment('EUR', '20.00'), account({ actions: ['list_accounts'] })];

const DIFFERENT = [
  ['a subset of the granted details', [GRANTED[0]]],
  ['an extra element', [...GRANTED, account({ actions: ['read_balances'] })]],
  ['a changed value', [payment('EUR', '20.01'), GRANTED[1]]],
] as const;

describe('Authorization details on the token endpoint - authorization_code grant', () => {
  it(jira`should issue the granted details when the client sends them in another order ${'AM-8080'}`, async () => {
    const callback = await fixture.approve(fixture.nextUser(), GRANTED);

    const response = await fixture.redeemCode(callback, { authorization_details: toDetailsParam(GRANTED[1], GRANTED[0]) });

    expect(response.status).toBe(200);
    expect(response.body.authorization_details).toEqual(GRANTED);
    expect(decodeJwt(response.body.access_token).authorization_details).toEqual(GRANTED);
  });

  it.each(DIFFERENT)(jira`should reject %s ${'AM-8080'}`, async (_, requested) => {
    const callback = await fixture.approve(fixture.nextUser(), GRANTED);

    const response = await fixture.redeemCode(callback, { authorization_details: toDetailsParam(...requested) });

    expectTokenError(response, 'invalid_authorization_details', MISMATCH);
  });

  it(jira`should reject details when the code was issued without any ${'AM-8080'}`, async () => {
    const callback = await fixture.signIn(fixture.nextUser(), {});
    expectCode(callback);

    const response = await fixture.redeemCode(callback, { authorization_details: toDetailsParam(payment()) });

    expectTokenError(response, 'invalid_authorization_details', NONE_GRANTED);
  });

  it(jira`should reject a type the application does not enable ${'AM-8081'}`, async () => {
    const callback = await fixture.approve(fixture.nextUser(), GRANTED);

    const response = await fixture.redeemCode(callback, { authorization_details: toDetailsParam({ type: 'unknown' }) });

    expectTokenError(
      response,
      'invalid_authorization_details',
      "authorization_details[0] of type 'unknown' is not allowed for this client",
    );
  });

  it(jira`should keep the code usable after rejecting malformed details ${'AM-8081'}`, async () => {
    const callback = await fixture.approve(fixture.nextUser(), GRANTED);

    const rejected = await fixture.redeemCode(callback, { authorization_details: 'not json' });
    expectTokenError(rejected, 'invalid_request', 'authorization_details is not valid JSON');

    const response = await fixture.redeemCode(callback);
    expect(response.status).toBe(200);
    expect(response.body.authorization_details).toEqual(GRANTED);
  });
});

describe('Authorization details on the token endpoint - refresh_token grant', () => {
  it(jira`should accept the granted details across refreshes with rotation, then reject a subset ${'AM-8080'}`, async () => {
    let tokens = (await fixture.exchangeCode(await fixture.approve(fixture.nextUser(), GRANTED))).body;

    for (let refreshes = 0; refreshes < 3; refreshes++) {
      const response = await fixture.refresh(tokens.refresh_token, { authorization_details: toDetailsParam(GRANTED[1], GRANTED[0]) });
      expect(response.status).toBe(200);
      expect(response.body.refresh_token).not.toEqual(tokens.refresh_token);
      tokens = response.body;
      expect(tokens.authorization_details).toEqual(GRANTED);
      expect(decodeJwt(tokens.access_token).authorization_details).toEqual(GRANTED);
    }

    const response = await fixture.refresh(tokens.refresh_token, { authorization_details: toDetailsParam(GRANTED[0]) });
    expectTokenError(response, 'invalid_authorization_details', MISMATCH);
  });

  it(jira`should reject details when the refresh token was issued without any ${'AM-8080'}`, async () => {
    const callback = await fixture.signIn(fixture.nextUser(), {});
    const { refresh_token } = (await fixture.exchangeCode(callback)).body;

    const response = await fixture.refresh(refresh_token, { authorization_details: toDetailsParam(payment()) });

    expectTokenError(response, 'invalid_authorization_details', NONE_GRANTED);
  });

  it(jira`should accept a granted scope along with the granted details ${'AM-8083'}`, async () => {
    const { refresh_token } = (
      await fixture.exchangeCode(await fixture.approve(fixture.nextUser(), GRANTED, { scope: CONSENT_TEST.SCOPE }))
    ).body;

    const response = await fixture.refresh(refresh_token, { scope: CONSENT_TEST.SCOPE, authorization_details: toDetailsParam(...GRANTED) });

    expect(response.status).toBe(200);
    expect(response.body.scope).toEqual(CONSENT_TEST.SCOPE);
    expect(response.body.authorization_details).toEqual(GRANTED);
  });

  it(jira`should drop a scope that was not granted and still issue the granted details ${'AM-8083'}`, async () => {
    const { refresh_token } = (
      await fixture.exchangeCode(await fixture.approve(fixture.nextUser(), GRANTED, { scope: CONSENT_TEST.SCOPE }))
    ).body;

    const response = await fixture.refresh(refresh_token, {
      scope: `${CONSENT_TEST.SCOPE} unknown`,
      authorization_details: toDetailsParam(...GRANTED),
    });

    expect(response.status).toBe(200);
    expect(response.body.scope).toEqual(CONSENT_TEST.SCOPE);
    expect(response.body.authorization_details).toEqual(GRANTED);
  });

  it(jira`should reject details that differ even when the scope was granted ${'AM-8083'}`, async () => {
    const { refresh_token } = (
      await fixture.exchangeCode(await fixture.approve(fixture.nextUser(), GRANTED, { scope: CONSENT_TEST.SCOPE }))
    ).body;

    const response = await fixture.refresh(refresh_token, { scope: CONSENT_TEST.SCOPE, authorization_details: toDetailsParam(GRANTED[0]) });

    expectTokenError(response, 'invalid_authorization_details', MISMATCH);
  });
});
