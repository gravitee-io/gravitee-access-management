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
import { expect } from '@jest/globals';
import { randomBytes } from 'crypto';
import request from 'supertest';
import jwt from 'jsonwebtoken';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createDomain, safeDeleteDomain, startDomain, waitForOidcReady } from '@management-commands/domain-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { getAllCertificates } from '@management-commands/certificate-management-commands';
import { createScope } from '@management-commands/scope-management-commands';
import { buildCreateAndTestUser } from '@management-commands/user-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { Application } from '@management-models/Application';
import { Domain } from '@management-models/Domain';
import { User } from '@management-models/User';
import { createTrustedDomain } from '../../management/domain/fixtures/cross-app-access-fixture';
import { createTrustedIssuerKeyMaterial } from '../../gateway/token-exchange/fixtures/trusted-issuer-jwt-helper';
import { ID_JAG_JOSE_TYPE } from '../../gateway/token-exchange/fixtures/id-jag-fixture';
import {
  createIdJagIssuerApp,
  crossAppAccessTrustedDomain,
  enableIdJagTokenExchange,
  exchangeForIdJag,
  requestIdToken,
} from '../../gateway/cross-app-access/fixtures/id-jag-issuer';
import { createClient, createIdentityProvider, createRealm, createUser, deleteRealm, linkFederatedIdentity } from './keycloak-admin';
import { KEYCLOAK_EXTERNAL, realmEntityId } from './keycloak-realm';

export const JWT_BEARER_GRANT = 'urn:ietf:params:oauth:grant-type:jwt-bearer';

const IDENTITY_PROVIDER_ALIAS = 'gravitee-am';
const ISSUER_SCOPE = 'tool.read';
const RESOURCE_SCOPE = 'mcp.read';

export interface KeycloakAssertionOptions {
  user?: 'linked' | 'stranger';
  audience?: string;
}

export interface KeycloakIdJagFixture {
  realm: string;
  realmIssuer: string;
  clientId: string;
  keycloakUserId: string;
  issuerDomain: Domain;
  issuerOidc: Record<string, any>;
  strangerAudience: string;
  assertion: (options?: KeycloakAssertionOptions) => Promise<string>;
  impostorAssertion: () => Promise<string>;
  craftAssertion: (overrides: Record<string, unknown>) => string;
  redeem: (assertion: string) => request.Test;
  cleanUp: () => Promise<void>;
}

interface IssuerTarget {
  audience: string;
  strangerAudience: string;
  clientId: string;
  resource: string;
}

interface Issuer {
  domain: Domain;
  oidc: Record<string, any>;
  linkedUser: User;
  linkedSubject: string;
  assertion: (options?: KeycloakAssertionOptions) => Promise<string>;
  impostorAssertion: () => Promise<string>;
  cleanUp: () => Promise<void>;
}

interface Redeemer {
  keycloakUserId: string;
  redeem: (assertion: string) => request.Test;
  cleanUp: () => Promise<void>;
}

const setupIssuer = async (target: IssuerTarget): Promise<Issuer> => {
  const accessToken = await requestAdminAccessToken();
  const domain = await createDomain(accessToken, uniqueName('kc-id-jag-idp', true), 'ID-JAG issued for Keycloak');
  await createScope(domain.id, accessToken, { key: ISSUER_SCOPE, name: ISSUER_SCOPE, description: ISSUER_SCOPE });
  await enableIdJagTokenExchange(domain.id, accessToken);

  const trustedDomainBody = crossAppAccessTrustedDomain();
  const resources = [{ name: 'mcp', resource: target.resource }];
  const scopeMappings = { [ISSUER_SCOPE]: RESOURCE_SCOPE };
  const keycloak: any = await createTrustedDomain(
    domain.id,
    accessToken,
    trustedDomainBody('keycloak', target.audience, resources, scopeMappings),
  );
  const stranger: any = await createTrustedDomain(
    domain.id,
    accessToken,
    trustedDomainBody('stranger', target.strangerAudience, resources, scopeMappings),
  );
  const [keycloakMcp] = keycloak.crossAppAccess.resourceServers;
  const [strangerMcp] = stranger.crossAppAccess.resourceServers;

  const idps = await getAllIdps(domain.id, accessToken);
  const certificates = await getAllCertificates(domain.id, accessToken);
  const issuerApp = (name: string, targetClientId: string) =>
    createIdJagIssuerApp(name, domain, accessToken, {
      certificate: certificates[0].id,
      identityProvider: idps.values().next().value.id,
      scopes: [ISSUER_SCOPE],
      resourceServers: [
        { trustDomainId: keycloak.id, resourceServerId: keycloakMcp.id, clientId: targetClientId },
        { trustDomainId: stranger.id, resourceServerId: strangerMcp.id, clientId: targetClientId },
      ],
    });
  const agentApp = await issuerApp('kc-agent-issuer', target.clientId);
  const impostorApp = await issuerApp('kc-impostor-issuer', 'some-other-agent');

  const startedDomain = await startDomain(domain.id, accessToken);
  const oidcResponse = await waitForOidcReady(startedDomain.hrid, { timeoutMs: 60000, intervalMs: 500 });
  expect(oidcResponse.status).toBe(200);
  const oidc = oidcResponse.body;

  const linkedUser = await buildCreateAndTestUser(domain.id, accessToken, 0);
  const strangerUser = await buildCreateAndTestUser(domain.id, accessToken, 1);

  const mint = async (application: Application, user: User, audience: string) => {
    const idToken = await requestIdToken(oidc.token_endpoint, application, user, `openid ${ISSUER_SCOPE}`);
    return exchangeForIdJag(oidc.token_endpoint, application, idToken, audience, target.resource, ISSUER_SCOPE);
  };

  return {
    domain: startedDomain,
    oidc,
    linkedUser,
    linkedSubject: jwt.decode(await mint(agentApp, linkedUser, target.audience))['sub'],
    assertion: (options: KeycloakAssertionOptions = {}) =>
      mint(agentApp, options.user === 'stranger' ? strangerUser : linkedUser, options.audience ?? target.audience),
    impostorAssertion: () => mint(impostorApp, linkedUser, target.audience),
    cleanUp: () => safeDeleteDomain(domain.id, accessToken),
  };
};

