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
import { IdentityProvider } from '@management-models/IdentityProvider';
import {
  createDomain,
  DomainOidcConfig,
  getDomainFlows,
  safeDeleteDomain,
  startDomain,
  updateDomainFlows,
  waitForDomainStart,
} from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { addRolesToUser, buildCreateAndTestUser, createUser } from '@management-commands/user-management-commands';
import { patchApplication } from '@management-commands/application-management-commands';
import { createRole } from '@management-commands/role-management-commands';
import { addGroupMember, createGroup } from '@management-commands/group-management-commands';
import { lookupFlowAndResetPolicies } from '@management-commands/flow-management-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { FlowEntityTypeEnum } from '../../../../api/management/models';
import { createTestApp } from '@utils-commands/application-commands';
import { createScope } from '@management-commands/scope-management-commands';
import { performGet, performPost, requestToken, signInUser } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { uniqueName } from '@utils-commands/misc';
import { Fixture } from '../../../test-fixture';

/**
 * Test constants shared by the identity/subject specs.
 */
export const TOKEN_IDENTITY_TEST = {
  DOMAIN_NAME_PREFIX: 'token-identity',
  APP_NAME: 'token-identity-app',
  REDIRECT_URI: 'https://auth-nightly.gravitee.io/myApp/callback',
  USER_PASSWORD: 'SomeP@ssw0rd',
} as const;

const FORM_HEADERS = { 'Content-type': 'application/x-www-form-urlencoded' };

/**
 * Reserved claim names deliberately planted in a user profile. An upstream IdP can
 * legitimately return these as user attributes, so AM has to keep its own values.
 */
export const SPOOFED_PROFILE_CLAIMS = {
  sub: 'idp-supplied-subject',
  gis: 'idp-supplied:gis',
  iss: 'https://evil.example.com',
  aud: 'some-other-client',
  /**
   * A harmless, non-reserved attribute planted alongside the others. Asserting it
   * DID reach the token proves the profile-copy path actually ran, so the
   * "reserved claim was not displaced" assertions cannot pass vacuously.
   */
  profile_copy_marker: 'profile-copy-happened',
} as const;

/**
 * Technical values an upstream OpenID provider leaves on the stored profile. They
 * are AM-internal plumbing (or belong to a different token) and should not be
 * handed to a downstream relying party.
 * `ConstantKeys.ID_TOKEN_EXCLUDED_CLAIMS` names these.
 */
export const LEAKY_PROFILE_CLAIMS = {
  op_id_token: 'upstream-id-token-do-not-forward',
  op_access_token: 'upstream-access-token-do-not-forward',
  nbf: 4102444800,
  iat: 111111111,
  exp: 222222222,
  auth_time: 333333333,
  updated_at: 444444444,
  /** Positive anchor - proves the profile-copy path ran. */
  leak_probe_marker: 'leak-probe-copied',
} as const;

/** Sorted claim names of a lightweight access token when the flow sets no optional claim. */
export const LIGHTWEIGHT_ACCESS_TOKEN_CLAIMS = ['aud', 'client_id', 'domain', 'exp', 'gis', 'iat', 'iss', 'jti', 'scope', 'sub'];

/** Sorted claim names of a lightweight refresh token when the flow sets no optional claim. */
export const LIGHTWEIGHT_REFRESH_TOKEN_CLAIMS = ['aud', 'domain', 'exp', 'gis', 'iat', 'iss', 'jti', 'scope', 'sub'];

/** Sorted claim names of a lightweight ID token when the flow sets no optional claim. */
export const LIGHTWEIGHT_ID_TOKEN_CLAIMS = ['aud', 'auth_time', 'exp', 'gis', 'iat', 'iss', 'sub'];

/**
 * A decoded JWT payload plus the raw token it came from, so a failing assertion
 * can report both the claim and the token that produced it.
 */
export interface DecodedToken {
  raw: string;
  payload: Record<string, any>;
}

export interface TokenIdentityFixture extends Fixture {
  domain: Domain;
  oidc: DomainOidcConfig;
  app: Application;
  defaultIdp: IdentityProvider;
  clientId: string;
  user: any;
  /** A second, unrelated user in the same domain - used to prove subjects do not collide. */
  otherUser: any;
  /**
   * A user whose stored profile carries its own `sub` (and other reserved names),
   * as an upstream identity provider would supply. AM must not let those win.
   * See https://github.com/gravitee-io/issues/issues/7118
   */
  spoofedUser: any;
  /** A user whose profile carries upstream/technical values that must not be forwarded. */
  leakyUser: any;

