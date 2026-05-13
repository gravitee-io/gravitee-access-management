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
} from '@management-commands/domain-management-commands';
import { createApplication, patchApplication, updateApplication } from '@management-commands/application-management-commands';
import { createUser } from '@management-commands/user-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { retryUntil } from '@utils-commands/retry';
import { uniqueName } from '@utils-commands/misc';
import { Application } from '@management-models/Application';
import { Domain } from '@management-models/Domain';
import { Fixture } from '../../../test-fixture';
import { CIMD_PRIVATE_KEY_JWT_KID, cimdPrivateKeyJwtPrivateJwk } from '@api-fixtures/cimd-private-key-jwt';
import * as jose from 'jose';
import crypto from 'crypto';

export const CIMD_CIBA_CLIENT_ID_POLL = 'http://wiremock:8080/cimd/ENABLED_BASE/valid-ciba-poll';
export const CIMD_CIBA_CLIENT_ID_JAR = 'http://wiremock:8080/cimd/ENABLED_BASE/valid-ciba-jar';
const CLIENT_ASSERTION_TYPE = 'urn:ietf:params:oauth:client-assertion-type:jwt-bearer';
const CIBA_GRANT_TYPE = 'urn:openid:params:grant-type:ciba';
/** Polling cadence when waiting for CIBA completion (domain `tokenReqInterval` is 5s). */
const CIBA_TOKEN_POLL_INTERVAL_MS = 5500;
const CIBA_TOKEN_POLL_TIMEOUT_MS = 90000;

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

const pollCibaTokenUntilGranted = async (fetchToken: () => Promise<any>): Promise<any> =>
  retryUntil(fetchToken, isCibaTokenGrantedResponse, {
    timeoutMillis: CIBA_TOKEN_POLL_TIMEOUT_MS,
    intervalMillis: CIBA_TOKEN_POLL_INTERVAL_MS,
  });

const CIMD_CIBA_TEST_USER = {
  username: 'cimd-ciba-user',
  password: 'CimdCibaP@ssw0rd123!',
  firstName: 'CimdCiba',
  lastName: 'User',
  email: 'cimd.ciba.user@test.com',
};

