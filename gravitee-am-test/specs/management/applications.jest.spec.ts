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
import { afterAll, beforeAll, describe, expect, it, jest } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { safeDeleteDomain, patchDomain, setupDomainForTest } from '@management-commands/domain-management-commands';
import {
  createApplication,
  deleteApplication,
  getApplication,
  listApplications,
  patchApplication,
  renewApplicationSecrets,
  updateApplication,
} from '@management-commands/application-management-commands';
import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
import { uniqueName } from '@utils-commands/misc';
import { ClientSecret } from '@management-models/ClientSecret';

global.fetch = fetch;

const SMALL_BATCH_SIZE = 5;
const LARGE_BATCH_SIZE = 55;
const WILDCARD_BATCH_SIZE = 8;
const SECOND_DOMAIN_BATCH_SIZE = 4;
const DEFAULT_PAGE_SIZE = 50;
const RUN_PREFIX = `${Date.now()}`;
const SMALL_PREFIX = `${RUN_PREFIX}-small`;
const LARGE_PREFIX = `${RUN_PREFIX}-large`;
const WILDCARD_PREFIX = `${RUN_PREFIX}-shared`;
const SEEDED_APPLICATION_COUNT = SMALL_BATCH_SIZE + LARGE_BATCH_SIZE;
const UNIQUE_APP_NAME = `${RUN_PREFIX}-unique123`;
const UNIQUE_CLIENT_ID = `${RUN_PREFIX}-client-unique123`;
const CLIENT_ID_WILDCARD_PREFIX = `${RUN_PREFIX}-client-shared`;

jest.setTimeout(200000);

let accessToken: string;
let primaryDomain: Domain;
let secondaryDomain: Domain;
const createdApplicationIds: { [domainId: string]: string[] } = {};
const smallBatch: Application[] = [];
const largeBatch: Application[] = [];
const wildcardBatch: Application[] = [];
const clientIdWildcardBatch: Application[] = [];
let uniqueClientIdApp: Application | undefined;

const recordApplicationForCleanup = (app?: Application, domainId?: string) => {
  if (app?.id && domainId) {
    if (!createdApplicationIds[domainId]) {
      createdApplicationIds[domainId] = [];
    }
    createdApplicationIds[domainId].push(app.id);
  }
};

const createApp = async (domainId: string, name?: string, clientId?: string): Promise<Application> => {
  const app: any = {
    name: name ?? faker.commerce.productName(),
    type: 'service',
    description: faker.lorem.paragraph(),
    metadata: {
      key1: faker.lorem.paragraph(),
      key2: faker.lorem.paragraph(),
      key3: faker.lorem.paragraph(),
      key4: faker.lorem.paragraph(),
    },
  };
  if (clientId) {
    app.clientId = clientId;
  }
  const createdApp = await createApplication(domainId, accessToken, app);
  recordApplicationForCleanup(createdApp, domainId);
  return createdApp;
};

type BatchOptions = {
  buildName?: (index: number) => string;
  onAppCreated?: (app: Application, index: number) => void;
};

const seedBatch = async (domainId: string, count: number, store: Application[], options: BatchOptions = {}) => {
  for (let i = 0; i < count; i++) {
    const name = options?.buildName?.(i);
    expect(name).toBeDefined();
    const createdApp = await createApp(domainId, name!);
    store.push(createdApp);
    options?.onAppCreated?.(createdApp, i);
  }
};

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  primaryDomain = await setupDomainForTest(uniqueName('applications-primary', true), { accessToken }).then((it) => it.domain);
  secondaryDomain = await setupDomainForTest(uniqueName('applications-secondary', true), { accessToken }).then((it) => it.domain);

  // Seed primary domain with applications
  await seedBatch(primaryDomain.id!, SMALL_BATCH_SIZE, smallBatch, {
    buildName: (index) => `${SMALL_PREFIX}-${index}-${uniqueName('app', true)}`,
  });

  await seedBatch(
    primaryDomain.id!,
    LARGE_BATCH_SIZE,
    largeBatch,
    {
      buildName: (index) =>
        index < WILDCARD_BATCH_SIZE
          ? `${WILDCARD_PREFIX}-${index}-${uniqueName('app', true)}`
          : `${LARGE_PREFIX}-${index}-${uniqueName('app', true)}`,
      onAppCreated: (app, index) => {
        if (index < WILDCARD_BATCH_SIZE) {
          wildcardBatch.push(app);
        }
      },
    },
  );

  // Create unique app in primary domain
  await createApp(primaryDomain.id!, UNIQUE_APP_NAME);

  // Create applications with specific client IDs for client ID search testing
  uniqueClientIdApp = await createApp(primaryDomain.id!, `${RUN_PREFIX}-app-for-client-id`, UNIQUE_CLIENT_ID);

  // Create applications with wildcard client ID prefix
  for (let i = 0; i < WILDCARD_BATCH_SIZE; i++) {
    const clientId = `${CLIENT_ID_WILDCARD_PREFIX}-${i}-${uniqueName('client', true)}`;
    const app = await createApp(primaryDomain.id!, `${RUN_PREFIX}-app-client-${i}`, clientId);
    clientIdWildcardBatch.push(app);
  }

  // Create applications in secondary domain with matching names to test domain-scoped filtering
  await seedBatch(secondaryDomain.id!, SECOND_DOMAIN_BATCH_SIZE, [], {
    buildName: (index) => {
      if (index === 0) {
        return UNIQUE_APP_NAME; // Same name as primary domain's unique app
      }
      return `${WILDCARD_PREFIX}-${index}-${uniqueName('app', true)}`; // Matching wildcard prefix
    },
  });
});

