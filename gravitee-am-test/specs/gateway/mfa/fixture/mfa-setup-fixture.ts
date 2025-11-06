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
import { requestAccessToken } from '@management-commands/token-management-commands';
import { createDomain, safeDeleteDomain, startDomain } from '@management-commands/domain-management-commands';
import { buildCreateAndTestUser } from '@management-commands/user-management-commands';
import { createFactor } from '@management-commands/factor-management-commands';
import { createApplication, patchApplication } from '@management-commands/application-management-commands';
import { createDevice } from '@management-commands/device-management-commands';
import { expect } from '@jest/globals';

export interface Domain {
  admin: {
    username: string;
    password: string;
    accessToken: string;
  };

  domain: {
    domainId: string;
    domainHrid: string;
    defaultIdpId: string;
    factors: { id: string }[];
    devices: { id: string }[];
    users: { username: string; password: string }[];
  };
}

export interface Application {
  name: string;
  id: string;
  clientId: string;
  clientSecret: string;
  redirectUris: string[];
}

export interface User {
  username: string;
  password: string;
}

export class TestSuiteContext {
  domain: Domain;
  client: Application;
  user: User;
  clientAuthUrl: string;
  session: any;
  constructor(domain: Domain, client: Application, user: User, authorizationEndpoint: string, session = null) {
    this.domain = domain;
    this.client = client;
    this.user = user;
    this.clientAuthUrl = `${authorizationEndpoint}?response_type=code&client_id=${client.name}&redirect_uri=https://auth-nightly.gravitee.io/myApp/callback`;
    this.session = session;
  }
}

export async function initDomain(domain: Domain, usersCount: number) {
  domain.admin.accessToken = await getAccessToken(domain);
  const domainDetails = await createTestDomain(domain);
  domain.domain.domainId = domainDetails.domainId;
  domain.domain.defaultIdpId = domainDetails.defaultIdpId;
  domain.domain.users = [];
  for (let i = 0; i < usersCount; i++) {
    domain.domain.users.push(await createTestUser(domain));
  }
  domain.domain.factors = await createMockFactors(domain);
  domain.domain.devices = [await createMockDevice(domain), await createCookieDevice(domain)];
}

export async function getAccessToken(domain: Domain): Promise<string> {
  return requestAccessToken(domain.admin.username, domain.admin.password);
}

export async function createTestDomain(domain: Domain): Promise<any> {
  return createDomain(domain.admin.accessToken, domain.domain.domainHrid, domain.domain.domainHrid).then((domain) => {
    return {
      domainId: domain.id,
      defaultIdpId: `default-idp-${domain.id}`,
    };
  });
}

export async function enableDomain(domain: Domain): Promise<any> {
  await startDomain(domain.domain.domainId, domain.admin.accessToken);
}

export async function createTestUser(ctx: Domain): Promise<any> {
  const password = 'SomeP@ssw0rd';
  const random = Math.floor(Math.random() * 200000);
  return buildCreateAndTestUser(ctx.domain.domainId, ctx.admin.accessToken, random, false, password).then((user) => {
    return {
      username: user.username,
      password: password,
    };
  });
}

export async function createMockFactors(ctx: Domain): Promise<any> {
  const createFactorBody = {
    type: 'mock-am-factor',
    factorType: 'MOCK',
    configuration: `{"code": "1234"}`,
    name: 'mock-factor',
  };
  const factor1 = await createFactor(ctx.domain.domainId, ctx.admin.accessToken, createFactorBody);
  const factor2 = await createFactor(ctx.domain.domainId, ctx.admin.accessToken, createFactorBody);
  return [{ id: factor1.id }, { id: factor2.id }];
}

export async function createMockDevice(ctx: Domain) {
  const body = {
    configuration: '{}',
    name: 'device',
    type: 'fingerprintjs-v3-community-device-identifier',
  };
  const device = await createDevice(ctx.domain.domainId, ctx.admin.accessToken, body);
  return { id: device.id };
}

export async function createCookieDevice(ctx: Domain) {
  const body = {
    configuration: '{}',
    name: 'cookie',
    type: 'cookie-device-identifier',
  };
  const device = await createDevice(ctx.domain.domainId, ctx.admin.accessToken, body);
  return { id: device.id };
}

