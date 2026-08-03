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
import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
import {
  createDomain,
  DomainOidcConfig,
  patchDomain,
  safeDeleteDomain,
  startDomain,
  waitForDomainStart,
} from '@management-commands/domain-management-commands';
import { getDomainManagerUrl } from '@management-commands/service/utils';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { createIdp, deleteIdp, getAllIdps } from '@management-commands/idp-management-commands';
import { createScope } from '@management-commands/scope-management-commands';
import {
  extractXsrfTokenAndActionResponse,
  performFormPost,
  performGet,
  performPatch,
  performPost,
} from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { createDomainCertificate } from '../../oauth2/fixture/oauth2-cert-fixture';
import { uniqueName } from '@utils-commands/misc';
import { Fixture } from '../../../test-fixture';

/**
 * Domain A — the "require-DPoP off" domain for the binding suite. Carries a signing certificate so
 * access tokens are JWTs and `cnf.jkt` is observable. See
 * `.scratch/dpop-jest-tests/issues/00-proof-minter-and-fixture.md`.
 */
export interface DpopFixture extends Fixture {
  domain: Domain;
  oidc: DomainOidcConfig;
  users: { username: string; password: string }[];
  /** SERVICE, client_credentials. */
  ccApp: Application;
  /** WEB, authorization_code + refresh_token + password, openid + scope1, consent skipped. */
  webApp: Application;
  /** SERVICE, client_credentials, per-app dpopBoundAccessTokens = true (requires DPoP). */
  requireApp: Application;
}

const USER_PASSWORD = '#CoMpL3X-P@SsW0Rd';

export const setupDpopFixture = async (): Promise<DpopFixture> => {
  const accessToken = await requestAdminAccessToken();

  const domain = await createDomain(accessToken, uniqueName('dpop-optional', true), 'DPoP binding suite (require off)')
    .then((d) => makeMaster(d, accessToken))
    .then((d) => createDomainCertificate(d, accessToken).then(() => d));

  const idp = await createInlineIdp(domain, accessToken);
  await createScope(domain.id, accessToken, { key: 'scope1', name: 'scope1', description: 'scope1' });

  const idps = [{ identity: idp.id, priority: -1 }];
  const ccApp = await createApp(domain, accessToken, {
    type: 'SERVICE',
    grantTypes: ['client_credentials'],
    scopeSettings: [{ scope: 'scope1', defaultScope: true }],
  });
  const webApp = await createApp(domain, accessToken, {
    type: 'WEB',
    grantTypes: ['authorization_code', 'refresh_token', 'password'],
    scopeSettings: [
      { scope: 'scope1', defaultScope: true },
      { scope: 'openid', defaultScope: true },
    ],
    identityProviders: idps,
    skipConsent: true,
  });
  const requireApp = await createApp(domain, accessToken, {
    type: 'SERVICE',
    grantTypes: ['client_credentials'],
    scopeSettings: [{ scope: 'scope1', defaultScope: true }],
    dpopBoundAccessTokens: true,
  });

  await startDomain(domain.id, accessToken);
  const { oidcConfig } = await waitForDomainStart(domain);

  return {
    accessToken,
    domain,
    oidc: oidcConfig,
    users: idp.users,
    ccApp,
    webApp,
    requireApp,
    cleanUp: async () => {
      if (domain?.id && accessToken) {
        await safeDeleteDomain(domain.id, accessToken);
      }
    },
  };
};

/**
 * Domain B — the domain-wide "require-DPoP floor" domain. Every application must present a DPoP
 * proof at the token endpoint regardless of its own flag. Ticket 02.
 */
export interface DpopRequiredFixture extends Fixture {
  domain: Domain;
  oidc: DomainOidcConfig;
  /** SERVICE, client_credentials — no per-app DPoP flag; the domain floor does the work. */
  floorApp: Application;
}

