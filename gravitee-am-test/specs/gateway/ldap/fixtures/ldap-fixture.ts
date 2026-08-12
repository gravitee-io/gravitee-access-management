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
import { setupDomainForTest, safeDeleteDomain, DomainOidcConfig } from '@management-commands/domain-management-commands';
import { createIdp, updateIdp } from '@management-commands/idp-management-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { uniqueName } from '@utils-commands/misc';
import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
import { IdentityProvider } from '@management-models/IdentityProvider';
import { Fixture } from '../../../test-fixture';

/**
 * Users seeded by docker/local-stack/dev/ldap/gravitee-io.ldif.
 * Start the directory with: ./local-stack.sh up --ldap  (or --full)
 */
export const LDAP_ADMIN = { username: 'ldap-admin', password: 'ldap-admin' };
/** In GRAVITEE_USERS, not GRAVITEE_ADMINS — proves a mapper discriminates between groups. */
export const LDAP_JDOE = { username: 'jdoe', password: 'password' };
/** In no group at all — authenticates, but must never receive a group-derived role. */
export const LDAP_NOGROUP = { username: 'nogroup', password: 'nogroup' };
/** Password held as an {SSHA} hash in the directory rather than in the clear. */
export const LDAP_HASHED = { username: 'hasheduser', password: 'HashedP@ss1' };
/** Password stored as a raw base64 SHA-1 digest, for compare-mode authentication. */
export const LDAP_COMPARE = { username: 'compareuser', password: 'ComparePass1' };
/** Same digest stored WITHOUT the {SCHEME} prefix — needs hashEncodedByThirdParty. */
export const LDAP_COMPARE_3P = { username: 'compareuser3p', password: 'ComparePass1' };

export const LDAP_BASE_DN = 'dc=gravitee,dc=io';
export const GRAVITEE_ADMINS_DN = `cn=GRAVITEE_ADMINS,ou=groups,${LDAP_BASE_DN}`;

export const REDIRECT_URI = 'https://auth-nightly.gravitee.io/myApp/callback';

/**
 * Options for the LDAP IdP configuration. Defaults mirror the plugin's own schema
 * defaults so a test only states what it actually varies.
 */
export interface LdapConfigOptions {
  /** Overridden to point the provider at an unreachable directory. */
  contextSourceUrl?: string;
  /** Overridden to simulate wrong service-account credentials. */
  contextSourcePassword?: string;
  /**
   * Must name the OU the user entries actually live under, relative to the base DN.
   * The seeded users are at uid=<uid>,ou=people,dc=gravitee,dc=io, so 'ou=people'
   * resolves them; pointing this at any other OU makes the user unfindable and the
   * login fails as though the account did not exist.
   */
  userSearchBase?: string;
  /** Defaults to '(uid={0})'. Override to log in by another attribute, e.g. '(mail={0})'. */
  userSearchFilter?: string;
  fetchGroups?: boolean;
  /** Walks up nested groups — a group whose member is another group. */
  recursiveGroupFetch?: boolean;
  /** Which group attribute is written into memberOf. Defaults to 'cn'. */
  groupRoleAttribute?: string;
  /** DN-based group search. Its filter substitutes {0} with the user's full DN. */
  groupSearchBase?: string;
  groupSearchFilter?: string;
  /** POSIX group search. Its filter substitutes {0} with the bare uid, not the DN. */
  posixGroupSearchBase?: string;
  posixGroupSearchFilter?: string;
  userReturnAttribute?: string;
  /**
   * Setting these switches the provider from bind-based authentication to COMPARE
   * mode: AM hashes the submitted password itself and compares the result with the
   * stored userPassword, instead of asking the directory to verify it.
   */
  passwordAlgorithm?: string;
  passwordEncoding?: string;
  /**
   * false/unset -> AM compares "{SCHEME}<encoded>"; true -> "<encoded>" alone.
   * The stored userPassword must match whichever form is in play.
   */
  hashEncodedByThirdParty?: boolean;
}

/**
 * Builds the LDAP IdP configuration.
 *
 * Two things here are not obvious and are load-bearing:
 *
 * 1. The container runs slapd as a non-root user, so it listens on 1389 — NOT the
 *    389 that most LDAP documentation (and the AM plugin's own schema default)
 *    assumes.
 * 2. `clientAuthenticationCertificate` must be present even when mTLS is off. The
 *    schema's `required` array does not list it, but the management API rejects the
 *    payload without it:
 *      {"message":"#: required key [clientAuthenticationCertificate] not found"}
 *    Omitting it fails at fixture setup with a misleading 400.
 */
export function buildLdapConfiguration(options: LdapConfigOptions = {}): string {
  const config: Record<string, unknown> = {
    contextSourceUrl: options.contextSourceUrl ?? 'ldap://openldap:1389',
    contextSourceBase: LDAP_BASE_DN,
    contextSourceUsername: `cn=admin,${LDAP_BASE_DN}`,
    contextSourcePassword: options.contextSourcePassword ?? 'admin',
    userSearchBase: options.userSearchBase ?? 'ou=people',
    userSearchFilter: options.userSearchFilter ?? '(uid={0})',
    userReturnAttribute: options.userReturnAttribute ?? '+',
    fetchGroups: options.fetchGroups ?? false,
    useMutualTLS: false,
    clientAuthenticationCertificate: '',
  };
  if (options.recursiveGroupFetch !== undefined) {
    config.recursiveGroupFetch = options.recursiveGroupFetch;
  }
  if (options.groupRoleAttribute !== undefined) {
    config.groupRoleAttribute = options.groupRoleAttribute;
  }
  if (options.passwordAlgorithm !== undefined) {
    config.passwordAlgorithm = options.passwordAlgorithm;
  }
  if (options.passwordEncoding !== undefined) {
    config.passwordEncoding = options.passwordEncoding;
  }
  if (options.hashEncodedByThirdParty !== undefined) {
    config.hashEncodedByThirdParty = options.hashEncodedByThirdParty;
  }
  if (options.groupSearchBase !== undefined) {
    config.groupSearchBase = options.groupSearchBase;
  }
  if (options.groupSearchFilter !== undefined) {
    config.groupSearchFilter = options.groupSearchFilter;
  }
  if (options.posixGroupSearchBase !== undefined) {
    config.posixGroupSearchBase = options.posixGroupSearchBase;
  }
  if (options.posixGroupSearchFilter !== undefined) {
    config.posixGroupSearchFilter = options.posixGroupSearchFilter;
  }
  return JSON.stringify(config);
}

