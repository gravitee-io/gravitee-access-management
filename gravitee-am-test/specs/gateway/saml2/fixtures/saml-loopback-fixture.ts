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
import { uniqueName } from '@utils-commands/misc';
import { patchApplication } from '@management-commands/application-management-commands';
import { updateIdp } from '@management-commands/idp-management-commands';
import { listUsers, getAllUsers, deleteUser } from '@management-commands/user-management-commands';
import { safeDeleteDomain } from '@management-commands/domain-management-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { User } from '@management-models/User';
import { BasicResponse } from '@utils-commands/misc';

import { SamlFixture, SamlProviderDomain, setupSamlProviderDomain, setupSamlProviderTest, TEST_USER } from '../setup';

/**
 * Poll window for gateway sync after patching the provider application.
 * Matches the value used by the existing SAML gateway spec.
 */
const SYNC_OPTS = { timeoutMillis: 60000, intervalMillis: 500 };

export const SAML_LOOPBACK_TEST = {
  PROVIDER_PREFIX: 'saml-loopback-provider',
  CLIENT_PREFIX: 'saml-loopback',
  /** EL resolving to the authenticating user's username. */
  EL_USERNAME: `{#context.attributes['user'].username}`,
  /** EL resolving to the authenticating user's email. */
  EL_EMAIL: `{#context.attributes['user'].email}`,
} as const;

/**
 * Fixture for the AM-to-AM SAML loopback used by the gateway SAML specs.
 *
 * Wraps the loopback environment built by `../setup` (provider domain acting as the
 * SAML IdP, client domain consuming it through `saml2-generic-am-idp`) and adds the
 * helpers these tests need: mutating the provider application's SAML settings, and
 * inspecting or removing the federated user the login produces in the client domain.
 */
export interface SamlLoopbackFixture {
  /** Underlying loopback fixture: domains, `login()`, `expectRedirectToClient()`. */
  saml: SamlFixture;
  accessToken: string;

  /** Patch the provider application's SAML settings and wait for the gateway to pick them up. */
  setSamlSettings: (saml: Record<string, unknown>) => Promise<void>;
  /**
   * Run the SAML login flow and assert it lands back on the client with an auth code.
   *
   * Deliberately does NOT look the federated user up: the federated user's username is
   * the NameID, which is what these tests vary, so the caller decides how to find it.
   */
  authenticate: (username: string, password: string) => Promise<void>;
  /** Patch the client domain's SAML IdP configuration (merged over the current config). */
  setSamlIdpConfig: (configPatch: Record<string, unknown>) => Promise<void>;
  /** Run the login flow WITHOUT asserting success — for negative scenarios. */
  attemptLogin: (username: string, password: string) => Promise<BasicResponse>;
  /** Federated users in the client domain; `query` omitted lists them all. */
  findFederatedUsers: (query?: string) => Promise<User[]>;
  /** The single federated user in the client domain — fails if there is not exactly one. */
  getOnlyFederatedUser: () => Promise<User>;
  /** Delete every federated user in the client domain, so each test starts clean. */
  clearFederatedUsers: () => Promise<void>;
  /** Restore the SAML configuration on both sides to the values captured at setup. */
  resetToBaseline: () => Promise<void>;

  cleanup: () => Promise<void>;
}

export const setupSamlLoopbackFixture = async (): Promise<SamlLoopbackFixture> => {
  let provider: SamlProviderDomain | null = null;
  let saml: SamlFixture | null = null;

  try {
    const accessToken = await requestAdminAccessToken();

    provider = await setupSamlProviderDomain(uniqueName(SAML_LOOPBACK_TEST.PROVIDER_PREFIX, true).toLowerCase());
    saml = await setupSamlProviderTest(uniqueName(SAML_LOOPBACK_TEST.CLIENT_PREFIX, true).toLowerCase(), undefined, provider);

    const providerDomainId = saml.domains.providerDomain.id;
    const providerAppId = saml.domains.providerApplication.id;
    const clientDomainId = saml.domains.clientDomain.id;

    // Known-good configuration captured at setup, so a scenario that breaks the SAML
    // wiring on purpose can hand the next test a working environment back.
    const baselineProviderSaml: Record<string, unknown> = {
      ...(saml.domains.providerApplication.settings?.saml ?? {}),
    };
    const baselineIdpConfig: Record<string, unknown> = JSON.parse(saml.domains.samlIdp.configuration);

    const setSamlSettings = async (samlSettings: Record<string, unknown>): Promise<void> => {
      await waitForSyncAfter(
        providerDomainId,
        () => patchApplication(providerDomainId, accessToken, { settings: { saml: samlSettings } }, providerAppId),
        SYNC_OPTS,
      );
    };

    const setSamlIdpConfig = async (configPatch: Record<string, unknown>): Promise<void> => {
      const current = JSON.parse(saml!.domains.samlIdp.configuration);
      const merged = { ...current, ...configPatch };
      await waitForSyncAfter(
        clientDomainId,
        () =>
          updateIdp(
            clientDomainId,
            accessToken,
            {
              name: saml!.domains.samlIdp.name,
              // `type` is mandatory on update — omitting it fails with "[type: must not be blank]".
              type: saml!.domains.samlIdp.type,
              configuration: JSON.stringify(merged),
            },
            saml!.domains.samlIdp.id,
          ),
        SYNC_OPTS,
      );
    };

    const attemptLogin = async (username: string, password: string) => saml!.login(username, password);

    const findFederatedUsers = async (query?: string): Promise<User[]> => {
      const page = query ? await listUsers(clientDomainId, accessToken, query) : await getAllUsers(clientDomainId, accessToken);
      return page.data ?? [];
    };

    const authenticate = async (username: string, password: string): Promise<void> => {
      const loginResponse = await saml!.login(username, password);
      const authCode = await saml!.expectRedirectToClient(loginResponse);
      expect(authCode).toEqual(expect.any(String));
    };

    const getOnlyFederatedUser = async (): Promise<User> => {
      const users = await findFederatedUsers();
      expect(users).toHaveLength(1);
      return users[0];
    };

    const clearFederatedUsers = async (): Promise<void> => {
      const users = await findFederatedUsers();
      for (const user of users) {
        await deleteUser(clientDomainId, accessToken, user.id);
      }
    };

    /**
     * Restore both sides of the SAML wiring to the configuration captured at setup.
     * Scenarios that deliberately break the config must call this so the next test
     * starts from a working environment (guidelines: tests run in any order).
     */
    const resetToBaseline = async (): Promise<void> => {
      await setSamlSettings(baselineProviderSaml);
      await setSamlIdpConfig(baselineIdpConfig);
    };

    const cleanup = async () => {
      if (saml) {
        await saml.cleanup();
      }
      if (provider) {
        await safeDeleteDomain(provider.domain.id, provider.accessToken);
      }
    };

    return {
      saml,
      accessToken,
      setSamlSettings,
      setSamlIdpConfig,
      authenticate,
      attemptLogin,
      findFederatedUsers,
      getOnlyFederatedUser,
      clearFederatedUsers,
      resetToBaseline,
      cleanup,
    };
  } catch (error) {
    // Cleanup on setup failure so a partial environment does not leak.
    try {
      if (saml) {
        await saml.cleanup();
      }
      if (provider) {
        await safeDeleteDomain(provider.domain.id, provider.accessToken);
      }
    } catch (cleanupError) {
      console.error('Failed to cleanup SAML mapping fixture after setup failure:', cleanupError);
    }
    throw error;
  }
};

export { TEST_USER };
