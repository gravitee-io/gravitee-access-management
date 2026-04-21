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

import crypto from 'crypto';
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

function managementUrl(path: string): string {
  return `${process.env.AM_MANAGEMENT_URL}/management/organizations/${ORG_ID}/environments/${ENV_ID}${path}`;
}

export interface WorkloadJwtFixture extends Fixture {
  domain: Domain;
  oidc: DomainOidcConfig;
  blueprintClientId: string;
  blueprintAppId: string;
  privateKey: crypto.KeyObject;
  kid: string;
  cleanUp: () => Promise<void>;
}

export const setupWorkloadJwtFixture = async (): Promise<WorkloadJwtFixture> => {
  const accessToken = await requestAdminAccessToken();
  let domain: Domain | null = null;

  try {
    domain = await createDomain(accessToken, uniqueName('workload-jwt', true), 'Workload-JWT assertion test');

    // Create AUTONOMOUS blueprint application
    const createResponse = await fetch(managementUrl(`/domains/${domain.id}/applications`), {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${accessToken}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        name: uniqueName('agent-autonomous', true),
        type: 'SERVICE',
        agentIdentityMode: true,
        agentSettings: { agentType: 'AUTONOMOUS' },
      }),
    });
    if (!createResponse.ok) {
      throw new Error(`Failed to create blueprint app: ${createResponse.status} ${await createResponse.text()}`);
    }
    const blueprintApp = await createResponse.json();
    const blueprintClientId = blueprintApp.settings.oauth.clientId;

    // Generate RSA keypair
    const { publicKey, privateKey } = crypto.generateKeyPairSync('rsa', { modulusLength: 2048 });
    const kid = uniqueName('agent-key', false);

    // Export public key as JWK and add to blueprint's agent JWKS
    const publicJwk = publicKey.export({ format: 'jwk' });
    const agentJwk = {
      kty: publicJwk.kty,
      n: publicJwk.n,
      e: publicJwk.e,
      kid,
      use: 'sig',
      alg: 'RS256',
    };

    const addKeyResponse = await fetch(managementUrl(`/domains/${domain.id}/applications/${blueprintApp.id}`), {
      method: 'PATCH',
      headers: {
        Authorization: `Bearer ${accessToken}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        settings: {
          oauth: {
            jwks: { keys: [agentJwk] },
            tokenEndpointAuthMethod: 'private_key_jwt',
          },
        },
      }),
    });
    if (!addKeyResponse.ok) {
      throw new Error(`Failed to register agent JWKS: ${addKeyResponse.status} ${await addKeyResponse.text()}`);
    }

    // Start domain
    const startedDomain = await startDomain(domain.id, accessToken);
    const started = await waitForDomainStart(startedDomain);

    return {
      accessToken,
      domain,
      oidc: started.oidcConfig,
      blueprintClientId,
      blueprintAppId: blueprintApp.id,
      privateKey,
      kid,
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
