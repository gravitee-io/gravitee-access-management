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

import { getApplicationApi, getDomainApi, getEntrypointsApi } from '@management-commands/service/utils';
import { waitForDomainStart } from '@management-commands/domain-management-commands';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { sendCockpitCommand } from '@cloud-commands/cockpit-commands';
import { bindSafeDeleteCloudDomain } from '@cloud-commands/domain-commands';
import { retryUntil } from '@utils-commands/retry';
import { uniqueName } from '@utils-commands/misc';
import { setupCloudSharedFixture } from './cloud-shared-fixture';

const POLL = { timeoutMillis: 30000, intervalMillis: 1000 };
const REDIRECT_URI = 'https://callback.example.com';
const PATH_LOGIN_CREDENTIALS = '/webauthn/login/credentials';
const CONFIGURED_RELYING_PARTY_ID = 'configured.example.com';

const urlFor = (host: string) => `https://${host}`;

export interface CloudWebAuthnFixture {
  organizationId: string;
  environmentId: string;
  domainId: string;
  /** Cockpit's own access point for this environment. */
  generatedHost: string;
  /** The customer's overriding access point, which entrypoint resolution prefers. */
  overridingHost: string;
  /** A globally-unique gateway host, safe to reuse across parallel runs. */
  uniqueHost: () => string;
  /** The relying party id the second domain configured for itself. */
  configuredRelyingPartyId: string;
  /**
   * Assertion options from POST /webauthn/login/credentials, optionally as seen from a given host.
   * The registration endpoint carries the same relying party id but demands an authenticated session,
   * so it is left to the unit tests.
   */
  assertionOptions: (forwardedHost?: string) => Promise<any>;
  /** As {@link assertionOptions}, for the domain that configured its own relying party id. */
  configuredAssertionOptions: (forwardedHost?: string) => Promise<any>;
  /** Re-issue the ENVIRONMENT command with a new set of gateway access points. */
  resyncAccessPoints: (hosts: { generated: string; overriding: string }) => Promise<void>;
  cleanup: () => Promise<void>;
}

/**
 * A managed-cloud environment with two gateway access points and two passwordless domains deployed in it,
 * so the relying party the gateway advertises can be observed per request.
 * <p>
 * Both domains seed a deliberately wrong origin, so the environment entrypoint has to be what wins and a
 * domain with nothing configured cannot pass the assertions for the wrong reason. They differ in the one
 * thing under test: the first leaves the relying party id unset for the entrypoint to supply, the second
 * sets its own and must keep it.
 *
 * Managed-cloud stack only (local-stack.sh --cloud).
 */
