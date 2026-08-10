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

import { SAML_LOOPBACK_TEST, SamlLoopbackFixture, setupSamlLoopbackFixture, TEST_USER } from './fixtures/saml-loopback-fixture';
import { setup } from '../../test-fixture';

/**
 * Behaviour for SAML application settings that the management API accepts without
 * validating — `validateApplicationSamlSettings` checks only the encryption certificate,
 * the algorithm URIs and the numeric time fields, so duplicate attribute names, blank
 * names, null values, malformed EL and non-URI audiences all persist.
 *
 * These tests pin what the gateway then does with that configuration. They document
 * current behaviour rather than asserting it is desirable — notably a blank attribute
 * name breaks authentication outright.
 */
setup(200000);

const UUID_FORMAT = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

let fixture: SamlLoopbackFixture;

beforeAll(async () => {
  fixture = await setupSamlLoopbackFixture();
});

beforeEach(async () => {
  await fixture.resetToBaseline();
  await fixture.clearFederatedUsers();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

describe('SAML permissive config - assertion attributes', () => {
  it(jira`should emit the last value when two assertion attributes share a name ${'AM-6961'}`, async () => {
    // The Console rejects duplicate names; the management API accepts them.
    await fixture.setSamlSettings({
      nameIdMapping: SAML_LOOPBACK_TEST.EL_USERNAME,
      assertionAttributes: [
        { name: 'dept', value: SAML_LOOPBACK_TEST.EL_USERNAME },
        { name: 'dept', value: SAML_LOOPBACK_TEST.EL_EMAIL },
      ],
    });

    await fixture.authenticate(TEST_USER.username, TEST_USER.password);

    const federatedUser = await fixture.getOnlyFederatedUser();
    expect(federatedUser.additionalInformation?.dept).toEqual(TEST_USER.email);
  });

  it(jira`should break authentication when an assertion attribute has a blank name ${'AM-6960'}`, async () => {
    await fixture.setSamlSettings({
      nameIdMapping: SAML_LOOPBACK_TEST.EL_USERNAME,
      assertionAttributes: [{ name: '', value: 'literal-value' }],
    });

    // A blank attribute name is accepted by the management API but leaves the IdP
    // unable to complete the flow — the SP never receives a usable response.
    await expect(fixture.attemptLogin(TEST_USER.username, TEST_USER.password)).rejects.toThrow(/got 401/);
    expect(await fixture.findFederatedUsers()).toHaveLength(0);
  });

  it(jira`should omit an assertion attribute whose value is null ${'AM-6961'}`, async () => {
    await fixture.setSamlSettings({
      nameIdMapping: SAML_LOOPBACK_TEST.EL_USERNAME,
      assertionAttributes: [{ name: 'dept', value: null }],
    });

    await fixture.authenticate(TEST_USER.username, TEST_USER.password);

    // `value` is declared @NotNull on SAMLAssertionAttribute but is neither rejected
    // nor emitted — the attribute simply never reaches the federated profile.
    const federatedUser = await fixture.getOnlyFederatedUser();
    expect(federatedUser.additionalInformation?.dept).toBeUndefined();
  });
});

describe('SAML permissive config - NameID and audiences', () => {
  it(jira`should fall back to the internal user id when the NameID expression is malformed ${'AM-6960'}`, async () => {
    await fixture.setSamlSettings({
      nameIdMapping: `{#context.attributes['user'].username`,
      assertionAttributes: null,
    });

    await fixture.authenticate(TEST_USER.username, TEST_USER.password);

    // Syntactically broken EL degrades the same way an unresolvable attribute does,
    // rather than failing the flow.
    expect((await fixture.getOnlyFederatedUser()).username).toMatch(UUID_FORMAT);
  });

  it(jira`should authenticate despite blank and non-URI audience entries ${'AM-6959'}`, async () => {
    await fixture.setSamlSettings({
      nameIdMapping: SAML_LOOPBACK_TEST.EL_USERNAME,
      includeAssertionConditions: true,
      audiences: ['', 'not a uri'],
    });

    await fixture.authenticate(TEST_USER.username, TEST_USER.password);

    expect((await fixture.getOnlyFederatedUser()).username).toEqual(TEST_USER.username);
  });
});

describe('SAML permissive config - legacy encryption algorithm', () => {
  it(jira`should authenticate with RSA1_5 key transport for encrypted assertions ${'AM-7107'}`, async () => {
    // RSA1_5 is a deprecated key transport algorithm, still present in the allow-list
    // at ApplicationServiceImpl. Authentication succeeds rather than being refused.
    await fixture.setSamlSettings({
      nameIdMapping: SAML_LOOPBACK_TEST.EL_USERNAME,
      wantAssertionsEncrypted: true,
      keyTransportEncryptionAlgorithm: 'http://www.w3.org/2001/04/xmlenc#rsa-1_5',
      dataEncryptionAlgorithm: 'http://www.w3.org/2001/04/xmlenc#aes256-cbc',
    });

    await fixture.authenticate(TEST_USER.username, TEST_USER.password);

    expect((await fixture.getOnlyFederatedUser()).username).toEqual(TEST_USER.username);
  });
});
