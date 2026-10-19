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
import { waitForCockpitConnection } from '@cloud-commands/cockpit-commands';
import { retryUntil } from '@utils-commands/retry';
import { SoftwareAuthenticator } from '@gateway-commands/webauthn-authenticator';
import { registerWebAuthnCredential, signInWithWebAuthn, WebAuthnClient } from '@gateway-commands/webauthn-commands';
import { setup } from '../test-fixture';
import { CloudWebAuthnFixture, setupCloudWebAuthnFixture } from './fixtures/cloud-webauthn-fixture';

setup(200000);

/**
 * In managed cloud the browser reaches the gateway on the environment's entrypoint, so that host, not
 * gateway.url and not the domain's own WebAuthn settings, is what the ceremony has to be scoped to: the
 * relying party id the gateway hands the browser, and the origin and relying party id it verifies the
 * response against. A software authenticator completes the ceremonies through the entrypoint host.
 *
 * The fixture's domains deliberately point their origin at localhost, so a passing assertion can only
 * mean the entrypoint overrode it. A relying party id the domain set for itself is the exception: that
 * one has to survive, because existing credentials are bound to it.
 */
describe('AM - Cloud - webauthn relying party from the environment entrypoint', () => {
  let fixture: CloudWebAuthnFixture;

  beforeAll(async () => {
    await waitForCockpitConnection();
    fixture = await setupCloudWebAuthnFixture();
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

  it('leaves a relying party id the domain configured for itself alone', async () => {
    // Credentials are bound to the relying party id they were registered under, so overriding a
    // deliberate one — typically a parent domain covering several subdomains — would orphan every
    // credential a customer already has.
    const options = await fixture.configuredAssertionOptions();

    expect(options.rpId).toEqual(fixture.configuredRelyingPartyId);
  });

  describe('passwordless ceremony through the entrypoint', () => {
    let client: WebAuthnClient;
    let username: string;
    let authenticator: SoftwareAuthenticator;

    beforeAll(async () => {
      client = fixture.passwordlessClient(fixture.overridingHost);
      const user = await fixture.createUser();
      username = user.username;
      authenticator = SoftwareAuthenticator.create();

      const registration = await registerWebAuthnCredential(client, user, authenticator);

      expect(registration.code).toEqual(expect.any(String));
    });

    it('signs in without a password with the credential registered through the entrypoint', async () => {
      const login = await signInWithWebAuthn(client, username, authenticator);

      expect(login.code).toEqual(expect.any(String));
    });

    // The response is verified against the relying party id the gateway gave the browser, so a
    // signature for the one the domain settings fall back to cannot pass.
    it('refuses an assertion made for the relying party of the configured origin', async () => {
      const login = await signInWithWebAuthn(client, username, authenticator, { rpId: 'localhost' });

      expect(login.code).toBeUndefined();
      expect(login.error).toBe('technical_error');
    });

    // Not pinned to an error code: the origin check fails before webauthn4j verifies anything, and that
    // failure ends on `unexpected_error` rather than `technical_error`.
    it('refuses an assertion whose client data reports the configured origin', async () => {
      const login = await signInWithWebAuthn(client, username, authenticator, { origin: 'http://localhost:8092' });

      expect(login.code).toBeUndefined();
      expect(login.error).toEqual(expect.any(String));
    });
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
