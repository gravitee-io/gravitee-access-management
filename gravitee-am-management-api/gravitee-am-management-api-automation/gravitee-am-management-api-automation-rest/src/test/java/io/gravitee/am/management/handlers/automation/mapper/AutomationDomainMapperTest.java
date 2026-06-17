/**
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
package io.gravitee.am.management.handlers.automation.mapper;

import io.gravitee.am.management.handlers.automation.model.AutomationAccountSettings;
import io.gravitee.am.management.handlers.automation.model.AutomationCertificateSettings;
import io.gravitee.am.management.handlers.automation.model.AutomationClientRegistrationSettings;
import io.gravitee.am.management.handlers.automation.model.AutomationDomain;
import io.gravitee.am.management.handlers.automation.model.AutomationOidcSettings;
import io.gravitee.am.management.handlers.automation.model.AutomationSamlSettings;
import io.gravitee.am.management.handlers.automation.resource.AutomationIds;
import io.gravitee.am.model.CorsSettings;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ManagedBy;
import io.gravitee.am.model.PasswordSettings;
import io.gravitee.am.model.SecretExpirationSettings;
import io.gravitee.am.model.SelfServiceAccountManagementSettings;
import io.gravitee.am.model.TokenExchangeSettings;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.login.WebAuthnSettings;
import io.gravitee.am.model.oidc.CIMDSettings;
import io.gravitee.am.model.oidc.ClientRegistrationSettings;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.model.oidc.SecurityProfileSettings;
import io.gravitee.am.model.scim.SCIMSettings;
import io.gravitee.am.model.uma.UMASettings;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author GraviteeSource Team
 */
class AutomationDomainMapperTest {

    private static final String DOMAIN_ID = "domain-1";

    /**
     * Writable {@link Domain} fields that the Automation API intentionally does NOT
     * surface. Adding to this set is a conscious decision and must be justified here —
     * the drift guard below fails until a new {@code Domain} field is either surfaced
     * on {@link AutomationDomain} (and mapped) or listed here.
     */
    private static final Set<String> INTENTIONALLY_NOT_SURFACED = Set.of(
            "id",             // internal id; the Automation API addresses domains by key
            "hrid",           // name-derived human id; the Automation API uses the dedicated key instead
            "version",        // internal domain schema version, not declarative state
            "referenceType",  // internal; always ENVIRONMENT
            "referenceId",    // internal; the environment id is taken from the URL path
            "identities",     // legacy field, DefaultOrganizationUpgrader use only
            "managedBy"       // set server-side to AUTOMATION_API; not a writable input
    );

    // --- drift guard --------------------------------------------------------

    /**
     * Fails when the core {@link Domain} model gains writable state that the
     * {@link AutomationDomain} projection (and therefore {@link AutomationDomainMapper})
     * silently drops. Either surface the new field on {@link AutomationDomain} and map
     * it, or add it to {@link #INTENTIONALLY_NOT_SURFACED} with a rationale.
     */
    @Test
    void domainWritableStateIsFullySurfacedByAutomationDomain() {
        Set<String> automationFields = writableFieldNames(AutomationDomain.class);

        Set<String> unsurfaced = new TreeSet<>();
        for (String domainField : writableFieldNames(Domain.class)) {
            if (INTENTIONALLY_NOT_SURFACED.contains(domainField)) {
                continue;
            }
            if (!automationFields.contains(domainField)) {
                unsurfaced.add(domainField);
            }
        }

        assertTrue(unsurfaced.isEmpty(),
                "Domain has writable state not surfaced by the Automation API: " + unsurfaced
                        + ". Add a matching property to AutomationDomain and map it in "
                        + "AutomationDomainMapper, or add the field to "
                        + "INTENTIONALLY_NOT_SURFACED with a rationale.");
    }

    private static Set<String> writableFieldNames(Class<?> type) {
        return java.util.Arrays.stream(type.getDeclaredFields())
                .filter(f -> !f.isSynthetic())
                .filter(f -> !Modifier.isStatic(f.getModifiers()))
                .map(Field::getName)
                .filter(name -> hasSetter(type, name))
                .collect(Collectors.toSet());
    }

    private static boolean hasSetter(Class<?> type, String fieldName) {
        String setter = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        return java.util.Arrays.stream(type.getMethods())
                .anyMatch(m -> m.getName().equals(setter) && m.getParameterCount() == 1);
    }

    private static Domain newDomain() {
        Domain domain = new Domain();
        domain.setId(DOMAIN_ID);
        return domain;
    }

    // --- settings reuse by reference ---------------------------------------

