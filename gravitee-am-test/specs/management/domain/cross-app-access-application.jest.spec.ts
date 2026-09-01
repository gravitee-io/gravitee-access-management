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
import { uniqueName } from '@utils-commands/misc';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { safeDeleteDomain, setupDomainForTest } from '@management-commands/domain-management-commands';
import { createApplication, getApplication, updateApplication } from '@management-commands/application-management-commands';
import { listCrossAppAccessResourceServers } from '@management-commands/cross-app-access-management-commands';
import { createTrustedIssuerKeyMaterial } from '../../gateway/token-exchange/fixtures/trusted-issuer-jwt-helper';
import { setup } from '../../test-fixture';
import { createTrustedDomain } from './fixtures/cross-app-access-fixture';

setup(200000);

let accessToken: string;
let domain: any;
let application: any;
let calendarResourceServerId: string;
let calendarTrustDomainId: string;

const oauthSettings = (crossAppAccessSettings: Record<string, unknown>, idJagValiditySeconds?: number) => ({
  settings: {
    oauth: {
      grantTypes: ['authorization_code'],
      redirectUris: ['https://callback'],
      crossAppAccessSettings,
      ...(idJagValiditySeconds === undefined ? {} : { idJagValiditySeconds }),
    },
  },
});

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  const created = await setupDomainForTest(uniqueName('xaa-app', true), { accessToken, waitForStart: true });
  domain = created.domain;

  const trustedDomain = await createTrustedDomain(domain.id, accessToken, {
    name: uniqueName('xaa-app-authority'),
    domainIdentifier: 'https://auth.example.com/xaa-app',
    crossAppAccess: {
      enabled: true,
      resourceServers: [{ name: 'Calendar', resource: 'https://calendar.example.com/xaa-app' }],
    },
  });
  calendarTrustDomainId = trustedDomain.id;
  calendarResourceServerId = trustedDomain.crossAppAccess.resourceServers[0].id;

  application = await createApplication(domain.id, accessToken, {
    name: 'xaa-app',
    type: 'WEB',
    clientId: 'xaa-app',
    clientSecret: 'xaa-app',
    redirectUris: ['https://callback'],
  });
});

afterAll(async () => {
  if (domain) {
    await safeDeleteDomain(domain.id, accessToken);
  }
});

describe('Cross App Access - the security domain lists the resource servers an application may reach', () => {
  it('should flatten the resource servers of Cross App Access trusted domains', async () => {
    const resourceServers = await listCrossAppAccessResourceServers(domain.id, accessToken);

    expect(resourceServers).toHaveLength(1);
    expect(resourceServers[0]).toEqual({
      trustDomainId: calendarTrustDomainId,
      trustDomainName: expect.stringContaining('xaa-app-authority'),
      resourceServerId: calendarResourceServerId,
      name: 'Calendar',
      resource: 'https://calendar.example.com/xaa-app',
    });
  });

  it('should not list resource servers of a trusted domain that has Cross App Access disabled', async () => {
    const trustedKey = createTrustedIssuerKeyMaterial();
    await createTrustedDomain(domain.id, accessToken, {
      name: uniqueName('xaa-app-disabled'),
      domainIdentifier: 'https://auth.example.com/xaa-app-disabled',
      tokenExchange: { enabled: true },
      keyMaterial: { source: 'PEM', certificate: trustedKey.certificatePem },
      crossAppAccess: {
        enabled: false,
        resourceServers: [{ name: 'Hidden', resource: 'https://hidden.example.com/xaa-app' }],
      },
    });

    const resourceServers = await listCrossAppAccessResourceServers(domain.id, accessToken);

    expect(resourceServers.map((rs) => rs.name)).toEqual(['Calendar']);
  });
});

describe('Cross App Access - the resource server listing narrows on a search term and a limit', () => {
  beforeAll(async () => {
    await createTrustedDomain(domain.id, accessToken, {
      name: uniqueName('xaa-app-globex'),
      domainIdentifier: 'https://auth.example.com/xaa-app-globex',
      crossAppAccess: {
        enabled: true,
        resourceServers: [
          { name: 'Mail', resource: 'https://mail.example.com/xaa-app' },
          { name: 'Files', resource: 'https://files.example.com/xaa-app' },
        ],
      },
    });
  });

  it('should keep only the resource servers the search term matches', async () => {
    const resourceServers = await listCrossAppAccessResourceServers(domain.id, accessToken, { q: 'mail' });

    expect(resourceServers.map((rs) => rs.name)).toEqual(['Mail']);
  });

  it('should match the search term against the resource identifier', async () => {
    const resourceServers = await listCrossAppAccessResourceServers(domain.id, accessToken, { q: 'https://files' });

    expect(resourceServers.map((rs) => rs.name)).toEqual(['Files']);
  });

  it('should return at most the requested limit', async () => {
    expect(await listCrossAppAccessResourceServers(domain.id, accessToken, { limit: 2 })).toHaveLength(2);
  });

  it('should reject a limit outside the allowed range', async () => {
    await expect(listCrossAppAccessResourceServers(domain.id, accessToken, { limit: 0 })).rejects.toBeDefined();
  });
});

