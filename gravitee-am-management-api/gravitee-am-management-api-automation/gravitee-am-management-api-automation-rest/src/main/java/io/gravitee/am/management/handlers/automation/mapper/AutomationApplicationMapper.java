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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import io.gravitee.am.management.handlers.automation.resource.AutomationRef;
import io.gravitee.am.management.handlers.management.api.mapper.ObjectMapperResolver;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.ApplicationFactorSettings;
import io.gravitee.am.model.ChallengeSettings;
import io.gravitee.am.model.CookieSettings;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.EnrollSettings;
import io.gravitee.am.model.EnrollmentSettings;
import io.gravitee.am.model.FactorSettings;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.ManagedBy;
import io.gravitee.am.model.RememberDeviceSettings;
import io.gravitee.am.model.SessionSettings;
import io.gravitee.am.model.StepUpAuthenticationSettings;
import io.gravitee.am.model.TokenClaim;
import io.gravitee.am.model.UserInfoClaim;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.application.ApplicationAdvancedSettings;
import io.gravitee.am.model.application.ApplicationCrossAppAccessResourceServer;
import io.gravitee.am.model.application.ApplicationCrossAppAccessSettings;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSAMLSettings;
import io.gravitee.am.model.application.ApplicationScopeSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.application.SAMLAssertionAttribute;
import io.gravitee.am.model.application.SpiffeApplicationSettings;
import io.gravitee.am.model.application.TokenExchangeOAuthSettings;
import io.gravitee.am.model.idp.ApplicationIdentityProvider;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.risk.assessment.api.assessment.settings.AssessmentSettings;
import io.gravitee.risk.assessment.api.assessment.settings.RiskAssessmentSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;

import static java.util.stream.Collectors.toCollection;

/**
 * Maps between the shared {@link Application} model and the {@link AutomationApplication} projection.
 * <p>
 * Writes follow the full-desired-state contract: {@link #applyTo} sets every modelled field of the target, an
 * omitted value included (so AM computes it again), and leaves the unmodelled ones (secrets, secret settings,
 * deprecated factors, the OAuth 2.0 fields the projection does not carry) untouched. The {@code login},
 * {@code account} and {@code cookieSettings} blocks and the token-exchange settings, when omitted, are
 * inherited from the domain.
 * <p>
 * References to a certificate or an identity provider are stored twice: the internal id read by the gateway and
 * the reference as given ({@code certificateKey}, {@code identityKey}). Reads echo the stored reference, or
 * {@code id:<uuid>} when there is none (a brownfield application, or one changed through the Management API):
 * no lookup, no comparison. References to resources without an Automation API endpoint (MFA factors, device
 * identifier, Cross App Access trusted domain and resource server) are {@code id:<uuid>} only.
 *
 * @author GraviteeSource Team
 */
public final class AutomationApplicationMapper {

    private static final ObjectMapper JWKS_MAPPER = ObjectMapperResolver.newManagementObjectMapper();

    private AutomationApplicationMapper() {
    }

    // --- model -> Automation (response) -------------------------------------

    public static AutomationApplication toAutomationApplication(Application app) {
        AutomationApplication out = new AutomationApplication();
        out.setAutomationKey(app.getAutomationKey());
        out.setName(app.getName());
        out.setDescription(app.getDescription());
        out.setType(app.getType());
        out.setKind(app.getKind());
        out.setEnabled(app.isEnabled());
        out.setTemplate(app.isTemplate());
        out.setClientId(app.clientId());
        out.setCertificate(echoReference(app.getCertificateKey(), app.getCertificate()));
        out.setMetadata(app.getMetadata());
        if (app.getIdentityProviders() != null) {
            out.setIdentityProviders(app.getIdentityProviders().stream()
                    .map(AutomationApplicationMapper::toAutomationIdentityProvider)
                    .toList());
        }
        if (app.getSettings() != null) {
            out.setSettings(toAutomationSettings(app.getSettings()));
        }
        out.setCreatedAt(app.getCreatedAt());
        out.setUpdatedAt(app.getUpdatedAt());
        return out;
    }

    private static AutomationApplicationIdentityProvider toAutomationIdentityProvider(ApplicationIdentityProvider in) {
        AutomationApplicationIdentityProvider out = new AutomationApplicationIdentityProvider();
        out.setIdentity(echoReference(in.getIdentityKey(), in.getIdentity()));
        // the JDBC repository stores an absent rule as an empty one
        out.setSelectionRule(in.getSelectionRule() == null || in.getSelectionRule().isEmpty() ? null : in.getSelectionRule());
        out.setPriority(in.getPriority());
        return out;
    }