export interface CimdCibaFixture extends Fixture {
  domain: Domain;
  accessToken: string;
  oidcConfig: any;
  cibaEndpoint: string;
  user: any;
  templateApplication: Application;
  initiateCiba: (clientId: string, loginHint: string, requestJwt?: string) => Promise<any>;
  initiateCibaWithPrivateKeyJwt: (clientId: string, loginHint: string, requestJwt?: string) => Promise<any>;
  pollToken: (authReqId: string, clientId: string) => Promise<any>;
  pollTokenWithPrivateKeyJwt: (authReqId: string, clientId: string) => Promise<any>;
  pollTokenUntilGranted: (authReqId: string, clientId: string) => Promise<any>;
  pollTokenWithPrivateKeyJwtUntilGranted: (authReqId: string, clientId: string) => Promise<any>;
  createRequestJwt: (clientId: string, loginHint: string) => Promise<string>;
  createPrivateKeyJwtAssertion: (clientId: string) => Promise<string>;
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
    name: uniqueName('cimd-ciba-template', true),
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

export const setupCimdCibaFixture = async (): Promise<CimdCibaFixture> => {
  let domain: Domain | null = null;
  let accessToken: string | null = null;

  try {
    accessToken = await requestAdminAccessToken();
    expect(accessToken).toBeDefined();

    domain = await createDomain(accessToken, uniqueName('cimd-ciba', true), 'CIMD + CIBA integration test');
    expect(domain.id).toBeDefined();

    const idpSet = await getAllIdps(domain.id, accessToken);
    const defaultIdp = idpSet.values().next().value;
    expect(defaultIdp).toBeDefined();

    const templateApplication = await createTemplateApplication(domain.id, accessToken, defaultIdp.id);

    const user = await createUser(domain.id, accessToken, {
      firstName: CIMD_CIBA_TEST_USER.firstName,
      lastName: CIMD_CIBA_TEST_USER.lastName,
      email: CIMD_CIBA_TEST_USER.email,
      username: CIMD_CIBA_TEST_USER.username,
      password: CIMD_CIBA_TEST_USER.password,
      client: templateApplication.id,
      source: defaultIdp.id,
      preRegistration: false,
    });
    expect(user).toBeDefined();

    const managementUrl = `${process.env.AM_MANAGEMENT_URL}/management/organizations/DEFAULT/environments/DEFAULT/domains/`;
    const internalCibaUrl = process.env.AM_INTERNAL_CIBA_NOTIFIER_URL;
    const cibaUrl = process.env.AM_CIBA_NOTIFIER_URL;

    const notifierResponse = await performPost(
      `${managementUrl}${domain.id}/auth-device-notifiers`,
      '',
      {
        type: 'http-am-authdevice-notifier',
        configuration: `{"endpoint":"${internalCibaUrl}/notify/accept-all","headerName":"Authorization","connectTimeout":5000,"idleTimeout":10000,"maxPoolSize":10}`,
        name: 'Always OK notifier',
      },
      {
        'Content-type': 'application/json',
        Authorization: `Bearer ${accessToken}`,
      },
    );
    expect(notifierResponse.status).toBe(201);
    const notifierPluginId = notifierResponse.body.id;

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
          deviceNotifiers: [{ id: notifierPluginId }],
        },
      },
    });

    await startDomain(domain.id, accessToken);
    // waitForDomainStart already waits for gateway sync (waitForDomainReady) and OIDC well-known (waitForOidcReady).
    const startedDomain = await waitForDomainStart(domain);

    const cibaNotifierRegResponse = await performPost(
      `${cibaUrl}/domains`,
      '',
      {
        domainId: domain.id,
        domainCallback: `${process.env.AM_INTERNAL_GATEWAY_URL}/${startedDomain.domain.hrid}/oidc/ciba/authenticate/callback`,
        // AM's callback ignores the Basic header but Vert.x requires url-encoding of client_id
        clientId: encodeURIComponent(CIMD_CIBA_CLIENT_ID_POLL),
        clientSecret: '',
      },
      {
        'Content-type': 'application/json',
        Authorization: `Bearer ${accessToken}`,
      },
    );
    expect(cibaNotifierRegResponse.status).toBe(200);

    const cibaEndpoint = `${process.env.AM_GATEWAY_URL}/${startedDomain.domain.hrid}/oidc/ciba/authenticate`;
    const tokenEndpoint = startedDomain.oidcConfig.token_endpoint;
    const oidcBaseAudience = `${process.env.AM_GATEWAY_URL}/${startedDomain.domain.hrid}/oidc`;

    const initiateCiba = async (clientId: string, loginHint: string, requestJwt?: string): Promise<any> => {
      const params = new URLSearchParams({ scope: 'openid', login_hint: loginHint, client_id: clientId });
      if (requestJwt) {
        params.set('request', requestJwt);
      }
      return performPost(cibaEndpoint, '', params.toString(), {
        'Content-Type': 'application/x-www-form-urlencoded',
      });
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

    const pollToken = async (authReqId: string, clientId: string): Promise<any> =>
      performPost(
        `${tokenEndpoint}?auth_req_id=${authReqId}&grant_type=${CIBA_GRANT_TYPE}`,
        '',
        new URLSearchParams({ client_id: clientId }).toString(),
        { 'Content-Type': 'application/x-www-form-urlencoded' },
      );

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

    const pollTokenUntilGranted = (authReqId: string, clientId: string): Promise<any> =>
      pollCibaTokenUntilGranted(() => pollToken(authReqId, clientId));

    const pollTokenWithPrivateKeyJwtUntilGranted = (authReqId: string, clientId: string): Promise<any> =>
      pollCibaTokenUntilGranted(() => pollTokenWithPrivateKeyJwt(authReqId, clientId));

    return {
      domain: startedDomain.domain,
      accessToken,
      oidcConfig: startedDomain.oidcConfig,
      cibaEndpoint,
      user,
      templateApplication,
      initiateCiba,
      initiateCibaWithPrivateKeyJwt,
      pollToken,
      pollTokenWithPrivateKeyJwt,
      pollTokenUntilGranted,
      pollTokenWithPrivateKeyJwtUntilGranted,
      createRequestJwt: (clientId: string, loginHint: string) =>
        createRequestJwt(clientId, oidcBaseAudience, loginHint),
      createPrivateKeyJwtAssertion: (clientId: string) =>
        createPrivateKeyJwtAssertion(clientId, cibaEndpoint),
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
