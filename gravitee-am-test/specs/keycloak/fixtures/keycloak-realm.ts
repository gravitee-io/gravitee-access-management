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
import { expect } from '@jest/globals';

import { performGet } from '@gateway-commands/oauth-oidc-commands';

/** Keycloak as the gateway container reaches it, and as this test process reaches it. */
const KEYCLOAK_INTERNAL = process.env.KEYCLOAK_INTERNAL_URL || 'http://keycloak:8080';
const KEYCLOAK_EXTERNAL = process.env.KEYCLOAK_URL || 'http://localhost:8180';

export const KEYCLOAK_TEST = {
  REALM: 'saml-test',
  SECOND_REALM: 'saml-test-2',
  USERNAME: 'testuser',
  PASSWORD: 'Test1234!',
  EMAIL: 'testuser@example.com',
  /** Client ID registered in the imported realm; also AM's SP entity ID. */
  SP_ENTITY_ID: 'http://localhost/am',
  DOMAIN_PREFIX: 'kc-saml',
  REDIRECT_URI: 'https://auth-nightly.gravitee.io/myApp/callback',
} as const;

export const HTTP_POST_BINDING = 'urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST';
export const HTTP_REDIRECT_BINDING = 'urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect';

/**
 * Rewrite a Keycloak-issued URL so this process can reach it.
 *
 * Keycloak advertises itself as `keycloak:8080` because that is how the gateway
 * container reaches it, and that hostname must stay in the IdP configuration. Every
 * URL Keycloak then generates — redirects, form actions — carries that host, which is
 * unresolvable from the test runner, so it is swapped for the published port.
 */
export const toReachableUrl = (url: string): string => url.split(KEYCLOAK_INTERNAL).join(KEYCLOAK_EXTERNAL);

export const realmDescriptorUrl = (realm: string, external = false): string =>
  `${external ? KEYCLOAK_EXTERNAL : KEYCLOAK_INTERNAL}/realms/${realm}/protocol/saml/descriptor`;

export const realmSsoUrl = (realm: string): string => `${KEYCLOAK_INTERNAL}/realms/${realm}/protocol/saml`;

export const realmEntityId = (realm: string): string => `${KEYCLOAK_INTERNAL}/realms/${realm}`;

/** Fetch a realm's published descriptor as XML. */
export async function fetchRealmDescriptor(realm: string): Promise<string> {
  const response = await performGet(realmDescriptorUrl(realm, true), '').expect(200);
  return response.text;
}

/** Extract the first signing certificate from a descriptor, wrapped as PEM. */
export function extractCertificatePem(descriptorXml: string): string {
  const match = /<(?:ds:)?X509Certificate>([\s\S]*?)<\/(?:ds:)?X509Certificate>/.exec(descriptorXml);
  expect(match?.[1]).toEqual(expect.any(String));
  const body = match![1].replace(/\s+/g, '');
  const wrapped = body.match(/.{1,64}/g)!.join('\n');
  return `-----BEGIN CERTIFICATE-----\n${wrapped}\n-----END CERTIFICATE-----`;
}
