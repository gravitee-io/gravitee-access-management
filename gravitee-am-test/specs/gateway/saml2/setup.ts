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
  allowHttpLocalhostRedirects,
  createDomain,
  safeDeleteDomain,
  startDomain,
  waitForDomainStart,
  waitForOidcReady,
  waitForOidcDown,
  patchDomain,
} from '@management-commands/domain-management-commands';
import { createIdp, deleteIdp, getAllIdps } from '@management-commands/idp-management-commands';
import { createCertificate, getPublicKeys } from '@management-commands/certificate-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
import { createTestApp } from '@utils-commands/application-commands';
import { expect } from '@jest/globals';
import { initiateLoginFlow, login } from '@gateway-commands/login-commands';
import { getWellKnownOpenIdConfiguration, performGet } from '@gateway-commands/oauth-oidc-commands';
import { BasicResponse, followRedirect, followRedirectTag } from '@utils-commands/misc';
import { waitForDomainReady, waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { withRetry } from '@utils-commands/retry';
import cheerio from 'cheerio';
import faker from 'faker';

const SAML_JKS_CERTIFICATE_CONFIG = JSON.stringify({
  jks: '{"name":"server.jks","type":"","size":2237,"content":"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"}',
  storepass: 'letmein',
  alias: 'mytestkey',
  keypass: 'changeme',
});

export const TEST_USER = {
  firstname: faker.name.firstName(),
  lastname: faker.name.lastName(),
  username: 'samluser',
  email: 'samluser@test.com',
  password: '#CoMpL3X-SAML-P@SsW0Rd',
};

export const FALLBACK_USER = {
  firstname: faker.name.firstName(),
  lastname: faker.name.lastName(),
  username: 'samluser-fallback',
  email: 'samluser-fallback@test.com',
  password: '#CoMpL3X-SAML-P@SsW0Rd',
};

export interface SamlTestDomains {
  providerDomain: Domain;
  clientDomain: Domain;
  providerApplication: Application;
  clientApplication: Application;
  inlineIdp: any;
  samlIdp: any;
}

export interface SamlFixture {
  domains: SamlTestDomains;
  clientOpenIdConfiguration: any;
  login: (username: string, password: string) => Promise<BasicResponse>;
  expectRedirectToClient: (response: BasicResponse) => Promise<string>;
  cleanup: () => Promise<void>;
}

export interface SamlProviderDomain {
  domain: Domain;
  accessToken: string;
  inlineIdp: any;
  certificatePem: string;
}

/**
 * Create and start a single shared SAML provider domain.
 * This domain can be reused by multiple client domains, avoiding the cost of
 * creating a separate provider per test variant.
 */
export async function setupSamlProviderDomain(domainSuffix: string): Promise<SamlProviderDomain> {
  const accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();

  // Create provider domain
  const providerDomain = await createDomain(accessToken, `saml-provider-${domainSuffix}`, 'Shared SAML Provider Domain for testing').then(
    (domain) => allowHttpLocalhostRedirects(domain, accessToken),
  );
  expect(providerDomain).toBeDefined();
  expect(providerDomain.id).toBeDefined();

  // Replace default IDP with inline IDP
  const inlineIdp = await replaceDefaultIdpWithInline(providerDomain, accessToken, domainSuffix);

  // Create certificate for SAML signing
  const certificate = await createCertificate(providerDomain.id, accessToken, {
    name: `saml-certificate-${domainSuffix}`,
    type: 'javakeystore-am-certificate',
    configuration: SAML_JKS_CERTIFICATE_CONFIG,
  });

  // Enable SAML 2.0 IdP support
  await patchDomain(providerDomain.id, accessToken, {
    saml: {
      enabled: true,
      entityId: `saml-provider-${domainSuffix}`,
      certificate: certificate.id,
    },
  });

  // Start provider domain and wait for SAML metadata to be ready
  const started = await doStartDomain(providerDomain, accessToken);
  await waitForSamlMetadataReady(started.domain);

  // Fetch certificate PEM
  const certificatePem = await fetchCertificatePem(started.domain.id, accessToken, certificate.id);

  return {
    domain: started.domain,
    accessToken,
    inlineIdp,
    certificatePem,
  };
}

/**
 * Add a client domain that uses the provider domain.
 * Creates the client domain, SAML IdP, provider app on provider, and client app.
 * Waits for the provider to sync the new app before starting the client domain.
 */
export async function addClientDomain(
  provider: SamlProviderDomain,
  createIdpFn: IdpCreatorFn,
  domainSuffix: string,
): Promise<SamlTestDomains> {
  const { domain: providerDomain, accessToken, inlineIdp, certificatePem } = provider;

  // Create client domain
  const clientDomain = await createDomain(accessToken, `saml-client-${domainSuffix}`, 'SAML Client Domain for testing');
  expect(clientDomain).toBeDefined();
  expect(clientDomain.id).toBeDefined();

  // Create SAML identity provider in client domain
  const samlIdp = await createIdpFn(clientDomain, providerDomain, accessToken, domainSuffix, certificatePem);

  // Create provider application with client ID matching SAML entity ID
  const samlEntityId = JSON.parse(samlIdp.configuration).entityId;

  // Use waitForSyncAfter to ensure the provider domain picks up the new app
  const providerApplication = await waitForSyncAfter(
    providerDomain.id,
    () => createProviderApp(providerDomain, clientDomain, accessToken, inlineIdp.id, samlEntityId, true, certificatePem),
    { timeoutMillis: 60000, intervalMillis: 500 },
  );

  // Create client application
  const clientApplication = await createClientApp(clientDomain, accessToken, samlIdp.id);

  // Start client domain
  const startedClient = await startClientDomainResilient(clientDomain, accessToken);

  return {
    providerDomain,
    clientDomain: startedClient.domain,
    providerApplication,
    clientApplication,
    inlineIdp,
    samlIdp,
  };
}

type IdpCreatorFn = (
  clientDomain: Domain,
  providerDomain: Domain,
  accessToken: string,
  domainSuffix: string,
  providerCertificatePem: string,
) => Promise<any>;

async function setupSamlTestDomainsWithIdpCreator(domainSuffix: string, createIdpFn: IdpCreatorFn): Promise<SamlTestDomains> {
  let providerDomain: Domain;
  let clientDomain: Domain;

  const accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();

  // Create provider domain
  providerDomain = await createDomain(accessToken, `saml-provider-${domainSuffix}`, 'SAML Provider Domain for testing').then((domain) =>
    allowHttpLocalhostRedirects(domain, accessToken),
  );

  expect(providerDomain).toBeDefined();
  expect(providerDomain.id).toBeDefined();

  // Create client domain
  clientDomain = await createDomain(accessToken, `saml-client-${domainSuffix}`, 'SAML Client Domain for testing');

  expect(clientDomain).toBeDefined();
  expect(clientDomain.id).toBeDefined();

  // Replace default IDP with inline IDP in provider domain
  const inlineIdp = await replaceDefaultIdpWithInline(providerDomain, accessToken, domainSuffix);

  // Create certificate for SAML signing in provider domain (used by saml2-idp to sign assertions)
  const certificate = await createCertificate(providerDomain.id, accessToken, {
    name: `saml-certificate-${domainSuffix}`,
    type: 'javakeystore-am-certificate',
    configuration: SAML_JKS_CERTIFICATE_CONFIG,
  });

  // Enable SAML 2.0 IdP support on provider domain
  await patchDomain(providerDomain.id, accessToken, {
    saml: {
      enabled: true,
      entityId: `saml-provider-${domainSuffix}`,
      certificate: certificate.id,
    },
  });

  // Start provider domain to activate SAML IdP endpoints.
  // This also serves as the required synchronization point: the management API's
  // SyncManager polls for certificate events every 5 seconds, and waitForDomainStart
  // takes long enough (10-30 s) to guarantee the DEPLOY event is processed and the
  // certificate provider is loaded before we call fetchCertificatePem.
  await doStartDomain(providerDomain, accessToken);

  const providerCertificatePem = await fetchCertificatePem(providerDomain.id, accessToken, certificate.id);

  // Create SAML identity provider in client domain first to get entity ID
  const samlIdp = await createIdpFn(clientDomain, providerDomain, accessToken, domainSuffix, providerCertificatePem);

  // Create provider application with client ID that matches SAML entity ID
  const samlEntityId = JSON.parse(samlIdp.configuration).entityId;

  const providerApplication = await createProviderApp(
    providerDomain,
    clientDomain,
    accessToken,
    inlineIdp.id,
    samlEntityId,
    true,
    providerCertificatePem,
  );

  // Create client application
  const clientApplication = await createClientApp(clientDomain, accessToken, samlIdp.id);

  // Start both domains.
  // Provider must be fully ready (including SAML metadata endpoint) before client starts,
  // because METADATA_URL-configured IdPs fetch metadata at deploy time.
  const startedProviderDomain = await doStartDomain(providerDomain, accessToken);
  await waitForSamlMetadataReady(providerDomain);
  const startedClientDomain = await startClientDomainResilient(clientDomain, accessToken);

  return {
    providerDomain: startedProviderDomain.domain,
    clientDomain: startedClientDomain.domain,
    providerApplication,
    clientApplication,
    inlineIdp,
    samlIdp,
  };
}

export async function setupSamlTestDomains(domainSuffix: string): Promise<SamlTestDomains> {
  return setupSamlTestDomainsWithIdpCreator(domainSuffix, createSamlProvider);
}

export async function setupSamlTestDomainsViaMetadataUrl(domainSuffix: string): Promise<SamlTestDomains> {
  return setupSamlTestDomainsWithIdpCreator(domainSuffix, createSamlProviderViaMetadataUrl);
}

export async function setupSamlTestDomainsViaMetadataFile(domainSuffix: string): Promise<SamlTestDomains> {
  return setupSamlTestDomainsWithIdpCreator(domainSuffix, createSamlProviderViaMetadataFile);
}

async function replaceDefaultIdpWithInline(domain: Domain, accessToken: string, domainSuffix: string) {
  await ensureDefaultIdpIsDeleted(domain, accessToken);

  return createIdp(domain.id, accessToken, {
    external: false,
    type: 'inline-am-idp',
    domainWhitelist: [],
    configuration: JSON.stringify({
      users: [TEST_USER, FALLBACK_USER],
    }),
    name: `inmemory-${domainSuffix}`,
  }).then((newIdp) => {
    expect(newIdp).toBeDefined();
    return newIdp;
  });
}

async function fetchCertificatePem(domainId: string, accessToken: string, certificateId: string): Promise<string> {
  const keys = await getPublicKeys(domainId, accessToken, certificateId);
  const pem = keys.find((k) => k.fmt === 'PEM')?.payload;
  expect(pem).toBeDefined();
  return pem;
}

export async function createSamlProvider(
  clientDomain: Domain,
  providerDomain: Domain,
  accessToken: string,
  domainSuffix: string,
  providerCertificatePem: string,
) {
  const samlIdpConfig = {
    entityId: `saml-idp-${domainSuffix}`,
    signInUrl: `${process.env.AM_GATEWAY_URL}/${providerDomain.hrid}/saml2/idp/SSO`,
    singleSignOnServiceUrl: `${process.env.AM_GATEWAY_URL}/${providerDomain.hrid}/saml2/idp/SSO`,
    singleLogoutServiceUrl: `${process.env.AM_GATEWAY_URL}/${providerDomain.hrid}/saml2/idp/logout`,
    wantAssertionsSigned: false,
    wantResponsesSigned: false,
    protocolBinding: 'urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect',
    signatureAlgorithm: 'RSA_SHA256',
    digestAlgorithm: 'SHA256',
    nameIDFormat: 'urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified',
    signingCertificate: providerCertificatePem,
    attributeMapping: {
      'http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress': 'email',
      'http://schemas.xmlsoap.org/ws/2005/05/identity/claims/givenname': 'firstname',
      'http://schemas.xmlsoap.org/ws/2005/05/identity/claims/surname': 'lastname',
    },
  };

  return createIdp(clientDomain.id, accessToken, {
    name: `saml-idp-${domainSuffix}`,
    type: 'saml2-generic-am-idp',
    configuration: JSON.stringify(samlIdpConfig),
    external: true,
  }).then((newIdp) => {
    expect(newIdp).toBeDefined();
    return newIdp;
  });
}

export function getProviderMetadataUrl(providerDomain: Domain): string {
  return `${process.env.AM_GATEWAY_URL}/${providerDomain.hrid}/saml2/idp/metadata`;
}

// WireMock base URLs — the gateway reaches it on the Docker network (INTERNAL_SFR_URL), the test runner on the
// host (SFR_URL). Same env vars the http-factor tests use, so this resolves identically locally and in CI.
const wiremockGatewayFacingBase = (): string => process.env.INTERNAL_SFR_URL || 'http://wiremock:8080';
const wiremockTestFacingBase = (): string => process.env.SFR_URL || 'http://localhost:8181';
const samlMetadataStubPath = (providerHrid: string): string => `/${providerHrid}/saml2/idp/metadata`;

/** METADATA_URL value stored in the client IdP config — points at WireMock, reachable from the gateway. */
export function getWiremockMetadataUrl(providerDomain: Domain): string {
  return `${wiremockGatewayFacingBase()}${samlMetadataStubPath(providerDomain.hrid)}`;
}

/** Same stub, addressed from the test runner (host) for direct assertions. */
export function getWiremockMetadataUrlExternal(providerDomain: Domain): string {
  return `${wiremockTestFacingBase()}${samlMetadataStubPath(providerDomain.hrid)}`;
}

// Publish the provider's real SAML metadata to WireMock. The client domain's METADATA_URL IdP fetches its
// metadata once, at deploy time; pointing that fetch at WireMock (an idle container) instead of looping back
// into the gateway while it is busy deploying that same client domain removes the deploy-time-fetch flake.
async function stubProviderMetadataInWiremock(providerDomain: Domain): Promise<void> {
  const metadataXml = (await withRetry(() => performGet(getProviderMetadataUrl(providerDomain), '').expect(200), 60, 500)).text;
  const res = await fetch(`${wiremockTestFacingBase()}/__admin/mappings`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      request: { method: 'GET', urlPath: samlMetadataStubPath(providerDomain.hrid) },
      response: { status: 200, headers: { 'Content-Type': 'application/xml' }, body: metadataXml },
    }),
  });
  expect(res.ok).toBe(true);
}

