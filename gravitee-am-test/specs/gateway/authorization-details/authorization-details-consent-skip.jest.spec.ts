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
import { isDeepStrictEqual } from 'util';
import { jira } from '@specs-utils/jira';
import { setup } from '../../test-fixture';
import { toDetailsParam } from './fixtures/authorization-details-fixture';
import {
  account,
  ConsentFixture,
  expectCode,
  follow,
  isConsentPage,
  openConsentPage,
  recordedConsent,
  setupConsentFixture,
  submitConsent,
} from './fixtures/authorization-details-consent-fixture';

// The application's skip user approval setting (advanced.skipConsent) also skips the authorization detail screen

setup(200000);

let fixture: ConsentFixture;

beforeAll(async () => {
  fixture = await setupConsentFixture({ skipConsent: true });
});

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

describe('Authorization detail consent - skip user approval', () => {
  it(jira`should not show the screen ${'AM-8059'}`, async () => {
    const signedIn = await fixture.signIn(fixture.nextUser(), {
      authorization_details: toDetailsParam(account({ actions: ['list_accounts'] }), account()),
    });

    expectCode(signedIn);
  });

  it(jira`should not record a consent ${'AM-8059'}`, async () => {
    const knownIds = await fixture.consentAuditIds();
    const user = fixture.nextUser();
    const skipped = await fixture.signIn(user, { authorization_details: toDetailsParam(account()) });
    expectCode(skipped);
    // a consent to other details, recorded afterwards, shows the audits caught up with the skipped request
    const promptedDetails = [account({ actions: ['read'] })];
    const prompted = await fixture.authorize(
      { prompt: 'consent', authorization_details: toDetailsParam(...promptedDetails) },
      skipped.cookie,
    );
    await follow(await submitConsent(await openConsentPage(prompted), true));

    const promptedConsent = [{ authorization_details: promptedDetails, status: 'APPROVED' }];
    const audits = await fixture.newConsentAudits(knownIds, (found) =>
      found.some((audit) => isDeepStrictEqual(recordedConsent(audit), promptedConsent)),
    );

    expect(audits.map((audit) => recordedConsent(audit))).toEqual([promptedConsent]);
  });

  it(jira`should show the screen with prompt=consent ${'AM-8059'}`, async () => {
    const signedIn = await fixture.signIn(fixture.nextUser(), { prompt: 'consent', authorization_details: toDetailsParam(account()) });

    expect((await openConsentPage(signedIn)).kind).toEqual('authorization-details');
  });

  it(jira`should issue a code with prompt=none ${'AM-8059'}`, async () => {
    const signedIn = await fixture.signIn(fixture.nextUser(), { authorization_details: toDetailsParam(account()) });

    const again = await fixture.authorize(
      { prompt: 'none', authorization_details: toDetailsParam(account({ actions: ['read'] })) },
      signedIn.cookie,
    );

    expectCode(again);
  });

  it(jira`should show the screen once the setting is turned off ${'AM-8059'}`, async () => {
    const user = fixture.nextUser();
    const skipped = await fixture.signIn(user, { authorization_details: toDetailsParam(account()) });
    expectCode(skipped);
    await fixture.setSkipConsent(false);
    try {
      const again = await fixture.authorize({ authorization_details: toDetailsParam(account()) }, skipped.cookie);

      expect(isConsentPage(again)).toBe(true);
    } finally {
      await fixture.setSkipConsent(true);
    }
  });
});
