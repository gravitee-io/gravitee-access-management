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
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import {
  allowHttpLocalhostRedirects,
  createDomain,
  patchDomain,
  safeDeleteDomain,
  startDomain,
  waitForDomainStart,
} from '@management-commands/domain-management-commands';
import { createApplication, patchApplication, updateApplication } from '@management-commands/application-management-commands';
import { createIdp, deleteIdp, getAllIdps } from '@management-commands/idp-management-commands';
import { waitForNextSync } from '@gateway-commands/monitoring-commands';
import { performGet } from '@gateway-commands/oauth-oidc-commands';
import { login } from '@gateway-commands/login-commands';
import { BasicResponse, followRedirectTag, uniqueName } from '@utils-commands/misc';
import { Application } from '@management-models/Application';
import { Domain } from '@management-models/Domain';
import { IdentityProvider } from '@management-models/IdentityProvider';
import { Fixture } from '../../../test-fixture';
import cheerio from 'cheerio';

export const CIMD_SOCIAL_REDIRECT_URI = 'https://client.example.com/callback';

/** WireMock stub already used by the other CIMD suites, it declares CIMD_SOCIAL_REDIRECT_URI. */
export const CIMD_SOCIAL_CLIENT_ID = 'http://wiremock:8080/cimd/ENABLED_BASE/valid-none';

/** Registered on the template application only, so it is reachable only once the CIMD client is resolved. */
export const CIMD_POST_LOGOUT_REDIRECT_URI = 'https://client.example.com/logged-out';

/** Registered at domain level only, so it must be rejected once the CIMD client is resolved. */
export const DOMAIN_POST_LOGOUT_REDIRECT_URI = 'https://somewhere/after/domain-logout';

const UPSTREAM_USER = {
  username: 'cimd-social-user',
  password: '#CoMpL3X-P@SsW0Rd',
  firstname: 'Cimd',
  lastname: 'Social',
};

/** Outcome of a social login: the SP redirect plus the CIMD domain cookies needed to keep using the session. */
export type CimdSocialLoginResult = {
  location: string;
  cookies: string[];
};

export interface CimdSocialLoginFixture extends Fixture {
  accessToken: string;
  domain: Domain;
  upstreamDomain: Domain;
  openIdConfiguration: any;
  templateApplication: Application;
  externalIdp: IdentityProvider;
  /** Runs authorize -> external IdP login -> /login/callback and returns the final SP redirect and the session. */
  loginThroughExternalIdp: (clientId: string, redirectUri?: string) => Promise<CimdSocialLoginResult>;
  /** RP-initiated logout relying on the session only (no client_id parameter). */
  logout: (session: CimdSocialLoginResult, postLogoutRedirectUri?: string) => Promise<any>;
}

/**
 * AM-7658 — two domains are required to exercise the social login callback:
 *  - an upstream domain acting as the external OIDC provider (inline IdP + web application),
 *  - the CIMD domain, whose template application is bound to an `oauth2-generic-am-idp`
 *    pointing at the upstream domain.
 * The upstream application redirects back to `{cimdDomain}/login/callback`, which is where AM has
 * to re-resolve the CIMD client from its metadata document to resume the authorization request.
 */
