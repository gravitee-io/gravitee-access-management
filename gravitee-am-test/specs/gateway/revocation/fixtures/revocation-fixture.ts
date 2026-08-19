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
import {
  createDomain,
  patchDomain,
  safeDeleteDomain,
  startDomain,
  waitForOidcReady,
} from '@management-commands/domain-management-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { buildCreateAndTestUser } from '@management-commands/user-management-commands';
import { getDomainManagerUrl } from '@management-commands/service/utils';
import { createTestApp } from '@utils-commands/application-commands';
import {
  extractXsrfTokenAndActionResponse,
  introspectToken as introspectOidcToken,
  performFormPost,
  performGet,
  performPatch,
  performPost,
} from '@gateway-commands/oauth-oidc-commands';
import { retryUntil, withRetry } from '@utils-commands/retry';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { uniqueName } from '@utils-commands/misc';
import { decodeJwt } from '@utils-commands/jwt';
import { JWT_FORMAT } from '@specs-utils/jwt-format';
import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
// Type-only: jest's moduleNameMapper has no @management-models entry, so a value import from here
// resolves under tsc but blows up at runtime. Keep every model import in a type position.
import type { NewApplication } from '@management-models/NewApplication';
import { IdentityProvider } from '@management-models/IdentityProvider';
import { User } from '@management-models/User';
import request from 'supertest';

export interface OidcConfiguration {
  issuer: string;
  authorization_endpoint: string;
  token_endpoint: string;
  introspection_endpoint: string;
  userinfo_endpoint: string;
  revocation_endpoint: string;
  end_session_endpoint: string;
  jwks_uri: string;
}

export interface SubjectTokens {
  accessToken: string;
  refreshToken?: string;
  idToken?: string;
  expiresIn: number;
}

/**
 * An application plus everything a test needs to drive it: its Basic auth header
 * and a helper that runs a full authorization_code flow against it.
 */
export interface RevocationClient {
  application: Application;
  basicAuth: string;
  obtainAuthorizationCodeTokens: (scope?: string) => Promise<SubjectTokens>;
}

export interface RevocationFixtureOptions {
  /** Domain name prefix; a random suffix is always appended. */
  domainNamePrefix?: string;
  /** Enable domain-level token exchange and add the grant type to each client. Defaults to true. */
  enableTokenExchange?: boolean;
  /**
   * Create a second, independent application in the same domain. Used to assert that
   * revocation targeting one client leaves other clients' tokens alone.
   */
  withSecondaryApplication?: boolean;
  /**
   * Enable SCIM on the domain and provision a service client, so tests can drive SCIM user
   * updates. SCIM disable takes a different revocation route to everything else in this folder:
   * ProvisioningUserServiceImpl calls RevokeTokenGatewayService#deleteByUser directly rather
   * than emitting a REVOKE_TOKEN event.
   */
  withScim?: boolean;
}

export interface RevocationFixture {
  domain: Domain;
  application: Application;
  /** Present only when the fixture was built with `withSecondaryApplication: true`. */
  secondaryClient?: RevocationClient;
  user: User;
  defaultIdp: IdentityProvider;
  oidc: OidcConfiguration;
  basicAuth: string;
  accessToken: string;
  cleanup: () => Promise<void>;
  obtainAuthorizationCodeTokens: (scope?: string) => Promise<SubjectTokens>;
  introspectToken: (token: string, basicAuth?: string) => Promise<any>;
  exchangeToken: (subjectToken: string, subjectTokenType: 'access_token' | 'refresh_token') => Promise<string>;
  /** GET the userinfo endpoint with a bearer token. Returns the raw response so tests can assert on status. */
  getUserInfo: (token: string) => Promise<any>;
  /** POST the RFC 7009 revocation endpoint. Returns the raw response so tests can assert on status and body. */
  revokeToken: (token: string, options?: { basicAuth?: string; tokenTypeHint?: string }) => Promise<any>;
  /**
   * Create a further application in the same domain, on demand. Lets a test that mutates an
   * application (disable, delete) own a client nobody else depends on, keeping it independent
   * of the other tests in the file.
   */
  createAdditionalClient: (namePrefix?: string, options?: { exactName?: boolean }) => Promise<RevocationClient>;
  /** Create a further user in the domain. `index` must be unique within a test file. */
  createAdditionalUser: (index: number) => Promise<User>;
  /** Run the authorization_code flow as an arbitrary user, defaulting to the fixture's primary client. */
  obtainAuthorizationCodeTokensAs: (asUser: User, client?: RevocationClient) => Promise<SubjectTokens>;
  /**
   * Poll until the gateway rejects this token at the userinfo endpoint. Use instead of a fixed
   * wait when asserting a token has been revoked — it tolerates slow propagation and still fails
   * honestly, because a token that was never revoked keeps returning 200 until the timeout.
   */
  waitUntilTokenRejected: (token: string) => Promise<void>;
  /** As `waitUntilTokenRejected`, but polls the introspection endpoint for `active: false`. */
  waitUntilTokenInactive: (token: string) => Promise<void>;
  /** Present only when the fixture was built with `withScim: true`. */
  scim?: {
    endpoint: string;
    accessToken: string;
    /** PATCH a user's `active` flag. Returns the raw response so tests can assert on it. */
    setUserActive: (userId: string, active: boolean) => Promise<any>;
  };
}

