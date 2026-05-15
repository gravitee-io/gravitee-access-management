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
  createDomain,
  patchDomain,
  safeDeleteDomain,
  startDomain,
  waitForDomainStart,
  waitForOidcReady,
} from '@management-commands/domain-management-commands';
import {
  createApplication,
  patchApplication,
  updateApplication,
} from '@management-commands/application-management-commands';
import { createUser } from '@management-commands/user-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { retryUntil } from '@utils-commands/retry';
import { uniqueName } from '@utils-commands/misc';
import { Application } from '@management-models/Application';
import { Domain } from '@management-models/Domain';
import { Fixture } from '../../../test-fixture';
import { CIMD_PRIVATE_KEY_JWT_KID, cimdPrivateKeyJwtPrivateJwk } from '@api-fixtures/cimd-private-key-jwt';
import * as jose from 'jose';
import crypto from 'crypto';

const CLIENT_ASSERTION_TYPE = 'urn:ietf:params:oauth:client-assertion-type:jwt-bearer';
const CIBA_GRANT_TYPE = 'urn:openid:params:grant-type:ciba';
const CIBA_TOKEN_POLL_INTERVAL_MS = 5500;
const CIBA_TOKEN_POLL_TIMEOUT_MS = 90000;

export const CIMD_HOSTED_DELEGATED_CIBA_URL = 'http://wiremock:8080/cimd/ENABLED_BASE/agent-hosted-delegated-ciba';

const TEST_USER = {
  username: 'agent-ciba-user',
  password: 'AgentCibaP@ssw0rd123!',
  firstName: 'AgentCiba',
  lastName: 'User',
  email: 'agent.ciba.user@test.com',
};

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

const pollCibaTokenUntilGranted = (fetchToken: () => Promise<any>): Promise<any> =>
  retryUntil(fetchToken, isCibaTokenGrantedResponse, {
    timeoutMillis: CIBA_TOKEN_POLL_TIMEOUT_MS,
    intervalMillis: CIBA_TOKEN_POLL_INTERVAL_MS,
  });

export interface AgentCibaApp {
  id: string;
  clientId: string;
  clientSecret?: string;
}

export interface AgentCibaFixture extends Fixture {
  domain: Domain;
  accessToken: string;
  oidcConfig: any;
  cibaEndpoint: string;
  user: any;
  control: AgentCibaApp;
  hostedDelegatedBasic: AgentCibaApp;
  hostedDelegatedPrivateKeyJwt: AgentCibaApp;
  /** Null if mgmt API rejected creation/patching of CIBA grant on USER_EMBEDDED. */
  userEmbeddedNone: AgentCibaApp | null;
  userEmbeddedCreateError: any | null;
  /** Null if mgmt API rejected creation/patching of CIBA grant on AUTONOMOUS. */
  autonomousBasic: AgentCibaApp | null;
  autonomousCreateError: any | null;
  /** Null if CIMD-based HOSTED_DELEGATED creation failed. */
  cimdHostedDelegated: AgentCibaApp | null;
  cimdHostedDelegatedCreateError: any | null;
  initiateCiba: (clientId: string, loginHint: string, clientSecret?: string, requestJwt?: string) => Promise<any>;
  initiateCibaWithPrivateKeyJwt: (clientId: string, loginHint: string, requestJwt?: string) => Promise<any>;
  pollToken: (authReqId: string, clientId: string, clientSecret?: string) => Promise<any>;
  pollTokenUntilGranted: (authReqId: string, clientId: string, clientSecret?: string) => Promise<any>;
  pollTokenWithPrivateKeyJwt: (authReqId: string, clientId: string) => Promise<any>;
  pollTokenWithPrivateKeyJwtUntilGranted: (authReqId: string, clientId: string) => Promise<any>;
  createRequestJwt: (clientId: string, loginHint: string) => Promise<string>;
  switchNotifier: (target: 'accept-all' | 'reject-all') => Promise<void>;
  /**
   * The delegated CIBA service stores a single registration per domain (last-write-wins).
   * Call this from each test before initiating CIBA to make sure the callback authenticates as the expected client.
   */
  registerClientWithDelegatedService: (clientId: string, clientSecret?: string) => Promise<void>;
}

const createPrivateKeyJwtAssertion = async (clientId: string, audience: string): Promise<string> => {
  const privateKey = await jose.importJWK(cimdPrivateKeyJwtPrivateJwk as jose.JWK, 'RS256');
  const now = Math.floor(Date.now() / 1000);
  return new jose.SignJWT({})
    .setProtectedHeader({ alg: 'RS256', kid: CIMD_PRIVATE_KEY_JWT_KID })
    .setIssuer(clientId)
    .setSubject(clientId)
    .setAudience(audience)
    .setJti(crypto.randomUUID())
    .setIssuedAt(now)
    .setExpirationTime(now + 300)
    .sign(privateKey);
};