    @Test
    void sharedSettingsBlocksAreReusedByReferenceOnApply() {
        UMASettings uma = new UMASettings();
        LoginSettings login = new LoginSettings();
        WebAuthnSettings webAuthn = new WebAuthnSettings();
        SCIMSettings scim = new SCIMSettings();
        PasswordSettings password = new PasswordSettings();
        SelfServiceAccountManagementSettings selfService = new SelfServiceAccountManagementSettings();
        CorsSettings cors = new CorsSettings();
        SecretExpirationSettings secret = new SecretExpirationSettings();
        TokenExchangeSettings tokenExchange = new TokenExchangeSettings();

        AutomationDomain in = new AutomationDomain();
        in.setName("My Domain");
        in.setPath("/app");
        in.setUma(uma);
        in.setLoginSettings(login);
        in.setWebAuthnSettings(webAuthn);
        in.setScim(scim);
        in.setPasswordSettings(password);
        in.setSelfServiceAccountManagementSettings(selfService);
        in.setCorsSettings(cors);
        in.setSecretExpirationSettings(secret);
        in.setTokenExchangeSettings(tokenExchange);

        Domain target = newDomain();
        AutomationDomainMapper.applyTo(in, target, List.of());

        assertSame(uma, target.getUma());
        assertSame(login, target.getLoginSettings());
        assertSame(webAuthn, target.getWebAuthnSettings());
        assertSame(scim, target.getScim());
        assertSame(password, target.getPasswordSettings());
        assertSame(selfService, target.getSelfServiceAccountManagementSettings());
        assertSame(cors, target.getCorsSettings());
        assertSame(secret, target.getSecretExpirationSettings());
        assertSame(tokenExchange, target.getTokenExchangeSettings());

        // a GET of the same domain reuses the shared sub-models by reference too
        AutomationDomain out = AutomationDomainMapper.toAutomationDomain(target);
        assertSame(tokenExchange, out.getTokenExchangeSettings());
    }

    // --- strict declarative null-reset semantics ---------------------------

    @Test
    void omittedSettingsAreResetToCreationDefaultsOnApply() {
        AutomationDomain in = new AutomationDomain();
        in.setName("My Domain");
        in.setPath("/app");
        // every settings block omitted

        // pre-populate the target so we prove they are actively reset, not just absent
        Domain target = newDomain();
        target.setUma(new UMASettings());
        target.setLoginSettings(new LoginSettings());

        AutomationDomainMapper.applyTo(in, target, List.of());

        // oidc must never be null — reset to the standard default
        assertNotNull(target.getOidc(), "oidc must be reset to its default, never null");

        assertNull(target.getUma());
        assertNull(target.getLoginSettings());
        assertNull(target.getWebAuthnSettings());
        assertNull(target.getScim());
        assertNull(target.getAccountSettings());
        assertNull(target.getPasswordSettings());
        assertNull(target.getSelfServiceAccountManagementSettings());
        assertNull(target.getCorsSettings());
        assertNull(target.getSecretExpirationSettings());
        assertNull(target.getTokenExchangeSettings());
        assertNull(target.getSaml());
        assertNull(target.getCertificateSettings());
    }

    @Test
    void providedOidcIsNotOverriddenByTheDefault() {
        AutomationOidcSettings oidc = new AutomationOidcSettings();
        oidc.setRedirectUriStrictMatching(true);
        AutomationDomain in = new AutomationDomain();
        in.setName("My Domain");
        in.setPath("/app");
        in.setOidc(oidc);

        Domain target = newDomain();
        AutomationDomainMapper.applyTo(in, target, List.of());

        assertNotNull(target.getOidc());
        assertTrue(target.getOidc().isRedirectUriStrictMatching());
    }

    @Test
    void oidcReusableSubBlocksAreReusedByReference() {
        SecurityProfileSettings security = new SecurityProfileSettings();

        AutomationOidcSettings oidc = new AutomationOidcSettings();
        oidc.setSecurityProfileSettings(security);

        AutomationDomain in = new AutomationDomain();
        in.setName("My Domain");
        in.setPath("/app");
        in.setOidc(oidc);

        Domain target = newDomain();
        AutomationDomainMapper.applyTo(in, target, List.of());

        assertSame(security, target.getOidc().getSecurityProfileSettings());
    }

