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
import { safeDeleteDomain, patchDomain, setupDomainForTest } from '@management-commands/domain-management-commands';
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
import { Domain } from '@management-models/Domain';
import { uniqueName } from '@utils-commands/misc';

global.fetch = fetch;

jest.setTimeout(200000);

let accessToken: string;
let domain: Domain;
let application;
let secret;

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  domain = await setupDomainForTest(uniqueName('applications', true), { accessToken }).then((it) => it.domain);
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
      secret = createdApp.secrets[0];
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
    const renewedSecret = await renewApplicationSecrets(domain.id, accessToken, application.id, secret.id);
    expect(renewedSecret.secret).not.toEqual(secret.secret);
    secret = renewedSecret;
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
      redirectUris: ['https://callback'],
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

describe('Redirect URI', () => {
  it('Should not turn dynamic params when any application contains redirect uris with same hostname and path', async () => {
    expect(domain.oidc.clientRegistrationSettings.allowRedirectUriParamsExpressionLanguage).toBe(false);
    const app = {
      name: faker.commerce.productName(),
      type: 'browser',
      description: faker.lorem.paragraph(),
      redirectUris: ['https://callback/?param=test', 'https://callback/?param2=test2'],
    };

    const createdApp = await createApplication(domain.id, accessToken, app);

    await expect(
      patchDomain(domain.id, accessToken, {
        oidc: {
          clientRegistrationSettings: {
            allowRedirectUriParamsExpressionLanguage: true,
          },
        },
      })
    ).rejects.toMatchObject({
      response: { status: 400 }
    });

    expect(domain.oidc.clientRegistrationSettings.allowRedirectUriParamsExpressionLanguage).toBe(false);

    await deleteApplication(domain.id, accessToken, createdApp.id);
  });

  it('Should turn on dynamic parameters evaluation', async () => {
    expect(domain.oidc.clientRegistrationSettings.allowRedirectUriParamsExpressionLanguage).toBe(false);
    domain = await patchDomain(domain.id, accessToken, {
      oidc: {
        clientRegistrationSettings: {
          allowRedirectUriParamsExpressionLanguage: true,
        },
      },
    });
    expect(domain.oidc.clientRegistrationSettings.allowRedirectUriParamsExpressionLanguage).toBe(true);
  });
  it('Should create app with redirect URI with EL', async () => {
    const app = {
      name: faker.commerce.productName(),
      type: 'browser',
      description: faker.lorem.paragraph(),
      redirectUris: ["https://callback/?param={#context.attributes['test']}"],
    };

    const createdApp = await createApplication(domain.id, accessToken, app);
    expect(createdApp.settings.oauth.redirectUris).toStrictEqual(["https://callback/?param={#context.attributes['test']}"]);
  });

  it('Should add new redirect URI with EL', async () => {
    const app = {
      name: faker.commerce.productName(),
      type: 'browser',
      description: faker.lorem.paragraph(),
      redirectUris: ["https://callback/?param={#context.attributes['test']}"],
    };

    const patch = {
      settings: {
        oauth: {
          redirectUris: ["https://callback2/?param={#context.attributes['test']}", "https://callback/?param={#context.attributes['test']}"],
        },
      },
    };

    const createdApp = await createApplication(domain.id, accessToken, app).then((app) =>
      patchApplication(domain.id, accessToken, patch, app.id),
    );

    expect(createdApp.settings.oauth.redirectUris).toStrictEqual([
      "https://callback2/?param={#context.attributes['test']}",
      "https://callback/?param={#context.attributes['test']}",
    ]);
  });

  it('Should add new redirect URI with EL with many params', async () => {
    const app = {
      name: faker.commerce.productName(),
      type: 'browser',
      description: faker.lorem.paragraph(),
      redirectUris: ["https://callback/?param={#context.attributes['test']}"],
    };

    const patch = {
      settings: {
        oauth: {
          redirectUris: [
            "https://callback/?param={#context.attributes['test']}",
            "https://callback2/?param={#context.attributes['test']}&param2={#context.attributes['test']}&xxx=123&test",
          ],
        },
      },
    };

    const createdApp = await createApplication(domain.id, accessToken, app).then((app) =>
      patchApplication(domain.id, accessToken, patch, app.id),
    );

    expect(createdApp.settings.oauth.redirectUris).toStrictEqual([
      "https://callback/?param={#context.attributes['test']}",
      "https://callback2/?param={#context.attributes['test']}&param2={#context.attributes['test']}&xxx=123&test",
    ]);
  });

  it('Should not add new redirect URI with EL if its the same domain and path', async () => {
    const app = {
      name: faker.commerce.productName(),
      type: 'browser',
      description: faker.lorem.paragraph(),
      redirectUris: ["https://callback/path?param={#context.attributes['test']}"],
    };

    const patch = {
      settings: {
        oauth: {
          redirectUris: [
            "https://callback/path?param2={#context.attributes['test']}",
            "https://callback/path?param={#context.attributes['test']}",
          ],
        },
      },
    };

    let createdApp = await createApplication(domain.id, accessToken, app);

    try {
      createdApp = await patchApplication(domain.id, accessToken, patch, createdApp.id);
    } catch (ex) {
      expect(ex.response.status).toEqual(400);
    }

    expect(createdApp.settings.oauth.redirectUris).toStrictEqual(["https://callback/path?param={#context.attributes['test']}"]);
  });

  it('Should not add new redirect with fragment', async () => {
    const app = {
      name: faker.commerce.productName(),
      type: 'browser',
      description: faker.lorem.paragraph(),
      redirectUris: ["https://callback/path?param={#context.attributes['test']}"],
    };

    const patch = {
      settings: {
        oauth: {
          redirectUris: ["https://callback/path?param2={#context.attributes['test']}#fragment"],
        },
      },
    };

    const createdApp = await createApplication(domain.id, accessToken, app);

    try {
      await patchApplication(domain.id, accessToken, patch, createdApp.id);
    } catch (ex) {
      expect(ex.response.status).toEqual(400);
    }
  });

  it('Should turn off dynamic parameters evaluation', async () => {
    domain = await patchDomain(domain.id, accessToken, {
      oidc: {
        clientRegistrationSettings: {
          allowRedirectUriParamsExpressionLanguage: false,
        },
      },
    });
    expect(domain.oidc.clientRegistrationSettings.allowRedirectUriParamsExpressionLanguage).toBe(false);
  });
});

afterAll(async () => {
  if (domain && domain.id) {
    await safeDeleteDomain(domain.id, accessToken);
  }
});