export const setupCimdSocialLoginFixture = async (): Promise<CimdSocialLoginFixture> => {
  let domain: Domain | null = null;
  let upstreamDomain: Domain | null = null;
  let accessToken: string | null = null;

  try {
    accessToken = await requestAdminAccessToken();
    expect(accessToken).toBeDefined();

    // The CIMD domain is created first, its hrid is part of the upstream application redirect_uri.
    domain = await createDomain(accessToken, uniqueName('cimd-social', true), 'CIMD social login (AM-7658)');
    expect(domain.id).toBeDefined();

    upstreamDomain = await createDomain(accessToken, uniqueName('cimd-social-upstream', true), 'CIMD social login upstream IdP').then(
      (created) => allowHttpLocalhostRedirects(created, accessToken),
    );
    expect(upstreamDomain.id).toBeDefined();

    const upstreamIdp = await replaceDefaultIdpWithInline(upstreamDomain, accessToken);
    const upstreamApplication = await createOAuthApplication(
      upstreamDomain.id,
      accessToken,
      'cimd-social-upstream-app',
      `${process.env.AM_GATEWAY_URL}/${domain.hrid}/login/callback`,
      upstreamIdp.id,
    );

    const externalIdp = await createExternalOidcIdp(domain, upstreamDomain, accessToken, upstreamApplication);

    // The template is the only application of the CIMD domain, every CIMD client is synthesized from it.
    const templateApplication = await createOAuthApplication(
      domain.id,
      accessToken,
      'cimd-social-template',
      CIMD_SOCIAL_REDIRECT_URI,
      externalIdp.id,
      [CIMD_POST_LOGOUT_REDIRECT_URI],
    );
    await patchApplication(domain.id, accessToken, { template: true }, templateApplication.id);

    await patchDomain(domain.id, accessToken, {
      oidc: {
        // AM-7658: the two lists differ on purpose. A logout that lands on the application list proves
        // the CIMD client was re-synthesized from the session; falling back to the domain list means it was not.
        postLogoutRedirectUris: [DOMAIN_POST_LOGOUT_REDIRECT_URI],
        cimdSettings: {
          enabled: true,
          allowUnsecuredHttpUri: true,
          allowPrivateIpAddress: true,
          allowedDomains: [],
          fetchTimeoutMs: 1500,
          maxResponseSizeKb: 10,
          cacheTtlSeconds: 3600,
          cacheMaxEntries: 500,
          templateId: templateApplication.id,
        },
      },
    });

    await startDomain(upstreamDomain.id, accessToken);
    await startDomain(domain.id, accessToken);
    const startedUpstreamDomain = await waitForDomainStart(upstreamDomain);
    const startedDomain = await waitForDomainStart(domain);
    await Promise.all([waitForNextSync(upstreamDomain.id), waitForNextSync(domain.id)]);

    /** Follows the "sign in with" link of the AM login page towards the upstream domain. */
    const navigateToExternalIdpLogin = async (loginPageRedirect: BasicResponse) => {
      const headers = loginPageRedirect.headers['set-cookie'] ? { Cookie: loginPageRedirect.headers['set-cookie'] } : {};
      const loginPage = await performGet(loginPageRedirect.headers['location'], '', headers).expect(200);

      const externalIdpUrl = cheerio.load(loginPage.text)('.btn-oauth2-generic-am-idp').attr('href');
      expect(externalIdpUrl).toBeDefined();
      return performGet(externalIdpUrl).expect(302);
    };

    const loginThroughExternalIdp = async (clientId: string, redirectUri = CIMD_SOCIAL_REDIRECT_URI): Promise<CimdSocialLoginResult> => {
      const params = new URLSearchParams({
        response_type: 'code',
        client_id: clientId,
        redirect_uri: redirectUri,
        scope: 'openid',
        state: 'cimd-social-state',
      });

      const authorizeResponse = await performGet(`${startedDomain.oidcConfig.authorization_endpoint}?${params.toString()}`).expect(302);

      const upstreamLoginPage = await navigateToExternalIdpLogin(authorizeResponse);
      const upstreamPostLogin = await login(
        upstreamLoginPage,
        UPSTREAM_USER.username,
        upstreamApplication.settings.oauth.clientId,
        UPSTREAM_USER.password,
      );

      // upstream /oauth/authorize -> CIMD domain /login/callback -> resumed /oauth/authorize -> SP redirect_uri
      return followUntilLeavingTheGateway(upstreamPostLogin, redirectUri, startedDomain.domain.hrid);
    };

    const logout = async (session: CimdSocialLoginResult, postLogoutRedirectUri?: string) => {
      // No client_id is sent: the client can only come from the session, which is the AM-7658 code path.
      const query = postLogoutRedirectUri ? `?post_logout_redirect_uri=${encodeURIComponent(postLogoutRedirectUri)}` : '';
      return performGet(startedDomain.oidcConfig.end_session_endpoint, query, { Cookie: session.cookies }).expect(302);
    };

    return {
      accessToken,
      domain: startedDomain.domain,
      upstreamDomain: startedUpstreamDomain.domain,
      openIdConfiguration: startedDomain.oidcConfig,
      templateApplication,
      externalIdp,
      loginThroughExternalIdp,
      logout,
      cleanUp: async () => {
        await Promise.all([safeDeleteDomain(domain?.id, accessToken), safeDeleteDomain(upstreamDomain?.id, accessToken)]);
      },
    };
  } catch (error) {
    if (accessToken) {
      await Promise.all([
        domain?.id ? safeDeleteDomain(domain.id, accessToken) : Promise.resolve(),
        upstreamDomain?.id ? safeDeleteDomain(upstreamDomain.id, accessToken) : Promise.resolve(),
      ]);
    }
    throw error;
  }
};