  /** Resource-owner password grant for the primary user. Returns the raw token endpoint body. */
  passwordGrant: (scope?: string) => Promise<Record<string, any>>;
  /** Resource-owner password grant for an arbitrary fixture user. */
  passwordGrantFor: (subject: any, scope?: string) => Promise<Record<string, any>>;
  /** Exchanges a refresh token for a fresh token set. */
  refreshGrant: (refreshToken: string) => Promise<Record<string, any>>;
  /**
   * Full browser authorization-code flow: login, consent-free redirect, code exchange.
   * `additionalParams` is appended to the authorize request (e.g. `nonce=...`).
   */
  authorizationCodeFlow: (additionalParams?: string) => Promise<Record<string, any>>;
  /** Calls /oauth/authorize with prompt=none and the id_token_hint, without a session cookie, and returns the redirect location. */
  silentAuthorize: (idTokenHint: string) => Promise<string>;
  /** Exchanges the code in an authorize redirect location at the token endpoint. */
  exchangeCode: (location: string) => Promise<Record<string, any>>;
  /** Calls /oidc/userinfo with the supplied access token. */
  userinfo: (accessTokenValue: string) => Promise<Record<string, any>>;
  /**
   * Patches the Lightweight JWT setting, then polls until a fresh access token reflects it.
   * `probeClaim` is a custom claim the setting removes. The allowlists replace the stored ones.
   */
  setLightweightJwt: (enabled: boolean, probeClaim: string, allowlists?: LightweightJwtAllowlists) => Promise<void>;
}

/**
 * Decodes the payload of a JWS without verifying it. Verification is the
 * responsibility of the specs that care about it; this is for claim inspection.
 */