async function createSamlProviderViaMetadataUrl(
  clientDomain: Domain,
  providerDomain: Domain,
  accessToken: string,
  domainSuffix: string,
  _providerCertificatePem: string,
) {
  // Create certificate in client domain for signing AuthnRequests (required when WantAuthnRequestsSigned=true in IdP metadata)
  const clientCertificate = await createCertificate(clientDomain.id, accessToken, {
    name: `saml-sign-cert-${domainSuffix}`,
    type: 'javakeystore-am-certificate',
    configuration: SAML_JKS_CERTIFICATE_CONFIG,
  });

  // Serve the provider metadata from WireMock and point the client IdP at it (see stubProviderMetadataInWiremock).
  await stubProviderMetadataInWiremock(providerDomain);

  const samlIdpConfig = {
    idpMetadataProvider: 'METADATA_URL',
    entityId: `saml-idp-${domainSuffix}`,
    idpMetadataUrl: getWiremockMetadataUrl(providerDomain),
    graviteeCertificate: clientCertificate.id,
    requestSigningAlgorithm: 'http://www.w3.org/2001/04/xmldsig-more#rsa-sha256',
    attributeMapping: {
      'http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress': 'email',
      'http://schemas.xmlsoap.org/ws/2005/05/identity/claims/givenname': 'firstname',
      'http://schemas.xmlsoap.org/ws/2005/05/identity/claims/surname': 'lastname',
    },
  };

  return createIdp(clientDomain.id, accessToken, {
    name: `saml-idp-${domainSuffix}`,
    type: 'saml2-generic-am-idp',
    configuration: JSON.stringify(samlIdpConfig),
    external: true,
  }).then((newIdp) => {
    expect(newIdp).toBeDefined();
    return newIdp;
  });
}