const createRequestJwt = async (clientId: string, audience: string, loginHint: string): Promise<string> => {
  const privateKey = await jose.importJWK(cimdPrivateKeyJwtPrivateJwk as jose.JWK, 'RS256');
  const now = Math.floor(Date.now() / 1000);
  return new jose.SignJWT({
    scope: 'openid',
    login_hint: loginHint,
  })
    .setProtectedHeader({ alg: 'RS256', kid: CIMD_PRIVATE_KEY_JWT_KID })
    .setIssuer(clientId)
    .setAudience(audience)
    .setJti(crypto.randomUUID())
    .setIssuedAt(now)
    .setNotBefore(now)
    .setExpirationTime(now + 300)
    .sign(privateKey);
};

async function createTemplateApplication(domainId: string, accessToken: string, idpId: string): Promise<Application> {
  const created = await createApplication(domainId, accessToken, {
    name: uniqueName('agent-ciba-template', true),
    type: 'WEB',
    redirectUris: ['https://client.example.com/callback'],
  });
  const updated = await updateApplication(
    domainId,
    accessToken,
    {
      settings: {
        oauth: {
          redirectUris: ['https://client.example.com/callback'],
          grantTypes: ['authorization_code', CIBA_GRANT_TYPE],
          responseTypes: ['code'],
          scopeSettings: [{ scope: 'openid', defaultScope: true }],
        },
        advanced: { skipConsent: true },
      },
      identityProviders: new Set([{ identity: idpId, priority: 0 }]),
    },
    created.id,
  );
  await patchApplication(domainId, accessToken, { template: true }, created.id);
  return updated;
}

async function managementFetch(url: string, init: any, accessToken: string): Promise<{ status: number; body: any }> {
  const res = await fetch(url, {
    ...init,
    headers: {
      ...(init.headers || {}),
      Authorization: `Bearer ${accessToken}`,
    },
  });
  const text = await res.text();
  let body: any = undefined;
  if (text) {
    try {
      body = JSON.parse(text);
    } catch {
      body = text;
    }
  }
  return { status: res.status, body };
}

async function createNotifier(
  managementUrl: string,
  domainId: string,
  accessToken: string,
  internalCibaUrl: string,
  endpointSuffix: 'accept-all' | 'reject-all',
): Promise<string> {
  const response = await performPost(
    `${managementUrl}${domainId}/auth-device-notifiers`,
    '',
    {
      type: 'http-am-authdevice-notifier',
      configuration: `{"endpoint":"${internalCibaUrl}/notify/${endpointSuffix}","headerName":"Authorization","connectTimeout":5000,"idleTimeout":10000,"maxPoolSize":10}`,
      name: `${endpointSuffix} notifier`,
    },
    {
      'Content-type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
  );
  expect(response.status).toBe(201);
  return response.body.id;
}

async function registerWithDelegatedService(
  cibaUrl: string,
  accessToken: string,
  domainId: string,
  gatewayCallback: string,
  clientId: string,
  clientSecret: string,
): Promise<void> {
  const res = await performPost(
    `${cibaUrl}/domains`,
    '',
    {
      domainId,
      domainCallback: gatewayCallback,
      clientId: encodeURIComponent(clientId),
      clientSecret: clientSecret ?? '',
    },
    {
      'Content-type': 'application/json',
      Authorization: `Bearer ${accessToken}`,
    },
  );
  expect(res.status).toBe(200);
}

async function createAgentApp(
  domainId: string,
  accessToken: string,
  managementBase: string,
  kind: 'USER_EMBEDDED' | 'HOSTED_DELEGATED' | 'AUTONOMOUS',
  tokenEndpointAuthMethod: string,
  options: { jwk?: any; signedRequest?: boolean } = {},
): Promise<{ app: AgentCibaApp | null; error: any | null }> {
  const body: any = {
    name: uniqueName(`agent-${kind.toLowerCase()}`, true),
    type: 'AGENT',
    kind,
  };
  if (kind !== 'AUTONOMOUS') {
    body.redirectUris = ['https://agent.example.com/callback'];
  }
  const createRes = await managementFetch(
    `${managementBase}/domains/${domainId}/applications`,
    { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) },
    accessToken,
  );
  if (createRes.status >= 400) {
    return { app: null, error: { phase: 'create', status: createRes.status, body: createRes.body } };
  }
  const created = createRes.body;
  // Secret is only returned on the create response — patch strips it from the echo.
  const clientSecret: string | undefined = created.settings?.oauth?.clientSecret;

  const oauthPatch: any = {
    grantTypes: Array.from(new Set([...(created.settings?.oauth?.grantTypes ?? []), CIBA_GRANT_TYPE])),
    tokenEndpointAuthMethod,
    scopeSettings: [{ scope: 'openid', defaultScope: true }],
  };
  if (options.jwk) {
    oauthPatch.jwks = { keys: [options.jwk] };
  }
  if (options.signedRequest) {
    oauthPatch.requestObjectSigningAlg = 'RS256';
  }
  const patchRes = await managementFetch(
    `${managementBase}/domains/${domainId}/applications/${created.id}`,
    {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ settings: { oauth: oauthPatch } }),
    },
    accessToken,
  );
  if (patchRes.status >= 400) {
    return { app: null, error: { phase: 'patch', status: patchRes.status, body: patchRes.body } };
  }
  return {
    app: {
      id: created.id,
      clientId: patchRes.body.settings.oauth.clientId,
      clientSecret,
    },
    error: null,
  };
}