describe('after creating applications', () => {
  let application: Application;
  let secret: ClientSecret;

  beforeAll(async () => {
    application = await createApp(primaryDomain.id!);
    secret = application.secrets[0];
  });

  it('must find application by ID', async () => {
    const foundApp = await getApplication(primaryDomain.id!, accessToken, application.id!);
    expect(foundApp).toBeDefined();
    expect(application.id).toEqual(foundApp.id);
  });

  it('must update application', async () => {
    const updatedApp = await updateApplication(
      primaryDomain.id!,
      accessToken,
      { ...application, name: faker.commerce.productName() },
      application.id,
    );
    expect(updatedApp.name === application.name).toBeFalsy();
    application = updatedApp;
  });

  it('must patch application', async () => {
    const patchedApp = await patchApplication(primaryDomain.id!, accessToken, { name: 'application name' }, application.id);
    expect(patchedApp.name === application.name).toBeFalsy();
    application = patchedApp;
  });

  it('must renew application secrets', async () => {
    const renewedSecret = await renewApplicationSecrets(primaryDomain.id!, accessToken, application.id, secret.id);
    expect(renewedSecret.secret).not.toEqual(secret.secret);
    secret = renewedSecret;
  });

  it('must delete application', async () => {
    await deleteApplication(primaryDomain.id!, accessToken, application.id);
    const applicationPage = await listApplications(primaryDomain.id!, accessToken, { size: SEEDED_APPLICATION_COUNT + 1 });

    expect(applicationPage.data.find((app) => app.id === application.id)).toBeFalsy();
  });
});