    private static AutomationApplicationSettings toAutomationSettings(ApplicationSettings in) {
        AutomationApplicationSettings out = new AutomationApplicationSettings();
        out.setOauth(mapOrNull(in.getOauth(), AutomationApplicationMapper::toAutomationOAuth));
        out.setLogin(in.getLogin());
        out.setAccount(AutomationDomainMapper.toAutomationAccountSettings(in.getAccount()));
        out.setCookieSettings(mapOrNull(in.getCookieSettings(), AutomationApplicationMapper::toAutomationCookieSettings));
        out.setMfa(mapOrNull(in.getMfa(), AutomationApplicationMapper::toAutomationMfa));
        out.setSaml(mapOrNull(in.getSaml(), AutomationApplicationMapper::toAutomationSaml));
        out.setAdvanced(mapOrNull(in.getAdvanced(), AutomationApplicationMapper::toAutomationAdvanced));
        out.setRiskAssessment(mapOrNull(in.getRiskAssessment(), AutomationApplicationMapper::toAutomationRiskAssessment));
        out.setSecretExpirationSettings(in.getSecretExpirationSettings());
        out.setWorkloadIdentitySettings(mapOrNull(in.getWorkloadIdentitySettings(), AutomationApplicationMapper::toAutomationWorkloadIdentity));
        return out;
    }

    private static AutomationApplicationOAuthSettings toAutomationOAuth(ApplicationOAuthSettings in) {
        AutomationApplicationOAuthSettings out = new AutomationApplicationOAuthSettings();
        out.setGrantTypes(in.getGrantTypes());
        out.setResponseTypes(in.getResponseTypes());
        out.setRedirectUris(in.getRedirectUris());
        out.setPostLogoutRedirectUris(in.getPostLogoutRedirectUris());
        out.setRequestUris(in.getRequestUris());
        out.setInitiateLoginUri(in.getInitiateLoginUri());
        out.setScopeSettings(mapList(in.getScopeSettings(), AutomationApplicationMapper::toAutomationScope));
        out.setEnhanceScopesWithUserPermissions(in.isEnhanceScopesWithUserPermissions());
        out.setOptInScopeSelection(in.isOptInScopeSelection());
        out.setAccessTokenValiditySeconds(in.getAccessTokenValiditySeconds());
        out.setRefreshTokenValiditySeconds(in.getRefreshTokenValiditySeconds());
        out.setIdTokenValiditySeconds(in.getIdTokenValiditySeconds());
        out.setIdJagValiditySeconds(in.getIdJagValiditySeconds());
        out.setDisableRefreshTokenRotation(in.isDisableRefreshTokenRotation());
        out.setTokenCustomClaims(mapList(in.getTokenCustomClaims(), AutomationApplicationMapper::toAutomationTokenClaim));
        out.setUserinfoCustomClaims(mapList(in.getUserinfoCustomClaims(), AutomationApplicationMapper::toAutomationUserInfoClaim));
        out.setLightweightJwtSettings(in.getLightweightJwtSettings());
        out.setTokenExchangeOAuthSettings(in.getTokenExchangeOAuthSettings());
        out.setTokenEndpointAuthMethod(in.getTokenEndpointAuthMethod());
        out.setTokenEndpointAuthSigningAlg(in.getTokenEndpointAuthSigningAlg());
        out.setJwksUri(in.getJwksUri());
        out.setJwks(in.getJwks() != null ? JWKS_MAPPER.convertValue(in.getJwks(), new TypeReference<Map<String, Object>>() { }) : null);
        out.setTlsClientAuthSubjectDn(in.getTlsClientAuthSubjectDn());
        out.setTlsClientAuthSanDns(in.getTlsClientAuthSanDns());
        out.setTlsClientAuthSanUri(in.getTlsClientAuthSanUri());
        out.setTlsClientAuthSanIp(in.getTlsClientAuthSanIp());
        out.setTlsClientAuthSanEmail(in.getTlsClientAuthSanEmail());
        out.setTlsClientCertificateBoundAccessTokens(in.isTlsClientCertificateBoundAccessTokens());
        out.setDpopBoundAccessTokens(in.isDpopBoundAccessTokens());
        out.setForcePKCE(in.isForcePKCE());
        out.setForceS256CodeChallengeMethod(in.isForceS256CodeChallengeMethod());
        out.setSingleSignOut(in.isSingleSignOut());
        out.setSilentReAuthentication(in.isSilentReAuthentication());
        out.setRequireAuthTime(Boolean.TRUE.equals(in.getRequireAuthTime()));
        out.setDefaultMaxAge(in.getDefaultMaxAge());
        out.setDefaultACRvalues(in.getDefaultACRvalues());
        out.setIdTokenSignedResponseAlg(in.getIdTokenSignedResponseAlg());
        out.setIdTokenEncryptedResponseAlg(in.getIdTokenEncryptedResponseAlg());
        out.setIdTokenEncryptedResponseEnc(in.getIdTokenEncryptedResponseEnc());
        out.setUserinfoSignedResponseAlg(in.getUserinfoSignedResponseAlg());
        out.setUserinfoEncryptedResponseAlg(in.getUserinfoEncryptedResponseAlg());
        out.setUserinfoEncryptedResponseEnc(in.getUserinfoEncryptedResponseEnc());
        out.setRequestObjectSigningAlg(in.getRequestObjectSigningAlg());
        out.setRequestObjectEncryptionAlg(in.getRequestObjectEncryptionAlg());
        out.setRequestObjectEncryptionEnc(in.getRequestObjectEncryptionEnc());
        out.setAuthorizationSignedResponseAlg(in.getAuthorizationSignedResponseAlg());
        out.setAuthorizationEncryptedResponseAlg(in.getAuthorizationEncryptedResponseAlg());
        out.setAuthorizationEncryptedResponseEnc(in.getAuthorizationEncryptedResponseEnc());
        out.setSubjectType(in.getSubjectType());
        out.setSectorIdentifierUri(in.getSectorIdentifierUri());
        out.setContacts(in.getContacts());
        out.setLogoUri(in.getLogoUri());
        out.setClientUri(in.getClientUri());
        out.setPolicyUri(in.getPolicyUri());
        out.setTosUri(in.getTosUri());
        out.setCrossAppAccessSettings(mapOrNull(in.getCrossAppAccessSettings(), settings -> crossAppAccess(settings, AutomationApplicationMapper::toIdReference)));
        out.setClientName(in.getClientName());
        out.setClientType(in.getClientType());
        out.setApplicationType(in.getApplicationType());
        return out;
    }