async function createControlApp(
  domainId: string,
  accessToken: string,
  managementBase: string,
): Promise<AgentCibaApp> {
  const created = await createApplication(domainId, accessToken, {
    name: uniqueName('non-agent-ciba', true),
    type: 'WEB',
    redirectUris: ['https://client.example.com/callback'],
  });
  const clientSecret: string | undefined = (created as any).settings?.oauth?.clientSecret;
  const patchRes = await managementFetch(
    `${managementBase}/domains/${domainId}/applications/${created.id}`,
    {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        settings: {
          oauth: {
            grantTypes: ['authorization_code', CIBA_GRANT_TYPE],
            tokenEndpointAuthMethod: 'client_secret_basic',
            scopeSettings: [{ scope: 'openid', defaultScope: true }],
          },
        },
      }),
    },
    accessToken,
  );
  expect(patchRes.status).toBeLessThan(400);
  return {
    id: created.id,
    clientId: patchRes.body.settings.oauth.clientId,
    clientSecret,
  };
}

async function createCimdHostedDelegated(
  domainId: string,
  accessToken: string,
  managementBase: string,
): Promise<{ app: AgentCibaApp | null; error: any | null }> {
  const res = await managementFetch(
    `${managementBase}/domains/${domainId}/cimd/applications`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: uniqueName('cimd-agent-hd-ciba', true),
        type: 'AGENT',
        kind: 'HOSTED_DELEGATED',
        cimdUrl: CIMD_HOSTED_DELEGATED_CIBA_URL,
      }),
    },
    accessToken,
  );
  if (res.status >= 400) {
    return { app: null, error: { phase: 'create', status: res.status, body: res.body } };
  }
  return {
    app: {
      id: res.body.id,
      clientId: res.body.settings.oauth.clientId,
      clientSecret: res.body.settings.oauth.clientSecret,
    },
    error: null,
  };
}

