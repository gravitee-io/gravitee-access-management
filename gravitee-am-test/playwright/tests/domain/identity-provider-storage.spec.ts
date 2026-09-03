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
import { test, expect } from '../../fixtures/base.fixture';
import { linkJira } from '../../utils/jira';
import { IdentityProviderSettingsPage } from '../../pages/identity-provider-settings.page';
import { createIdp, deleteIdp } from '@management-commands/idp-management-commands';

const MONGO_IDP_TYPE = 'mongo-am-idp';

const mongoIdpBody = (name: string, useSystemCluster = true) => ({
  external: false,
  type: MONGO_IDP_TYPE,
  domainWhitelist: [],
  name,
  configuration: JSON.stringify({
    uri: 'mongodb://localhost:27017',
    host: 'localhost',
    port: 27017,
    enableCredentials: false,
    useSystemCluster,
    database: 'my-own-database',
    usersCollection: 'my-own-users',
    findUserByUsernameQuery: '{username: ?}',
    findUserByEmailQuery: '{email: ?}',
    usernameField: 'username',
    passwordField: 'password',
    passwordEncoder: 'BCrypt',
  }),
});

const DATASOURCE_ADVICE = 'Prefer a data source';

const IMMUTABLE_HINT = 'Once saved, this option cannot be changed.';

const readsPinStorage = async (request, adminToken: string): Promise<boolean> => {
  const response = await request.get(`${process.env.AM_MANAGEMENT_URL}/management/platform/configuration/installation`, {
    headers: { Authorization: `Bearer ${adminToken}` },
  });
  return (await response.json())?.systemClusterRestricted === true;
};

/** Two regimes share this file: each test skips on the installation the other one covers. */
test.describe('Identity provider storage', () => {
  test('AM-7581: creation screen tells the administrator which fields the platform will set', async ({
    page,
    adminToken,
    testDomain,
    request,
  }, testInfo) => {
    linkJira(testInfo, 'AM-7581');
    test.skip(!(await readsPinStorage(request, adminToken)), 'The installation does not pin identity provider storage');

    const providerPage = new IdentityProviderSettingsPage(page);
    await providerPage.navigateToCreation(testDomain.id);
    await providerPage.chooseType('Mongo DB');

    await expect(providerPage.hint('The MongoDB database used to run query to search for users.')).toContainText(
      'The platform sets this value when "use system cluster" is selected.',
    );
    await expect(providerPage.hint('The collection which is containing all the users.')).toContainText(
      'The platform sets this value when "use system cluster" is selected.',
    );
  });

  test('AM-7581: edit screen locks the storage of a pinned provider', async ({ page, adminToken, testDomain, request }, testInfo) => {
    linkJira(testInfo, 'AM-7581');
    test.skip(!(await readsPinStorage(request, adminToken)), 'The installation does not pin identity provider storage');

    const idp = await createIdp(testDomain.id, adminToken, mongoIdpBody('pinned-provider'));
    try {
      const providerPage = new IdentityProviderSettingsPage(page);
      await providerPage.navigateToSettings(testDomain.id, idp.id);

      await expect(providerPage.databaseField).toHaveAttribute('readonly', 'true');
      await expect(providerPage.usersCollectionField).toHaveAttribute('readonly', 'true');
      await expect(providerPage.usersCollectionField).toHaveValue(`idp_${idp.id}`);

      // The widget drops the form binding for a boolean marked readonly, leaving it disabled.
      await expect(providerPage.systemClusterToggle).toBeChecked();
      await expect(providerPage.systemClusterToggle).toBeDisabled();
    } finally {
      await deleteIdp(testDomain.id, adminToken, idp.id);
    }
  });

  test('edit screen leaves the toggle open when the platform does not own the storage', async ({
    page,
    adminToken,
    testDomain,
    request,
  }) => {
    test.skip(await readsPinStorage(request, adminToken), 'The installation pins storage, which locks the toggle');

    const idp = await createIdp(testDomain.id, adminToken, mongoIdpBody('system-cluster-provider'));
    try {
      const providerPage = new IdentityProviderSettingsPage(page);
      await providerPage.navigateToSettings(testDomain.id, idp.id);

      await expect(providerPage.systemClusterToggle).toBeChecked();
      await expect(providerPage.systemClusterToggle).toBeEnabled();
      await expect(page.getByText(IMMUTABLE_HINT)).toHaveCount(0);
    } finally {
      await deleteIdp(testDomain.id, adminToken, idp.id);
    }
  });

  test('the creation wizard recommends a data source', async ({ page, testDomain }) => {
    const providerPage = new IdentityProviderSettingsPage(page);
    await providerPage.navigateToCreation(testDomain.id);
    await providerPage.chooseType('Mongo DB');

    await expect(providerPage.descriptionPanel).toContainText(DATASOURCE_ADVICE);
  });

  test('the edit screen repeats the storage guidance beside the form', async ({ page, adminToken, testDomain }) => {
    const idp = await createIdp(testDomain.id, adminToken, mongoIdpBody('guidance-provider'));
    try {
      const providerPage = new IdentityProviderSettingsPage(page);
      await providerPage.navigateToSettings(testDomain.id, idp.id);

      await expect(providerPage.descriptionPanel).toContainText(DATASOURCE_ADVICE);
    } finally {
      await deleteIdp(testDomain.id, adminToken, idp.id);
    }
  });
});
