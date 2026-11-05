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
  CONSENT_TEST,
  ConsentFixture,
  expectCode,
  expectError,
  follow,
  isConsentPage,
  openConsentPage,
  payment,
  setupConsentFixture,
  submitConsent,
  withAuthorizationDetails,
} from './fixtures/authorization-details-consent-fixture';

// RFC 9396: the user approves or rejects the requested authorization_details as a whole

setup(200000);

let fixture: ConsentFixture;

beforeAll(async () => {
  fixture = await setupConsentFixture();
});

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

describe('Authorization detail consent - showing the screen', () => {
  it(jira`should only show the scope screen for a request without authorization_details ${'AM-8056'}`, async () => {
    const signedIn = await fixture.signIn(fixture.nextUser(), { scope: CONSENT_TEST.SCOPE });

    const scopePage = await openConsentPage(signedIn);
    expect(scopePage.kind).toEqual('scopes');
    const submitted = await submitConsent(scopePage, true, [CONSENT_TEST.SCOPE]);

    expectCode(await follow(submitted));
  });

  it(jira`should show the detail screen after login when the request has no scope ${'AM-8056'}`, async () => {
    const signedIn = await fixture.signIn(fixture.nextUser(), { authorization_details: toDetailsParam(account()) });

    const page = await openConsentPage(signedIn);

    expect(page.kind).toEqual('authorization-details');
  });

  it(jira`should show the detail screen right after the scope screen ${'AM-8056'}`, async () => {
    const signedIn = await fixture.signIn(fixture.nextUser(), {
      scope: CONSENT_TEST.SCOPE,
      authorization_details: toDetailsParam(account()),
    });
    const scopePage = await openConsentPage(signedIn);
    expect(scopePage.kind).toEqual('scopes');

    const submitted = await submitConsent(scopePage, true, [CONSENT_TEST.SCOPE]);

    expect(isConsentPage(submitted)).toBe(true);
    expect((await openConsentPage(submitted)).kind).toEqual('authorization-details');
  });

  it('should ask for consent again in a new request after the detail screen was abandoned', async () => {
    const signedIn = await fixture.signIn(fixture.nextUser(), {
      scope: CONSENT_TEST.SCOPE,
      authorization_details: toDetailsParam(account()),
    });
    const abandoned = await submitConsent(await openConsentPage(signedIn), true, [CONSENT_TEST.SCOPE]);
    expect(isConsentPage(abandoned)).toBe(true);

    const again = await fixture.authorize(
      { state: 'another-request', prompt: 'consent', scope: CONSENT_TEST.SCOPE, authorization_details: toDetailsParam(account()) },
      abandoned.cookie,
    );

    expect((await openConsentPage(again)).kind).toEqual('scopes');
  });

  it(jira`should show the detail screen when the user already approved every scope ${'AM-8056'}`, async () => {
    const user = fixture.nextUser();
    const first = await fixture.signIn(user, { scope: CONSENT_TEST.SCOPE });
    const approvedScopes = await follow(await submitConsent(await openConsentPage(first), true, [CONSENT_TEST.SCOPE]));
    expectCode(approvedScopes);

    const second = await fixture.authorize(
      { scope: CONSENT_TEST.SCOPE, authorization_details: toDetailsParam(account()) },
      approvedScopes.cookie,
    );

    expect((await openConsentPage(second)).kind).toEqual('authorization-details');
  });

  it(jira`should show every requested detail with its type name and other fields ${'AM-8056'}`, async () => {
    const signedIn = await fixture.signIn(fixture.nextUser(), {
      authorization_details: toDetailsParam(payment('EUR', '10.00'), account({ actions: ['list_accounts'] })),
    });

    const page = await openConsentPage(signedIn);

    expect(page.details).toEqual([
      { name: 'Payment initiation', description: 'Initiate a payment from your account', fields: ['instructedAmount'] },
      { name: 'Account information', description: '', fields: ['actions'] },
    ]);
  });

  it(jira`should offer one choice for the whole set ${'AM-8056'}`, async () => {
    const signedIn = await fixture.signIn(fixture.nextUser(), { authorization_details: toDetailsParam(payment(), account()) });

    const page = await openConsentPage(signedIn);

    expect([...new Set(page.fieldNames)].sort()).toEqual(['X-XSRF-TOKEN', 'user_oauth_approval']);
  });
});

describe('Authorization detail consent - approving and rejecting', () => {
  it(jira`should issue a code once the user approves ${'AM-8057'}`, async () => {
    const signedIn = await fixture.signIn(fixture.nextUser(), { authorization_details: toDetailsParam(payment(), account()) });

    const submitted = await submitConsent(await openConsentPage(signedIn), true);

    expectCode(await follow(submitted));
  });

  it(jira`should deny access when the user rejects ${'AM-8057'}`, async () => {
    const signedIn = await fixture.signIn(fixture.nextUser(), { authorization_details: toDetailsParam(account()) });

    const submitted = await submitConsent(await openConsentPage(signedIn), false);

    expectError(await follow(submitted), 'access_denied');
  });

  it(jira`should deny access in the fragment when the user rejects an implicit request ${'AM-8057'}`, async () => {
    const signedIn = await fixture.signIn(fixture.nextUser(), { response_type: 'token', authorization_details: toDetailsParam(account()) });

    const submitted = await submitConsent(await openConsentPage(signedIn), false);

    expectError(await follow(submitted), 'access_denied', '#');
  });

  it(jira`should deny access when the user rejects the details after approving the scopes ${'AM-8057'}`, async () => {
    const signedIn = await fixture.signIn(fixture.nextUser(), {
      scope: CONSENT_TEST.SCOPE,
      authorization_details: toDetailsParam(account()),
    });
    const scopesApproved = await submitConsent(await openConsentPage(signedIn), true, [CONSENT_TEST.SCOPE]);

    const submitted = await submitConsent(await openConsentPage(scopesApproved), false);

    expectError(await follow(submitted), 'access_denied');
  });

  it(jira`should not show the detail screen when the user denies the scopes ${'AM-8057'}`, async () => {
    const signedIn = await fixture.signIn(fixture.nextUser(), {
      scope: CONSENT_TEST.SCOPE,
      authorization_details: toDetailsParam(account()),
    });

    const submitted = await submitConsent(await openConsentPage(signedIn), false);

    expect(isConsentPage(submitted)).toBe(false);
    expectError(await follow(submitted), 'access_denied');
  });

  it(jira`should deny access when the submitted details differ from those presented ${'AM-8057'}`, async () => {
    const signedIn = await fixture.signIn(fixture.nextUser(), { authorization_details: toDetailsParam(account()) });
    const page = await openConsentPage(signedIn);

    const submitted = await submitConsent(page, true, [], withAuthorizationDetails(page.action, toDetailsParam(payment())));

    expectError(await follow(submitted), 'access_denied');
  });

  it(jira`should deny access when the details change after approval ${'AM-8057'}`, async () => {
    const signedIn = await fixture.signIn(fixture.nextUser(), { authorization_details: toDetailsParam(account()) });
    const submitted = await submitConsent(await openConsentPage(signedIn), true);

    const replayed = await follow({
      response: { headers: { location: withAuthorizationDetails(submitted.response.headers['location'], toDetailsParam(payment())) } },
      cookie: submitted.cookie,
    });

    expectError(replayed, 'access_denied');
  });
});
