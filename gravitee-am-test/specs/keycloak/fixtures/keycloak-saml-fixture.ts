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

import {
  KEYCLOAK_TEST,
  HTTP_POST_BINDING,
  HTTP_REDIRECT_BINDING,
  extractCertificatePem,
  fetchRealmDescriptor,
  realmDescriptorUrl,
  realmSsoUrl,
  toReachableUrl,
} from './keycloak-realm';
import { CookieJar, getWithJar, postWithJar, submitAutoPostForm } from './saml-http-flow';
import { expect } from '@jest/globals';
import cheerio from 'cheerio';

import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { safeDeleteDomain, setupDomainForTest, waitForDomainSync } from '@management-commands/domain-management-commands';
import { createIdp, updateIdp } from '@management-commands/idp-management-commands';
import { getAllCertificates } from '@management-commands/certificate-management-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { listUsers, getAllUsers, deleteUser } from '@management-commands/user-management-commands';
import { performGet, performPost } from '@gateway-commands/oauth-oidc-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { initiateLoginFlow } from '@gateway-commands/login-commands';
import { uniqueName, BasicResponse } from '@utils-commands/misc';
import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
import { User } from '@management-models/User';

/**
 * Environment for exercising AM's SAML SP plugin against Keycloak rather than
 * against another AM domain.
 *
 * Keycloak is started by `./local-stack.sh up --keycloak` with its realms imported
 * from checked-in JSON. Realm signing keys are generated per container, so the IdP
 * certificate is read from the published descriptor at setup rather than hardcoded.
 */

export interface KeycloakSamlFixture {
  domain: Domain;
  application: Application;
  samlIdp: any;
  accessToken: string;
  openIdConfiguration: any;
  /** The IdP signing certificate, read from the realm descriptor at setup. */
  idpCertificatePem: string;

  /** Replace the SAML IdP configuration wholesale and wait for the gateway to pick it up. */
  setSamlIdpConfig: (configuration: Record<string, unknown>) => Promise<void>;
  /** Authenticate through Keycloak; resolves to the final response of the flow. */
  login: (username: string, password: string) => Promise<BasicResponse>;
  /** Federated users created in the AM domain by a successful login. */
  findFederatedUsers: (query?: string) => Promise<User[]>;
  /** The SAML sign-in link on the AM login page, or undefined when the IdP failed to deploy. */
  findSamlSignInLink: () => Promise<string | undefined>;
  /** Restore the IdP configuration captured at setup. */
  resetToBaseline: () => Promise<void>;
  /** Block until the SAML sign-in link renders, redeploying the IdP if the fetch lost. */
  waitForSamlIdpReady: () => Promise<void>;
  /** Remove every federated user, so each scenario starts clean. */
  clearFederatedUsers: () => Promise<void>;
  /** Signing certificate of any realm, read from its descriptor. */
  certificateFor: (realm: string) => Promise<string>;
  /**
   * Authenticate, but rewrite the SAML response before AM receives it.
   *
   * Used to prove AM refuses assertions it should refuse — the IdP itself will only
   * ever mint well-formed ones, so the tampering has to happen in transit.
   */
  loginWithTamperedAssertion: (username: string, password: string, tamper: (xml: string) => string) => Promise<BasicResponse>;
  /** The working MANUAL-mode configuration, with optional overrides applied. */
  manualConfigWith: (overrides: Record<string, unknown>) => Record<string, unknown>;
  /** Id of a certificate in the AM domain, for signing AuthnRequests. */
  signingCertificateId: string;

  cleanup: () => Promise<void>;
}

/**
 * MANUAL-mode configuration for a SAML IdP pointed at a Keycloak realm.
 *
 * Every key below is declared in the saml2-generic-am-idp schema. Unknown keys are
 * silently dropped rather than rejected, so it is worth checking the plugin's
 * schema-form.json before adding one.
 */
export function manualModeConfig(
  realm: string,
  certificatePem: string,
  protocolBinding: string,
  graviteeCertificateId: string,
): Record<string, unknown> {
  return {
    idpMetadataProvider: 'MANUAL',
    entityId: KEYCLOAK_TEST.SP_ENTITY_ID,
    signInUrl: realmSsoUrl(realm),
    signOutUrl: realmSsoUrl(realm),
    signingCertificate: certificatePem,
    protocolBinding,
    nameIdFormat: 'urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress',
    // Keycloak's descriptor advertises WantAuthnRequestsSigned, so AM must sign.
    requestSigned: true,
    requestSigningAlgorithm: 'http://www.w3.org/2001/04/xmldsig-more#rsa-sha256',
    graviteeCertificate: graviteeCertificateId,
  };
}

/**
 * METADATA_URL-mode configuration: AM derives the endpoints and signing certificate
 * from the IdP's published descriptor instead of each being supplied by hand.
 */
