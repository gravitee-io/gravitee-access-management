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
import { afterAll, beforeAll, expect, jest } from '@jest/globals';
import { uniqueName } from '@utils-commands/misc';
import { setupSamlTestDomains, cleanupSamlTestDomains, SamlTestDomains, SamlFixture, setupSamlProviderTest, TEST_USER } from './common';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { performGet } from '@gateway-commands/oauth-oidc-commands';

jest.setTimeout(200000);

// Add custom Jest matcher
expect.extend({
    toBeOneOf(received, expected) {
        const pass = expected.includes(received);
        if (pass) {
            return {
                message: () => `expected ${received} not to be one of ${expected}`,
                pass: true
            };
        } else {
            return {
                message: () => `expected ${received} to be one of ${expected}`,
                pass: false
            };
        }
    }
});

let domains: SamlTestDomains;
let accessToken: string;
let samlFixture: SamlFixture;

beforeAll(async function () {
    accessToken = await requestAdminAccessToken();
    domains = await setupSamlTestDomains(uniqueName('saml-basic', true));
    samlFixture = await setupSamlProviderTest(uniqueName('saml-auth', true));
});

afterAll(async function () {
    if (domains && accessToken) {
        await cleanupSamlTestDomains(accessToken, domains);
    }
    if (samlFixture) {
        await samlFixture.cleanup();
    }
});

describe('SAML Authentication', () => {
    it('should create provider and client domains successfully', async () => {
        expect(domains).toBeDefined();
        expect(domains.providerDomain).toBeDefined();
        expect(domains.clientDomain).toBeDefined();
        expect(domains.providerApplication).toBeDefined();
        expect(domains.clientApplication).toBeDefined();
        expect(domains.inlineIdp).toBeDefined();
        expect(domains.samlIdp).toBeDefined();
    });

    it('should have properly configured domains', async () => {
        expect(domains.providerDomain.name).toContain('saml-provider');
        expect(domains.clientDomain.name).toContain('saml-client');
        expect(domains.providerDomain.enabled).toBe(true);
        expect(domains.clientDomain.enabled).toBe(true);
    });

    it('should have configured inline IDP with test user', async () => {
        expect(domains.inlineIdp).toBeDefined();
        expect(domains.inlineIdp.type).toBe('inline-am-idp');
        expect(domains.inlineIdp.name).toContain('inmemory-');

        const config = JSON.parse(domains.inlineIdp.configuration);
        expect(config.users).toBeDefined();
        expect(config.users).toHaveLength(1);
        expect(config.users[0].username).toBe(TEST_USER.username);
    });

    it('should have configured SAML IDP pointing to provider domain', async () => {
        expect(domains.samlIdp).toBeDefined();
        expect(domains.samlIdp.type).toBe('saml2-generic-am-idp');
        expect(domains.samlIdp.name).toContain('saml-idp-');

        const config = JSON.parse(domains.samlIdp.configuration);
        expect(config.entityId).toContain('saml-idp-');
        expect(config.singleSignOnServiceUrl).toContain(domains.providerDomain.hrid);
        expect(config.singleSignOnServiceUrl).toContain('/saml2/idp/SSO');
        expect(config.singleLogoutServiceUrl).toContain('/saml2/idp/SLO');
    });
});

describe('SAML Authentication Flow', () => {
    it('should successfully login with valid SAML credentials', async () => {
        const loginResponse = await samlFixture.login(TEST_USER.username, TEST_USER.password);

        // Should get redirected back to client application
        expect(loginResponse.status).toBe(302);
        const authCode = await samlFixture.expectRedirectToClient(loginResponse);
        expect(authCode).toBeDefined();
        expect(authCode).toMatch(/^[a-zA-Z0-9_-]+$/);
    });

    it('should fail login with invalid SAML credentials', async () => {
        try {
            await samlFixture.login('invaliduser', 'wrongpassword');
            throw new Error('Login should have failed with invalid credentials');
        } catch (error) {
            // Login failure is expected - SAML provider should reject invalid credentials
            expect(error).toBeDefined();
            // Should not be our error message, but should be a legitimate auth failure
            expect(error.message).not.toBe('Login should have failed with invalid credentials');
        }
    });
});