    private static AutomationScopeSettings toAutomationScope(ApplicationScopeSettings in) {
        AutomationScopeSettings out = new AutomationScopeSettings();
        out.setScope(in.getScope());
        out.setDefaultScope(in.isDefaultScope());
        out.setScopeApproval(in.getScopeApproval());
        out.setRequiredScope(in.isRequiredScope());
        return out;
    }

    private static AutomationTokenClaim toAutomationTokenClaim(TokenClaim in) {
        AutomationTokenClaim out = new AutomationTokenClaim();
        out.setTokenType(in.getTokenType());
        out.setClaimName(in.getClaimName());
        out.setClaimValue(in.getClaimValue());
        return out;
    }

    private static AutomationUserInfoClaim toAutomationUserInfoClaim(UserInfoClaim in) {
        AutomationUserInfoClaim out = new AutomationUserInfoClaim();
        out.setClaimName(in.getClaimName());
        out.setClaimValue(in.getClaimValue());
        return out;
    }

    private static AutomationCookieSettings toAutomationCookieSettings(CookieSettings in) {
        AutomationCookieSettings out = new AutomationCookieSettings();
        out.setInherited(in.isInherited());
        out.setSession(mapOrNull(in.getSession(), session -> {
            AutomationSessionSettings sessionOut = new AutomationSessionSettings();
            sessionOut.setPersistent(session.isPersistent());
            return sessionOut;
        }));
        return out;
    }

    private static AutomationMfaSettings toAutomationMfa(MFASettings in) {
        AutomationMfaSettings out = new AutomationMfaSettings();
        out.setLoginRule(in.getLoginRule());
        out.setFactor(mapOrNull(in.getFactor(), factor -> {
            AutomationFactorSettings factorOut = new AutomationFactorSettings();
            factorOut.setDefaultFactorId(toIdReference(factor.getDefaultFactorId()));
            factorOut.setApplicationFactors(mapList(factor.getApplicationFactors(), appFactor -> {
                AutomationApplicationFactorSettings appFactorOut = new AutomationApplicationFactorSettings();
                appFactorOut.setId(toIdReference(appFactor.getId()));
                appFactorOut.setSelectionRule(appFactor.getSelectionRule());
                return appFactorOut;
            }));
            return factorOut;
        }));
        out.setStepUpAuthenticationRule(in.getStepUpAuthenticationRule());
        out.setStepUpAuthentication(mapOrNull(in.getStepUpAuthentication(), stepUp -> {
            AutomationStepUpAuthenticationSettings stepUpOut = new AutomationStepUpAuthenticationSettings();
            stepUpOut.setActive(stepUp.isActive());
            stepUpOut.setStepUpAuthenticationRule(stepUp.getStepUpAuthenticationRule());
            return stepUpOut;
        }));
        out.setAdaptiveAuthenticationRule(in.getAdaptiveAuthenticationRule());
        out.setRememberDevice(mapOrNull(in.getRememberDevice(), rememberDevice -> {
            AutomationRememberDeviceSettings rememberDeviceOut = new AutomationRememberDeviceSettings();
            rememberDeviceOut.setActive(rememberDevice.isActive());
            rememberDeviceOut.setSkipChallengeWhenRememberDevice(rememberDevice.isSkipChallengeWhenRememberDevice());
            rememberDeviceOut.setExpirationTimeSeconds(rememberDevice.getExpirationTimeSeconds());
            rememberDeviceOut.setDeviceIdentifierId(toIdReference(rememberDevice.getDeviceIdentifierId()));
            return rememberDeviceOut;
        }));
        out.setEnrollment(mapOrNull(in.getEnrollment(), enrollment -> {
            AutomationEnrollmentSettings enrollmentOut = new AutomationEnrollmentSettings();
            enrollmentOut.setForceEnrollment(enrollment.getForceEnrollment());
            enrollmentOut.setSkipTimeSeconds(enrollment.getSkipTimeSeconds());
            return enrollmentOut;
        }));
        out.setChallenge(mapOrNull(in.getChallenge(), challenge -> {
            AutomationChallengeSettings challengeOut = new AutomationChallengeSettings();
            challengeOut.setActive(challenge.isActive());
            challengeOut.setChallengeRule(challenge.getChallengeRule());
            challengeOut.setType(challenge.getType());
            return challengeOut;
        }));
        out.setEnroll(mapOrNull(in.getEnroll(), enroll -> {
            AutomationEnrollSettings enrollOut = new AutomationEnrollSettings();
            enrollOut.setActive(enroll.isActive());
            enrollOut.setForceEnrollment(enroll.getForceEnrollment());
            enrollOut.setEnrollmentRule(enroll.getEnrollmentRule());
            enrollOut.setEnrollmentSkipActive(enroll.isEnrollmentSkipActive());
            enrollOut.setEnrollmentSkipRule(enroll.getEnrollmentSkipRule());
            enrollOut.setSkipTimeSeconds(enroll.getSkipTimeSeconds());
            enrollOut.setType(enroll.getType());
            return enrollOut;
        }));
        return out;
    }