export const setupDpopRequiredFixture = async (): Promise<DpopRequiredFixture> => {
  const accessToken = await requestAdminAccessToken();
  const domain = await createDomain(accessToken, uniqueName('dpop-required', true), 'DPoP binding suite (domain floor)')
    .then((d) => makeMaster(d, accessToken))
    .then((d) => createDomainCertificate(d, accessToken).then(() => d));

  await createScope(domain.id, accessToken, { key: 'scope1', name: 'scope1', description: 'scope1' });
  const floorApp = await createApp(domain, accessToken, {
    type: 'SERVICE',
    grantTypes: ['client_credentials'],
    scopeSettings: [{ scope: 'scope1', defaultScope: true }],
  });

  // Domain-wide DPoP floor. The generated SDK does not expose dpopSettings → raw PATCH.
  await patchDomainOidc(domain.id, accessToken, { dpopSettings: { requireDpopForAll: true } });

  await startDomain(domain.id, accessToken);
  const { oidcConfig } = await waitForDomainStart(domain);

  return {
    accessToken,
    domain,
    oidc: oidcConfig,
    floorApp,
    cleanUp: async () => {
      if (domain?.id && accessToken) {
        await safeDeleteDomain(domain.id, accessToken);
      }
    },
  };
};

/**
 * Domain C — restricts DPoP proof signing to a per-domain allowlist (default ES256 only). Ticket 03.
 */
export interface DpopAllowlistFixture extends Fixture {
  domain: Domain;
  oidc: DomainOidcConfig;
  /** SERVICE, client_credentials. */
  algApp: Application;
  algorithms: string[];
}

export const setupDpopAllowlistFixture = async (algorithms: string[] = ['ES256']): Promise<DpopAllowlistFixture> => {
  const accessToken = await requestAdminAccessToken();
  const domain = await createDomain(accessToken, uniqueName('dpop-alg', true), 'DPoP binding suite (algorithm allowlist)')
    .then((d) => makeMaster(d, accessToken))
    .then((d) => createDomainCertificate(d, accessToken).then(() => d));

  await createScope(domain.id, accessToken, { key: 'scope1', name: 'scope1', description: 'scope1' });
  const algApp = await createApp(domain, accessToken, {
    type: 'SERVICE',
    grantTypes: ['client_credentials'],
    scopeSettings: [{ scope: 'scope1', defaultScope: true }],
  });

  // Per-domain signing-algorithm allowlist. The generated SDK does not expose it → raw PATCH.
  await patchDomainOidc(domain.id, accessToken, { dpopSettings: { dpopSigningAlgorithms: algorithms } });

  await startDomain(domain.id, accessToken);
  const { oidcConfig } = await waitForDomainStart(domain);

  return {
    accessToken,
    domain,
    oidc: oidcConfig,
    algApp,
    algorithms,
    cleanUp: async () => {
      if (domain?.id && accessToken) {
        await safeDeleteDomain(domain.id, accessToken);
      }
    },
  };
};

// --- request helpers (kept here so specs contain only setup/teardown and test cases) ------------

const FORM = 'application/x-www-form-urlencoded';

/** The minimum a request helper needs from any of the DPoP fixtures. */
type WithOidc = { oidc: DomainOidcConfig };

function basicAuth(app: Application): string {
  return 'Basic ' + applicationBase64Token(app);
}

/** The `cnf.jkt` claim carried by a JWT access token. */
export function cnfJkt(accessToken: string): string | undefined {
  const payload = JSON.parse(Buffer.from(accessToken.split('.')[1], 'base64url').toString('utf8'));
  return payload.cnf?.jkt;
}

/** POST the token endpoint with a client_credentials grant, optionally presenting a DPoP proof. */
export function clientCredentialsToken(fixture: WithOidc, app: Application, proof?: string) {
  const headers: Record<string, string> = { 'Content-type': FORM, Authorization: basicAuth(app) };
  if (proof) {
    headers.DPoP = proof;
  }
  return performPost(fixture.oidc.token_endpoint, '', 'grant_type=client_credentials', headers);
}