    @Test
    void clientRegistrationSettingsAreMappedWithCleanWireNames() {
        AutomationClientRegistrationSettings clientReg = new AutomationClientRegistrationSettings();
        clientReg.setDynamicClientRegistrationEnabled(true);
        clientReg.setOpenDynamicClientRegistrationEnabled(true);
        clientReg.setClientTemplateEnabled(true);
        clientReg.setAllowedScopesEnabled(true);

        AutomationOidcSettings oidc = new AutomationOidcSettings();
        oidc.setClientRegistrationSettings(clientReg);

        AutomationDomain in = new AutomationDomain();
        in.setName("My Domain");
        in.setPath("/app");
        in.setOidc(oidc);

        Domain target = newDomain();
        AutomationDomainMapper.applyTo(in, target, List.of());

        // wrapped, not reused by reference: values map onto the shared model's is-prefixed fields
        ClientRegistrationSettings mapped = target.getOidc().getClientRegistrationSettings();
        assertTrue(mapped.isDynamicClientRegistrationEnabled());
        assertTrue(mapped.isOpenDynamicClientRegistrationEnabled());
        assertTrue(mapped.isClientTemplateEnabled());
        assertTrue(mapped.isAllowedScopesEnabled());

        // and back out with the same clean names
        AutomationClientRegistrationSettings out =
                AutomationDomainMapper.toAutomationDomain(target).getOidc().getClientRegistrationSettings();
        assertTrue(out.isDynamicClientRegistrationEnabled());
        assertTrue(out.isOpenDynamicClientRegistrationEnabled());
        assertTrue(out.isClientTemplateEnabled());
        assertTrue(out.isAllowedScopesEnabled());
    }

    @Test
    void existingCimdSettingsAreWipedWhenOidcIsProvidedOnApply() {
        CIMDSettings preExisting = new CIMDSettings();
        preExisting.setEnabled(true);
        preExisting.setTemplateId("template-id-123");

        OIDCSettings existingOidc = new OIDCSettings();
        existingOidc.setCimdSettings(preExisting);

        Domain target = newDomain();
        target.setOidc(existingOidc);

        AutomationDomain in = new AutomationDomain();
        in.setName("My Domain");
        in.setPath("/app");
        in.setOidc(new AutomationOidcSettings());

        AutomationDomainMapper.applyTo(in, target, List.of());

        assertNull(target.getOidc().getCimdSettings(),
                "CIMD must be wiped when oidc is replaced by an Automation API PUT");
    }

    @Test
    void existingCimdSettingsAreResetToDefaultWhenOidcIsOmittedOnApply() {
        CIMDSettings preExisting = new CIMDSettings();
        preExisting.setEnabled(true);
        preExisting.setTemplateId("template-id-123");

        OIDCSettings existingOidc = new OIDCSettings();
        existingOidc.setCimdSettings(preExisting);

        Domain target = newDomain();
        target.setOidc(existingOidc);

        AutomationDomain in = new AutomationDomain();
        in.setName("My Domain");
        in.setPath("/app");

        AutomationDomainMapper.applyTo(in, target, List.of());

        assertNotNull(target.getOidc().getCimdSettings());
        assertFalse(target.getOidc().getCimdSettings().isEnabled());
        assertNull(target.getOidc().getCimdSettings().getTemplateId());
    }

    // --- certificate key <-> id translation (parallel storage) --------------

    @Test
    void samlAndFallbackCertificateRoundTripByKey() {
        // Automation -> model: the certificate id is computed deterministically from the key and the key
        // is kept verbatim in the parallel *Key field; no existence check is required.
        AutomationDomain in = new AutomationDomain();
        in.setName("My Domain");
        in.setPath("/app");
        AutomationSamlSettings saml = new AutomationSamlSettings();
        saml.setEnabled(true);
        saml.setEntityId("https://idp.example.com");
        saml.setCertificate("saml-signing");
        in.setSaml(saml);
        AutomationCertificateSettings cs = new AutomationCertificateSettings();
        cs.setFallbackCertificate("saml-signing");
        in.setCertificateSettings(cs);

        Domain target = newDomain();
        AutomationDomainMapper.applyTo(in, target, List.of());

        String expectedId = AutomationIds.certificateId(DOMAIN_ID, "saml-signing");
        assertEquals(expectedId, target.getSaml().getCertificate());
        assertEquals("saml-signing", target.getSaml().getCertificateKey());
        assertEquals(expectedId, target.getCertificateSettings().getFallbackCertificate());
        assertEquals("saml-signing", target.getCertificateSettings().getFallbackCertificateKey());

        // model -> Automation: the key is read back from the parallel field
        AutomationDomain out = AutomationDomainMapper.toAutomationDomain(target);
        assertEquals("saml-signing", out.getSaml().getCertificate());
        assertEquals("saml-signing", out.getCertificateSettings().getFallbackCertificate());
    }

    @Test
    void certificateReferenceToNotYetCreatedCertificateIsAccepted() {
        // eventual consistency: referencing a certificate that does not exist yet is allowed; the id is the
        // deterministic key-based id and the key still round-trips on a GET.
        AutomationDomain in = new AutomationDomain();
        in.setName("My Domain");
        in.setPath("/app");
        AutomationSamlSettings saml = new AutomationSamlSettings();
        saml.setEnabled(true);
        saml.setCertificate("does-not-exist-yet");
        in.setSaml(saml);

        Domain target = newDomain();
        AutomationDomainMapper.applyTo(in, target, List.of());

        assertEquals(AutomationIds.certificateId(DOMAIN_ID, "does-not-exist-yet"), target.getSaml().getCertificate());
        assertEquals("does-not-exist-yet", AutomationDomainMapper.toAutomationDomain(target).getSaml().getCertificate());
    }

