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
import { beforeAll, expect, it } from '@jest/globals';
import { timeBasedCounter } from '@gateway-commands/webauthn-authenticator';
import { getDataPlaneTargets, getInstanceLabel } from '../../migration-seeding/seed';
import { createWebAuthnMigrationFixture, WEBAUTHN_REQUIREMENTS, WebAuthnMigrationFixture } from './fixture/webauthn-fixture';
import { currentChannel, describeWhen, itWhen } from './fixture/migration-versions';
import { setup } from '../test-fixture';

setup(120000);

const channelLabel = currentChannel();
const requires = WEBAUTHN_REQUIREMENTS;

// The alpha channel holds a credential registered through a gateway on vertx-auth-webauthn (4.13 or
// earlier), which a 4.14 gateway (webauthn4j) has to accept after the upgrade; the beta channel holds
// one registered through webauthn4j, which the older gateway has to accept again after the downgrade.
describeWhen(requires.seeded, channelLabel).each(getDataPlaneTargets())('migration WebAuthn [data plane $id]', (target) => {
  const label = getInstanceLabel(channelLabel, target.id);
  let fixture: WebAuthnMigrationFixture;

  beforeAll(async () => {
    fixture = await createWebAuthnMigrationFixture(label, target);
  });

  it('still lists the WebAuthn credential the user registered', async () => {
    const credentials = await fixture.credentials();

    expect(credentials.map((credential) => credential.credentialId)).toEqual([fixture.authenticator.id]);
  });

  // The counter grows with time, so every verification stage presents a higher one than the stage
  // before, and the stored counter proves the gateway of this stage wrote it back.
  itWhen(requires.gatewaySignsIn)('signs in without a password with the registered credential', async () => {
    const counter = timeBasedCounter();

    const login = await fixture.signIn(fixture.authenticator, { counter });

    expect(login.code).toEqual(expect.any(String));
    expect((await fixture.credentials())[0].counter).toBe(counter);
  });

  // Guards the test above against passing for the wrong reason: a gateway that no longer checked the
  // signature against the stored public key would let anyone in.
  itWhen(requires.gatewaySignsIn)('refuses an assertion that the registered key did not sign', async () => {
    const login = await fixture.signIn(fixture.authenticator.impostor());

    expect(login.code).toBeUndefined();
    expect(login.error).toBe('technical_error');
  });
});