    private static AutomationApplicationSAMLSettings toAutomationSaml(ApplicationSAMLSettings in) {
        AutomationApplicationSAMLSettings out = new AutomationApplicationSAMLSettings();
        out.setEntityId(in.getEntityId());
        out.setAttributeConsumeServiceUrl(in.getAttributeConsumeServiceUrl());
        out.setSingleLogoutServiceUrl(in.getSingleLogoutServiceUrl());
        out.setCertificate(in.getCertificate());
        out.setWantResponseSigned(in.isWantResponseSigned());
        out.setWantAssertionsSigned(in.isWantAssertionsSigned());
        out.setWantAssertionsEncrypted(in.isWantAssertionsEncrypted());
        out.setKeyTransportEncryptionAlgorithm(in.getKeyTransportEncryptionAlgorithm());
        out.setDataEncryptionAlgorithm(in.getDataEncryptionAlgorithm());
        out.setResponseBinding(in.getResponseBinding());
        out.setNameIdMapping(in.getNameIdMapping());
        out.setAssertionAttributes(mapList(in.getAssertionAttributes(), attribute -> {
            AutomationSamlAssertionAttribute attributeOut = new AutomationSamlAssertionAttribute();
            attributeOut.setName(attribute.name());
            attributeOut.setValue(attribute.value());
            return attributeOut;
        }));
        out.setIncludeAssertionConditions(in.isIncludeAssertionConditions());
        out.setAudiences(in.getAudiences());
        out.setAssertionValiditySeconds(in.getAssertionValiditySeconds());
        out.setNotBeforeTimeSkewSeconds(in.getNotBeforeTimeSkewSeconds());
        out.setNotOnOrAfterTimeSkewSeconds(in.getNotOnOrAfterTimeSkewSeconds());
        return out;
    }

    private static AutomationAdvancedSettings toAutomationAdvanced(ApplicationAdvancedSettings in) {
        AutomationAdvancedSettings out = new AutomationAdvancedSettings();
        out.setSkipConsent(in.isSkipConsent());
        out.setFlowsInherited(in.isFlowsInherited());
        return out;
    }

    private static AutomationRiskAssessmentSettings toAutomationRiskAssessment(RiskAssessmentSettings in) {
        AutomationRiskAssessmentSettings out = new AutomationRiskAssessmentSettings();
        out.setEnabled(in.isEnabled());
        out.setDeviceAssessment(mapOrNull(in.getDeviceAssessment(), AutomationApplicationMapper::toAutomationAssessment));
        out.setIpReputationAssessment(mapOrNull(in.getIpReputationAssessment(), AutomationApplicationMapper::toAutomationAssessment));
        out.setGeoVelocityAssessment(mapOrNull(in.getGeoVelocityAssessment(), AutomationApplicationMapper::toAutomationAssessment));
        return out;
    }

    private static AutomationAssessmentSettings toAutomationAssessment(AssessmentSettings in) {
        AutomationAssessmentSettings out = new AutomationAssessmentSettings();
        out.setEnabled(in.isEnabled());
        out.setThresholds(in.getThresholds());
        return out;
    }

    private static AutomationWorkloadIdentitySettings toAutomationWorkloadIdentity(SpiffeApplicationSettings in) {
        AutomationWorkloadIdentitySettings out = new AutomationWorkloadIdentitySettings();
        out.setTrustDomain(in.getTrustDomain());
        out.setSubject(in.getSubject());
        out.setSubjectMatchMode(in.getSubjectMatchMode());
        return out;
    }

    // --- Automation (request) -> model -------------------------------------

