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

import request from 'supertest';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { getApplicationApi, getDomainApi, getDomainManagerUrl, getScopeApi } from '../api/commands/management/service/utils';
import { Application } from '../api/management/models/Application';
import { Domain } from '../api/management/models/Domain';
import { NewApplicationTypeEnum } from '../api/management/models/NewApplication';
import { DataPlaneTarget, getDataPlaneTargets, getInstanceLabel, normalizeForName } from './seed';
import { migrationSeed } from './version-range';

export const TOKEN_EXCHANGE_GRANT_TYPE = 'urn:ietf:params:oauth:grant-type:token-exchange';
export const JWT_TOKEN_TYPE = 'urn:ietf:params:oauth:token-type:jwt';
export const ACCESS_TOKEN_TYPE = 'urn:ietf:params:oauth:token-type:access_token';

/**
 * The scope the issuer domain puts on the JWT it mints, and the consumer domain scope the trusted
 * issuer maps it to. Deliberately different names, so the exchanged token can only carry the
 * consumer scope if the mapping survived. Both are registered on their own security domain:
 * `openid` cannot serve here, since a client credentials token has no user to describe.
 */
export const ISSUER_SCOPE = 'migration-te-external';
export const CONSUMER_SCOPE = 'migration-te';

/**
 * The trusted issuer's scope mappings. Asserted verbatim against what the Management API reports
 * back, so it is the payload that proves the migration carried more than just the issuer name.
 */
export const TRUSTED_ISSUER_SCOPE_MAPPINGS: Record<string, string> = { [ISSUER_SCOPE]: CONSUMER_SCOPE };

/**
 * Which consumer domain a trusted issuer was written through. Both exist on every channel so the
 * verification spec never has to guess which seed version produced the data:
 *
 * - `primary` is written with the API that is current for the seeded version — the inline
 *   `tokenExchangeSettings.trustedIssuers` list up to 4.12, the `trusted-domains` endpoint from
 *   4.13 on. On the alpha channel it is therefore the 4.12 shape the upgrade has to migrate.
 * - `legacy` is always written with the inline list, so a 4.13 seed proves the deprecated write
 *   path still works for as long as it is accepted.
 */
export const TOKEN_EXCHANGE_VARIANTS = ['primary', 'legacy'] as const;
export type TokenExchangeVariant = typeof TOKEN_EXCHANGE_VARIANTS[number];

/** How a trusted issuer is written into a security domain. */
export type TrustedIssuerApi = 'inline' | 'trusted-domain';

/**
 * Which field carries the key-retrieval (SSRF) policy in the seeded version. The limits moved out
 * of the SPIFFE block into `keyRetrievalSettings` in 4.13; a 4.12 Management API only knows the
 * former. A domain seeded through the legacy block keeps the same limits after the upgrade, since
 * `Domain.getKeyRetrievalSettings()` falls back to it and `DomainKeyRetrievalSettingsUpgrader`
 * relocates it.
 */
export type KeyRetrievalApi = 'legacy-spiffe' | 'key-retrieval-settings';

export interface TokenExchangeSeedOptions {
  trustedIssuerApi: TrustedIssuerApi;
  keyRetrievalApi: KeyRetrievalApi;
}

/**
 * The trusted-issuer token exchange data set, seeded from 4.12 on. 4.11 already had trusted issuers
 * but no way to widen the key-retrieval policy, so a 4.11-seeded issuer whose JWKS sits on a loopback
 * address could not be resolved after the upgrade.
 */
export const TOKEN_EXCHANGE_SEED = migrationSeed<TokenExchangeSeedOptions>({
  name: 'token exchange trusted issuer',
  variants: [
    // No trusted-domains API yet: both consumer domains carry their trusted issuer in the inline list,
    // and the key-retrieval limits live in the SPIFFE block. The shape the 4.13 upgrade has to migrate.
    { range: { from: '4.12', until: '4.13' }, options: { trustedIssuerApi: 'inline', keyRetrievalApi: 'legacy-spiffe' } },
    // A trusted issuer is a trusted domain of its own and the key-retrieval limits sit at the top level.
    // The `legacy` consumer domain still goes through the deprecated inline list.
    { range: { from: '4.13' }, options: { trustedIssuerApi: 'trusted-domain', keyRetrievalApi: 'key-retrieval-settings' } },
  ],
  seed: seedTokenExchangeData,
});