export async function removeDomain(ctx: Domain) {
  if (ctx.domain?.domainId) {
    await safeDeleteDomain(ctx.domain.domainId, ctx.admin.accessToken);
  }
}

export async function initClient(domain: Domain, applicationName: string, applicationSettings: any) {
  const client = {
    clientId: applicationName,
    name: applicationName,
    id: applicationName,
    clientSecret: applicationName,
    redirectUris: ['https://auth-nightly.gravitee.io/myApp/callback'],
  } as Application;

  const applicationId = await createApp(domain, client);
  await patchApplication(domain.domain.domainId, domain.admin.accessToken, applicationSettings, applicationId);
  return client;
}

async function createApp(domain: Domain, client: Application) {
  const body = {
    name: client.name,
    type: 'WEB',
    description: client.name,
    clientId: client.clientId,
    clientSecret: client.clientSecret,
    redirectUris: client.redirectUris,
  };
  const app = await createApplication(domain.domain.domainId, domain.admin.accessToken, body);

  const enableIDPBody = {
    identityProviders: [{ identity: domain.domain.defaultIdpId, selectionRule: '', priority: 0 }],
  };
  await patchApplication(domain.domain.domainId, domain.admin.accessToken, enableIDPBody, app.id);
  return app.id;
}

export const createSMSFactor = async (domain, accessToken, sfrResource) => {
  const smsFactor = await createFactor(domain.id, accessToken, {
    name: 'sms-factor',
    factorType: 'SMS',
    type: 'sms-am-factor',
    configuration: JSON.stringify({
      countryCodes: 'fr',
      graviteeResource: sfrResource.id,
      messageBody: "{#context.attributes['code']}",
      returnDigits: 6,
      expiresAfter: 300,
    }),
  });
  expect(smsFactor).toBeDefined();
  expect(smsFactor).not.toBeNull();
  expect(smsFactor.id).not.toBeNull();
  return smsFactor;
};

export const createMockFactor = async (code, domain, accessToken) => {
  const factor = await createFactor(domain.id, accessToken, {
    type: 'mock-am-factor',
    factorType: 'MOCK',
    configuration: `{\"code\":\"${code}\"}`,
    name: 'Mock Factor',
  });

  expect(factor).toBeDefined();
  expect(factor).not.toBeNull();

  return factor;
};

export const createRecoveryCodeFactor = async (domain, accessToken) => {
  const factor = await createFactor(domain.id, accessToken, {
    type: 'recovery-code-am-factor',
    factorType: 'Recovery Code',
    configuration: '{"digit":5,"count":6}',
    name: 'Recovery Code',
  });

  expect(factor).toBeDefined();
  expect(factor).not.toBeNull();

  return factor;
};

export const createEmailFactor = async (smtpResource, domain, accessToken) => {
  const factor = await createFactor(domain.id, accessToken, {
    type: 'email-am-factor',
    factorType: 'EMAIL',
    configuration: `{\"graviteeResource\":\"${smtpResource.id}\",\"returnDigits\":6}`,
    name: 'Email',
  });

  expect(factor).toBeDefined();
  expect(factor).not.toBeNull();

  return factor;
};

export const createOtpFactor = async (domain, accessToken) => {
  return await createFactor(domain.id, accessToken, {
    type: 'otp-am-factor',
    factorType: 'TOTP',
    configuration: '{"issuer":"Gravitee.io","algorithm":"HmacSHA1","timeStep":"30","returnDigits":"6"}',
    name: 'totp Factor',
  });
};

export const createCallFactor = async (domain, accessToken, twilioResource, name) => {
  const callFactor = await createFactor(domain.id, accessToken, {
    type: 'call-am-factor',
    factorType: 'CALL',
    name: name,
    configuration: JSON.stringify({
      countryCodes: 'fr',
      graviteeResource: twilioResource.id,
    }),
  });
  expect(callFactor).toBeDefined();
  expect(callFactor.id).not.toBeNull();
  return callFactor;
};
