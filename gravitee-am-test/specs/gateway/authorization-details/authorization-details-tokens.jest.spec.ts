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
  callbackParams,
  CONSENT_TEST,
  ConsentFixture,
  expectCode,
  payment,
  setupConsentFixture,
} from './fixtures/authorization-details-consent-fixture';

// RFC 9396 §7 and §9.1: the granted authorization_details reach the token response and the access token

setup(200000);

let fixture: ConsentFixture;

beforeAll(async () => {
  fixture = await setupConsentFixture();
});

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

describe('Authorization details in tokens - authorization_code grant', () => {
  it(jira`should issue tokens without authorization_details when none were requested ${'AM-8064'}`, async () => {
    const callback = await fixture.signIn(fixture.nextUser(), {});
    expectCode(callback);

    const tokens = (await fixture.exchangeCode(callback)).body;

    expect(tokens).not.toHaveProperty('authorization_details');
    expect(decodeJwt(tokens.access_token)).not.toHaveProperty('authorization_details');
  });

  it(jira`should include the approved details in the token response and the access token ${'AM-8064'}`, async () => {
    const details = [payment(), account({ actions: ['read'] })];
    const callback = await fixture.approve(fixture.nextUser(), details);
    expectCode(callback);

    const tokens = (await fixture.exchangeCode(callback)).body;

    expect(tokens.authorization_details).toEqual(details);
    expect(decodeJwt(tokens.access_token).authorization_details).toEqual(details);
  });

  it(jira`should include remembered details when the user is not asked again ${'AM-8064'}`, async () => {
    const details = [payment('EUR', '10.00')];
    const session = await fixture.approve(fixture.nextUser(), details);
    await fixture.exchangeCode(session);
    const again = await fixture.authorize({ authorization_details: toDetailsParam(...details) }, session.cookie);
    expectCode(again);

    const tokens = (await fixture.exchangeCode(again)).body;

    expect(tokens.authorization_details).toEqual(details);
    expect(decodeJwt(tokens.access_token).authorization_details).toEqual(details);
  });
});

describe('Authorization details in tokens - refresh_token grant', () => {
  it(jira`should keep the granted details across refreshes with rotation ${'AM-8065'}`, async () => {
    const details = [payment('GBP', '42.00')];
    let tokens = (await fixture.exchangeCode(await fixture.approve(fixture.nextUser(), details))).body;

    for (let refresh = 0; refresh < 2; refresh++) {
      const response = await fixture.refresh(tokens.refresh_token);
      expect(response.status).toBe(200);
      expect(response.body.refresh_token).not.toEqual(tokens.refresh_token);
      tokens = response.body;

      expect(tokens.authorization_details).toEqual(details);
      expect(decodeJwt(tokens.access_token).authorization_details).toEqual(details);
    }
  });

  it(jira`should refresh without authorization_details when none were granted ${'AM-8065'}`, async () => {
    const callback = await fixture.signIn(fixture.nextUser(), {});
    const { refresh_token } = (await fixture.exchangeCode(callback)).body;

    const response = await fixture.refresh(refresh_token);

    expect(response.status).toBe(200);
    expect(response.body).not.toHaveProperty('authorization_details');
    expect(decodeJwt(response.body.access_token)).not.toHaveProperty('authorization_details');
  });
});

describe('Authorization details in tokens - authorization endpoint', () => {
  it(jira`should include the approved details in an implicit access token, not in the fragment ${'AM-8066'}`, async () => {
    const details = [account({ actions: ['list_accounts'] })];

    const params = callbackParams(await fixture.approve(fixture.nextUser(), details, { response_type: 'token' }), '#');

    expect(params.has('authorization_details')).toBe(false);
    expect(decodeJwt(params.get('access_token')).authorization_details).toEqual(details);
  });

  it(jira`should include the approved details in a hybrid access token and in the tokens for its code ${'AM-8066'}`, async () => {
    const details = [payment('USD', '5.00')];
    const callback = await fixture.approve(fixture.nextUser(), details, { response_type: 'code token', nonce: 'authz-details-nonce' });
    const params = callbackParams(callback, '#');

    expect(params.has('authorization_details')).toBe(false);
    expect(decodeJwt(params.get('access_token')).authorization_details).toEqual(details);
    const response = await fixture.requestToken({
      grant_type: 'authorization_code',
      code: params.get('code'),
      redirect_uri: CONSENT_TEST.REDIRECT_URI,
    });
    expect(response.status).toBe(200);
    expect(response.body.authorization_details).toEqual(details);
  });
});