    /**
     * A new Automation-managed application, addressed by {@code key}: its id is the deterministic key-based id.
     */
    public static Application newApplication(Domain domain, String key, AutomationApplication definition, List<IdentityProvider> idps) {
        Application app = new Application();
        app.setId(AutomationIds.applicationId(domain.getId(), key));
        app.setDomain(domain.getId());
        app.setAutomationKey(key);
        app.setManagedBy(ManagedBy.AUTOMATION_API);
        return applyTo(definition, app, domain, idps);
    }

    /**
     * Write every modelled field of {@code definition} onto {@code target} (full desired state); an omitted
     * {@code clientId} keeps the target's one.
     */
    public static Application applyTo(AutomationApplication definition, Application target, Domain domain, List<IdentityProvider> idps) {
        String domainId = domain.getId();
        target.setName(definition.getName());
        target.setDescription(definition.getDescription());
        target.setType(definition.getType());
        target.setKind(definition.getKind());
        target.setEnabled(definition.isEnabled());
        target.setTemplate(definition.isTemplate());
        target.setMetadata(definition.getMetadata());
        target.setCertificate(AutomationDomainMapper.certificateKeyToId(domainId, definition.getCertificate()));
        target.setCertificateKey(definition.getCertificate());
        target.setIdentityProviders(definition.getIdentityProviders() == null ? new TreeSet<>() : definition.getIdentityProviders().stream()
                .map(idp -> toModelIdentityProvider(idp, domainId, idps))
                .collect(toCollection(TreeSet::new)));

        AutomationApplicationSettings settingsIn = definition.getSettings() != null ? definition.getSettings() : new AutomationApplicationSettings();
        ApplicationSettings settings = target.getSettings() != null ? target.getSettings() : new ApplicationSettings();
        ApplicationOAuthSettings oauth = settings.getOauth() != null ? settings.getOauth() : new ApplicationOAuthSettings();
        applyOAuth(settingsIn.getOauth() != null ? settingsIn.getOauth() : new AutomationApplicationOAuthSettings(), oauth);
        if (definition.getClientId() != null) {
            oauth.setClientId(definition.getClientId());
        }
        // the OAuth client name always follows the application name
        oauth.setClientName(definition.getName());
        settings.setOauth(oauth);
        settings.setLogin(settingsIn.getLogin() != null ? settingsIn.getLogin() : new LoginSettings());
        settings.setAccount(settingsIn.getAccount() != null
                ? AutomationDomainMapper.toModelAccountSettings(settingsIn.getAccount(), domainId, idps)
                : new AccountSettings());
        settings.setCookieSettings(settingsIn.getCookieSettings() != null ? toModelCookieSettings(settingsIn.getCookieSettings()) : new CookieSettings());
        settings.setMfa(mapOrNull(settingsIn.getMfa(), AutomationApplicationMapper::toModelMfa));
        settings.setSaml(mapOrNull(settingsIn.getSaml(), AutomationApplicationMapper::toModelSaml));
        settings.setAdvanced(mapOrNull(settingsIn.getAdvanced(), AutomationApplicationMapper::toModelAdvanced));
        settings.setRiskAssessment(mapOrNull(settingsIn.getRiskAssessment(), AutomationApplicationMapper::toModelRiskAssessment));
        settings.setSecretExpirationSettings(settingsIn.getSecretExpirationSettings());
        settings.setWorkloadIdentitySettings(mapOrNull(settingsIn.getWorkloadIdentitySettings(), AutomationApplicationMapper::toModelWorkloadIdentity));
        target.setSettings(settings);
        return target;
    }

    private static ApplicationIdentityProvider toModelIdentityProvider(AutomationApplicationIdentityProvider in, String domainId, List<IdentityProvider> idps) {
        ApplicationIdentityProvider out = new ApplicationIdentityProvider();
        out.setIdentity(AutomationDomainMapper.identityProviderKeyToId(domainId, in.getIdentity(), idps));
        out.setIdentityKey(in.getIdentity());
        out.setSelectionRule(in.getSelectionRule());
        out.setPriority(in.getPriority());
        return out;
    }

