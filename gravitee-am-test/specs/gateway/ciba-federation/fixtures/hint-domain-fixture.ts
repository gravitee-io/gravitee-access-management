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
  waitForDomainStart,
} from '@management-commands/domain-management-commands';
import { createIdp } from '@management-commands/idp-management-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { retryUntil } from '@utils-commands/retry';
import { uniqueName } from '@utils-commands/misc';
import { Domain } from '@management-models/Domain';
import { IdentityProvider } from '@management-models/IdentityProvider';
import { Fixture } from '../../../test-fixture';
import { TargetDomainFixture } from './target-domain-fixture';

const CIBA_GRANT_TYPE = 'urn:openid:params:grant-type:ciba';
const CIBA_TOKEN_POLL_INTERVAL_MS = 5500;
const CIBA_TOKEN_POLL_TIMEOUT_MS = 90000;

export interface HintDomainFixture extends Fixture {
  domain: Domain;
  oidcConfig: any;
  clientId: string;
  clientSecret: string;
  federationIdp: IdentityProvider;
  initiateCiba: (loginHint: string) => Promise<any>;
  pollToken: (authReqId: string) => Promise<any>;
  pollTokenUntilGranted: (authReqId: string) => Promise<any>;
}

const isCibaTokenGrantedResponse = (res: any): boolean => {
  if (res.status === 200 && res.body?.access_token) {
    return true;
  }
  if (res.status === 400) {
    const err = res.body?.error;
    if (err === 'authorization_pending' || err === 'slow_down') {
      return false;
    }
  }
  throw new Error(`Unexpected CIBA token response: HTTP ${res.status} ${JSON.stringify(res.body)}`);
};

async function createCallbackApplication(domainId: string, accessToken: string): Promise<{ clientId: string; clientSecret: string }> {
  const created = await createApplication(domainId, accessToken, {
    name: uniqueName('ciba-fed-callback', true),
    type: 'WEB',
    redirectUris: ['https://callback.example.com/callback'],
  });
  const clientSecret: string = created.settings.oauth.clientSecret;
  const updated = await updateApplication(
    domainId,
    accessToken,
    {
      settings: {
        oauth: {
          redirectUris: ['https://callback.example.com/callback'],
          // GatewayCallbackClient authenticates with client_id/client_secret as form params (client_secret_post).
          tokenEndpointAuthMethod: 'client_secret_post',
        },
      },
    },
    created.id,
  );
  return { clientId: updated.settings.oauth.clientId, clientSecret };
}

async function createHintApplication(domainId: string, accessToken: string): Promise<{ clientId: string; clientSecret: string }> {
  const created = await createApplication(domainId, accessToken, {
    name: uniqueName('ciba-fed-hint-client', true),
    type: 'WEB',
    redirectUris: ['https://client.example.com/callback'],
  });
  const clientSecret: string = created.settings.oauth.clientSecret;
  const updated = await updateApplication(
    domainId,
    accessToken,
    {
      settings: {
        oauth: {
          redirectUris: ['https://client.example.com/callback'],
          grantTypes: ['authorization_code', CIBA_GRANT_TYPE],
          responseTypes: ['code'],
          tokenEndpointAuthMethod: 'client_secret_basic',
          scopeSettings: [{ scope: 'openid', defaultScope: true }],
        },
      },
    },
    created.id,
  );
  return { clientId: updated.settings.oauth.clientId, clientSecret };
}

