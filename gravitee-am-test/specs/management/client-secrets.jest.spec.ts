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
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { safeDeleteDomain, patchDomain, setupDomainForTest } from '@management-commands/domain-management-commands';
import { delay, uniqueName } from '@utils-commands/misc';
import { createApplication, patchApplication, updateApplication } from '@management-commands/application-management-commands';
import { performDelete, performPost } from '@gateway-commands/oauth-oidc-commands';
import {
  createClientSecret,
  deleteClientSecret,
  listClientSecrets,
  renewClientSecret,
} from '@management-commands/client-secrets-management-commands';
import { getBase64BasicAuth } from '@gateway-commands/utils';
import { setup } from '../test-fixture';

setup(200000);

let accessToken: any;
let domain: any;
let application: any;
let openIdConfiguration: any;
const managementUrl = `${process.env.AM_MANAGEMENT_URL}/management/organizations/DEFAULT/environments/DEFAULT/domains/`;

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  await setupDomainForTest(uniqueName('client-secret-domain', true), { accessToken, waitForStart: true }).then((it) => {
    domain = it.domain;
    openIdConfiguration = it.oidcConfig;
  });
  domain = await patchDomain(domain.id, accessToken, {
    oidc: {
      clientRegistrationSettings: {
        allowLocalhostRedirectUri: false,
        allowHttpSchemeRedirectUri: false,
        allowWildCardRedirectUri: false,
        isDynamicClientRegistrationEnabled: true,
        isOpenDynamicClientRegistrationEnabled: true,
      },
    },
  });
  application = await createApplication(domain.id, accessToken, {
    name: 'my-client',
    type: 'WEB',
    clientId: 'client-secret-app',
    clientSecret: 'client-secret-app',
    redirectUris: ['https://callback'],
  }).then((app) =>
    updateApplication(
      domain.id,
      accessToken,
      {
        settings: {
          oauth: {
            redirectUris: ['https://callback'],
            grantTypes: ['client_credentials'],
          },
        },
      },
      app.id,
    ).then((updatedApp) => {
      // restore the clientSecret coming from the create order
      updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
      return updatedApp;
    }),
  );
  expect(application).toBeDefined();
  await delay(6000);
});

function useSecret(application: any, openIdConfiguration: any, clientSecret: string, expectedResult: number) {
  const redirect_uri = application.settings.oauth.redirectUris[0];
  performPost(
    openIdConfiguration.token_endpoint,
    `?grant_type=client_credentials&redirect_uri=${redirect_uri}&clientId=${application.settings.oauth.clientId}`,
    null,
    {
      Authorization: 'Basic ' + getBase64BasicAuth(application.settings.oauth.clientId, clientSecret),
    },
  ).expect(expectedResult);
}