describe('Cross App Access - the application block round-trips through the management API', () => {
  it('should store the block and read it back unchanged', async () => {
    await updateApplication(
      domain.id,
      accessToken,
      oauthSettings(
        {
          enabled: true,
          resourceServers: [
            { trustDomainId: calendarTrustDomainId, resourceServerId: calendarResourceServerId, clientId: 'calendar-client' },
          ],
        },
        120,
      ) as any,
      application.id,
    );

    const reloaded = await getApplication(domain.id, accessToken, application.id);
    const oauth = (reloaded as any).settings.oauth;

    expect(oauth.crossAppAccessSettings.enabled).toBe(true);
    expect(oauth.crossAppAccessSettings.resourceServers).toEqual([
      { trustDomainId: calendarTrustDomainId, resourceServerId: calendarResourceServerId, clientId: 'calendar-client' },
    ]);
    expect(oauth.idJagValiditySeconds).toBe(120);
  });

  it('should default the ID-JAG validity to 300 seconds on an application that never set one', async () => {
    const untouched = await createApplication(domain.id, accessToken, {
      name: 'xaa-app-default-ttl',
      type: 'WEB',
      clientId: 'xaa-app-default-ttl',
      clientSecret: 'xaa-app-default-ttl',
      redirectUris: ['https://callback'],
    });

    const reloaded = await getApplication(domain.id, accessToken, untouched.id);

    expect((reloaded as any).settings.oauth.idJagValiditySeconds).toBe(300);
  });

  it('should keep the mappings when Cross App Access is disabled', async () => {
    const resourceServers = [
      { trustDomainId: calendarTrustDomainId, resourceServerId: calendarResourceServerId, clientId: 'calendar-client' },
    ];
    await updateApplication(domain.id, accessToken, oauthSettings({ enabled: true, resourceServers }, 120) as any, application.id);

    await updateApplication(domain.id, accessToken, oauthSettings({ enabled: false, resourceServers }, 120) as any, application.id);

    const reloaded = await getApplication(domain.id, accessToken, application.id);
    expect((reloaded as any).settings.oauth.crossAppAccessSettings.enabled).toBe(false);
    expect((reloaded as any).settings.oauth.crossAppAccessSettings.resourceServers).toEqual(resourceServers);
  });

  it('should accept a resource server that no longer exists', async () => {
    await updateApplication(
      domain.id,
      accessToken,
      oauthSettings({
        enabled: true,
        resourceServers: [{ trustDomainId: 'gone', resourceServerId: 'gone', clientId: 'calendar-client' }],
      }) as any,
      application.id,
    );

    const reloaded = await getApplication(domain.id, accessToken, application.id);
    expect((reloaded as any).settings.oauth.crossAppAccessSettings.resourceServers[0].resourceServerId).toBe('gone');
  });
});

describe('Cross App Access - the management API rejects an invalid application block', () => {
  const rejects = async (crossAppAccessSettings: Record<string, unknown>, idJagValiditySeconds?: number) =>
    expect(
      updateApplication(domain.id, accessToken, oauthSettings(crossAppAccessSettings, idJagValiditySeconds) as any, application.id),
    ).rejects.toBeDefined();

  it('should reject a repeated resource server', async () => {
    await rejects({
      enabled: true,
      resourceServers: [
        { trustDomainId: calendarTrustDomainId, resourceServerId: calendarResourceServerId, clientId: 'client-1' },
        { trustDomainId: calendarTrustDomainId, resourceServerId: calendarResourceServerId, clientId: 'client-2' },
      ],
    });
  });

  it('should reject a blank client id', async () => {
    await rejects({
      enabled: true,
      resourceServers: [{ trustDomainId: calendarTrustDomainId, resourceServerId: calendarResourceServerId, clientId: '  ' }],
    });
  });

  it('should reject an ID-JAG validity below one second', async () => {
    await rejects({ enabled: true, resourceServers: [] }, 0);
  });
});