const setupRedeemer = async (realm: string, clientId: string, issuer: Issuer): Promise<Redeemer> => {
  const clientSecret = randomBytes(24).toString('hex');
  const gatewayUrl = (process.env.AM_GATEWAY_URL || 'http://localhost:8092').replace(/\/$/, '');
  const internalGatewayUrl = (process.env.AM_INTERNAL_GATEWAY_URL || gatewayUrl).replace(/\/$/, '');
  const reachableFromKeycloak = (url: string) => url.replace(gatewayUrl, internalGatewayUrl);

  await createRealm(realm);
  await createIdentityProvider(realm, {
    alias: IDENTITY_PROVIDER_ALIAS,
    providerId: 'oidc',
    enabled: true,
    config: {
      issuer: issuer.oidc.issuer,
      jwksUrl: reachableFromKeycloak(issuer.oidc.jwks_uri),
      useJwksUrl: 'true',
      validateSignature: 'true',
      authorizationUrl: reachableFromKeycloak(issuer.oidc.authorization_endpoint),
      tokenUrl: reachableFromKeycloak(issuer.oidc.token_endpoint),
      clientId: 'discovery-only',
      clientSecret: 'discovery-only',
      jwtAuthorizationGrantEnabled: 'true',
      jwtAuthorizationGrantAssertionReuseAllowed: 'false',
    },
  });
  await createClient(realm, {
    clientId,
    secret: clientSecret,
    publicClient: false,
    standardFlowEnabled: false,
    attributes: {
      'oauth2.jwt.authorization.grant.enabled': 'true',
      'oauth2.jwt.authorization.grant.idp': IDENTITY_PROVIDER_ALIAS,
    },
  });
  const keycloakUserId = await createUser(realm, {
    username: issuer.linkedUser.username,
    email: issuer.linkedUser.email,
    firstName: issuer.linkedUser.firstName,
    lastName: issuer.linkedUser.lastName,
    emailVerified: true,
    enabled: true,
  });
  await linkFederatedIdentity(realm, keycloakUserId, IDENTITY_PROVIDER_ALIAS, issuer.linkedSubject, issuer.linkedUser.username);

  const tokenEndpoint = `${KEYCLOAK_EXTERNAL}/realms/${realm}/protocol/openid-connect/token`;
  return {
    keycloakUserId,
    redeem: (assertion: string) =>
      request(tokenEndpoint).post('').auth(clientId, clientSecret).type('form').send({ grant_type: JWT_BEARER_GRANT, assertion }),
    cleanUp: () => deleteRealm(realm),
  };
};

export const setupKeycloakIdJagFixture = async (): Promise<KeycloakIdJagFixture> => {
  const realm = uniqueName('xaa', true).toLowerCase();
  const realmIssuer = realmEntityId(realm);
  const clientId = uniqueName('xaa-agent', true);
  const mcpResource = `https://mcp-${realm}.example.com`;
  const strangerAudience = `https://stranger-${realm}.example.com/realms/${realm}`;
  const strayKey = createTrustedIssuerKeyMaterial();

  const issuer = await setupIssuer({ audience: realmIssuer, strangerAudience, clientId, resource: mcpResource });
  const redeemer = await setupRedeemer(realm, clientId, issuer);

  const craftAssertion = (overrides: Record<string, unknown>) => {
    const issuedAt = Math.floor(Date.now() / 1000);
    const claims = {
      iss: issuer.oidc.issuer,
      sub: issuer.linkedSubject,
      aud: realmIssuer,
      client_id: clientId,
      resource: mcpResource,
      scope: RESOURCE_SCOPE,
      jti: uniqueName('jti', true),
      iat: issuedAt,
      exp: issuedAt + 300,
      ...overrides,
    };
    return jwt.sign(claims, strayKey.privateKeyPem, { algorithm: 'RS256', header: { alg: 'RS256', typ: ID_JAG_JOSE_TYPE } });
  };

  return {
    realm,
    realmIssuer,
    clientId,
    keycloakUserId: redeemer.keycloakUserId,
    issuerDomain: issuer.domain,
    issuerOidc: issuer.oidc,
    strangerAudience,
    assertion: issuer.assertion,
    impostorAssertion: issuer.impostorAssertion,
    craftAssertion,
    redeem: redeemer.redeem,
    cleanUp: async () => {
      await issuer.cleanUp();
      await redeemer.cleanUp();
    },
  };
};