async function createSamlProviderViaMetadataFile(
  clientDomain: Domain,
  providerDomain: Domain,
  accessToken: string,
  domainSuffix: string,
  _providerCertificatePem: string,
) {
  // Create certificate in client domain for signing AuthnRequests (required when WantAuthnRequestsSigned=true in IdP metadata)
  const clientCertificate = await createCertificate(clientDomain.id, accessToken, {
    name: `saml-sign-cert-${domainSuffix}`,
    type: 'javakeystore-am-certificate',
    configuration: SAML_JKS_CERTIFICATE_CONFIG,
  });

  const metadataUrl = getProviderMetadataUrl(providerDomain);
  const metadataXml = (await performGet(metadataUrl, '').expect(200)).text;

  const samlIdpConfig = {
    idpMetadataProvider: 'METADATA_FILE',
    entityId: `saml-idp-${domainSuffix}`,
    idpMetadataFile: metadataXml,
    graviteeCertificate: clientCertificate.id,
    requestSigningAlgorithm: 'http://www.w3.org/2001/04/xmldsig-more#rsa-sha256',
    attributeMapping: {
      'http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress': 'email',
      'http://schemas.xmlsoap.org/ws/2005/05/identity/claims/givenname': 'firstname',
      'http://schemas.xmlsoap.org/ws/2005/05/identity/claims/surname': 'lastname',
    },
  };

  return createIdp(clientDomain.id, accessToken, {
    name: `saml-idp-${domainSuffix}`,
    type: 'saml2-generic-am-idp',
    configuration: JSON.stringify(samlIdpConfig),
    external: true,
  }).then((newIdp) => {
    expect(newIdp).toBeDefined();
    return newIdp;
  });
}

