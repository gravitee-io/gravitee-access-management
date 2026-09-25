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
import { describe, expect } from '@jest/globals';
import { randomBytes } from 'crypto';
import request from 'supertest';
import jwt from 'jsonwebtoken';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createDomain, safeDeleteDomain, startDomain, waitForOidcReady } from '@management-commands/domain-management-commands';
import { getAllIdps, createIdp } from '@management-commands/idp-management-commands';
import { getAllCertificates } from '@management-commands/certificate-management-commands';
import { createScope } from '@management-commands/scope-management-commands';
import { createProtectedResource } from '@management-commands/protected-resources-management-commands';
import { createExtensionGrant } from '@management-commands/extension-grant-commands';
import { buildCreateAndTestUser } from '@management-commands/user-management-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { getDomainManagerUrl } from '@management-commands/service/utils';
import { uniqueName } from '@utils-commands/misc';
import { retryUntil } from '@utils-commands/retry';
import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
import { User } from '@management-models/User';
import { Fixture } from '../../../test-fixture';
import { createTrustedDomain } from '../../../management/domain/fixtures/cross-app-access-fixture';
import { createTrustedIssuerKeyMaterial } from '../../token-exchange/fixtures/trusted-issuer-jwt-helper';
import { TOKEN_EXCHANGE_TEST } from '../../token-exchange/fixtures/token-exchange-fixture';
import { ID_JAG_JOSE_TYPE, ID_JAG_TOKEN_TYPE } from '../../token-exchange/fixtures/id-jag-fixture';
import {
  createIdJagIssuerApp,
  crossAppAccessTrustedDomain,
  enableIdJagTokenExchange,
  exchangeForIdJag,
  requestIdToken,
} from './id-jag-issuer';

export { ID_JAG_JOSE_TYPE, ID_JAG_TOKEN_TYPE };

export const JWT_BEARER_GRANT = 'urn:ietf:params:oauth:grant-type:jwt-bearer';
export const XAA_EXTENSION_GRANT_TYPE = 'xaa-am-extension-grant';
export const JWT_BEARER_EXTENSION_GRANT_TYPE = 'jwtbearer-am-extension-grant';

export const ISSUER_SCOPE = {
  read: 'tool.read',
  write: 'tool.write',
  admin: 'tool.admin',
  ghost: 'tool.ghost',
};

export const RESOURCE_SCOPE = {
  read: 'mcp.read',
  write: 'mcp.write',
  admin: 'mcp.admin',
  ghost: 'mcp.ghost',
};

const SCOPE_MAPPINGS = {
  [ISSUER_SCOPE.read]: RESOURCE_SCOPE.read,
  [ISSUER_SCOPE.write]: RESOURCE_SCOPE.write,
  [ISSUER_SCOPE.admin]: RESOURCE_SCOPE.admin,
  [ISSUER_SCOPE.ghost]: RESOURCE_SCOPE.ghost,
};

const SUBJECT_TOKEN_SCOPE = ['openid', ...Object.values(ISSUER_SCOPE)].join(' ');
const DEFAULT_ASSERTION_SCOPE = `${ISSUER_SCOPE.read} ${ISSUER_SCOPE.write}`;

const THIRD_PARTY_PUBLIC_KEY =
  'AAAAB3NzaC1yc2EAAAADAQABAAABAQC7VJTUt9Us8cKjMzEfYyjiWA4R4/M2bS1GB4t7NXp98C3SC6dVMvDuictGeurT8jNbvJZHtCSuYEvuNMoSfm76oqFvAp8Gy0iz5sxjZmSnXyCdPEovGhLa0VzMaQ8s+CLOyS56YyCFGeJZqgtzJ6GR3eqoYSW9b9UMvkBpZODSctWSNGj3P7jRFDO5VoTwCQAWbFnOjDfH5Ulgp2PKSQnSJP3AJLQNFNe7br1XbrhV//eO+t51mIpGSDCUv3E0DDFcWDTH9cXDTTlRZVEiR2BwpZOOkE/Z0/BVnhZYL71oZV34bKfWjQIt6V/isSMahdsAASACp4ZTGtwiVuNd9tyb';

