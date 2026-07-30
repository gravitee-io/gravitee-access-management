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
import { retryUntil } from '@utils-commands/retry';
import { uniqueName } from '@utils-commands/misc';

const POLL = { timeoutMillis: 30000, intervalMillis: 1000 };
const DATA_PLANE_ID = process.env.AM_DOMAIN_DATA_PLANE_ID || 'default';
const REDIRECT_URI = 'https://callback.example.com';

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
  /**
   * Assertion options from POST /webauthn/login/credentials, optionally as seen from a given host.
   * The registration endpoint carries the same relying party id but demands an authenticated session,
   * so it is left to the unit tests.
   */
  assertionOptions: (forwardedHost?: string) => Promise<any>;
  /** Re-issue the ENVIRONMENT command with a new set of gateway access points. */
  resyncAccessPoints: (hosts: { generated: string; overriding: string }) => Promise<void>;
  cleanup: () => Promise<void>;
}

/**
 * A managed-cloud environment with two gateway access points and a passwordless domain deployed in it,
 * so the relying party the gateway advertises can be observed per request.
 * <p>
 * The domain's own WebAuthn settings are deliberately seeded with the wrong host: in cloud the
 * environment entrypoint has to win over them, and a domain with nothing configured would pass the
 * assertions for the wrong reason.
 *
 * Managed-cloud stack only (local-stack.sh --cloud).
 */
export const setupCloudWebAuthnFixture = async (accessToken: string): Promise<CloudWebAuthnFixture> => {
  const organizationId = process.env.AM_DEF_ORG_ID;
  const environmentId = uniqueName('env-wa', true);
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

  // Passwordless and the WebAuthn settings go on before the domain starts, so the initial sync picks
  // everything up in one pass and no patch lands on a running domain.
  const domainApi = getDomainApi(accessToken);
  const domain = await domainApi.createDomain({
    organizationId,
    environmentId,
    newDomain: { name: uniqueName('wa-entrypoint-domain', true), dataPlaneId: DATA_PLANE_ID },
  });
  await domainApi.patchDomain({
    organizationId,
    environmentId,
    domain: domain.id,
    patchDomain: {
      loginSettings: { inherited: false, passwordlessEnabled: true },
      webAuthnSettings: { origin: 'http://localhost:8092', relyingPartyId: 'localhost', relyingPartyName: 'AM7231' },
    },
  });

  // Through the API directly rather than createTestApp: that helper hardcodes the default
  // organization/environment, and this domain lives in the environment Cockpit just provisioned.
  const clientId = uniqueName('wa-entrypoint-app', true);
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

  const gatewayUrl = process.env.AM_GATEWAY_URL;
  const options = async (path: string, body: any, forwardedHost?: string) => {
    // X-Forwarded-Host rather than Host: supertest owns the Host header, and the gateway resolves the
    // public origin from the forwarding headers first anyway.
    const headers = {
      'Content-Type': 'application/json',
      ...(forwardedHost ? { 'X-Forwarded-Host': forwardedHost, 'X-Forwarded-Proto': 'https' } : {}),
    };
    const response = await performPost(
      gatewayUrl,
      `/${domain.hrid}${path}?client_id=${application.settings.oauth.clientId}`,
      body,
      headers,
    );
    if (response.status !== 200) {
      throw new Error(`${path} returned ${response.status}: ${response.text}`);
    }
    return JSON.parse(response.text);
  };

  // The entrypoints this fixture provisions are deliberately left behind: AM-7228 makes them read-only
  // in a managed installation, so deleting them can only ever return 400. The hosts are unique per run.
  const cleanup = async () => {
    await domainApi
      .deleteDomain({ organizationId, environmentId, domain: domain.id })
      .catch((e) => console.warn(`cleanup: failed to delete domain ${domain.id}: ${e.message}`));
  };

  return {
    organizationId,
    environmentId,
    domainId: domain.id,
    get generatedHost() {
      return generatedHost;
    },
    get overridingHost() {
      return overridingHost;
    },
    uniqueHost,
    assertionOptions: (forwardedHost?: string) =>
      options('/webauthn/login/credentials', { name: uniqueName('wa-user', true) }, forwardedHost),
    resyncAccessPoints,
    cleanup,
  };
};
