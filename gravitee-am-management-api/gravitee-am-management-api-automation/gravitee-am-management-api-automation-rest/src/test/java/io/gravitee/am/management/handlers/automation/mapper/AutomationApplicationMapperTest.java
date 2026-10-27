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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.management.handlers.automation.model.AutomationAdvancedSettings;
import io.gravitee.am.management.handlers.automation.model.AutomationApplication;
import io.gravitee.am.management.handlers.automation.model.AutomationApplicationFactorSettings;
import io.gravitee.am.management.handlers.automation.model.AutomationApplicationIdentityProvider;
import io.gravitee.am.management.handlers.automation.model.AutomationApplicationOAuthSettings;
import io.gravitee.am.management.handlers.automation.model.AutomationApplicationSAMLSettings;
import io.gravitee.am.management.handlers.automation.model.AutomationApplicationSettings;
import io.gravitee.am.management.handlers.automation.model.AutomationAssessmentSettings;
import io.gravitee.am.management.handlers.automation.model.AutomationChallengeSettings;
import io.gravitee.am.management.handlers.automation.model.AutomationCookieSettings;
import io.gravitee.am.management.handlers.automation.model.AutomationEnrollSettings;
import io.gravitee.am.management.handlers.automation.model.AutomationEnrollmentSettings;
import io.gravitee.am.management.handlers.automation.model.AutomationFactorSettings;
import io.gravitee.am.management.handlers.automation.model.AutomationMfaSettings;
import io.gravitee.am.management.handlers.automation.model.AutomationRememberDeviceSettings;
import io.gravitee.am.management.handlers.automation.model.AutomationRiskAssessmentSettings;
import io.gravitee.am.management.handlers.automation.model.AutomationSamlAssertionAttribute;
import io.gravitee.am.management.handlers.automation.model.AutomationScopeSettings;
import io.gravitee.am.management.handlers.automation.model.AutomationSessionSettings;
import io.gravitee.am.management.handlers.automation.model.AutomationStepUpAuthenticationSettings;
import io.gravitee.am.management.handlers.automation.model.AutomationTokenClaim;
import io.gravitee.am.management.handlers.automation.model.AutomationUserInfoClaim;
import io.gravitee.am.management.handlers.automation.model.AutomationWorkloadIdentitySettings;
import io.gravitee.am.management.handlers.automation.resource.AutomationIds;
import io.gravitee.am.management.handlers.management.api.mapper.ObjectMapperResolver;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ManagedBy;
import io.gravitee.am.model.MfaChallengeType;
import io.gravitee.am.model.MfaEnrollType;
import io.gravitee.am.model.SecretExpirationSettings;
import io.gravitee.am.model.application.ApplicationCrossAppAccessResourceServer;
import io.gravitee.am.model.application.ApplicationCrossAppAccessSettings;
import io.gravitee.am.model.application.ApplicationLightweightJwtSettings;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.application.ApplicationType;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.application.SpiffeApplicationSettings;
import io.gravitee.am.model.application.TokenExchangeOAuthSettings;
import io.gravitee.am.model.application.TokenExchangeScopeHandling;
import io.gravitee.am.model.idp.ApplicationIdentityProvider;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.risk.assessment.api.assessment.Assessment;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author GraviteeSource Team
 */
class AutomationApplicationMapperTest {

    private static final ObjectMapper MAPPER = ObjectMapperResolver.newManagementObjectMapper();
    private static final Domain DOMAIN = new Domain("domain-1");

