import { requestAccessToken } from '@management-commands/token-management-commands';
import { createDomain, deleteDomain, startDomain } from '@management-commands/domain-management-commands';
import { buildCreateAndTestUser } from '@management-commands/user-management-commands';
import { createResource } from '@management-commands/resource-management-commands';
import { createFactor } from '@management-commands/factor-management-commands';
import { createApplication, patchApplication } from '@management-commands/application-management-commands';
import { withRetry } from '@utils-commands/retry';
import { getWellKnownOpenIdConfiguration } from '@gateway-commands/oauth-oidc-commands';
import { createDevice } from '@management-commands/device-management-commands';

export interface MfaTestContext {
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
    user: { username: string; password: string };
  };

  application: {
    name: string;
    id: string;
    clientId: string;
    clientSecret: string;
    redirectUris: string[];
  };

  auth: {
    cookie: string;
    xsrf: string;
  };

  oidc: {
    authorizationEndpoint: string;
    logoutEndpoint: string;
    clientAuthorizationEndpoint: string;
  };
}

export async function init(testContext: MfaTestContext) {
  testContext.admin.accessToken = await getAccessToken(testContext);
  const domainDetails = await createTestDomain(testContext);
  testContext.domain.domainId = domainDetails.domainId;
  testContext.domain.defaultIdpId = domainDetails.defaultIdpId;
  testContext.domain.user = await createTestUser(testContext);
  testContext.domain.factors = await createMockFactors(testContext);
  testContext.domain.devices = [await createMockDevice(testContext)];
  const oidc = await withRetry(() => getWellKnownOpenIdConfiguration(testContext.domain.domainHrid).expect(200));
  testContext.oidc = {
    authorizationEndpoint: oidc.body.authorization_endpoint,
    logoutEndpoint: oidc.body.end_session_endpoint,
    clientAuthorizationEndpoint: '',
  };
}
export async function getAccessToken(ctx: MfaTestContext): Promise<string> {
  return requestAccessToken(ctx.admin.username, ctx.admin.password).then((response) => response.body.access_token);
}

export async function createTestDomain(ctx: MfaTestContext): Promise<any> {
  return createDomain(ctx.admin.accessToken, ctx.domain.domainHrid, ctx.domain.domainHrid)
    .then((domain) => startDomain(domain.id, ctx.admin.accessToken))
    .then((domain) => {
      return {
        domainId: domain.id,
        defaultIdpId: `default-idp-${domain.id}`,
      };
    });
}

export async function createTestUser(ctx: MfaTestContext): Promise<any> {
  const password = 'SomeP@ssw0rd';
  const random = Math.floor(Math.random() * 200000);
  return buildCreateAndTestUser(ctx.domain.domainId, ctx.admin.accessToken, random, false, password).then((user) => {
    return {
      username: user.username,
      password: password,
    };
  });
}

export async function createMockFactors(ctx: MfaTestContext): Promise<any> {
  const createResourceBody = {
    type: 'mock-mfa-am-resource',
    configuration: '{"code": "1234"}',
    name: 'mock-resource',
  };
  const resource = await createResource(ctx.domain.domainId, ctx.admin.accessToken, createResourceBody);
  const createFactorBody = {
    type: 'mock-am-factor',
    factorType: 'MOCK',
    configuration: `{"graviteeResource": "${resource.id}"}`,
    name: 'mock-factor',
  };
  const factor1 = await createFactor(ctx.domain.domainId, ctx.admin.accessToken, createFactorBody);
  const factor2 = await createFactor(ctx.domain.domainId, ctx.admin.accessToken, createFactorBody);
  return [{ id: factor1.id }, { id: factor2.id }];
}

export async function createMockDevice(ctx: MfaTestContext) {
  const body = {
    configuration: '{}',
    name: 'device',
    type: 'fingerprintjs-v3-community-device-identifier',
  };
  const device = await createDevice(ctx.domain.domainId, ctx.admin.accessToken, body);
  const value = await device.value();
  return { id: value.id };
}

export async function createApp(ctx: MfaTestContext) {
  const body = {
    name: ctx.application.name,
    type: 'WEB',
    description: ctx.application.name,
    clientId: ctx.application.clientId,
    clientSecret: ctx.application.clientSecret,
    redirectUris: ctx.application.redirectUris,
  };
  const app = await createApplication(ctx.domain.domainId, ctx.admin.accessToken, body);

  const enableIDPBody = {
    identityProviders: [{ identity: ctx.domain.defaultIdpId, selectionRule: '', priority: 0 }],
  };
  await patchApplication(ctx.domain.domainId, ctx.admin.accessToken, enableIDPBody, app.id);
  return app.id;
}

export async function removeDomain(ctx: MfaTestContext) {
  if (ctx.domain?.domainId) {
    await deleteDomain(ctx.domain.domainId, ctx.admin.accessToken);
  }
}
