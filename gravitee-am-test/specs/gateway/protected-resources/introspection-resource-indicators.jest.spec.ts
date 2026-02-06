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
import { performPost, requestClientCredentialsToken } from '@gateway-commands/oauth-oidc-commands';
import { getBase64BasicAuth } from '@gateway-commands/utils';
import { setupProtectedResourcesFixture, ProtectedResourcesFixture } from './fixtures/protected-resources-fixture';
import { decodeJwt } from '@utils-commands/jwt';
import { createCertificate } from '@management-commands/certificate-management-commands';
import { patchProtectedResource, getMcpServer } from '@management-commands/protected-resources-management-commands';
import { waitForDomainSync } from '@management-commands/domain-management-commands';
import { buildCertificate } from '@api-fixtures/certificates';

// RFC 8707 Introspection: Protected Resource can introspect tokens obtained via authorization_code grant with resource indicators
// AuthZen Introspection: Protected Resource can introspect tokens obtained via client_credentials grant with aud = clientId

globalThis.fetch = fetch;
jest.setTimeout(200000);

let fixture: ProtectedResourcesFixture;

beforeAll(async () => {
  fixture = await setupProtectedResourcesFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Protected Resource Introspection with Resource Indicators (RFC 8707)', () => {
  it('Protected Resource can introspect token obtained via authorization_code grant', async () => {
    // Use resources from the fixture's protected resources
    const resources = ['https://api.example.com/photos'];

    // Step 1: Complete authorization flow with resources to get authorization code
    const authCode = await fixture.completeAuthorizationFlow(resources);
    expect(authCode).toBeDefined();
    expect(authCode.length).toBeGreaterThan(0);

    // Step 2: Exchange authorization code for token with resources
    const tokenResponse = await fixture.exchangeAuthCodeForToken(authCode, resources).expect(200);
    expect(tokenResponse.body.access_token).toBeDefined();
    const accessToken = tokenResponse.body.access_token;

    // Step 3: Use one of the protected resources to introspect the token
    const protectedResource = fixture.protectedResources[0];
    expect(protectedResource).toBeDefined();
    expect(protectedResource.clientId).toBeDefined();
    expect(protectedResource.clientSecret).toBeDefined();

    // Step 4: Introspect the token using the Protected Resource credentials
    // Note that the client ID of the resources indicated in the token exchange must match the client ID of the Authorization header
    const introspectionResponse = await performPost(
      fixture.openIdConfiguration.introspection_endpoint,
      '',
      `token=${accessToken}&token_type_hint=access_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + getBase64BasicAuth(protectedResource.clientId, protectedResource.clientSecret),
      },
    ).expect(200);

    // Verify introspection response
    expect(introspectionResponse.body).toBeDefined();
    expect(introspectionResponse.body.active).toBe(true);
    expect(introspectionResponse.body.client_id).toBe('protected-resources-client');
    expect(introspectionResponse.body.aud).toBe(resources[0]);
    expect(introspectionResponse.body.token_type).toBe('bearer');
    expect(introspectionResponse.body.scope).toBe('openid');
    expect(introspectionResponse.body.username).toBe('protecteduser');
    expect(introspectionResponse.body.sub).toBeDefined();
    expect(typeof introspectionResponse.body.sub).toBe('string');
    expect(introspectionResponse.body.iss).toBeDefined();
    expect(introspectionResponse.body.iss).toContain(fixture.domain.hrid);
    expect(introspectionResponse.body.domain).toBe(fixture.domain.id);
    expect(introspectionResponse.body.jti).toBeDefined();
    expect(typeof introspectionResponse.body.jti).toBe('string');
    expect(introspectionResponse.body.exp).toBeDefined();
    expect(introspectionResponse.body.iat).toBeDefined();
    expect(introspectionResponse.body.exp).toBeGreaterThan(introspectionResponse.body.iat);
  });
});

describe('Protected Resource Introspection with ClientId (AuthZen Flow)', () => {
  it('Protected Resource can introspect token obtained via client_credentials grant (aud = clientId)', async () => {
    // Given: Protected Resource exists
    const protectedResource = fixture.protectedResources[0];
    expect(protectedResource).toBeDefined();
    expect(protectedResource.clientId).toBeDefined();
    expect(protectedResource.clientSecret).toBeDefined();

    // Step 1: Obtain access token using client_credentials WITHOUT resource parameter
    // This makes aud = Protected Resource clientId (correct for AuthZen flow)
    const accessToken = await requestClientCredentialsToken(
      protectedResource.clientId,
      protectedResource.clientSecret,
      fixture.openIdConfiguration,
    );
    expect(accessToken).toBeDefined();

    // Step 2: Verify token has aud = Protected Resource clientId
    const decodedToken = decodeJwt(accessToken);
    expect(decodedToken.aud).toBe(protectedResource.clientId);

    // Step 3: Introspect the token using the Protected Resource credentials
    const introspectionResponse = await performPost(
      fixture.openIdConfiguration.introspection_endpoint,
      '',
      `token=${accessToken}&token_type_hint=access_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + getBase64BasicAuth(protectedResource.clientId, protectedResource.clientSecret),
      },
    ).expect(200);

    // Step 4: Verify introspection response
    expect(introspectionResponse.body).toBeDefined();
    expect(introspectionResponse.body.active).toBe(true);
    expect(introspectionResponse.body.client_id).toBe(protectedResource.clientId);
    expect(introspectionResponse.body.aud).toBe(protectedResource.clientId);
    expect(introspectionResponse.body.token_type).toBe('bearer');
    expect(introspectionResponse.body.sub).toBeDefined();
    expect(typeof introspectionResponse.body.sub).toBe('string');
    expect(introspectionResponse.body.iss).toBeDefined();
    expect(introspectionResponse.body.iss).toContain(fixture.domain.hrid);
    expect(introspectionResponse.body.domain).toBe(fixture.domain.id);
    expect(introspectionResponse.body.jti).toBeDefined();
    expect(typeof introspectionResponse.body.jti).toBe('string');
    expect(introspectionResponse.body.exp).toBeDefined();
    expect(introspectionResponse.body.iat).toBeDefined();
    expect(introspectionResponse.body.exp).toBeGreaterThan(introspectionResponse.body.iat);
  });

  it('Protected Resource with certificate can introspect token obtained via client_credentials grant', async () => {
    // Given: Protected Resource exists and has a certificate configured
    const protectedResource = fixture.protectedResources[0];
    expect(protectedResource).toBeDefined();
    expect(protectedResource.clientId).toBeDefined();
    expect(protectedResource.clientSecret).toBeDefined();

    // Create a certificate for this test
    const certificateRequest = buildCertificate('introspection-test');
    const createdCertificate = await createCertificate(fixture.domain.id, fixture.accessToken, certificateRequest);
    expect(createdCertificate).toBeDefined();
    expect(createdCertificate.id).toBeDefined();
    const certificateId = createdCertificate.id;

    // Patch Protected Resource with certificate
    const patchedResource = await patchProtectedResource(
      fixture.domain.id,
      fixture.accessToken,
      protectedResource.id,
      { certificate: certificateId },
    );
    expect(patchedResource.certificate).toBe(certificateId);

    // Verify the resource state is correct before proceeding
    const verifiedResource = await getMcpServer(fixture.domain.id, fixture.accessToken, protectedResource.id);
    expect(verifiedResource.certificate).toBe(certificateId);

    // Wait for Protected Resource sync to gateway
    await waitForDomainSync(fixture.domain.id);

    // Step 1: Obtain access token using client_credentials WITHOUT resource parameter
    const accessToken = await requestClientCredentialsToken(
      protectedResource.clientId,
      protectedResource.clientSecret,
      fixture.openIdConfiguration,
    );
    expect(accessToken).toBeDefined();

    // Step 2: Verify token has aud = Protected Resource clientId
    const decodedToken = decodeJwt(accessToken);
    expect(decodedToken.aud).toBe(protectedResource.clientId);

    // Step 3: Introspect the token using the Protected Resource credentials
    // The certificate should be used for JWT verification
    const introspectionResponse = await performPost(
      fixture.openIdConfiguration.introspection_endpoint,
      '',
      `token=${accessToken}&token_type_hint=access_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + getBase64BasicAuth(protectedResource.clientId, protectedResource.clientSecret),
      },
    ).expect(200);

    // Step 4: Verify introspection response (certificate was used for JWT verification)
    expect(introspectionResponse.body).toBeDefined();
    expect(introspectionResponse.body.active).toBe(true);
    expect(introspectionResponse.body.client_id).toBe(protectedResource.clientId);
    expect(introspectionResponse.body.aud).toBe(protectedResource.clientId);
    expect(introspectionResponse.body.token_type).toBe('bearer');
  });

  it('Protected Resource without certificate (HMAC) can introspect token obtained via client_credentials grant', async () => {
    // Given: Protected Resource exists without certificate (HMAC-signed tokens)
    const protectedResource = fixture.protectedResources[1];
    expect(protectedResource).toBeDefined();
    expect(protectedResource.clientId).toBeDefined();
    expect(protectedResource.clientSecret).toBeDefined();

    // Ensure Protected Resource has no certificate
    const currentResource = await getMcpServer(fixture.domain.id, fixture.accessToken, protectedResource.id);
    expect(currentResource).toBeDefined();

    const patchedResource = await patchProtectedResource(
      fixture.domain.id,
      fixture.accessToken,
      protectedResource.id,
      { certificate: null },
    );
    expect(patchedResource.certificate).toBeUndefined();

    // Wait for Protected Resource sync to gateway
    await waitForDomainSync(fixture.domain.id);

    // Step 1: Obtain access token using client_credentials WITHOUT resource parameter
    const accessToken = await requestClientCredentialsToken(
      protectedResource.clientId,
      protectedResource.clientSecret,
      fixture.openIdConfiguration,
    );
    expect(accessToken).toBeDefined();

    // Step 2: Verify token has aud = Protected Resource clientId
    const decodedToken = decodeJwt(accessToken);
    expect(decodedToken.aud).toBe(protectedResource.clientId);

    // Step 3: Introspect the token using the Protected Resource credentials
    // HMAC verification should be used (empty certificate string)
    const introspectionResponse = await performPost(
      fixture.openIdConfiguration.introspection_endpoint,
      '',
      `token=${accessToken}&token_type_hint=access_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + getBase64BasicAuth(protectedResource.clientId, protectedResource.clientSecret),
      },
    ).expect(200);

    // Step 4: Verify introspection response (HMAC verification was used)
    expect(introspectionResponse.body).toBeDefined();
    expect(introspectionResponse.body.active).toBe(true);
    expect(introspectionResponse.body.client_id).toBe(protectedResource.clientId);
    expect(introspectionResponse.body.aud).toBe(protectedResource.clientId);
    expect(introspectionResponse.body.token_type).toBe('bearer');
  });
});

