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

import { spawnSync } from 'child_process';
import path from 'path';
import { Domain } from '@management-models/Domain';
import {
  createDomain,
  DomainOidcConfig,
  safeDeleteDomain,
  startDomain,
  waitForDomainStart,
} from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { Fixture } from '../../../test-fixture';

const ORG_ID = process.env.AM_DEF_ORG_ID;
const ENV_ID = process.env.AM_DEF_ENV_ID;

const SPIRE_TRUST_DOMAIN = 'am.local';
const SPIRE_JWKS_URL = 'http://spire-oidc:8443/keys';

function managementUrl(path: string): string {
  return `${process.env.AM_MANAGEMENT_URL}/management/organizations/${ORG_ID}/environments/${ENV_ID}${path}`;
}

export interface SpiffePrefixFixture extends Fixture {
  domain: Domain;
  oidc: DomainOidcConfig;
  clientId: string;
  blueprintAppId: string;
  /** Configured `spiffe.subject` for the blueprint app — SVIDs under this are accepted. */
  prefixSubject: string;
  /** Fetch a JWT-SVID from the local SPIRE agent for the given SPIFFE ID + audience. */
  fetchSvid: (spiffeId: string, audience: string) => string;
  cleanUp: () => Promise<void>;
}

/**
 * Sets up domain + trust-domain + HOSTED_DELEGATED agent app wired for SPIFFE
 * with `subjectMatchMode=PREFIX`. Assumes the SPIRE compose overlay is up
 * (`docker compose ... -f dev/docker-compose.spire.yml up -d`).
 */
export const setupSpiffePrefixFixture = async (): Promise<SpiffePrefixFixture> => {
  const accessToken = await requestAdminAccessToken();
  let domain: Domain | null = null;

  try {
    domain = await createDomain(accessToken, uniqueName('spiffe-prefix', true), 'GMA-329 SPIFFE prefix matching');

    // 1. Enable SPIFFE auth on the domain. SPIRE OIDC discovery is served over plain HTTP
    //    on the docker network, so we relax both gates.
    const patchDomainResp = await fetch(managementUrl(`/domains/${domain.id}`), {
      method: 'PATCH',
      headers: { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        oidc: {
          spiffeSettings: {
            enabled: true,
            allowPrivateIpAddress: true,
            allowUnsecuredHttpUri: true,
          },
        },
      }),
    });
    if (!patchDomainResp.ok) {
      throw new Error(`Failed to enable SPIFFE on domain: ${patchDomainResp.status} ${await patchDomainResp.text()}`);
    }

    // 2. Register the SPIRE trust domain via JWKS_URL bundle source.
    const trustDomainResp = await fetch(managementUrl(`/domains/${domain.id}/trust-domains`), {
      method: 'POST',
      headers: { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: SPIRE_TRUST_DOMAIN,
        bundleSource: 'JWKS_URL',
        jwksUrl: SPIRE_JWKS_URL,
      }),
    });
    if (!trustDomainResp.ok) {
      throw new Error(`Failed to register trust domain: ${trustDomainResp.status} ${await trustDomainResp.text()}`);
    }

    // 3. Create the blueprint agent application.
    const createAppResp = await fetch(managementUrl(`/domains/${domain.id}/applications`), {
      method: 'POST',
      headers: { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: uniqueName('agent-hosted-delegated', true),
        type: 'AGENT',
        kind: 'HOSTED_DELEGATED',
      }),
    });
    if (!createAppResp.ok) {
      throw new Error(`Failed to create blueprint app: ${createAppResp.status} ${await createAppResp.text()}`);
    }
    const blueprintApp = await createAppResp.json();
    const clientId = blueprintApp.settings.oauth.clientId;

    // 4. Switch the app to spiffe_jwt with a PREFIX subject under the bootstrap SPIRE
    //    entry `spiffe://am.local/agent/test/sample`.
    const prefixSubject = 'spiffe://am.local/agent/test';
    const patchAppResp = await fetch(managementUrl(`/domains/${domain.id}/applications/${blueprintApp.id}`), {
      method: 'PATCH',
      headers: { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        settings: {
          oauth: { tokenEndpointAuthMethod: 'spiffe_jwt' },
          spiffe: {
            trustDomain: SPIRE_TRUST_DOMAIN,
            subject: prefixSubject,
            subjectMatchMode: 'PREFIX',
          },
        },
      }),
    });
    if (!patchAppResp.ok) {
      throw new Error(`Failed to configure SPIFFE app settings: ${patchAppResp.status} ${await patchAppResp.text()}`);
    }

    const startedDomain = await startDomain(domain.id, accessToken);
    const started = await waitForDomainStart(startedDomain);

    return {
      accessToken,
      domain,
      oidc: started.oidcConfig,
      clientId,
      blueprintAppId: blueprintApp.id,
      prefixSubject,
      fetchSvid: (spiffeId, audience) => fetchSvidFromSpireAgent(spiffeId, audience),
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
      } catch (e) {
        console.error('Cleanup failed:', e);
      }
    }
    throw error;
  }
};

/**
 * Shells out to the SPIRE agent inside the local-stack to mint a JWT-SVID.
 * Mirrors the convenience script at docker/local-stack/dev/spire/scripts/issue-svid.sh.
 */
function fetchSvidFromSpireAgent(spiffeId: string, audience: string): string {
  const repoRoot = path.resolve(__dirname, '..', '..', '..', '..', '..');
  const composeBase = path.join(repoRoot, 'docker/local-stack/dev/docker-compose.yml');
  const composeSpire = path.join(repoRoot, 'docker/local-stack/dev/docker-compose.spire.yml');

  const result = spawnSync(
    'docker',
    [
      'compose',
      '-f',
      composeBase,
      '-f',
      composeSpire,
      'exec',
      '-T',
      'spire-agent',
      '/opt/spire/bin/spire-agent',
      'api',
      'fetch',
      'jwt',
      '-socketPath',
      '/run/spire-agent/public/api.sock',
      '-audience',
      audience,
      '-spiffeID',
      spiffeId,
    ],
    { encoding: 'utf8' },
  );
  if (result.status !== 0) {
    throw new Error(`spire-agent fetch jwt failed (exit ${result.status}) for ${spiffeId}: ${result.stderr || result.stdout}`);
  }

  // Output format (relevant lines):
  //   token(spiffe://am.local/agent/test/sample):
  //       eyJhbGciOi...
  const lines = result.stdout.split('\n').map((l) => l.trim());
  const headerIdx = lines.findIndex((l) => l.startsWith('token('));
  if (headerIdx < 0 || headerIdx + 1 >= lines.length || !lines[headerIdx + 1]) {
    throw new Error(`Unable to parse SVID from spire-agent output:\n${result.stdout}`);
  }
  return lines[headerIdx + 1];
}
