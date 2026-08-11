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
 * Signature requirements between the IdP (provider application SAML settings) and the
 * SP (saml2-generic-am-idp configuration).
 *
 * The SP accepts the response when it can verify *something*: the response signature,
 * the assertion signature, or — when it holds no signing certificate — neither. It only
 * refuses when it expects a signature it cannot obtain or cannot verify.
 */
setup(200000);

let fixture: SamlLoopbackFixture;

beforeAll(async () => {
  fixture = await setupSamlLoopbackFixture();
});

beforeEach(async () => {
  await fixture.resetToBaseline();
  await fixture.setSamlSettings({ nameIdMapping: SAML_LOOPBACK_TEST.EL_USERNAME, assertionAttributes: null });
  await fixture.clearFederatedUsers();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

describe('SAML Signing - SP holds the IdP signing certificate', () => {
  it(jira`should authenticate when both the response and the assertions are signed ${'AM-2551'}`, async () => {
    await fixture.setSamlSettings({ wantResponseSigned: true, wantAssertionsSigned: true });

    await fixture.authenticate(TEST_USER.username, TEST_USER.password);

    expect((await fixture.getOnlyFederatedUser()).username).toEqual(TEST_USER.username);
  });

  it(jira`should authenticate when only the assertions are signed ${'AM-2548'}`, async () => {
    await fixture.setSamlSettings({ wantResponseSigned: false, wantAssertionsSigned: true });

    await fixture.authenticate(TEST_USER.username, TEST_USER.password);

    expect((await fixture.getOnlyFederatedUser()).username).toEqual(TEST_USER.username);
  });

  it(jira`should authenticate when only the response is signed ${'AM-2551'}`, async () => {
    await fixture.setSamlSettings({ wantResponseSigned: true, wantAssertionsSigned: false });

    await fixture.authenticate(TEST_USER.username, TEST_USER.password);

    expect((await fixture.getOnlyFederatedUser()).username).toEqual(TEST_USER.username);
  });

  it(jira`should refuse an entirely unsigned response when the SP can verify signatures ${'AM-2564'}`, async () => {
    // Neither the response nor the assertions are signed, but the SP still holds the
    // IdP certificate and therefore expects a signature it never receives.
    await fixture.setSamlSettings({ wantResponseSigned: false, wantAssertionsSigned: false });

    const response = await fixture.attemptLogin(TEST_USER.username, TEST_USER.password);

    expect(response.text).toContain('social_authentication_failed');
    expect(await fixture.findFederatedUsers()).toHaveLength(0);
  });
});

describe('SAML Signing - SP holds no signing certificate', () => {
  it(jira`should authenticate an unsigned response when the SP cannot verify signatures ${'AM-2564'}`, async () => {
    // With nothing signed and no certificate to verify against, the SP has no signature
    // expectation to violate, so the flow completes.
    await fixture.setSamlSettings({ wantResponseSigned: false, wantAssertionsSigned: false });
    await fixture.setSamlIdpConfig({ signingCertificate: null });

    await fixture.authenticate(TEST_USER.username, TEST_USER.password);

    expect((await fixture.getOnlyFederatedUser()).username).toEqual(TEST_USER.username);
  });
});