export function getTokenExchangeIssuerDomainName(label: string): string {
  return `migration-seeded-te-issuer-${normalizeForName(label)}`;
}

export function getTokenExchangeIssuerApplicationName(label: string): string {
  return `migration-seeded-te-issuer-app-${normalizeForName(label)}`;
}

export function getTokenExchangeDomainName(label: string, variant: TokenExchangeVariant): string {
  return `migration-seeded-te-${variant}-${normalizeForName(label)}`;
}

export function getTokenExchangeApplicationName(label: string, variant: TokenExchangeVariant): string {
  return `migration-seeded-te-${variant}-app-${normalizeForName(label)}`;
}

export function getTrustedDomainName(label: string, variant: TokenExchangeVariant): string {
  return `migration-seeded-te-${variant}-issuer-${normalizeForName(label)}`;
}

/**
 * Base URL the *gateway* uses to reach itself. The trusted issuer resolves its keys by fetching a
 * JWKS URL, and the issuer security domain is served by the same gateway as the consumer one — but
 * from inside the gateway that is its own listen port, not the port-forwarded one the test process
 * talks to. Defaults to the gateway's standard HTTP port.
 */
export function getInternalGatewayUrl(): string {
  return process.env.AM_GATEWAY_INTERNAL_URL || 'http://localhost:8092';
}

/**
 * Seed the trusted-issuer token exchange data set: one security domain that only mints the JWT to
 * exchange, and one consumer domain per {@link TOKEN_EXCHANGE_VARIANTS} trusting it.
 *
 * Duplicated onto every configured data plane. Each data plane gets its own issuer domain because
 * the trusted issuer is matched on the `iss` of the presented JWT, and that value carries the
 * gateway host the token was minted through — which differs per data plane.
 */
export async function seedTokenExchangeData(channelLabel: string, options: TokenExchangeSeedOptions): Promise<void> {
  const accessToken = await requestAdminAccessToken();
  const apis = {
    domainApi: getDomainApi(accessToken),
    applicationApi: getApplicationApi(accessToken),
    scopeApi: getScopeApi(accessToken),
  };

  for (const target of getDataPlaneTargets()) {
    await seedDataPlane(getInstanceLabel(channelLabel, target.id), target, apis, accessToken, options);
  }
}

type SeedApis = {
  domainApi: any;
  applicationApi: any;
  scopeApi: any;
};

async function seedDataPlane(
  label: string,
  target: DataPlaneTarget,
  apis: SeedApis,
  accessToken: string,
  options: TokenExchangeSeedOptions,
): Promise<void> {
  const issuer = await seedIssuerDomain(label, target, apis);

  for (const variant of TOKEN_EXCHANGE_VARIANTS) {
    await seedConsumerDomain(label, variant, target, apis, accessToken, issuer, {
      ...options,
      // The legacy variant is deliberately pinned to the deprecated write path on every version.
      trustedIssuerApi: variant === 'legacy' ? 'inline' : options.trustedIssuerApi,
    });
  }
}

type SeededIssuer = {
  /** The `iss` the minted JWTs carry, read from the issuer domain's own discovery document. */
  issuer: string;
  /** The issuer domain's JWKS endpoint, rewritten to the base URL the gateway reaches itself on. */
  jwksUri: string;
};