    /**
     * {@link ApplicationOAuthSettings} fields the Automation API intentionally does not carry. Adding to this set is a
     * conscious decision: the drift guard below fails until a new field is either surfaced on
     * {@link AutomationApplicationOAuthSettings} (and mapped) or listed here.
     */
    private static final Set<String> OAUTH_FIELDS_NOT_SURFACED = Set.of(
            "clientId",                 // top-level clientId of the application
            "clientSecret",             // client secrets have their own resource
            "clientSecretExpiresAt",    // client secrets have their own resource
            "softwareId",               // dynamic client registration runtime field
            "softwareVersion",          // dynamic client registration runtime field
            "softwareStatement",        // dynamic client registration runtime field
            "registrationAccessToken",  // dynamic client registration runtime field
            "registrationClientUri",    // dynamic client registration runtime field
            "clientIdIssuedAt",         // dynamic client registration runtime field
            "scopes",                   // deprecated, migration only
            "defaultScopes",            // deprecated, migration only
            "scopeApprovals",           // deprecated, migration only
            "requireParRequest",        // not changeable through the Management API
            "backchannelTokenDeliveryMode",            // not changeable through the Management API
            "backchannelClientNotificationEndpoint",   // not changeable through the Management API
            "backchannelAuthRequestSignAlg",           // not changeable through the Management API
            "backchannelUserCodeParameter"             // not changeable through the Management API
    );

    @Test
    void oauthSettingsAreFullySurfaced() {
        Set<String> surfaced = fieldNames(AutomationApplicationOAuthSettings.class);

        Set<String> unsurfaced = new TreeSet<>(fieldNames(ApplicationOAuthSettings.class));
        unsurfaced.removeAll(surfaced);
        unsurfaced.removeAll(OAUTH_FIELDS_NOT_SURFACED);

        assertTrue(unsurfaced.isEmpty(), "ApplicationOAuthSettings has fields not surfaced by the Automation API: "
                + unsurfaced + ". Add them to AutomationApplicationOAuthSettings and map them in "
                + "AutomationApplicationMapper, or add them to OAUTH_FIELDS_NOT_SURFACED with a rationale.");
    }

