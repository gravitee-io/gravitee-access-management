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
import { afterAll, beforeAll, expect } from '@jest/globals';
import { McpClientSecretsFixture, setupMcpClientSecretsFixture } from './fixtures/mcp-client-secrets-fixture';
import { setup } from '../test-fixture';

setup(200000);

let fixture: McpClientSecretsFixture;

beforeAll(async () => {
  fixture = await setupMcpClientSecretsFixture();
});

afterAll(async () => {
  await fixture.cleanup();
});

describe('MCP Client Secrets Management', () => {
  it('should create new secret', async () => {
    const createdSecret = await fixture.createSecret('test-mcp-secret');
    expect(createdSecret).toBeDefined();
    expect(createdSecret.id).toBeDefined();
    expect(createdSecret.secret).toBeDefined();
    expect(createdSecret.name).toEqual('test-mcp-secret');

    const secrets = await fixture.listSecrets();
    expect(secrets.find((s) => s.id === createdSecret.id)).toBeDefined();
  });

  it('should fail to create secret with duplicate name', async () => {
    try {
      await fixture.createSecret('test-mcp-secret');
      throw new Error('Should have thrown an error');
    } catch (e: any) {
      expect(e.message).toContain('already exists');
    }
  });

  it('should rotate (renew) secret', async () => {
    const secretToRenew = await fixture.createSecret('secret-to-renew');

    // Wait briefly to ensure timestamps might differ if relevant, though we check value
    await new Promise((r) => setTimeout(r, 2000));

    const renewedSecret = await fixture.renewSecret(secretToRenew.id!);
    expect(renewedSecret).toBeDefined();
    expect(renewedSecret.id).toEqual(secretToRenew.id);

    // The 'secret' field is the actual secret string
    expect(renewedSecret.secret).not.toEqual(secretToRenew.secret);
  });

  it('should remove secret', async () => {
    const secretToDelete = await fixture.createSecret('secret-to-delete');

    await fixture.deleteSecret(secretToDelete.id!);

    const secrets = await fixture.listSecrets();
    expect(secrets.find((s) => s.id === secretToDelete.id)).toBeUndefined();
  });

  describe('Expiration in client secrets', () => {
    it('should check expiration settings from Domain', async () => {
      // Update domain to have secret expiry
      await fixture.setDomainSecretSettings({
        enabled: true,
        expiryTimeSeconds: 120,
      });

      const clientSecret = await fixture.createSecret('expired-test-secret');

      expect(clientSecret.expiresAt).toBeDefined();
      const now = Date.now();
      // 120 seconds = 120000 ms
      // Allow some delta
      expect(clientSecret.expiresAt!.getTime()).toBeLessThan(now + 120000 + 5000);
      expect(clientSecret.expiresAt!.getTime()).toBeGreaterThan(now + 120000 - 5000);
    });
  });
});
