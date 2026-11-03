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
import fetch from 'cross-fetch';
import { afterAll, beforeAll, describe, expect, it, jest } from '@jest/globals';
import { resetUserPassword, unlockUser } from '@management-commands/user-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { BruteForceFixture, INLINE_USERNAME, PASSWORD, setupBruteForceFixture } from './fixtures/brute-force-fixture';

globalThis.fetch = fetch;
jest.setTimeout(200000);

let fixture: BruteForceFixture;

beforeAll(async () => {
  fixture = await setupBruteForceFixture();
});

afterAll(async () => {
  await fixture?.cleanup();
});

describe('Brute force detection with a mixed-case username', () => {
  it('should lock the account when the failures use different casings', async () => {
    // an inline user exists in AM only after a first good login
    expect(await fixture.loginSucceeds(fixture.inlineApp, INLINE_USERNAME, PASSWORD)).toBe(true);

    await fixture.failLogins(fixture.inlineApp, ['lock.user', 'LOCK.USER', 'Lock.User']);

    expect(await fixture.loginSucceeds(fixture.inlineApp, 'lOcK.uSeR', PASSWORD)).toBe(false);
  });

  it('should let a mixed-case user log in again after an admin unlock', async () => {
    const username = `Unlock.${uniqueName('u', true)}`;
    const user = await fixture.createDefaultIdpUser(username);
    await fixture.failLogins(fixture.defaultIdpApp, [username, username, username]);
    expect(await fixture.loginSucceeds(fixture.defaultIdpApp, username, PASSWORD)).toBe(false);

    await unlockUser(fixture.domain.id, fixture.accessToken, user.id);

    expect(await fixture.eventuallyLoginSucceeds(fixture.defaultIdpApp, username, PASSWORD)).toBe(true);
  });

  it('should let a mixed-case user log in again after an admin password reset', async () => {
    const username = `Reset.${uniqueName('u', true)}`;
    const newPassword = '#N3w-CoMpL3X-P@SsW0Rd';
    const user = await fixture.createDefaultIdpUser(username);
    await fixture.failLogins(fixture.defaultIdpApp, [username, username, username]);
    expect(await fixture.loginSucceeds(fixture.defaultIdpApp, username, PASSWORD)).toBe(false);

    await resetUserPassword(fixture.domain.id, fixture.accessToken, user.id, newPassword);

    expect(await fixture.eventuallyLoginSucceeds(fixture.defaultIdpApp, username, newPassword)).toBe(true);
  });
});
