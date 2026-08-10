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
import {
  captureSamlResponseXml,
  encryptionAlgorithms,
  hasEncryptedAssertion,
  hasPlaintextAssertion,
  signatureLevels,
} from './fixtures/saml-response-capture';
import { setup } from '../../test-fixture';

/**
 * Assertion encryption and signing, asserted against the SAML response itself.
 *
 * Both are implemented only on the HTTP-POST response builder in the `saml2-idp`
 * plugin — the HTTP-Redirect builder reads none of these settings and signs at the
 * binding layer instead. Every scenario here therefore pins the SP to HTTP-POST,
 * except the final one which documents what Redirect actually does.
 */
setup(200000);

const HTTP_POST = 'urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST';
const HTTP_REDIRECT = 'urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect';
const RSA_OAEP = 'http://www.w3.org/2001/04/xmlenc#rsa-oaep-mgf1p';
const AES_256_CBC = 'http://www.w3.org/2001/04/xmlenc#aes256-cbc';
const AES_128_CBC = 'http://www.w3.org/2001/04/xmlenc#aes128-cbc';
const RSA_1_5 = 'http://www.w3.org/2001/04/xmlenc#rsa-1_5';

/**
 * Key transport algorithms AM permits (ApplicationServiceImpl). RSA1_5 is on that list
 * but is padding-oracle vulnerable, so it must never be what AM falls back to.
 */
const STRONG_KEY_TRANSPORT = [RSA_OAEP];
/** Data encryption algorithms AM permits, all of which are acceptable as a default. */
const ALLOWED_DATA_ENCRYPTION = [AES_128_CBC, AES_256_CBC, 'http://www.w3.org/2009/xmlenc11#aes256-gcm'];

let fixture: SamlLoopbackFixture;

const captureResponse = () =>
  captureSamlResponseXml(fixture.saml.domains, fixture.saml.clientOpenIdConfiguration, TEST_USER.username, TEST_USER.password);

beforeAll(async () => {
  fixture = await setupSamlLoopbackFixture();
});