/**
 * `handlers.oauth2.introspect.offlineVerificationTimerSeconds`. Within this many seconds of a
 * token being issued the gateway trusts the JWT and never looks it up, so a revoked token still
 * works. The stack these tests run against disables it
 * (`GRAVITEE_HANDLERS_OAUTH2_INTROSPECT_OFFLINEVERIFICATIONTIMERSECONDS=0` in
 * docker/local-stack/dev/docker-compose.yml, which the CI compose layers), hence the default of 0
 * here. Override to match if you point the suite at a stack that leaves the product default of 10.
 */
const OFFLINE_VERIFICATION_SECONDS = Number(process.env.AM_OFFLINE_VERIFICATION_SECONDS ?? 0);

/** Headroom for the revoke event to reach the gateway and the delete to land. */
const REVOKE_PROPAGATION_SETTLE_MS = 2000;

/** How long to keep polling before deciding a token was never revoked. */
const REVOCATION_POLL_TIMEOUT_MS = 20000;

/**
 * Wait until the gateway will consult the token store for this token rather than trusting the JWT.
 *
 * Only needed before asserting a token is STILL VALID. Inside the offline-verification window such
 * an assertion is meaningless — the JWT is trusted, so it passes even if the token has been revoked.
 * To assert a token HAS been revoked, poll with `waitUntilTokenRejected` / `waitUntilTokenInactive`
 * instead: polling for the revoked state cannot produce a false pass, because a token that was
 * never revoked simply stays valid until the timeout.
 */
export const waitPastOfflineVerification = async (token: string): Promise<void> => {
  const { iat } = decodeJwt(token) as { iat: number };
  const remainingWindowMs = Math.max(0, (iat + OFFLINE_VERIFICATION_SECONDS) * 1000 - Date.now());
  await new Promise((resolve) => setTimeout(resolve, remainingWindowMs + REVOKE_PROPAGATION_SETTLE_MS));
};

const REVOCATION_TEST = {
  // Kept as the original consent-revocation prefix so existing callers that pass no
  // options keep producing the same domain names. New specs pass their own prefix.
  DOMAIN_NAME_PREFIX: 'revoke-consents',
  DOMAIN_DESCRIPTION: 'Token revocation domain',
  CLIENT_NAME: 'revoke-consents-client',
  SECONDARY_CLIENT_NAME: 'revocation-secondary-client',
  USER_PASSWORD: 'SomeP@ssw0rd',
  REDIRECT_URI: 'https://gravitee.io/callback',
  DEFAULT_SCOPE: 'openid%20profile%20offline_access',
  DEFAULT_SCOPES: [
    { scope: 'openid', defaultScope: true },
    { scope: 'profile', defaultScope: true },
    { scope: 'offline_access', defaultScope: false },
  ],
  BASE_GRANT_TYPES: ['authorization_code', 'refresh_token'],
  TOKEN_EXCHANGE_GRANT_TYPE: 'urn:ietf:params:oauth:grant-type:token-exchange',
  ALLOWED_SUBJECT_TOKEN_TYPES: ['urn:ietf:params:oauth:token-type:access_token', 'urn:ietf:params:oauth:token-type:refresh_token'],
  ALLOWED_REQUESTED_TOKEN_TYPES: ['urn:ietf:params:oauth:token-type:access_token'],
};

const enableTokenExchangeOnDomain = async (domainId: string, token: string): Promise<void> => {
  await request(getDomainManagerUrl(domainId))
    .patch('')
    .set('Authorization', `Bearer ${token}`)
    .set('Content-Type', 'application/json')
    .send({
      tokenExchangeSettings: {
        enabled: true,
        allowedSubjectTokenTypes: REVOCATION_TEST.ALLOWED_SUBJECT_TOKEN_TYPES,
        allowedRequestedTokenTypes: REVOCATION_TEST.ALLOWED_REQUESTED_TOKEN_TYPES,
        allowImpersonation: true,
        allowDelegation: false,
      },
    })
    .expect(200);
};

