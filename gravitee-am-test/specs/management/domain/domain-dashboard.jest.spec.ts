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
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { safeDeleteDomain, setupDomainForTest } from '@management-commands/domain-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { loginUserNameAndPassword } from '@gateway-commands/login-commands';
import { buildCreateAndTestUser } from '@management-commands/user-management-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { delay, uniqueName } from '@utils-commands/misc';
import { getDomainApi } from '../../../api/commands/management/service/utils';
import { AnalyticsTypeParamValueEnum } from '../../../api/management/models/AnalyticsTypeParam';
import { setup } from '../../test-fixture';

setup(200000);

const USER_PASSWORD = 'SomeP@ssw0rd';
const REDIRECT_URI = 'https://auth-nightly.gravitee.io/myApp/callback';

let accessToken: string;

let domain1: any;
let oidcConfig1: any;
let app1: any;
let user1: any;

let domain2: any;
let oidcConfig2: any;
let app2: any;
let user2: any;

type DomainDefinition = {
  domain: any;
  oidcConfig: any;
  app: any;
  user: any;
};

async function setupDomainWithApp(name: string): Promise<DomainDefinition> {
  const { domain, oidcConfig } = await setupDomainForTest(uniqueName(name, true), { accessToken, waitForStart: true });

  const idps = await getAllIdps(domain.id, accessToken);
  const defaultIdp = idps.values().next().value;

  const app = await createTestApp(`${name}-app`, domain, accessToken, 'WEB', {
    settings: {
      oauth: {
        redirectUris: [REDIRECT_URI],
        grantTypes: ['authorization_code'],
        scopeSettings: [{ scope: 'openid', defaultScope: true }],
      },
    },
    identityProviders: new Set([{ identity: defaultIdp.id, priority: 0 }]),
  });

  const user = await buildCreateAndTestUser(domain.id, accessToken, 0);

  return { domain, oidcConfig, app, user };
}

async function getLoginCount(domainId: string): Promise<number> {
  const now = Date.now();
  const response = await getDomainApi(accessToken).findDomainAnalyticsRaw({
    organizationId: process.env.AM_DEF_ORG_ID,
    environmentId: process.env.AM_DEF_ENV_ID,
    domain: domainId,
    type: AnalyticsTypeParamValueEnum.Count as any,
    field: 'user_login',
    from: now - 3600000,
    to: now,
    interval: 3600000,
  });
  const data = await response.raw.json();
  return data.value ?? 0;
}

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();

  [{ domain: domain1, oidcConfig: oidcConfig1, app: app1, user: user1 }, { domain: domain2, oidcConfig: oidcConfig2, app: app2, user: user2 }] =
    await Promise.all([setupDomainWithApp('dashboard-d1'), setupDomainWithApp('dashboard-d2')]);

  // 5 successful logins on domain1
  for (let i = 0; i < 5; i++) {
    await loginUserNameAndPassword(app1.settings.oauth.clientId, user1, USER_PASSWORD, false, oidcConfig1, domain1);
  }

  // 2 successful logins on domain2
  for (let i = 0; i < 2; i++) {
    await loginUserNameAndPassword(app2.settings.oauth.clientId, user2, USER_PASSWORD, false, oidcConfig2, domain2);
  }

  // allow time for login audit events to be indexed
  await delay(5000);
});

afterAll(async () => {
  await Promise.all([
    domain1?.id ? safeDeleteDomain(domain1.id, accessToken) : Promise.resolve(),
    domain2?.id ? safeDeleteDomain(domain2.id, accessToken) : Promise.resolve(),
  ]);
});

describe('Domain dashboard — login counts across 2 domains', () => {
  it('domain1 should show 5 logins', async () => {
    const count = await getLoginCount(domain1.id);
    expect(count).toBe(5);
  });

  it('domain2 should show 2 logins', async () => {
    const count = await getLoginCount(domain2.id);
    expect(count).toBe(2);
  });
});

describe('Domain dashboard — after deleting a domain', () => {
  let deletedDomain2Id: string;

  beforeAll(async () => {
    deletedDomain2Id = domain2.id;
    await safeDeleteDomain(deletedDomain2Id, accessToken);
  });

  it('deleted domain dashboard should no longer be accessible (404)', async () => {
    let status: number;
    try {
      await getDomainApi(accessToken).findDomainAnalyticsRaw({
        organizationId: process.env.AM_DEF_ORG_ID,
        environmentId: process.env.AM_DEF_ENV_ID,
        domain: deletedDomain2Id,
        type: AnalyticsTypeParamValueEnum.Count as any,
        field: 'user_login',
        from: Date.now() - 3600000,
        to: Date.now(),
        interval: 3600000,
      });
      status = 200;
    } catch (e) {
      status = e.status ?? e.response?.status ?? 404;
    }
    expect(status).toBe(404);
  });

  it('domain1 dashboard should still show 5 logins after domain2 is deleted', async () => {
    const count = await getLoginCount(domain1.id);
    expect(count).toBe(5);
  });
});
