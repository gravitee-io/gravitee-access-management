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

import {
  HTTP_POST_BINDING,
  HTTP_REDIRECT_BINDING,
  KEYCLOAK_TEST,
  KeycloakSamlFixture,
  setupKeycloakSamlFixture,
} from './fixtures/keycloak-saml-fixture';
import { setup } from '../test-fixture';

/**
 * Checks that AM refuses assertions it must refuse when federating to a third-party IdP.
 *
 * Keycloak only ever mints well-formed assertions, so these rewrite the response in
 * transit. Each one represents a failure that would be silent in production: an
 * unsigned assertion accepted, or one minted for a different service provider accepted.
 */
setup(200000);

let fixture: KeycloakSamlFixture;

/** Remove the assertion's enveloped signature, leaving the rest of the response intact. */
const stripAssertionSignature = (xml: string): string =>
  xml.replace(/<dsig:Signature[\s\S]*?<\/dsig:Signature>/g, '').replace(/<ds:Signature[\s\S]*?<\/ds:Signature>/g, '');

const expectRejected = async (response: { headers?: Record<string, string> }) => {
  expect(response.headers?.location ?? '').not.toMatch(/[?&]code=/);
  expect(await fixture.findFederatedUsers()).toHaveLength(0);
};

beforeAll(async () => {
  fixture = await setupKeycloakSamlFixture(KEYCLOAK_TEST.REALM, HTTP_REDIRECT_BINDING);
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

describe('Keycloak SAML - assertion must be signed', () => {
  it(jira`should refuse an assertion whose signature has been removed ${'AM-6960'}`, async () => {
    const response = await fixture.loginWithTamperedAssertion(KEYCLOAK_TEST.USERNAME, KEYCLOAK_TEST.PASSWORD, stripAssertionSignature);

    await expectRejected(response);
  });
});

describe('Keycloak SAML - federated user lifecycle', () => {
  it(jira`should map the IdP attributes onto the federated user profile ${'AM-6799'}`, async () => {
    await fixture.login(KEYCLOAK_TEST.USERNAME, KEYCLOAK_TEST.PASSWORD);

    // Keycloak's protocol mappers emit email, given name and surname; losing them would
    // federate users with an empty profile and nothing would fail loudly.
    const [user] = await fixture.findFederatedUsers();
    const claims = user.additionalInformation ?? {};

    expect(user.username).toEqual(KEYCLOAK_TEST.EMAIL);
    // Keycloak's attributes reach the federated profile under the claim URIs it emits.
    expect(claims['http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress']).toEqual(KEYCLOAK_TEST.EMAIL);
    expect(claims['http://schemas.xmlsoap.org/ws/2005/05/identity/claims/givenname']).toEqual('Test');
    expect(claims['http://schemas.xmlsoap.org/ws/2005/05/identity/claims/surname']).toEqual('User');

    // Documented, not endorsed: the IdP's attributeMapping names these claims as email,
    // firstname and lastname, but the corresponding profile fields are left unset.
    expect(user.email).toBeUndefined();
    expect(user.firstName).toBeUndefined();
    expect(user.lastName).toBeUndefined();
  });

  it(jira`should match the existing federated user on re-login rather than duplicating ${'AM-6959'}`, async () => {
    await fixture.login(KEYCLOAK_TEST.USERNAME, KEYCLOAK_TEST.PASSWORD);
    const [first] = await fixture.findFederatedUsers();

    await fixture.login(KEYCLOAK_TEST.USERNAME, KEYCLOAK_TEST.PASSWORD);

    // A duplicate per login would grow the user store and split each user's history.
    const users = await fixture.findFederatedUsers();
    expect(users).toHaveLength(1);
    expect(users[0].id).toEqual(first.id);
    expect(users[0].loginsCount).toEqual(2);
  });
});

describe('Keycloak SAML - HTTP-POST AuthnRequest binding', () => {
  it(jira`should authenticate when the AuthnRequest is sent over HTTP-POST ${'AM-6819'}`, async () => {
    await fixture.setSamlIdpConfig(fixture.manualConfigWith({ protocolBinding: HTTP_POST_BINDING }));

    const response = await fixture.login(KEYCLOAK_TEST.USERNAME, KEYCLOAK_TEST.PASSWORD);

    expect(response.headers?.location ?? '').toMatch(/[?&]code=/);
    expect(await fixture.findFederatedUsers()).toHaveLength(1);
  });
});
