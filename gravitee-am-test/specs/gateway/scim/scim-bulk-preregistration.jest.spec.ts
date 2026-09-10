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
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { getUser } from '@management-commands/user-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { clearEmails, countEmailsFor, waitForEmails } from '@utils-commands/email-commands';
import { ScimFixture, setupFixture } from './fixture/scim-fixture';
import { setup } from '../../test-fixture';

setup(200000);

const SCHEMA_BULK_REQUEST = 'urn:ietf:params:scim:api:messages:2.0:BulkRequest';
const SCHEMA_USER = 'urn:ietf:params:scim:schemas:core:2.0:User';
const SCHEMA_CUSTOM_USER = 'urn:ietf:params:scim:schemas:extension:custom:2.0:User';

let fixture: ScimFixture;

beforeAll(async () => {
  fixture = await setupFixture('scim-bulk-prereg');
});

afterAll(async () => {
  if (fixture) await fixture.cleanup();
});

type Candidate = { userName: string; email: string; preRegistration: boolean };

const candidate = (preRegistration: boolean): Candidate => {
  const userName = uniqueName('prereg', true);
  return { userName, email: `${userName}@user.com`, preRegistration };
};

const operationFor = (c: Candidate) => ({
  method: 'POST',
  path: '/Users',
  bulkId: uniqueName('bulk', true),
  data: {
    schemas: [SCHEMA_USER, SCHEMA_CUSTOM_USER],
    userName: c.userName,
    emails: [{ value: c.email, type: 'work', primary: true }],
    [SCHEMA_CUSTOM_USER]: { preRegistration: c.preRegistration },
  },
});

/** Sends one bulk request creating every candidate, and asserts each operation returned 201. */
async function bulkCreate(candidates: Candidate[]) {
  await Promise.all(candidates.map((c) => clearEmails(c.email)));

  const response = await performPost(
    fixture.scimEndpoint,
    '/Bulk',
    JSON.stringify({ schemas: [SCHEMA_BULK_REQUEST], Operations: candidates.map(operationFor) }),
    { 'Content-type': 'application/json', Authorization: `Bearer ${fixture.scimAccessToken}` },
  ).expect(200);

  expect(response.body.Operations).toHaveLength(candidates.length);
  response.body.Operations.forEach((op) => expect(op.status).toEqual('201'));
  return response.body.Operations;
}

/**
 * Reads the created user through the management API. The SCIM representation does not carry
 * `enabled`, and a bulk operation returns only a location rather than the created body.
 */
async function readUser(location: string) {
  return getUser(fixture.domain.id, fixture.accessToken, location.substring(location.lastIndexOf('/') + 1));
}

/*
 * `enabled` is false for every user created here, pre-registered or not, because none is given a
 * password — so it does not distinguish the two and is not asserted. `preRegistration` on the user
 * and the registration email are what actually differ.
 */
describe('SCIM Bulk - pre-registration', () => {
  it('marks every user for pre-registration and emails each one when all are pre-registered', async () => {
    const candidates = [candidate(true), candidate(true), candidate(true)];

    const operations = await bulkCreate(candidates);

    for (const op of operations) {
      expect(await readUser(op.location)).toMatchObject({ preRegistration: true, registrationCompleted: false });
    }
    // One registration email per user, each carrying its own confirmation link.
    const emails = await waitForEmails(candidates.map((c) => c.email));
    emails.forEach((email) => expect(email.extractLink()).toContain('token='));
  });

  it('sends no email when no user is pre-registered', async () => {
    const candidates = [candidate(false), candidate(false), candidate(false)];

    const operations = await bulkCreate(candidates);

    for (const op of operations) {
      expect(await readUser(op.location)).toMatchObject({ preRegistration: false });
    }
    // A pre-registered user in the same domain is the control: once its email lands, the staging
    // processor has run, so the absence of the others is a result rather than a race.
    const control = candidate(true);
    await bulkCreate([control]);
    await waitForEmails([control.email]);

    for (const c of candidates) {
      expect(await countEmailsFor(c.email)).toBe(0);
    }
  });

  it('lets every pre-registered user complete registration from their own email', async () => {
    const candidates = [candidate(true), candidate(true), candidate(true)];

    const operations = await bulkCreate(candidates);
    const emails = await waitForEmails(candidates.map((c) => c.email));

    // Each link must complete registration for its own user, so a bulk request cannot hand out
    // links that resolve to the same account.
    for (let i = 0; i < candidates.length; i++) {
      const confirmation = await fixture.confirmRegistrationLink(emails[i].extractLink());
      expect(confirmation.headers['location']).toContain('success=registration_completed');

      expect(await readUser(operations[i].location)).toMatchObject({ enabled: true, registrationCompleted: true });
    }
  });

  it('emails only the pre-registered users when the request is mixed', async () => {
    const preRegistered = [candidate(true), candidate(true)];
    const plain = [candidate(false), candidate(false)];

    const operations = await bulkCreate([preRegistered[0], plain[0], preRegistered[1], plain[1]]);

    expect(await readUser(operations[0].location)).toMatchObject({ preRegistration: true });
    expect(await readUser(operations[1].location)).toMatchObject({ preRegistration: false });
    expect(await readUser(operations[2].location)).toMatchObject({ preRegistration: true });
    expect(await readUser(operations[3].location)).toMatchObject({ preRegistration: false });

    await waitForEmails(preRegistered.map((c) => c.email));
    for (const c of plain) {
      expect(await countEmailsFor(c.email)).toBe(0);
    }
  });
});