export const THIRD_PARTY_JWT =
  'eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.Eci61G6w4zh_u9oOCk_v1M_sKcgk0svOmW4ZsL-rt4ojGUH2QY110bQTYNwbEVlowW7phCg7vluX_MCKVwJkxJT6tMk2Ij3Plad96Jf2G2mMsKbxkC-prvjvQkBFYWrYnKWClPBRCyIcG0dVfBvqZ8Mro3t5bX59IKwQ3WZ7AtGBYz5BSiBlrKkp6J1UmP_bFV3eEzIHEFgzRa3pbr4ol4TK6SnAoF88rLr2NhEz9vpdHglUMlOBQiqcZwqrI-Z4XDyDzvnrpujIToiepq9bCimPgVkP54VoZzy-mMSGbthYpLqsL_4MQXaI1Uf_wKFAUuAtzVn4-ebgsKOpvKNzVA';

export interface AssertionOptions {
  scope?: string;
  resource?: string;
  audience?: string;
  user?: 'known' | 'stranger';
}

export interface RedeemingAgent {
  clientId: string;
  clientSecret: string;
  basicAuth: string;
  assertion: (options?: AssertionOptions) => Promise<string>;
  redeem: (assertion: string, extraParams?: string) => request.Test;
}

export interface CraftOptions {
  algorithm?: jwt.Algorithm;
  key?: string;
  type?: string;
  kid?: string;
}

export interface IdJagRedemptionFixtureConfig {
  binding?: boolean;
  coexistence?: boolean;
  selfIssued?: boolean;
}

export interface IdJagRedemptionFixture extends Fixture {
  accessToken: string;
  issuerDomain: Domain;
  resourceDomain: Domain;
  issuerOidc: Record<string, any>;
  resourceOidc: Record<string, any>;
  knownUser: User;
  strangerUser: User;
  boundUserSub: string;
  mcpResource: string;
  otherResource: string;
  unregisteredResource: string;
  strangerAudience: string;
  checkGrantId: string;
  agent: RedeemingAgent;
  impostor: RedeemingAgent;
  publicAgent: RedeemingAgent;
  binder?: RedeemingAgent;
  legacy?: RedeemingAgent;
  selfIssuedAssertion: () => Promise<string>;
  craftAssertion: (claims: Record<string, unknown>, options?: CraftOptions) => string;
  craftedClaims: (overrides?: Record<string, unknown>) => Record<string, unknown>;
  awaitTokenAudit: (status: 'SUCCESS' | 'FAILURE', matches: (detail: any) => boolean, since?: number) => Promise<any>;
}

const now = () => Math.floor(Date.now() / 1000);

export const decodeToken = (token: string): any => jwt.decode(token);

export const audiencesOf = (payload: any): string[] => (Array.isArray(payload.aud) ? payload.aud : [payload.aud]);

export const scopesOf = (scope: string): string[] => (scope ? scope.split(' ').sort() : []);

