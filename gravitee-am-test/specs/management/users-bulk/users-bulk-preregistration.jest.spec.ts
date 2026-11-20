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
import { countEmailsFor, waitForEmails } from '@utils-commands/email-commands';
import { setupUsersBulkFixture, UsersBulkFixture } from './fixtures/users-bulk-preregistration-fixture';
import { setup } from '../../test-fixture';

setup(200000);

let fixture: UsersBulkFixture;

beforeAll(async () => {
  fixture = await setupUsersBulkFixture();
});

afterAll(async () => {
  if (fixture) await fixture.cleanup();
});

describe('Management API users bulk create - pre-registration', () => {
  it('emails every pre-registered user once the bulk is processed', async () => {
    const candidates = [fixture.candidate(true), fixture.candidate(true), fixture.candidate(true)];

    const results = await fixture.bulkCreate(candidates);

    for (const result of results) {
      expect(await fixture.readUser(result.body['id'])).toMatchObject({ preRegistration: true, registrationCompleted: false });
    }
    const emails = await waitForEmails(candidates.map((c) => c.email));
    emails.forEach((email) => expect(email.extractLink()).toContain('token='));
    for (const c of candidates) {
      expect(await countEmailsFor(c.email)).toBe(1);
    }
  });

  it('emails only the pre-registered users when the request is mixed', async () => {
    const preRegistered = [fixture.candidate(true), fixture.candidate(true)];
    const plain = [fixture.candidate(false), fixture.candidate(false)];

    await fixture.bulkCreate([preRegistered[0], plain[0], preRegistered[1], plain[1]]);

    // Once the pre-registered emails land, the staging processor has run for this domain,
    // so the absence of the others is a result rather than a race.
    await waitForEmails(preRegistered.map((c) => c.email));
    for (const c of plain) {
      expect(await countEmailsFor(c.email)).toBe(0);
    }
  });
});
