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
import { expect } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { getUserApi } from '@management-commands/service/utils';
import { uniqueName } from '@utils-commands/misc';
import { JWT_FORMAT } from '@specs-utils/jwt-format';
import { Fixture } from '../../../test-fixture';
import { AutomationClient } from './automation-client';

/**
 * Token-based auth fixture for service-account opaque bearer tokens against the Automation API.
 * Mints the token against the existing admin user.
 * Cleanup revokes the issued token. Tests that revoke the token explicitly should clear
 * {@code serviceAccountTokenId} on the fixture to avoid a double-revoke 404 during cleanup.
 */
export interface AutomationServiceAccountAuthFixture extends Fixture {
  /** Admin JWT used to mint and to revoke the opaque token. */
  adminAccessToken: string;
  /** {@code sub} claim of the admin JWT. */
  adminUserId: string;
  /** Server-generated id of the minted token. Empty string means "already revoked, skip cleanup". */
  serviceAccountTokenId: string;
  /** {@code Base64(tokenId + '.' + value)} — the wire form sent as Bearer. */
  opaqueToken: string;
  /** AutomationClient wired with the opaque token (not the admin JWT). */
  client: AutomationClient;
}

const decodeJwtSub = (jwt: string): string => {
  const parts = jwt.split('.');
  const payload = JSON.parse(Buffer.from(parts[1], 'base64').toString());
  return payload.sub as string;
};

export const setupAutomationServiceAccountAuthFixture = async (): Promise<AutomationServiceAccountAuthFixture> => {
  const adminAccessToken = await requestAdminAccessToken();
  expect(adminAccessToken).toMatch(JWT_FORMAT);
  const adminUserId = decodeJwtSub(adminAccessToken);
  expect(adminUserId).toEqual(expect.any(String));

  const minted = await getUserApi(adminAccessToken).createAccountAccessToken({
    organizationId: process.env.AM_DEF_ORG_ID,
    user: adminUserId,
    newAccountAccessToken: { name: uniqueName('autosvc-token', true) },
  });
  expect(minted.tokenId).toEqual(expect.any(String));
  expect(minted.token).toEqual(expect.any(String));

  const fixture: AutomationServiceAccountAuthFixture = {
    adminAccessToken,
    adminUserId,
    serviceAccountTokenId: minted.tokenId!,
    opaqueToken: minted.token!,
    client: new AutomationClient(minted.token!),
    cleanUp: async () => {
      if (!fixture.serviceAccountTokenId) return;
      await getUserApi(adminAccessToken).revokeAccountAccessToken({
        organizationId: process.env.AM_DEF_ORG_ID,
        user: adminUserId,
        tokenId: fixture.serviceAccountTokenId,
      });
    },
  };
  return fixture;
};
