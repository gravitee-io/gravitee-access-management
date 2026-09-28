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
import { afterAll, afterEach, beforeAll, describe, expect, it } from '@jest/globals';
import { setup } from '../../test-fixture';
import { buildAutomationCertificateDef } from './fixtures/automation-definitions';
import {
  configurationOf,
  buildSecretInlineIdpDef,
  buildSecretKafkaReporterDef,
  newKey,
  SENSITIVE_VALUES_TEST,
  SensitiveValuesFixture,
  setupSensitiveValuesFixture,
} from './fixtures/sensitive-values-fixture';

setup(120000);

const { MASK, PASSWORD } = SENSITIVE_VALUES_TEST;

let fixture: SensitiveValuesFixture;

beforeAll(async () => {
  fixture = await setupSensitiveValuesFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

afterEach(async () => {
  await fixture?.cleanUpResources();
});

describe('Automation API - sensitive values in identity providers', () => {
  it('should mask inline user passwords in the PUT, GET and list responses', async () => {
    const key = newKey('secretidp');
    const put = await fixture.putIdentity(buildSecretInlineIdpDef(key));
    const get = await fixture.client.getIdentity(fixture.domainKey, key);
    const list = await fixture.client.listIdentities(fixture.domainKey);

    expect(put.status).toBe(200);
    expect(configurationOf(put.body).users[0]).toMatchObject({ username: 'alice', password: MASK });
    expect(configurationOf(get.body).users[0]).toMatchObject({ username: 'alice', password: MASK });
    const listed = list.body.find((idp) => idp.key === key);
    expect(configurationOf(listed).users[0]).toMatchObject({ username: 'alice', password: MASK });
    expect(JSON.stringify([put.body, get.body, list.body])).not.toContain(PASSWORD);
  });

  it('should accept its masked GET response back on update', async () => {
    const key = newKey('secretidp');
    await fixture.putIdentity(buildSecretInlineIdpDef(key));
    const { body } = await fixture.client.getIdentity(fixture.domainKey, key);

    const response = await fixture.putIdentity({ key, name: body.name, type: body.type, configuration: body.configuration });

    expect(response.status).toBe(200);
    expect(configurationOf(response.body).users[0].password).toEqual(MASK);
  });

  it('should reject a masked password on create (400)', async () => {
    const key = newKey('secretidp');

    const response = await fixture.putIdentity(buildSecretInlineIdpDef(key, MASK));

    expect(response.status).toBe(400);
    expect(JSON.stringify(response.body)).toContain('configuration/users/0/password');
    expect((await fixture.client.getIdentity(fixture.domainKey, key)).status).toBe(404);
  });
});

describe('Automation API - sensitive values in certificates', () => {
  it('should mask the keystore file and passwords but not the alias', async () => {
    const key = newKey('secretcert');

    const response = await fixture.putCertificate(buildAutomationCertificateDef({ key }));

    expect(response.status).toBe(200);
    const configuration = configurationOf(response.body);
    expect(configuration).toMatchObject({ jks: MASK, storepass: MASK, keypass: MASK });
    expect(configuration.alias).not.toEqual(MASK);
  });

  it('should accept its masked GET response back on update', async () => {
    const key = newKey('secretcert');
    await fixture.putCertificate(buildAutomationCertificateDef({ key }));
    const { body } = await fixture.client.getCertificate(fixture.domainKey, key);

    const response = await fixture.putCertificate({ key, name: body.name, type: body.type, configuration: body.configuration });

    expect(response.status).toBe(200);
    expect(configurationOf(response.body)).toMatchObject({ jks: MASK, storepass: MASK, keypass: MASK });
  });
});

describe('Automation API - sensitive values in reporters', () => {
  it('should mask the password but not the username', async () => {
    const response = await fixture.putReporter(buildSecretKafkaReporterDef(newKey('secretrep'), PASSWORD));

    expect(response.status).toBe(200);
    expect(configurationOf(response.body)).toMatchObject({ username: 'am', password: MASK });
  });

  it('should reject a masked password on create (400)', async () => {
    const response = await fixture.putReporter(buildSecretKafkaReporterDef(newKey('secretrep'), MASK));

    expect(response.status).toBe(400);
    expect(JSON.stringify(response.body)).toContain('configuration/password');
  });
});

describe('Automation API - sensitive values in audits', () => {
  it('should audit identity provider creation and update without the secret', async () => {
    const { domainRef, waitForAudits } = await fixture.createAuditedDomain();
    const definition = buildSecretInlineIdpDef(newKey('auditidp'));
    const from = Date.now();

    expect((await fixture.client.putIdentity(domainRef, definition)).status).toBe(200);
    expect((await fixture.client.putIdentity(domainRef, { ...definition, name: `${definition.name} v2` })).status).toBe(200);

    const audits = await waitForAudits(from, ['IDENTITY_PROVIDER_CREATED', 'IDENTITY_PROVIDER_UPDATED']);
    audits.forEach((audit) => {
      expect(audit).toMatchObject({ actor: { alternativeId: process.env.AM_ADMIN_USERNAME }, outcome: { status: 'success' } });
    });
    expect(JSON.stringify(audits)).not.toContain(PASSWORD);
  });
});