    /**
     * Only the modelled fields are written; omitted computed values fall back to what a new application gets.
     */
    private static void applyOAuth(AutomationApplicationOAuthSettings in, ApplicationOAuthSettings out) {
        ApplicationOAuthSettings defaults = new ApplicationOAuthSettings();
        out.setGrantTypes(in.getGrantTypes());
        out.setResponseTypes(in.getResponseTypes());
        out.setRedirectUris(in.getRedirectUris());
        out.setPostLogoutRedirectUris(in.getPostLogoutRedirectUris());
        out.setRequestUris(in.getRequestUris());
        out.setInitiateLoginUri(in.getInitiateLoginUri());
        out.setScopeSettings(mapList(in.getScopeSettings(), AutomationApplicationMapper::toModelScope));
        out.setEnhanceScopesWithUserPermissions(in.isEnhanceScopesWithUserPermissions());
        out.setOptInScopeSelection(in.getOptInScopeSelection() == null || in.getOptInScopeSelection());
        out.setAccessTokenValiditySeconds(valueOrDefault(in.getAccessTokenValiditySeconds(), defaults.getAccessTokenValiditySeconds()));
        out.setRefreshTokenValiditySeconds(valueOrDefault(in.getRefreshTokenValiditySeconds(), defaults.getRefreshTokenValiditySeconds()));
        out.setIdTokenValiditySeconds(valueOrDefault(in.getIdTokenValiditySeconds(), defaults.getIdTokenValiditySeconds()));
        out.setIdJagValiditySeconds(valueOrDefault(in.getIdJagValiditySeconds(), defaults.getIdJagValiditySeconds()));
        out.setDisableRefreshTokenRotation(in.isDisableRefreshTokenRotation());
        out.setTokenCustomClaims(mapList(in.getTokenCustomClaims(), claim -> TokenClaim.of(claim.getTokenType(), claim.getClaimName(), claim.getClaimValue())));
        out.setUserinfoCustomClaims(mapList(in.getUserinfoCustomClaims(), claim -> UserInfoClaim.of(claim.getClaimName(), claim.getClaimValue())));
        out.setLightweightJwtSettings(in.getLightweightJwtSettings());
        out.setTokenExchangeOAuthSettings(in.getTokenExchangeOAuthSettings() != null ? in.getTokenExchangeOAuthSettings() : new TokenExchangeOAuthSettings());
        out.setTokenEndpointAuthMethod(in.getTokenEndpointAuthMethod());
        out.setTokenEndpointAuthSigningAlg(in.getTokenEndpointAuthSigningAlg());
        out.setJwksUri(in.getJwksUri());
        out.setJwks(in.getJwks() != null ? toJwkSet(in.getJwks()) : null);
        out.setTlsClientAuthSubjectDn(in.getTlsClientAuthSubjectDn());
        out.setTlsClientAuthSanDns(in.getTlsClientAuthSanDns());
        out.setTlsClientAuthSanUri(in.getTlsClientAuthSanUri());
        out.setTlsClientAuthSanIp(in.getTlsClientAuthSanIp());
        out.setTlsClientAuthSanEmail(in.getTlsClientAuthSanEmail());
        out.setTlsClientCertificateBoundAccessTokens(in.isTlsClientCertificateBoundAccessTokens());
        out.setDpopBoundAccessTokens(in.isDpopBoundAccessTokens());
        out.setForcePKCE(in.isForcePKCE());
        out.setForceS256CodeChallengeMethod(in.isForceS256CodeChallengeMethod());
        out.setSingleSignOut(in.isSingleSignOut());
        out.setSilentReAuthentication(in.isSilentReAuthentication());
        out.setRequireAuthTime(in.isRequireAuthTime());
        out.setDefaultMaxAge(in.getDefaultMaxAge());
        out.setDefaultACRvalues(in.getDefaultACRvalues());
        out.setIdTokenSignedResponseAlg(in.getIdTokenSignedResponseAlg());
        out.setIdTokenEncryptedResponseAlg(in.getIdTokenEncryptedResponseAlg());
        out.setIdTokenEncryptedResponseEnc(in.getIdTokenEncryptedResponseEnc());
        out.setUserinfoSignedResponseAlg(in.getUserinfoSignedResponseAlg());
        out.setUserinfoEncryptedResponseAlg(in.getUserinfoEncryptedResponseAlg());
        out.setUserinfoEncryptedResponseEnc(in.getUserinfoEncryptedResponseEnc());
        out.setRequestObjectSigningAlg(in.getRequestObjectSigningAlg());
        out.setRequestObjectEncryptionAlg(in.getRequestObjectEncryptionAlg());
        out.setRequestObjectEncryptionEnc(in.getRequestObjectEncryptionEnc());
        out.setAuthorizationSignedResponseAlg(in.getAuthorizationSignedResponseAlg());
        out.setAuthorizationEncryptedResponseAlg(in.getAuthorizationEncryptedResponseAlg());
        out.setAuthorizationEncryptedResponseEnc(in.getAuthorizationEncryptedResponseEnc());
        out.setSubjectType(in.getSubjectType());
        out.setSectorIdentifierUri(in.getSectorIdentifierUri());
        out.setContacts(in.getContacts());
        out.setLogoUri(in.getLogoUri());
        out.setClientUri(in.getClientUri());
        out.setPolicyUri(in.getPolicyUri());
        out.setTosUri(in.getTosUri());
        out.setCrossAppAccessSettings(mapOrNull(in.getCrossAppAccessSettings(),
                settings -> crossAppAccess(settings, reference -> fromIdReference(reference, "crossAppAccessSettings.resourceServers"))));
    }

    private static ApplicationScopeSettings toModelScope(AutomationScopeSettings in) {
        ApplicationScopeSettings out = new ApplicationScopeSettings(in.getScope());
        out.setDefaultScope(in.isDefaultScope());
        out.setScopeApproval(in.getScopeApproval());
        out.setRequiredScope(in.isRequiredScope());
        return out;
    }