beforeEach(async () => {
  await fixture.resetToBaseline();
  await fixture.setSamlIdpConfig({ protocolBinding: HTTP_POST });
  await fixture.setSamlSettings({ nameIdMapping: SAML_LOOPBACK_TEST.EL_USERNAME, assertionAttributes: null });
  await fixture.clearFederatedUsers();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

describe('SAML Encryption - assertion is encrypted', () => {
  it(jira`should emit an encrypted assertion and no readable assertion ${'AM-7107'}`, async () => {
    await fixture.setSamlSettings({ wantAssertionsEncrypted: true });

    const xml = await captureResponse();

    expect(hasEncryptedAssertion(xml)).toBe(true);
    expect(hasPlaintextAssertion(xml)).toBe(false);
    // The user's identity must not be readable in the transported response.
    expect(xml).not.toContain(TEST_USER.username);
    expect(xml).not.toContain(TEST_USER.email);
  });

  it(jira`should apply RSA-OAEP key transport with AES-256-CBC data encryption ${'AM-7107'}`, async () => {
    await fixture.setSamlSettings({
      wantAssertionsEncrypted: true,
      keyTransportEncryptionAlgorithm: RSA_OAEP,
      dataEncryptionAlgorithm: AES_256_CBC,
    });

    const { data, keyTransport } = encryptionAlgorithms(await captureResponse());

    expect(data).toEqual(AES_256_CBC);
    expect(keyTransport).toEqual(RSA_OAEP);
  });

  it(jira`should apply AES-128-CBC data encryption when configured ${'AM-7107'}`, async () => {
    await fixture.setSamlSettings({
      wantAssertionsEncrypted: true,
      keyTransportEncryptionAlgorithm: RSA_OAEP,
      dataEncryptionAlgorithm: AES_128_CBC,
    });

    expect(encryptionAlgorithms(await captureResponse()).data).toEqual(AES_128_CBC);
  });

  it(jira`should encrypt using default algorithms when none are configured ${'AM-7107'}`, async () => {
    await fixture.setSamlSettings({
      wantAssertionsEncrypted: true,
      keyTransportEncryptionAlgorithm: null,
      dataEncryptionAlgorithm: null,
    });

    const xml = await captureResponse();
    const { data, keyTransport } = encryptionAlgorithms(xml);

    expect(hasEncryptedAssertion(xml)).toBe(true);
    // Assert the security property rather than pinning exact URIs: the defaults are
    // chosen by the IdP and may legitimately change, but must never weaken to RSA1_5.
    expect(keyTransport).not.toEqual(RSA_1_5);
    expect(STRONG_KEY_TRANSPORT).toContain(keyTransport);
    expect(ALLOWED_DATA_ENCRYPTION).toContain(data);
  });
});

describe('SAML Encryption - signing and encryption combinations', () => {
  it(jira`should sign the assertion in place when signing is enabled without encryption ${'AM-7108'}`, async () => {
    await fixture.setSamlSettings({
      wantResponseSigned: true,
      wantAssertionsSigned: true,
      wantAssertionsEncrypted: false,
    });

    const xml = await captureResponse();

    expect(hasEncryptedAssertion(xml)).toBe(false);
    expect(signatureLevels(xml)).toEqual({ response: true, assertion: true });
  });

  it(jira`should leave only a response-level signature when the assertion is encrypted ${'AM-7108'}`, async () => {
    await fixture.setSamlSettings({
      wantResponseSigned: true,
      wantAssertionsSigned: false,
      wantAssertionsEncrypted: true,
    });

    const xml = await captureResponse();

    // Any assertion-level signature is sealed inside the ciphertext, so only the
    // response-level signature remains observable.
    expect(hasEncryptedAssertion(xml)).toBe(true);
    expect(signatureLevels(xml)).toEqual({ response: true, assertion: false });
  });

  it(jira`should encrypt the assertion when both signing and encryption are enabled ${'AM-7108'}`, async () => {
    await fixture.setSamlSettings({
      wantResponseSigned: true,
      wantAssertionsSigned: true,
      wantAssertionsEncrypted: true,
    });

    const xml = await captureResponse();

    expect(hasEncryptedAssertion(xml)).toBe(true);
    expect(signatureLevels(xml).response).toBe(true);
  });
});

describe('SAML Signing - HTTP-POST binding', () => {
  it(jira`should not sign the response or the assertion when both signing flags are disabled ${'AM-2564'}`, async () => {
    // The equivalent scenario under HTTP-Redirect refuses the login, because signing
    // there happens at the binding layer. On the HTTP-POST path the flags reach the
    // response builder instead, so the response is simply emitted unsigned.
    await fixture.setSamlSettings({
      wantResponseSigned: false,
      wantAssertionsSigned: false,
      wantAssertionsEncrypted: false,
    });

    const xml = await captureResponse();

    expect(signatureLevels(xml)).toEqual({ response: false, assertion: false });
    expect(hasPlaintextAssertion(xml)).toBe(true);
  });
});

describe('SAML Encryption - disabled', () => {
  it(jira`should emit a readable assertion when encryption is disabled ${'AM-7111'}`, async () => {
    await fixture.setSamlSettings({ wantAssertionsEncrypted: false });

    const xml = await captureResponse();

    expect(hasEncryptedAssertion(xml)).toBe(false);
    expect(hasPlaintextAssertion(xml)).toBe(true);
    expect(xml).toContain(TEST_USER.username);
  });
});

describe('SAML Encryption - HTTP-Redirect binding', () => {
  it(jira`should not encrypt the assertion under HTTP-Redirect binding ${'AM-7110'}`, async () => {
    await fixture.setSamlIdpConfig({ protocolBinding: HTTP_REDIRECT });
    await fixture.setSamlSettings({
      wantAssertionsEncrypted: true,
      keyTransportEncryptionAlgorithm: RSA_OAEP,
      dataEncryptionAlgorithm: AES_256_CBC,
    });

    const xml = await captureResponse();

    // The HTTP-Redirect response builder never reads the encryption settings, so the
    // assertion is transported in the clear and authentication still succeeds — the
    // configuration is silently ignored rather than refused.
    expect(hasEncryptedAssertion(xml)).toBe(false);
    expect(hasPlaintextAssertion(xml)).toBe(true);
  });
});