describe('application search and pagination', () => {
  it('returns a small page without a query', async () => {
    const pageResponse = await listApplications(primaryDomain.id!, accessToken, { size: SMALL_BATCH_SIZE });

    expect(pageResponse.data?.length).toBeLessThanOrEqual(SMALL_BATCH_SIZE);
    expect(pageResponse.totalCount).toBeGreaterThanOrEqual(SEEDED_APPLICATION_COUNT);
  });

  it('defaults to 50 results per page when many applications exist', async () => {
    const pageResponse = await listApplications(primaryDomain.id!, accessToken);

    expect(pageResponse.data?.length).toBe(DEFAULT_PAGE_SIZE);
    expect(pageResponse.totalCount).toBeGreaterThanOrEqual(SEEDED_APPLICATION_COUNT);
  });

  it('paginates results when requesting a custom page and size', async () => {
    const firstPageApps = (await listApplications(primaryDomain.id!, accessToken, { page: 0, size: 10 })).data;
    const secondPageApps = (await listApplications(primaryDomain.id!, accessToken, { page: 1, size: 10 })).data;

    expect(firstPageApps.length).toBe(10);
    expect(secondPageApps.length).toBe(10);

    const firstPageIds = new Set(firstPageApps.map((app) => app.id));
    const overlap = secondPageApps.filter((app) => firstPageIds.has(app.id));
    expect(overlap).toHaveLength(0);
  });

  it('finds an application when searching by its name (targeted domain only)', async () => {
    const pageResponse = await listApplications(primaryDomain.id!, accessToken, { q: UNIQUE_APP_NAME });

    expect(pageResponse.data?.length).toBe(1);
    expect(pageResponse.data?.[0].name).toEqual(UNIQUE_APP_NAME);
  });

  it('supports wildcard searching by name (targeted domain only)', async () => {
    const wildcardQuery = `${WILDCARD_PREFIX}*`;
    const pageResponse = await listApplications(primaryDomain.id!, accessToken, { q: wildcardQuery, size: 100 });
    const expectedIds = wildcardBatch.map((app) => app.id);

    expect(pageResponse.data).toHaveLength(expectedIds.length);
    expect(pageResponse.data?.find((app) => !app.name?.startsWith(WILDCARD_PREFIX))).toBeUndefined();
    expect(pageResponse.data?.map((app) => app.id).sort()).toEqual([...expectedIds].sort());
  });

  it('finds an application when searching by its client ID (targeted domain only)', async () => {
    expect(uniqueClientIdApp?.settings?.oauth?.clientId).toBeDefined();
    const pageResponse = await listApplications(primaryDomain.id!, accessToken, { q: UNIQUE_CLIENT_ID });

    expect(pageResponse.data?.length).toBe(1);
    expect(pageResponse.data?.[0].id).toEqual(uniqueClientIdApp!.id);
  });

  it('supports wildcard searching by client ID (targeted domain only)', async () => {
    const wildcardQuery = `${CLIENT_ID_WILDCARD_PREFIX}*`;
    const pageResponse = await listApplications(primaryDomain.id!, accessToken, { q: wildcardQuery, size: 100 });
    const expectedIds = clientIdWildcardBatch.map((app) => app.id);

    expect(pageResponse.data).toHaveLength(expectedIds.length);
    expect(pageResponse.data?.map((app) => app.id).sort()).toEqual([...expectedIds].sort());
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

    const createdApp = await createApplication(primaryDomain.id!, accessToken, app);
    recordApplicationForCleanup(createdApp, primaryDomain.id!);

    const patchedApplication = await patchApplication(
      primaryDomain.id!,
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
    expect(primaryDomain.oidc.clientRegistrationSettings.allowRedirectUriParamsExpressionLanguage).toBe(false);
    const app = {
      name: faker.commerce.productName(),
      type: 'browser',
      description: faker.lorem.paragraph(),
      redirectUris: ['https://callback/?param=test', 'https://callback/?param2=test2'],
    };

    const createdApp = await createApplication(primaryDomain.id!, accessToken, app);
    recordApplicationForCleanup(createdApp, primaryDomain.id!);

    await expect(
      patchDomain(primaryDomain.id!, accessToken, {
        oidc: {
          clientRegistrationSettings: {
            allowRedirectUriParamsExpressionLanguage: true,
          },
        },
      })
    ).rejects.toMatchObject({
      response: { status: 400 }
    });

    expect(primaryDomain.oidc.clientRegistrationSettings.allowRedirectUriParamsExpressionLanguage).toBe(false);

    await deleteApplication(primaryDomain.id!, accessToken, createdApp.id);
  });

  it('Should turn on dynamic parameters evaluation', async () => {
    expect(primaryDomain.oidc.clientRegistrationSettings.allowRedirectUriParamsExpressionLanguage).toBe(false);
    primaryDomain = await patchDomain(primaryDomain.id!, accessToken, {
      oidc: {
        clientRegistrationSettings: {
          allowRedirectUriParamsExpressionLanguage: true,
        },
      },
    });
    expect(primaryDomain.oidc.clientRegistrationSettings.allowRedirectUriParamsExpressionLanguage).toBe(true);
  });
  it('Should create app with redirect URI with EL', async () => {
    const app = {
      name: faker.commerce.productName(),
      type: 'browser',
      description: faker.lorem.paragraph(),
      redirectUris: ["https://callback/?param={#context.attributes['test']}"],
    };

    const createdApp = await createApplication(primaryDomain.id!, accessToken, app);
    recordApplicationForCleanup(createdApp, primaryDomain.id!);
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

    const createdApp = await createApplication(primaryDomain.id!, accessToken, app).then((app) => {
      recordApplicationForCleanup(app, primaryDomain.id!);
      return patchApplication(primaryDomain.id!, accessToken, patch, app.id);
    });

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

    const createdApp = await createApplication(primaryDomain.id!, accessToken, app).then((app) => {
      recordApplicationForCleanup(app, primaryDomain.id!);
      return patchApplication(primaryDomain.id!, accessToken, patch, app.id);
    });

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

    let createdApp = await createApplication(primaryDomain.id!, accessToken, app);
    recordApplicationForCleanup(createdApp, primaryDomain.id!);

    try {
      createdApp = await patchApplication(primaryDomain.id!, accessToken, patch, createdApp.id);
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

    const createdApp = await createApplication(primaryDomain.id!, accessToken, app);
    recordApplicationForCleanup(createdApp, primaryDomain.id!);

    try {
      await patchApplication(primaryDomain.id!, accessToken, patch, createdApp.id);
    } catch (ex) {
      expect(ex.response.status).toEqual(400);
    }
  });

  it('Should turn off dynamic parameters evaluation', async () => {
    primaryDomain = await patchDomain(primaryDomain.id!, accessToken, {
      oidc: {
        clientRegistrationSettings: {
          allowRedirectUriParamsExpressionLanguage: false,
        },
      },
    });
    expect(primaryDomain.oidc.clientRegistrationSettings.allowRedirectUriParamsExpressionLanguage).toBe(false);
  });
});

afterAll(async () => {
  for (const domainId of Object.keys(createdApplicationIds)) {
    for (const appId of createdApplicationIds[domainId]) {
      try {
        await deleteApplication(domainId, accessToken, appId);
      } catch (err) {
      }
    }
  }
  if (primaryDomain?.id) {
    await safeDeleteDomain(primaryDomain.id, accessToken);
  }
  if (secondaryDomain?.id) {
    await safeDeleteDomain(secondaryDomain.id, accessToken);
  }
});