export const setupHintDomain = async (accessToken: string, targetDomain: TargetDomainFixture): Promise<HintDomainFixture> => {
  let domain: Domain | null = null;

  try {
    domain = await createDomain(accessToken, uniqueName('ciba-fed-hint', true), 'CIBA federation hint domain');
    expect(domain.id).toBeDefined();

    // OIDC connection material used by the federation notifier to reach the target domain — not used for browser login.
    const federationIdp = await createIdp(domain.id, accessToken, {
      name: 'ciba-federation-target',
      type: 'oauth2-generic-am-idp',
      external: true,
      configuration: JSON.stringify({
        clientId: targetDomain.resourceApp.clientId,
        clientSecret: targetDomain.resourceApp.clientSecret,
        clientAuthenticationMethod: 'client_secret_post',
        wellKnownUri: `${process.env.AM_INTERNAL_GATEWAY_URL}/${targetDomain.domain.hrid}/oidc/.well-known/openid-configuration`,
        responseType: 'code',
        responseMode: 'default',
        scopes: ['openid'],
        connectTimeout: 10000,
        idleTimeout: 10000,
        maxPoolSize: 200,
      }),
    });

    const callbackApp = await createCallbackApplication(domain.id, accessToken);

    const notifierResponse = await performPost(
      `${process.env.AM_MANAGEMENT_URL}/management/organizations/DEFAULT/environments/DEFAULT/domains/${domain.id}/auth-device-notifiers`,
      '',
      {
        type: 'ciba-federation-am-authdevice-notifier',
        name: 'ciba-federation-notifier',
        configuration: JSON.stringify({
          identityProviderId: federationIdp.id,
          callbackClientId: callbackApp.clientId,
          callbackClientSecret: callbackApp.clientSecret,
        }),
      },
      { 'Content-type': 'application/json', Authorization: `Bearer ${accessToken}` },
    );
    expect(notifierResponse.status).toBe(201);
    const federationNotifierId = notifierResponse.body.id;

    await patchDomain(domain.id, accessToken, {
      oidc: {
        cibaSettings: {
          enabled: true,
          authReqExpiry: 600,
          tokenReqInterval: 5,
          bindingMessageLength: 256,
          deviceNotifiers: [{ id: federationNotifierId }],
        },
      },
    });

    const { clientId, clientSecret } = await createHintApplication(domain.id, accessToken);

    await startDomain(domain.id, accessToken);
    const startedDomain = await waitForDomainStart(domain);

    const cibaEndpoint = `${process.env.AM_GATEWAY_URL}/${startedDomain.domain.hrid}/oidc/ciba/authenticate`;
    const tokenEndpoint = startedDomain.oidcConfig.token_endpoint;
    const basicAuth = `Basic ${Buffer.from(`${clientId}:${clientSecret}`).toString('base64')}`;

    const initiateCiba = async (loginHint: string): Promise<any> => {
      const params = new URLSearchParams({ scope: 'openid', login_hint: loginHint, client_id: clientId });
      return performPost(cibaEndpoint, '', params.toString(), {
        'Content-Type': 'application/x-www-form-urlencoded',
        Authorization: basicAuth,
      });
    };

    const pollToken = async (authReqId: string): Promise<any> => {
      const params = new URLSearchParams({ client_id: clientId });
      return performPost(`${tokenEndpoint}?auth_req_id=${authReqId}&grant_type=${CIBA_GRANT_TYPE}`, '', params.toString(), {
        'Content-Type': 'application/x-www-form-urlencoded',
        Authorization: basicAuth,
      });
    };

    const pollTokenUntilGranted = (authReqId: string): Promise<any> =>
      retryUntil(() => pollToken(authReqId), isCibaTokenGrantedResponse, {
        timeoutMillis: CIBA_TOKEN_POLL_TIMEOUT_MS,
        intervalMillis: CIBA_TOKEN_POLL_INTERVAL_MS,
      });

    return {
      domain: startedDomain.domain,
      oidcConfig: startedDomain.oidcConfig,
      clientId,
      clientSecret,
      federationIdp,
      initiateCiba,
      pollToken,
      pollTokenUntilGranted,
      cleanUp: async () => {
        await safeDeleteDomain(domain?.id, accessToken);
      },
    };
  } catch (error) {
    if (domain?.id) {
      await safeDeleteDomain(domain.id, accessToken);
    }
    throw error;
  }
};
