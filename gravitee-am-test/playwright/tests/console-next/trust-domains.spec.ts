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
import request from 'supertest';
import { test, expect } from '../../fixtures/base.fixture';
import { getDomainManagerUrl } from '@management-commands/service/utils';
import { createKeyMaterial, jwkSetOf } from '../../utils/token-exchange-helpers';

const trustedDomainsUrl = (domainId: string) => `${getDomainManagerUrl(domainId)}/trusted-domains`;

const listTrustedDomains = async (domainId: string, adminToken: string) =>
  (await request(trustedDomainsUrl(domainId)).get('').set('Authorization', `Bearer ${adminToken}`).expect(200)).body;

/** The trusted domains of a domain in the React console (gravitee-am-ui-next). Runs in the console-next project only. */
test.describe('React console: trusted domains', () => {
  const trustDomainsUrl = (hrid: string) => `/environments/${process.env.AM_DEF_ENV_HRID}/domains/${hrid}/settings/trust-domains`;

  test('creates a token exchange issuer with an inline JWK set, then opens it', async ({ page, adminToken, testDomain }) => {
    const jwkSet = jwkSetOf(createKeyMaterial().certificatePem);
    await page.goto(`${trustDomainsUrl(testDomain.hrid)}/domains/new`);

    await page.getByRole('textbox', { name: 'Issuer URL' }).fill('https://sso.acme.com');
    await expect(page.getByRole('textbox', { name: 'Name' })).toHaveValue('https-sso.acme.com');
    await page.getByRole('combobox', { name: 'Key source' }).click();
    await page.getByRole('option', { name: 'JWK Set' }).click();
    await page.getByRole('textbox', { name: 'JWK set' }).fill(JSON.stringify(jwkSet));
    const createRequest = page.waitForRequest(
      (request) => request.method() === 'POST' && request.url().endsWith(`/domains/${testDomain.id}/trusted-domains`),
    );
    await page.getByRole('button', { name: 'Create' }).click();

    expect((await createRequest).postDataJSON()).toEqual({
      name: 'https-sso.acme.com',
      keyMaterial: { source: 'JWK_SET', jwkSet, refreshIntervalSeconds: 300 },
      domainIdentifier: 'https://sso.acme.com',
      spiffe: { spiffeTrustDomain: '', allowedAlgorithms: [] },
      tokenExchange: { enabled: true, scopeMappings: {}, userBindingEnabled: false, userBindingCriteria: [] },
      crossAppAccess: { enabled: false },
    });
    const [created] = await listTrustedDomains(testDomain.id, adminToken);
    expect(created).toMatchObject({
      name: 'https-sso.acme.com',
      domainIdentifier: 'https://sso.acme.com',
      keyMaterial: { source: 'jwk_set' },
    });
    await expect(page).toHaveURL(new RegExp(`/trust-domains/domains/${created.id}$`));
    await expect(page.getByRole('heading', { level: 1, name: 'https-sso.acme.com' })).toBeVisible();
  });

  test('deletes a trusted domain from the list after the confirmation', async ({ page, adminToken, testDomain }) => {
    await request(trustedDomainsUrl(testDomain.id))
      .post('')
      .set('Authorization', `Bearer ${adminToken}`)
      .send({
        name: 'pw-next-acme',
        domainIdentifier: 'https://sso.acme.com',
        keyMaterial: { source: 'JWK_SET', jwkSet: jwkSetOf(createKeyMaterial().certificatePem), refreshIntervalSeconds: 300 },
        tokenExchange: { enabled: true },
        crossAppAccess: { enabled: false },
      })
      .expect(201);
    await page.goto(`${trustDomainsUrl(testDomain.hrid)}/domains`);

    await expect(page.getByRole('row', { name: /pw-next-acme/ }).getByText('Token Exchange')).toBeVisible();
    await page.getByRole('button', { name: 'Actions for pw-next-acme' }).click();
    await page.getByRole('menuitem', { name: 'Delete' }).click();
    const deleteResponse = page.waitForResponse(
      (response) => response.request().method() === 'DELETE' && /\/trusted-domains\/[^/]+$/.test(response.url()),
    );
    await page.getByRole('dialog', { name: 'Delete Trusted Domain' }).getByRole('button', { name: 'Delete' }).click();
    expect((await deleteResponse).ok()).toBe(true);

    await expect(page.getByText('No trusted domains yet')).toBeVisible();
    expect(await listTrustedDomains(testDomain.id, adminToken)).toEqual([]);
  });

  test('adds Cross App Access to a trusted domain and saves it', async ({ page, adminToken, testDomain }) => {
    const created = (
      await request(trustedDomainsUrl(testDomain.id))
        .post('')
        .set('Authorization', `Bearer ${adminToken}`)
        .send({
          name: 'pw-next-xaa',
          domainIdentifier: 'https://sso.acme.com',
          keyMaterial: { source: 'JWK_SET', jwkSet: jwkSetOf(createKeyMaterial().certificatePem), refreshIntervalSeconds: 300 },
          tokenExchange: { enabled: true },
          crossAppAccess: { enabled: false },
        })
        .expect(201)
    ).body;
    await page.goto(`${trustDomainsUrl(testDomain.hrid)}/domains/${created.id}`);

    await page.getByRole('checkbox', { name: 'Cross App Access' }).click();
    await page.getByRole('textbox', { name: 'Name', exact: true }).last().fill('Calendar');
    await page.getByRole('textbox', { name: 'Resource URI' }).fill('https://calendar.acme.com');
    await page.getByRole('button', { name: 'Add resource server' }).click();
    const putResponse = page.waitForResponse(
      (response) => response.request().method() === 'PUT' && response.url().endsWith(`/trusted-domains/${created.id}`),
    );
    await page.getByRole('button', { name: 'Save' }).click();
    expect((await putResponse).ok()).toBe(true);

    const [saved] = await listTrustedDomains(testDomain.id, adminToken);
    expect(saved.crossAppAccess).toMatchObject({
      enabled: true,
      resourceServers: [{ name: 'Calendar', resource: 'https://calendar.acme.com' }],
    });
    await expect(page.getByRole('row', { name: /Calendar/ })).toBeVisible();
  });

  test('saves the key retrieval settings of the domain', async ({ page, adminToken, testDomain }) => {
    await page.goto(`${trustDomainsUrl(testDomain.hrid)}/key-retrieval`);

    await page.getByRole('switch', { name: 'Allow JWKS URLs resolving to private IP addresses' }).click();
    await page.getByRole('spinbutton', { name: 'Cache max entries' }).fill('80');
    const patchRequest = page.waitForRequest(
      (request) => request.method() === 'PATCH' && request.url().endsWith(`/domains/${testDomain.id}`),
    );
    const patchResponse = page.waitForResponse(
      (response) => response.request().method() === 'PATCH' && response.url().endsWith(`/domains/${testDomain.id}`),
    );
    await page.getByRole('button', { name: 'Save' }).click();

    expect((await patchRequest).postDataJSON()).toEqual({
      keyRetrievalSettings: {
        allowUnsecuredHttpUri: false,
        allowPrivateIpAddress: true,
        fetchTimeoutMs: 5000,
        maxResponseSizeKb: 32,
        cacheTtlSeconds: 300,
        cacheMaxEntries: 80,
      },
    });
    expect((await patchResponse).ok()).toBe(true);
    const domain = (await request(getDomainManagerUrl(testDomain.id)).get('').set('Authorization', `Bearer ${adminToken}`).expect(200))
      .body;
    expect(domain.keyRetrievalSettings).toMatchObject({ allowPrivateIpAddress: true, cacheMaxEntries: 80 });
  });
});
