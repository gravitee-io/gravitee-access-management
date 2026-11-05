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
  follow,
  openConsentPage,
  payment,
  recordedConsent,
  setupConsentFixture,
  submitConsent,
} from './fixtures/authorization-details-consent-fixture';

// Each consent decision is audited on its own

setup(200000);

let fixture: ConsentFixture;

beforeAll(async () => {
  fixture = await setupConsentFixture();
});

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

describe('Authorization detail consent - audit', () => {
  it(jira`should audit an approval with the full authorization_details ${'AM-8060'}`, async () => {
    const details = [payment(), account()];
    const knownIds = await fixture.consentAuditIds();
    const signedIn = await fixture.signIn(fixture.nextUser(), { authorization_details: toDetailsParam(...details) });

    await follow(await submitConsent(await openConsentPage(signedIn), true));

    const [audit] = await fixture.newConsentAudits(knownIds, 1);
    expect(audit.outcome.status.toUpperCase()).toEqual('SUCCESS');
    expect(recordedConsent(audit)).toEqual([{ authorization_details: details, status: 'APPROVED' }]);
  });

  it(jira`should audit a rejection ${'AM-8060'}`, async () => {
    const knownIds = await fixture.consentAuditIds();
    const signedIn = await fixture.signIn(fixture.nextUser(), { authorization_details: toDetailsParam(account()) });

    await follow(await submitConsent(await openConsentPage(signedIn), false));

    const [audit] = await fixture.newConsentAudits(knownIds, 1);
    expect(recordedConsent(audit)).toEqual([{ authorization_details: [account()], status: 'DENIED' }]);
  });

  it(jira`should audit scope consent and authorization detail consent separately ${'AM-8060'}`, async () => {
    const knownIds = await fixture.consentAuditIds();
    const signedIn = await fixture.signIn(fixture.nextUser(), {
      scope: CONSENT_TEST.SCOPE,
      authorization_details: toDetailsParam(account()),
    });
    const scopesApproved = await submitConsent(await openConsentPage(signedIn), true, [CONSENT_TEST.SCOPE]);

    const [scopeAudit] = await fixture.newConsentAudits(knownIds, 1);
    expect(recordedConsent(scopeAudit)).toEqual([expect.objectContaining({ scope: CONSENT_TEST.SCOPE, status: 'APPROVED' })]);

    await follow(await submitConsent(await openConsentPage(scopesApproved), true));

    const [detailsAudit] = await fixture.newConsentAudits(new Set([...knownIds, scopeAudit.id]), 1);
    expect(recordedConsent(detailsAudit)).toEqual([{ authorization_details: [account()], status: 'APPROVED' }]);
  });
});