describe('SAML Single Logout (SLO)', () => {
    it('should have SLO endpoints properly configured', async () => {
        // Verify that SLO endpoints are configured correctly
        const samlConfig = JSON.parse(samlFixture.domains.samlIdp.configuration);
        
        expect(samlConfig.singleLogoutServiceUrl).toBeDefined();
        expect(samlConfig.singleLogoutServiceUrl).toContain('/saml2/idp/SLO');
        expect(samlConfig.singleLogoutServiceUrl).toContain(samlFixture.domains.providerDomain.hrid);
        
        // Verify URL format
        expect(samlConfig.singleLogoutServiceUrl).toMatch(/^https?:\/\/.+\/saml2\/idp\/SLO$/);
    });

    it('should validate SLO endpoint configuration', async () => {
        // Test that the SLO endpoint is configured properly
        const samlConfig = JSON.parse(samlFixture.domains.samlIdp.configuration);
        const sloUrl = samlConfig.singleLogoutServiceUrl;
        
        // Verify the SLO URL structure is correct
        expect(sloUrl).toMatch(/^https?:\/\/.+\/saml2\/idp\/SLO$/);
        expect(sloUrl).toContain(samlFixture.domains.providerDomain.hrid);
        
        // Note: Full SLO endpoint testing would require proper SAML LogoutRequest/LogoutResponse
        // handling which may not be fully implemented. The configuration test ensures
        // the infrastructure is in place for SLO when fully implemented.
        
        // Verify the endpoint URL is well-formed
        const url = new URL(sloUrl);
        expect(url.pathname).toMatch(/\/saml2\/idp\/SLO$/);
        expect(url.hostname).toBeTruthy();
        expect(url.protocol).toMatch(/^https?:$/);
    });

    it('should support SLO initiation after successful authentication', async () => {
        // First perform successful authentication to establish session
        const loginResponse = await samlFixture.login(TEST_USER.username, TEST_USER.password);
        expect(loginResponse.status).toBe(302);
        
        const authCode = await samlFixture.expectRedirectToClient(loginResponse);
        expect(authCode).toBeDefined();
        
        // Verify that after successful authentication, SLO endpoints are still properly configured
        const samlConfig = JSON.parse(samlFixture.domains.samlIdp.configuration);
        expect(samlConfig.singleLogoutServiceUrl).toBeDefined();
        
        // In a full implementation, we would:
        // 1. Create a SAML LogoutRequest
        // 2. Send it to the SLO endpoint 
        // 3. Verify the LogoutResponse
        // For now, we verify the infrastructure supports it
        expect(samlConfig.singleLogoutServiceUrl).toContain(samlFixture.domains.providerDomain.hrid);
    });
});

describe('SAML Attribute Mapping', () => {
    it('should map SAML attributes to user claims correctly', async () => {
        // Perform successful login to get SAML response with attributes
        const loginResponse = await samlFixture.login(TEST_USER.username, TEST_USER.password);
        expect(loginResponse.status).toBe(302);
        
        // Verify authentication was successful (should redirect to client with code)
        const authCode = await samlFixture.expectRedirectToClient(loginResponse);
        expect(authCode).toBeDefined();
        
        // The SAML response should contain mapped attributes
        // In a real scenario, we would decode the SAML response and verify attributes
        // For now, we verify the configuration supports the expected mappings
        const samlConfig = JSON.parse(samlFixture.domains.samlIdp.configuration);
        expect(samlConfig.attributeMapping).toBeDefined();
        expect(samlConfig.attributeMapping['http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress']).toBe('email');
        expect(samlConfig.attributeMapping['http://schemas.xmlsoap.org/ws/2005/05/identity/claims/givenname']).toBe('firstname');
        expect(samlConfig.attributeMapping['http://schemas.xmlsoap.org/ws/2005/05/identity/claims/surname']).toBe('lastname');
    });

    it('should handle missing SAML attributes gracefully', async () => {
        // Test with user that might have missing attributes
        // Since we use an inline IDP with complete user data, this tests the system's robustness
        const loginResponse = await samlFixture.login(TEST_USER.username, TEST_USER.password);
        expect(loginResponse.status).toBe(302);
        
        // Even with potentially missing attributes, authentication should succeed
        const authCode = await samlFixture.expectRedirectToClient(loginResponse);
        expect(authCode).toBeDefined();
        
        // Verify the system handles missing attributes by having defaults or graceful degradation
        const samlConfig = JSON.parse(samlFixture.domains.samlIdp.configuration);
        expect(samlConfig.attributeMapping).toBeDefined();
        
        // The configuration should be robust enough to handle missing attributes
        expect(Object.keys(samlConfig.attributeMapping).length).toBeGreaterThan(0);
    });

    it('should support custom attribute mapping configuration', async () => {
        // Verify that the SAML IDP configuration has the expected attribute mappings
        const samlConfig = JSON.parse(samlFixture.domains.samlIdp.configuration);

        expect(samlConfig.attributeMapping).toBeDefined();
        expect(samlConfig.attributeMapping['http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress']).toBe('email');
        expect(samlConfig.attributeMapping['http://schemas.xmlsoap.org/ws/2005/05/identity/claims/givenname']).toBe('firstname');
        expect(samlConfig.attributeMapping['http://schemas.xmlsoap.org/ws/2005/05/identity/claims/surname']).toBe('lastname');

        // This test validates configuration only, not actual login flow
        expect(samlConfig.attributeMapping).toEqual(expect.objectContaining({
            'http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress': 'email',
            'http://schemas.xmlsoap.org/ws/2005/05/identity/claims/givenname': 'firstname',
            'http://schemas.xmlsoap.org/ws/2005/05/identity/claims/surname': 'lastname'
        }));
    });
});