// A new function specifically for SAML Provider apps
async function createProviderApp(
  domain: Domain,
  clientDomain: Domain,
  accessToken: string,
  idpId: string,
  clientId: string,
  signAssertions: boolean,
  spCertificatePem: string,
): Promise<Application> {
  const appName = 'saml-provider-app-' + Math.random().toString(36).substring(7);
  const redirectUri = process.env.AM_GATEWAY_URL + '/' + clientDomain.hrid + '/login/callback';

  // Define only the settings that are specific to a Provider
  const providerSettings = {
    oauth: {
      redirectUris: [redirectUri],
      singleSignOut: true,
      grantTypes: ['authorization_code', 'refresh_token'],
      responseTypes: ['code'],
    },
    saml: {
      attributeConsumeServiceUrl: `${process.env.AM_GATEWAY_URL}/${domain.hrid}/login/callback`,
      singleLogoutServiceUrl: `${process.env.AM_GATEWAY_URL}/${domain.hrid}/logout`,
      entityId: clientId,
      wantResponseSigned: false,
      wantAssertionsSigned: signAssertions,
      wantAssertionsEncrypted: false,
      responseBinding: 'HTTP-POST',
      // X.509 PEM certificate of the SP; saml2-idp uses this to verify signed AuthnRequests.
      certificate: spCertificatePem,
    },
  };

  // Call the common function with the specific settings
  return createTestApp(appName, domain, accessToken, 'web', {
    // clientId: clientId, // Pass the specific client ID here
    settings: providerSettings,
    identityProviders: new Set([{ identity: idpId, priority: -1 }]),
  });
}