async function seedIssuerDomain(label: string, target: DataPlaneTarget, apis: SeedApis): Promise<SeededIssuer> {
  const { domainApi, applicationApi, scopeApi } = apis;
  const organizationId = process.env.AM_DEF_ORG_ID;
  const environmentId = process.env.AM_DEF_ENV_ID;

  const domain = await getOrCreateDomain(
    getTokenExchangeIssuerDomainName(label),
    target.id,
    domainApi,
    `Migration token exchange issuer ${label}`,
  );
  await domainApi.patchDomain({ organizationId, environmentId, domain: domain.id, patchDomain: { enabled: true } });

  await getOrCreateScope(scopeApi, domain.id, ISSUER_SCOPE);
  const application = await getOrCreateServiceApplication(applicationApi, domain.id, getTokenExchangeIssuerApplicationName(label));
  await applicationApi.updateApplication({
    organizationId,
    environmentId,
    domain: domain.id,
    application: application.id,
    patchApplication: {
      settings: {
        oauth: {
          grantTypes: ['client_credentials'],
          scopeSettings: [{ scope: ISSUER_SCOPE, defaultScope: true }],
        },
      },
    },
  });

  const openIdConfiguration = await waitForOpenIdConfiguration(target.gatewayUrl, domain.hrid);
  return {
    issuer: openIdConfiguration.issuer,
    jwksUri: rebaseOnInternalGateway(openIdConfiguration.jwks_uri),
  };
}

async function seedConsumerDomain(
  label: string,
  variant: TokenExchangeVariant,
  target: DataPlaneTarget,
  apis: SeedApis,
  accessToken: string,
  issuer: SeededIssuer,
  options: TokenExchangeSeedOptions,
): Promise<void> {
  const { domainApi, applicationApi, scopeApi } = apis;
  const organizationId = process.env.AM_DEF_ORG_ID;
  const environmentId = process.env.AM_DEF_ENV_ID;

  const domain = await getOrCreateDomain(
    getTokenExchangeDomainName(label, variant),
    target.id,
    domainApi,
    `Migration token exchange consumer ${variant} ${label}`,
  );
  await domainApi.patchDomain({ organizationId, environmentId, domain: domain.id, patchDomain: { enabled: true } });

  // The JWKS lives on a loopback address behind plain HTTP, which the key-retrieval policy refuses
  // by default — both when the trusted issuer is stored and when its keys are fetched. Relax it
  // before writing the issuer, or the write itself is rejected.
  await allowLoopbackKeyRetrieval(domain.id, accessToken, options.keyRetrievalApi);

  await getOrCreateScope(scopeApi, domain.id, CONSUMER_SCOPE);
  const application = await getOrCreateServiceApplication(applicationApi, domain.id, getTokenExchangeApplicationName(label, variant));
  await applicationApi.updateApplication({
    organizationId,
    environmentId,
    domain: domain.id,
    application: application.id,
    patchApplication: {
      settings: {
        oauth: {
          grantTypes: ['client_credentials', TOKEN_EXCHANGE_GRANT_TYPE],
          scopeSettings: [{ scope: CONSUMER_SCOPE, defaultScope: true }],
        },
      },
    },
  });

  if (options.trustedIssuerApi === 'inline') {
    // One patch carries both the settings and the issuer: a patch replaces the whole token
    // exchange block, and from 4.13 an absent inline list means "leave the trusted domains alone".
    await writeInlineTrustedIssuer(domain.id, accessToken, issuer);
  } else {
    await enableTokenExchange(domain.id, accessToken);
    await writeTrustedDomain(domain.id, accessToken, getTrustedDomainName(label, variant), issuer);
  }

  // Leave the domain deployed rather than mid-redeploy, so the verification stage that follows
  // does not race the gateway picking the last write up.
  await waitForOpenIdConfiguration(target.gatewayUrl, domain.hrid);
}

/**
 * The deprecated write path: the trusted issuer travels inside the security domain's token
 * exchange settings. Up to 4.12 that list is what the gateway reads; from 4.13 it is projected
 * onto trusted-domain entities on the way in and back out on the way out.
 */