    private static Set<String> fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> !field.isSynthetic() && !Modifier.isStatic(field.getModifiers()))
                .map(Field::getName)
                .collect(Collectors.toSet());
    }

    @Test
    void shouldRoundTripAFullDocument() {
        AutomationApplication definition = fullDefinition();

        Application app = AutomationApplicationMapper.newApplication(DOMAIN, "mobile-app", definition, List.of());
        AutomationApplication read = AutomationApplicationMapper.toAutomationApplication(app);

        // clientName is read-only: always the application name
        assertEquals("Mobile app", read.getSettings().getOauth().getClientName());
        read.getSettings().getOauth().setClientName(null);
        assertEquals(MAPPER.valueToTree(definition), MAPPER.valueToTree(read));
    }

    @Test
    void shouldCreateAnAutomationManagedApplication() {
        Application app = AutomationApplicationMapper.newApplication(DOMAIN, "mobile-app", minimalDefinition(), List.of());

        assertEquals(AutomationIds.applicationId(DOMAIN.getId(), "mobile-app"), app.getId());
        assertEquals(DOMAIN.getId(), app.getDomain());
        assertEquals("mobile-app", app.getAutomationKey());
        assertEquals(ManagedBy.AUTOMATION_API, app.getManagedBy());
        assertNull(app.clientId());
    }

    @Test
    void shouldInheritOmittedInheritableBlocks() {
        Application app = AutomationApplicationMapper.newApplication(DOMAIN, "mobile-app", minimalDefinition(), List.of());

        ApplicationSettings settings = app.getSettings();
        assertTrue(settings.getLogin().isInherited());
        assertTrue(settings.getAccount().isInherited());
        assertTrue(settings.getCookieSettings().isInherited());
        assertTrue(settings.getOauth().getTokenExchangeOAuthSettings().isInherited());
        assertNull(settings.getMfa());
        assertNull(settings.getSaml());
        // the model falls back to the default advanced settings
        assertTrue(settings.getAdvanced().isFlowsInherited());
    }

    @Test
    void shouldComputeOmittedOAuthDefaults() {
        Application app = AutomationApplicationMapper.newApplication(DOMAIN, "mobile-app", minimalDefinition(), List.of());

        ApplicationOAuthSettings oauth = app.getSettings().getOauth();
        ApplicationOAuthSettings defaults = new ApplicationOAuthSettings();
        assertTrue(oauth.isOptInScopeSelection());
        assertEquals(defaults.getAccessTokenValiditySeconds(), oauth.getAccessTokenValiditySeconds());
        assertEquals(defaults.getRefreshTokenValiditySeconds(), oauth.getRefreshTokenValiditySeconds());
        assertEquals(defaults.getIdTokenValiditySeconds(), oauth.getIdTokenValiditySeconds());
        assertEquals(defaults.getIdJagValiditySeconds(), oauth.getIdJagValiditySeconds());
        assertNull(oauth.getGrantTypes());
    }

    @Test
    void shouldStoreKeyReferencesAndEchoThem() {
        AutomationApplication definition = minimalDefinition();
        definition.setIdentityProviders(List.of(identityProvider("not-created-yet")));

        Application app = AutomationApplicationMapper.newApplication(DOMAIN, "mobile-app", definition, List.of());

        assertEquals(AutomationIds.certificateId(DOMAIN.getId(), "signing-cert"), app.getCertificate());
        assertEquals("signing-cert", app.getCertificateKey());
        ApplicationIdentityProvider idp = app.getIdentityProviders().first();
        assertEquals(AutomationIds.identityProviderId(DOMAIN.getId(), "not-created-yet"), idp.getIdentity());
        assertEquals("not-created-yet", idp.getIdentityKey());

        AutomationApplication read = AutomationApplicationMapper.toAutomationApplication(app);
        assertEquals("signing-cert", read.getCertificate());
        assertEquals("not-created-yet", read.getIdentityProviders().get(0).getIdentity());
    }

    @Test
    void shouldStoreIdReferencesById() {
        AutomationApplication definition = minimalDefinition();
        definition.setCertificate("id:cert-uuid");
        definition.setIdentityProviders(List.of(identityProvider("id:idp-uuid")));

        Application app = AutomationApplicationMapper.newApplication(DOMAIN, "mobile-app", definition, List.of());

        assertEquals("cert-uuid", app.getCertificate());
        assertEquals("idp-uuid", app.getIdentityProviders().first().getIdentity());
        AutomationApplication read = AutomationApplicationMapper.toAutomationApplication(app);
        assertEquals("id:cert-uuid", read.getCertificate());
        assertEquals("id:idp-uuid", read.getIdentityProviders().get(0).getIdentity());
    }

    @Test
    void shouldReadReferencesWithoutKeyAsIds() {
        // a brownfield application, or one whose references were changed through the Management API
        Application app = new Application();
        app.setCertificate("new-cert-uuid");
        ApplicationIdentityProvider idp = new ApplicationIdentityProvider();
        idp.setIdentity("idp-uuid");
        app.setIdentityProviders(new TreeSet<>(List.of(idp)));

        AutomationApplication read = AutomationApplicationMapper.toAutomationApplication(app);

        assertEquals("id:new-cert-uuid", read.getCertificate());
        assertEquals("id:idp-uuid", read.getIdentityProviders().get(0).getIdentity());
    }

    @Test
    void shouldReadAnEmptySelectionRuleAsAbsent() {
        Application app = new Application();
        ApplicationIdentityProvider idp = new ApplicationIdentityProvider();
        idp.setIdentity("idp-uuid");
        idp.setSelectionRule("");
        app.setIdentityProviders(new TreeSet<>(List.of(idp)));

        assertNull(AutomationApplicationMapper.toAutomationApplication(app).getIdentityProviders().get(0).getSelectionRule());
    }

    @Test
    void shouldRejectAFactorReferencedByKey() {
        AutomationApplication definition = minimalDefinition();
        AutomationApplicationFactorSettings factor = new AutomationApplicationFactorSettings();
        factor.setId("otp");
        AutomationFactorSettings factorSettings = new AutomationFactorSettings();
        factorSettings.setApplicationFactors(List.of(factor));
        AutomationMfaSettings mfa = new AutomationMfaSettings();
        mfa.setFactor(factorSettings);
        definition.getSettings().setMfa(mfa);

        InvalidParameterException ex = assertThrows(InvalidParameterException.class,
                () -> AutomationApplicationMapper.newApplication(DOMAIN, "mobile-app", definition, List.of()));
        assertEquals("mfa.factor.applicationFactors.id only accepts id:<uuid> references", ex.getMessage());
    }

    @Test
    void shouldRejectAnInvalidJwkSet() {
        AutomationApplication definition = minimalDefinition();
        definition.getSettings().getOauth().setJwks(Map.of("keys", "not-a-list"));

        assertThrows(InvalidParameterException.class,
                () -> AutomationApplicationMapper.newApplication(DOMAIN, "mobile-app", definition, List.of()));
    }

    @Test
    void shouldKeepUnmodelledFieldsOnApply() {
        Application existing = AutomationApplicationMapper.newApplication(DOMAIN, "mobile-app", fullDefinition(), List.of());
        existing.setFactors(Set.of("legacy-factor"));
        ClientSecret secret = new ClientSecret();
        secret.setId("secret-1");
        existing.setSecrets(List.of(secret));
        ApplicationOAuthSettings oauth = existing.getSettings().getOauth();
        oauth.setSoftwareId("software-1");
        oauth.setRegistrationAccessToken("registration-token");
        oauth.setClientSecretExpiresAt(new Date(0));
        oauth.setClientType("public");

        AutomationApplication definition = minimalDefinition();
        definition.setClientId(null);
        Application updated = AutomationApplicationMapper.applyTo(definition, new Application(existing), DOMAIN, List.of());

        assertEquals(Set.of("legacy-factor"), updated.getFactors());
        assertEquals("secret-1", updated.getSecrets().get(0).getId());
        ApplicationOAuthSettings updatedOAuth = updated.getSettings().getOauth();
        assertEquals("mobile-app-client", updatedOAuth.getClientId());
        assertEquals("software-1", updatedOAuth.getSoftwareId());
        assertEquals("registration-token", updatedOAuth.getRegistrationAccessToken());
        assertEquals(new Date(0), updatedOAuth.getClientSecretExpiresAt());
        assertEquals("public", updatedOAuth.getClientType());
        // modelled values absent from the definition are reset
        assertNull(updatedOAuth.getRedirectUris());
        assertNull(updated.getSettings().getMfa());
        assertTrue(updated.getIdentityProviders().isEmpty());
    }

    @Test
    void shouldNeverMapASecretOut() {
        Application app = AutomationApplicationMapper.newApplication(DOMAIN, "mobile-app", minimalDefinition(), List.of());
        app.getSettings().getOauth().setClientSecret("clear-text");

        assertTrue(MAPPER.valueToTree(AutomationApplicationMapper.toAutomationApplication(app)).findValues("clientSecret").isEmpty());
    }

    private static AutomationApplication minimalDefinition() {
        AutomationApplication definition = new AutomationApplication();
        definition.setAutomationKey("mobile-app");
        definition.setName("Mobile app");
        definition.setType(ApplicationType.NATIVE);
        definition.setCertificate("signing-cert");
        AutomationApplicationSettings settings = new AutomationApplicationSettings();
        settings.setOauth(new AutomationApplicationOAuthSettings());
        definition.setSettings(settings);
        return definition;
    }

    private static AutomationApplicationIdentityProvider identityProvider(String reference) {
        AutomationApplicationIdentityProvider idp = new AutomationApplicationIdentityProvider();
        idp.setIdentity(reference);
        idp.setPriority(1);
        idp.setSelectionRule("{#request.params['idp'][0] == 'customers'}");
        return idp;
    }

    private static AutomationApplication fullDefinition() {
        AutomationApplication definition = minimalDefinition();
        definition.setDescription("Customer mobile application");
        definition.setEnabled(false);
        definition.setTemplate(true);
        definition.setClientId("mobile-app-client");
        definition.setMetadata(Map.of("team", "mobile"));
        definition.setIdentityProviders(new ArrayList<>(List.of(identityProvider("customer-users"))));
        definition.getSettings().setOauth(fullOAuth());

        AutomationApplicationSettings settings = definition.getSettings();
        LoginSettings login = new LoginSettings();
        login.setInherited(false);
        login.setForgotPasswordEnabled(true);
        settings.setLogin(login);
        settings.setAccount(fullAccount());
        AutomationCookieSettings cookieSettings = new AutomationCookieSettings();
        cookieSettings.setInherited(false);
        AutomationSessionSettings session = new AutomationSessionSettings();
        session.setPersistent(true);
        cookieSettings.setSession(session);
        settings.setCookieSettings(cookieSettings);
        settings.setMfa(fullMfa());
        settings.setSaml(fullSaml());
        AutomationAdvancedSettings advanced = new AutomationAdvancedSettings();
        advanced.setSkipConsent(true);
        advanced.setFlowsInherited(false);
        settings.setAdvanced(advanced);
        settings.setRiskAssessment(fullRiskAssessment());
        SecretExpirationSettings secretExpiration = new SecretExpirationSettings();
        secretExpiration.setEnabled(true);
        secretExpiration.setExpiryTimeSeconds(3600L);
        settings.setSecretExpirationSettings(secretExpiration);
        AutomationWorkloadIdentitySettings workloadIdentity = new AutomationWorkloadIdentitySettings();
        workloadIdentity.setTrustDomain("prod.example.org");
        workloadIdentity.setSubject("spiffe://prod.example.org/ns/payments/");
        workloadIdentity.setSubjectMatchMode(SpiffeApplicationSettings.SubjectMatchMode.PREFIX);
        settings.setWorkloadIdentitySettings(workloadIdentity);
        return definition;
    }

    private static AutomationApplicationOAuthSettings fullOAuth() {
        AutomationApplicationOAuthSettings oauth = new AutomationApplicationOAuthSettings();
        oauth.setGrantTypes(List.of("authorization_code", "refresh_token"));
        oauth.setResponseTypes(List.of("code"));
        oauth.setRedirectUris(List.of("https://app.example.com/callback"));
        oauth.setPostLogoutRedirectUris(List.of("https://app.example.com/logged-out"));
        oauth.setRequestUris(List.of("https://app.example.com/request"));
        oauth.setInitiateLoginUri("https://app.example.com/login");
        AutomationScopeSettings scope = new AutomationScopeSettings();
        scope.setScope("openid");
        scope.setDefaultScope(true);
        scope.setScopeApproval(86400);
        scope.setRequiredScope(true);
        oauth.setScopeSettings(List.of(scope));
        oauth.setEnhanceScopesWithUserPermissions(true);
        oauth.setOptInScopeSelection(false);
        oauth.setAccessTokenValiditySeconds(600);
        oauth.setRefreshTokenValiditySeconds(1200);
        oauth.setIdTokenValiditySeconds(300);
        oauth.setIdJagValiditySeconds(60);
        oauth.setDisableRefreshTokenRotation(true);
        AutomationTokenClaim tokenClaim = new AutomationTokenClaim();
        tokenClaim.setTokenType(TokenTypeHint.ACCESS_TOKEN);
        tokenClaim.setClaimName("department");
        tokenClaim.setClaimValue("sales");
        oauth.setTokenCustomClaims(List.of(tokenClaim));
        AutomationUserInfoClaim userInfoClaim = new AutomationUserInfoClaim();
        userInfoClaim.setClaimName("team");
        userInfoClaim.setClaimValue("mobile");
        oauth.setUserinfoCustomClaims(List.of(userInfoClaim));
        ApplicationLightweightJwtSettings lightweightJwt = new ApplicationLightweightJwtSettings();
        lightweightJwt.setEnabled(true);
        lightweightJwt.setAllowedAccessTokenClaims(List.of("tenant"));
        oauth.setLightweightJwtSettings(lightweightJwt);
        TokenExchangeOAuthSettings tokenExchange = new TokenExchangeOAuthSettings();
        tokenExchange.setInherited(false);
        tokenExchange.setScopeHandling(TokenExchangeScopeHandling.PERMISSIVE);
        oauth.setTokenExchangeOAuthSettings(tokenExchange);
        oauth.setTokenEndpointAuthMethod("private_key_jwt");
        oauth.setTokenEndpointAuthSigningAlg("RS256");
        oauth.setJwks(Map.of("keys", List.of(Map.of("kty", "RSA", "kid", "app-key", "use", "sig", "n", "0vx7", "e", "AQAB"))));
        oauth.setTlsClientAuthSubjectDn("CN=app.example.com");
        oauth.setTlsClientAuthSanDns("app.example.com");
        oauth.setTlsClientAuthSanUri("https://app.example.com");
        oauth.setTlsClientAuthSanIp("10.0.0.1");
        oauth.setTlsClientAuthSanEmail("ops@example.com");
        oauth.setTlsClientCertificateBoundAccessTokens(true);
        oauth.setDpopBoundAccessTokens(true);
        oauth.setForcePKCE(true);
        oauth.setForceS256CodeChallengeMethod(true);
        oauth.setSingleSignOut(true);
        oauth.setSilentReAuthentication(true);
        oauth.setRequireAuthTime(true);
        oauth.setDefaultMaxAge(3600);
        oauth.setDefaultACRvalues(List.of("urn:mace:incommon:iap:silver"));
        oauth.setIdTokenSignedResponseAlg("RS256");
        oauth.setIdTokenEncryptedResponseAlg("RSA-OAEP-256");
        oauth.setIdTokenEncryptedResponseEnc("A256GCM");
        oauth.setUserinfoSignedResponseAlg("RS256");
        oauth.setUserinfoEncryptedResponseAlg("RSA-OAEP-256");
        oauth.setUserinfoEncryptedResponseEnc("A256GCM");
        oauth.setRequestObjectSigningAlg("RS256");
        oauth.setRequestObjectEncryptionAlg("RSA-OAEP-256");
        oauth.setRequestObjectEncryptionEnc("A256GCM");
        oauth.setAuthorizationSignedResponseAlg("RS256");
        oauth.setAuthorizationEncryptedResponseAlg("RSA-OAEP-256");
        oauth.setAuthorizationEncryptedResponseEnc("A256GCM");
        oauth.setSubjectType("pairwise");
        oauth.setSectorIdentifierUri("https://app.example.com/sector.json");
        oauth.setContacts(List.of("ops@example.com"));
        oauth.setLogoUri("https://app.example.com/logo.png");
        oauth.setClientUri("https://app.example.com");
        oauth.setPolicyUri("https://app.example.com/policy");
        oauth.setTosUri("https://app.example.com/tos");
        ApplicationCrossAppAccessResourceServer resourceServer = new ApplicationCrossAppAccessResourceServer();
        resourceServer.setTrustDomainId("id:trust-domain-uuid");
        resourceServer.setResourceServerId("id:resource-server-uuid");
        resourceServer.setClientId("calendar-client");
        ApplicationCrossAppAccessSettings crossAppAccess = new ApplicationCrossAppAccessSettings();
        crossAppAccess.setEnabled(true);
        crossAppAccess.setResourceServers(List.of(resourceServer));
        oauth.setCrossAppAccessSettings(crossAppAccess);
        return oauth;
    }

    private static io.gravitee.am.management.handlers.automation.model.AutomationAccountSettings fullAccount() {
        var account = new io.gravitee.am.management.handlers.automation.model.AutomationAccountSettings();
        account.setInherited(false);
        account.setLoginAttemptsDetectionEnabled(true);
        account.setMaxLoginAttempts(5);
        account.setDefaultIdentityProviderForRegistration("customer-users");
        return account;
    }

    private static AutomationMfaSettings fullMfa() {
        AutomationMfaSettings mfa = new AutomationMfaSettings();
        mfa.setLoginRule("{#true}");
        AutomationApplicationFactorSettings factor = new AutomationApplicationFactorSettings();
        factor.setId("id:otp-uuid");
        factor.setSelectionRule("{#true}");
        AutomationFactorSettings factorSettings = new AutomationFactorSettings();
        factorSettings.setDefaultFactorId("id:otp-uuid");
        factorSettings.setApplicationFactors(List.of(factor));
        mfa.setFactor(factorSettings);
        mfa.setStepUpAuthenticationRule("{#legacy}");
        AutomationStepUpAuthenticationSettings stepUp = new AutomationStepUpAuthenticationSettings();
        stepUp.setActive(true);
        stepUp.setStepUpAuthenticationRule("{#stepUp}");
        mfa.setStepUpAuthentication(stepUp);
        mfa.setAdaptiveAuthenticationRule("{#adaptive}");
        AutomationRememberDeviceSettings rememberDevice = new AutomationRememberDeviceSettings();
        rememberDevice.setActive(true);
        rememberDevice.setSkipChallengeWhenRememberDevice(true);
        rememberDevice.setExpirationTimeSeconds(36000L);
        rememberDevice.setDeviceIdentifierId("id:device-identifier-uuid");
        mfa.setRememberDevice(rememberDevice);
        AutomationEnrollmentSettings enrollment = new AutomationEnrollmentSettings();
        enrollment.setForceEnrollment(true);
        enrollment.setSkipTimeSeconds(600L);
        mfa.setEnrollment(enrollment);
        AutomationChallengeSettings challenge = new AutomationChallengeSettings();
        challenge.setActive(true);
        challenge.setChallengeRule("{#challenge}");
        challenge.setType(MfaChallengeType.CONDITIONAL);
        mfa.setChallenge(challenge);
        AutomationEnrollSettings enroll = new AutomationEnrollSettings();
        enroll.setActive(true);
        enroll.setForceEnrollment(false);
        enroll.setEnrollmentRule("{#enroll}");
        enroll.setEnrollmentSkipActive(true);
        enroll.setEnrollmentSkipRule("{#skip}");
        enroll.setSkipTimeSeconds(7200L);
        enroll.setType(MfaEnrollType.CONDITIONAL);
        mfa.setEnroll(enroll);
        return mfa;
    }

    private static AutomationApplicationSAMLSettings fullSaml() {
        AutomationApplicationSAMLSettings saml = new AutomationApplicationSAMLSettings();
        saml.setEntityId("https://app.example.com");
        saml.setAttributeConsumeServiceUrl("https://app.example.com/saml/acs");
        saml.setSingleLogoutServiceUrl("https://app.example.com/saml/logout");
        saml.setCertificate("-----BEGIN CERTIFICATE-----\nMIIB\n-----END CERTIFICATE-----");
        saml.setWantResponseSigned(false);
        saml.setWantAssertionsSigned(true);
        saml.setWantAssertionsEncrypted(true);
        saml.setKeyTransportEncryptionAlgorithm("http://www.w3.org/2009/xmlenc11#rsa-oaep");
        saml.setDataEncryptionAlgorithm("http://www.w3.org/2009/xmlenc11#aes256-gcm");
        saml.setResponseBinding("urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST");
        saml.setNameIdMapping("{#context.attributes['user'].username}");
        AutomationSamlAssertionAttribute attribute = new AutomationSamlAssertionAttribute();
        attribute.setName("email");
        attribute.setValue("{#context.attributes['user'].email}");
        saml.setAssertionAttributes(List.of(attribute));
        saml.setIncludeAssertionConditions(true);
        saml.setAudiences(List.of("https://audience.example.com"));
        saml.setAssertionValiditySeconds(300);
        saml.setNotBeforeTimeSkewSeconds(30);
        saml.setNotOnOrAfterTimeSkewSeconds(60);
        return saml;
    }

    private static AutomationRiskAssessmentSettings fullRiskAssessment() {
        AutomationAssessmentSettings assessment = new AutomationAssessmentSettings();
        assessment.setEnabled(true);
        assessment.setThresholds(Map.of(Assessment.HIGH, 1.0));
        AutomationRiskAssessmentSettings riskAssessment = new AutomationRiskAssessmentSettings();
        riskAssessment.setEnabled(true);
        riskAssessment.setDeviceAssessment(assessment);
        riskAssessment.setIpReputationAssessment(assessment);
        riskAssessment.setGeoVelocityAssessment(assessment);
        return riskAssessment;
    }
}