// A new function for SAML Client apps (which might just be createTestApp)
async function createClientApp(domain: Domain, accessToken: string, idpId: string): Promise<Application> {
  const appName = 'saml-client-app-' + Math.random().toString(36).substring(7);
  const redirectUri = 'https://auth-nightly.gravitee.io/myApp/callback';

  const clientSettings = {
    oauth: {
      redirectUris: [redirectUri],
      grantTypes: ['authorization_code', 'refresh_token'],
      responseTypes: ['code'],
    },
  };

  return createTestApp(appName, domain, accessToken, 'web', {
    settings: clientSettings,
    identityProviders: new Set([{ identity: idpId, priority: -1 }]),
  });
}

async function ensureDefaultIdpIsDeleted(domain: Domain, accessToken: string) {
  await deleteIdp(domain.id, accessToken, 'default-idp-' + domain.id);
  const idpSet = await getAllIdps(domain.id, accessToken);
  expect(idpSet).toHaveLength(0);
}

async function doStartDomain(domain: Domain, accessToken: string) {
  const enabledDomain = await startDomain(domain.id, accessToken);
  await waitForDomainReady(enabledDomain.id, { timeoutMillis: 120000, intervalMillis: 500 });
  const oidcResponse = await waitForOidcReady(enabledDomain.hrid, { timeoutMs: 120000, intervalMs: 500 });
  console.log(`domain "${enabledDomain.hrid}" ready (SAML setup)`);
  return { domain: enabledDomain, oidcConfig: oidcResponse.body };
}