async function writeInlineTrustedIssuer(domainId: string, accessToken: string, issuer: SeededIssuer): Promise<void> {
  await patchDomainRaw(domainId, accessToken, {
    tokenExchangeSettings: {
      ...tokenExchangeSettingsBody(),
      trustedIssuers: [
        {
          issuer: issuer.issuer,
          keyResolutionMethod: 'JWKS_URL',
          jwksUri: issuer.jwksUri,
          scopeMappings: TRUSTED_ISSUER_SCOPE_MAPPINGS,
        },
      ],
    },
  });
}

/**
 * The 4.13 write path: the trusted issuer is an entity of its own under the security domain.
 * Re-seeding amends the entity rather than skipping it, so a repeated `seed-alpha` / `seed-beta`
 * stage converges on the current payload like every other get-or-create in the seed.
 */
async function writeTrustedDomain(domainId: string, accessToken: string, name: string, issuer: SeededIssuer): Promise<void> {
  const collectionUrl = `${getDomainManagerUrl(domainId)}/trusted-domains`;
  const existing = (await listTrustedDomains(domainId, accessToken)).find(
    (trustedDomain: any) => trustedDomain.domainIdentifier === issuer.issuer,
  );
  const url = existing ? `${collectionUrl}/${existing.id}` : collectionUrl;
  const method = existing ? 'PUT' : 'POST';

  const response = await fetch(url, {
    method,
    headers: { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      name: existing ? existing.name : name,
      domainIdentifier: issuer.issuer,
      keyMaterial: { source: 'JWKS_URL', jwksUrl: issuer.jwksUri },
      tokenExchange: { enabled: true, scopeMappings: TRUSTED_ISSUER_SCOPE_MAPPINGS },
    }),
  });
  if (!response.ok) {
    throw new Error(`${method} ${url} failed: ${response.status} ${await response.text()}`);
  }
}

/**
 * The trusted domains registered on a security domain, straight over HTTP: the generated SDK model
 * predates `domainIdentifier` and would drop it.
 */
export async function listTrustedDomains(domainId: string, accessToken: string): Promise<any[]> {
  const url = `${getDomainManagerUrl(domainId)}/trusted-domains`;
  const response = await fetch(url, { headers: { Authorization: `Bearer ${accessToken}` } });
  if (!response.ok) {
    throw new Error(`GET ${url} failed: ${response.status} ${await response.text()}`);
  }
  return response.json();
}

function tokenExchangeSettingsBody(): Record<string, unknown> {
  return {
    enabled: true,
    allowImpersonation: true,
    allowDelegation: false,
    allowedSubjectTokenTypes: [ACCESS_TOKEN_TYPE, JWT_TOKEN_TYPE],
    allowedRequestedTokenTypes: [ACCESS_TOKEN_TYPE],
  };
}

async function enableTokenExchange(domainId: string, accessToken: string): Promise<void> {
  await patchDomainRaw(domainId, accessToken, { tokenExchangeSettings: tokenExchangeSettingsBody() });
}

async function allowLoopbackKeyRetrieval(domainId: string, accessToken: string, api: KeyRetrievalApi): Promise<void> {
  const limits = { allowUnsecuredHttpUri: true, allowPrivateIpAddress: true };
  const body = api === 'legacy-spiffe' ? { oidc: { workloadIdentitySettings: limits } } : { keyRetrievalSettings: limits };
  await patchDomainRaw(domainId, accessToken, body);
}

/**
 * Patch a security domain over raw HTTP. The generated SDK models lag the bodies used here
 * (`trustedIssuers`, `keyRetrievalSettings`) and would silently drop the very fields being seeded;
 * going through the SDK for the 4.12 shapes would also mean carrying a second, older SDK.
 */
async function patchDomainRaw(domainId: string, accessToken: string, body: Record<string, unknown>): Promise<void> {
  await request(getDomainManagerUrl(domainId))
    .patch('')
    .set('Authorization', `Bearer ${accessToken}`)
    .set('Content-Type', 'application/json')
    .send(body)
    .expect(200);
}

