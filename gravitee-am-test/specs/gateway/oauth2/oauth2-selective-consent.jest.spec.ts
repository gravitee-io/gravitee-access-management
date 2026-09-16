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
import { afterAll, beforeAll, beforeEach, describe, expect, it } from '@jest/globals';
import { setup } from '../../test-fixture';
import {
  ALL_SCOPES,
  SELECTIVE_CONSENT,
  SelectiveConsentFixture,
  UserSession,
  authorize,
  consentPageUrl,
  exchangeCodeForScopes,
  expectAccessDenied,
  expectAuthorizationCode,
  grantInitialConsent,
  openConsentPage,
  setupSelectiveConsentFixture,
  signIn,
  submitConsent,
} from './fixture/oauth2-selective-consent-fixture';

setup(200000);

/**
 * Selective scope consent on the authorization_code flow: the user approves a subset of the requested
 * scopes, and on later requests is only asked about scopes not approved yet.
 */
const { REQUIRED_SCOPE } = SELECTIVE_CONSENT;
const [ORDERS, PAYMENTS, REPORTS] = SELECTIVE_CONSENT.OPTIONAL_SCOPES;

let fixture: SelectiveConsentFixture;

beforeAll(async () => {
  fixture = await setupSelectiveConsentFixture();
});

afterAll(async () => {
  await fixture?.cleanUp();
});

describe('OAuth2 - selective consent', () => {
  it('should issue an access token limited to the approved subset of requested scopes', async () => {
    const { session, postLoginRedirect } = await signIn(fixture, fixture.nextUser(), ALL_SCOPES);
    expect(postLoginRedirect.headers['location']).toContain(consentPageUrl(fixture));

    const page = await openConsentPage(session, postLoginRedirect);
    expect(page.presentedScopes.sort()).toEqual([...ALL_SCOPES].sort());

    const { callback } = await submitConsent(page, [REQUIRED_SCOPE, ORDERS]);
    expectAuthorizationCode(callback);
    expect(await exchangeCodeForScopes(fixture, callback)).toEqual([REQUIRED_SCOPE, ORDERS].sort());
  });

  it('should reject an empty selection on a first request, when no scope has been approved yet', async () => {
    const { session, postLoginRedirect } = await signIn(fixture, fixture.nextUser(), [ORDERS, PAYMENTS]);
    const page = await openConsentPage(session, postLoginRedirect);
    expect(page.allowEmpty).toEqual('false');

    const { callback } = await submitConsent(page, []);
    expectAccessDenied(callback);
  });
});

describe('OAuth2 - selective consent on a repeat request', () => {
  let session: UserSession;

  beforeEach(async () => {
    session = await grantInitialConsent(fixture, fixture.nextUser(), ALL_SCOPES, [REQUIRED_SCOPE, ORDERS]);
  });

  it('should only present scopes that have not been approved yet, and allow an empty selection', async () => {
    const repeat = await authorize(fixture, ALL_SCOPES, session);
    expect(repeat.headers['location']).toContain(consentPageUrl(fixture));

    const page = await openConsentPage(session, repeat);
    expect(page.presentedScopes.sort()).toEqual([PAYMENTS, REPORTS].sort());
    expect(page.allowEmpty).toEqual('true');
  });

  it('should keep the earlier grant when the user approves without selecting any new scope', async () => {
    const repeat = await authorize(fixture, ALL_SCOPES, session);
    const page = await openConsentPage(session, repeat);

    const { callback } = await submitConsent(page, []);
    expectAuthorizationCode(callback);
    expect(await exchangeCodeForScopes(fixture, callback)).toEqual([REQUIRED_SCOPE, ORDERS].sort());
  });

  it('should add a newly approved optional scope to the earlier grant', async () => {
    const repeat = await authorize(fixture, ALL_SCOPES, session);
    const page = await openConsentPage(session, repeat);

    const { callback } = await submitConsent(page, [PAYMENTS]);
    expectAuthorizationCode(callback);
    expect(await exchangeCodeForScopes(fixture, callback)).toEqual([REQUIRED_SCOPE, ORDERS, PAYMENTS].sort());
  });

  it('should deny access when the user rejects the request', async () => {
    const repeat = await authorize(fixture, ALL_SCOPES, session);
    const page = await openConsentPage(session, repeat);

    const { callback } = await submitConsent(page, [], false);
    expectAccessDenied(callback);
  });
});