export function metadataUrlModeConfig(realm: string, graviteeCertificateId: string): Record<string, unknown> {
  return {
    idpMetadataProvider: 'METADATA_URL',
    idpMetadataUrl: realmDescriptorUrl(realm),
    entityId: KEYCLOAK_TEST.SP_ENTITY_ID,
    requestSigned: true,
    requestSigningAlgorithm: 'http://www.w3.org/2001/04/xmldsig-more#rsa-sha256',
    graviteeCertificate: graviteeCertificateId,
  };
}

/**
 * Submit Keycloak's login form.
 *
 * Keycloak renders its own form with a one-time action URL rather than the XSRF-token
 * shape AM uses, so the shared login helper cannot drive it.
 */
async function submitKeycloakLogin(jar: CookieJar, loginPage: BasicResponse, username: string, password: string): Promise<BasicResponse> {
  const dom = cheerio.load(loginPage.text);
  const action = dom('form#kc-form-login').attr('action') ?? dom('form').attr('action');
  expect(action).toEqual(expect.any(String));

  return postWithJar(jar, toReachableUrl(action!), { username, password });
}

export const setupKeycloakSamlFixture = async (
  realm: string = KEYCLOAK_TEST.REALM,
  protocolBinding: string = HTTP_REDIRECT_BINDING,
): Promise<KeycloakSamlFixture> => {
  let domain: Domain | null = null;
  let accessToken: string | null = null;

  try {
    accessToken = await requestAdminAccessToken();

    const idpCertificatePem = extractCertificatePem(await fetchRealmDescriptor(realm));

    const setupResult = await setupDomainForTest(uniqueName(KEYCLOAK_TEST.DOMAIN_PREFIX, true).toLowerCase(), {
      accessToken,
      waitForStart: true,
    });
    domain = setupResult.domain;
    const openIdConfiguration = setupResult.oidcConfig;

    // Keycloak's descriptor sets WantAuthnRequestsSigned, so AM signs its AuthnRequests
    // using a certificate from the domain. Every domain is created with a default one.
    const certificates = await getAllCertificates(domain.id, accessToken);
    const signingCertificate = [...certificates][0];
    expect(signingCertificate?.id).toEqual(expect.any(String));

    // Wrap the mutation so the sync watermark is captured before the call — creating the
    // IdP and then polling separately races the deployer and can miss the cycle entirely.
    const samlIdp = await waitForSyncAfter(domain.id, () =>
      createIdp(domain!.id, accessToken!, {
        name: `keycloak-saml-${realm}`,
        type: 'saml2-generic-am-idp',
        configuration: JSON.stringify(manualModeConfig(realm, idpCertificatePem, protocolBinding, signingCertificate.id)),
        external: true,
      }),
    );

    const application = await waitForSyncAfter(domain.id, () =>
      createTestApp('kc-saml-app', domain!, accessToken!, 'web', {
        settings: {
          oauth: {
            redirectUris: [KEYCLOAK_TEST.REDIRECT_URI],
            grantTypes: ['authorization_code'],
            responseTypes: ['code'],
          },
        },
        identityProviders: new Set([{ identity: samlIdp.id, priority: -1 }]),
      }),
    );

    const setSamlIdpConfig = async (configuration: Record<string, unknown>): Promise<void> => {
      await waitForSyncAfter(domain!.id, () =>
        updateIdp(
          domain!.id,
          accessToken!,
          { name: samlIdp.name, type: samlIdp.type, configuration: JSON.stringify(configuration) },
          samlIdp.id,
        ),
      );
    };

    const findSamlSignInLink = async (): Promise<string | undefined> => {
      const authorize = await initiateLoginFlow(application.settings.oauth.clientId, openIdConfiguration, domain!);
      const cookies = authorize.headers['set-cookie'];
      const loginPage = await performGet(authorize.headers['location'], '', cookies ? { Cookie: cookies } : {});
      const dom = cheerio.load(loginPage.text ?? '');
      return dom('.btn-saml2-generic-am-idp').attr('href') || dom('a[href*="saml2"]').attr('href');
    };

    const login = async (username: string, password: string, tamper?: (xml: string) => string): Promise<BasicResponse> => {
      const jar = new CookieJar();

      const authorize = await initiateLoginFlow(application.settings.oauth.clientId, openIdConfiguration, domain!);
      const loginPageUrl = authorize.headers['location'];
      jar.store(loginPageUrl, authorize.headers['set-cookie']);

      const loginPage = await getWithJar(jar, loginPageUrl);
      const dom = cheerio.load(loginPage.text ?? '');
      const samlLink = dom('.btn-saml2-generic-am-idp').attr('href') || dom('a[href*="saml2"]').attr('href');
      expect(samlLink).toEqual(expect.any(String));

      // AM embeds the IdP URL and AuthnRequest directly in the sign-in link, so it points
      // at the container hostname and must be rewritten for this process.
      // Getting to Keycloak's login form takes a different number of hops depending on
      // how the IdP is configured: manual mode redirects straight there, metadata mode
      // adds a hop, and POST binding arrives as an auto-submitting form.
      let current = await getWithJar(jar, toReachableUrl(samlLink!));
      for (let hop = 0; hop < 5; hop++) {
        if (current.status === 200 && (current.text ?? '').includes('SAMLRequest')) {
          current = await submitAutoPostForm(jar, current);
          continue;
        }
        if (current.status >= 300 && current.status < 400 && current.headers?.location) {
          current = await getWithJar(jar, toReachableUrl(current.headers['location']));
          continue;
        }
        break;
      }

      current = await submitKeycloakLogin(jar, current, username, password);

      for (let hop = 0; hop < 8; hop++) {
        if (current.status >= 300 && current.status < 400 && current.headers?.location) {
          const next = toReachableUrl(current.headers['location']);
          // Stop at the application's redirect URI — it is outside the stack and the
          // authorization code on it is what the caller asserts against.
          if (next.startsWith(KEYCLOAK_TEST.REDIRECT_URI)) {
            return current;
          }
          current = await getWithJar(jar, next);
          continue;
        }
        if (current.status === 200 && (current.text ?? '').includes('SAMLResponse')) {
          current = await submitAutoPostForm(jar, current, tamper);
          continue;
        }
        break;
      }
      return current;
    };

    const baselineIdpConfig = manualModeConfig(realm, idpCertificatePem, protocolBinding, signingCertificate.id);

    const manualConfigWith = (overrides: Record<string, unknown>): Record<string, unknown> => ({
      ...baselineIdpConfig,
      ...overrides,
    });

    const resetToBaseline = async (): Promise<void> => setSamlIdpConfig(baselineIdpConfig);

    const certificateFor = async (targetRealm: string): Promise<string> => extractCertificatePem(await fetchRealmDescriptor(targetRealm));

    const loginWithTamperedAssertion = async (
      username: string,
      password: string,
      tamper: (xml: string) => string,
    ): Promise<BasicResponse> => login(username, password, tamper);

    /**
     * Wait for the SAML IdP to be usable.
     *
     * In METADATA_URL mode the IdP fetches the descriptor once, at deploy time. Under load
     * that fetch can lose, leaving the IdP with no signInUrl so the sign-in link never
     * renders — the test then fails for a reason unrelated to what it asserts. Re-saving
     * the configuration re-initialises the IdP and re-runs the fetch.
     */
    const waitForSamlIdpReady = async (): Promise<void> => {
      for (let attempt = 0; attempt < 3; attempt++) {
        for (let poll = 0; poll < 10; poll++) {
          if (await findSamlSignInLink()) {
            return;
          }
          await new Promise((resolve) => setTimeout(resolve, 1000));
        }
        const current = JSON.parse(samlIdp.configuration);
        await setSamlIdpConfig(current);
      }
      throw new Error('SAML IdP never became ready — the sign-in link did not render');
    };

    const findFederatedUsers = async (query?: string): Promise<User[]> => {
      // An empty query does not list everything — fall back to the full listing.
      const page = query ? await listUsers(domain!.id, accessToken!, query) : await getAllUsers(domain!.id, accessToken!);
      return page.data ?? [];
    };

    const clearFederatedUsers = async (): Promise<void> => {
      for (const user of await findFederatedUsers()) {
        await deleteUser(domain!.id, accessToken!, user.id);
      }
    };

    const cleanup = async () => {
      if (domain?.id && accessToken) {
        await safeDeleteDomain(domain.id, accessToken);
      }
    };

    return {
      domain,
      application,
      samlIdp,
      accessToken,
      openIdConfiguration,
      idpCertificatePem,
      setSamlIdpConfig,
      login,
      findFederatedUsers,
      findSamlSignInLink,
      resetToBaseline,
      waitForSamlIdpReady,
      clearFederatedUsers,
      certificateFor,
      loginWithTamperedAssertion,
      manualConfigWith,
      signingCertificateId: signingCertificate.id,
      cleanup,
    };
  } catch (error) {
    if (domain?.id && accessToken) {
      try {
        await safeDeleteDomain(domain.id, accessToken);
      } catch (cleanupError) {
        console.error('Failed to cleanup Keycloak SAML fixture after setup failure:', cleanupError);
      }
    }
    throw error;
  }
};

export { KEYCLOAK_TEST, HTTP_POST_BINDING, HTTP_REDIRECT_BINDING } from './keycloak-realm';
