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
import { CimdFixture, setupFixture } from './fixture/cimd-fixture';
import { setup } from '../../test-fixture';

setup();

let fixture: CimdFixture;

beforeAll(async () => {
  fixture = await setupFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('CIMD Settings - Defaults', () => {
  it('should return cimdSettings with default values on GET domain', async () => {
    const domain = await fixture.getDomain();

    expect(domain.oidc).toBeDefined();
    const cimd = domain.oidc.cimdSettings;
    expect(cimd).toBeDefined();
    expect(cimd.enabled).toBe(false);
    expect(cimd.allowUnsecuredHttpUri).toBe(false);
    expect(cimd.allowPrivateIpAddress).toBe(false);
    expect(cimd.fetchTimeoutMs).toBe(5000);
    expect(cimd.maxResponseSizeKb).toBe(10);
    expect(cimd.cacheTtlSeconds).toBe(86400);
    expect(cimd.cacheMaxEntries).toBe(1000);
    expect(cimd.allowedGrantTypes).toEqual(expect.arrayContaining(['authorization_code', 'password']));
    expect(cimd.accessTokenValiditySeconds).toBe(7200);
    expect(cimd.refreshTokenValiditySeconds).toBe(14400);
    expect(cimd.idTokenValiditySeconds).toBe(14400);
  });
});

describe('CIMD Settings - Enable/Disable', () => {
  it('should enable CIMD via PATCH', async () => {
    const domain = await fixture.patchCimdSettings({ enabled: true });

    expect(domain.oidc.cimdSettings.enabled).toBe(true);
  });

  it('should disable CIMD via PATCH', async () => {
    const domain = await fixture.patchCimdSettings({ enabled: false });

    expect(domain.oidc.cimdSettings.enabled).toBe(false);
  });
});

describe('CIMD Settings - SSRF Protection', () => {
  it('should update SSRF protection fields', async () => {
    const domain = await fixture.patchCimdSettings({
      allowUnsecuredHttpUri: true,
      allowPrivateIpAddress: true,
      fetchTimeoutMs: 3000,
      maxResponseSizeKb: 20,
    });

    const cimd = domain.oidc.cimdSettings;
    expect(cimd.allowUnsecuredHttpUri).toBe(true);
    expect(cimd.allowPrivateIpAddress).toBe(true);
    expect(cimd.fetchTimeoutMs).toBe(3000);
    expect(cimd.maxResponseSizeKb).toBe(20);
  });

  it('should update domain trust allowlist', async () => {
    const domain = await fixture.patchCimdSettings({
      allowedDomains: ['*.example.com', 'trusted.org'],
    });

    expect(domain.oidc.cimdSettings.allowedDomains).toEqual(['*.example.com', 'trusted.org']);
  });
});

describe('CIMD Settings - Cache', () => {
  it('should update cache settings', async () => {
    const domain = await fixture.patchCimdSettings({
      cacheTtlSeconds: 3600,
      cacheMaxEntries: 500,
    });

    const cimd = domain.oidc.cimdSettings;
    expect(cimd.cacheTtlSeconds).toBe(3600);
    expect(cimd.cacheMaxEntries).toBe(500);
  });
});

describe('CIMD Settings - Security Policy', () => {
  it('should update allowed grant types and scopes', async () => {
    const domain = await fixture.patchCimdSettings({
      allowedGrantTypes: ['authorization_code'],
      allowedScopes: ['openid', 'profile'],
    });

    const cimd = domain.oidc.cimdSettings;
    expect(cimd.allowedGrantTypes).toEqual(['authorization_code']);
    expect(cimd.allowedScopes).toEqual(['openid', 'profile']);
  });

  it('should update token expiration settings', async () => {
    const domain = await fixture.patchCimdSettings({
      accessTokenValiditySeconds: 3600,
      refreshTokenValiditySeconds: 7200,
      idTokenValiditySeconds: 3600,
    });

    const cimd = domain.oidc.cimdSettings;
    expect(cimd.accessTokenValiditySeconds).toBe(3600);
    expect(cimd.refreshTokenValiditySeconds).toBe(7200);
    expect(cimd.idTokenValiditySeconds).toBe(3600);
  });

  it('should update identity provider assignments', async () => {
    const domain = await fixture.patchCimdSettings({
      identityProviders: ['idp-1', 'idp-2'],
    });

    expect(domain.oidc.cimdSettings.identityProviders).toEqual(['idp-1', 'idp-2']);
  });
});

