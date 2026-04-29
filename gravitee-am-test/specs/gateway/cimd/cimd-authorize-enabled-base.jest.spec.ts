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

import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { setup } from '../../test-fixture';
import { CIMD_REDIRECT_URI, CimdAuthorizeFixture, setupCimdAuthorizeFixture } from './fixtures/cimd-authorize-fixture';
import {
  clearWireMockRequestJournal,
  countGetRequestsForPathSubstring,
  fetchWireMockRequestJournal,
} from './fixtures/cimd-wiremock-helpers';

setup(200000);

let fixture: CimdAuthorizeFixture;

beforeAll(async () => {
  fixture = await setupCimdAuthorizeFixture('ENABLED_BASE');
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('CIMD authorize - ENABLED_BASE', () => {
  it('should advertise client_id_metadata_document_supported in OIDC discovery', () => {
    expect(fixture.openIdConfiguration.client_id_metadata_document_supported).toBe(true);
  });

  it('should continue authorization with URL client_id when CIMD metadata is valid', async () => {
    const response = await fixture.authorize(fixture.buildClientId('valid-none'));
    fixture.expectLoginRedirect(response);
  });

  it('should continue authorization on a second request with the same URL client_id', async () => {
    const clientId = fixture.buildClientId('valid-none');
    fixture.expectLoginRedirect(await fixture.authorize(clientId));
    fixture.expectLoginRedirect(await fixture.authorize(clientId));
  });

  it('should fetch CIMD metadata from source server only once for two consecutive authorizations', async () => {
    await clearWireMockRequestJournal();
    const pathMarker = '/cimd/ENABLED_BASE/cache-twice';
    const journalBefore = await fetchWireMockRequestJournal();
    const baseline = countGetRequestsForPathSubstring(journalBefore, pathMarker);

    const clientId = fixture.buildClientId('cache-twice');
    fixture.expectLoginRedirect(await fixture.authorize(clientId));
    fixture.expectLoginRedirect(await fixture.authorize(clientId));

    const journalAfter = await fetchWireMockRequestJournal();
    const after = countGetRequestsForPathSubstring(journalAfter, pathMarker);
    expect(after - baseline).toBe(1);
  });

  it('should prioritize pre-registered URL client over CIMD lookup for the same URL client_id', async () => {
    const clientId = fixture.preRegisteredUrlApplication.settings.oauth.clientId;

    const response = await fixture.authorize(clientId);
    fixture.expectLoginRedirect(response);
  });

  it('should return invalid_client_metadata when metadata endpoint responds with redirect', async () => {
    const response = await fixture.authorize(fixture.buildClientId('http-302'));
    fixture.expectInvalidClientMetadata(response, 'HTTP 302');
  });

  it('should return invalid_client_metadata when metadata endpoint returns non-200', async () => {
    const response = await fixture.authorize(fixture.buildClientId('http-404'));
    fixture.expectInvalidClientMetadata(response, 'HTTP 404');
  });

  it('should return invalid_client_metadata when metadata payload is not valid JSON', async () => {
    const response = await fixture.authorize(fixture.buildClientId('invalid-json'));
    fixture.expectInvalidClientMetadata(response, 'not valid JSON');
  });

  it('should return invalid_client_metadata when metadata client_id does not match requested client_id', async () => {
    const response = await fixture.authorize(fixture.buildClientId('client-id-mismatch'));
    fixture.expectInvalidClientMetadata(response, 'does not match requested client_id');
  });

  it('should return invalid_client_metadata when metadata redirect_uris is missing', async () => {
    const response = await fixture.authorize(fixture.buildClientId('missing-redirect-uris'));
    fixture.expectInvalidClientMetadata(response, 'Missing or invalid redirect_uris');
  });

  it('should reject when requested redirect_uri is not present in metadata redirect_uris', async () => {
    const response = await fixture.authorize(fixture.buildClientId('redirect-uri-mismatch'));
    const error = fixture.readOAuthError(response);
    expect(['invalid_client_metadata', 'redirect_uri_mismatch']).toContain(error.error);
    expect(error.errorDescription).toContain('redirect_uri');
  });

  it('should return invalid_client_metadata when token_endpoint_auth_method is client_secret_basic', async () => {
    const response = await fixture.authorize(fixture.buildClientId('secret-basic'));
    fixture.expectInvalidClientMetadata(response, 'Secret-based token_endpoint_auth_method is not allowed');
  });

  it('should return invalid_client_metadata when token_endpoint_auth_method is client_secret_post', async () => {
    const response = await fixture.authorize(fixture.buildClientId('secret-post'));
    fixture.expectInvalidClientMetadata(response, 'Secret-based token_endpoint_auth_method is not allowed');
  });

  it('should return invalid_client_metadata when token_endpoint_auth_method is client_secret_jwt', async () => {
    const response = await fixture.authorize(fixture.buildClientId('secret-jwt'));
    fixture.expectInvalidClientMetadata(response, 'Secret-based token_endpoint_auth_method is not allowed');
  });

  it('should return invalid_client_metadata when metadata contains client_secret', async () => {
    const response = await fixture.authorize(fixture.buildClientId('has-client-secret'));
    fixture.expectInvalidClientMetadata(response, 'client_secret metadata is not allowed');
  });

  it('should return invalid_client_metadata when metadata contains client_secret_expires_at', async () => {
    const response = await fixture.authorize(fixture.buildClientId('has-client-secret-expires-at'));
    fixture.expectInvalidClientMetadata(response, 'client_secret metadata is not allowed');
  });

  it('should return invalid_client_metadata when private_key_jwt metadata has no jwks and no jwks_uri', async () => {
    const response = await fixture.authorize(fixture.buildClientId('private-key-jwt-missing-jwks'));
    fixture.expectInvalidClientMetadata(response, 'private_key_jwt requires jwks or jwks_uri');
  });

  it('should continue authorization when private_key_jwt metadata includes jwks', async () => {
    const response = await fixture.authorize(fixture.buildClientId('private-key-jwt-with-jwks'));
    fixture.expectLoginRedirect(response);
  });

  it('should enforce exact redirect_uri matching for CIMD clients', async () => {
    const response = await fixture.authorize(
      fixture.buildClientId('valid-none'),
      CIMD_REDIRECT_URI + '/sub',
    );
    const error = fixture.readOAuthError(response);
    expect(error.error).toBe('redirect_uri_mismatch');
  });
});