export const setupAgentCibaFixture = async (): Promise<AgentCibaFixture> => {
  let domain: Domain | null = null;
  let accessToken: string | null = null;

  try {
    accessToken = await requestAdminAccessToken();
    expect(accessToken).toBeDefined();

    domain = await createDomain(accessToken, uniqueName('agent-ciba', true), 'CIBA × agent blueprint integration test');
    expect(domain.id).toBeDefined();

    const idpSet = await getAllIdps(domain.id, accessToken);
    const defaultIdp = idpSet.values().next().value;
    expect(defaultIdp).toBeDefined();

    const templateApplication = await createTemplateApplication(domain.id, accessToken, defaultIdp.id);

    const user = await createUser(domain.id, accessToken, {
      firstName: TEST_USER.firstName,
      lastName: TEST_USER.lastName,
      email: TEST_USER.email,
      username: TEST_USER.username,
      password: TEST_USER.password,
      client: templateApplication.id,
      source: defaultIdp.id,
      preRegistration: false,
    });
    expect(user).toBeDefined();

    const orgEnv = `${process.env.AM_MANAGEMENT_URL}/management/organizations/${process.env.AM_DEF_ORG_ID}/environments/${process.env.AM_DEF_ENV_ID}`;
    const managementUrl = `${process.env.AM_MANAGEMENT_URL}/management/organizations/DEFAULT/environments/DEFAULT/domains/`;
    const internalCibaUrl = process.env.AM_INTERNAL_CIBA_NOTIFIER_URL;
    const cibaUrl = process.env.AM_CIBA_NOTIFIER_URL;

    const acceptNotifierId = await createNotifier(managementUrl, domain.id, accessToken, internalCibaUrl, 'accept-all');
    const rejectNotifierId = await createNotifier(managementUrl, domain.id, accessToken, internalCibaUrl, 'reject-all');

    // Enable CIBA + CIMD (CIMD needed for case F). Start with accept-all notifier.
    await patchDomain(domain.id, accessToken, {
      oidc: {
        cimdSettings: {
          enabled: true,
          allowUnsecuredHttpUri: true,
          allowPrivateIpAddress: true,
          allowedDomains: [],
          fetchTimeoutMs: 1500,
          maxResponseSizeKb: 10,
          cacheTtlSeconds: 3600,
          cacheMaxEntries: 500,
          templateId: templateApplication.id,
        },
        cibaSettings: {
          enabled: true,
          authReqExpiry: 600,
          tokenReqInterval: 5,
          bindingMessageLength: 256,
          deviceNotifiers: [{ id: acceptNotifierId }],
        },
      },
    });

    // Create apps BEFORE startDomain so initial sync picks them up.
    const control = await createControlApp(domain.id, accessToken, orgEnv);

    const hostedBasicResult = await createAgentApp(domain.id, accessToken, orgEnv, 'HOSTED_DELEGATED', 'client_secret_basic');
    expect(hostedBasicResult.app).not.toBeNull();
    const hostedDelegatedBasic = hostedBasicResult.app!;

    const cimdJwk = {
      kty: 'RSA',
      n: (cimdPrivateKeyJwtPrivateJwk as any).n,
      e: (cimdPrivateKeyJwtPrivateJwk as any).e,
      use: 'sig',
      alg: 'RS256',
      kid: CIMD_PRIVATE_KEY_JWT_KID,
    };
    const hostedJwtResult = await createAgentApp(
      domain.id,
      accessToken,
      orgEnv,
      'HOSTED_DELEGATED',
      'private_key_jwt',
      { jwk: cimdJwk, signedRequest: true },
    );
    if (!hostedJwtResult.app) {
      throw new Error(`hostedJwt creation failed: ${JSON.stringify(hostedJwtResult.error)}`);
    }
    const hostedDelegatedPrivateKeyJwt = hostedJwtResult.app;

    const userEmbeddedResult = await createAgentApp(domain.id, accessToken, orgEnv, 'USER_EMBEDDED', 'none');
    const autonomousResult = await createAgentApp(domain.id, accessToken, orgEnv, 'AUTONOMOUS', 'client_secret_basic');
    const cimdHostedResult = await createCimdHostedDelegated(domain.id, accessToken, orgEnv);

    await startDomain(domain.id, accessToken);
    const startedDomain = await waitForDomainStart(domain);

    const gatewayCallback = `${process.env.AM_INTERNAL_GATEWAY_URL}/${startedDomain.domain.hrid}/oidc/ciba/authenticate/callback`;

    const registerClientWithDelegatedService = async (clientId: string, clientSecret?: string): Promise<void> => {
      await registerWithDelegatedService(
        cibaUrl,
        accessToken!,
        domain!.id,
        gatewayCallback,
        clientId,
        clientSecret ?? '',
      );
    };
    // Seed with hostedDelegatedBasic so case A works out of the box; tests override before initiating.
    await registerClientWithDelegatedService(hostedDelegatedBasic.clientId, hostedDelegatedBasic.clientSecret);

    const cibaEndpoint = `${process.env.AM_GATEWAY_URL}/${startedDomain.domain.hrid}/oidc/ciba/authenticate`;
    const tokenEndpoint = startedDomain.oidcConfig.token_endpoint;
    const oidcBaseAudience = `${process.env.AM_GATEWAY_URL}/${startedDomain.domain.hrid}/oidc`;

    const initiateCiba = async (
      clientId: string,
      loginHint: string,
      clientSecret?: string,
      requestJwt?: string,
    ): Promise<any> => {
      const params = new URLSearchParams({ scope: 'openid', login_hint: loginHint, client_id: clientId });
      if (requestJwt) {
        params.set('request', requestJwt);
      }
      const headers: Record<string, string> = { 'Content-Type': 'application/x-www-form-urlencoded' };
      if (clientSecret) {
        headers.Authorization = `Basic ${Buffer.from(`${clientId}:${clientSecret}`).toString('base64')}`;
      }
      return performPost(cibaEndpoint, '', params.toString(), headers);
    };

    const initiateCibaWithPrivateKeyJwt = async (clientId: string, loginHint: string, requestJwt?: string): Promise<any> => {
      const assertion = await createPrivateKeyJwtAssertion(clientId, cibaEndpoint);
      const params = new URLSearchParams({
        client_id: clientId,
        client_assertion_type: CLIENT_ASSERTION_TYPE,
        client_assertion: assertion,
      });
      if (requestJwt) {
        params.set('request', requestJwt);
      } else {
        params.set('scope', 'openid');
        params.set('login_hint', loginHint);
      }
      return performPost(cibaEndpoint, '', params.toString(), {
        'Content-Type': 'application/x-www-form-urlencoded',
      });
    };

    const basicAuthHeader = (clientId: string, clientSecret: string): Record<string, string> => ({
      Authorization: `Basic ${Buffer.from(`${clientId}:${clientSecret}`).toString('base64')}`,
    });

    const pollToken = async (authReqId: string, clientId: string, clientSecret?: string): Promise<any> => {
      const headers: Record<string, string> = { 'Content-Type': 'application/x-www-form-urlencoded' };
      const params: Record<string, string> = { client_id: clientId };
      if (clientSecret) {
        Object.assign(headers, basicAuthHeader(clientId, clientSecret));
      }
      return performPost(
        `${tokenEndpoint}?auth_req_id=${authReqId}&grant_type=${CIBA_GRANT_TYPE}`,
        '',
        new URLSearchParams(params).toString(),
        headers,
      );
    };

    const pollTokenWithPrivateKeyJwt = async (authReqId: string, clientId: string): Promise<any> => {
      const assertion = await createPrivateKeyJwtAssertion(clientId, tokenEndpoint);
      const params = new URLSearchParams({
        client_id: clientId,
        client_assertion_type: CLIENT_ASSERTION_TYPE,
        client_assertion: assertion,
      });
      return performPost(
        `${tokenEndpoint}?auth_req_id=${authReqId}&grant_type=${CIBA_GRANT_TYPE}`,
        '',
        params.toString(),
        { 'Content-Type': 'application/x-www-form-urlencoded' },
      );
    };

    const pollTokenUntilGranted = (authReqId: string, clientId: string, clientSecret?: string): Promise<any> =>
      pollCibaTokenUntilGranted(() => pollToken(authReqId, clientId, clientSecret));

    const pollTokenWithPrivateKeyJwtUntilGranted = (authReqId: string, clientId: string): Promise<any> =>
      pollCibaTokenUntilGranted(() => pollTokenWithPrivateKeyJwt(authReqId, clientId));

    const switchNotifier = async (target: 'accept-all' | 'reject-all'): Promise<void> => {
      const id = target === 'accept-all' ? acceptNotifierId : rejectNotifierId;
      // waitForSyncAfter snapshots lastSync BEFORE the patch and polls until it advances —
      // this avoids the race in waitForNextSync and ensures the gateway has applied the new notifier.
      await waitForSyncAfter(domain!.id, () =>
        patchDomain(domain!.id, accessToken!, {
          oidc: {
            cibaSettings: {
              enabled: true,
              authReqExpiry: 600,
              tokenReqInterval: 5,
              bindingMessageLength: 256,
              deviceNotifiers: [{ id }],
            },
          },
        }),
      );
      // patchDomain causes a full HTTP route redeploy — wait for the OIDC well-known to be live again.
      await waitForOidcReady(startedDomain.domain.hrid, { timeoutMs: 10000, intervalMs: 200 });
    };

    return {
      domain: startedDomain.domain,
      accessToken,
      oidcConfig: startedDomain.oidcConfig,
      cibaEndpoint,
      user,
      control,
      hostedDelegatedBasic,
      hostedDelegatedPrivateKeyJwt,
      userEmbeddedNone: userEmbeddedResult.app,
      userEmbeddedCreateError: userEmbeddedResult.error,
      autonomousBasic: autonomousResult.app,
      autonomousCreateError: autonomousResult.error,
      cimdHostedDelegated: cimdHostedResult.app,
      cimdHostedDelegatedCreateError: cimdHostedResult.error,
      initiateCiba,
      initiateCibaWithPrivateKeyJwt,
      pollToken,
      pollTokenUntilGranted,
      pollTokenWithPrivateKeyJwt,
      pollTokenWithPrivateKeyJwtUntilGranted,
      createRequestJwt: (clientId: string, loginHint: string) => createRequestJwt(clientId, oidcBaseAudience, loginHint),
      switchNotifier,
      registerClientWithDelegatedService,
      cleanUp: async () => {
        await safeDeleteDomain(domain?.id, accessToken);
      },
    };
  } catch (error) {
    if (domain?.id && accessToken) {
      await safeDeleteDomain(domain.id, accessToken);
    }
    throw error;
  }
};
