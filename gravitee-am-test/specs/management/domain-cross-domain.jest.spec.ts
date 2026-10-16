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
import { afterAll, beforeAll, describe, expect, it, jest } from "@jest/globals";
import {
  requestAccessToken,
  requestAdminAccessToken,
} from "@management-commands/token-management-commands";
import {
  deleteDomain,
  setupDomainForTest,
} from "@management-commands/domain-management-commands";
import {
  createOrganisationUser,
  deleteOrganisationUser,
} from "@management-commands/organisation-user-commands";
import { getAllCertificates } from "@management-commands/certificate-management-commands";
import {
  performDelete,
  performGet,
  performPost,
} from "@gateway-commands/oauth-oidc-commands";
import { getOrganisationManagementUrl } from "@management-commands/service/utils";
import { getPublicKey } from "@utils-commands/jwt";
import { uniqueName } from "@utils-commands/misc";

const supertest = require("supertest");

jest.setTimeout(200000);

const PASSWORD = "SomeP@ssw0rd";
const headers = (token: string) => ({
  "Content-Type": "application/json",
  Authorization: `Bearer ${token}`,
});
const domainPath = (domainId: string, suffix = "") =>
  `/environments/${process.env.AM_DEF_ENV_ID}/domains/${domainId}${suffix}`;
const performPut = (uri: string, body: object, token: string) =>
  supertest(getOrganisationManagementUrl())
    .put(uri)
    .set(headers(token))
    .send(body);

let adminToken: string;
let ownDomain: any;
let foreignDomain: any;
let domainOwner: { id: string; token: string };