export const setupCloudWebAuthnFixture = async (): Promise<CloudWebAuthnFixture> => {
  const shared = await setupCloudSharedFixture();
  const { organizationId, environmentId, accessToken, dataPlaneId } = shared;
  // Lowercased: uniqueName capitalises a word, and a host is case-insensitive, so the browser lowercases
  // it when it parses the origin. The relying party id has to match that, not the stored spelling.
  const uniqueHost = () => `${uniqueName('gw', true)}.example.com`.toLowerCase();

  let generatedHost = uniqueHost();
  let overridingHost = uniqueHost();

  const resyncAccessPoints = async (hosts: { generated: string; overriding: string }): Promise<void> => {
    generatedHost = hosts.generated;
    overridingHost = hosts.overriding;
    await sendCockpitCommand({
      type: 'ENVIRONMENT',
      payload: {
        id: environmentId,
        organizationId,
        hrids: [environmentId],
        name: 'AM7231 cloud webauthn env',
        accessPoints: [
          { target: 'GATEWAY', host: generatedHost, overriding: false },
          { target: 'GATEWAY', host: overridingHost, overriding: true },
        ],
      },
    });
    await retryUntil(
      () => getEntrypointsApi(accessToken).listEntrypoints({ organizationId }),
      (entrypoints: any[]) => [generatedHost, overridingHost].every((host) => entrypoints.some((e) => e.url === urlFor(host))),
      POLL,
    );
  };

  await resyncAccessPoints({ generated: generatedHost, overriding: overridingHost });

  const domainApi = getDomainApi(accessToken);
  const gatewayUrl = process.env.AM_GATEWAY_URL;

  // Passwordless and the WebAuthn settings go on before the domain starts, so the initial sync picks
  // everything up in one pass and no patch lands on a running domain.
  const passwordlessDomain = async (namePrefix: string, webAuthnSettings: Record<string, string>) => {
    const domain = await domainApi.createDomain({
      organizationId,
      environmentId,
      newDomain: { name: uniqueName(namePrefix, true), dataPlaneId },
    });
    await domainApi.patchDomain({
      organizationId,
      environmentId,
      domain: domain.id,
      patchDomain: { loginSettings: { inherited: false, passwordlessEnabled: true }, webAuthnSettings },
    });

    // Through the API directly rather than createTestApp: that helper hardcodes the default
    // organization/environment, and this domain lives in the environment Cockpit just provisioned.
    const clientId = uniqueName(`${namePrefix}-app`, true);
    const application = await getApplicationApi(accessToken).createApplication({
      organizationId,
      environmentId,
      domain: domain.id,
      newApplication: { name: clientId, type: 'WEB', clientId, redirectUris: [REDIRECT_URI] },
    });

    await domainApi.patchDomain({ organizationId, environmentId, domain: domain.id, patchDomain: { enabled: true } });
    // waitForDomainStart, not waitForDomainReady: the latter only reports sync, and the gateway rebuilds its
    // routes asynchronously after that, so the first request can land in a 404 window.
    await waitForDomainStart(domain);

    const assertionOptions = async (forwardedHost?: string) => {
      // X-Forwarded-Host rather than Host: supertest owns the Host header, and the gateway resolves the
      // public origin from the forwarding headers first anyway.
      const headers = {
        'Content-Type': 'application/json',
        ...(forwardedHost ? { 'X-Forwarded-Host': forwardedHost, 'X-Forwarded-Proto': 'https' } : {}),
      };
      const response = await performPost(
        gatewayUrl,
        `/${domain.hrid}${PATH_LOGIN_CREDENTIALS}?client_id=${application.settings.oauth.clientId}`,
        { name: uniqueName('wa-user', true) },
        headers,
      );
      if (response.status !== 200) {
        throw new Error(`${PATH_LOGIN_CREDENTIALS} returned ${response.status}: ${response.text}`);
      }
      return JSON.parse(response.text);
    };

    return { domain, assertionOptions };
  };

  // Origin deliberately wrong and no relying party id at all, so the entrypoint is the only thing that can
  // supply one and a passing assertion cannot mean "it was already right".
  const derived = await passwordlessDomain('wa-entrypoint-domain', {
    origin: 'http://localhost:8092',
    relyingPartyName: 'AM7231',
  });

  // A relying party id the domain set deliberately. Credentials are bound to it, so the entrypoint must
  // leave it alone or every already-registered authenticator stops offering them.
  const configured = await passwordlessDomain('wa-configured-domain', {
    origin: 'http://localhost:8092',
    relyingPartyId: CONFIGURED_RELYING_PARTY_ID,
    relyingPartyName: 'AM7231',
  });

  // The entrypoints this fixture provisions are deliberately left behind: AM-7228 makes them read-only
  // in a managed installation, so deleting them can only ever return 400. The hosts are unique per run.
  const deleteDomain = bindSafeDeleteCloudDomain({ accessToken, organizationId, environmentId });
  const cleanup = async () => {
    await Promise.all([derived, configured].map(({ domain }) => deleteDomain(domain.id)));
  };

  return {
    organizationId,
    environmentId,
    domainId: derived.domain.id,
    get generatedHost() {
      return generatedHost;
    },
    get overridingHost() {
      return overridingHost;
    },
    uniqueHost,
    configuredRelyingPartyId: CONFIGURED_RELYING_PARTY_ID,
    assertionOptions: derived.assertionOptions,
    configuredAssertionOptions: configured.assertionOptions,
    resyncAccessPoints,
    cleanup,
  };
};
