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
import { afterAll, beforeAll, describe, expect, it, jest } from '@jest/globals';
import { requestAccessToken, requestAdminAccessToken } from '@management-commands/token-management-commands';
import { deleteDomain, setupDomainForTest } from '@management-commands/domain-management-commands';
import { createApplication } from '@management-commands/application-management-commands';
import { listClientSecrets } from '@management-commands/client-secrets-management-commands';
import { createOrganisationUser, deleteOrganisationUser } from '@management-commands/organisation-user-commands';
import { performGet, performPost } from '@gateway-commands/oauth-oidc-commands';
import { getOrganisationManagementUrl } from '@management-commands/service/utils';
import { uniqueName } from '@utils-commands/misc';

jest.setTimeout(200000);

const PASSWORD = 'SomeP@ssw0rd';
const headers = (token: string) => ({ 'Content-Type': 'application/json', Authorization: `Bearer ${token}` });
const domainPath = (domainId: string, suffix = '') => `/environments/${process.env.AM_DEF_ENV_ID}/domains/${domainId}${suffix}`;

let adminToken: string;
let ownDomain: any;
let foreignDomain: any;
let ownApplication: any;
let foreignApplication: any;
let domainOwner: { id: string; token: string };

beforeAll(async () => {
  adminToken = await requestAdminAccessToken();
  ownDomain = (await setupDomainForTest(uniqueName('cross-domain-own', true), { accessToken: adminToken })).domain;
  foreignDomain = (await setupDomainForTest(uniqueName('cross-domain-foreign', true), { accessToken: adminToken })).domain;

  ownApplication = await createApplication(ownDomain.id, adminToken, {
    name: 'own-service-app',
    type: 'SERVICE',
    clientId: uniqueName('own-app', true),
  });
  foreignApplication = await createApplication(foreignDomain.id, adminToken, {
    name: 'foreign-service-app',
    type: 'SERVICE',
    clientId: uniqueName('foreign-app', true),
  });

  // DOMAIN_OWNER on ownDomain only, no membership on foreignDomain.
  const username = uniqueName('domainowner', true).toLowerCase();
  const user = await createOrganisationUser(adminToken, {
    firstName: 'Domain',
    lastName: 'Owner',
    email: `${username}@test.com`,
    username,
    password: PASSWORD,
    preRegistration: false,
  });
  const roles = await performGet(getOrganisationManagementUrl(), '/roles?type=DOMAIN', headers(adminToken));
  expect(roles.status).toBe(200);
  const domainOwnerRole = roles.body.find((role) => role.name === 'DOMAIN_OWNER');
  expect(domainOwnerRole).toBeDefined();
  const membership = await performPost(
    getOrganisationManagementUrl(),
    domainPath(ownDomain.id, '/members'),
    { memberId: user.id, memberType: 'USER', role: domainOwnerRole.id },
    headers(adminToken),
  );
  expect(membership.status).toBe(201);
  domainOwner = { id: user.id, token: await requestAccessToken(username, PASSWORD) };
});

afterAll(async () => {
  if (domainOwner) {
    await deleteOrganisationUser(adminToken, domainOwner.id);
  }
  for (const domain of [ownDomain, foreignDomain]) {
    if (domain) {
      await deleteDomain(domain.id, adminToken);
    }
  }
});

/**
 * Owning domain A must not give access to an application of domain B addressed as
 * /domains/A/applications/{appB}/...
 */
describe('A domain owner cannot reach an application of another domain through their own domain', () => {
  const foreignApplicationPath = (suffix: string) => domainPath(ownDomain.id, `/applications/${foreignApplication.id}${suffix}`);

  it('should refuse creating a secret for an application of another domain', async () => {
    const secretsBefore = await listClientSecrets(foreignDomain.id, adminToken, foreignApplication.id);

    const response = await performPost(
      getOrganisationManagementUrl(),
      foreignApplicationPath('/secrets'),
      { name: 'stolen' },
      headers(domainOwner.token),
    );

    expect(response.status).toBe(403);
    expect(response.body.secret).toBeUndefined();
    const secretsAfter = await listClientSecrets(foreignDomain.id, adminToken, foreignApplication.id);
    expect(secretsAfter.length).toEqual(secretsBefore.length);
  });

  it('should refuse listing the secrets of an application of another domain', async () => {
    const response = await performGet(getOrganisationManagementUrl(), foreignApplicationPath('/secrets'), headers(domainOwner.token));

    expect(response.status).toBe(403);
  });

  it('should refuse creating an email template for an application of another domain', async () => {
    const response = await performPost(
      getOrganisationManagementUrl(),
      foreignApplicationPath('/emails'),
      { template: 'RESET_PASSWORD', from: 'attacker@example.com', subject: 'x', content: 'x', expiresAfter: 86400 },
      headers(domainOwner.token),
    );

    expect(response.status).toBe(403);
  });

  it('should refuse reading the analytics of an application of another domain', async () => {
    const response = await performGet(
      getOrganisationManagementUrl(),
      foreignApplicationPath('/analytics?type=count&field=application&interval=3600000&from=0&to=1'),
      headers(domainOwner.token),
    );

    expect(response.status).toBe(403);
  });

  it('should refuse reading a flow of an application of another domain', async () => {
    const response = await performGet(
      getOrganisationManagementUrl(),
      foreignApplicationPath('/flows/any-flow'),
      headers(domainOwner.token),
    );

    expect(response.status).toBe(403);
  });

  it('should still let a DOMAIN_OWNER create a secret for an application of their own domain', async () => {
    const response = await performPost(
      getOrganisationManagementUrl(),
      domainPath(ownDomain.id, `/applications/${ownApplication.id}/secrets`),
      { name: 'own-domain-secret' },
      headers(domainOwner.token),
    );

    expect(response.status).toBe(201);
    expect(response.body.secret).toBeDefined();
  });
});
