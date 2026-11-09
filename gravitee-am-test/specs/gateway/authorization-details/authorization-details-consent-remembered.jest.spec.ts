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
import {
  account,
  ConsentFixture,
  expectCode,
  expectError,
  follow,
  isConsentPage,
  openConsentPage,
  payment,
  setupConsentFixture,
  Step,
  submitConsent,
} from './fixtures/authorization-details-consent-fixture';

// RFC 9396: an approval is remembered, matched exactly, for the user and the application

setup(200000);

let fixture: ConsentFixture;

beforeAll(async () => {
  fixture = await setupConsentFixture();
});

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

const authorizeAgain = (session: Step, params: Record<string, string>): Promise<Step> => fixture.authorize(params, session.cookie);

describe('Authorization detail consent - remembered consent', () => {
  it(jira`should not ask again for details the user approved ${'AM-8058'}`, async () => {
    const session = await fixture.approve(fixture.nextUser(), [payment(), account()]);

    const again = await authorizeAgain(session, { authorization_details: toDetailsParam(payment(), account()) });

    expectCode(again);
  });

  it(jira`should match approved details whatever their order and member order ${'AM-8058'}`, async () => {
    const session = await fixture.approve(fixture.nextUser(), [payment('EUR', '10.00'), account()]);

    const again = await authorizeAgain(session, {
      authorization_details: toDetailsParam(account(), {
        instructedAmount: { amount: '10.00', currency: 'EUR' },
        type: 'payment_initiation',
      }),
    });

    expectCode(again);
  });

  it(jira`should ask again for a detail that differs from the approved one ${'AM-8058'}`, async () => {
    const session = await fixture.approve(fixture.nextUser(), [account({ actions: ['read', 'write'] })]);

    const again = await authorizeAgain(session, { authorization_details: toDetailsParam(account({ actions: ['write', 'read'] })) });

    expect(isConsentPage(again)).toBe(true);
  });

  it(jira`should only show the details not approved yet, and grant every requested detail ${'AM-8058'}`, async () => {
    const session = await fixture.approve(fixture.nextUser(), [payment()]);
    const again = await authorizeAgain(session, { authorization_details: toDetailsParam(payment(), account()) });

    const page = await openConsentPage(again);
    expect(page.details.map((detail) => detail.name)).toEqual(['Account information']);

    expectCode(await follow(await submitConsent(page, true)));
  });

  it(jira`should ask again for a detail the user rejected ${'AM-8058'}`, async () => {
    const signedIn = await fixture.signIn(fixture.nextUser(), { authorization_details: toDetailsParam(account()) });
    const rejected = await follow(await submitConsent(await openConsentPage(signedIn), false));
    expectError(rejected, 'access_denied');

    const again = await authorizeAgain(rejected, { authorization_details: toDetailsParam(account()) });

    expect(isConsentPage(again)).toBe(true);
  });

  it(jira`should ask again for an approved detail the user rejected later ${'AM-8058'}`, async () => {
    const session = await fixture.approve(fixture.nextUser(), [account()]);
    const prompted = await authorizeAgain(session, { prompt: 'consent', authorization_details: toDetailsParam(account()) });
    const rejected = await follow(await submitConsent(await openConsentPage(prompted), false));
    expectError(rejected, 'access_denied');

    const again = await authorizeAgain(rejected, { authorization_details: toDetailsParam(account()) });

    expect(isConsentPage(again)).toBe(true);
  });
});

describe('Authorization detail consent - prompt', () => {
  it(jira`should show every requested detail with prompt=consent, even approved ones ${'AM-8058'}`, async () => {
    const session = await fixture.approve(fixture.nextUser(), [payment()]);

    const again = await authorizeAgain(session, { prompt: 'consent', authorization_details: toDetailsParam(payment(), account()) });

    const page = await openConsentPage(again);
    expect(page.details.map((detail) => detail.name)).toEqual(['Payment initiation', 'Account information']);
  });

  it(jira`should require interaction with prompt=none when details are not approved yet ${'AM-8058'}`, async () => {
    const session = await fixture.approve(fixture.nextUser(), [payment()]);

    const again = await authorizeAgain(session, { prompt: 'none', authorization_details: toDetailsParam(account()) });

    expectError(again, 'interaction_required');
  });

  it(jira`should issue a code with prompt=none when every detail is approved ${'AM-8058'}`, async () => {
    const session = await fixture.approve(fixture.nextUser(), [payment()]);

    const again = await authorizeAgain(session, { prompt: 'none', authorization_details: toDetailsParam(payment()) });

    expectCode(again);
  });
});
