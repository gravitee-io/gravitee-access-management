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
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { waitForCockpitConnection } from '@cloud-commands/cockpit-commands';
import { retryUntil } from '@utils-commands/retry';
import { setup } from '../test-fixture';
import { CloudWebAuthnFixture, setupCloudWebAuthnFixture } from './fixtures/cloud-webauthn-fixture';

setup(200000);

/**
 * In managed cloud the browser reaches the gateway on the environment's entrypoint, so that host, not
 * gateway.url and not the domain's own WebAuthn settings, is what the ceremony has to be scoped to. The
 * origin is only compared server-side during verification and needs a real authenticator, so these
 * assert on the relying party id the gateway hands the browser, which travels with it.
 *
 * The fixture's domain deliberately has origin/relyingPartyId set to localhost, so a passing assertion
 * can only mean the entrypoint overrode them.
 */
describe('AM - Cloud - webauthn relying party from the environment entrypoint', () => {
  let accessToken: string;
  let fixture: CloudWebAuthnFixture;

  beforeAll(async () => {
    accessToken = await requestAdminAccessToken();
    await waitForCockpitConnection();
    fixture = await setupCloudWebAuthnFixture(accessToken);
  });

  afterAll(async () => {
    await fixture?.cleanup();
  });

  it('scopes the ceremony to the environment entrypoint rather than the configured localhost', async () => {
    const options = await fixture.assertionOptions();

    expect(options.rpId).toEqual(fixture.overridingHost);
  });

  it('follows the entrypoint the request actually came in on', async () => {
    const options = await fixture.assertionOptions(fixture.generatedHost);

    // The overriding access point is the environment's primary, so answering with the generated one
    // proves the request host won rather than the fallback.
    expect(options.rpId).toEqual(fixture.generatedHost);
  });

  it('falls back to the primary entrypoint for a host the environment never synced', async () => {
    const options = await fixture.assertionOptions('forged.example.com');

    expect(options.rpId).toEqual(fixture.overridingHost);
  });

  it('picks up re-synced access points without redeploying the domain', async () => {
    const generated = fixture.uniqueHost();
    const overriding = fixture.uniqueHost();

    await fixture.resyncAccessPoints({ generated, overriding });

    // No domain restart in between: the origin is resolved per request, so the gateway's entrypoint
    // cache update is enough. retryUntil covers the gap between the management API persisting the new
    // entrypoints and the gateway's cache catching the events.
    const options = await retryUntil(
      () => fixture.assertionOptions(),
      (resolved: any) => resolved.rpId === overriding,
      { timeoutMillis: 30000, intervalMillis: 1000 },
    );
    expect(options.rpId).toEqual(overriding);
  });
});
