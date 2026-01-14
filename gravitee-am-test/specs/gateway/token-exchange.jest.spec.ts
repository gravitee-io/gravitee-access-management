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
import fetch from 'cross-fetch';
import { afterAll, beforeAll, describe, expect, it, jest } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createDomain, safeDeleteDomain, startDomain, waitForDomainSync } from '@management-commands/domain-management-commands';
import { getWellKnownOpenIdConfiguration, performPost } from '@gateway-commands/oauth-oidc-commands';
import { getDomainManagerUrl } from '@management-commands/service/utils';
import { createServiceApplication } from './fixtures/rate-limit-fixture';
import { updateApplication } from '@management-commands/application-management-commands';
import { getBase64BasicAuth } from '@gateway-commands/utils';
import { uniqueName } from '@utils-commands/misc';
import { buildCreateAndTestUser } from '@management-commands/user-management-commands';

global.fetch = fetch;

let accessToken;
let domain;
let openIdConfiguration;
let basicAuth;
let user;

jest.setTimeout(200000);

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();

  domain = await createDomain(accessToken, uniqueName('token-exchange', true), 'Domain with Token Exchange');
  
  // Enable Token Exchange in the domain using supertest to bypass SDK limitations
  const supertest = require('supertest');
  await supertest(process.env.AM_MANAGEMENT_URL)
    .patch(getDomainManagerUrl(domain.id).replace(process.env.AM_MANAGEMENT_URL, ''))
    .set('Authorization', 'Bearer ' + accessToken)
    .send({
      oidc: {
        tokenExchangeSettings: {
          enabled: true,
          allowImpersonation: true,
          allowDelegation: true
        }
      }
    })
    .expect(200);

  const application = await createServiceApplication(domain.id, accessToken, 'app', 'app', 'app').then((app) =>
    updateApplication(
      domain.id,
      accessToken,
      {
        settings: {
          oauth: {
            grantTypes: ['password', 'urn:ietf:params:oauth:grant-type:token-exchange'],
          },
        },
      },
      app.id,
    ),
  );
  basicAuth = getBase64BasicAuth('app', 'app');

  user = await buildCreateAndTestUser(domain.id, accessToken, 1);

  await startDomain(domain.id, accessToken);
  await waitForDomainSync(domain.id, accessToken);

  const result = await getWellKnownOpenIdConfiguration(domain.hrid).expect(200);
  openIdConfiguration = result.body;
});

afterAll(async () => {
  if (domain && domain.id) {
    await safeDeleteDomain(domain.id, accessToken);
  }
});

describe('Scenario: OAuth 2.0 Token Exchange (RFC 8693)', () => {
  it('Successful Token Exchange (Positive)', async () => {
    // 1. Get subject token via password grant
    const tokenResponse = await performPost(
      openIdConfiguration.token_endpoint,
      `?grant_type=password&username=${user.username}&password=${user.password}`,
      null,
      {
        Authorization: `Basic ${basicAuth}`,
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    ).expect(200);

    const subjectToken = tokenResponse.body.access_token;

    // 2. Perform Token Exchange
    const exchangeResponse = await performPost(
      openIdConfiguration.token_endpoint,
      `?grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
      `&subject_token=${subjectToken}` +
      `&subject_token_type=urn:ietf:params:oauth:token-type:access_token`,
      null,
      {
        Authorization: `Basic ${basicAuth}`,
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    ).expect(200);

    expect(exchangeResponse.body.access_token).toBeDefined();
    expect(exchangeResponse.body.issued_token_type).toBe('urn:ietf:params:oauth:token-type:access_token');
    expect(exchangeResponse.body.token_type).toBe('Bearer');
  });

  it('Successful Token Exchange with Resource and Audience', async () => {
    // 1. Get subject token
    const tokenResponse = await performPost(
      openIdConfiguration.token_endpoint,
      `?grant_type=password&username=${user.username}&password=${user.password}`,
      null,
      {
        Authorization: `Basic ${basicAuth}`,
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    ).expect(200);

    const subjectToken = tokenResponse.body.access_token;

    // 2. Perform Token Exchange with audience and resource
    const exchangeResponse = await performPost(
      openIdConfiguration.token_endpoint,
      `?grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
      `&subject_token=${subjectToken}` +
      `&subject_token_type=urn:ietf:params:oauth:token-type:access_token` + 
      `&resource=api-resource-1` + 
      `&audience=api-audience-2`,
      null,
      {
        Authorization: `Basic ${basicAuth}`,
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    ).expect(200);

    const accessToken = exchangeResponse.body.access_token;
    // Basic JWT decode to check audience
    const payload = JSON.parse(Buffer.from(accessToken.split('.')[1], 'base64').toString());
    
    // As per RFC 8693, the token should be valid for both
    expect(payload.aud).toBeDefined();
    expect(Array.isArray(payload.aud)).toBeTruthy();
    expect(payload.aud).toContain('api-resource-1');
    expect(payload.aud).toContain('api-audience-2');
  });

  it('Should reject request when subject_token is missing', async () => {
    await performPost(
      openIdConfiguration.token_endpoint,
      `?grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token_type=urn:ietf:params:oauth:token-type:access_token`,
      null,
      {
        Authorization: `Basic ${basicAuth}`,
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    ).expect(400)
    .then(response => {
      expect(response.body.error).toBe('invalid_request');
    });
  });

  it('Should reject request when subject_token_type is missing', async () => {
    await performPost(
      openIdConfiguration.token_endpoint,
      `?grant_type=urn:ietf:params:oauth:grant-type:token-exchange&subject_token=some-token`,
      null,
      {
        Authorization: `Basic ${basicAuth}`,
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    ).expect(400)
    .then(response => {
      expect(response.body.error).toBe('invalid_request');
    });
  });

  it('Should reject request when subject_token_type is unsupported', async () => {
    await performPost(
      openIdConfiguration.token_endpoint,
      `?grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
      `&subject_token=some-token` +
      `&subject_token_type=urn:ietf:params:oauth:token-type:invalid`,
      null,
      {
        Authorization: `Basic ${basicAuth}`,
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    ).expect(400)
    .then(response => {
      expect(response.body.error).toBe('unsupported_token_type');
    });
  });

  it('Should reject request when subject_token is invalid', async () => {
    await performPost(
      openIdConfiguration.token_endpoint,
      `?grant_type=urn:ietf:params:oauth:grant-type:token-exchange` +
      `&subject_token=invalid-token` +
      `&subject_token_type=urn:ietf:params:oauth:token-type:access_token`,
      null,
      {
        Authorization: `Basic ${basicAuth}`,
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    ).expect(400)
    .then(response => {
      expect(response.body.error).toBe('invalid_grant');
    });
  });
});
