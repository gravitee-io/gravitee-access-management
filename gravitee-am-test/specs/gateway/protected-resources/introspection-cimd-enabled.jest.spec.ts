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
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import {
  createDomain,
  patchDomain,
  safeDeleteDomain,
  startDomain,
  waitForDomainStart,
} from '@management-commands/domain-management-commands';
import { createApplication, patchApplication } from '@management-commands/application-management-commands';
import { createProtectedResource } from '@management-commands/protected-resources-management-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { performPost, requestClientCredentialsToken } from '@gateway-commands/oauth-oidc-commands';
import { getBase64BasicAuth } from '@gateway-commands/utils';
import { decodeJwt } from '@utils-commands/jwt';
import { uniqueName } from '@utils-commands/misc';

// Regression: when CIMD is enabled on a domain, the gateway's CIMD-aware client lookup is also
// consulted during access-token introspection (for the audience claim). A URL-shaped audience
// that is actually an RFC 8707 protected-resource identifier — not a CIMD client — would cause
// CimdMetadataServiceImpl to attempt an HTTP fetch of the resource URL, fail, and surface as
// InvalidClientMetadataException. That error was not caught by audience validation, so
// introspection returned {"active": false} for an otherwise-valid token.
//
// This test pins the expected behaviour: CIMD probing must not break protected-resource
// introspection. The audience URL points at WireMock, which has no stub for the chosen path
// (returns 404). Before the fix this path 404 turned into active=false; after the fix the
// lookup falls through to protected-resource validation and we get active=true.

setup();

const RESOURCE_URL = 'http://wiremock:8080/regression/introspection-cimd-not-a-client';

let accessToken: string;
let domainId: string;
let openIdConfiguration: any;
let serviceClientId: string;
let serviceClientSecret: string;
let resourceClientId: string;
let resourceClientSecret: string;

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();

  const domain = await createDomain(
    accessToken,
    uniqueName('introspect-cimd-regression', true),
    'PR introspection regression with CIMD enabled',
  );
  domainId = domain.id;

  // CIMD template app (required when CIMD is enabled)
  const template = await createApplication(domainId, accessToken, {
    name: 'cimd-template',
    type: 'WEB',
    clientId: 'cimd-template',
    clientSecret: 'cimd-template-secret',
    redirectUris: ['https://example.com/callback'],
  });
  await patchApplication(domainId, accessToken, { template: true }, template.id);

  // Enable CIMD with permissive trust validation so the URL fetch is actually attempted.
  // Without the fix, the lookup tries to GET RESOURCE_URL, gets 404 from WireMock, and
  // bubbles InvalidClientMetadataException out of introspection.
  await patchDomain(domainId, accessToken, {
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
        templateId: template.id,
      },
    },
  });

  // Service app that mints the access token via client_credentials.
  const serviceApp = await createApplication(domainId, accessToken, {
    name: 'introspect-cimd-service',
    type: 'SERVICE',
    clientId: 'introspect-cimd-service',
    clientSecret: 'introspect-cimd-service-secret',
  });
  await patchApplication(
    domainId,
    accessToken,
    { settings: { oauth: { grantTypes: ['client_credentials'] } } },
    serviceApp.id,
  );
  serviceClientId = serviceApp.settings.oauth.clientId;
  serviceClientSecret = serviceApp.settings.oauth.clientSecret;

  // Protected Resource backed by a confidential client that performs introspection.
  const protectedResource = await createProtectedResource(domainId, accessToken, {
    name: 'introspect-cimd-pr',
    description: 'PR with URL-shaped resource identifier',
    type: 'MCP_SERVER',
    resourceIdentifiers: [RESOURCE_URL],
  });
  resourceClientId = protectedResource.clientId;
  resourceClientSecret = protectedResource.clientSecret;

  await startDomain(domainId, accessToken);
  const started = await waitForDomainStart(domain);
  openIdConfiguration = started.oidcConfig;

  // Belt-and-braces: ensure the PR is visible on the gateway before issuing tokens.
  await waitForSyncAfter(domainId, async () => {});
}, 60000);

afterAll(async () => {
  if (domainId && accessToken) {
    await safeDeleteDomain(domainId, accessToken);
  }
});

describe('Introspection when CIMD is enabled and audience is an RFC 8707 resource identifier', () => {
  it('returns active=true even when the resource URL is not a CIMD client', async () => {
    const token = await requestClientCredentialsToken(
      serviceClientId,
      serviceClientSecret,
      openIdConfiguration,
      undefined,
      RESOURCE_URL,
    );
    const decoded = decodeJwt(token);
    expect(decoded.aud).toBe(RESOURCE_URL);

    const introspection = await performPost(
      openIdConfiguration.introspection_endpoint,
      '',
      `token=${token}&token_type_hint=access_token`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + getBase64BasicAuth(resourceClientId, resourceClientSecret),
      },
    ).expect(200);

    // Before the fix, this fails: CIMD probes RESOURCE_URL, WireMock returns 404,
    // InvalidClientMetadataException leaks out of audience validation, and the response
    // is {"active": false}.
    expect(introspection.body.active).toBe(true);
    expect(introspection.body.aud).toBe(RESOURCE_URL);
    expect(introspection.body.client_id).toBe(serviceClientId);
  });
});
