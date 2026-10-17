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
import {
  createDomain,
  patchDomain,
  safeDeleteDomain,
  startDomain,
  waitForDomainStart,
} from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createUser, listUserCredentials } from '@management-commands/user-management-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { getDefaultIdp } from '@management-commands/idp-management-commands';
import { WebAuthnClient } from '@gateway-commands/webauthn-commands';
import { TestCertificateAuthority } from '@gateway-commands/webauthn-certificates';
import { uniqueName } from '@utils-commands/misc';
import { Domain } from '@management-models/Domain';
import { Fixture } from '../../../test-fixture';

const REDIRECT_URI = 'https://callback.example.com';
const USER_PASSWORD = 'SomeP@ssw0rd';

export interface WebAuthnUser {
  id: string;
  username: string;
  password: string;
}

/** A WebAuthn credential as the Management API lists it for a user. */
export interface StoredCredential {
  credentialId: string;
  counter: number;
  attestationStatementFormat?: string;
}

export interface WebAuthnFixture extends Fixture {
  accessToken: string;
  domain: Domain;
  /** The application the ceremonies run for, on the gateway serving the security domain. */
  client: WebAuthnClient;
  /** The certificate authority the security domain trusts for `apple` attestations, through its WebAuthn settings. */
  appleRoot: TestCertificateAuthority;
  /** A new user of the default identity provider, with a password and no WebAuthn credential. */
  createUser: () => Promise<WebAuthnUser>;
  /** The WebAuthn credentials the Management API lists for a user. */
  credentials: (userId: string) => Promise<StoredCredential[]>;
}

/**
 * A security domain with passwordless enabled and user verification required, as the WebAuthn
 * Playwright suite configures it, plus a root certificate of its own for the `apple` attestation
 * format, so an attestation chained to it can only be trusted through the domain settings.
 */
export const setupWebAuthnFixture = async (): Promise<WebAuthnFixture> => {
  const accessToken = await requestAdminAccessToken();
  const appleRoot = TestCertificateAuthority.create('AM WebAuthn test Apple root');
  const gatewayUrl = process.env.AM_GATEWAY_URL;

  const domain = await createDomain(accessToken, uniqueName('webauthn', true), 'WebAuthn ceremonies');
  try {
    // Configured before the domain starts, so the gateway builds its WebAuthn settings once.
    await patchDomain(domain.id, accessToken, {
      loginSettings: { inherited: false, passwordlessEnabled: true, passwordlessDeviceNamingEnabled: false },
      webAuthnSettings: {
        origin: gatewayUrl,
        relyingPartyName: domain.name,
        userVerification: 'REQUIRED',
        attestationConveyancePreference: 'DIRECT',
        requireResidentKey: false,
        certificates: { apple: appleRoot.certificateBase64 },
      },
    });

    const defaultIdp = await getDefaultIdp(domain.id, accessToken);
    const application = await createApplication(domain.id, accessToken, {
      name: uniqueName('webauthn-app', true),
      type: 'WEB',
      redirectUris: [REDIRECT_URI],
    }).then((created) =>
      updateApplication(
        domain.id,
        accessToken,
        {
          settings: { oauth: { redirectUris: [REDIRECT_URI], grantTypes: ['authorization_code'] } },
          identityProviders: new Set([{ identity: defaultIdp.id, priority: -1 }]),
        },
        created.id,
      ),
    );

    await startDomain(domain.id, accessToken).then(waitForDomainStart);

    return {
      accessToken,
      domain,
      appleRoot,
      client: {
        gatewayUrl,
        domainHrid: domain.hrid,
        clientId: application.settings.oauth.clientId,
        redirectUri: REDIRECT_URI,
        origin: gatewayUrl,
      },
      createUser: async () => {
        const username = uniqueName('webauthn-user', true);
        const user = await createUser(domain.id, accessToken, {
          firstName: 'WebAuthn',
          lastName: 'User',
          email: `${username}@example.com`,
          username,
          password: USER_PASSWORD,
          source: defaultIdp.id,
          preRegistration: false,
        });
        return { id: user.id, username, password: USER_PASSWORD };
      },
      credentials: async (userId: string) => (await listUserCredentials(domain.id, accessToken, userId)) as unknown as StoredCredential[],
      cleanUp: () => safeDeleteDomain(domain.id, accessToken),
    };
  } catch (error) {
    await safeDeleteDomain(domain.id, accessToken);
    throw error;
  }
};