async function getOrCreateDomain(name: string, dataPlaneId: string, domainApi: any, description: string): Promise<Domain> {
  const organizationId = process.env.AM_DEF_ORG_ID;
  const environmentId = process.env.AM_DEF_ENV_ID;
  const existingDomains = await domainApi.listDomains({ organizationId, environmentId, q: name });
  const existingDomain = existingDomains.data?.find((candidate) => candidate.name === name);
  if (existingDomain) {
    return existingDomain;
  }

  return domainApi.createDomain({
    organizationId,
    environmentId,
    newDomain: { name, description, dataPlaneId },
  });
}

async function getOrCreateServiceApplication(applicationApi: any, domainId: string, name: string): Promise<Application> {
  const organizationId = process.env.AM_DEF_ORG_ID;
  const environmentId = process.env.AM_DEF_ENV_ID;
  const existingApplications = await applicationApi.listApplications({ organizationId, environmentId, domain: domainId, q: name });
  const existingApplication = existingApplications.data?.find((candidate) => candidate.name === name);
  if (existingApplication) {
    return existingApplication;
  }

  return applicationApi.createApplication({
    organizationId,
    environmentId,
    domain: domainId,
    newApplication: {
      name,
      type: NewApplicationTypeEnum.Service,
      clientId: name,
      clientSecret: name,
    },
  });
}

async function getOrCreateScope(scopeApi: any, domainId: string, key: string): Promise<void> {
  const organizationId = process.env.AM_DEF_ORG_ID;
  const environmentId = process.env.AM_DEF_ENV_ID;
  const existingScopes = await scopeApi.listScopes({ organizationId, environmentId, domain: domainId, q: key });
  if (existingScopes.data?.some((candidate) => candidate.key === key)) {
    return;
  }

  await scopeApi.createScope({
    organizationId,
    environmentId,
    domain: domainId,
    newScope: { key, name: key, description: `Migration token exchange scope ${key}` },
  });
}

const OPENID_CONFIGURATION_TIMEOUT_MS = Number(process.env.AM_DOMAIN_SYNC_TIMEOUT_MS) || 120000;
const OPENID_CONFIGURATION_INTERVAL_MS = 500;

/**
 * Poll the gateway until it serves the domain's discovery document. The seed needs the live
 * `issuer` and `jwks_uri` rather than URLs assembled by hand: the `iss` a token carries depends on
 * the gateway host it was minted through, and must match the trusted issuer exactly.
 */
async function waitForOpenIdConfiguration(gatewayUrl: string, domainHrid: string): Promise<{ issuer: string; jwks_uri: string }> {
  const start = Date.now();
  let lastStatus: number | undefined;
  let lastError: unknown;

  while (Date.now() - start < OPENID_CONFIGURATION_TIMEOUT_MS) {
    try {
      const response = await request(gatewayUrl).get(`/${domainHrid}/oidc/.well-known/openid-configuration`).send();
      lastStatus = response.status;
      if (response.status === 200 && response.body?.issuer && response.body?.jwks_uri) {
        return response.body;
      }
    } catch (error) {
      lastError = error;
    }
    await new Promise((resolve) => setTimeout(resolve, OPENID_CONFIGURATION_INTERVAL_MS));
  }

  throw new Error(
    `Timed out waiting for the OIDC configuration of domain "${domainHrid}" on ${gatewayUrl}` +
      (lastStatus ? `. Last status: ${lastStatus}` : '') +
      (lastError instanceof Error ? `. Last error: ${lastError.message}` : ''),
  );
}

/** Keep the discovered JWKS path, swap the host the test process uses for the one the gateway uses. */
function rebaseOnInternalGateway(jwksUri: string): string {
  const internal = new URL(getInternalGatewayUrl());
  const discovered = new URL(jwksUri);
  discovered.protocol = internal.protocol;
  discovered.host = internal.host;
  return discovered.toString();
}