export interface LdapFixture extends Fixture {
  domain: Domain;
  accessToken: string;
  openIdConfiguration: DomainOidcConfig;
  idp: IdentityProvider;
  app: Application;
  /** Rewrites the IdP configuration and waits for the gateway to pick it up. */
  reconfigureIdp: (options: LdapConfigOptions) => Promise<IdentityProvider>;
  /** Sets role/group mappers, leaving the LDAP connection configuration untouched. */
  setMappers: (
    roleMapper: Record<string, string[]>,
    groupMapper?: Record<string, string[]>,
    options?: LdapConfigOptions,
  ) => Promise<IdentityProvider>;
  /**
   * Sets the user (attribute) mappers — `{ profileAttribute: rawLdapAttribute }` —
   * alongside the LDAP configuration.
   */
  setUserMappers: (mappers: Record<string, string>, options?: LdapConfigOptions) => Promise<IdentityProvider>;
}

export const setupLdapFixture = async (): Promise<LdapFixture> => {
  let domain: Domain | null = null;
  let accessToken: string | null = null;

  try {
    accessToken = await requestAdminAccessToken();
    expect(accessToken).toEqual(expect.any(String));

    const { domain: createdDomain, oidcConfig } = await setupDomainForTest(uniqueName('ldap', true), {
      accessToken,
      waitForStart: true,
    });
    domain = createdDomain;
    expect(domain.id).toEqual(expect.any(String));

    const idp = await createIdp(domain.id, accessToken, {
      external: false,
      type: 'ldap-am-idp',
      domainWhitelist: [],
      name: uniqueName('ldap-idp', true),
      configuration: buildLdapConfiguration(),
    });
    expect(idp.id).toEqual(expect.any(String));

    const app = await waitForSyncAfter(domain.id, () =>
      createTestApp(uniqueName('ldap-app', true), domain, accessToken, 'WEB', {
        identityProviders: new Set([{ identity: idp.id, priority: 0 }]),
        settings: {
          oauth: {
            redirectUris: [REDIRECT_URI],
            grantTypes: ['authorization_code'],
            scopeSettings: [{ scope: 'openid', defaultScope: true }, { scope: 'profile' }, { scope: 'email' }],
          },
          advanced: { skipConsent: true },
        },
      }),
    );
    expect(app.id).toEqual(expect.any(String));

    // `type` is required on update — omitting it is rejected. Mappers are only
    // settable via update; the create endpoint rejects roleMapper/groupMapper/mappers
    // with "Property [...] is not recognized as a valid property".
    /**
     * NOTE: waitForSyncAfter returns once the domain reports synced, but the gateway
     * rebuilds the identity provider a moment later. Authenticating inside that gap
     * silently uses the PREVIOUS configuration — a test then passes alone and fails
     * under parallel load. Chaining waitForNextSync here looks like the fix but is
     * not: it waits for lastSync to ADVANCE, and with no further change pending it
     * burns its full timeout on every call (it made the suite 5x slower).
     * The gap is handled by retryImmediatelyForThisFile() in the specs instead.
     */
    const applyIdp = (
      configuration: string,
      mappers: Record<string, string>,
      roleMapper: Record<string, string[]>,
      groupMapper: Record<string, string[]>,
    ): Promise<IdentityProvider> =>
      waitForSyncAfter(domain.id, () =>
        updateIdp(
          domain.id,
          accessToken,
          { name: idp.name, type: idp.type, configuration, mappers, roleMapper, groupMapper },
          idp.id,
        ),
      );

    const reconfigureIdp = (options: LdapConfigOptions) => applyIdp(buildLdapConfiguration(options), {}, {}, {});

    const setMappers = (
      roleMapper: Record<string, string[]>,
      groupMapper: Record<string, string[]> = {},
      options: LdapConfigOptions = {},
    ) => applyIdp(buildLdapConfiguration(options), {}, roleMapper, groupMapper);

    const setUserMappers = (mappers: Record<string, string>, options: LdapConfigOptions = {}) =>
      applyIdp(buildLdapConfiguration(options), mappers, {}, {});

    return {
      domain,
      accessToken,
      openIdConfiguration: oidcConfig,
      idp,
      app,
      reconfigureIdp,
      setMappers,
      setUserMappers,
      cleanUp: async () => {
        if (domain?.id && accessToken) {
          await safeDeleteDomain(domain.id, accessToken);
        }
      },
    };
  } catch (error) {
    if (domain?.id && accessToken) {
      try {
        await safeDeleteDomain(domain.id, accessToken);
      } catch (cleanupError) {
        console.error('Failed to cleanup domain after LDAP fixture setup failure:', cleanupError);
      }
    }
    throw error;
  }
};
