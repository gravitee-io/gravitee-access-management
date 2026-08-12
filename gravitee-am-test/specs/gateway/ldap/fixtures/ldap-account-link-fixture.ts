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
  setupDomainForTest,
  safeDeleteDomain,
  DomainOidcConfig,
  getDomainFlows,
  updateDomainFlows,
  waitForOidcReady,
} from '@management-commands/domain-management-commands';
import { createIdp, updateIdp } from '@management-commands/idp-management-commands';
import { createRole } from '@management-commands/role-management-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { patchApplication } from '@management-commands/application-management-commands';
import { lookupFlowAndResetPolicies } from '@management-commands/flow-management-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { uniqueName } from '@utils-commands/misc';
import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
import { IdentityProvider } from '@management-models/IdentityProvider';
import { Fixture } from '../../../test-fixture';
import { buildLdapConfiguration, REDIRECT_URI } from './ldap-fixture';

/** Exists in BOTH subtrees with the same username, which is what lets them be linked. */
export const LDAP_JOHN = { username: 'john', password: 'test' };

export const WORKER_ROLE = 'worker-role';
export const ENGINEER_ROLE = 'engineer-role';

/**
 * Role mapper conditions as EL expressions over the fetched groups, matching how
 * the original AM-5385 investigation configured them. `memberOf` holds the group
 * cn values produced by the group search.
 */
export const WORKER_CONDITION = "{#profile['memberOf'].contains('workers')}";
export const ENGINEER_CONDITION = "{#profile['memberOf'].contains('engineers')}";

/** Group search settings shared by both providers: nested search, no explicit base. */
const GROUP_SEARCH = {
  fetchGroups: true,
  recursiveGroupFetch: true,
  groupSearchFilter: '(member={0})',
} as const;

export interface LdapAccountLinkFixture extends Fixture {
  domain: Domain;
  accessToken: string;
  openIdConfiguration: DomainOidcConfig;
  /** Points at ou=people — John is in 'workers' here, and this provider carries the role mappers. */
  idpWithRoles: IdentityProvider;
  /** Points at ou=people2 — same username, no groups, no mappers. */
  idpWithoutRoles: IdentityProvider;
  app: Application;
  /** Attaches exactly one provider to the application, mirroring enabling it in the console. */
  useIdp: (idp: IdentityProvider) => Promise<void>;
  /** Replaces the role mapper on the roles-bearing provider. */
  setRoleMapper: (roleMapper: Record<string, string[]>) => Promise<IdentityProvider>;
}

export const setupLdapAccountLinkFixture = async (): Promise<LdapAccountLinkFixture> => {
  let domain: Domain | null = null;
  let accessToken: string | null = null;

  try {
    accessToken = await requestAdminAccessToken();

    const { domain: created, oidcConfig } = await setupDomainForTest(uniqueName('ldap-link', true), {
      accessToken,
      waitForStart: true,
    });
    domain = created;

    for (const name of [WORKER_ROLE, ENGINEER_ROLE]) {
      await createRole(domain.id, accessToken, { name, description: `AM-5385 ${name}`, assignableType: 'DOMAIN' });
    }

    const idpWithRoles = await createIdp(domain.id, accessToken, {
      external: false,
      type: 'ldap-am-idp',
      domainWhitelist: [],
      name: uniqueName('ldap-with-roles', true),
      configuration: buildLdapConfiguration({ userSearchBase: 'ou=people', ...GROUP_SEARCH }),
    });

    const idpWithoutRoles = await createIdp(domain.id, accessToken, {
      external: false,
      type: 'ldap-am-idp',
      domainWhitelist: [],
      name: uniqueName('ldap-without-roles', true),
      configuration: buildLdapConfiguration({ userSearchBase: 'ou=people2', ...GROUP_SEARCH }),
    });

    const app = await waitForSyncAfter(domain.id, () =>
      createTestApp(uniqueName('ldap-link-app', true), domain, accessToken, 'WEB', {
        identityProviders: new Set([{ identity: idpWithRoles.id, priority: 0 }]),
        settings: {
          oauth: {
            redirectUris: [REDIRECT_URI],
            grantTypes: ['authorization_code'],
            scopeSettings: [{ scope: 'openid', defaultScope: true }],
          },
          advanced: { skipConsent: true },
        },
      }),
    );

    // Account linking on the CONNECT flow, matching on username — the configuration
    // the original report used. Without it the second provider would simply create
    // a second, unrelated user.
    const flows = await getDomainFlows(domain.id, accessToken);
    lookupFlowAndResetPolicies(flows, 'CONNECT', 'pre', [
      {
        name: 'Account Linking',
        policy: 'policy-am-account-linking',
        description: '',
        configuration: JSON.stringify({
          exitIfNoAccount: false,
          exitIfMultipleAccount: false,
          userAttributes: [{ name: 'username', value: LDAP_JOHN.username }],
        }),
        enabled: true,
        condition: '',
      },
    ]);
    await waitForSyncAfter(domain.id, () => updateDomainFlows(domain.id, accessToken, flows));
    // Changing domain flows redeploys the gateway's route chain, which happens
    // asynchronously AFTER the domain reports synced. Without this the first
    // authorization request can hit the gap and 404.
    await waitForOidcReady(domain.hrid, { timeoutMs: 5000, intervalMs: 200 });

    const useIdp = async (idp: IdentityProvider): Promise<void> => {
      await waitForSyncAfter(domain.id, () =>
        patchApplication(
          domain.id,
          accessToken,
          { identityProviders: [{ identity: idp.id, priority: 0 }] },
          app.id,
        ),
      );
    };

    const setRoleMapper = async (roleMapper: Record<string, string[]>): Promise<IdentityProvider> => {
      const updated = await waitForSyncAfter(domain.id, () =>
        updateIdp(
          domain.id,
          accessToken,
          {
            name: idpWithRoles.name,
            type: idpWithRoles.type,
            configuration: idpWithRoles.configuration,
            mappers: {},
            roleMapper,
            groupMapper: {},
          },
          idpWithRoles.id,
        ),
      );
      return updated;
    };

    expect(idpWithRoles.id).toEqual(expect.any(String));
    expect(idpWithoutRoles.id).toEqual(expect.any(String));

    return {
      domain,
      accessToken,
      openIdConfiguration: oidcConfig,
      idpWithRoles,
      idpWithoutRoles,
      app,
      useIdp,
      setRoleMapper,
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
        console.error('Failed to cleanup domain after AM-5385 fixture setup failure:', cleanupError);
      }
    }
    throw error;
  }
};