const createRevocationApp = async (
  name: string,
  domain: Domain,
  accessToken: string,
  defaultIdp: IdentityProvider,
  withTokenExchange: boolean,
  exactName: boolean = false,
): Promise<Application> => {
  const grantTypes = withTokenExchange
    ? [...REVOCATION_TEST.BASE_GRANT_TYPES, REVOCATION_TEST.TOKEN_EXCHANGE_GRANT_TYPE]
    : [...REVOCATION_TEST.BASE_GRANT_TYPES];

  // createTestApp derives clientId from the name, so an exact name pins the clientId. Only safe
  // across separate domains — clientId uniqueness in AM is per-domain.
  const applicationName = exactName ? name : uniqueName(name, true);

  const application = await createTestApp(applicationName, domain, accessToken, 'WEB', {
    settings: {
      oauth: {
        redirectUris: [REVOCATION_TEST.REDIRECT_URI],
        grantTypes,
        scopeSettings: REVOCATION_TEST.DEFAULT_SCOPES,
      },
    },
    identityProviders: new Set([{ identity: defaultIdp.id, priority: 0 }]),
  });

  expect(application.settings.oauth.clientId).toEqual(expect.any(String));
  return application;
};

/**
 * Runs a full authorization_code flow (login, optional consent, code exchange) for one client.
 */
