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
import {
  createDomain,
  safeDeleteDomain,
  setupDomainForTest,
  patchDomain,
  waitForOidcReady,
} from '@management-commands/domain-management-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { uniqueName } from '@utils-commands/misc';
import type { Domain } from '@management-models/Domain';
import { setup } from '../../test-fixture';

setup(200000);

let accessToken: string;
let mainDomain: Domain;
let otherDomain: Domain;
let newPath: string;

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();

  const { domain } = await setupDomainForTest(uniqueName('vhost-main', true), {
    accessToken,
    waitForStart: true,
  });
  mainDomain = domain;

  // Other domain only needs to exist for path overlap validation — does not need to be started
  otherDomain = await createDomain(accessToken, uniqueName('vhost-other', true), 'Domain path mode tests - other');
  expect(otherDomain.id).toBeDefined();

  // Build a unique new path for use across tests
  newPath = `/${uniqueName('vhostu', false)}`;
});

afterAll(async () => {
  await safeDeleteDomain(mainDomain?.id, accessToken);
  await safeDeleteDomain(otherDomain?.id, accessToken);
});

describe('domain context-path mode - path validation', () => {
  it('should reject the root path "/" with a descriptive error', async () => {
    await expect(
      patchDomain(mainDomain.id, accessToken, { path: '/' }),
    ).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringContaining("'/' path is not allowed in context-path mode"),
    });
  });

  it('should reject an empty path (treated as "/") with a descriptive error', async () => {
    await expect(
      patchDomain(mainDomain.id, accessToken, { path: '' }),
    ).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringContaining("'/' path is not allowed in context-path mode"),
    });
  });

  it('should accept a valid new context path', async () => {
    const updated = await waitForSyncAfter(mainDomain.id, () =>
      patchDomain(mainDomain.id, accessToken, { path: newPath }),
    );
    expect(updated.path).toEqual(newPath);
  });
});

describe('domain context-path mode - OIDC endpoint URLs after path change', () => {
  it('should expose OIDC discovery endpoints relative to the updated context path', async () => {
    const res = await waitForOidcReady(newPath.slice(1));
    const body = res.body;
    expect(body.issuer).toContain(newPath);
    expect(body.authorization_endpoint).toContain(newPath);
    expect(body.token_endpoint).toContain(newPath);
    expect(body.userinfo_endpoint).toContain(newPath);
    expect(body.jwks_uri).toContain(newPath);
    expect(body.end_session_endpoint).toContain(newPath);
    expect(body.revocation_endpoint).toContain(newPath);
    expect(body.introspection_endpoint).toContain(newPath);
    expect(body.registration_endpoint).toContain(newPath);
  });
});

describe('domain context-path mode - overlap validation', () => {
  it('should reject a path that exactly overlaps another domain path', async () => {
    await expect(
      patchDomain(otherDomain.id, accessToken, { path: newPath }),
    ).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringContaining(`overlap path defined in another security domain`),
    });
  });

  it('should reject a path that is a subdirectory of an existing domain path', async () => {
    await expect(
      patchDomain(otherDomain.id, accessToken, { path: `${newPath}/overlapped` }),
    ).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringContaining(`is overlapped by another security domain`),
    });
  });

  it('should accept a non-overlapping path for the other domain', async () => {
    const distinctPath = `/${uniqueName('vhostother', false)}`;
    const updated = await patchDomain(otherDomain.id, accessToken, { path: distinctPath });
    expect(updated.path).toEqual(distinctPath);
  });
});
