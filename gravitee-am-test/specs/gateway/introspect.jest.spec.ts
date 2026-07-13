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
import {
  deleteDomain,
  setupDomainForTest,
  waitForDomainStart,
  waitForDomainSync,
  waitForOidcReady,
} from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { patchApplication } from '@management-commands/application-management-commands';
import { createCertificate } from '@management-commands/certificate-management-commands';
import { buildCertificate } from '@api-fixtures/certificates';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { createTestApp } from '@utils-commands/application-commands';
import { uniqueName } from '@utils-commands/misc';

global.fetch = fetch;

jest.setTimeout(200000);

let accessToken: string;
let domain;
let oidc;
let application;
let tokenSignedWithInitialCertificate: string;
let initialCertificateKid: string;

const decodeJwtHeader = (token: string): Record<string, any> => {
  const headerSegment = token.split('.')[0];
  return JSON.parse(Buffer.from(headerSegment, 'base64url').toString('utf8'));
};

const issueClientCredentialsToken = async () => {
  const response = await performPost(oidc.token_endpoint, '', 'grant_type=client_credentials', {
    'Content-type': 'application/x-www-form-urlencoded',
    Authorization: 'Basic ' + applicationBase64Token(application),
  }).expect(200);

  return response.body.access_token as string;
};

const introspect = (token: string) =>
  performPost(oidc.introspection_endpoint, '', `token=${encodeURIComponent(token)}`, {
    'Content-type': 'application/x-www-form-urlencoded',
    Authorization: 'Basic ' + applicationBase64Token(application),
  });

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();

  const setup = await setupDomainForTest(uniqueName('introspect', true), { accessToken, waitForStart: false });
  domain = setup.domain;

  application = await createTestApp('introspect-app', domain, accessToken, 'service', {
    settings: {
      oauth: {
        grantTypes: ['client_credentials'],
      },
    },
  });

  await waitForDomainStart(domain);

  const result = await waitForOidcReady(domain.hrid);
  oidc = result.body;
});

afterAll(async () => {
  if (domain?.id) {
    await deleteDomain(domain.id, accessToken);
  }
});

describe('Gateway token introspection', () => {
  it('keeps an old access token active after the application certificate changes', async () => {
    tokenSignedWithInitialCertificate = await issueClientCredentialsToken();

    const initialHeader = decodeJwtHeader(tokenSignedWithInitialCertificate);
    initialCertificateKid = initialHeader.kid;
    expect(initialCertificateKid).toEqual(expect.any(String));

    const initialIntrospection = await introspect(tokenSignedWithInitialCertificate).expect(200);
    expect(initialIntrospection.body.active).toBe(true);

    const newCertificate = await createCertificate(domain.id, accessToken, buildCertificate(0));
    expect(newCertificate.id).toBeDefined();

    await waitForDomainSync();

    const originalClientSecret = application.settings.oauth.clientSecret;
    const patchedApplication = await patchApplication(domain.id, accessToken, { certificate: newCertificate.id }, application.id);
    expect(patchedApplication.certificate).toEqual(newCertificate.id);
    patchedApplication.settings.oauth.clientSecret = originalClientSecret;
    application = patchedApplication;

    await waitForDomainSync();

    const introspectionAfterCertificateChange = await introspect(tokenSignedWithInitialCertificate).expect(200);
    expect(introspectionAfterCertificateChange.body.active).toBe(true);
  });

  it('introspects a new access token signed with the updated application certificate', async () => {
    await waitForDomainSync();
    const newToken = await issueClientCredentialsToken();
    const newHeader = decodeJwtHeader(newToken);

    expect(newHeader.kid).toEqual(expect.any(String));
    expect(newHeader.kid).not.toEqual(initialCertificateKid);

    const introspection = await introspect(newToken).expect(200);
    expect(introspection.body.active).toBe(true);
  });
});
