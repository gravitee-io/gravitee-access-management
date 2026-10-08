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
import { auditIdsOfType, waitForNewAudit } from '@management-commands/audit-management-commands';
import { retryUntil } from '@utils-commands/retry';
import { jira } from '@specs-utils/jira';
import { MagicLinkGatewayFixture, setupFixture } from './fixture/magic-link-gateway-fixture';
import { setup } from '../../test-fixture';

setup(200000);

const REQUESTED = 'MAGIC_LINK_REQUESTED';
const EMAIL_SENT = 'MAGIC_LINK_EMAIL_SENT';
const LOGIN = 'USER_MAGIC_LINK_LOGIN';

let fixture: MagicLinkGatewayFixture;

beforeAll(async () => {
  fixture = await setupFixture('magic-link-audits');
});

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

const messageOf = (audit: any) => audit.outcome?.message ?? '';

/** Ids of the audits already on the domain, so a test only ever asserts on one it caused. */
const baseline = (type: string) => auditIdsOfType(fixture.domain.id, fixture.accessToken, type);

describe('Magic Link audits - requesting a link', () => {
  it(jira`records a success for an email that matches a user ${'AM-6722'}`, async () => {
    const known = await baseline(REQUESTED);

    await fixture.requestLink();

    const audit = await waitForNewAudit(fixture.domain.id, fixture.accessToken, REQUESTED, 'SUCCESS', known);
    expect(audit.type).toEqual(REQUESTED);
  });

  it(jira`records that the email was sent ${'AM-6722'}`, async () => {
    const known = await baseline(EMAIL_SENT);

    await fixture.requestLink();

    const audit = await waitForNewAudit(fixture.domain.id, fixture.accessToken, EMAIL_SENT, 'SUCCESS', known);
    expect(audit.type).toEqual(EMAIL_SENT);
  });

  it(jira`records a failure naming the unknown email ${'AM-6722'}`, async () => {
    const unknown = 'no-such-user@test.com';
    const known = await baseline(REQUESTED);

    await fixture.requestLink(unknown);

    const audit = await waitForNewAudit(fixture.domain.id, fixture.accessToken, REQUESTED, 'FAILURE', known);
    // The reason has to name the address, otherwise this audit cannot be told apart from the
    // invalid-format failure below.
    expect(messageOf(audit)).toContain(unknown);
  });

  it(jira`records a failure for an invalid email format ${'AM-6722'}`, async () => {
    const known = await baseline(REQUESTED);

    await fixture.requestLink('notanemail');

    const audit = await waitForNewAudit(fixture.domain.id, fixture.accessToken, REQUESTED, 'FAILURE', known);
    expect(messageOf(audit)).toContain('notanemail');
    expect(messageOf(audit)).toContain('not a valid email');
  });
});

describe('Magic Link audits - using a link', () => {
  it(jira`records a success when the link authenticates ${'AM-6732'}`, async () => {
    const request = await fixture.requestLink();
    const link = await fixture.lastLink();
    const known = await baseline(LOGIN);

    await fixture.useLink(link, request.cookie);

    const audit = await waitForNewAudit(fixture.domain.id, fixture.accessToken, LOGIN, 'SUCCESS', known);
    expect(audit.type).toEqual(LOGIN);
  });

  it(jira`records a failure when the link is reused ${'AM-6732'}`, async () => {
    const request = await fixture.requestLink();
    const link = await fixture.lastLink();
    await fixture.completeLogin(link, request.cookie);

    // A link is only treated as used once the login has been written against the user, and that
    // write is asynchronous. Polling the behaviour rather than a proxy for it: retry until the
    // gateway actually refuses the link, then assert on the audit that refusal produced.
    const known = await baseline(LOGIN);
    const refusal = await retryUntil(
      async () => fixture.useLink(link, request.cookie),
      (response) => String(response.text ?? '').includes('token_already_used'),
      { timeoutMillis: 30000, intervalMillis: 1000 },
    );
    expect(String(refusal.text)).toContain('Magic link has already been used');

    const audit = await waitForNewAudit(fixture.domain.id, fixture.accessToken, LOGIN, 'FAILURE', known);
    expect(messageOf(audit)).toContain('already been used');
  });

  it(jira`records a failure when the link is opened in another session ${'AM-6732'}`, async () => {
    await fixture.requestLink();
    const link = await fixture.lastLink();
    const known = await baseline(LOGIN);

    // Sending no cookie is the API-level equivalent of opening the link in a different browser.
    await fixture.useLink(link);

    const audit = await waitForNewAudit(fixture.domain.id, fixture.accessToken, LOGIN, 'FAILURE', known);
    expect(messageOf(audit)).toContain('different session');
  });
});
