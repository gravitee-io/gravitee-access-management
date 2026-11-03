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
import { createClientSecret, listClientSecrets } from '@management-commands/client-secrets-management-commands';
import { waitForOidcReady } from '@management-commands/domain-management-commands';
import { waitForDomainReady } from '@gateway-commands/monitoring-commands';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { getBase64BasicAuth } from '@gateway-commands/utils';
import { retryUntil } from '@utils-commands/retry';
import { setup } from '../../test-fixture';
import {
  AutomationClientSecretFixture,
  buildServiceApplicationDef,
  newSecretValue,
  setupAutomationClientSecretFixture,
} from './fixtures/automation-client-secret-fixture';

setup(180000);

/** Default value of applications.secretsMax. */
const SECRETS_MAX = 10;

let fixture: AutomationClientSecretFixture;

beforeAll(async () => {
  fixture = await setupAutomationClientSecretFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

/** Status of a client_credentials token request authenticated with the given secret. */
const tokenStatus = async (clientId: string, secret: string): Promise<number> => {
  await waitForDomainReady(fixture.domainId);
  const oidcConfig = (await waitForOidcReady(fixture.domainKey)).body;
  const response = await performPost(oidcConfig.token_endpoint, '', 'grant_type=client_credentials', {
    'Content-Type': 'application/x-www-form-urlencoded',
    Authorization: 'Basic ' + getBase64BasicAuth(clientId, secret),
  });
  return response.status;
};

const waitForTokenStatus = (clientId: string, secret: string, status: number) =>
  retryUntil(
    () => tokenStatus(clientId, secret),
    (actual) => actual === status,
    { timeoutMillis: 30000, intervalMillis: 500 },
  );

describe('Automation API - Client secrets', () => {
  it('should create a secret the gateway accepts', async () => {
    const app = await fixture.createServiceApplication();
    const key = fixture.newSecretKey();
    const value = newSecretValue();

    const response = await fixture.putSecret(app.key, key, value);

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ key });
    expect(await waitForTokenStatus(app.clientId, value, 200)).toBe(200);
  });

  it('should keep a secret applied again with the same value', async () => {
    const app = await fixture.createServiceApplication();
    const key = fixture.newSecretKey();
    const value = newSecretValue();
    expect((await fixture.putSecret(app.key, key, value)).status).toBe(200);

    const again = await fixture.putSecret(app.key, key, value);

    expect(again.status).toBe(200);
    expect(again.body).toEqual({ key });
    expect((await fixture.client.listClientSecrets(fixture.domainKey, app.key)).body).toEqual([{ key }]);
  });

  it('should reject a different value for an existing secret', async () => {
    const app = await fixture.createServiceApplication();
    const key = fixture.newSecretKey();
    expect((await fixture.putSecret(app.key, key, newSecretValue())).status).toBe(200);

    const response = await fixture.putSecret(app.key, key, newSecretValue());

    expect(response.status).toBe(400);
    expect(response.body.message).toEqual('Client secret value is immutable; use a new key to rotate');
  });

  it('should reject a value shorter than 43 characters', async () => {
    const app = await fixture.createServiceApplication();

    const response = await fixture.putSecret(app.key, fixture.newSecretKey(), 'a'.repeat(42));

    expect(response.status).toBe(400);
  });

  it('should return 404 for an unknown application', async () => {
    const response = await fixture.putSecret(fixture.newApplicationKey(), fixture.newSecretKey(), newSecretValue());

    expect(response.status).toBe(404);
  });

  it('should list only automation secrets, never with a value', async () => {
    const app = await fixture.createServiceApplication();
    const [first, second] = [fixture.newSecretKey(), fixture.newSecretKey()].sort();
    const value = newSecretValue();
    await fixture.putSecret(app.key, second, value);
    await fixture.putSecret(app.key, first, newSecretValue());
    const managementApp = await fixture.managementApplication(app.key);
    await createClientSecret(fixture.domainId, fixture.accessToken, managementApp.id, { name: 'console' });

    const response = await fixture.client.listClientSecrets(fixture.domainKey, app.key);

    expect(response.status).toBe(200);
    expect(response.body.map((secret) => secret.key)).toEqual([first, second]);
    expect(JSON.stringify(response.body)).not.toContain(value);
  });

  it('should read and delete a secret created through the Management API by id', async () => {
    const app = await fixture.createServiceApplication();
    const managementApp = await fixture.managementApplication(app.key);
    const consoleSecret = await createClientSecret(fixture.domainId, fixture.accessToken, managementApp.id, { name: 'console' });
    const ref = `id:${consoleSecret.id}`;

    const get = await fixture.client.getClientSecret(fixture.domainKey, app.key, ref);
    expect(get.status).toBe(200);
    expect(get.body.key).toEqual(ref);

    expect((await fixture.client.deleteClientSecret(fixture.domainKey, app.key, ref)).status).toBe(204);
    expect(await listClientSecrets(fixture.domainId, fixture.accessToken, managementApp.id)).toEqual([]);
  });

  it('should persist nothing on a dry run', async () => {
    const app = await fixture.createServiceApplication();
    const key = fixture.newSecretKey();

    const response = await fixture.putSecret(app.key, key, newSecretValue(), '?dryRun=true');

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ key });
    expect((await fixture.client.getClientSecret(fixture.domainKey, app.key, key)).status).toBe(404);
  });

  it('should revoke a deleted secret, even the last one', async () => {
    const app = await fixture.createServiceApplication();
    const key = fixture.newSecretKey();
    const value = newSecretValue();
    await fixture.putSecret(app.key, key, value);
    expect(await waitForTokenStatus(app.clientId, value, 200)).toBe(200);

    expect((await fixture.client.deleteClientSecret(fixture.domainKey, app.key, key)).status).toBe(204);

    expect((await fixture.client.listClientSecrets(fixture.domainKey, app.key)).body).toEqual([]);
    expect(await waitForTokenStatus(app.clientId, value, 401)).toBe(401);
    expect((await fixture.client.deleteClientSecret(fixture.domainKey, app.key, key)).status).toBe(204);
  });

  it('should keep every concurrent write to the application', async () => {
    const app = await fixture.createServiceApplication();
    const keys = Array.from({ length: 5 }, () => fixture.newSecretKey());

    const responses = await Promise.all([
      ...keys.map((key) => fixture.putSecret(app.key, key, newSecretValue())),
      fixture.client.putApplication(
        fixture.domainKey,
        buildServiceApplicationDef(app.key, fixture.signingCertificateKey, { description: 'updated concurrently' }),
      ),
    ]);

    expect(responses.map((response) => response.status)).toEqual([200, 200, 200, 200, 200, 200]);
    const listed = (await fixture.client.listClientSecrets(fixture.domainKey, app.key)).body.map((secret) => secret.key);
    expect(listed).toEqual([...keys].sort());
    expect((await fixture.client.getApplication(fixture.domainKey, app.key)).body.description).toEqual('updated concurrently');
  });

  it('should reject a secret beyond the limit', async () => {
    const app = await fixture.createServiceApplication();
    for (let i = 0; i < SECRETS_MAX; i++) {
      expect((await fixture.putSecret(app.key, fixture.newSecretKey(), newSecretValue())).status).toBe(200);
    }

    const response = await fixture.putSecret(app.key, fixture.newSecretKey(), newSecretValue());

    expect(response.status).toBe(400);
  });
});