export function decodeToken(token: string): DecodedToken {
  const parts = token.split('.');
  expect(parts).toHaveLength(3);
  return { raw: token, payload: JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8')) };
}

export interface TokenCustomClaim {
  tokenType: 'ACCESS_TOKEN' | 'ID_TOKEN';
  claimName: string;
  claimValue: string;
}

export interface UserInfoCustomClaim {
  claimName: string;
  claimValue: string;
}

export interface LightweightJwtAllowlists {
  accessTokenAllowlist?: string[];
  idTokenAllowlist?: string[];
}

export interface TokenIdentityOptions {
  /** Application-level custom claims, applied to the token types named on each entry. */
  tokenCustomClaims?: TokenCustomClaim[];
  /** Application-level claims evaluated when /oidc/userinfo is called. */
  userinfoCustomClaims?: UserInfoCustomClaim[];
  /** Extra attributes merged onto the primary user's stored profile. */
  userAttributes?: Record<string, unknown>;
  /**
   * Custom metadata stored on the application, reachable from expression language as
   * `{#context.attributes['client'].metadata['key']}`. Left unset unless a test asks for it,
   * so it is inert for every other spec.
   */
  applicationMetadata?: Record<string, unknown>;
  /**
   * Extra requestable scopes, on top of the defaults this fixture always configures.
   * Each is created on the domain first, since an application may only reference
   * a scope the domain already knows about.
   */
  extraScopes?: { scope: string; defaultScope: boolean }[];
  /** Application-level Lightweight JWT settings. */
  lightweightJwtSettings?: { enabled: boolean } & LightweightJwtAllowlists;
  /** Lets prompt=none with an id_token_hint authenticate the user without a session. */
  silentReAuthentication?: boolean;
  /** Name of a domain role assigned to the primary user. */
  userRole?: string;
  /** Name of a domain group the primary user belongs to. */
  userGroup?: string;
  /** Groovy script run in the TOKEN pre flow. */
  tokenPreScript?: string;
}

/**
 * Note on `createDomain` + `startDomain` rather than the preferred `setupDomainForTest`:
 * that helper creates and starts in a single call, and this fixture has to add scopes,
 * an application and several users in between, so that the first sync sees them all.
 * Everything else follows the guidelines - unique domain name, guaranteed cleanup, and
 * the OIDC config taken from `waitForDomainStart` rather than a raw well-known fetch.
 *
 * Only the domain name is randomised. Every other resource lives inside that domain,
 * so fixed names cannot collide between parallel spec files.
 */
export const setupTokenIdentityFixture = async (options: TokenIdentityOptions = {}): Promise<TokenIdentityFixture> => {
  const accessToken = await requestAdminAccessToken();
  let domain: Domain | null = null;

  try {
    domain = await createDomain(
      accessToken,
      uniqueName(TOKEN_IDENTITY_TEST.DOMAIN_NAME_PREFIX, true),
      'Subject and identity claim regression tests',
    );

    // Any non-standard scope has to exist on the domain before an application
    // may reference it, otherwise the token request fails with "scope X is not valid".
    for (const { scope } of options.extraScopes ?? []) {
      await createScope(domain.id, accessToken, { key: scope, name: scope, description: `${scope} test scope` });
    }

    const idpSet = await getAllIdps(domain.id, accessToken);
    const defaultIdp = idpSet.values().next().value;
    expect(defaultIdp?.id).toEqual(expect.any(String));

    const app = await createTestApp(TOKEN_IDENTITY_TEST.APP_NAME, domain, accessToken, 'WEB', {
      settings: {
        oauth: {
          redirectUris: [TOKEN_IDENTITY_TEST.REDIRECT_URI],
          grantTypes: ['authorization_code', 'password', 'refresh_token'],
          scopeSettings: [
            { scope: 'openid', defaultScope: true },
            { scope: 'email', defaultScope: true },
            { scope: 'profile', defaultScope: true },
            // Requestable but not default. Non-default scopes are inert unless a test
            // asks for them, so adding them here does not change any other spec.
            // full_profile is the path where the whole user profile is copied into the
            // token, so reserved-name collisions surface there rather than under the
            // narrower scopes; address and phone let the projection tests prove that a
            // claim set appears only when its own scope was requested.
            { scope: 'full_profile', defaultScope: false },
            { scope: 'address', defaultScope: false },
            { scope: 'phone', defaultScope: false },
            ...(options.extraScopes ?? []).map(({ scope, defaultScope }) => ({ scope, defaultScope })),
          ],
          ...(options.tokenCustomClaims ? { tokenCustomClaims: options.tokenCustomClaims } : {}),
          ...(options.userinfoCustomClaims ? { userinfoCustomClaims: options.userinfoCustomClaims } : {}),
          ...(options.lightweightJwtSettings ? { lightweightJwtSettings: options.lightweightJwtSettings } : {}),
          ...(options.silentReAuthentication ? { silentReAuthentication: true } : {}),
        },
        advanced: { skipConsent: true },
      },
      ...(options.applicationMetadata ? { metadata: options.applicationMetadata } : {}),
      identityProviders: new Set([{ identity: defaultIdp.id, priority: 0 }]),
    });

    // Created before the domain starts so the initial sync picks everything up.
    const user = options.userAttributes
      ? await createUser(domain.id, accessToken, {
          firstName: 'firstName0',
          lastName: 'lastName0',
          email: 'firstName0.lastName0@example.com',
          username: 'firstName0.lastName0',
          password: TOKEN_IDENTITY_TEST.USER_PASSWORD,
          preRegistration: false,
          additionalInformation: options.userAttributes,
        })
      : await buildCreateAndTestUser(domain.id, accessToken, 0, false, TOKEN_IDENTITY_TEST.USER_PASSWORD);
    const otherUser = await buildCreateAndTestUser(domain.id, accessToken, 1, false, TOKEN_IDENTITY_TEST.USER_PASSWORD);
    const spoofedUser = await createUser(domain.id, accessToken, {
      firstName: 'Spoofed',
      lastName: 'Subject',
      email: 'spoofed.subject@example.com',
      username: 'spoofed.subject',
      password: TOKEN_IDENTITY_TEST.USER_PASSWORD,
      preRegistration: false,
      additionalInformation: SPOOFED_PROFILE_CLAIMS,
    });
    const leakyUser = await createUser(domain.id, accessToken, {
      firstName: 'Leaky',
      lastName: 'Profile',
      email: 'leaky.profile@example.com',
      username: 'leaky.profile',
      password: TOKEN_IDENTITY_TEST.USER_PASSWORD,
      preRegistration: false,
      additionalInformation: LEAKY_PROFILE_CLAIMS,
    });

    if (options.userRole) {
      const role = await createRole(domain.id, accessToken, { name: options.userRole, assignableType: 'DOMAIN' });
      await addRolesToUser(domain.id, accessToken, user.id, [role.id]);
    }
    if (options.userGroup) {
      const group = await createGroup(domain.id, accessToken, { name: options.userGroup, description: options.userGroup });
      await addGroupMember(domain.id, accessToken, group.id, user.id);
    }
    if (options.tokenPreScript) {
      const flows = await getDomainFlows(domain.id, accessToken);
      lookupFlowAndResetPolicies(flows, FlowEntityTypeEnum.Token, 'pre', [
        {
          name: 'Groovy',
          policy: 'groovy',
          description: '',
          condition: '',
          enabled: true,
          configuration: JSON.stringify({ onRequestScript: options.tokenPreScript }),
        },
      ]);
      await updateDomainFlows(domain.id, accessToken, flows);
    }

    await startDomain(domain.id, accessToken);
    const started = await waitForDomainStart(domain);
    const startedDomain = started.domain;
    const oidc = started.oidcConfig;

    const basicAuth = () => 'Basic ' + applicationBase64Token(app);

    const passwordGrantFor = async (subject: any, scope?: string) => {
      const body =
        `grant_type=password&username=${encodeURIComponent(subject.username)}` +
        `&password=${encodeURIComponent(TOKEN_IDENTITY_TEST.USER_PASSWORD)}` +
        (scope ? `&scope=${encodeURIComponent(scope)}` : '');
      const response = await performPost(oidc.token_endpoint, '', body, {
        ...FORM_HEADERS,
        Authorization: basicAuth(),
      }).expect(200);
      return response.body;
    };

    const passwordGrant = (scope?: string) => passwordGrantFor(user, scope);

    const refreshGrant = async (refreshToken: string) => {
      const response = await performPost(oidc.token_endpoint, '', `grant_type=refresh_token&refresh_token=${refreshToken}`, {
        ...FORM_HEADERS,
        Authorization: basicAuth(),
      }).expect(200);
      return response.body;
    };

    const authorizationCodeFlow = async (additionalParams?: string) => {
      const postLogin = await signInUser(
        startedDomain,
        app,
        { ...user, password: TOKEN_IDENTITY_TEST.USER_PASSWORD },
        oidc,
        additionalParams,
      );
      const response = await requestToken(app, oidc, postLogin);
      return response.body;
    };

    const silentAuthorize = async (idTokenHint: string) => {
      const params =
        `?response_type=code&prompt=none&scope=openid&state=silent` +
        `&client_id=${app.settings.oauth.clientId}&redirect_uri=${TOKEN_IDENTITY_TEST.REDIRECT_URI}` +
        `&id_token_hint=${idTokenHint}`;
      const response = await performGet(oidc.authorization_endpoint, params).expect(302);
      return response.headers['location'];
    };

    const exchangeCode = async (location: string) => {
      const response = await requestToken(app, oidc, { headers: { location } });
      return response.body;
    };

    /**
     * A user is created before the domain starts, but the gateway does not always
     * serve the stored profile on the very first authentication - the attributes can
     * come back undefined under load. Rather than hand back a fixture that is only
     * probably ready, block until the profile is genuinely visible.
     *
     * `full_profile` copies the whole stored profile into the token, so a planted
     * attribute appearing there is proof the gateway can read it.
     */
    const waitForUserProfileVisible = async (attributeName: string) => {
      const deadline = Date.now() + 20_000;
      for (;;) {
        const tokens = await passwordGrantFor(user, 'openid full_profile');
        if (decodeToken(tokens.id_token).payload[attributeName] !== undefined) {
          return;
        }
        if (Date.now() > deadline) {
          throw new Error(`gateway still not serving user attribute "${attributeName}" after 20s`);
        }
        await new Promise((resolve) => setTimeout(resolve, 500));
      }
    };

    if (options.userAttributes) {
      await waitForUserProfileVisible(Object.keys(options.userAttributes)[0]);
    }

    const userinfo = async (accessTokenValue: string) => {
      const response = await performGet(oidc.userinfo_endpoint, '', {
        Authorization: `Bearer ${accessTokenValue}`,
      }).expect(200);
      return response.body;
    };

    const setLightweightJwt = async (enabled: boolean, probeClaim: string, allowlists: LightweightJwtAllowlists = {}) => {
      await waitForSyncAfter(startedDomain.id, () =>
        patchApplication(
          startedDomain.id,
          accessToken,
          { settings: { oauth: { lightweightJwtSettings: { enabled, ...allowlists } } } },
          app.id,
        ),
      );

      // the gateway client can lag the domain lastSync by a few seconds
      const deadline = Date.now() + 15_000;
      for (;;) {
        const probe = await passwordGrantFor(user, 'openid');
        if (probeClaim in decodeToken(probe.access_token).payload !== enabled) {
          return;
        }
        if (Date.now() > deadline) {
          throw new Error(`gateway still not serving lightweightJwtSettings.enabled=${enabled} after 15s`);
        }
        await new Promise((resolve) => setTimeout(resolve, 500));
      }
    };

    return {
      accessToken,
      domain: startedDomain,
      oidc,
      app,
      defaultIdp,
      clientId: app.settings.oauth.clientId,
      user,
      otherUser,
      spoofedUser,
      leakyUser,
      passwordGrant,
      passwordGrantFor,
      refreshGrant,
      authorizationCodeFlow,
      silentAuthorize,
      exchangeCode,
      userinfo,
      setLightweightJwt,
      cleanUp: async () => {
        if (domain?.id) {
          await safeDeleteDomain(domain.id, accessToken);
        }
      },
    };
  } catch (error) {
    if (domain?.id) {
      try {
        await safeDeleteDomain(domain.id, accessToken);
      } catch (cleanupError) {
        console.error('Failed to clean up domain after setup failure:', cleanupError);
      }
    }
    throw error;
  }
};
