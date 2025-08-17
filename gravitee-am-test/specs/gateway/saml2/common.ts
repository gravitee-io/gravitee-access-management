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
    deleteDomain,
    startDomain,
    waitForDomainStart,
    patchDomain
} from '@management-commands/domain-management-commands';
import { createIdp, deleteIdp, getAllIdps } from '@management-commands/idp-management-commands';
import { createCertificate } from '@management-commands/certificate-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createApplication, updateApplication, patchApplication } from '@management-commands/application-management-commands';
import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
import { createTestApp } from '@utils-commands/application-commands';
import { expect } from '@jest/globals';
import { initiateLoginFlow, login } from '@gateway-commands/login-commands';
import {  getWellKnownOpenIdConfiguration, performGet } from '@gateway-commands/oauth-oidc-commands';
import { BasicResponse, followRedirect, followRedirectTag } from '@utils-commands/misc';
import cheerio from 'cheerio';
import faker from 'faker';

export const TEST_USER = {
    firstname: faker.name.firstName(),
    lastname: faker.name.lastName(),
    username: 'samluser',
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

export async function setupSamlTestDomains(domainSuffix: string): Promise<SamlTestDomains> {
    let providerDomain: Domain;
    let clientDomain: Domain;

    const accessToken = await requestAdminAccessToken();
    expect(accessToken).toBeDefined();

    // Create provider domain
    providerDomain = await createDomain(
        accessToken,
        `saml-provider-${domainSuffix}`,
        'SAML Provider Domain for testing'
    ).then(domain => allowHttpLocalhostRedirects(domain, accessToken));

    expect(providerDomain).toBeDefined();
    expect(providerDomain.id).toBeDefined();

    // Create client domain
    clientDomain = await createDomain(
        accessToken,
        `saml-client-${domainSuffix}`,
        'SAML Client Domain for testing'
    );

    expect(clientDomain).toBeDefined();
    expect(clientDomain.id).toBeDefined();

    // Replace default IDP with inline IDP in provider domain
    const inlineIdp = await replaceDefaultIdpWithInline(providerDomain, accessToken, domainSuffix);

    // Create certificate for SAML signing
    const certificate = await createCertificate(providerDomain.id, accessToken, {
        name: `saml-certificate-${domainSuffix}`,
        type: 'javakeystore-am-certificate',
        configuration: JSON.stringify({
            jks: '{"name":"server.jks","type":"","size":2237,"content":"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"}',
            storepass: 'letmein',
            alias: 'mytestkey',
            keypass: 'changeme'
        })
    });

    // Enable SAML 2.0 IdP support on provider domain
    await patchDomain(providerDomain.id, accessToken, {
        saml: {
            enabled: true,
            entityId: `saml-provider-${domainSuffix}`,
            certificate: certificate.id
        }
    });

    // Wait for SAML configuration to be applied and restart domain to activate SAML IdP service
    await new Promise(resolve => setTimeout(resolve, 1000));

    // Restart provider domain to activate SAML IdP endpoints
    await doStartDomain(providerDomain, accessToken);

    // Verify SAML IdP metadata endpoint is available
    let metadataRetries = 0;
    const maxMetadataRetries = 5;
    while (metadataRetries < maxMetadataRetries) {
        try {
            const metadataUrl = `${process.env.AM_GATEWAY_URL}/${providerDomain.hrid}/saml2/idp/metadata`;
            const metadataResponse = await performGet(metadataUrl);
            if (metadataResponse.status === 200) {
                break;
            } else if (metadataRetries === maxMetadataRetries - 1) {
                console.warn(`WARNING: SAML metadata endpoint returned ${metadataResponse.status}, SAML IdP may not be properly configured`);
            }
        } catch (error) {
            if (metadataRetries === maxMetadataRetries - 1) {
                console.error(`ERROR: SAML metadata endpoint check failed: ${error.message}`);
            }
        }
        await new Promise(resolve => setTimeout(resolve, 1000));
        metadataRetries++;
    }

    // Create SAML identity provider in client domain first to get entity ID
    const samlIdp = await createSamlProvider(clientDomain, providerDomain, accessToken, domainSuffix);

    // Create provider application with client ID that matches SAML entity ID
    const samlEntityId = JSON.parse(samlIdp.configuration).entityId;

    const providerApplication = await createAppWithSpecificClientId(
        `saml-provider-app-${domainSuffix}`,
        providerDomain,
        accessToken,
        inlineIdp.id,
        process.env.AM_GATEWAY_URL + '/' + clientDomain.hrid + '/login/callback',
        samlEntityId,  // Use SAML entity ID as client ID
        true,  // This is a provider app that needs SAML settings
        certificate.id  // Pass certificate ID for SAML configuration
    );

    // Wait for provider application to be available and verify client ID
    await new Promise(resolve => setTimeout(resolve, 2000));

    // Create client application
    const clientApplication = await createApp(
        `saml-client-app-${domainSuffix}`,
        clientDomain,
        accessToken,
        samlIdp.id,
        'https://auth-nightly.gravitee.io/myApp/callback'
    );

    // Start both domains
    const startedProviderDomain = await doStartDomain(providerDomain, accessToken);
    const startedClientDomain = await doStartDomain(clientDomain, accessToken);

    return {
        providerDomain: startedProviderDomain.domain,
        clientDomain: startedClientDomain.domain,
        providerApplication,
        clientApplication,
        inlineIdp,
        samlIdp
    };
}

async function replaceDefaultIdpWithInline(domain: Domain, accessToken: string, domainSuffix: string) {
    await ensureDefaultIdpIsDeleted(domain, accessToken);

    return createIdp(domain.id, accessToken, {
        external: false,
        type: 'inline-am-idp',
        domainWhitelist: [],
        configuration: JSON.stringify({
            users: [TEST_USER],
        }),
        name: `inmemory-${domainSuffix}`,
    }).then((newIdp) => {
        expect(newIdp).toBeDefined();
        return newIdp;
    });
}

async function createSamlProvider(clientDomain: Domain, providerDomain: Domain, accessToken: string, domainSuffix: string) {
    const samlIdpConfig = {
        entityId: `saml-idp-${domainSuffix}`,
        signInUrl: `${process.env.AM_GATEWAY_URL}/${providerDomain.hrid}/saml2/idp/SSO`,
        singleSignOnServiceUrl: `${process.env.AM_GATEWAY_URL}/${providerDomain.hrid}/saml2/idp/SSO`,
        singleLogoutServiceUrl: `${process.env.AM_GATEWAY_URL}/${providerDomain.hrid}/saml2/idp/SLO`,
        wantAssertionsSigned: false,
        wantResponsesSigned: false,
        signatureAlgorithm: 'RSA_SHA256',
        digestAlgorithm: 'SHA256',
        nameIDFormat: 'urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress',
        attributeMapping: {
            'http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress': 'email',
            'http://schemas.xmlsoap.org/ws/2005/05/identity/claims/givenname': 'firstname',
            'http://schemas.xmlsoap.org/ws/2005/05/identity/claims/surname': 'lastname'
        }
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

async function createAppWithSpecificClientId(name: string, domain: Domain, accessToken: string, idpId: string, redirectUri: string, clientId: string, isProviderApp: boolean = false, certificateId?: string): Promise<Application> {
    // Create application directly without using createTestApp to avoid client ID override
    const createAppSettings = {
        name: name,
        type: 'web',
        clientId: clientId,  // This will be the exact client ID we want
        redirectUris: [redirectUri]
    };

    const app = await createApplication(domain.id, accessToken, createAppSettings);
    expect(app).toBeDefined();

    // Configure OAuth and SAML settings via update
    const settings: any = {
        oauth: {
            redirectUris: [redirectUri],
            grantTypes: ['authorization_code', 'refresh_token'],
            responseTypes: ['code']
        },
    };

    // Configure SAML settings for provider applications
    if (isProviderApp && certificateId) {
        settings.saml = {
            entityId: clientId,  // Use the same entity ID as the client ID for SAML
            wantResponseSigned: false,
            wantAssertionsSigned: false,
            responseBinding: 'HTTP-POST',
            certificate: certificateId  // Reference the SAML certificate
        };
    }

    const updateBody = {
        settings,
        identityProviders: new Set([{ identity: idpId, priority: -1 }]),
    };

    const updatedApp = await updateApplication(domain.id, accessToken, updateBody, app.id);
    // Preserve the client secret from the original creation
    updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;

    expect(updatedApp).toBeDefined();
    expect(updatedApp.settings.oauth.clientId).toBe(clientId);  // Verify exact client ID match
    return updatedApp;
}

async function createApp(name: string, domain: Domain, accessToken: string, idpId: string, redirectUri: string, isProviderApp: boolean = false): Promise<Application> {
    const settings: any = {
        oauth: {
            redirectUris: [redirectUri],
            grantTypes: ['authorization_code', 'refresh_token'],
            responseTypes: ['code']
        },
    };

    // Configure SAML settings for provider applications
    if (isProviderApp) {
        settings.saml = {
            entityId: `${name}-entity`,
            wantResponseSigned: false,
            wantAssertionsSigned: false,
            responseBinding: 'HTTP-POST'
        };
    }

    const app = await createTestApp(name, domain, accessToken, 'web', {
        settings,
        identityProviders: new Set([{ identity: idpId, priority: -1 }]),
    });
    expect(app).toBeDefined();
    return app;
}

async function ensureDefaultIdpIsDeleted(domain: Domain, accessToken: string) {
    await deleteIdp(domain.id, accessToken, 'default-idp-' + domain.id);
    const idpSet = await getAllIdps(domain.id, accessToken);
    expect(idpSet).toHaveLength(0);
}

async function doStartDomain(domain: Domain, accessToken: string) {
    const started = await startDomain(domain.id, accessToken).then(waitForDomainStart);
    expect(started).toBeDefined();
    expect(started.domain.id).toEqual(domain.id);
    return started;
}

export async function setupSamlProviderTest(domainSuffix: string): Promise<SamlFixture> {
    const accessToken = await requestAdminAccessToken();
    expect(accessToken).toBeDefined();

    const domains = await setupSamlTestDomains(domainSuffix);

    // Wait for domains to be fully ready and get OIDC configuration
    let clientOpenIdConfiguration: any;
    let retries = 0;
    const maxRetries = 10;

    while (!clientOpenIdConfiguration && retries < maxRetries) {
        await new Promise(resolve => setTimeout(resolve, 1000));
        try {
            const response = await getWellKnownOpenIdConfiguration(domains.clientDomain.hrid);
            if (response && response.status === 200) {
                clientOpenIdConfiguration = response.body;
                break;
            }
        } catch (error) {
            console.log(`OIDC config attempt ${retries + 1} failed:`, error.message);
        }
        retries++;
    }

    expect(clientOpenIdConfiguration).toBeDefined();
    expect(clientOpenIdConfiguration.authorization_endpoint).toBeDefined();

    const navigateToSamlProviderLogin = async (response: BasicResponse) => {
        const headers = response.headers['set-cookie'] ? { Cookie: response.headers['set-cookie'] } : {};
        const result = await performGet(response.headers['location'], '', headers).expect(200);
        const dom = cheerio.load(result.text);

        // Find the SAML IDP login button/link
        const samlProviderLoginUrl = dom('.btn-saml2-generic-am-idp').attr('href') ||
            dom(`a[href*="saml2"]`).attr('href') ||
            dom(`a[href*="${domains.samlIdp.id}"]`).attr('href');

        expect(samlProviderLoginUrl).toBeDefined();
        return await performGet(samlProviderLoginUrl, '', headers).expect(302);
    };

    const expectRedirectToClient = async (clientAuthorizeResponse: BasicResponse) => {
        expect(clientAuthorizeResponse.status).toBe(302);
        const location = clientAuthorizeResponse.header['location'];
        expect(location).toMatch(new RegExp(/^/.source + domains.clientApplication.settings.oauth.redirectUris[0]));
        return location.match(/\?code=([^&]+)/)?.[1];
    };

    // Pre-authenticate user to handle consents
    await initiateLoginFlow(domains.clientApplication.settings.oauth.clientId, clientOpenIdConfiguration, domains.clientDomain)
        .then((response) => navigateToSamlProviderLogin(response))
        .then((response) => login(response, TEST_USER.username, domains.providerApplication.settings.oauth.clientId, TEST_USER.password))
        .then(followRedirectTag('saml-1'))
        .then(followRedirectTag('saml-2'))
        .then(() => console.debug('SAML user consents granted'))
        .catch(() => console.debug('Consent flow may have been skipped'));

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
            return Promise.all([
                deleteDomain(domains.clientDomain.id, accessToken),
                deleteDomain(domains.providerDomain.id, accessToken)
            ]).then(() => console.log('SAML Cleanup complete'));
        }
    };
}

export async function cleanupSamlTestDomains(accessToken: string, domains: SamlTestDomains): Promise<void> {
    console.log(`Cleaning up domains: ${domains.clientDomain.hrid}, ${domains.providerDomain.hrid}`);
    return Promise.all([
        deleteDomain(domains.clientDomain.id, accessToken),
        deleteDomain(domains.providerDomain.id, accessToken)
    ]).then(() => console.log('Cleanup complete'));
}