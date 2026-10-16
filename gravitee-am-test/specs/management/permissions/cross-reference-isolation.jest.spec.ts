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
import { setup } from '../../test-fixture';
import { RbacFixture, setupRbacFixture } from './fixtures/rbac-fixture';
import { performDelete, performGet, performPatch, performPost, performPut } from '@gateway-commands/oauth-oidc-commands';
import { getOrganisationManagementUrl } from '@management-commands/service/utils';
import { createApplication } from '@management-commands/application-management-commands';
import { listClientSecrets } from '@management-commands/client-secrets-management-commands';
import { getAllCertificates } from '@management-commands/certificate-management-commands';
import { getPublicKey } from '@utils-commands/jwt';

setup(200000);

let fixture: RbacFixture;

const headers = (token: string) => ({ 'Content-Type': 'application/json', Authorization: `Bearer ${token}` });
const domainPath = (domainId: string, suffix = '') => `/environments/${process.env.AM_DEF_ENV_ID}/domains/${domainId}${suffix}`;

beforeAll(async () => {
  fixture = await setupRbacFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

/**
 * Management API paths name a whole chain of resources — organization, environment, domain,
 * application. Authorising only the leaf would let a caller reach a resource by quoting a parent
 * they are entitled to instead of the one that actually owns it. `haveConsistentReferenceIds`
 * exists to reject that, and these cases are issued **as the administrator**: a caller with every
 * permission on both domains. Permissions therefore cannot account for the refusal — only the
 * inconsistent path can.
 */
describe('Cross-reference isolation - a resource cannot be reached through a parent that does not own it', () => {
  it('should serve an application addressed through its own domain', async () => {
    const response = await performGet(
      getOrganisationManagementUrl(),
      domainPath(fixture.domain.id, `/applications/${fixture.application.id}`),
      headers(fixture.adminToken),
    );

    expect(response.status).toBe(200);
    expect(response.body.id).toEqual(fixture.application.id);
  });

  it('should refuse an application addressed through an unrelated domain', async () => {
    const response = await performGet(
      getOrganisationManagementUrl(),
      domainPath(fixture.otherDomain.id, `/applications/${fixture.application.id}`),
      headers(fixture.adminToken),
    );

    expect(response.status).toBe(403);
    expect(response.body.message).toEqual('Permission denied');
  });

  it('should refuse a sub-resource addressed through an unrelated domain', async () => {
    const response = await performGet(
      getOrganisationManagementUrl(),
      domainPath(fixture.otherDomain.id, `/applications/${fixture.application.id}/members`),
      headers(fixture.adminToken),
    );

    expect(response.status).toBe(403);
    expect(response.body.message).toEqual('Permission denied');
  });
});

describe('Cross-reference isolation - a domain owner is confined to their own domain', () => {
  it('should let a DOMAIN_OWNER read the domain they are assigned to', async () => {
    const response = await performGet(getOrganisationManagementUrl(), domainPath(fixture.domain.id), headers(fixture.domainOwner.token));

    expect(response.status).toBe(200);
    expect(response.body.id).toEqual(fixture.domain.id);
  });

  it('should refuse a DOMAIN_OWNER reading a domain they have no membership on', async () => {
    const response = await performGet(
      getOrganisationManagementUrl(),
      domainPath(fixture.otherDomain.id),
      headers(fixture.domainOwner.token),
    );

    expect(response.status).toBe(403);
    expect(response.body.message).toEqual('Permission denied');
  });

  it('should refuse a DOMAIN_OWNER modifying a domain they have no membership on', async () => {
    const response = await performPatch(
      getOrganisationManagementUrl(),
      domainPath(fixture.otherDomain.id),
      { description: 'modified by a domain owner from elsewhere' },
      headers(fixture.domainOwner.token),
    );

    expect(response.status).toBe(403);
    expect(response.body.message).toEqual('Permission denied');
  });
});

describe('Cross-reference isolation - a domain owner cannot reach a certificate of another domain through their own domain', () => {
  const certificatePath = (certificateId: string) => domainPath(fixture.domain.id, `/certificates/${certificateId}`);
  const firstCertificateOf = async (domainId: string) => (await getAllCertificates(domainId, fixture.adminToken))[0];

  it('should refuse reading a certificate of another domain', async () => {
    const foreignCertificate = await firstCertificateOf(fixture.otherDomain.id);

    const response = await performGet(
      getOrganisationManagementUrl(),
      certificatePath(foreignCertificate.id),
      headers(fixture.domainOwner.token),
    );

    expect(response.status).toBe(404);
  });

  it('should refuse updating a certificate of another domain', async () => {
    const foreignCertificate = await firstCertificateOf(fixture.otherDomain.id);

    const response = await performPut(
      getOrganisationManagementUrl(),
      certificatePath(foreignCertificate.id),
      { name: 'updated through another domain', type: foreignCertificate.type, configuration: '{}' },
      headers(fixture.domainOwner.token),
    );

    expect(response.status).toBe(404);
  });

  it('should refuse deleting a certificate of another domain', async () => {
    const foreignCertificate = await firstCertificateOf(fixture.otherDomain.id);

    const response = await performDelete(
      getOrganisationManagementUrl(),
      certificatePath(foreignCertificate.id),
      headers(fixture.domainOwner.token),
    );

    expect(response.status).toBe(404);
    const remaining = await getAllCertificates(fixture.otherDomain.id, fixture.adminToken);
    expect(remaining.map((certificate) => certificate.id)).toContain(foreignCertificate.id);
  });

  it('should still let a DOMAIN_OWNER read a certificate of their own domain', async () => {
    const ownCertificate = await firstCertificateOf(fixture.domain.id);

    const response = await performGet(
      getOrganisationManagementUrl(),
      certificatePath(ownCertificate.id),
      headers(fixture.domainOwner.token),
    );

    expect(response.status).toBe(200);
    expect(response.body.id).toEqual(ownCertificate.id);
  });
});

const recaptchaConfiguration =
  '{"siteKey":"cdef","secretKey":"abcd","serviceUrl":"https://www.google.com/recaptcha/api/siteverify","tokenParameterName":"X-Recaptcha-Token","minScore":0.5}';
const totpConfiguration = '{"issuer":"Gravitee.io","algorithm":"HmacSHA1","timeStep":"30","returnDigits":"6"}';
const notifierConfiguration =
  '{"endpoint":"http://localhost:8080/ciba/notify/accept-all","headerName":"Authorization","connectTimeout":5000,"idleTimeout":10000,"maxPoolSize":10}';
const smtpConfiguration = '{"host":"smtp","port":5025,"from":"admin@test.com","protocol":"smtp","authentication":false,"startTls":false}';
const pluginEntity = (label: string, segment: string, type: string, configuration: string, extra: object = {}) => {
  const definition = { name: `isolation ${segment}`, type, configuration };
  return { label, segment, create: { ...definition, ...extra }, update: { ...definition, name: 'updated through another domain' } };
};

const domainEntities = [
  {
    label: 'a scope',
    segment: 'scopes',
    create: { key: 'isolation_scope', name: 'isolation scope', description: 'isolation scope' },
    update: { name: 'updated through another domain', description: 'updated through another domain' },
  },
  pluginEntity('a factor', 'factors', 'otp-am-factor', totpConfiguration, { factorType: 'TOTP' }),
  pluginEntity('a bot detection', 'bot-detections', 'google-recaptcha-v3-am-bot-detection', recaptchaConfiguration, {
    detectionType: 'CAPTCHA',
  }),
  pluginEntity('an extension grant', 'extensionGrants', 'jwtbearer-am-extension-grant', `{"publicKey":"ssh-rsa ${getPublicKey()}"}`, {
    grantType: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
  }),
  pluginEntity('a device identifier', 'device-identifiers', 'fingerprintjs-v3-community-device-identifier', '{}'),
  pluginEntity('an authentication device notifier', 'auth-device-notifiers', 'http-am-authdevice-notifier', notifierConfiguration),
  pluginEntity('a service resource', 'resources', 'smtp-am-resource', smtpConfiguration),
  pluginEntity('a reporter', 'reporters', 'reporter-am-file', '{"filename":"isolation-reporter"}'),
];

describe('Cross-reference isolation - a domain owner cannot reach a sub-entity of another domain through their own domain', () => {
  const foreignIds = new Map<string, string>();
  const foreignPath = (segment: string) => domainPath(fixture.domain.id, `/${segment}/${foreignIds.get(segment)}`);
  const storedForeign = (segment: string) =>
    performGet(
      getOrganisationManagementUrl(),
      domainPath(fixture.otherDomain.id, `/${segment}/${foreignIds.get(segment)}`),
      headers(fixture.adminToken),
    );

  beforeAll(async () => {
    for (const entity of domainEntities) {
      const created = await performPost(
        getOrganisationManagementUrl(),
        domainPath(fixture.otherDomain.id, `/${entity.segment}`),
        entity.create,
        headers(fixture.adminToken),
      );
      expect(created.status).toBe(201);
      foreignIds.set(entity.segment, created.body.id);
    }
  });

  it.each(domainEntities)('should refuse reading $label of another domain', async ({ segment }) => {
    const response = await performGet(getOrganisationManagementUrl(), foreignPath(segment), headers(fixture.domainOwner.token));

    expect(response.status).toBe(404);
  });

  it.each(domainEntities)('should refuse updating $label of another domain', async ({ segment, create, update }) => {
    const response = await performPut(getOrganisationManagementUrl(), foreignPath(segment), update, headers(fixture.domainOwner.token));

    expect(response.status).toBe(404);
    expect((await storedForeign(segment)).body.name).toEqual(create.name);
  });

  it.each(domainEntities)('should refuse deleting $label of another domain', async ({ segment }) => {
    const response = await performDelete(getOrganisationManagementUrl(), foreignPath(segment), headers(fixture.domainOwner.token));

    expect(response.status).toBe(404);
    expect((await storedForeign(segment)).status).toBe(200);
  });

  it('should still let a DOMAIN_OWNER read a scope of their own domain', async () => {
    const created = await performPost(
      getOrganisationManagementUrl(),
      domainPath(fixture.domain.id, '/scopes'),
      { key: 'own_isolation_scope', name: 'own isolation scope', description: 'own isolation scope' },
      headers(fixture.adminToken),
    );

    const response = await performGet(
      getOrganisationManagementUrl(),
      domainPath(fixture.domain.id, `/scopes/${created.body.id}`),
      headers(fixture.domainOwner.token),
    );

    expect(response.status).toBe(200);
    expect(response.body.id).toEqual(created.body.id);
  });
});

describe('Cross-reference isolation - an application owner is confined to their own application', () => {
  it('should let an APPLICATION_OWNER read the application they are assigned to', async () => {
    const response = await performGet(
      getOrganisationManagementUrl(),
      domainPath(fixture.domain.id, `/applications/${fixture.application.id}`),
      headers(fixture.appOwner.token),
    );

    expect(response.status).toBe(200);
    expect(response.body.id).toEqual(fixture.application.id);
  });

  it('should refuse an APPLICATION_OWNER reading a sibling application in the same domain', async () => {
    const response = await performGet(
      getOrganisationManagementUrl(),
      domainPath(fixture.domain.id, `/applications/${fixture.otherApplication.id}`),
      headers(fixture.appOwner.token),
    );

    expect(response.status).toBe(403);
    expect(response.body.message).toEqual('Permission denied');
  });

  it('should refuse an APPLICATION_OWNER modifying a sibling application in the same domain', async () => {
    const response = await performPatch(
      getOrganisationManagementUrl(),
      domainPath(fixture.domain.id, `/applications/${fixture.otherApplication.id}`),
      { description: 'modified by the owner of a different application' },
      headers(fixture.appOwner.token),
    );

    expect(response.status).toBe(403);
    expect(response.body.message).toEqual('Permission denied');
  });
});

/**
 * Sub-resources of an application (secrets, emails, analytics, flows) must reject an application
 * that does not belong to the domain of the path, even when the caller owns that domain: owning
 * domain A must not give access to an application of domain B addressed as /domains/A/applications/{appB}.
 */
describe('Cross-reference isolation - a domain owner cannot reach an application of another domain through their own domain', () => {
  let foreignApplication: any;

  beforeAll(async () => {
    foreignApplication = await createApplication(fixture.otherDomain.id, fixture.adminToken, {
      name: 'foreign-service-app',
      type: 'SERVICE',
      clientId: `foreign-${Date.now()}`,
    });
  });

  const foreignApplicationPath = (suffix: string) => domainPath(fixture.domain.id, `/applications/${foreignApplication.id}${suffix}`);

  it('should refuse creating a secret for an application of another domain', async () => {
    const secretsBefore = await listClientSecrets(fixture.otherDomain.id, fixture.adminToken, foreignApplication.id);

    const response = await performPost(
      getOrganisationManagementUrl(),
      foreignApplicationPath('/secrets'),
      { name: 'stolen' },
      headers(fixture.domainOwner.token),
    );

    expect(response.status).toBe(403);
    expect(response.body.secret).toBeUndefined();
    const secretsAfter = await listClientSecrets(fixture.otherDomain.id, fixture.adminToken, foreignApplication.id);
    expect(secretsAfter.length).toEqual(secretsBefore.length);
  });

  it('should refuse listing the secrets of an application of another domain', async () => {
    const response = await performGet(
      getOrganisationManagementUrl(),
      foreignApplicationPath('/secrets'),
      headers(fixture.domainOwner.token),
    );

    expect(response.status).toBe(403);
  });

  it('should refuse creating an email template for an application of another domain', async () => {
    const response = await performPost(
      getOrganisationManagementUrl(),
      foreignApplicationPath('/emails'),
      { template: 'RESET_PASSWORD', from: 'attacker@example.com', subject: 'x', content: 'x', expiresAfter: 86400 },
      headers(fixture.domainOwner.token),
    );

    expect(response.status).toBe(403);
  });

  it('should refuse reading the analytics of an application of another domain', async () => {
    const response = await performGet(
      getOrganisationManagementUrl(),
      foreignApplicationPath('/analytics?type=count&field=application&interval=3600000&from=0&to=1'),
      headers(fixture.domainOwner.token),
    );

    expect(response.status).toBe(403);
  });

  it('should refuse reading a flow of an application of another domain', async () => {
    const response = await performGet(
      getOrganisationManagementUrl(),
      foreignApplicationPath('/flows/any-flow'),
      headers(fixture.domainOwner.token),
    );

    expect(response.status).toBe(403);
  });

  it('should still let a DOMAIN_OWNER create a secret for an application of their own domain', async () => {
    const response = await performPost(
      getOrganisationManagementUrl(),
      domainPath(fixture.domain.id, `/applications/${fixture.application.id}/secrets`),
      { name: 'own-domain-secret' },
      headers(fixture.domainOwner.token),
    );

    expect(response.status).toBe(201);
    expect(response.body.secret).toBeDefined();
  });
});