// The client's METADATA_URL SAML IdP fetches the provider metadata once, at deploy time, via a 20s
// blockingAwait that runs on the gateway's own event loop — the same loop busy deploying this domain.
// Serving the metadata from WireMock (see stubProviderMetadataInWiremock) makes the response instant and
// removes the loopback contention, but the fetch is still initiated and completed on the busy deploy path,
// so it can still time out when deploy work hogs the loop (deploy succeeds but the IdP loads without a
// signInUrl) or, on a non-200, fail the deploy outright. Redeploying re-runs the fetch against fast
// WireMock, which recovers reliably. Safety net on top of the WireMock hosting.
async function startClientDomainResilient(clientDomain: Domain, accessToken: string) {
  const maxRedeploys = 2;
  for (let attempt = 0; ; attempt++) {
    try {
      return await doStartDomain(clientDomain, accessToken);
    } catch (err) {
      if (attempt >= maxRedeploys) {
        throw err;
      }
      console.log(
        `client domain "${clientDomain.hrid}" did not become ready (${(err as Error).message}) — ` +
          `redeploying to re-trigger the SAML IdP metadata fetch (${attempt + 1}/${maxRedeploys})`,
      );
      await patchDomain(clientDomain.id, accessToken, { enabled: false });
      await waitForOidcDown(clientDomain.hrid, { timeoutMs: 30000, intervalMs: 500 });
    }
  }
}

