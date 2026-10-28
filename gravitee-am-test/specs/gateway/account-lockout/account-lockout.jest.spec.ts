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
import { setup } from '../../test-fixture';
import {
  ACCOUNT_BLOCKED_DURATION_SECONDS,
  AccountLockoutFixture,
  MAX_LOGIN_ATTEMPTS,
  REDIRECT_URI,
  setupAccountLockoutFixture,
  USER_PASSWORD,
} from './fixture/account-lockout-fixture';

// Covers the lockout half of "Protect sign-in from abuse": the account locks after repeated wrong
// passwords, the correct password is refused while it is locked, the lock is audited and it ends.
setup(300000);

const USER_LOCKED = 'USER_LOCKED';
const LOGIN_FAILED = 'error=login_failed';
// The login attempt that carries the lock is purged by the repository once its expiry date is
// reached, and the Mongo TTL monitor only runs about once a minute.
const LOCK_END_TIMEOUT_MILLIS = 150000;

let fixture: AccountLockoutFixture;
let knownLockAudits: Set<string>;

beforeAll(async () => {
  fixture = await setupAccountLockoutFixture();
  knownLockAudits = await auditIdsOfType(fixture.domain.id, fixture.accessToken, USER_LOCKED);

  for (let attempt = 1; attempt <= MAX_LOGIN_ATTEMPTS; attempt++) {
    const response = await fixture.submitLogin('not-the-password');
    expect(response.headers['location']).toContain(LOGIN_FAILED);
  }
});

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

describe('Account lockout - brute force', () => {
  it('should refuse the correct password once the maximum of failed attempts is reached', async () => {
    const response = await fixture.submitLogin(USER_PASSWORD);

    expect(response.headers['location']).toContain(LOGIN_FAILED);
    expect(response.headers['location']).not.toContain('/oauth/authorize');
  });

  it('should record the lockout in the audits', async () => {
    const audit = await waitForNewAudit(fixture.domain.id, fixture.accessToken, USER_LOCKED, 'SUCCESS', knownLockAudits);

    expect(audit.type).toEqual(USER_LOCKED);
  });

  it(`should accept the correct password again once the lock of ${ACCOUNT_BLOCKED_DURATION_SECONDS}s has ended`, async () => {
    const response = await retryUntil(
      () => fixture.completeLogin(USER_PASSWORD),
      (res) => (res.headers['location'] ?? '').includes(`${REDIRECT_URI}?code=`),
      { timeoutMillis: LOCK_END_TIMEOUT_MILLIS, intervalMillis: 5000 },
    );

    expect(response.headers['location']).toContain(`${REDIRECT_URI}?code=`);
  });
});