/** POST the token endpoint with a password grant for the fixture's user (openid scope). */
export function passwordToken(fixture: DpopFixture, proof?: string) {
  const user = fixture.users[0];
  const headers: Record<string, string> = { 'Content-type': FORM, Authorization: basicAuth(fixture.webApp) };
  if (proof) {
    headers.DPoP = proof;
  }
  return performPost(
    fixture.oidc.token_endpoint,
    '',
    `grant_type=password&username=${user.username}&password=${encodeURIComponent(user.password)}&scope=openid`,
    headers,
  );
}

/** POST the token endpoint with a refresh_token grant (webApp), optionally presenting a DPoP proof. */
export function refreshAccessToken(fixture: DpopFixture, refreshToken: string, proof?: string) {
  const headers: Record<string, string> = { 'Content-type': FORM, Authorization: basicAuth(fixture.webApp) };
  if (proof) {
    headers.DPoP = proof;
  }
  return performPost(fixture.oidc.token_endpoint, '', `grant_type=refresh_token&refresh_token=${refreshToken}`, headers);
}

/**
 * Run the full authorize + login flow for the fixture's user (accepting consent if presented) and
 * return the authorization code, optionally committing a `dpop_jkt`. Mirrors executeAuthCodeFlow in
 * oauth-flows.jest.spec.ts; webApp skips consent, but the fallback keeps this robust if that changes.
 */
export async function getAuthorizationCode(fixture: DpopFixture, dpopJkt?: string): Promise<string> {
  const user = fixture.users[0];
  const clientId = fixture.webApp.settings.oauth.clientId;
  const jktParam = dpopJkt ? `&dpop_jkt=${dpopJkt}` : '';
  const params = `?response_type=code&client_id=${clientId}&redirect_uri=http://localhost:4000/&scope=openid${jktParam}`;

  const authResponse = await performGet(fixture.oidc.authorization_endpoint, params).expect(302);
  const { headers, token, action } = await extractXsrfTokenAndActionResponse(authResponse);
  const postLogin = await performFormPost(
    action,
    '',
    { 'X-XSRF-TOKEN': token, username: user.username, password: user.password, client_id: clientId },
    { Cookie: headers['set-cookie'], 'Content-type': FORM },
  ).expect(302);

  let redirect = await performGet(postLogin.headers['location'], '', { Cookie: postLogin.headers['set-cookie'] }).expect(302);

  // Accept the consent screen if one is shown.
  if (redirect.headers['location']?.includes('/oauth/consent')) {
    const consent = await extractXsrfTokenAndActionResponse(redirect);
    const postConsent = await performFormPost(
      consent.action,
      '',
      { 'X-XSRF-TOKEN': consent.token, 'scope.openid': true, user_oauth_approval: true },
      { Cookie: consent.headers['set-cookie'], 'Content-type': FORM },
    ).expect(302);
    redirect = await performGet(postConsent.headers['location'], '', { Cookie: postLogin.headers['set-cookie'] }).expect(302);
  }

  const codePattern = /code=([-_a-zA-Z0-9]+)&?/;
  expect(redirect.headers['location']).toMatch(codePattern);
  return redirect.headers['location'].match(codePattern)[1];
}

/** Redeem an authorization code (webApp), optionally presenting a DPoP proof. */
export function redeemAuthorizationCode(fixture: DpopFixture, code: string, proof?: string) {
  const headers: Record<string, string> = { Authorization: basicAuth(fixture.webApp) };
  if (proof) {
    headers.DPoP = proof;
  }
  return performPost(
    `${fixture.oidc.token_endpoint}?grant_type=authorization_code&code=${code}&redirect_uri=http://localhost:4000/`,
    '',
    null,
    headers,
  );
}

