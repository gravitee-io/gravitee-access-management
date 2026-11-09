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
import { toDetailsParam } from './fixtures/authorization-details-fixture';
import { account, callbackParams, ConsentFixture, payment, setupConsentFixture } from './fixtures/authorization-details-consent-fixture';

// RFC 9396 §9.2: the introspection response carries the authorization_details granted to the token

setup(200000);

let fixture: ConsentFixture;

beforeAll(async () => {
  fixture = await setupConsentFixture();
});

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

describe('Introspection - tokens without authorization details', () => {
  it(jira`should not return authorization_details for tokens issued without them ${'AM-8086'}`, async () => {
    const tokens = (await fixture.exchangeCode(await fixture.signIn(fixture.nextUser(), {}))).body;

    const accessToken = await fixture.introspect(tokens.access_token);
    const refreshToken = await fixture.introspect(tokens.refresh_token, 'refresh_token');

    expect(accessToken.active).toBe(true);
    expect(accessToken).not.toHaveProperty('authorization_details');
    expect(refreshToken.active).toBe(true);
    expect(refreshToken).not.toHaveProperty('authorization_details');
  });
});

describe('Introspection - authorization_code grant', () => {
  it(jira`should return the details of the token response for the access token ${'AM-8086'}`, async () => {
    const details = [payment(), account({ actions: ['read', 'list_accounts'], locations: ['https://bank.example.com'] })];
    const tokens = (await fixture.exchangeCode(await fixture.approve(fixture.nextUser(), details))).body;

    const body = await fixture.introspect(tokens.access_token);

    expect(body.active).toBe(true);
    expect(body.authorization_details).toEqual(tokens.authorization_details);
    expect(body.authorization_details).toEqual(details);
  });

  it(jira`should return the granted details when the token request repeats them ${'AM-8086'}`, async () => {
    const details = [payment('EUR', '9.99'), account()];
    const callback = await fixture.approve(fixture.nextUser(), details);
    const response = await fixture.redeemCode(callback, { authorization_details: toDetailsParam(details[1], details[0]) });
    expect(response.status).toBe(200);

    const body = await fixture.introspect(response.body.access_token);

    expect(body.authorization_details).toEqual(response.body.authorization_details);
    expect(body.authorization_details).toEqual(details);
  });

  it(jira`should return the granted details for the refresh token, with and without a hint ${'AM-8086'}`, async () => {
    const details = [account({ actions: ['read'] })];
    const tokens = (await fixture.exchangeCode(await fixture.approve(fixture.nextUser(), details))).body;

    const withHint = await fixture.introspect(tokens.refresh_token, 'refresh_token');
    const withoutHint = await fixture.introspect(tokens.refresh_token);

    expect(withHint.active).toBe(true);
    expect(withHint.authorization_details).toEqual(details);
    expect(withoutHint.active).toBe(true);
    expect(withoutHint.authorization_details).toEqual(details);
  });
});

describe('Introspection - refresh_token grant', () => {
  it(jira`should return the granted details for the tokens of each refresh with rotation ${'AM-8086'}`, async () => {
    const details = [payment('GBP', '42.00')];
    let tokens = (await fixture.exchangeCode(await fixture.approve(fixture.nextUser(), details))).body;

    for (let refresh = 0; refresh < 2; refresh++) {
      const response = await fixture.refresh(tokens.refresh_token);
      expect(response.status).toBe(200);
      expect(response.body.refresh_token).not.toEqual(tokens.refresh_token);
      tokens = response.body;

      expect((await fixture.introspect(tokens.access_token)).authorization_details).toEqual(tokens.authorization_details);
      expect((await fixture.introspect(tokens.refresh_token, 'refresh_token')).authorization_details).toEqual(details);
    }
  });
});

describe('Introspection - authorization endpoint', () => {
  it(jira`should return the approved details for an implicit access token ${'AM-8086'}`, async () => {
    const details = [account({ actions: ['list_accounts'] })];
    const params = callbackParams(await fixture.approve(fixture.nextUser(), details, { response_type: 'token' }), '#');

    const body = await fixture.introspect(params.get('access_token'));

    expect(body.active).toBe(true);
    expect(body.authorization_details).toEqual(details);
  });

  it(jira`should return the approved details for a hybrid access token ${'AM-8086'}`, async () => {
    const details = [payment('USD', '5.00')];
    const callback = await fixture.approve(fixture.nextUser(), details, { response_type: 'code token', nonce: 'authz-details-nonce' });
    const params = callbackParams(callback, '#');

    const body = await fixture.introspect(params.get('access_token'));

    expect(body.active).toBe(true);
    expect(body.authorization_details).toEqual(details);
  });
});

describe('Introspection - client_credentials grant', () => {
  it(jira`should return the requested details and no username ${'AM-8086'}`, async () => {
    const details = [account({ actions: ['list_accounts'] }), payment('GBP', '7.50')];
    const response = await fixture.requestClientCredentialsToken({ authorization_details: toDetailsParam(...details) });
    expect(response.status).toBe(200);

    const body = await fixture.introspect(response.body.access_token);

    expect(body.active).toBe(true);
    expect(body.authorization_details).toEqual(response.body.authorization_details);
    expect(body.authorization_details).toEqual(details);
    expect(body.sub).toEqual(fixture.clientId);
    expect(body).not.toHaveProperty('username');
  });
});

describe('Introspection - revoked tokens', () => {
  it(jira`should report a revoked access token as inactive, without authorization_details ${'AM-8089'}`, async () => {
    const tokens = (await fixture.exchangeCode(await fixture.approve(fixture.nextUser(), [payment()]))).body;
    expect((await fixture.introspect(tokens.access_token)).active).toBe(true);

    await fixture.revoke(tokens.access_token, 'access_token');

    expect(await fixture.introspect(tokens.access_token)).toEqual({ active: false });
  });

  it(jira`should report a revoked refresh token as inactive, without authorization_details ${'AM-8089'}`, async () => {
    const tokens = (await fixture.exchangeCode(await fixture.approve(fixture.nextUser(), [account()]))).body;
    expect((await fixture.introspect(tokens.refresh_token, 'refresh_token')).active).toBe(true);

    await fixture.revoke(tokens.refresh_token, 'refresh_token');

    expect(await fixture.introspect(tokens.refresh_token, 'refresh_token')).toEqual({ active: false });
  });
});
