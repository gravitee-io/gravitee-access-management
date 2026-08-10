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
 * SAML assertion mapping when AM acts as the IdP: how the NameID is derived and which
 * attributes are emitted.
 *
 * Verified through the federated user the client domain builds from the assertion —
 * note its username IS the NameID, so each test looks the user up accordingly rather
 * than by the username used to authenticate.
 */
setup(200000);

const UUID_FORMAT = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

let fixture: SamlLoopbackFixture;

beforeAll(async () => {
  fixture = await setupSamlLoopbackFixture();
});

beforeEach(async () => {
  // Each scenario asserts on the single federated user its own login produces.
  await fixture.clearFederatedUsers();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

describe('SAML Assertion Mapping - NameID derivation', () => {
  it(jira`should use the internal user id as NameID when no assertion mapping is configured ${'AM-6954'}`, async () => {
    await fixture.setSamlSettings({ nameIdMapping: null, assertionAttributes: null });

    await fixture.authenticate(TEST_USER.username, TEST_USER.password);

    // Default behaviour: the IdP emits the internal user id, so the federated user is
    // keyed on a UUID rather than the name the user actually logged in with.
    const federatedUser = await fixture.getOnlyFederatedUser();
    expect(federatedUser.username).toMatch(UUID_FORMAT);
    expect(federatedUser.username).not.toEqual(TEST_USER.username);
  });

  it(jira`should use the email as NameID when assertion mapping resolves to email ${'AM-6954'}`, async () => {
    await fixture.setSamlSettings({ nameIdMapping: SAML_LOOPBACK_TEST.EL_EMAIL });

    await fixture.authenticate(TEST_USER.username, TEST_USER.password);

    const federatedUser = await fixture.getOnlyFederatedUser();
    expect(federatedUser.username).toEqual(TEST_USER.email);
    expect(federatedUser.externalId).toEqual(TEST_USER.email);
  });
});

describe('SAML Assertion Mapping - Custom attributes', () => {
  it(jira`should emit both EL-resolved and literal assertion attributes ${'AM-6961'}`, async () => {
    await fixture.setSamlSettings({
      nameIdMapping: SAML_LOOPBACK_TEST.EL_USERNAME,
      assertionAttributes: [
        { name: 'customAttr1', value: SAML_LOOPBACK_TEST.EL_EMAIL },
        { name: 'customAttr2', value: SAML_LOOPBACK_TEST.EL_USERNAME },
        // A plain string literal rather than an EL expression — the mapper must pass it
        // through verbatim instead of attempting to resolve it.
        { name: 'customAttr3', value: 'quality-assurance' },
      ],
    });

    await fixture.authenticate(TEST_USER.username, TEST_USER.password);

    const federatedUser = await fixture.getOnlyFederatedUser();
    expect(federatedUser.username).toEqual(TEST_USER.username);
    expect(federatedUser.additionalInformation?.customAttr1).toEqual(TEST_USER.email);
    expect(federatedUser.additionalInformation?.customAttr2).toEqual(TEST_USER.username);
    expect(federatedUser.additionalInformation?.customAttr3).toEqual('quality-assurance');
  });
});