/**
 * GET /userinfo, optionally with a full Authorization header value and a DPoP proof. Omitting the
 * authorization sends an anonymous request.
 */
export function userInfo(fixture: WithOidc, authorization?: string, proof?: string) {
  const headers: Record<string, string> = {};
  if (authorization) {
    headers.Authorization = authorization;
  }
  if (proof) {
    headers.DPoP = proof;
  }
  return performGet(fixture.oidc.userinfo_endpoint, '', headers);
}

/** Deep-merge DPoP OIDC settings the generated SDK does not expose (raw PATCH). */
async function patchDomainOidc(domainId: string, accessToken: string, oidc: Record<string, unknown>): Promise<void> {
  await performPatch(
    getDomainManagerUrl(domainId),
    '',
    { oidc },
    { Authorization: `Bearer ${accessToken}`, 'Content-type': 'application/json' },
  ).expect(200);
}

async function makeMaster(domain: Domain, accessToken: string): Promise<Domain> {
  const patched = await patchDomain(domain.id, accessToken, {
    master: true,
    oidc: {
      clientRegistrationSettings: {
        allowLocalhostRedirectUri: true,
        allowHttpSchemeRedirectUri: true,
        allowWildCardRedirectUri: true,
        isDynamicClientRegistrationEnabled: false,
        isOpenDynamicClientRegistrationEnabled: false,
      },
    },
  });
  expect(patched.master).toBeTruthy();
  return patched;
}

async function createInlineIdp(domain: Domain, accessToken: string) {
  await deleteIdp(domain.id, accessToken, 'default-idp-' + domain.id);
  expect((await getAllIdps(domain.id, accessToken)).length).toEqual(0);

  const users = [{ firstname: 'Joe', lastname: 'Doe', username: uniqueName('joe.doe', true), password: USER_PASSWORD }];
  const idp = await createIdp(domain.id, accessToken, {
    external: false,
    type: 'inline-am-idp',
    domainWhitelist: [],
    configuration: JSON.stringify({ users }),
    name: 'inmemory',
  });
  expect(idp).toBeDefined();
  return { id: idp.id, users: users.map((u) => ({ username: u.username, password: u.password })) };
}

interface AppSpec {
  type: 'WEB' | 'SERVICE';
  grantTypes: string[];
  scopeSettings: any[];
  identityProviders?: any[];
  skipConsent?: boolean;
  dpopBoundAccessTokens?: boolean;
}

async function createApp(domain: Domain, accessToken: string, spec: AppSpec): Promise<Application> {
  const created = await createApplication(domain.id, accessToken, {
    name: uniqueName('dpop-client', true),
    type: spec.type,
    clientId: uniqueName('dpop-clientId', true),
    redirectUris: ['http://localhost:4000/'],
  });

  const updateBody: any = {
    settings: {
      oauth: {
        redirectUris: ['http://localhost:4000/'],
        grantTypes: spec.grantTypes,
        scopeSettings: spec.scopeSettings,
      },
      ...(spec.skipConsent ? { advanced: { skipConsent: true } } : {}),
    },
    identityProviders: spec.identityProviders,
  };
  const updated = await updateApplication(domain.id, accessToken, updateBody, created.id);
  // Restore the secret from the create response (the update response omits it).
  updated.settings.oauth.clientSecret = created.settings.oauth.clientSecret;

  if (spec.dpopBoundAccessTokens) {
    // The generated TS SDK does not expose `dpopBoundAccessTokens`; its typed serializer would drop
    // it. Set it with a raw merge-PATCH straight to the management API.
    await performPatch(
      `${getDomainManagerUrl(domain.id)}/applications/${created.id}`,
      '',
      { settings: { oauth: { dpopBoundAccessTokens: true } } },
      { Authorization: `Bearer ${accessToken}`, 'Content-type': 'application/json' },
    ).expect(200);
  }

  return updated;
}