describe('Multiple secrets', () => {
  it('use secret', async () => {
    useSecret(application, openIdConfiguration, application.settings.oauth.clientSecret, 200);
  });

  it('rotate secrets', async () => {
    const rotatedSecret = await renewClientSecret(domain.id, accessToken, application.id, application.secrets[0].id);
    await delay(6000); // wait for sync
    useSecret(application, openIdConfiguration, application.settings.oauth.clientSecret, 400);
    useSecret(application, openIdConfiguration, rotatedSecret.secret, 200);
  });
  it('Create new secret', async () => {
    const createdSecret = await createClientSecret(domain.id, accessToken, application.id, {
      name: 'test',
    });
    await delay(6000); // wait for sync
    useSecret(application, openIdConfiguration, createdSecret.secret, 200);
  });
  it('Create more than 10 secrets', async () => {
    for (let i = 0; i < 8; i++) {
      await createClientSecret(domain.id, accessToken, application.id, {
        name: 'test ' + i,
      });
    }
    const res = await performPost(
      managementUrl + domain.id + '/applications/' + application.id + '/secrets',
      '',
      { name: 'not allowed' },
      {
        'Content-type': 'application/json',
        Authorization: `Bearer ${accessToken}`,
      },
    );
    expect(res.error.text).toContain('Too many Client Secrets.');
  });
  it('Create secret with the same name', async () => {
    const res = await performPost(
      managementUrl + domain.id + '/applications/' + application.id + '/secrets',
      '',
      { name: 'test 0' },
      {
        'Content-type': 'application/json',
        Authorization: `Bearer ${accessToken}`,
      },
    );
    expect(res.error.text).toContain('Secret with description test 0 already exists');
  });
  it('Remove secret', async () => {
    const clientSecrets = await listClientSecrets(domain.id, accessToken, application.id);
    await deleteClientSecret(domain.id, accessToken, application.id, clientSecrets[9].id);
    await deleteClientSecret(domain.id, accessToken, application.id, clientSecrets[8].id);
    await deleteClientSecret(domain.id, accessToken, application.id, clientSecrets[7].id);
    await deleteClientSecret(domain.id, accessToken, application.id, clientSecrets[6].id);
    await deleteClientSecret(domain.id, accessToken, application.id, clientSecrets[5].id);
    await deleteClientSecret(domain.id, accessToken, application.id, clientSecrets[4].id);
    await deleteClientSecret(domain.id, accessToken, application.id, clientSecrets[3].id);
    await deleteClientSecret(domain.id, accessToken, application.id, clientSecrets[2].id);
    await deleteClientSecret(domain.id, accessToken, application.id, clientSecrets[1].id);
    useSecret(application, openIdConfiguration, clientSecrets[9].secret, 400);
  });
  it('Remove last secret', async () => {
    const clientSecrets = await listClientSecrets(domain.id, accessToken, application.id);
    expect(clientSecrets.length).toBe(1);
    const res = await performDelete(managementUrl + domain.id + '/applications/' + application.id + '/secrets/' + clientSecrets[0].id, '', {
      'Content-type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    });
    expect(res.error.text).toContain('Cannot remove last secret');
  });
});

describe('Expiration in client secrets', () => {
  it('No Expiration - new application', async () => {
    const clientSecrets = await listClientSecrets(domain.id, accessToken, application.id);
    expect(clientSecrets.length).toBe(1);
    expect(clientSecrets[0].expiresAt).toBeUndefined();
  });
  it('expiration on domain - new application', async () => {
    domain = await patchDomain(domain.id, accessToken, {
      secretSettings: {
        enabled: true,
        expiryTimeSeconds: 120,
      },
    });
    application = await createApplication(domain.id, accessToken, {
      name: 'my-client-expiry',
      type: 'WEB',
      clientId: 'client-secret-app-expiry',
      clientSecret: 'client-secret-app-expiry',
      redirectUris: ['https://callback'],
    }).then((app) =>
      updateApplication(
        domain.id,
        accessToken,
        {
          settings: {
            oauth: {
              redirectUris: ['https://callback'],
              grantTypes: ['client_credentials'],
            },
          },
        },
        app.id,
      ),
    );

    const clientSecrets = await listClientSecrets(domain.id, accessToken, application.id);
    expect(clientSecrets.length).toBe(1);
    expect(clientSecrets[0].expiresAt).toBeDefined();
    expect(clientSecrets[0].expiresAt.getTime()).toBeLessThan(Date.now() + 120000 + 5000);
    expect(clientSecrets[0].expiresAt.getTime()).toBeGreaterThan(Date.now() + 120000 - 5000);
  });

  it('expiration on domain - renew secret', async () => {
    domain = await patchDomain(domain.id, accessToken, {
      secretSettings: {
        enabled: true,
        expiryTimeSeconds: 100,
      },
    });

    const clientSecret = await renewClientSecret(domain.id, accessToken, application.id, application.secrets[0].id);
    const now = Date.now();

    expect(clientSecret.expiresAt).toBeDefined();
    assetTime(now + 100000, clientSecret.expiresAt.getTime(), 5000);
  });

  it('expiration on domain - new secret', async () => {
    domain = await patchDomain(domain.id, accessToken, {
      secretSettings: {
        enabled: true,
        expiryTimeSeconds: 800,
      },
    });

    const clientSecret = await createClientSecret(domain.id, accessToken, application.id, {
      name: 'test1',
    });
    const now = Date.now();
    expect(clientSecret.expiresAt).toBeDefined();
    assetTime(now + 800000, clientSecret.expiresAt.getTime(), 5000);
  });
  it('no expiration on domain/expiration on application - new secret', async () => {
    domain = await patchDomain(domain.id, accessToken, {
      secretSettings: {
        enabled: false,
        expiryTimeSeconds: 120,
      },
    });
    application = await patchApplication(
      domain.id,
      accessToken,
      {
        settings: {
          secretExpirationSettings: {
            enabled: true,
            expiryTimeSeconds: 60,
          },
        },
      },
      application.id,
    );

    const clientSecret = await createClientSecret(domain.id, accessToken, application.id, {
      name: 'test2',
    });
    const now = Date.now();

    expect(clientSecret.expiresAt).toBeDefined();
    assetTime(now + 60000, clientSecret.expiresAt.getTime(), 5000);
  });
  it('no expiration on domain/expiration on application - rotate secret', async () => {
    application = await patchApplication(
      domain.id,
      accessToken,
      {
        settings: {
          secretExpirationSettings: {
            enabled: true,
            expiryTimeSeconds: 50,
          },
        },
      },
      application.id,
    );

    const clientSecret = await renewClientSecret(domain.id, accessToken, application.id, application.secrets[0].id);
    const now = Date.now();

    expect(clientSecret.expiresAt).toBeDefined();
    assetTime(now + 50000, clientSecret.expiresAt.getTime(), 5000);
  });

  it('no expiration on domain/expiration on application set to NONE - new secret', async () => {
    application = await patchApplication(
      domain.id,
      accessToken,
      {
        settings: {
          secretExpirationSettings: {
            enabled: true,
            expiryTimeSeconds: 0,
          },
        },
      },
      application.id,
    );

    const clientSecret = await createClientSecret(domain.id, accessToken, application.id, {
      name: 'test3',
    });

    expect(clientSecret.expiresAt).toBeUndefined();
  });

  it('no expiration on domain/expiration on application set to NONE - rotate secret', async () => {
    const clientSecret = await renewClientSecret(domain.id, accessToken, application.id, application.secrets[1].id);

    expect(clientSecret.expiresAt).toBeUndefined();
  });

  it('expiration on domain/expiration on application - new secret', async () => {
    domain = await patchDomain(domain.id, accessToken, {
      secretSettings: {
        enabled: true,
        expiryTimeSeconds: 120,
      },
    });
    application = await patchApplication(
      domain.id,
      accessToken,
      {
        settings: {
          secretExpirationSettings: {
            enabled: true,
            expiryTimeSeconds: 70,
          },
        },
      },
      application.id,
    );

    const clientSecret = await createClientSecret(domain.id, accessToken, application.id, {
      name: 'test5',
    });
    const now = Date.now();

    expect(clientSecret.expiresAt).toBeDefined();
    assetTime(now + 70000, clientSecret.expiresAt.getTime(), 5000);
  });
  it('expiration on domain/expiration on application - rotate secret', async () => {
    application = await patchApplication(
      domain.id,
      accessToken,
      {
        settings: {
          secretExpirationSettings: {
            enabled: true,
            expiryTimeSeconds: 150,
          },
        },
      },
      application.id,
    );

    const clientSecret = await renewClientSecret(domain.id, accessToken, application.id, application.secrets[0].id);
    const now = Date.now();

    expect(clientSecret.expiresAt).toBeDefined();
    assetTime(now + 150000, clientSecret.expiresAt.getTime(), 5000);
  });

  it('expiration on domain/expiration on application set to NONE - new secret', async () => {
    application = await patchApplication(
      domain.id,
      accessToken,
      {
        settings: {
          secretExpirationSettings: {
            enabled: true,
            expiryTimeSeconds: 0,
          },
        },
      },
      application.id,
    );

    const clientSecret = await createClientSecret(domain.id, accessToken, application.id, {
      name: 'test6',
    });

    expect(clientSecret.expiresAt).toBeUndefined();
  });

  it('expiration on domain/expiration on application set to NONE - rotate secret', async () => {
    const clientSecret = await renewClientSecret(domain.id, accessToken, application.id, application.secrets[1].id);

    expect(clientSecret.expiresAt).toBeUndefined();
  });
});

describe('Usage expired secrets', () => {
  it('Cant use expired secret', async () => {
    domain = await patchDomain(domain.id, accessToken, {
      secretSettings: {
        enabled: true,
        expiryTimeSeconds: 5,
      },
    });
    application = await patchApplication(
      domain.id,
      accessToken,
      {
        settings: {
          secretExpirationSettings: {
            enabled: false,
            expiryTimeSeconds: 70,
          },
        },
      },
      application.id,
    );

    const clientSecret = await createClientSecret(domain.id, accessToken, application.id, {
      name: 'test8',
    });
    const now = Date.now();
    assetTime(now + 5000, clientSecret.expiresAt.getTime(), 1000);
    await delay(6000); //wait to expire
    useSecret(application, openIdConfiguration, clientSecret.secret, 400);
  });
});

function assetTime(now: number, received: number, delta: number) {
  expect(received).toBeLessThan(now + delta);
  expect(received).toBeGreaterThan(now - delta);
}

afterAll(async () => {
  if (domain && domain.id) {
    await safeDeleteDomain(domain.id, accessToken);
  }
});