    // --- account settings: IdP key translation -----------------------------

    @Test
    void defaultIdentityProviderForRegistrationResolvesToExistingProviderId() {
        IdentityProvider idp = new IdentityProvider();
        idp.setId("idp-id-1");
        idp.setAutomationKey("customer-users");
        idp.setManagedBy(ManagedBy.AUTOMATION_API);

        AutomationAccountSettings account = new AutomationAccountSettings();
        account.setDefaultIdentityProviderForRegistration("customer-users");
        AutomationDomain in = new AutomationDomain();
        in.setName("My Domain");
        in.setPath("/app");
        in.setAccountSettings(account);

        Domain target = newDomain();
        AutomationDomainMapper.applyTo(in, target, List.of(idp));

        // resolved to the provider's actual id, key kept verbatim in the parallel field
        assertEquals("idp-id-1", target.getAccountSettings().getDefaultIdentityProviderForRegistration());
        assertEquals("customer-users", target.getAccountSettings().getDefaultIdentityProviderForRegistrationKey());

        // GET reads the key back from the parallel field
        AutomationDomain out = AutomationDomainMapper.toAutomationDomain(target);
        assertEquals("customer-users", out.getAccountSettings().getDefaultIdentityProviderForRegistration());
    }

    @Test
    void certificateReferenceByIdUsesTheInternalIdVerbatim() {
        // an id: reference addresses a preexisting (e.g. brownfield) certificate directly — the internal
        // id is used as-is, no deterministic computation, and the id: token round-trips via the *Key field.
        AutomationDomain in = new AutomationDomain();
        in.setName("My Domain");
        in.setPath("/app");
        AutomationSamlSettings saml = new AutomationSamlSettings();
        saml.setEnabled(true);
        saml.setCertificate("id:11111111-2222-3333-4444-555555555555");
        in.setSaml(saml);

        Domain target = newDomain();
        AutomationDomainMapper.applyTo(in, target, List.of());

        assertEquals("11111111-2222-3333-4444-555555555555", target.getSaml().getCertificate());
        assertEquals("id:11111111-2222-3333-4444-555555555555", target.getSaml().getCertificateKey());
        // GET echoes the id: token back
        assertEquals("id:11111111-2222-3333-4444-555555555555",
                AutomationDomainMapper.toAutomationDomain(target).getSaml().getCertificate());
    }

    @Test
    void defaultIdentityProviderForRegistrationByIdUsesTheInternalIdVerbatim() {
        // a brownfield identity provider has no automation key, so it is referenced by id:; the internal
        // id is used as-is (no key scan, no deterministic fallback) and the token round-trips.
        AutomationAccountSettings account = new AutomationAccountSettings();
        account.setDefaultIdentityProviderForRegistration("id:default-idp-94157683-f481-45a9-9576-83f48145a9a0");
        AutomationDomain in = new AutomationDomain();
        in.setName("My Domain");
        in.setPath("/app");
        in.setAccountSettings(account);

        Domain target = newDomain();
        AutomationDomainMapper.applyTo(in, target, List.of());

        assertEquals("default-idp-94157683-f481-45a9-9576-83f48145a9a0",
                target.getAccountSettings().getDefaultIdentityProviderForRegistration());
        assertEquals("id:default-idp-94157683-f481-45a9-9576-83f48145a9a0",
                target.getAccountSettings().getDefaultIdentityProviderForRegistrationKey());
        assertEquals("id:default-idp-94157683-f481-45a9-9576-83f48145a9a0",
                AutomationDomainMapper.toAutomationDomain(target).getAccountSettings().getDefaultIdentityProviderForRegistration());
    }

    @Test
    void defaultIdentityProviderForRegistrationToNotYetCreatedProviderFallsBackToDeterministicId() {
        AutomationAccountSettings account = new AutomationAccountSettings();
        account.setDefaultIdentityProviderForRegistration("not-created-yet");
        AutomationDomain in = new AutomationDomain();
        in.setName("My Domain");
        in.setPath("/app");
        in.setAccountSettings(account);

        Domain target = newDomain();
        AutomationDomainMapper.applyTo(in, target, List.of());

        assertEquals(AutomationIds.identityProviderId(DOMAIN_ID, "not-created-yet"),
                target.getAccountSettings().getDefaultIdentityProviderForRegistration());
        assertEquals("not-created-yet",
                AutomationDomainMapper.toAutomationDomain(target).getAccountSettings().getDefaultIdentityProviderForRegistration());
    }
}
