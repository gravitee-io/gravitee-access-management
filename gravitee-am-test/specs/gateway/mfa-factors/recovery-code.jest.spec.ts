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
import { RecoveryCodeFixture, setupRecoveryCodeFixture } from './fixtures/recovery-code-fixture';

setup(300000);

/**
 * AM-2216 / UC-AM46 — a recovery code is spent when it is used.
 *
 * A refused code and a code that was never issued produce the same error, so every refusal below
 * is paired with the same code having worked first. Without that, a test could pass because the
 * code never worked at all.
 */
let fixture: RecoveryCodeFixture;

beforeAll(async () => {
  fixture = await setupRecoveryCodeFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

/** The gateway sends an accepted challenge on to the authorization endpoint. */
const expectAccepted = (response: any) => {
  expect(response.status).toEqual(302);
  expect(response.headers['location']).toContain('/oauth/authorize');
};

/** A refused challenge goes back to the challenge page carrying the failure. */
const expectRefused = (response: any) => {
  expect(response.status).toEqual(302);
  expect(response.headers['location']).toContain('error=mfa_challenge_failed');
  expect(response.headers['location']).not.toMatch(/[?&]code=/);
};

describe('Recovery codes during sign-in', () => {
  it(jira`a code signs the user in and is refused when used again ${'AM-2216'}`, async () => {
    const { user, codes, accessToken } = await fixture.enrolUserWithRecoveryCodes();

    // The anchor: this exact code works. A refusal below therefore means it was spent, not that
    // it was never valid.
    expectAccepted(await fixture.submitRecoveryCode(user, codes[0]));

    // Through the second application, so the retry is not answered by the MFA rate limiter.
    expectRefused(await fixture.submitRecoveryCode(user, codes[0], true));

    // Not only refused at sign-in — the code has left the user's set entirely.
    expect(await fixture.remainingCodes(accessToken)).not.toContain(codes[0]);
  });

  it(jira`a code that was never issued is refused ${'AM-2216'}`, async () => {
    const { user, codes } = await fixture.enrolUserWithRecoveryCodes();
    const notIssued = 'ZZZZZ';
    expect(codes).not.toContain(notIssued);

    expectRefused(await fixture.submitRecoveryCode(user, notIssued));

    // The user's own codes are untouched by the failed attempt.
    expectAccepted(await fixture.submitRecoveryCode(user, codes[0], true));
  });

  it(jira`spending one code leaves the others usable ${'AM-2216'}`, async () => {
    const { user, codes, accessToken } = await fixture.enrolUserWithRecoveryCodes();

    expectAccepted(await fixture.submitRecoveryCode(user, codes[0]));
    expectAccepted(await fixture.submitRecoveryCode(user, codes[1], true));

    expect(await fixture.remainingCodes(accessToken)).toEqual([]);
  });

  it(jira`spending every code leaves the user with none ${'AM-2216'}`, async () => {
    const { user, codes, accessToken } = await fixture.enrolUserWithRecoveryCodes();
    expect(await fixture.remainingCodes(accessToken)).toEqual(codes);

    for (const [index, code] of codes.entries()) {
      expectAccepted(await fixture.submitRecoveryCode(user, code, index > 0));
    }

    expect(await fixture.remainingCodes(accessToken)).toEqual([]);
  });

  it(jira`regenerating the codes stops the old ones working ${'AM-2216'}`, async () => {
    const { user, codes, accessToken } = await fixture.enrolUserWithRecoveryCodes();

    const fresh = await fixture.regenerateCodes(accessToken);
    expect(fresh).toHaveLength(codes.length);
    expect(fresh).not.toEqual(expect.arrayContaining(codes));

    // The old code was never used, so a refusal here is the regeneration and nothing else.
    expectRefused(await fixture.submitRecoveryCode(user, codes[0]));
    expectAccepted(await fixture.submitRecoveryCode(user, fresh[0], true));
  });
});
