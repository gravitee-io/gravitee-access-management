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

import fetch from 'cross-fetch';
import { afterAll, beforeAll, describe, expect, it, jest } from '@jest/globals';
import {
  requestPermissionTicket,
  requestProtectionApiToken,
  requestRptToken,
  registerUmaResource,
  setupUmaFixture,
  UmaFixture,
  UMA_TEST,
} from './fixtures/uma-fixture';

globalThis.fetch = fetch;
jest.setTimeout(200000);

let fixture: UmaFixture;

beforeAll(async () => {
  fixture = await setupUmaFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

describe('UMA grant', () => {
  it('should advertise UMA grant in discovery', async () => {
    expect(fixture.umaConfig.grant_types_supported).toContain(UMA_TEST.UMA_GRANT);
  });

  it('should issue a token from a permission ticket', async () => {
    const protectionApiToken = await requestProtectionApiToken(fixture);
    const resourceId = await registerUmaResource(fixture, protectionApiToken);
    const ticket = await requestPermissionTicket(fixture, protectionApiToken, resourceId);

    const response = await requestRptToken(fixture, ticket).expect(200);

    expect(response.body.access_token).toBeDefined();
    expect(response.body.token_type).toBeDefined();
    expect(response.body.expires_in).toBeDefined();
  });

  it('should reject when ticket is missing', async () => {
    const response = await requestRptToken(fixture, '').expect(400);

    expect(response.body.error).toBeDefined();
    expect(response.body.error_description).toContain('ticket');
  });

  it('should reject when ticket is invalid', async () => {
    const response = await requestRptToken(fixture, 'invalid-ticket').expect(400);

    expect(response.body.error).toBeDefined();
    expect(response.body.error_description).toContain('ticket');
  });

  it('should reject when requested scope is not allowed', async () => {
    const protectionApiToken = await requestProtectionApiToken(fixture);
    const resourceId = await registerUmaResource(fixture, protectionApiToken);
    const ticket = await requestPermissionTicket(fixture, protectionApiToken, resourceId);

    const response = await requestRptToken(fixture, ticket, 'unknown').expect(400);

    expect(response.body.error).toBe('invalid_scope');
  });
});
