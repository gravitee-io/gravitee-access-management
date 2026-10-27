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
import { updateApplication } from '@management-commands/application-management-commands';
import { createClientSecret, listClientSecrets } from '@management-commands/client-secrets-management-commands';
import { waitForOidcReady } from '@management-commands/domain-management-commands';
import { waitForDomainReady, waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { introspectToken, requestClientCredentialsToken } from '@gateway-commands/oauth-oidc-commands';
import { getBase64BasicAuth } from '@gateway-commands/utils';
import { retryUntil } from '@utils-commands/retry';
import { uniqueName } from '@utils-commands/misc';
import { setup } from '../../test-fixture';
import { buildAutomationCertificateDef, buildInlineIdpDef, buildSystemAutomationDef } from './fixtures/automation-definitions';
import {
  AutomationApplicationFixture,
  buildAutomationApplicationDef,
  setupAutomationApplicationFixture,
} from './fixtures/automation-application-fixture';

setup(180000);

let fixture: AutomationApplicationFixture;
/** A certificate shared by the tests: a domain cannot hold the same test keystore twice. */
let signingCertificateKey: string;

beforeAll(async () => {
  fixture = await setupAutomationApplicationFixture();
  signingCertificateKey = newKey('signcert');
  await createCertificate(buildAutomationCertificateDef({ key: signingCertificateKey, keystore: 0 }));
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

const newKey = (prefix: string): string => uniqueName(prefix, true).toLowerCase();

/** Creates a certificate through the Automation API, removed with the domain. */
const createCertificate = async (definition: object): Promise<void> => {
  const response = await fixture.client.putCertificate(fixture.domainKey, definition);
  expect(response.status).toBe(200);
};

/** A client secret created through the Management API: the Automation API never generates one. */
const createSecret = async (key: string): Promise<{ clientId: string; secret: string }> => {
  const app = await fixture.managementApplication(key);
  const secret = await createClientSecret(fixture.domainId, fixture.accessToken, app.id, { name: 'jest' });
  return { clientId: app.settings.oauth.clientId, secret: secret.secret };
};

describe('Automation API - Applications', () => {
  it('should create an application with a generated clientId and no client secret', async () => {
    const key = fixture.newApplicationKey();

    const created = await fixture.createApplication({ key });

    expect(created.clientId).toEqual(expect.any(String));
    expect(JSON.stringify(created)).not.toContain('clientSecret');
    const get = await fixture.client.getApplication(fixture.domainKey, key);
    expect(get.status).toBe(200);
    expect(get.body.clientId).toEqual(created.clientId);
    const app = await fixture.managementApplication(key);
    expect(await listClientSecrets(fixture.domainId, fixture.accessToken, app.id)).toEqual([]);
  });

  it('should round-trip GET then PUT without change', async () => {
    const key = fixture.newApplicationKey();
    await fixture.createApplication({ key });
    const { updatedAt, ...before } = (await fixture.client.getApplication(fixture.domainKey, key)).body;

    const put = await fixture.client.putApplication(fixture.domainKey, before);
    expect(put.status).toBe(200);

    const { updatedAt: updatedAgain, ...after } = (await fixture.client.getApplication(fixture.domainKey, key)).body;
    expect(after).toEqual(before);
    expect(updatedAgain >= updatedAt).toBe(true);
  });

  it('should store references to a certificate and an identity provider that do not exist yet', async () => {
    const key = fixture.newApplicationKey();
    const certificateKey = newKey('latecert');
    const identityKey = newKey('lateidp');

    const created = await fixture.createApplication({
      key,
      certificate: certificateKey,
      identityProviders: [{ identity: identityKey, priority: 0 }],
    });
    expect(created.certificate).toEqual(certificateKey);
    expect(created.identityProviders).toEqual([{ identity: identityKey, priority: 0 }]);

    // the references resolve once the resources exist, without updating the application
    await createCertificate(buildAutomationCertificateDef({ key: certificateKey, keystore: 1 }));
    const idp = await fixture.client.putIdentity(
      fixture.domainKey,
      buildInlineIdpDef({ key: identityKey, users: [{ username: 'alice', password: 'P@ssw0rd!' }] }),
    );
    expect(idp.status).toBe(200);
    const app = await fixture.managementApplication(key);
    expect(app.certificate).toEqual(await fixture.certificateId(certificateKey));
    expect([...app.identityProviders].map((appIdp) => appIdp.identity)).toEqual([await fixture.identityProviderId(identityKey)]);
  });

  it('should read a certificate changed through the Management API back as an id: reference', async () => {
    const key = fixture.newApplicationKey();
    // the second certificate is the domain's system one, built from gravitee.yml
    await createCertificate(buildSystemAutomationDef(newKey('syscert')));
    await fixture.createApplication({ key, certificate: signingCertificateKey });
    const app = await fixture.managementApplication(key);

    // a Management API save that keeps the certificate keeps the key
    await updateApplication(
      fixture.domainId,
      fixture.accessToken,
      { description: 'saved in the Console', certificate: app.certificate },
      app.id,
    );
    expect((await fixture.client.getApplication(fixture.domainKey, key)).body.certificate).toEqual(signingCertificateKey);

    const secondId = await fixture.systemCertificateId();
    await updateApplication(fixture.domainId, fixture.accessToken, { certificate: secondId }, app.id);
    expect((await fixture.client.getApplication(fixture.domainKey, key)).body.certificate).toEqual(`id:${secondId}`);
  });

  it('should reject an unknown scope', async () => {
    const response = await fixture.client.putApplication(
      fixture.domainKey,
      buildAutomationApplicationDef({
        key: fixture.newApplicationKey(),
        settings: { oauth: { redirectUris: ['https://app.example.com/callback'], scopeSettings: [{ scope: 'unknown-scope' }] } },
      }),
    );

    expect(response.status).toBe(400);
  });

  it('should reject a clientId already used in the domain', async () => {
    const existing = await fixture.createApplication({ key: fixture.newApplicationKey() });

    const response = await fixture.client.putApplication(
      fixture.domainKey,
      buildAutomationApplicationDef({ key: fixture.newApplicationKey(), clientId: existing.clientId }),
    );

    expect(response.status).toBe(400);
  });

  it('should reject a change of clientId', async () => {
    const key = fixture.newApplicationKey();
    await fixture.createApplication({ key });

    const response = await fixture.client.putApplication(
      fixture.domainKey,
      buildAutomationApplicationDef({ key, clientId: 'another-client-id' }),
    );

    expect(response.status).toBe(400);
  });

  it('should change the type and apply the new type defaults', async () => {
    const key = fixture.newApplicationKey();
    await fixture.createApplication({ key });

    const response = await fixture.client.putApplication(fixture.domainKey, buildAutomationApplicationDef({ key, type: 'service' }));

    expect(response.status).toBe(200);
    expect(response.body.type).toEqual('service');
    expect(response.body.settings.oauth.grantTypes).toEqual(['client_credentials']);
  });

  it('should validate a dry-run without persisting', async () => {
    const key = fixture.newApplicationKey();

    const response = await fixture.client.putApplication(
      fixture.domainKey,
      buildAutomationApplicationDef({
        key,
        settings: { oauth: { redirectUris: ['https://app.example.com/callback'], scopeSettings: [{ scope: 'unknown-scope' }] } },
      }),
      '?dryRun=true',
    );

    expect(response.status).toBe(200);
    expect(response.body.dryRunErrors).toEqual([{ severity: 'error', message: expect.any(String) }]);
    expect((await fixture.client.getApplication(fixture.domainKey, key)).status).toBe(404);
  });

  it('should delete an application', async () => {
    const key = fixture.newApplicationKey();
    await fixture.createApplication({ key });

    expect((await fixture.client.deleteApplication(fixture.domainKey, key)).status).toBe(204);
    expect((await fixture.client.getApplication(fixture.domainKey, key)).status).toBe(404);
    expect((await fixture.client.deleteApplication(fixture.domainKey, key)).status).toBe(204);
  });
});

describe('Automation API - Applications on the gateway', () => {
  it('should revoke the application tokens when it is disabled', async () => {
    const serviceDef = (key: string, enabled = true) =>
      buildAutomationApplicationDef({ key, type: 'service', certificate: signingCertificateKey, enabled, settings: {} });
    const appKey = fixture.newApplicationKey();
    const introspectorKey = fixture.newApplicationKey();
    await fixture.createApplication(serviceDef(appKey));
    await fixture.createApplication(serviceDef(introspectorKey));
    const app = await createSecret(appKey);
    const introspector = await createSecret(introspectorKey);
    await waitForDomainReady(fixture.domainId);
    const oidcConfig = (await waitForOidcReady(fixture.domainKey)).body;

    const accessToken = await requestClientCredentialsToken(app.clientId, app.secret, oidcConfig);
    const introspect = () =>
      introspectToken(oidcConfig.introspection_endpoint, accessToken, getBase64BasicAuth(introspector.clientId, introspector.secret));
    expect(await introspect()).toMatchObject({ active: true });

    await waitForSyncAfter(fixture.domainId, () => fixture.client.putApplication(fixture.domainKey, serviceDef(appKey, false)));

    const revoked = await retryUntil(introspect, (result) => result.active === false, { timeoutMillis: 30000, intervalMillis: 500 });
    expect(revoked).toMatchObject({ active: false });
  });
});