export const setupIdJagRedemptionFixture = async (config: IdJagRedemptionFixtureConfig = {}): Promise<IdJagRedemptionFixture> => {
  const accessToken = await requestAdminAccessToken();
  const strayKey = createTrustedIssuerKeyMaterial();

  const resourceDomain = await createDomain(accessToken, uniqueName('id-jag-ras', true), 'ID-JAG redemption - resource server');
  for (const scope of [RESOURCE_SCOPE.read, RESOURCE_SCOPE.write, RESOURCE_SCOPE.admin]) {
    await createScope(resourceDomain.id, accessToken, { key: scope, name: scope, description: scope });
  }

  const mcpResource = `https://mcp-${resourceDomain.hrid}.example.com`;
  const otherResource = `https://other-${resourceDomain.hrid}.example.com`;
  const unregisteredResource = `https://nowhere-${resourceDomain.hrid}.example.com`;

  await createProtectedResource(resourceDomain.id, accessToken, {
    name: uniqueName('mcp-server', true),
    type: 'MCP_SERVER',
    resourceIdentifiers: [mcpResource],
    features: [{ key: 'default', type: 'MCP_TOOL', scopes: [RESOURCE_SCOPE.read, RESOURCE_SCOPE.write, RESOURCE_SCOPE.admin] }],
  } as any);
  await createProtectedResource(resourceDomain.id, accessToken, {
    name: uniqueName('other-server', true),
    type: 'MCP_SERVER',
    resourceIdentifiers: [otherResource],
    features: [{ key: 'default', type: 'MCP_TOOL', scopes: [RESOURCE_SCOPE.read] }],
  } as any);

  const createXaaGrant = (name: string, configuration: Record<string, unknown> = {}) =>
    createExtensionGrant(resourceDomain.id, accessToken, {
      type: XAA_EXTENSION_GRANT_TYPE,
      grantType: JWT_BEARER_GRANT,
      name: uniqueName(name, true),
      configuration: JSON.stringify({ grantType: JWT_BEARER_GRANT, userBindingCriteria: [], ...configuration }),
    });

  const checkGrant = await createXaaGrant('xaa-check');
  const bindingGrant = config.binding
    ? await createXaaGrant('xaa-binding', { userBindingCriteria: [{ attribute: 'emails.value', expression: "{#token['email']}" }] })
    : null;
  const jwtBearerGrant = config.coexistence
    ? await createExtensionGrant(resourceDomain.id, accessToken, {
        type: JWT_BEARER_EXTENSION_GRANT_TYPE,
        grantType: JWT_BEARER_GRANT,
        name: uniqueName('jwt-bearer', true),
        configuration: JSON.stringify({ publicKey: `ssh-rsa ${THIRD_PARTY_PUBLIC_KEY}` }),
      })
    : null;

  const resourceApp = (name: string, grantTypes: string[], oauth: Record<string, unknown> = {}) =>
    createTestApp(uniqueName(name, true), resourceDomain, accessToken, 'WEB', {
      settings: {
        oauth: {
          redirectUris: [TOKEN_EXCHANGE_TEST.REDIRECT_URI],
          grantTypes,
          scopeSettings: [
            { scope: RESOURCE_SCOPE.read, defaultScope: false },
            { scope: RESOURCE_SCOPE.write, defaultScope: false },
          ],
          ...oauth,
        },
      },
    } as any);

  const suffixed = (grantId: string) => `${JWT_BEARER_GRANT}~${grantId}`;

  const agentApp = await resourceApp('xaa-agent', [suffixed(checkGrant.id), 'refresh_token']);
  const publicApp = await resourceApp('xaa-public-agent', [suffixed(checkGrant.id)], { tokenEndpointAuthMethod: 'none' });
  const binderApp = bindingGrant ? await resourceApp('xaa-binder', [suffixed(bindingGrant.id)]) : null;
  const legacyApp = jwtBearerGrant ? await resourceApp('xaa-legacy', [JWT_BEARER_GRANT, suffixed(jwtBearerGrant.id)]) : null;

  const gatewayUrl = (process.env.AM_GATEWAY_URL || 'http://localhost:8092').replace(/\/$/, '');
  const wellKnownOf = (hrid: string) => `${gatewayUrl}/${hrid}/oidc/.well-known/openid-configuration`;
  const selfIssuer = `${gatewayUrl}/${resourceDomain.hrid}/oidc`;

  const trustedDomainBody = crossAppAccessTrustedDomain();

  let selfIssuingApp: Application = null;
  if (config.selfIssued) {
    await enableIdJagTokenExchange(resourceDomain.id, accessToken);
    const selfTrustedDomain: any = await createTrustedDomain(
      resourceDomain.id,
      accessToken,
      trustedDomainBody('self', selfIssuer, [{ name: 'mcp', resource: mcpResource }], { [RESOURCE_SCOPE.read]: RESOURCE_SCOPE.read }),
    );
    const [selfMcp] = selfTrustedDomain.crossAppAccess.resourceServers;
    const resourceCertificates = await getAllCertificates(resourceDomain.id, accessToken);
    const resourceIdps = await getAllIdps(resourceDomain.id, accessToken);
    const resourceDefaultIdp = resourceIdps.values().next().value;
    selfIssuingApp = await createIdJagIssuerApp('self-issuer', resourceDomain, accessToken, {
      certificate: resourceCertificates[0].id,
      identityProvider: resourceDefaultIdp.id,
      scopes: [RESOURCE_SCOPE.read],
      resourceServers: [{ trustDomainId: selfTrustedDomain.id, resourceServerId: selfMcp.id, clientId: agentApp.settings.oauth.clientId }],
    });
  }

  const startedResourceDomain = await startDomain(resourceDomain.id, accessToken);
  const resourceOidcResponse = await waitForOidcReady(startedResourceDomain.hrid, { timeoutMs: 60000, intervalMs: 500 });
  expect(resourceOidcResponse.status).toBe(200);
  const resourceOidc = resourceOidcResponse.body;

  const issuerDomain = await createDomain(accessToken, uniqueName('id-jag-idp', true), 'ID-JAG redemption - enterprise issuer');
  for (const scope of Object.values(ISSUER_SCOPE)) {
    await createScope(issuerDomain.id, accessToken, { key: scope, name: scope, description: scope });
  }

  await enableIdJagTokenExchange(issuerDomain.id, accessToken);

  const strangerAudience = `https://stranger-${resourceDomain.hrid}.example.com/oidc`;
  const partner: any = await createTrustedDomain(
    issuerDomain.id,
    accessToken,
    trustedDomainBody(
      'partner',
      resourceOidc.issuer,
      [
        { name: 'mcp', resource: mcpResource },
        { name: 'nowhere', resource: unregisteredResource },
      ],
      SCOPE_MAPPINGS,
    ),
  );
  const stranger: any = await createTrustedDomain(
    issuerDomain.id,
    accessToken,
    trustedDomainBody('stranger', strangerAudience, [{ name: 'mcp', resource: mcpResource }], SCOPE_MAPPINGS),
  );
  const [partnerMcp, partnerNowhere] = partner.crossAppAccess.resourceServers;
  const [strangerMcp] = stranger.crossAppAccess.resourceServers;

  const issuerIdps = await getAllIdps(issuerDomain.id, accessToken);
  const issuerDefaultIdp = issuerIdps.values().next().value;
  const issuerCertificates = await getAllCertificates(issuerDomain.id, accessToken);
  const issuerCertificate = issuerCertificates[0].id;

  const issuerApp = (name: string, targetClientId: string, tokenCustomClaims: Record<string, unknown>[] = []) =>
    createIdJagIssuerApp(name, issuerDomain, accessToken, {
      certificate: issuerCertificate,
      identityProvider: issuerDefaultIdp.id,
      scopes: Object.values(ISSUER_SCOPE),
      resourceServers: [
        { trustDomainId: partner.id, resourceServerId: partnerMcp.id, clientId: targetClientId },
        { trustDomainId: partner.id, resourceServerId: partnerNowhere.id, clientId: targetClientId },
        { trustDomainId: stranger.id, resourceServerId: strangerMcp.id, clientId: targetClientId },
      ],
      tokenCustomClaims,
    });

  const emailClaim = [{ tokenType: 'ID_JAG', claimName: 'email', claimValue: "{#context.attributes['user'].email}" }];

  const agentIssuerApp = await issuerApp('agent-issuer', agentApp.settings.oauth.clientId);
  const impostorIssuerApp = await issuerApp('impostor-issuer', 'some-other-agent');
  const publicIssuerApp = await issuerApp('public-issuer', publicApp.settings.oauth.clientId);
  const binderIssuerApp = binderApp ? await issuerApp('binder-issuer', binderApp.settings.oauth.clientId, emailClaim) : null;
  const legacyIssuerApp = legacyApp ? await issuerApp('legacy-issuer', legacyApp.settings.oauth.clientId) : null;

  const startedIssuerDomain = await startDomain(issuerDomain.id, accessToken);
  const issuerOidcResponse = await waitForOidcReady(startedIssuerDomain.hrid, { timeoutMs: 60000, intervalMs: 500 });
  expect(issuerOidcResponse.status).toBe(200);
  const issuerOidc = issuerOidcResponse.body;

  const knownUser = await buildCreateAndTestUser(issuerDomain.id, accessToken, 0);
  const strangerUser = await buildCreateAndTestUser(issuerDomain.id, accessToken, 1);
  const localUser = binderApp || selfIssuingApp ? await buildCreateAndTestUser(resourceDomain.id, accessToken, 0) : null;

  const oidcIdp = (name: string, hrid: string) => ({
    name: uniqueName(name, true),
    type: 'oauth2-generic-am-idp',
    external: true,
    configuration: JSON.stringify({
      clientId: 'discovery-only',
      clientSecret: 'discovery-only',
      clientAuthenticationMethod: 'client_secret_basic',
      wellKnownUri: wellKnownOf(hrid),
      responseType: 'code',
      encodeRedirectUri: false,
      useIdTokenForUserInfo: false,
      signature: 'RSA_RS256',
      publicKeyResolver: 'GIVEN_KEY',
      scopes: ['openid'],
      connectTimeout: 10000,
      idleTimeout: 10000,
      maxPoolSize: 200,
      storeOriginalTokens: false,
      codeChallengeMethod: 'S256',
      responseMode: 'default',
    }),
  });

  const provisioningSecret = randomBytes(32).toString('hex');
  let provisionerApp: Application = null;
  await waitForSyncAfter(resourceDomain.id, async () => {
    const enterpriseIdp = await createIdp(resourceDomain.id, accessToken, oidcIdp('enterprise-idp', startedIssuerDomain.hrid));
    if (config.selfIssued) {
      await createIdp(resourceDomain.id, accessToken, oidcIdp('self-idp', startedResourceDomain.hrid));
    }
    const provisioningGrant = await createExtensionGrant(resourceDomain.id, accessToken, {
      type: JWT_BEARER_EXTENSION_GRANT_TYPE,
      grantType: JWT_BEARER_GRANT,
      name: uniqueName('provisioning', true),
      configuration: JSON.stringify({ publicKey: provisioningSecret }),
      createUser: true,
      identityProvider: enterpriseIdp.id,
    });
    provisionerApp = await resourceApp('xaa-provisioner', [suffixed(provisioningGrant.id)]);
  });

  const mintAssertion = async (application: Application, options: AssertionOptions = {}) => {
    const user = options.user === 'stranger' ? strangerUser : knownUser;
    const idToken = await requestIdToken(issuerOidc.token_endpoint, application, user, SUBJECT_TOKEN_SCOPE);
    return exchangeForIdJag(
      issuerOidc.token_endpoint,
      application,
      idToken,
      options.audience ?? resourceOidc.issuer,
      options.resource ?? mcpResource,
      options.scope ?? DEFAULT_ASSERTION_SCOPE,
    );
  };

  const redeemAs =
    (application: Application, publicClient: boolean) =>
    (assertion: string, extraParams = '') => {
      const body = `grant_type=${JWT_BEARER_GRANT}&assertion=${assertion}${extraParams}`;
      const headers: Record<string, string> = { 'Content-type': 'application/x-www-form-urlencoded' };
      if (publicClient) {
        return performPost(resourceOidc.token_endpoint, '', `${body}&client_id=${application.settings.oauth.clientId}`, headers);
      }
      headers.Authorization = `Basic ${applicationBase64Token(application)}`;
      return performPost(resourceOidc.token_endpoint, '', body, headers);
    };

  const agentOf = (resourceApplication: Application, issuerApplication: Application, publicClient = false): RedeemingAgent => ({
    clientId: resourceApplication.settings.oauth.clientId,
    clientSecret: resourceApplication.settings.oauth.clientSecret,
    basicAuth: applicationBase64Token(resourceApplication),
    assertion: (options?: AssertionOptions) => mintAssertion(issuerApplication, options),
    redeem: redeemAs(resourceApplication, publicClient),
  });

  const agent = agentOf(agentApp, agentIssuerApp);
  const impostor = agentOf(agentApp, impostorIssuerApp);
  const publicAgent = agentOf(publicApp, publicIssuerApp, true);
  const binder = binderApp ? agentOf(binderApp, binderIssuerApp) : null;
  const legacy = legacyApp ? agentOf(legacyApp, legacyIssuerApp) : null;

  const craftedClaims = (overrides: Record<string, unknown> = {}) => {
    const issuedAt = now();
    return {
      iss: issuerOidc.issuer,
      sub: 'crafted-subject',
      aud: resourceOidc.issuer,
      client_id: agent.clientId,
      resource: mcpResource,
      scope: RESOURCE_SCOPE.read,
      jti: uniqueName('jti', true),
      iat: issuedAt,
      exp: issuedAt + 300,
      ...overrides,
    };
  };

  const craftAssertion = (claims: Record<string, unknown>, options: CraftOptions = {}) => {
    const algorithm = options.algorithm ?? 'RS256';
    return jwt.sign(claims, options.key ?? strayKey.privateKeyPem, {
      algorithm,
      noTimestamp: true,
      header: { alg: algorithm, typ: options.type ?? ID_JAG_JOSE_TYPE, ...(options.kid ? { kid: options.kid } : {}) },
    });
  };

  const selfIssuedAssertion = async () => {
    expect(resourceOidc.issuer).toBe(selfIssuer);
    const scope = `openid ${RESOURCE_SCOPE.read}`;
    const idToken = await requestIdToken(resourceOidc.token_endpoint, selfIssuingApp, localUser, scope);
    return exchangeForIdJag(resourceOidc.token_endpoint, selfIssuingApp, idToken, selfIssuer, mcpResource, RESOURCE_SCOPE.read);
  };

  const assertionSubject = decodeToken(await agent.assertion()).sub;
  const provisioningAssertion = jwt.sign({ sub: assertionSubject, exp: now() + 300 }, provisioningSecret, { algorithm: 'HS256' });
  const boundUserSub = await retryUntil(
    async () => {
      const response = await redeemAs(provisionerApp, false)(provisioningAssertion);
      return response.status === 200 ? decodeToken(response.body.access_token).sub : null;
    },
    (sub) => sub !== null,
    { timeoutMillis: 120000, intervalMillis: 2000 },
  );

  const listTokenAudits = async (status: string, since?: number) => {
    const from = since === undefined ? '' : `&from=${since}`;
    const response = await request(getDomainManagerUrl(resourceDomain.id))
      .get(`/audits?type=TOKEN_CREATED&status=${status}&size=20${from}`)
      .set('Authorization', `Bearer ${accessToken}`);
    return response.status === 200 ? response.body.data ?? [] : [];
  };

  const readTokenAudit = async (auditId: string) => {
    const response = await request(getDomainManagerUrl(resourceDomain.id))
      .get(`/audits/${auditId}`)
      .set('Authorization', `Bearer ${accessToken}`);
    return response.status === 200 ? response.body : null;
  };

  const awaitTokenAudit = async (status: 'SUCCESS' | 'FAILURE', matches: (detail: any) => boolean, since?: number) => {
    const matching = async () => {
      const details = await Promise.all((await listTokenAudits(status, since)).map((audit) => readTokenAudit(audit.id)));
      return details.filter((detail) => detail !== null).find(matches) ?? null;
    };
    return retryUntil(matching, (found) => found !== null, { timeoutMillis: 30000, intervalMillis: 500 });
  };

  return {
    accessToken,
    issuerDomain: startedIssuerDomain,
    resourceDomain: startedResourceDomain,
    issuerOidc,
    resourceOidc,
    knownUser,
    strangerUser,
    boundUserSub,
    mcpResource,
    otherResource,
    unregisteredResource,
    strangerAudience,
    checkGrantId: checkGrant.id,
    agent,
    impostor,
    publicAgent,
    binder,
    legacy,
    selfIssuedAssertion,
    craftAssertion,
    craftedClaims,
    awaitTokenAudit,
    cleanUp: async () => {
      await safeDeleteDomain(issuerDomain.id, accessToken);
      await safeDeleteDomain(resourceDomain.id, accessToken);
    },
  };
};