    private static CookieSettings toModelCookieSettings(AutomationCookieSettings in) {
        CookieSettings out = new CookieSettings();
        out.setInherited(in.isInherited());
        out.setSession(mapOrNull(in.getSession(), session -> {
            SessionSettings sessionOut = new SessionSettings();
            sessionOut.setPersistent(session.isPersistent());
            return sessionOut;
        }));
        return out;
    }

    private static MFASettings toModelMfa(AutomationMfaSettings in) {
        MFASettings out = new MFASettings();
        out.setLoginRule(in.getLoginRule());
        out.setFactor(mapOrNull(in.getFactor(), factor -> {
            FactorSettings factorOut = new FactorSettings();
            factorOut.setDefaultFactorId(fromIdReference(factor.getDefaultFactorId(), "mfa.factor.defaultFactorId"));
            factorOut.setApplicationFactors(mapList(factor.getApplicationFactors(), appFactor -> {
                ApplicationFactorSettings appFactorOut = new ApplicationFactorSettings();
                appFactorOut.setId(fromIdReference(appFactor.getId(), "mfa.factor.applicationFactors.id"));
                appFactorOut.setSelectionRule(appFactor.getSelectionRule());
                return appFactorOut;
            }));
            return factorOut;
        }));
        out.setStepUpAuthenticationRule(in.getStepUpAuthenticationRule());
        out.setStepUpAuthentication(mapOrNull(in.getStepUpAuthentication(), stepUp -> {
            StepUpAuthenticationSettings stepUpOut = new StepUpAuthenticationSettings();
            stepUpOut.setActive(stepUp.isActive());
            stepUpOut.setStepUpAuthenticationRule(stepUp.getStepUpAuthenticationRule());
            return stepUpOut;
        }));
        out.setAdaptiveAuthenticationRule(in.getAdaptiveAuthenticationRule());
        out.setRememberDevice(mapOrNull(in.getRememberDevice(), rememberDevice -> {
            RememberDeviceSettings rememberDeviceOut = new RememberDeviceSettings();
            rememberDeviceOut.setActive(rememberDevice.isActive());
            rememberDeviceOut.setSkipChallengeWhenRememberDevice(rememberDevice.isSkipChallengeWhenRememberDevice());
            rememberDeviceOut.setExpirationTimeSeconds(rememberDevice.getExpirationTimeSeconds());
            rememberDeviceOut.setDeviceIdentifierId(fromIdReference(rememberDevice.getDeviceIdentifierId(), "mfa.rememberDevice.deviceIdentifierId"));
            return rememberDeviceOut;
        }));
        out.setEnrollment(mapOrNull(in.getEnrollment(), enrollment -> {
            EnrollmentSettings enrollmentOut = new EnrollmentSettings();
            enrollmentOut.setForceEnrollment(enrollment.getForceEnrollment());
            enrollmentOut.setSkipTimeSeconds(enrollment.getSkipTimeSeconds());
            return enrollmentOut;
        }));
        out.setChallenge(mapOrNull(in.getChallenge(), challenge -> {
            ChallengeSettings challengeOut = new ChallengeSettings();
            challengeOut.setActive(challenge.isActive());
            challengeOut.setChallengeRule(challenge.getChallengeRule());
            challengeOut.setType(challenge.getType());
            return challengeOut;
        }));
        out.setEnroll(mapOrNull(in.getEnroll(), enroll -> {
            EnrollSettings enrollOut = new EnrollSettings();
            enrollOut.setActive(enroll.isActive());
            enrollOut.setForceEnrollment(enroll.getForceEnrollment());
            enrollOut.setEnrollmentRule(enroll.getEnrollmentRule());
            enrollOut.setEnrollmentSkipActive(enroll.isEnrollmentSkipActive());
            enrollOut.setEnrollmentSkipRule(enroll.getEnrollmentSkipRule());
            enrollOut.setSkipTimeSeconds(enroll.getSkipTimeSeconds());
            enrollOut.setType(enroll.getType());
            return enrollOut;
        }));
        return out;
    }

    private static ApplicationSAMLSettings toModelSaml(AutomationApplicationSAMLSettings in) {
        ApplicationSAMLSettings out = new ApplicationSAMLSettings();
        out.setEntityId(in.getEntityId());
        out.setAttributeConsumeServiceUrl(in.getAttributeConsumeServiceUrl());
        out.setSingleLogoutServiceUrl(in.getSingleLogoutServiceUrl());
        out.setCertificate(in.getCertificate());
        out.setWantResponseSigned(in.isWantResponseSigned());
        out.setWantAssertionsSigned(in.isWantAssertionsSigned());
        out.setWantAssertionsEncrypted(in.isWantAssertionsEncrypted());
        out.setKeyTransportEncryptionAlgorithm(in.getKeyTransportEncryptionAlgorithm());
        out.setDataEncryptionAlgorithm(in.getDataEncryptionAlgorithm());
        out.setResponseBinding(in.getResponseBinding());
        out.setNameIdMapping(in.getNameIdMapping());
        out.setAssertionAttributes(mapList(in.getAssertionAttributes(), attribute -> new SAMLAssertionAttribute(attribute.getName(), attribute.getValue())));
        out.setIncludeAssertionConditions(in.isIncludeAssertionConditions());
        out.setAudiences(in.getAudiences());
        out.setAssertionValiditySeconds(in.getAssertionValiditySeconds());
        out.setNotBeforeTimeSkewSeconds(in.getNotBeforeTimeSkewSeconds());
        out.setNotOnOrAfterTimeSkewSeconds(in.getNotOnOrAfterTimeSkewSeconds());
        return out;
    }