beforeAll(async () => {
  adminToken = await requestAdminAccessToken();
  ownDomain = (
    await setupDomainForTest(uniqueName("cross-domain-own", true), {
      accessToken: adminToken,
    })
  ).domain;
  foreignDomain = (
    await setupDomainForTest(uniqueName("cross-domain-foreign", true), {
      accessToken: adminToken,
    })
  ).domain;

  // DOMAIN_OWNER on ownDomain only, no membership on foreignDomain.
  const username = uniqueName("domainowner", true).toLowerCase();
  const user = await createOrganisationUser(adminToken, {
    firstName: "Domain",
    lastName: "Owner",
    email: `${username}@test.com`,
    username,
    password: PASSWORD,
    preRegistration: false,
  });
  const roles = await performGet(
    getOrganisationManagementUrl(),
    "/roles?type=DOMAIN",
    headers(adminToken)
  );
  expect(roles.status).toBe(200);
  const domainOwnerRole = roles.body.find(
    (role) => role.name === "DOMAIN_OWNER"
  );
  expect(domainOwnerRole).toBeDefined();
  const membership = await performPost(
    getOrganisationManagementUrl(),
    domainPath(ownDomain.id, "/members"),
    { memberId: user.id, memberType: "USER", role: domainOwnerRole.id },
    headers(adminToken)
  );
  expect(membership.status).toBe(201);
  domainOwner = {
    id: user.id,
    token: await requestAccessToken(username, PASSWORD),
  };
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

describe("A domain owner cannot reach a certificate of another domain through their own domain", () => {
  const certificatePath = (certificateId: string) =>
    domainPath(ownDomain.id, `/certificates/${certificateId}`);
  const firstCertificateOf = async (domainId: string) =>
    (await getAllCertificates(domainId, adminToken))[0];

  it("should refuse reading a certificate of another domain", async () => {
    const foreignCertificate = await firstCertificateOf(foreignDomain.id);

    const response = await performGet(
      getOrganisationManagementUrl(),
      certificatePath(foreignCertificate.id),
      headers(domainOwner.token)
    );

    expect(response.status).toBe(404);
  });

  it("should refuse updating a certificate of another domain", async () => {
    const foreignCertificate = await firstCertificateOf(foreignDomain.id);

    const response = await performPut(
      certificatePath(foreignCertificate.id),
      {
        name: "updated through another domain",
        type: foreignCertificate.type,
        configuration: "{}",
      },
      domainOwner.token
    );

    expect(response.status).toBe(404);
  });

  it("should refuse deleting a certificate of another domain", async () => {
    const foreignCertificate = await firstCertificateOf(foreignDomain.id);

    const response = await performDelete(
      getOrganisationManagementUrl(),
      certificatePath(foreignCertificate.id),
      headers(domainOwner.token)
    );

    expect(response.status).toBe(404);
    const remaining = await getAllCertificates(foreignDomain.id, adminToken);
    expect(remaining.map((certificate) => certificate.id)).toContain(
      foreignCertificate.id
    );
  });

  it("should still let a DOMAIN_OWNER read a certificate of their own domain", async () => {
    const ownCertificate = await firstCertificateOf(ownDomain.id);

    const response = await performGet(
      getOrganisationManagementUrl(),
      certificatePath(ownCertificate.id),
      headers(domainOwner.token)
    );

    expect(response.status).toBe(200);
    expect(response.body.id).toEqual(ownCertificate.id);
  });
});

const recaptchaConfiguration =
  '{"siteKey":"cdef","secretKey":"abcd","serviceUrl":"https://www.google.com/recaptcha/api/siteverify","tokenParameterName":"X-Recaptcha-Token","minScore":0.5}';
const totpConfiguration =
  '{"issuer":"Gravitee.io","algorithm":"HmacSHA1","timeStep":"30","returnDigits":"6"}';
const notifierConfiguration =
  '{"endpoint":"http://localhost:8080/ciba/notify/accept-all","headerName":"Authorization","connectTimeout":5000,"idleTimeout":10000,"maxPoolSize":10}';
const smtpConfiguration =
  '{"host":"smtp","port":5025,"from":"admin@test.com","protocol":"smtp","authentication":false,"startTls":false}';
const pluginEntity = (
  label: string,
  segment: string,
  type: string,
  configuration: string,
  extra: object = {}
) => {
  const definition = { name: `isolation ${segment}`, type, configuration };
  return {
    label,
    segment,
    create: { ...definition, ...extra },
    update: { ...definition, name: "updated through another domain" },
  };
};

const domainEntities = [
  {
    label: "a scope",
    segment: "scopes",
    create: {
      key: "isolation_scope",
      name: "isolation scope",
      description: "isolation scope",
    },
    update: {
      name: "updated through another domain",
      description: "updated through another domain",
    },
  },
  pluginEntity("a factor", "factors", "otp-am-factor", totpConfiguration, {
    factorType: "TOTP",
  }),
  pluginEntity(
    "a bot detection",
    "bot-detections",
    "google-recaptcha-v3-am-bot-detection",
    recaptchaConfiguration,
    { detectionType: "CAPTCHA" }
  ),
  pluginEntity(
    "an extension grant",
    "extensionGrants",
    "jwtbearer-am-extension-grant",
    `{"publicKey":"ssh-rsa ${getPublicKey()}"}`,
    {
      grantType: "urn:ietf:params:oauth:grant-type:jwt-bearer",
    }
  ),
  pluginEntity(
    "a device identifier",
    "device-identifiers",
    "fingerprintjs-v3-community-device-identifier",
    "{}"
  ),
  pluginEntity(
    "an authentication device notifier",
    "auth-device-notifiers",
    "http-am-authdevice-notifier",
    notifierConfiguration
  ),
  pluginEntity(
    "a service resource",
    "resources",
    "smtp-am-resource",
    smtpConfiguration
  ),
  pluginEntity(
    "a reporter",
    "reporters",
    "reporter-am-file",
    '{"filename":"isolation-reporter"}'
  ),
];

describe("A domain owner cannot reach a sub-entity of another domain through their own domain", () => {
  const foreignIds = new Map<string, string>();
  const foreignPath = (segment: string) =>
    domainPath(ownDomain.id, `/${segment}/${foreignIds.get(segment)}`);
  const storedForeign = (segment: string) =>
    performGet(
      getOrganisationManagementUrl(),
      domainPath(foreignDomain.id, `/${segment}/${foreignIds.get(segment)}`),
      headers(adminToken)
    );

  beforeAll(async () => {
    for (const entity of domainEntities) {
      const created = await performPost(
        getOrganisationManagementUrl(),
        domainPath(foreignDomain.id, `/${entity.segment}`),
        entity.create,
        headers(adminToken)
      );
      expect(created.status).toBe(201);
      foreignIds.set(entity.segment, created.body.id);
    }
  });

  it.each(domainEntities)(
    "should refuse reading $label of another domain",
    async ({ segment }) => {
      const response = await performGet(
        getOrganisationManagementUrl(),
        foreignPath(segment),
        headers(domainOwner.token)
      );

      expect(response.status).toBe(404);
    }
  );

  it.each(domainEntities)(
    "should refuse updating $label of another domain",
    async ({ segment, create, update }) => {
      const response = await performPut(
        foreignPath(segment),
        update,
        domainOwner.token
      );

      expect(response.status).toBe(404);
      expect((await storedForeign(segment)).body.name).toEqual(create.name);
    }
  );

  it.each(domainEntities)(
    "should refuse deleting $label of another domain",
    async ({ segment }) => {
      const response = await performDelete(
        getOrganisationManagementUrl(),
        foreignPath(segment),
        headers(domainOwner.token)
      );

      expect(response.status).toBe(404);
      expect((await storedForeign(segment)).status).toBe(200);
    }
  );

  it("should still let a DOMAIN_OWNER read a scope of their own domain", async () => {
    const created = await performPost(
      getOrganisationManagementUrl(),
      domainPath(ownDomain.id, "/scopes"),
      {
        key: "own_isolation_scope",
        name: "own isolation scope",
        description: "own isolation scope",
      },
      headers(adminToken)
    );

    const response = await performGet(
      getOrganisationManagementUrl(),
      domainPath(ownDomain.id, `/scopes/${created.body.id}`),
      headers(domainOwner.token)
    );

    expect(response.status).toBe(200);
    expect(response.body.id).toEqual(created.body.id);
  });
});