export async function setupSamlProviderTest(
  domainSuffix: string,
  setupFn: (suffix: string) => Promise<SamlTestDomains> = setupSamlTestDomains,
  provider?: SamlProviderDomain,
  createIdpFn: IdpCreatorFn = createSamlProvider,
): Promise<SamlFixture> {
  const accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();

  let domains: SamlTestDomains;
  if (provider) {
    domains = await addClientDomain(provider, createIdpFn, domainSuffix);
  } else {
    domains = await setupFn(domainSuffix);
  }

  // Wait for domains to be fully ready and get OIDC configuration
  let clientOpenIdConfiguration: any;

  const response = await getWellKnownOpenIdConfiguration(domains.clientDomain.hrid);
  if (response && response.status === 200) {
    clientOpenIdConfiguration = response.body;
  }

  expect(clientOpenIdConfiguration).toBeDefined();
  expect(clientOpenIdConfiguration.authorization_endpoint).toBeDefined();

  const findSamlProviderLink = (html: string): string | undefined => {
    const dom = cheerio.load(html);
    return (
      dom('.btn-saml2-generic-am-idp').attr('href') ||
      dom(`a[href*="saml2"]`).attr('href') ||
      dom(`a[href*="${domains.samlIdp.id}"]`).attr('href')
    );
  };

  // Poll the client login page for the SAML provider button, returning its href once it renders
  // (or undefined if it never appears within the window).
  const pollForSamlProviderLink = async (
    headers: Record<string, string>,
    loginPageUrl: string,
    maxAttempts: number,
  ): Promise<string | undefined> => {
    for (let attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        const result = await performGet(loginPageUrl, '', headers).expect(200);
        const link = findSamlProviderLink(result.text);
        if (link) {
          return link;
        }
      } catch (err) {
        // Transient non-200 during a redeploy/route-wiring window — treat as "not ready yet" and keep polling.
        console.debug(`SAML login page not ready (attempt ${attempt}/${maxAttempts}): ${err instanceof Error ? err.message : err}`);
      }
      if (attempt < maxAttempts) {
        await new Promise((r) => setTimeout(r, 1000));
      }
    }
    return undefined;
  };

  const initiateSamlLoginPage = async (): Promise<{ headers: Record<string, string>; loginPageUrl: string }> => {
    const response = await initiateLoginFlow(
      domains.clientApplication.settings.oauth.clientId,
      clientOpenIdConfiguration,
      domains.clientDomain,
    );
    const headers = response.headers['set-cookie'] ? { Cookie: response.headers['set-cookie'] } : {};
    return { headers, loginPageUrl: response.headers['location'] };
  };

  // Mode-2 safety net: even with the metadata served from WireMock, the client's one-shot deploy-time fetch
  // can time out on a busy gateway event loop, leaving the IdP with no signInUrl so the SAML button never
  // renders. Redeploying the client domain re-inits the IdP bean and re-runs the fetch (against fast
  // WireMock), which recovers. Complements startClientDomainResilient, which covers the deploy-fails case.
  const ensureClientSamlIdpReady = async () => {
    const maxRedeploys = 2;
    for (let attempt = 0; attempt <= maxRedeploys; attempt++) {
      const { headers, loginPageUrl } = await initiateSamlLoginPage();
      if (await pollForSamlProviderLink(headers, loginPageUrl, 15)) {
        return;
      }
      if (attempt < maxRedeploys) {
        console.log(
          `SAML login button not rendered for client domain "${
            domains.clientDomain.hrid
          }" — redeploying to re-trigger the METADATA_URL fetch (${attempt + 1}/${maxRedeploys})`,
        );
        await patchDomain(domains.clientDomain.id, accessToken, { enabled: false });
        await waitForOidcDown(domains.clientDomain.hrid, { timeoutMs: 30000, intervalMs: 500 });
        await startDomain(domains.clientDomain.id, accessToken);
        await waitForDomainReady(domains.clientDomain.id, { timeoutMillis: 120000, intervalMillis: 500 });
        await waitForOidcReady(domains.clientDomain.hrid, { timeoutMs: 120000, intervalMs: 500 });
      }
    }
    throw new Error(
      `SAML IdP for client domain "${domains.clientDomain.hrid}" never became ready after ${maxRedeploys} redeploys ` +
        `(METADATA_URL metadata fetch kept failing at deploy time)`,
    );
  };

  const navigateToSamlProviderLogin = async (response: BasicResponse) => {
    const headers = response.headers['set-cookie'] ? { Cookie: response.headers['set-cookie'] } : {};
    const loginPageUrl = response.headers['location'];

    // ensureClientSamlIdpReady already confirmed the button renders; keep a generous poll for router lag.
    const samlProviderLoginUrl = await pollForSamlProviderLink(headers, loginPageUrl, 60);
    expect(samlProviderLoginUrl).toBeDefined();
    return await performGet(samlProviderLoginUrl, '', headers).expect(302);
  };

  const expectRedirectToClient = async (clientAuthorizeResponse: BasicResponse) => {
    expect(clientAuthorizeResponse.status).toBe(302);
    const location = clientAuthorizeResponse.header['location'];
    expect(location).toMatch(new RegExp(/^/.source + domains.clientApplication.settings.oauth.redirectUris[0]));
    return location.match(/\?code=([^&]+)/)?.[1];
  };

  // Ensure the client's SAML IdP has loaded the provider metadata (redeploying if the one-shot deploy-time
  // fetch timed out) before exercising the login flow.
  await ensureClientSamlIdpReady();

  // Pre-authenticate user to handle consents
  await initiateLoginFlow(domains.clientApplication.settings.oauth.clientId, clientOpenIdConfiguration, domains.clientDomain)
    .then((response) => navigateToSamlProviderLogin(response))
    .then((response) => login(response, TEST_USER.username, domains.providerApplication.settings.oauth.clientId, TEST_USER.password))
    .then(followRedirectTag('saml-1'))
    .then(followRedirectTag('saml-2'))
    .then(() => {});

  return {
    domains,
    clientOpenIdConfiguration,
    login: async (username: string, password: string) => {
      const clientId = domains.clientApplication.settings.oauth.clientId;
      return initiateLoginFlow(clientId, clientOpenIdConfiguration, domains.clientDomain)
        .then((response) => navigateToSamlProviderLogin(response))
        .then((samlProviderLoginResponse) => {
          // Login to the SAML provider
          return login(samlProviderLoginResponse, username, domains.providerApplication.settings.oauth.clientId, password)
            .then(followRedirectTag('saml-login-1'))
            .then(followRedirectTag('saml-login-2'))
            .then(followRedirect);
        });
    },
    expectRedirectToClient,
    cleanup: async () => {
      if (provider) {
        // Only delete the client domain — provider is managed externally
        await safeDeleteDomain(domains.clientDomain.id, accessToken);
      } else {
        await Promise.all([
          safeDeleteDomain(domains.clientDomain.id, accessToken),
          safeDeleteDomain(domains.providerDomain.id, accessToken),
        ]);
      }
    },
  };
}

