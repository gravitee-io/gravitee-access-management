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
import { afterAll, beforeAll, expect, jest } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import {
  createDomain,
  safeDeleteDomain,
  setupDomainForTest,
  startDomain,
  waitForDomainStart,
} from '@management-commands/domain-management-commands';
import { buildRSA512Certificate } from '@api-fixtures/certificates';
import { createCertificate } from '@management-commands/certificate-management-commands';
import { getWellKnownOpenIdConfiguration, performGet } from '@gateway-commands/oauth-oidc-commands';
import { Domain } from 'api/management/models';
import { delay, uniqueName } from '@utils-commands/misc';

global.fetch = fetch;

let accessToken: any;
let domain: Domain;
let jwksUriEndpoint: any;

jest.setTimeout(200000);

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  domain = await setupDomainForTest(uniqueName('oidc-discovery', true), { accessToken }).then((it) => it.domain);

  const builtCertificate = buildRSA512Certificate();
  const certificateResponse = await createCertificate(domain.id, accessToken, builtCertificate);
  expect(certificateResponse).toBeDefined();
  await waitForDomainStart(domain);
});

describe('well-known/openid-configuration', () => {
  it('must describe oidc', async () => {
    const wellKnown = await getWellKnownOpenIdConfiguration(domain.hrid);
    // Discovery endpoints:
    const openIdConfiguration = wellKnown.body;
    expect(openIdConfiguration).toHaveProperty('authorization_endpoint');
    expect(openIdConfiguration).toHaveProperty('token_endpoint');
    expect(openIdConfiguration).toHaveProperty('userinfo_endpoint');
    expect(openIdConfiguration).toHaveProperty('jwks_uri');
    jwksUriEndpoint = openIdConfiguration.jwks_uri;
    expect(openIdConfiguration).toHaveProperty('end_session_endpoint');
    expect(openIdConfiguration).toHaveProperty('revocation_endpoint');
    expect(openIdConfiguration).toHaveProperty('introspection_endpoint');
    expect(openIdConfiguration).toHaveProperty('registration_endpoint');

    // Discovery properties:
    expect(openIdConfiguration).toHaveProperty('issuer');
    expect(openIdConfiguration).toHaveProperty('scopes_supported');
    expect(openIdConfiguration).toHaveProperty('response_types_supported');
    expect(openIdConfiguration).toHaveProperty('id_token_signing_alg_values_supported');
    expect(openIdConfiguration).toHaveProperty('id_token_encryption_alg_values_supported');
    expect(openIdConfiguration).toHaveProperty('id_token_encryption_enc_values_supported');
    expect(openIdConfiguration).toHaveProperty('userinfo_signing_alg_values_supported');
    expect(openIdConfiguration).toHaveProperty('userinfo_encryption_alg_values_supported');
    expect(openIdConfiguration).toHaveProperty('userinfo_encryption_enc_values_supported');
    expect(openIdConfiguration).toHaveProperty('token_endpoint_auth_methods_supported');
    expect(openIdConfiguration).toHaveProperty('claim_types_supported');
    expect(openIdConfiguration).toHaveProperty('claims_supported');
    expect(openIdConfiguration).toHaveProperty('code_challenge_methods_supported');
    expect(openIdConfiguration).toHaveProperty('claims_parameter_supported');
    expect(openIdConfiguration).toHaveProperty('request_parameter_supported');
    expect(openIdConfiguration).toHaveProperty('request_uri_parameter_supported');
    expect(openIdConfiguration).toHaveProperty('require_request_uri_registration');

    // Check for specific values:
    expect(openIdConfiguration.code_challenge_methods_supported).toEqual(['plain', 'S256']);
    expect(openIdConfiguration.scopes_supported).toEqual([
      'address',
      'email',
      'full_profile',
      'groups',
      'offline_access',
      'openid',
      'phone',
      'profile',
      'roles',
    ]);
  });

  it('must return jwks', async () => {
    const jwks = await performGet(jwksUriEndpoint);
    const responseBody = jwks.body;
    expect(responseBody).toHaveProperty('keys');

    // Check for the RSA keys
    const keyTypes = responseBody.keys.map((key) => key.kty);
    expect(keyTypes).toContain('RSA');

    // Ensure there are two RSA keys
    const rsaKeys = responseBody.keys.filter((key) => key.kty === 'RSA');
    expect(rsaKeys.length).toBe(2);

    // Check if one of the keys has RS512 algorithm
    const hasRS512 = rsaKeys.some((key) => key.alg === 'RS512');
    expect(hasRS512).toBe(true);

    // Check if one of the keys has RS256 algorithm
    const hasRS256 = rsaKeys.some((key) => key.alg === 'RS256');
    expect(hasRS256).toBe(true);
  });
});

afterAll(async () => {
  if (domain?.id) {
    await safeDeleteDomain(domain.id, accessToken);
  }
});
