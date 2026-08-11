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
  HTTP_REDIRECT_BINDING,
  KEYCLOAK_TEST,
  KeycloakSamlFixture,
  metadataUrlModeConfig,
  setupKeycloakSamlFixture,
} from './fixtures/keycloak-saml-fixture';
import { setup } from '../test-fixture';

/**
 * AM acting as a SAML service provider against Keycloak, a real third-party IdP.
 *
 * The AM-to-AM loopback in specs/gateway/saml2 proves AM can talk to itself, where both
 * ends are configured leniently and assertions are small. These cover what only a real
 * counterparty exercises: a signed assertion of realistic size, metadata published by
 * someone else, and a configuration that does not match the IdP.
 *
 * Requires `./local-stack.sh up --keycloak`.
 */
setup(200000);

let fixture: KeycloakSamlFixture;

beforeAll(async () => {
  fixture = await setupKeycloakSamlFixture(KEYCLOAK_TEST.REALM, HTTP_REDIRECT_BINDING);
});

beforeEach(async () => {
  await fixture.resetToBaseline();
  await fixture.waitForSamlIdpReady();
  await fixture.clearFederatedUsers();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

describe('Keycloak SAML - authentication against a third-party IdP', () => {
  it(jira`should authenticate a Keycloak user and federate them into the AM domain ${'AM-6799'}`, async () => {
    const response = await fixture.login(KEYCLOAK_TEST.USERNAME, KEYCLOAK_TEST.PASSWORD);

    const landing = response.headers?.location ?? '';
    expect(landing).toContain(KEYCLOAK_TEST.REDIRECT_URI);
    expect(landing).toMatch(/[?&]code=/);

    // Keycloak issues the NameID in emailAddress format, so that is what identifies
    // the federated user AM creates.
    const federated = await fixture.findFederatedUsers();
    expect(federated).toHaveLength(1);
    expect(federated[0].username).toEqual(KEYCLOAK_TEST.EMAIL);
  });

  it(jira`should authenticate when the IdP is configured from published metadata ${'AM-6810'}`, async () => {
    // Instead of each endpoint being entered by hand, AM reads Keycloak's descriptor and
    // derives the SSO endpoints and signing certificate from it.
    await fixture.setSamlIdpConfig(metadataUrlModeConfig(KEYCLOAK_TEST.REALM, fixture.signingCertificateId));
    await fixture.waitForSamlIdpReady();

    const response = await fixture.login(KEYCLOAK_TEST.USERNAME, KEYCLOAK_TEST.PASSWORD);

    expect(response.headers?.location ?? '').toMatch(/[?&]code=/);
    expect(await fixture.findFederatedUsers()).toHaveLength(1);
  });
});

describe('Keycloak SAML - misconfiguration', () => {
  it(jira`should refuse the assertion when the configured IdP certificate does not match ${'AM-6960'}`, async () => {
    // Point AM at a different realm's certificate while still talking to the first, so
    // signature validation cannot succeed.
    const otherRealmCertificate = await fixture.certificateFor(KEYCLOAK_TEST.SECOND_REALM);
    await fixture.setSamlIdpConfig(fixture.manualConfigWith({ signingCertificate: otherRealmCertificate }));
    await fixture.waitForSamlIdpReady();

    const response = await fixture.login(KEYCLOAK_TEST.USERNAME, KEYCLOAK_TEST.PASSWORD);

    expect(response.headers?.location ?? '').not.toMatch(/[?&]code=/);
    expect(await fixture.findFederatedUsers()).toHaveLength(0);
  });
});
