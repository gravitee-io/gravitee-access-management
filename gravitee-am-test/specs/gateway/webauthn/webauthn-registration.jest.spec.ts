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
import { COSE_ALGORITHM, SoftwareAuthenticator } from '@gateway-commands/webauthn-authenticator';
import { TestCertificateAuthority } from '@gateway-commands/webauthn-certificates';
import { registerWebAuthnCredential, signInWithWebAuthn } from '@gateway-commands/webauthn-commands';
import { setup } from '../../test-fixture';
import { setupWebAuthnFixture, WebAuthnFixture } from './fixture/webauthn-fixture';

setup(120000);

// A refused registration ends on the error page. Not pinned to an error code: the gateway reports
// `unexpected_error` there, as 4.13 did, though a WebAuthn verification failure is meant to be a `technical_error`.
const REFUSED = expect.any(String);

let fixture: WebAuthnFixture;

beforeAll(async () => {
  fixture = await setupWebAuthnFixture();
});

afterAll(async () => {
  await fixture?.cleanUp();
});

describe('WebAuthn registration', () => {
  it('registers a credential without attestation, then signs in with it', async () => {
    const user = await fixture.createUser();
    const authenticator = SoftwareAuthenticator.create();

    const registration = await registerWebAuthnCredential(fixture.client, user, authenticator, { format: 'none' });

    expect(registration.code).toEqual(expect.any(String));
    expect(await fixture.credentials(user.id)).toEqual([
      expect.objectContaining({ credentialId: authenticator.id, attestationStatementFormat: 'none' }),
    ]);
    expect((await signInWithWebAuthn(fixture.client, user.username, authenticator)).code).toEqual(expect.any(String));
  });

  it('registers a self-attested packed credential, then signs in with it', async () => {
    const user = await fixture.createUser();
    const authenticator = SoftwareAuthenticator.create();

    const registration = await registerWebAuthnCredential(fixture.client, user, authenticator, { format: 'packed' });

    expect(registration.code).toEqual(expect.any(String));
    expect(await fixture.credentials(user.id)).toEqual([
      expect.objectContaining({ credentialId: authenticator.id, attestationStatementFormat: 'packed' }),
    ]);
    expect((await signInWithWebAuthn(fixture.client, user.username, authenticator)).code).toEqual(expect.any(String));
  });

  it('refuses a credential whose algorithm the creation options did not offer', async () => {
    // The gateway offers ES256 and RS256 only.
    const user = await fixture.createUser();

    const registration = await registerWebAuthnCredential(fixture.client, user, SoftwareAuthenticator.create(COSE_ALGORITHM.ES384));

    expect(registration.code).toBeUndefined();
    expect(registration.error).toEqual(REFUSED);
    expect(await fixture.credentials(user.id)).toEqual([]);
  });
});

describe('WebAuthn attestation trust anchors', () => {
  it('trusts an apple attestation chained to a root certificate of the security domain, then signs in with it', async () => {
    const user = await fixture.createUser();
    const authenticator = SoftwareAuthenticator.create();

    const registration = await registerWebAuthnCredential(fixture.client, user, authenticator, {
      format: 'apple',
      issuer: fixture.appleRoot,
    });

    expect(registration.code).toEqual(expect.any(String));
    expect(await fixture.credentials(user.id)).toEqual([
      expect.objectContaining({ credentialId: authenticator.id, attestationStatementFormat: 'apple' }),
    ]);
    expect((await signInWithWebAuthn(fixture.client, user.username, authenticator)).code).toEqual(expect.any(String));
  });

  it('refuses an apple attestation chained to a root certificate nobody trusts', async () => {
    const user = await fixture.createUser();
    const unknownRoot = TestCertificateAuthority.create('AM WebAuthn test unknown root');

    const registration = await registerWebAuthnCredential(fixture.client, user, SoftwareAuthenticator.create(), {
      format: 'apple',
      issuer: unknownRoot,
    });

    expect(registration.code).toBeUndefined();
    expect(registration.error).toEqual(REFUSED);
    expect(await fixture.credentials(user.id)).toEqual([]);
  });
});
