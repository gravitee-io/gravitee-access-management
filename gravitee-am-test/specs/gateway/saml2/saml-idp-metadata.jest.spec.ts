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
import { jira } from '@specs-utils/jira';
import { performGet } from '@gateway-commands/oauth-oidc-commands';

import { SamlLoopbackFixture, setupSamlLoopbackFixture } from './fixtures/saml-loopback-fixture';
import { getProviderMetadataUrl } from './setup';
import { setup } from '../../test-fixture';

/**
 * The IdP metadata document AM publishes when it acts as a SAML IdP.
 *
 * The existing SAML suite only ever fetches metadata from WireMock; nothing asserts
 * what AM itself serves on /{domain}/saml2/idp/metadata.
 */
setup(200000);

let fixture: SamlLoopbackFixture;

beforeAll(async () => {
  fixture = await setupSamlLoopbackFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

describe('SAML IdP metadata endpoint', () => {
  it(jira`should publish IdP metadata describing the SSO endpoints and certificates ${'AM-6959'}`, async () => {
    const metadataUrl = getProviderMetadataUrl(fixture.saml.domains.providerDomain);

    const response = await performGet(metadataUrl, '').expect(200);

    // Descriptor identity and role.
    expect(response.text).toContain('EntityDescriptor');
    expect(response.text).toContain('IDPSSODescriptor');

    // Key material the SP needs: a signing key to verify responses, and an encryption
    // key so it can be offered encrypted assertions.
    expect(response.text).toContain('use="signing"');
    expect(response.text).toContain('use="encryption"');
    expect(response.text).toContain('X509Certificate');

    // AM advertises the HTTP-Redirect binding only, for both SSO and SLO — an SP
    // reading this metadata will never negotiate HTTP-POST from it.
    expect(response.text).toMatch(/SingleSignOnService Binding="urn:oasis:names:tc:SAML:2\.0:bindings:HTTP-Redirect"/);
    expect(response.text).toMatch(/SingleLogoutService Binding="urn:oasis:names:tc:SAML:2\.0:bindings:HTTP-Redirect"/);
    expect(response.text).not.toContain('urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST');

    // Signed AuthnRequests are required.
    expect(response.text).toContain('WantAuthnRequestsSigned="true"');
  });

  it(jira`should return 404 for the metadata of a domain that does not exist ${'AM-6959'}`, async () => {
    const unknownDomainMetadata = getProviderMetadataUrl({
      ...fixture.saml.domains.providerDomain,
      hrid: 'domain-that-does-not-exist',
    });

    await performGet(unknownDomainMetadata, '').expect(404);
  });
});
