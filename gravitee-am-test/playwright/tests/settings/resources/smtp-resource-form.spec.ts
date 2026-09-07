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
import { test, expect } from '../../../fixtures/base.fixture';
import { linkJira } from '../../../utils/jira';
import { uniqueTestName } from '../../../utils/fixture-helpers';
import { DomainResourcesPage } from '../../../pages/domain-resources.page';
import { createResource, getResource, listResources } from '@management-commands/resource-management-commands';

const SMTP_HOST = 'smtp';
const SMTP_PORT = 5025;
const SMTP_FROM = 'admin@test.com';

/** The management API replaces stored secrets with this before returning them. */
const MASKED_SECRET = '********';

/** The stored configuration, as the management API reports it. */
async function storedConfiguration(domainId: string, token: string, resourceId: string): Promise<Record<string, any>> {
  const resource: any = await getResource(domainId, token, resourceId);
  return JSON.parse(resource.configuration);
}

async function findResourceIdByName(domainId: string, token: string, name: string): Promise<string> {
  const resources: any = await listResources(domainId, token);
  const match = resources.find((r: any) => r.name === name);
  expect(match, `resource "${name}" was not created`).toBeTruthy();
  return match.id;
}

test.describe('SMTP resource form', () => {
  test('AM-7642: the credential fields appear and disappear with the authentication flag', async ({ page, testDomain }, testInfo) => {
    linkJira(testInfo, 'AM-7642');

    const resourcesPage = new DomainResourcesPage(page);
    await resourcesPage.navigateToCreate(testDomain.id);
    await resourcesPage.selectResourceType(/smtp/i);
    await resourcesPage.clickNext();

    // `host` has no schema condition, so it anchors the assertions below: it proves the
    // form rendered, which is what makes the absence of the credential fields meaningful.
    // A false condition removes a control from the DOM rather than hiding it.
    await expect(resourcesPage.field('host')).toBeVisible();
    await expect(resourcesPage.field('username')).toHaveCount(0);
    await expect(resourcesPage.field('password')).toHaveCount(0);
    await expect(resourcesPage.select('authenticationType')).toHaveCount(0);

    await resourcesPage.toggleCheckbox('authentication');

    await expect(resourcesPage.field('username')).toBeVisible();
    await expect(resourcesPage.field('password')).toBeVisible();
    await expect(resourcesPage.select('authenticationType')).toBeVisible();

    await resourcesPage.toggleCheckbox('authentication');

    await expect(resourcesPage.field('host')).toBeVisible();
    await expect(resourcesPage.field('username')).toHaveCount(0);
    await expect(resourcesPage.field('password')).toHaveCount(0);
  });

  test('AM-7642: the OAuth2 branch replaces the basic credential fields', async ({ page, testDomain }, testInfo) => {
    linkJira(testInfo, 'AM-7642');

    const resourcesPage = new DomainResourcesPage(page);
    await resourcesPage.navigateToCreate(testDomain.id);
    await resourcesPage.selectResourceType(/smtp/i);
    await resourcesPage.clickNext();

    await resourcesPage.toggleCheckbox('authentication');
    await expect(resourcesPage.field('username')).toBeVisible();
    await resourcesPage.setField('username', 'basic-user');
    await resourcesPage.setField('password', 'basic-password');

    await resourcesPage.chooseOption('authenticationType', /oauth2/i);

    await expect(resourcesPage.field('oauth2ClientId')).toBeVisible();
    await expect(resourcesPage.field('tokenEndpoint')).toBeVisible();
    await expect(resourcesPage.field('username')).toHaveCount(0);
    await expect(resourcesPage.field('password')).toHaveCount(0);

    // Returning to basic restores what was typed: the schema stops rendering the control,
    // it does not clear the underlying model.
    await resourcesPage.chooseOption('authenticationType', /basic/i);
    await expect(resourcesPage.field('username')).toHaveValue('basic-user');
  });

  test('AM-7642: an explicit authentication flag is stored even when credentials were entered first', async ({
    page,
    testDomain,
    adminToken,
  }, testInfo) => {
    linkJira(testInfo, 'AM-7642');

    const name = uniqueTestName('smtp-unticked');
    const resourcesPage = new DomainResourcesPage(page);
    await resourcesPage.navigateToCreate(testDomain.id);
    await resourcesPage.selectResourceType(/smtp/i);
    await resourcesPage.clickNext();

    await expect(resourcesPage.nameInput).toBeVisible();
    await resourcesPage.nameInput.fill(name);
    await resourcesPage.fillSmtpBasics(SMTP_HOST, SMTP_FROM);

    await resourcesPage.toggleCheckbox('authentication');
    await expect(resourcesPage.field('username')).toBeVisible();
    await resourcesPage.setField('username', 'the-user');
    await resourcesPage.setField('password', 'the-password');
    await resourcesPage.toggleCheckbox('authentication');

    await resourcesPage.clickCreate();

    const resourceId = await findResourceIdByName(testDomain.id, adminToken, name);
    const configuration = await storedConfiguration(testDomain.id, adminToken, resourceId);

    // The Console always submits the flag, so SmtpResourceConfiguration reads it directly
    // and never falls back to inferring authentication from the credentials (AM-7622).
    expect(configuration).toHaveProperty('authentication', false);

    // The credentials are retained rather than cleared. That is safe only while the explicit
    // flag above is present, which is why this test pins the two together.
    expect(configuration.username).toBe('the-user');
  });

  test('AM-7642: an existing password survives an unrelated edit', async ({ page, testDomain, adminToken }, testInfo) => {
    linkJira(testInfo, 'AM-7642');

    const resource: any = await createResource(testDomain.id, adminToken, {
      type: 'smtp-am-resource',
      name: uniqueTestName('smtp-round-trip'),
      configuration: JSON.stringify({
        host: SMTP_HOST,
        port: SMTP_PORT,
        from: SMTP_FROM,
        protocol: 'smtp',
        authentication: true,
        authenticationType: 'basic',
        username: 'original-user',
        password: 'original-password',
        startTls: false,
      }),
    });

    const resourcesPage = new DomainResourcesPage(page);
    await resourcesPage.navigateToDetail(testDomain.id, resource.id);

    // The API masks the secret, so the form renders the mask rather than the password.
    await expect(resourcesPage.field('password')).toHaveValue(MASKED_SECRET);

    await resourcesPage.setField('from', 'changed@test.com');
    await resourcesPage.save();
    await resourcesPage.expectSnackbar(/updated/i);

    const configuration = await storedConfiguration(testDomain.id, adminToken, resource.id);
    // `from` changing proves the update was applied, so the untouched fields below are a
    // genuine round trip rather than a save that silently did nothing.
    expect(configuration.from).toBe('changed@test.com');
    expect(configuration.username).toBe('original-user');
    expect(configuration.authentication).toBe(true);
  });
});