/**
 * Follows the redirect chain until it leaves AM, either towards the service provider redirect_uri
 * or onto an error redirect. Hop count is not asserted, only the outcome: the number of internal
 * hops depends on the consent and session handling of both domains.
 *
 * Cookies emitted by the CIMD domain are accumulated along the way so the caller can keep using the
 * session (ie. to log out). Upstream domain cookies are skipped, both domains are served by the same
 * host and share the session cookie name.
 */
async function followUntilLeavingTheGateway(
  response: BasicResponse,
  redirectUri: string,
  domainHrid: string,
  maxHops = 8,
): Promise<CimdSocialLoginResult> {
  const jar = new Map<string, string>();
  let current = response;

  for (let hop = 0; hop < maxHops; hop++) {
    collectDomainCookies(jar, current, domainHrid);

    const location = current.headers['location'];
    if (location && (location.startsWith(redirectUri) || location.includes('error='))) {
      return { location, cookies: [...jar].map(([name, value]) => `${name}=${value}`) };
    }
    current = await followRedirectTag(`cimd-social-hop-${hop}`)(current);
  }
  throw new Error(`The social login flow did not reach ${redirectUri} within ${maxHops} redirects`);
}

function collectDomainCookies(jar: Map<string, string>, response: BasicResponse, domainHrid: string) {
  if (!String(response.request?.url ?? '').includes(`/${domainHrid}/`)) {
    return;
  }
  const setCookie = (response.headers['set-cookie'] ?? []) as unknown as string[];
  for (const raw of setCookie) {
    const [pair] = raw.split(';');
    const separator = pair.indexOf('=');
    if (separator > 0) {
      jar.set(pair.slice(0, separator).trim(), pair.slice(separator + 1));
    }
  }
}

async function createOAuthApplication(
  domainId: string,
  accessToken: string,
  namePrefix: string,
  redirectUri: string,
  identityProviderId: string,
  postLogoutRedirectUris: string[] = [],
): Promise<Application> {
  const created = await createApplication(domainId, accessToken, {
    name: uniqueName(namePrefix, true),
    type: 'WEB',
    redirectUris: [redirectUri],
  });

  const updated = await updateApplication(
    domainId,
    accessToken,
    {
      settings: {
        oauth: {
          redirectUris: [redirectUri],
          grantTypes: ['authorization_code'],
          responseTypes: ['code'],
          scopeSettings: [{ scope: 'openid', defaultScope: true }],
          postLogoutRedirectUris,
        },
        advanced: { skipConsent: true },
      },
      identityProviders: new Set([{ identity: identityProviderId, priority: 0 }]),
    },
    created.id,
  );

  // updateApplication strips the autogenerated client_secret, restore it for the IdP configuration.
  updated.settings.oauth.clientSecret = created.settings.oauth.clientSecret;
  return updated;
}

async function replaceDefaultIdpWithInline(domain: Domain, accessToken: string): Promise<IdentityProvider> {
  await deleteIdp(domain.id, accessToken, `default-idp-${domain.id}`);
  expect(await getAllIdps(domain.id, accessToken)).toHaveLength(0);

  const idp = await createIdp(domain.id, accessToken, {
    external: false,
    type: 'inline-am-idp',
    domainWhitelist: [],
    configuration: JSON.stringify({ users: [UPSTREAM_USER] }),
    name: 'inmemory',
  });
  expect(idp).toBeDefined();
  return idp;
}

async function createExternalOidcIdp(
  domain: Domain,
  upstreamDomain: Domain,
  accessToken: string,
  upstreamApplication: Application,
): Promise<IdentityProvider> {
  const idp = await createIdp(domain.id, accessToken, {
    name: 'cimd-social-oidc-provider',
    type: 'oauth2-generic-am-idp',
    external: true,
    configuration: JSON.stringify({
      clientId: upstreamApplication.settings.oauth.clientId,
      clientSecret: upstreamApplication.settings.oauth.clientSecret,
      clientAuthenticationMethod: 'client_secret_basic',
      wellKnownUri: `${process.env.AM_GATEWAY_URL}/${upstreamDomain.hrid}/oidc/.well-known/openid-configuration`,
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
  expect(idp).toBeDefined();
  return idp;
}
