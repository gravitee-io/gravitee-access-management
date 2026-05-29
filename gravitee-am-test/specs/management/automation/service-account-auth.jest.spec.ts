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

/**
 * Asserts the Automation API accepts opaque user service-account access tokens
 * (as minted via mAPI POST /organizations/{orgId}/users/{userId}/tokens).
 */
import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { setup } from '../../test-fixture';
import {
  AutomationServiceAccountAuthFixture,
  setupAutomationServiceAccountAuthFixture,
} from './fixtures/automation-service-account-fixture';
import { authHeaders, automationUrl, envPath, jsonHeaders } from './fixtures/automation-client';
import { performGet } from '@gateway-commands/oauth-oidc-commands';
import { getUserApi } from '@management-commands/service/utils';

setup();

let fixture: AutomationServiceAccountAuthFixture;

beforeAll(async () => {
  fixture = await setupAutomationServiceAccountAuthFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Automation API - Service-account opaque bearer token', () => {
  it('should accept a valid opaque service-account token (200)', async () => {
    const response = await performGet(automationUrl(), `${envPath()}/domains`, authHeaders(fixture.opaqueToken));
    expect(response.status).toBe(200);
  });

  it('should reject an opaque token whose payload is not Base64-decodable (401)', async () => {
    // Distinct from auth-and-openapi.jest.spec.ts which exercises the JWT branch with a dotted malformed value.
    const response = await performGet(
      automationUrl(),
      `${envPath()}/domains`,
      jsonHeaders({ Authorization: 'Bearer not-base64-no-dot' }),
    );
    expect(response.status).toBe(401);
  });

  it('should reject the opaque token after it has been revoked (401)', async () => {
    await getUserApi(fixture.adminAccessToken).revokeAccountAccessToken({
      organizationId: process.env.AM_DEF_ORG_ID,
      user: fixture.adminUserId,
      tokenId: fixture.serviceAccountTokenId,
    });
    // Mark revoked so the afterAll cleanup does not double-revoke.
    fixture.serviceAccountTokenId = '';

    const response = await performGet(automationUrl(), `${envPath()}/domains`, authHeaders(fixture.opaqueToken));
    expect(response.status).toBe(401);
  });
});