export function setupSamlProviderTestViaMetadataUrl(domainSuffix: string, provider?: SamlProviderDomain): Promise<SamlFixture> {
  return setupSamlProviderTest(domainSuffix, setupSamlTestDomainsViaMetadataUrl, provider, createSamlProviderViaMetadataUrl);
}

export function setupSamlProviderTestViaMetadataFile(domainSuffix: string, provider?: SamlProviderDomain): Promise<SamlFixture> {
  return setupSamlProviderTest(domainSuffix, setupSamlTestDomainsViaMetadataFile, provider, createSamlProviderViaMetadataFile);
}

/**
 * Poll the SAML metadata endpoint until it returns 200.
 * After domain start, the SAML IdP handler may take additional time to initialize
 * even though OIDC routes are already serving.
 */
async function waitForSamlMetadataReady(providerDomain: Domain): Promise<void> {
  const metadataUrl = getProviderMetadataUrl(providerDomain);
  await withRetry(() => performGet(metadataUrl, '').expect(200), 60, 500);
}

export async function cleanupSamlTestDomains(accessToken: string, domains: SamlTestDomains): Promise<void> {
  return Promise.all([
    safeDeleteDomain(domains.clientDomain.id, accessToken),
    safeDeleteDomain(domains.providerDomain.id, accessToken),
  ]).then(() => {});
}