    private static ApplicationAdvancedSettings toModelAdvanced(AutomationAdvancedSettings in) {
        ApplicationAdvancedSettings out = new ApplicationAdvancedSettings();
        out.setSkipConsent(in.isSkipConsent());
        out.setFlowsInherited(in.isFlowsInherited());
        return out;
    }

    private static RiskAssessmentSettings toModelRiskAssessment(AutomationRiskAssessmentSettings in) {
        RiskAssessmentSettings out = new RiskAssessmentSettings();
        out.setEnabled(in.isEnabled());
        out.setDeviceAssessment(mapOrNull(in.getDeviceAssessment(), AutomationApplicationMapper::toModelAssessment));
        out.setIpReputationAssessment(mapOrNull(in.getIpReputationAssessment(), AutomationApplicationMapper::toModelAssessment));
        out.setGeoVelocityAssessment(mapOrNull(in.getGeoVelocityAssessment(), AutomationApplicationMapper::toModelAssessment));
        return out;
    }

    private static AssessmentSettings toModelAssessment(AutomationAssessmentSettings in) {
        AssessmentSettings out = new AssessmentSettings();
        out.setEnabled(in.isEnabled());
        out.setThresholds(in.getThresholds());
        return out;
    }

    private static SpiffeApplicationSettings toModelWorkloadIdentity(AutomationWorkloadIdentitySettings in) {
        SpiffeApplicationSettings out = new SpiffeApplicationSettings();
        out.setTrustDomain(in.getTrustDomain());
        out.setSubject(in.getSubject());
        out.setSubjectMatchMode(in.getSubjectMatchMode());
        return out;
    }

    private static JWKSet toJwkSet(Map<String, Object> jwks) {
        try {
            return JWKS_MAPPER.convertValue(jwks, JWKSet.class);
        } catch (IllegalArgumentException ex) {
            throw new InvalidParameterException("jwks is not a valid JSON Web Key Set");
        }
    }

    // --- references ---------------------------------------------------------

    /** The stored reference as given, or {@code id:<uuid>} when the Automation API did not set it. */
    private static String echoReference(String reference, String id) {
        if (reference != null) {
            return reference;
        }
        return id != null ? AutomationRef.ID_PREFIX + id : null;
    }

    private static String toIdReference(String id) {
        return id != null ? AutomationRef.ID_PREFIX + id : null;
    }

    /** Strips the {@code id:} prefix of a reference to a resource the Automation API does not manage. */
    private static String fromIdReference(String reference, String field) {
        if (reference == null) {
            return null;
        }
        return switch (AutomationRef.parse(reference, field)) {
            case AutomationRef.IdRef(String id) -> id;
            case AutomationRef.KeyRef ignored -> throw new InvalidParameterException(field + " only accepts id:<uuid> references");
        };
    }

    /** A copy of the Cross App Access settings with each resource-server reference converted. */
    private static ApplicationCrossAppAccessSettings crossAppAccess(ApplicationCrossAppAccessSettings in, Function<String, String> convertReference) {
        ApplicationCrossAppAccessSettings out = new ApplicationCrossAppAccessSettings(in);
        out.setResourceServers(mapList(in.getResourceServers(), resourceServer -> {
            ApplicationCrossAppAccessResourceServer resourceServerOut = new ApplicationCrossAppAccessResourceServer(resourceServer);
            resourceServerOut.setTrustDomainId(convertReference.apply(resourceServer.getTrustDomainId()));
            resourceServerOut.setResourceServerId(convertReference.apply(resourceServer.getResourceServerId()));
            return resourceServerOut;
        }));
        return out;
    }

    // --- helpers ------------------------------------------------------------

    private static <I, O> O mapOrNull(I in, Function<I, O> mapper) {
        return in != null ? mapper.apply(in) : null;
    }

    /** Mutable: the application templates may complete the lists (e.g. the resource-server scope). */
    private static <I, O> List<O> mapList(List<I> in, Function<I, O> mapper) {
        return in != null ? in.stream().map(mapper).collect(toCollection(ArrayList::new)) : null;
    }

    private static int valueOrDefault(Integer value, int defaultValue) {
        return value != null ? value : defaultValue;
    }
}
