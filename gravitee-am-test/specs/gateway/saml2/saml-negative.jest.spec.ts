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
import { afterAll, beforeAll, beforeEach, describe, expect, it } from '@jest/globals';
import { jira } from '@specs-utils/jira';

import { SamlLoopbackFixture, setupSamlLoopbackFixture, TEST_USER } from './fixtures/saml-loopback-fixture';
import { initiateSamlFlow } from './fixtures/saml-response-capture';
import { setup } from '../../test-fixture';

/**
 * SAML failure paths: bad credentials, and misconfiguration between the IdP and the SP.
 *
 * Each scenario asserts that authentication is refused AND that no federated user is
 * left behind in the client domain — a failed login must not provision anyone.
 */
setup(200000);

let fixture: SamlLoopbackFixture;

beforeAll(async () => {
  fixture = await setupSamlLoopbackFixture();
});

beforeEach(async () => {
  // Several scenarios here deliberately break the SAML wiring, so restore a known-good
  // configuration first — tests must be runnable in any order.
  await fixture.resetToBaseline();
  await fixture.clearFederatedUsers();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

describe('SAML Negative - invalid credentials', () => {
  it(jira`should reject unknown credentials and provision no federated user ${'AM-6960'}`, async () => {
    const response = await fixture.attemptLogin('not-a-real-user', 'wrong-password');

    expect(response.status).toEqual(200);
    expect(response.text).toContain('login_failed');

    // A rejected authentication must not create a federated identity.
    expect(await fixture.findFederatedUsers()).toHaveLength(0);
  });

  it(jira`should reject a known user with the wrong password ${'AM-6960'}`, async () => {
    const response = await fixture.attemptLogin(TEST_USER.username, 'definitely-not-the-password');

    expect(response.status).toEqual(200);
    expect(response.text).toContain('login_failed');
    expect(await fixture.findFederatedUsers()).toHaveLength(0);
  });
});

describe('SAML Negative - SP cannot validate the response signature', () => {
  it(jira`should fail authentication when the SP has no signing certificate to validate with ${'AM-6960'}`, async () => {
    // Strip the IdP's public certificate from the SP-side configuration, leaving the SP
    // unable to verify the signature on the SAML response it receives.
    await fixture.setSamlIdpConfig({ signingCertificate: null });

    const response = await fixture.attemptLogin(TEST_USER.username, TEST_USER.password);

    expect(response.text).toContain('social_authentication_failed');
    expect(await fixture.findFederatedUsers()).toHaveLength(0);
  });
});

describe('SAML Negative - SP entity ID the IdP cannot resolve', () => {
  it(jira`should refuse the AuthnRequest when the SP entity ID is unknown to the IdP ${'AM-6960'}`, async () => {
    // The IdP resolves the requesting SP by matching the AuthnRequest issuer against the
    // application's SAML entityId. Changing it leaves the SP unregistered as far as the
    // IdP is concerned, and the flow fails before any login form is presented.
    await fixture.setSamlSettings({ entityId: 'unknown-sp-entity-id', assertionAttributes: null });

    const { locations } = await initiateSamlFlow(fixture.saml.domains, fixture.saml.clientOpenIdConfiguration);
    // The failure is reported on the error redirect, form-encoded, so '+' must become
    // whitespace before decoding.
    const surfaced = decodeURIComponent(locations.join(' ').replace(/\+/g, ' '));

    expect(surfaced).toContain('error=technical_error');
    expect(surfaced).toContain('can not be found');
    expect(await fixture.findFederatedUsers()).toHaveLength(0);
  });
});