const buildAuthorizationCodeFlow =
  (oidc: OidcConfiguration, application: Application, basicAuth: string, user: User) =>
  async (scope: string = REVOCATION_TEST.DEFAULT_SCOPE): Promise<SubjectTokens> => {
    const clientId = application.settings.oauth.clientId;
    const authorizationRequestParams = `?response_type=code&client_id=${clientId}&redirect_uri=${encodeURIComponent(
      REVOCATION_TEST.REDIRECT_URI,
    )}&scope=${scope}`;

    const authResponse = await performGet(oidc.authorization_endpoint, authorizationRequestParams).expect(302);
    const loginResult = await extractXsrfTokenAndActionResponse(authResponse);

    const postLogin = await performFormPost(
      loginResult.action,
      '',
      {
        'X-XSRF-TOKEN': loginResult.token,
        username: user.username,
        password: REVOCATION_TEST.USER_PASSWORD,
        client_id: clientId,
      },
      {
        Cookie: loginResult.headers['set-cookie'],
        'Content-type': 'application/x-www-form-urlencoded',
      },
    ).expect(302);

    const postLoginRedirect = await performGet(postLogin.headers['location'], '', {
      Cookie: postLogin.headers['set-cookie'],
    }).expect(302);

    let redirectWithCodeLocation = postLoginRedirect.headers['location'];

    if (redirectWithCodeLocation.includes('/oauth/consent')) {
      const consentResult = await extractXsrfTokenAndActionResponse(postLoginRedirect);
      const postConsent = await performFormPost(
        consentResult.action,
        '',
        {
          'X-XSRF-TOKEN': consentResult.token,
          'scope.openid': true,
          'scope.profile': true,
          'scope.offline_access': true,
          user_oauth_approval: true,
        },
        {
          Cookie: consentResult.headers['set-cookie'],
          'Content-type': 'application/x-www-form-urlencoded',
        },
      ).expect(302);

      const postConsentRedirect = await performGet(postConsent.headers['location'], '', {
        Cookie: postConsent.headers['set-cookie'] || consentResult.headers['set-cookie'],
      }).expect(302);

      redirectWithCodeLocation = postConsentRedirect.headers['location'];
    }

    const codeMatch = redirectWithCodeLocation.match(/[?&]code=([-_a-zA-Z0-9]+)&?/);
    expect(codeMatch).not.toBeNull();
    const authorizationCode = codeMatch![1];

    const response = await performPost(
      oidc.token_endpoint,
      '',
      `grant_type=authorization_code&code=${authorizationCode}&redirect_uri=${encodeURIComponent(REVOCATION_TEST.REDIRECT_URI)}`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth}`,
      },
    ).expect(200);

    expect(response.body.access_token).toMatch(JWT_FORMAT);

    return {
      accessToken: response.body.access_token,
      refreshToken: response.body.refresh_token,
      idToken: response.body.id_token,
      expiresIn: response.body.expires_in,
    };
  };

/**
 * Service application used to obtain a SCIM bearer token. Mirrors specs/gateway/scim/fixture.
 */
const setupScimApp = async (domainId: string, accessToken: string): Promise<Application> => {
  // OAuth settings can't be supplied at create time, hence create-then-update.
  const createAppRequest: NewApplication = {
    name: uniqueName('revocation-scim-app', true),
    type: 'SERVICE',
  };
  const app = await createApplication(domainId, accessToken, createAppRequest);

  await updateApplication(
    domainId,
    accessToken,
    {
      settings: {
        oauth: {
          grantTypes: ['client_credentials'],
          accessTokenValiditySeconds: 7200,
          scopeSettings: [{ scope: 'scim', defaultScope: true }],
        },
      },
    },
    app.id,
  );

  return app;
};

const generateScimAccessToken = async (oidc: OidcConfiguration, scimClient: Application): Promise<string> => {
  const auth = 'Basic ' + applicationBase64Token(scimClient);
  // The SCIM service app may not have reached the gateway when the OIDC endpoint first answers,
  // so retry rather than returning an undefined token that 401s on every later call.
  return withRetry(
    async () => {
      const response = await performPost(oidc.token_endpoint, '', 'grant_type=client_credentials', {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: auth,
      }).expect(200);
      expect(response.body.access_token).toMatch(JWT_FORMAT);
      return response.body.access_token;
    },
    60,
    500,
  );
};

export const setupRevocationFixture = async (options: RevocationFixtureOptions = {}): Promise<RevocationFixture> => {
  const {
    domainNamePrefix = REVOCATION_TEST.DOMAIN_NAME_PREFIX,
    enableTokenExchange = true,
    withSecondaryApplication = false,
    withScim = false,
  } = options;

  let domain: Domain | null = null;
  let accessToken: string | null = null;

  try {
    accessToken = await requestAdminAccessToken();
    expect(accessToken).toMatch(JWT_FORMAT);

    const createdDomain = await createDomain(accessToken, uniqueName(domainNamePrefix, true), REVOCATION_TEST.DOMAIN_DESCRIPTION);
    expect(createdDomain.id).toEqual(expect.any(String));
    domain = createdDomain;

    const idpSet = await getAllIdps(createdDomain.id, accessToken);
    const defaultIdp = idpSet.values().next().value;
    expect(defaultIdp?.id).toEqual(expect.any(String));

    if (enableTokenExchange) {
      await enableTokenExchangeOnDomain(createdDomain.id, accessToken);
    }

    const application = await createRevocationApp(REVOCATION_TEST.CLIENT_NAME, createdDomain, accessToken, defaultIdp, enableTokenExchange);

    const secondaryApplication = withSecondaryApplication
      ? await createRevocationApp(REVOCATION_TEST.SECONDARY_CLIENT_NAME, createdDomain, accessToken, defaultIdp, enableTokenExchange)
      : null;

    // SCIM settings and the service client must exist before the domain starts, so the initial
    // sync picks them both up.
    let scimApplication: Application | null = null;
    if (withScim) {
      await patchDomain(createdDomain.id, accessToken, { scim: { enabled: true, idpSelectionEnabled: false } });
      scimApplication = await setupScimApp(createdDomain.id, accessToken);
    }

    const startedDomain = await startDomain(createdDomain.id, accessToken);
    domain = startedDomain;

    const oidcResponse = await waitForOidcReady(startedDomain.hrid, { timeoutMs: 30000, intervalMs: 500 });
    expect(oidcResponse.status).toBe(200);
    const oidc = oidcResponse.body as OidcConfiguration;

    const user = await buildCreateAndTestUser(startedDomain.id, accessToken, 0);
    expect(user.username).toEqual(expect.any(String));

    const basicAuth = applicationBase64Token(application);

    const obtainAuthorizationCodeTokens = buildAuthorizationCodeFlow(oidc, application, basicAuth, user);

    const secondaryClient: RevocationClient | undefined = secondaryApplication
      ? {
          application: secondaryApplication,
          basicAuth: applicationBase64Token(secondaryApplication),
          obtainAuthorizationCodeTokens: buildAuthorizationCodeFlow(
            oidc,
            secondaryApplication,
            applicationBase64Token(secondaryApplication),
            user,
          ),
        }
      : undefined;

    const introspectToken = (token: string, auth: string = basicAuth): Promise<any> =>
      introspectOidcToken(oidc.introspection_endpoint, token, auth);

    const getUserInfo = (token: string): Promise<any> => performGet(oidc.userinfo_endpoint, '', { Authorization: `Bearer ${token}` });

    const revokeToken = (token: string, revokeOptions: { basicAuth?: string; tokenTypeHint?: string } = {}): Promise<any> => {
      const { basicAuth: auth = basicAuth, tokenTypeHint } = revokeOptions;
      const body = tokenTypeHint
        ? `token=${encodeURIComponent(token)}&token_type_hint=${tokenTypeHint}`
        : `token=${encodeURIComponent(token)}`;

      return performPost(oidc.revocation_endpoint, '', body, {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${auth}`,
      });
    };

    const exchangeToken = async (subjectToken: string, subjectTokenType: 'access_token' | 'refresh_token'): Promise<string> => {
      const response = await performPost(
        oidc.token_endpoint,
        '',
        `grant_type=${REVOCATION_TEST.TOKEN_EXCHANGE_GRANT_TYPE}` +
          `&subject_token=${subjectToken}` +
          `&subject_token_type=urn:ietf:params:oauth:token-type:${subjectTokenType}`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
          Authorization: `Basic ${basicAuth}`,
        },
      ).expect(200);

      expect(response.body.access_token).toMatch(JWT_FORMAT);
      return response.body.access_token;
    };

    const createAdditionalClient = async (
      namePrefix: string = REVOCATION_TEST.SECONDARY_CLIENT_NAME,
      clientOptions: { exactName?: boolean } = {},
    ): Promise<RevocationClient> => {
      // Callers run an authorization_code flow against this client straight away, so it has to be
      // on the gateway before we hand it back — createTestApp does not wait.
      const additionalApplication = await waitForSyncAfter(startedDomain.id, () =>
        createRevocationApp(namePrefix, startedDomain, accessToken!, defaultIdp, enableTokenExchange, clientOptions.exactName),
      );
      const additionalBasicAuth = applicationBase64Token(additionalApplication);

      return {
        application: additionalApplication,
        basicAuth: additionalBasicAuth,
        obtainAuthorizationCodeTokens: buildAuthorizationCodeFlow(oidc, additionalApplication, additionalBasicAuth, user),
      };
    };

    const waitUntilTokenRejected = async (token: string): Promise<void> => {
      await retryUntil(
        () => getUserInfo(token).then((response) => response.status),
        (status) => status === 401,
        { timeoutMillis: REVOCATION_POLL_TIMEOUT_MS, intervalMillis: 250 },
      );
    };

    const waitUntilTokenInactive = async (token: string): Promise<void> => {
      await retryUntil(
        () => introspectToken(token).then((body) => body.active),
        (active) => active === false,
        {
          timeoutMillis: REVOCATION_POLL_TIMEOUT_MS,
          intervalMillis: 250,
        },
      );
    };

    const createAdditionalUser = async (index: number): Promise<User> => {
      const additionalUser = await buildCreateAndTestUser(startedDomain.id, accessToken!, index, false, REVOCATION_TEST.USER_PASSWORD);
      expect(additionalUser.username).toEqual(expect.any(String));
      return additionalUser;
    };

    const obtainAuthorizationCodeTokensAs = (asUser: User, client?: RevocationClient): Promise<SubjectTokens> =>
      buildAuthorizationCodeFlow(oidc, client?.application ?? application, client?.basicAuth ?? basicAuth, asUser)();

    let scim: RevocationFixture['scim'];
    if (scimApplication) {
      const scimAccessToken = await generateScimAccessToken(oidc, scimApplication);
      const scimEndpoint = `${process.env.AM_GATEWAY_URL}/${startedDomain.hrid}/scim`;

      scim = {
        endpoint: scimEndpoint,
        accessToken: scimAccessToken,
        setUserActive: (userId: string, active: boolean) =>
          performPatch(
            scimEndpoint,
            `/Users/${userId}`,
            JSON.stringify({
              schemas: ['urn:ietf:params:scim:api:messages:2.0:PatchOp'],
              Operations: [{ op: 'Replace', path: 'active', value: active }],
            }),
            {
              Authorization: `Bearer ${scimAccessToken}`,
              'Content-type': 'application/json',
            },
          ),
      };
    }

    const cleanup = async () => {
      if (domain?.id && accessToken) {
        await safeDeleteDomain(domain.id, accessToken);
      }
    };

    return {
      domain: startedDomain,
      application,
      secondaryClient,
      user,
      defaultIdp,
      oidc,
      basicAuth,
      accessToken,
      obtainAuthorizationCodeTokens,
      introspectToken,
      exchangeToken,
      getUserInfo,
      revokeToken,
      createAdditionalClient,
      createAdditionalUser,
      obtainAuthorizationCodeTokensAs,
      waitUntilTokenRejected,
      waitUntilTokenInactive,
      scim,
      cleanup,
    };
  } catch (error) {
    if (domain?.id && accessToken) {
      try {
        await safeDeleteDomain(domain.id, accessToken);
      } catch (cleanupError) {
        console.error('Failed to cleanup domain after setup failure:', cleanupError);
      }
    }
    throw error;
  }
};
