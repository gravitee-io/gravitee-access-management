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
} from '@management-commands/domain-management-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { performGet } from '@gateway-commands/oauth-oidc-commands';
import { uniqueName } from '@utils-commands/misc';
import type { Domain } from '@management-models/Domain';
import { setup } from '../../test-fixture';

setup(200000);

let accessToken: string;
let mainDomain: Domain;
let otherDomain: Domain;
let vhostPath: string;
let gatewayHost: string;

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();

  const { domain } = await setupDomainForTest(uniqueName('vhost-main', true), {
    accessToken,
    waitForStart: true,
  });
  mainDomain = domain;

  // Other domain only needs to exist for overlap validation — does not need to be started
  otherDomain = await createDomain(accessToken, uniqueName('vhost-other', true), 'VHost mode tests - other');
  expect(otherDomain.id).toBeDefined();

  // Extract the gateway host (e.g. "localhost:8092") to use as the vhost host value
  gatewayHost = new URL(process.env.AM_GATEWAY_URL!).host;

  // Build a unique vhost path for use across tests
  vhostPath = `/${uniqueName('vhostp', false)}`;
});

afterAll(async () => {
  await safeDeleteDomain(mainDomain?.id, accessToken);
  await safeDeleteDomain(otherDomain?.id, accessToken);
});

describe('domain vhost mode - validation', () => {
  it('should reject enabling vhost mode with an empty vhosts array', async () => {
    await expect(
      patchDomain(mainDomain.id, accessToken, { vhostMode: true, vhosts: [] }),
    ).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringContaining('VHost mode requires at least one VHost'),
    });
  });

  it('should reject a vhost with a null host', async () => {
    await expect(
      patchDomain(mainDomain.id, accessToken, {
        vhostMode: true,
        vhosts: [{ host: null, path: vhostPath, overrideEntrypoint: true }],
      }),
    ).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringContaining('Host is required'),
    });
  });

  it('should reject a vhost with a port-only host (empty hostname)', async () => {
    await expect(
      patchDomain(mainDomain.id, accessToken, {
        vhostMode: true,
        vhosts: [{ host: ':1234', path: vhostPath, overrideEntrypoint: true }],
      }),
    ).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringContaining('Host [] is invalid'),
    });
  });

  it('should reject a vhost with an invalid host format', async () => {
    await expect(
      patchDomain(mainDomain.id, accessToken, {
        vhostMode: true,
        vhosts: [{ host: '!Not a Valid Host', path: vhostPath, overrideEntrypoint: true }],
      }),
    ).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringContaining('Host [!Not a Valid Host] is invalid'),
    });
  });

  it('should reject a vhost with a non-numeric port', async () => {
    await expect(
      patchDomain(mainDomain.id, accessToken, {
        vhostMode: true,
        vhosts: [{ host: 'localhost:NotValid', path: vhostPath, overrideEntrypoint: true }],
      }),
    ).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringContaining('Host port for [localhost:NotValid] is invalid'),
    });
  });

  it('should reject a vhost with a port number that exceeds the valid range', async () => {
    await expect(
      patchDomain(mainDomain.id, accessToken, {
        vhostMode: true,
        vhosts: [{ host: 'localhost:700000', path: vhostPath, overrideEntrypoint: true }],
      }),
    ).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringContaining('Host port [700000] is invalid'),
    });
  });

  it('should reject a vhost configuration where no vhost has overrideEntrypoint set', async () => {
    await expect(
      patchDomain(mainDomain.id, accessToken, {
        vhostMode: true,
        vhosts: [{ host: gatewayHost, path: vhostPath, overrideEntrypoint: false }],
      }),
    ).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringContaining('You must select one vhost to override entrypoint'),
    });
  });
});

describe('domain vhost mode - gateway routing after switching to vhost', () => {
  it('should accept a valid vhost configuration and switch the domain to vhost mode', async () => {
    const updated = await waitForSyncAfter(mainDomain.id, () =>
      patchDomain(mainDomain.id, accessToken, {
        vhostMode: true,
        vhosts: [{ host: gatewayHost, path: vhostPath, overrideEntrypoint: true }],
      }),
    );
    expect(updated.vhostMode).toBe(true);
    expect(updated.vhosts?.[0].path).toEqual(vhostPath);
  });

  it('should serve OIDC discovery at the vhost path with correct endpoint URLs', async () => {
    const res = await performGet(process.env.AM_GATEWAY_URL, `${vhostPath}/oidc/.well-known/openid-configuration`);
    expect(res.status).toBe(200);
    const body = res.body;
    expect(body.issuer).toContain(vhostPath);
    expect(body.authorization_endpoint).toContain(vhostPath);
    expect(body.token_endpoint).toContain(vhostPath);
    expect(body.userinfo_endpoint).toContain(vhostPath);
    expect(body.jwks_uri).toContain(vhostPath);
    expect(body.end_session_endpoint).toContain(vhostPath);
    expect(body.revocation_endpoint).toContain(vhostPath);
    expect(body.introspection_endpoint).toContain(vhostPath);
    expect(body.registration_endpoint).toContain(vhostPath);
  });

  it('should reflect X-Forwarded headers in OIDC discovery endpoint URLs', async () => {
    const forwardedHost = 'test.gravitee.io';
    const forwardedPrefix = '/am';
    const res = await performGet(process.env.AM_GATEWAY_URL, `${vhostPath}/oidc/.well-known/openid-configuration`, {
      'X-Forwarded-Host': forwardedHost,
      'X-Forwarded-Prefix': forwardedPrefix,
    });
    expect(res.status).toBe(200);
    const body = res.body;
    const expectedBase = `http://${forwardedHost}${forwardedPrefix}${vhostPath}`;
    expect(body.issuer).toContain(expectedBase);
    expect(body.authorization_endpoint).toContain(expectedBase);
    expect(body.token_endpoint).toContain(expectedBase);
  });
});

describe('domain vhost mode - path overlap validation', () => {
  it('should reject setting another domain to a context path that overlaps the active vhost path', async () => {
    await expect(
      patchDomain(otherDomain.id, accessToken, { path: vhostPath }),
    ).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringContaining('overlap path defined in another security domain'),
    });
  });

  it('should reject a subdirectory context path that is overlapped by the active vhost path', async () => {
    await expect(
      patchDomain(otherDomain.id, accessToken, { path: `${vhostPath}/overlapped` }),
    ).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringContaining('is overlapped by another security domain'),
    });
  });

  it('should reject switching another domain to vhost mode with a path overlapping the active vhost', async () => {
    await expect(
      patchDomain(otherDomain.id, accessToken, {
        vhostMode: true,
        vhosts: [{ host: gatewayHost, path: vhostPath, overrideEntrypoint: true }],
      }),
    ).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringContaining('overlap path defined in another security domain'),
    });
  });

  it('should accept switching another domain to vhost mode with a non-overlapping path', async () => {
    const distinctPath = `/${uniqueName('vhostother', false)}`;
    const updated = await patchDomain(otherDomain.id, accessToken, {
      vhostMode: true,
      vhosts: [{ host: gatewayHost, path: distinctPath, overrideEntrypoint: true }],
    });
    expect(updated.vhostMode).toBe(true);
    expect(updated.vhosts?.[0].path).toEqual(distinctPath);
  });
});
