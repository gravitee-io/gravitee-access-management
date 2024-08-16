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
import fetch from 'cross-fetch';
import * as faker from 'faker';
import { afterAll, beforeAll, expect } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createDomain, deleteDomain, patchDomain, startDomain } from '@management-commands/domain-management-commands';
import {
  createApplication,
  deleteApplication,
  getAllApplications,
  getApplication,
  getApplicationPage,
  patchApplication,
  renewApplicationSecrets,
  updateApplication,
} from '@management-commands/application-management-commands';

global.fetch = fetch;

let accessToken;
let domain;
let application;

beforeAll(async () => {
  const adminTokenResponse = await requestAdminAccessToken();
  accessToken = adminTokenResponse.body.access_token;
  expect(accessToken).toBeDefined();

  const createdDomain = await createDomain(accessToken, 'domain-applications', faker.company.catchPhraseDescriptor());
  expect(createdDomain).toBeDefined();
  expect(createdDomain.id).toBeDefined();

  const domainStarted = await startDomain(createdDomain.id, accessToken);
  expect(domainStarted).toBeDefined();
  expect(domainStarted.id).toEqual(createdDomain.id);

  domain = domainStarted;
});

describe('when creating applications', () => {
  for (let i = 0; i < 10; i++) {
    it('must create new application: ' + i, async () => {
      const app = {
        name: faker.commerce.productName(),
        type: 'service',
        description: faker.lorem.paragraph(),
        metadata: {
          key1: faker.lorem.paragraph(),
          key2: faker.lorem.paragraph(),
          key3: faker.lorem.paragraph(),
          key4: faker.lorem.paragraph(),
        },
      };

      const createdApp = await createApplication(domain.id, accessToken, app);
      expect(createdApp).toBeDefined();
      expect(createdApp.id).toBeDefined();
      expect(createdApp.name).toEqual(app.name);
      expect(createdApp.description).toEqual(app.description);
      expect(createdApp.type).toEqual(app.type);
      application = createdApp;
    });
  }
});

describe('after creating applications', () => {
  it('must find Application', async () => {
    const foundApp = await getApplication(domain.id, accessToken, application.id);
    expect(foundApp).toBeDefined();
    expect(application.id).toEqual(foundApp.id);
  });

  it('must update application', async () => {
    const updatedApp = await updateApplication(
      domain.id,
      accessToken,
      { ...application, name: faker.commerce.productName() },
      application.id,
    );
    expect(updatedApp.name === application.name).toBeFalsy();
    application = updatedApp;
  });

  it('must patch application', async () => {
    const patchedApp = await patchApplication(domain.id, accessToken, { name: 'application name' }, application.id);
    expect(patchedApp.name === application.name).toBeFalsy();
    application = patchedApp;
  });

  it('must renew application secrets', async () => {
    const renewedSecretApp = await renewApplicationSecrets(domain.id, accessToken, application.id);
    expect(renewedSecretApp.settings.oauth.clientId).toEqual(application.settings.oauth.clientId);
    expect(renewedSecretApp.settings.oauth.clientSecret).not.toEqual(application.settings.oauth.clientSecret);
    application = renewedSecretApp;
  });

  it('must find all Applications', async () => {
    const applicationPage = await getAllApplications(domain.id, accessToken);

    expect(applicationPage.currentPage).toEqual(0);
    expect(applicationPage.totalCount).toEqual(10);
    expect(applicationPage.data.length).toEqual(10);
  });

  it('must find application page', async () => {
    const applicationPage = await getApplicationPage(domain.id, accessToken, 1, 3);

    expect(applicationPage.currentPage).toEqual(1);
    expect(applicationPage.totalCount).toEqual(10);
    expect(applicationPage.data.length).toEqual(3);
  });

  it('must find last application page', async () => {
    const applicationPage = await getApplicationPage(domain.id, accessToken, 3, 3);

    expect(applicationPage.currentPage).toEqual(3);
    expect(applicationPage.totalCount).toEqual(10);
    expect(applicationPage.data.length).toEqual(1);
  });

  it('Must delete application', async () => {
    await deleteApplication(domain.id, accessToken, application.id);
    const applicationPage = await getAllApplications(domain.id, accessToken);

    expect(applicationPage.currentPage).toEqual(0);
    expect(applicationPage.totalCount).toEqual(9);
    expect(applicationPage.data.length).toEqual(9);
    expect(applicationPage.data.find((app) => app.id === application.id)).toBeFalsy();
  });
});

describe('Entrypoints: User accounts', () => {
  it('should define the "remember me" amount of time', async () => {
    const app = {
      name: faker.commerce.productName(),
      type: 'browser',
      description: faker.lorem.paragraph(),
    };

    const createdApp = await createApplication(domain.id, accessToken, app);

    const patchedApplication = await patchApplication(
      domain.id,
      accessToken,
      {
        settings: {
          account: {
            inherited: false,
            rememberMe: true,
            rememberMeDuration: 7200,
          },
        },
      },
      createdApp.id,
    );

    const accountSettings = patchedApplication.settings.account;
    expect(accountSettings.rememberMe).toBe(true);
    expect(accountSettings.rememberMeDuration).toBe(7200);
  });
});

afterAll(async () => {
  if (domain && domain.id) {
    await deleteDomain(domain.id, accessToken);
  }
});
