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
import { waitForEmail } from '@utils-commands/email-commands';
import { delay } from '@utils-commands/misc';
import { retryUntil } from '@utils-commands/retry';
import { jira } from '@specs-utils/jira';
import { errorDescriptionOf, MagicLinkGatewayFixture, setupFixture, tokenIatMillis } from './fixture/magic-link-gateway-fixture';
import { setup } from '../../test-fixture';

setup(300000);

/** Short enough for a test to outlive a link, long enough to use one first. */
const SHORT_EXPIRY_SECONDS = 20;
/** The gateway's gravitee.yaml default, which the template is expected to override. */
const DEFAULT_EXPIRY_SECONDS = 900;

let fixture: MagicLinkGatewayFixture;

beforeAll(async () => {
  fixture = await setupFixture('magic-link-expiry');
});

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

const lifetimeOf = (link: string): number => {
  const token = new URL(link).searchParams.get('token');
  const payload = JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString());
  return payload.exp - payload.iat;
};

const bodyOf = (response: any) => response.text ?? JSON.stringify(response.body ?? '');

/**
 * Requests a link until the email arrives with the expected subject, and returns that subject.
 * Returns whatever the last attempt saw if it never settles, so the assertion reports the real
 * value rather than a timeout.
 */
const subjectOnceSettled = async (expected: string): Promise<string> => {
  let seen = '';
  try {
    await retryUntil(
      async () => {
        await fixture.requestLink();
        seen = (await waitForEmail(fixture.userEmail)).subject;
        return seen;
      },
      (subject) => subject === expected,
      { timeoutMillis: 60000, intervalMillis: 2000 },
    );
  } catch {
    // fall through and let the caller assert on `seen`
  }
  return seen;
};

describe('Magic Link - link expiry', () => {
  it(jira`takes its validity from the email template rather than the gateway default ${'AM-6720'}`, async () => {
    // Anchor on the default first, so the change below is attributable to the template.
    await fixture.requestLink();
    const beforeTemplate = await fixture.lastLink();
    expect(lifetimeOf(beforeTemplate)).toEqual(DEFAULT_EXPIRY_SECONDS);

    await fixture.setDomainTemplate({ expiresAfter: SHORT_EXPIRY_SECONDS });
    await fixture.waitForLinkLifetime(SHORT_EXPIRY_SECONDS);

    await fixture.requestLink();
    expect(lifetimeOf(await fixture.lastLink())).toEqual(SHORT_EXPIRY_SECONDS);
  });

  it(jira`rejects a link used after its validity window ${'AM-6718'}`, async () => {
    const request = await fixture.requestLink();
    const link = await fixture.lastLink();
    expect(lifetimeOf(link)).toEqual(SHORT_EXPIRY_SECONDS);

    // Wait out the window. There is nothing to poll for — the token simply becomes too old.
    await delay((SHORT_EXPIRY_SECONDS + 3) * 1000);
    const response = await fixture.useLink(link, request.cookie);

    expect(bodyOf(response) + String(errorDescriptionOf(response))).toContain('token_expired');
  });

  it(jira`authenticates with a fresh link requested after one expired ${'AM-6719'}`, async () => {
    const expiredRequest = await fixture.requestLink();
    const expiredLink = await fixture.lastLink();
    await delay((SHORT_EXPIRY_SECONDS + 3) * 1000);
    await fixture.useLink(expiredLink, expiredRequest.cookie);

    // Same session, new link, as the case describes.
    const freshRequest = await fixture.requestLink();
    const freshLink = await fixture.lastLink();
    const response = await fixture.completeLogin(freshLink, freshRequest.cookie);

    expect(response.headers['location']).toContain('code=');
    await fixture.waitForLoginRecorded(tokenIatMillis(freshLink));
  });
});

describe('Magic Link - which email template is used', () => {
  it(jira`uses the application template in preference to the domain one ${'AM-6738'}`, async () => {
    const domainSubject = 'Domain level magic link';
    const applicationSubject = 'Application level magic link';

    await fixture.setDomainTemplate({ expiresAfter: SHORT_EXPIRY_SECONDS, subject: domainSubject });
    // A template change has to reach the gateway before it affects the emails it sends.
    expect(await subjectOnceSettled(domainSubject)).toEqual(domainSubject);

    await fixture.setApplicationTemplate({ expiresAfter: SHORT_EXPIRY_SECONDS, subject: applicationSubject });

    expect(await subjectOnceSettled(applicationSubject)).toEqual(applicationSubject);
  });
});
