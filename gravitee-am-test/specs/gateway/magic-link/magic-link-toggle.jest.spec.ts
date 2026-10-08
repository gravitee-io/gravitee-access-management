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
import { performGet } from '@gateway-commands/oauth-oidc-commands';
import { retryUntil } from '@utils-commands/retry';
import { jira } from '@specs-utils/jira';
import { errorDescriptionOf, MagicLinkGatewayFixture, setupFixture } from './fixture/magic-link-gateway-fixture';
import { setup } from '../../test-fixture';

setup(200000);

/** The gateway's own wording when the feature is off, taken from the redirect it issues. */
const DISABLED_MESSAGE = 'Magic Link authentication is disabled';

/**
 * These tests turn the domain-level toggle off, so they get their own file and their own domain.
 * Sharing a fixture with the enabled-state tests would make those order-dependent.
 */
let fixture: MagicLinkGatewayFixture;

beforeAll(async () => {
  fixture = await setupFixture('magic-link-toggle');
});

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

/** Turns the toggle off and waits for the gateway to pick the change up. */
const disableAndWaitForEffect = async (): Promise<void> => {
  await fixture.setMagicLinkEnabled(false);
  await retryUntil(
    async () => performGet(fixture.loginPageUrl(), '', {}),
    (response) => errorDescriptionOf(response) === DISABLED_MESSAGE,
    { timeoutMillis: 60000, intervalMillis: 1000 },
  );
};

describe('Magic Link - domain toggle off', () => {
  it(jira`rejects the magic link login endpoint once the toggle is off ${'AM-6703'}`, async () => {
    // Positive anchor: while the toggle is on the endpoint serves the login page, so the
    // rejection below is caused by the toggle rather than by a wrong URL.
    const whileEnabled = await performGet(fixture.loginPageUrl(), '', {});
    expect(whileEnabled.status).toEqual(200);

    await fixture.setMagicLinkEnabled(false);
    const response = await retryUntil(
      async () => performGet(fixture.loginPageUrl(), '', {}),
      (r) => r.status === 302,
      { timeoutMillis: 60000, intervalMillis: 1000 },
    );

    expect(errorDescriptionOf(response)).toEqual(DISABLED_MESSAGE);
  });

  it(jira`rejects a link that was issued before the toggle was turned off ${'AM-6649'}`, async () => {
    await fixture.setMagicLinkEnabled(true);
    await retryUntil(
      async () => performGet(fixture.loginPageUrl(), '', {}),
      (response) => response.status === 200,
      { timeoutMillis: 60000, intervalMillis: 1000 },
    );

    const request = await fixture.requestLink();
    const link = await fixture.lastLink();
    await disableAndWaitForEffect();

    const response = await fixture.useLink(link, request.cookie);

    expect(errorDescriptionOf(response)).toEqual(DISABLED_MESSAGE);
  });
});