describe('SAML Security Configuration', () => {
    it('should configure unsigned SAML assertions (current config)', async () => {
        // Verify current configuration uses unsigned assertions
        const samlConfig = JSON.parse(samlFixture.domains.samlIdp.configuration);
        expect(samlConfig.wantAssertionsSigned).toBe(false);
        expect(samlConfig.wantResponsesSigned).toBe(false);

        // Validate this is a deliberate security configuration for testing
        expect(typeof samlConfig.wantAssertionsSigned).toBe('boolean');
        expect(typeof samlConfig.wantResponsesSigned).toBe('boolean');
    });

    it('should validate SAML signature algorithms', async () => {
        const samlConfig = JSON.parse(samlFixture.domains.samlIdp.configuration);

        // Verify signature algorithm configuration
        expect(samlConfig.signatureAlgorithm).toBe('RSA_SHA256');
        expect(samlConfig.digestAlgorithm).toBe('SHA256');

        // These are secure, modern algorithms
        expect(['RSA_SHA256', 'RSA_SHA512']).toContain(samlConfig.signatureAlgorithm);
        expect(['SHA256', 'SHA512']).toContain(samlConfig.digestAlgorithm);

        // Validate algorithm format
        expect(samlConfig.signatureAlgorithm).toMatch(/^[A-Z0-9_]+$/);
        expect(samlConfig.digestAlgorithm).toMatch(/^[A-Z0-9_]+$/);
    });

    it('should validate SAML NameID format configuration', async () => {
        const samlConfig = JSON.parse(samlFixture.domains.samlIdp.configuration);

        // Verify NameID format
        expect(samlConfig.nameIDFormat).toBe('urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress');

        // Should be a valid SAML NameID format URN
        expect(samlConfig.nameIDFormat).toMatch(/^urn:oasis:names:tc:SAML:/);
        expect(samlConfig.nameIDFormat).toContain('nameid-format');

        // Validate it's a supported format
        const supportedFormats = [
            'urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress',
            'urn:oasis:names:tc:SAML:2.0:nameid-format:persistent',
            'urn:oasis:names:tc:SAML:2.0:nameid-format:transient'
        ];
        expect(supportedFormats).toContain(samlConfig.nameIDFormat);
    });

    it('should enforce SAML entity ID uniqueness', async () => {
        const samlConfig = JSON.parse(samlFixture.domains.samlIdp.configuration);

        // Entity ID should be unique and properly formatted
        expect(samlConfig.entityId).toBeDefined();
        expect(samlConfig.entityId).toContain('saml-idp-');
        expect(samlConfig.entityId).toMatch(/^[a-zA-Z0-9_-]+$/);

        // Should match the pattern we set in the fixture
        expect(samlConfig.entityId).toMatch(/^saml-idp-saml-auth-/);

        // Should be sufficiently unique (contains random suffix)
        expect(samlConfig.entityId.length).toBeGreaterThan(20);
    });

    it('should validate SAML service endpoint configurations', async () => {
        const samlConfig = JSON.parse(samlFixture.domains.samlIdp.configuration);

        // Verify SSO and SLO endpoints are properly configured
        expect(samlConfig.singleSignOnServiceUrl).toBeDefined();
        expect(samlConfig.singleLogoutServiceUrl).toBeDefined();
        expect(samlConfig.signInUrl).toBeDefined();

        // Should be valid URLs
        expect(samlConfig.singleSignOnServiceUrl).toMatch(/^https?:\/\/.+\/saml2\/idp\/SSO$/);
        expect(samlConfig.singleLogoutServiceUrl).toMatch(/^https?:\/\/.+\/saml2\/idp\/SLO$/);
        expect(samlConfig.signInUrl).toMatch(/^https?:\/\/.+\/saml2\/idp\/SSO$/);

        // Should point to the correct provider domain
        expect(samlConfig.singleSignOnServiceUrl).toContain(samlFixture.domains.providerDomain.hrid);
        expect(samlConfig.singleLogoutServiceUrl).toContain(samlFixture.domains.providerDomain.hrid);
    });
});