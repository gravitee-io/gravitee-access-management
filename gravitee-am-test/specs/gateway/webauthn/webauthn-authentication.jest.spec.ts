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
import { SoftwareAuthenticator, timeBasedCounter } from '@gateway-commands/webauthn-authenticator';
import { registerWebAuthnCredential, signInWithWebAuthn } from '@gateway-commands/webauthn-commands';
import { setup } from '../../test-fixture';
import { setupWebAuthnFixture, WebAuthnFixture, WebAuthnUser } from './fixture/webauthn-fixture';

setup(120000);

let fixture: WebAuthnFixture;

beforeAll(async () => {
  fixture = await setupWebAuthnFixture();
});

afterAll(async () => {
  await fixture?.cleanUp();
});

/** A user with a registered credential, so each test starts from a counter of its own. */
const registeredUser = async (): Promise<{ user: WebAuthnUser; authenticator: SoftwareAuthenticator }> => {
  const user = await fixture.createUser();
  const authenticator = SoftwareAuthenticator.create();
  const registration = await registerWebAuthnCredential(fixture.client, user, authenticator);
  expect(registration.code).toEqual(expect.any(String));
  return { user, authenticator };
};

describe('WebAuthn passwordless login', () => {
  it('signs in with the registered credential and stores its signature counter', async () => {
    const { user, authenticator } = await registeredUser();
    const counter = timeBasedCounter() + 10;

    const login = await signInWithWebAuthn(fixture.client, user.username, authenticator, { counter });

    expect(login.code).toEqual(expect.any(String));
    const credentials = await fixture.credentials(user.id);
    expect(credentials).toHaveLength(1);
    expect(credentials[0].credentialId).toBe(authenticator.id);
    expect(credentials[0].counter).toBe(counter);
  });

  it('refuses an assertion that the registered key did not sign', async () => {
    const { user, authenticator } = await registeredUser();

    const login = await signInWithWebAuthn(fixture.client, user.username, authenticator.impostor());

    expect(login.code).toBeUndefined();
    expect(login.error).toBe('technical_error');
  });

  it('refuses an assertion made for another relying party', async () => {
    const { user, authenticator } = await registeredUser();

    const login = await signInWithWebAuthn(fixture.client, user.username, authenticator, { rpId: 'other.example.com' });

    expect(login.code).toBeUndefined();
    expect(login.error).toBe('technical_error');
  });

  it('refuses an assertion without user verification when the security domain requires it', async () => {
    const { user, authenticator } = await registeredUser();

    const login = await signInWithWebAuthn(fixture.client, user.username, authenticator, { flags: { userVerified: false } });

    expect(login.code).toBeUndefined();
    expect(login.error).toBe('technical_error');
  });

  it('refuses a signature counter lower than the stored one, a sign of a cloned authenticator', async () => {
    const { user, authenticator } = await registeredUser();
    const counter = timeBasedCounter() + 1000;
    expect((await signInWithWebAuthn(fixture.client, user.username, authenticator, { counter })).code).toEqual(expect.any(String));

    const login = await signInWithWebAuthn(fixture.client, user.username, authenticator, { counter: counter - 1 });

    expect(login.code).toBeUndefined();
    expect(login.error).toBe('technical_error');
    expect((await fixture.credentials(user.id))[0].counter).toBe(counter);
  });
});
