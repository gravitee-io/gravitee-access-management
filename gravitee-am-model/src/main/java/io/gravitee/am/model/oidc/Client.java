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
package io.gravitee.am.model.oidc;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.ResponseType;
import io.gravitee.am.common.oidc.ApplicationType;
import io.gravitee.am.common.oidc.ClientAuthenticationMethod;
import io.gravitee.am.model.ApplicationFactorSettings;
import io.gravitee.am.model.CookieSettings;
import io.gravitee.am.model.FactorSettings;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.PasswordSettings;
import io.gravitee.am.model.PasswordSettingsAware;
import io.gravitee.am.model.Resource;
import io.gravitee.am.model.SecretExpirationSettings;
import io.gravitee.am.model.TokenClaim;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.application.ApplicationScopeSettings;
import io.gravitee.am.model.application.ApplicationSecretSettings;
import io.gravitee.am.model.application.ClientSecret;
import io.gravitee.am.model.idp.ApplicationIdentityProvider;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.risk.assessment.api.assessment.settings.RiskAssessmentSettings;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.springframework.util.CollectionUtils.isEmpty;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 * See https://openid.net/specs/openid-connect-registration-1_0.html#ClientMetadata
 */
@Builder
@AllArgsConstructor
public class Client implements Cloneable, Resource, PasswordSettingsAware {

    public static final int DEFAULT_ACCESS_TOKEN_VALIDITY_SECONDS = 7200;
    public static final int DEFAULT_REFRESH_TOKEN_VALIDITY_SECONDS = 14400;
    public static final int DEFAULT_ID_TOKEN_VALIDITY_SECONDS = 14400;
    public static final List<String> DEFAULT_GRANT_TYPES = Collections.singletonList(GrantType.AUTHORIZATION_CODE);
    public static final List<String> DEFAULT_RESPONSE_TYPES = Collections.singletonList(ResponseType.CODE);

    private String id;

    private String clientId;

    private String clientSecret;

    private String agentCardUri;

    @JsonIgnore
    private List<ApplicationSecretSettings> secretSettings;

    @JsonIgnore
    private List<ClientSecret> clientSecrets;

    private List<String> redirectUris;

    private List<String> authorizedGrantTypes = DEFAULT_GRANT_TYPES;

    private List<String> responseTypes = DEFAULT_RESPONSE_TYPES;

    //Default value must be web.
    private String applicationType = ApplicationType.WEB;

    private List<String> contacts;

    private String clientName;

    private String logoUri;

    private String clientUri;

    private String policyUri;

    private String tosUri;

    private String jwksUri;

    private JWKSet jwks;

    private String sectorIdentifierUri;

    private String subjectType;

    private String idTokenSignedResponseAlg;

    private String idTokenEncryptedResponseAlg;

    private String idTokenEncryptedResponseEnc;

    private String userinfoSignedResponseAlg;

    private String userinfoEncryptedResponseAlg;

    private String userinfoEncryptedResponseEnc;

    private String requestObjectSigningAlg;

    private String requestObjectEncryptionAlg;

    private String requestObjectEncryptionEnc;

    private String tokenEndpointAuthMethod = ClientAuthenticationMethod.CLIENT_SECRET_BASIC;

    private String tokenEndpointAuthSigningAlg;

    private Integer defaultMaxAge;

    private Boolean requireAuthTime = false;

    private List<String> defaultACRvalues;

    private String initiateLoginUri;

    private List<String> requestUris;

    private String softwareId; //Should be UUID

    private String softwareVersion;

    private String softwareStatement; //Should be JWT

    private String registrationAccessToken;

    private String registrationClientUri;

    private Date clientIdIssuedAt;

    private Date clientSecretExpiresAt;

    private List<String> autoApproveScopes;

    private int accessTokenValiditySeconds = DEFAULT_ACCESS_TOKEN_VALIDITY_SECONDS;

    private int refreshTokenValiditySeconds = DEFAULT_REFRESH_TOKEN_VALIDITY_SECONDS;

    private int idTokenValiditySeconds = DEFAULT_ID_TOKEN_VALIDITY_SECONDS;

    private String tlsClientAuthSubjectDn;

    private String tlsClientAuthSanDns;

    private String tlsClientAuthSanUri;

    private String tlsClientAuthSanIp;

    private String tlsClientAuthSanEmail;

    private boolean tlsClientCertificateBoundAccessTokens;

    private String authorizationSignedResponseAlg;

    private String authorizationEncryptedResponseAlg;

    private String authorizationEncryptedResponseEnc;

    /**
     * Security domain associated to the client
     */
    private String domain;

    /**
     * Client enabled.
     */
    private boolean enabled;

    /**
     * The Client creation date
     */
    private Date createdAt;

    /**
     * The Client last updated date
     */
    private Date updatedAt;

    private String certificate;

    /**
     * Deprecated since 4.3. Use factorSettings instead
     */
    @Deprecated
    private Set<String> factors;

    private FactorSettings factorSettings;

    private boolean enhanceScopesWithUserPermissions;

    private List<ApplicationScopeSettings> scopeSettings;

    private AccountSettings accountSettings;

    private SortedSet<ApplicationIdentityProvider> identityProviders;

    private LoginSettings loginSettings;

    private PasswordSettings passwordSettings;

    private List<TokenClaim> tokenCustomClaims;

    private boolean template;

    private Map<String, Object> metadata;

    private boolean forcePKCE;

    private boolean forceS256CodeChallengeMethod;

    private List<String> postLogoutRedirectUris;

    private boolean flowsInherited;

    private MFASettings mfaSettings;

    private CookieSettings cookieSettings;

    private RiskAssessmentSettings riskAssessment;

    private boolean singleSignOut;

    private boolean silentReAuthentication;

    private boolean requireParRequest;

    private String backchannelTokenDeliveryMode;

    private String backchannelClientNotificationEndpoint;

    private String backchannelAuthRequestSignAlg;

    private boolean backchannelUserCodeParameter;

    // ----------- SAML Settings ----------------
    private String entityId;

    private String attributeConsumeServiceUrl;

    private String singleLogoutServiceUrl;

    private String samlCertificate;

    private boolean wantResponseSigned;

    private boolean wantAssertionsSigned;

    private String responseBinding;

    // ----------- Refresh token Settings -----------
    private boolean disableRefreshTokenRotation;

    private SecretExpirationSettings secretExpirationSettings;

    public Client() {
    }

    public Client(Client other) {
        this.id = other.id;
        this.clientId = other.clientId;
        this.clientSecret = other.clientSecret;
        this.redirectUris = other.redirectUris != null ? new ArrayList<>(other.redirectUris) : null;
        this.authorizedGrantTypes = other.authorizedGrantTypes != null ? new ArrayList<>(other.authorizedGrantTypes) : null;
        this.responseTypes = other.responseTypes != null ? new ArrayList<>(other.responseTypes) : null;
        this.applicationType = other.applicationType;
        this.contacts = other.contacts != null ? new ArrayList<>(other.contacts) : null;
        this.clientName = other.clientName;
        this.logoUri = other.logoUri;
        this.clientUri = other.clientUri;
        this.policyUri = other.policyUri;
        this.tosUri = other.tosUri;
        this.jwksUri = other.jwksUri;
        this.agentCardUri = other.agentCardUri;
        this.jwks = other.jwks;
        this.sectorIdentifierUri = other.sectorIdentifierUri;
        this.subjectType = other.subjectType;
        this.idTokenSignedResponseAlg = other.idTokenSignedResponseAlg;
        this.idTokenEncryptedResponseAlg = other.idTokenEncryptedResponseAlg;
        this.idTokenEncryptedResponseEnc = other.idTokenEncryptedResponseEnc;
        this.userinfoSignedResponseAlg = other.userinfoSignedResponseAlg;
        this.userinfoEncryptedResponseAlg = other.userinfoEncryptedResponseAlg;
        this.userinfoEncryptedResponseEnc = other.userinfoEncryptedResponseEnc;
        this.requestObjectSigningAlg = other.requestObjectSigningAlg;
        this.requestObjectEncryptionAlg = other.requestObjectEncryptionAlg;
        this.requestObjectEncryptionEnc = other.requestObjectEncryptionEnc;
        this.tokenEndpointAuthMethod = other.tokenEndpointAuthMethod;
        this.tokenEndpointAuthSigningAlg = other.tokenEndpointAuthSigningAlg;
        this.defaultMaxAge = other.defaultMaxAge;
        this.requireAuthTime = other.requireAuthTime;
        this.defaultACRvalues = other.defaultACRvalues;
        this.initiateLoginUri = other.initiateLoginUri;
        this.requestUris = other.requestUris != null ? new ArrayList<>(other.requestUris) : null;
        this.softwareId = other.softwareId;
        this.softwareVersion = other.softwareVersion;
        this.softwareStatement = other.softwareStatement;
        this.registrationAccessToken = other.registrationAccessToken;
        this.registrationClientUri = other.registrationClientUri;
        this.clientIdIssuedAt = other.clientIdIssuedAt;
        this.clientSecretExpiresAt = other.clientSecretExpiresAt;
        this.autoApproveScopes = other.autoApproveScopes != null ? new ArrayList<>(other.autoApproveScopes) : null;
        this.accessTokenValiditySeconds = other.accessTokenValiditySeconds;
        this.refreshTokenValiditySeconds = other.refreshTokenValiditySeconds;
        this.idTokenValiditySeconds = other.idTokenValiditySeconds;
        this.domain = other.domain;
        this.enabled = other.enabled;
        this.createdAt = other.createdAt;
        this.updatedAt = other.updatedAt;
        this.identityProviders = other.identityProviders != null ? new TreeSet<>(other.identityProviders) : null;
        this.factors = other.factors != null ? new HashSet<>(other.factors) : null;
        this.factorSettings = other.factorSettings;
        this.certificate = other.certificate;
        this.enhanceScopesWithUserPermissions = other.enhanceScopesWithUserPermissions;
        this.scopeSettings = other.scopeSettings != null ? new ArrayList<>(other.scopeSettings) : null;
        this.accountSettings = other.accountSettings;
        this.loginSettings = other.loginSettings;
        this.passwordSettings = other.passwordSettings;
        this.tokenCustomClaims = other.tokenCustomClaims != null ? new ArrayList<>(other.tokenCustomClaims) : null;
        this.template = other.template;
        this.metadata = other.metadata != null ? new HashMap<>(other.metadata) : null;
        this.authorizationSignedResponseAlg = other.authorizationSignedResponseAlg;
        this.authorizationEncryptedResponseAlg = other.authorizationEncryptedResponseAlg;
        this.authorizationEncryptedResponseEnc = other.authorizationEncryptedResponseEnc;
        this.forcePKCE = other.forcePKCE;
        this.postLogoutRedirectUris = other.postLogoutRedirectUris;
        this.flowsInherited = other.flowsInherited;
        this.mfaSettings = other.mfaSettings;
        this.cookieSettings = other.cookieSettings;
        this.riskAssessment = other.riskAssessment;
        this.singleSignOut = other.singleSignOut;
        this.silentReAuthentication = other.silentReAuthentication;
        this.tlsClientCertificateBoundAccessTokens = other.tlsClientCertificateBoundAccessTokens;
        this.requireParRequest = other.requireParRequest;
        this.backchannelTokenDeliveryMode = other.backchannelTokenDeliveryMode;
        this.backchannelClientNotificationEndpoint = other.backchannelClientNotificationEndpoint;
        this.backchannelAuthRequestSignAlg = other.backchannelAuthRequestSignAlg;
        this.backchannelUserCodeParameter = other.backchannelUserCodeParameter;
        this.entityId = other.entityId;
        this.attributeConsumeServiceUrl = other.attributeConsumeServiceUrl;
        this.singleLogoutServiceUrl = other.singleLogoutServiceUrl;
        this.samlCertificate = other.samlCertificate;
        this.wantResponseSigned = other.wantResponseSigned;
        this.wantAssertionsSigned = other.wantAssertionsSigned;
        this.responseBinding = other.responseBinding;
        this.disableRefreshTokenRotation = other.disableRefreshTokenRotation;
        this.secretExpirationSettings = other.secretExpirationSettings;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public List<String> getRedirectUris() {
        return redirectUris;
    }

    public void setRedirectUris(List<String> redirectUris) {
        this.redirectUris = redirectUris;
    }

    public List<String> getAuthorizedGrantTypes() {
        return authorizedGrantTypes;
    }

    public void setAuthorizedGrantTypes(List<String> grantTypes) {
        this.authorizedGrantTypes = grantTypes;
    }

    public List<String> getResponseTypes() {
        return responseTypes;
    }

    public void setResponseTypes(List<String> responseTypes) {
        this.responseTypes = responseTypes;
    }

    public String getApplicationType() {
        return applicationType;
    }

    public void setApplicationType(String applicationType) {
        this.applicationType = applicationType;
    }

    public List<String> getContacts() {
        return contacts;
    }

    public void setContacts(List<String> contacts) {
        this.contacts = contacts;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getLogoUri() {
        return logoUri;
    }

    public void setLogoUri(String logoUri) {
        this.logoUri = logoUri;
    }

    public String getClientUri() {
        return clientUri;
    }

    public void setClientUri(String clientUri) {
        this.clientUri = clientUri;
    }

    public String getPolicyUri() {
        return policyUri;
    }

    public void setPolicyUri(String policyUri) {
        this.policyUri = policyUri;
    }

    public String getTosUri() {
        return tosUri;
    }

    public void setTosUri(String tosUri) {
        this.tosUri = tosUri;
    }

    public String getJwksUri() {
        return jwksUri;
    }

    public void setJwksUri(String jwksUri) {
        this.jwksUri = jwksUri;
    }

    public JWKSet getJwks() {
        return jwks;
    }

    public void setJwks(JWKSet jwks) {
        this.jwks = jwks;
    }

    public String getSectorIdentifierUri() {
        return sectorIdentifierUri;
    }

    public void setSectorIdentifierUri(String sectorIdentifierUri) {
        this.sectorIdentifierUri = sectorIdentifierUri;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType;
    }

    public String getIdTokenSignedResponseAlg() {
        return idTokenSignedResponseAlg;
    }

    public void setIdTokenSignedResponseAlg(String idTokenSignedResponseAlg) {
        this.idTokenSignedResponseAlg = idTokenSignedResponseAlg;
    }

    public String getIdTokenEncryptedResponseAlg() {
        return idTokenEncryptedResponseAlg;
    }

    public void setIdTokenEncryptedResponseAlg(String idTokenEncryptedResponseAlg) {
        this.idTokenEncryptedResponseAlg = idTokenEncryptedResponseAlg;
    }

    public String getIdTokenEncryptedResponseEnc() {
        return idTokenEncryptedResponseEnc;
    }

    public void setIdTokenEncryptedResponseEnc(String idTokenEncryptedResponseEnc) {
        this.idTokenEncryptedResponseEnc = idTokenEncryptedResponseEnc;
    }

    public String getUserinfoSignedResponseAlg() {
        return userinfoSignedResponseAlg;
    }

    public void setUserinfoSignedResponseAlg(String userinfoSignedResponseAlg) {
        this.userinfoSignedResponseAlg = userinfoSignedResponseAlg;
    }

    public String getUserinfoEncryptedResponseAlg() {
        return userinfoEncryptedResponseAlg;
    }

    public void setUserinfoEncryptedResponseAlg(String userinfoEncryptedResponseAlg) {
        this.userinfoEncryptedResponseAlg = userinfoEncryptedResponseAlg;
    }

    public String getUserinfoEncryptedResponseEnc() {
        return userinfoEncryptedResponseEnc;
    }

    public void setUserinfoEncryptedResponseEnc(String userinfoEncryptedResponseEnc) {
        this.userinfoEncryptedResponseEnc = userinfoEncryptedResponseEnc;
    }

    public String getRequestObjectSigningAlg() {
        return requestObjectSigningAlg;
    }

    public void setRequestObjectSigningAlg(String requestObjectSigningAlg) {
        this.requestObjectSigningAlg = requestObjectSigningAlg;
    }

    public String getRequestObjectEncryptionAlg() {
        return requestObjectEncryptionAlg;
    }

    public void setRequestObjectEncryptionAlg(String requestObjectEncryptionAlg) {
        this.requestObjectEncryptionAlg = requestObjectEncryptionAlg;
    }

    public String getRequestObjectEncryptionEnc() {
        return requestObjectEncryptionEnc;
    }

    public void setRequestObjectEncryptionEnc(String requestObjectEncryptionEnc) {
        this.requestObjectEncryptionEnc = requestObjectEncryptionEnc;
    }

    public String getTokenEndpointAuthMethod() {
        return tokenEndpointAuthMethod;
    }

    public void setTokenEndpointAuthMethod(String tokenEndpointAuthMethod) {
        this.tokenEndpointAuthMethod = tokenEndpointAuthMethod;
    }

    public String getTokenEndpointAuthSigningAlg() {
        return tokenEndpointAuthSigningAlg;
    }

    public void setTokenEndpointAuthSigningAlg(String tokenEndpointAuthSigningAlg) {
        this.tokenEndpointAuthSigningAlg = tokenEndpointAuthSigningAlg;
    }

    public Integer getDefaultMaxAge() {
        return defaultMaxAge;
    }

    public void setDefaultMaxAge(Integer defaultMaxAge) {
        this.defaultMaxAge = defaultMaxAge;
    }

    public Boolean getRequireAuthTime() {
        return requireAuthTime;
    }

    public void setRequireAuthTime(Boolean requireAuthTime) {
        this.requireAuthTime = requireAuthTime;
    }

    public List<String> getDefaultACRvalues() {
        return defaultACRvalues;
    }

    public void setDefaultACRvalues(List<String> defaultACRvalues) {
        this.defaultACRvalues = defaultACRvalues;
    }

    public String getInitiateLoginUri() {
        return initiateLoginUri;
    }

    public void setInitiateLoginUri(String initiateLoginUri) {
        this.initiateLoginUri = initiateLoginUri;
    }

    public List<String> getRequestUris() {
        return requestUris;
    }

    public void setRequestUris(List<String> requestUris) {
        this.requestUris = requestUris;
    }

    public String getSoftwareId() {
        return softwareId;
    }

    public void setSoftwareId(String softwareId) {
        this.softwareId = softwareId;
    }

    public String getSoftwareVersion() {
        return softwareVersion;
    }

    public void setSoftwareVersion(String softwareVersion) {
        this.softwareVersion = softwareVersion;
    }

    public String getSoftwareStatement() {
        return softwareStatement;
    }

    public void setSoftwareStatement(String softwareStatement) {
        this.softwareStatement = softwareStatement;
    }

    public String getRegistrationAccessToken() {
        return registrationAccessToken;
    }

    public void setRegistrationAccessToken(String registrationAccessToken) {
        this.registrationAccessToken = registrationAccessToken;
    }

    public String getRegistrationClientUri() {
        return registrationClientUri;
    }

    public void setRegistrationClientUri(String registrationClientUri) {
        this.registrationClientUri = registrationClientUri;
    }

    public Date getClientIdIssuedAt() {
        return clientIdIssuedAt;
    }

    public void setClientIdIssuedAt(Date clientIdIssuedAt) {
        this.clientIdIssuedAt = clientIdIssuedAt;
    }

    public Date getClientSecretExpiresAt() {
        return clientSecretExpiresAt;
    }

    public void setClientSecretExpiresAt(Date clientSecretExpiresAt) {
        //Enforce date to UTC time zone.
        if (clientSecretExpiresAt != null) {
            this.clientSecretExpiresAt = Date.from(
                    ZonedDateTime.ofInstant(clientSecretExpiresAt.toInstant(), ZoneId.of("UTC")).toInstant()
            );
        }
    }

    public List<String> getAutoApproveScopes() {
        return autoApproveScopes;
    }

    public void setAutoApproveScopes(List<String> autoApproveScopes) {
        this.autoApproveScopes = autoApproveScopes;
    }

    public int getAccessTokenValiditySeconds() {
        return accessTokenValiditySeconds;
    }

    public void setAccessTokenValiditySeconds(int accessTokenValiditySeconds) {
        this.accessTokenValiditySeconds = accessTokenValiditySeconds;
    }

    public int getRefreshTokenValiditySeconds() {
        return refreshTokenValiditySeconds;
    }

    public void setRefreshTokenValiditySeconds(int refreshTokenValiditySeconds) {
        this.refreshTokenValiditySeconds = refreshTokenValiditySeconds;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Use getFactorSettings instead
     * This method remains only for backward compatibility as it maybe use in some policy flows
     * @return
     */
    @Deprecated
    public Set<String> getFactors() {
        if (factorSettings != null && !isEmpty(factorSettings.getApplicationFactors())) {
            return factorSettings.getApplicationFactors().stream().map(ApplicationFactorSettings::getId).collect(Collectors.toUnmodifiableSet());
        }
        return factors;
    }

    /**
     * Use setFactorSettings instead
     * This method remains only for backward compatibility
     * @param factors
     */
    @Deprecated
    public void setFactors(Set<String> factors) {
        this.factors = factors;
    }

    public FactorSettings getFactorSettings() {
        return factorSettings;
    }

    public void setFactorSettings(FactorSettings factorSettings) {
        this.factorSettings = factorSettings;
    }

    public int getIdTokenValiditySeconds() {
        return idTokenValiditySeconds;
    }

    public void setIdTokenValiditySeconds(int idTokenValiditySeconds) {
        this.idTokenValiditySeconds = idTokenValiditySeconds;
    }

    public String getCertificate() {
        return certificate;
    }

    public void setCertificate(String certificate) {
        this.certificate = certificate;
    }

    public boolean isEnhanceScopesWithUserPermissions() {
        return enhanceScopesWithUserPermissions;
    }

    public void setEnhanceScopesWithUserPermissions(boolean enhanceScopesWithUserPermissions) {
        this.enhanceScopesWithUserPermissions = enhanceScopesWithUserPermissions;
    }

    public List<ApplicationScopeSettings> getScopeSettings() {
        return scopeSettings;
    }

    public void setScopeSettings(List<ApplicationScopeSettings> scopeSettings) {
        this.scopeSettings = scopeSettings;
    }

    public AccountSettings getAccountSettings() {
        return accountSettings;
    }

    public void setAccountSettings(AccountSettings accountSettings) {
        this.accountSettings = accountSettings;
    }

    public LoginSettings getLoginSettings() {
        return loginSettings;
    }

    public void setLoginSettings(LoginSettings loginSettings) {
        this.loginSettings = loginSettings;
    }

    public PasswordSettings getPasswordSettings() {
        return passwordSettings;
    }

    public void setPasswordSettings(PasswordSettings passwordSettings) {
        this.passwordSettings = passwordSettings;
    }

    public List<TokenClaim> getTokenCustomClaims() {
        return tokenCustomClaims;
    }

    public void setTokenCustomClaims(List<TokenClaim> tokenCustomClaims) {
        this.tokenCustomClaims = tokenCustomClaims;
    }

    public boolean isTemplate() {
        return template;
    }

    public void setTemplate(boolean template) {
        this.template = template;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public String getTlsClientAuthSubjectDn() {
        return tlsClientAuthSubjectDn;
    }

    public void setTlsClientAuthSubjectDn(String tlsClientAuthSubjectDn) {
        this.tlsClientAuthSubjectDn = tlsClientAuthSubjectDn;
    }

    public String getTlsClientAuthSanDns() {
        return tlsClientAuthSanDns;
    }

    public void setTlsClientAuthSanDns(String tlsClientAuthSanDns) {
        this.tlsClientAuthSanDns = tlsClientAuthSanDns;
    }

    public String getTlsClientAuthSanUri() {
        return tlsClientAuthSanUri;
    }

    public void setTlsClientAuthSanUri(String tlsClientAuthSanUri) {
        this.tlsClientAuthSanUri = tlsClientAuthSanUri;
    }

    public String getTlsClientAuthSanIp() {
        return tlsClientAuthSanIp;
    }

    public void setTlsClientAuthSanIp(String tlsClientAuthSanIp) {
        this.tlsClientAuthSanIp = tlsClientAuthSanIp;
    }

    public String getTlsClientAuthSanEmail() {
        return tlsClientAuthSanEmail;
    }

    public void setTlsClientAuthSanEmail(String tlsClientAuthSanEmail) {
        this.tlsClientAuthSanEmail = tlsClientAuthSanEmail;
    }

    public String getAuthorizationSignedResponseAlg() {
        return authorizationSignedResponseAlg;
    }

    public void setAuthorizationSignedResponseAlg(String authorizationSignedResponseAlg) {
        this.authorizationSignedResponseAlg = authorizationSignedResponseAlg;
    }

    public String getAuthorizationEncryptedResponseAlg() {
        return authorizationEncryptedResponseAlg;
    }

    public void setAuthorizationEncryptedResponseAlg(String authorizationEncryptedResponseAlg) {
        this.authorizationEncryptedResponseAlg = authorizationEncryptedResponseAlg;
    }

    public String getAuthorizationEncryptedResponseEnc() {
        return authorizationEncryptedResponseEnc;
    }

    public void setAuthorizationEncryptedResponseEnc(String authorizationEncryptedResponseEnc) {
        this.authorizationEncryptedResponseEnc = authorizationEncryptedResponseEnc;
    }

    public boolean isForcePKCE() {
        return forcePKCE;
    }

    public void setForcePKCE(boolean forcePKCE) {
        this.forcePKCE = forcePKCE;
    }

    public boolean isForceS256CodeChallengeMethod() {
        return forceS256CodeChallengeMethod;
    }

    public void setForceS256CodeChallengeMethod(boolean forceS256CodeChallengeMethod) {
        this.forceS256CodeChallengeMethod = forceS256CodeChallengeMethod;
    }

    public List<String> getPostLogoutRedirectUris() {
        return postLogoutRedirectUris;
    }

    public void setPostLogoutRedirectUris(List<String> postLogoutRedirectUris) {
        this.postLogoutRedirectUris = postLogoutRedirectUris;
    }

    public boolean isFlowsInherited() {
        return flowsInherited;
    }

    public void setFlowsInherited(boolean flowsInherited) {
        this.flowsInherited = flowsInherited;
    }

    public MFASettings getMfaSettings() {
        return mfaSettings;
    }

    public void setMfaSettings(MFASettings mfaSettings) {
        this.mfaSettings = mfaSettings;
    }

    public boolean isSingleSignOut() {
        return singleSignOut;
    }

    public void setSingleSignOut(boolean singleSignOut) {
        this.singleSignOut = singleSignOut;
    }

    public boolean isSilentReAuthentication() {
        return silentReAuthentication;
    }

    public void setSilentReAuthentication(boolean silentReAuthentication) {
        this.silentReAuthentication = silentReAuthentication;
    }

    public CookieSettings getCookieSettings() {
        return cookieSettings;
    }

    public void setCookieSettings(CookieSettings cookieSettings) {
        this.cookieSettings = cookieSettings;
    }

    public RiskAssessmentSettings getRiskAssessment() {
        return riskAssessment;
    }

    public void setRiskAssessment(RiskAssessmentSettings riskAssessment) {
        this.riskAssessment = riskAssessment;
    }

    public boolean isTlsClientCertificateBoundAccessTokens() {
        return tlsClientCertificateBoundAccessTokens;
    }

    public void setTlsClientCertificateBoundAccessTokens(boolean tlsClientCertificateBoundAccessTokens) {
        this.tlsClientCertificateBoundAccessTokens = tlsClientCertificateBoundAccessTokens;
    }

    public boolean isRequireParRequest() {
        return requireParRequest;
    }

    public void setRequireParRequest(boolean requireParRequest) {
        this.requireParRequest = requireParRequest;
    }

    public String getBackchannelTokenDeliveryMode() {
        return backchannelTokenDeliveryMode;
    }

    public void setBackchannelTokenDeliveryMode(String backchannelTokenDeliveryMode) {
        this.backchannelTokenDeliveryMode = backchannelTokenDeliveryMode;
    }

    public String getBackchannelClientNotificationEndpoint() {
        return backchannelClientNotificationEndpoint;
    }

    public void setBackchannelClientNotificationEndpoint(String backchannelClientNotificationEndpoint) {
        this.backchannelClientNotificationEndpoint = backchannelClientNotificationEndpoint;
    }

    public String getBackchannelAuthRequestSignAlg() {
        return backchannelAuthRequestSignAlg;
    }

    public void setBackchannelAuthRequestSignAlg(String backchannelAuthRequestSignAlg) {
        this.backchannelAuthRequestSignAlg = backchannelAuthRequestSignAlg;
    }

    public boolean getBackchannelUserCodeParameter() {
        return backchannelUserCodeParameter;
    }

    public void setBackchannelUserCodeParameter(boolean backchannelUserCodeParameter) {
        this.backchannelUserCodeParameter = backchannelUserCodeParameter;
    }

    public SortedSet<ApplicationIdentityProvider> getIdentityProviders() {
        return identityProviders;
    }

    public void setIdentityProviders(SortedSet<ApplicationIdentityProvider> identityProviders) {
        this.identityProviders = identityProviders;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getAttributeConsumeServiceUrl() {
        return attributeConsumeServiceUrl;
    }

    public void setAttributeConsumeServiceUrl(String attributeConsumeServiceUrl) {
        this.attributeConsumeServiceUrl = attributeConsumeServiceUrl;
    }

    public String getSingleLogoutServiceUrl() {
        return singleLogoutServiceUrl;
    }

    public void setSingleLogoutServiceUrl(String singleLogoutServiceUrl) {
        this.singleLogoutServiceUrl = singleLogoutServiceUrl;
    }

    public String getSamlCertificate() {
        return samlCertificate;
    }

    public void setSamlCertificate(String samlCertificate) {
        this.samlCertificate = samlCertificate;
    }

    public boolean isWantResponseSigned() {
        return wantResponseSigned;
    }

    public void setWantResponseSigned(boolean wantResponseSigned) {
        this.wantResponseSigned = wantResponseSigned;
    }

    public boolean isWantAssertionsSigned() {
        return wantAssertionsSigned;
    }

    public void setWantAssertionsSigned(boolean wantAssertionsSigned) {
        this.wantAssertionsSigned = wantAssertionsSigned;
    }

    public String getResponseBinding() {
        return responseBinding;
    }

    public void setResponseBinding(String responseBinding) {
        this.responseBinding = responseBinding;
    }

    public boolean isBackchannelUserCodeParameter() {
        return backchannelUserCodeParameter;
    }

    public boolean isDisableRefreshTokenRotation() {
        return disableRefreshTokenRotation;
    }

    public void setDisableRefreshTokenRotation(boolean disableRefreshTokenRotation) {
        this.disableRefreshTokenRotation = disableRefreshTokenRotation;
    }

    public List<ApplicationSecretSettings> getSecretSettings() {
        return secretSettings;
    }

    public void setSecretSettings(List<ApplicationSecretSettings> secretSettings) {
        this.secretSettings = secretSettings;
    }

    public List<ClientSecret> getClientSecrets() {
        return clientSecrets;
    }

    public void setClientSecrets(List<ClientSecret> clientSecrets) {
        this.clientSecrets = clientSecrets;
    }

    public SecretExpirationSettings getSecretExpirationSettings() {
        return secretExpirationSettings;
    }

    public void setSecretExpirationSettings(SecretExpirationSettings secretExpirationSettings) {
        this.secretExpirationSettings = secretExpirationSettings;
    }

    public String getAgentCardUri() {
        return agentCardUri;
    }

    public void setAgentCardUri(String agentCardUri) {
        this.agentCardUri = agentCardUri;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Client client = (Client) o;
        return Objects.equals(id, client.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public Client clone() throws CloneNotSupportedException {
        Client clone = (Client) super.clone();
clone.setAgentCardUri(this.getAgentCardUri());
        clone.setRedirectUris(this.getRedirectUris() != null ? new ArrayList<>(this.getRedirectUris()) : null);
        clone.setAuthorizedGrantTypes(this.getAuthorizedGrantTypes() != null ? new ArrayList<>(this.getAuthorizedGrantTypes()) : null);
        clone.setResponseTypes(this.getResponseTypes() != null ? new ArrayList<>(this.getResponseTypes()) : null);
        clone.setContacts(this.getContacts() != null ? new ArrayList<>(this.getContacts()) : null);
        clone.setDefaultACRvalues(this.getDefaultACRvalues() != null ? new ArrayList<>(this.getDefaultACRvalues()) : null);
        clone.setRequestUris(this.getRequestUris() != null ? new ArrayList<>(this.getRequestUris()) : null);
        clone.setScopeSettings(this.scopeSettings != null ? new ArrayList<>(this.getScopeSettings()) : null);
        clone.setAutoApproveScopes(this.getAutoApproveScopes() != null ? new ArrayList<>(this.getAutoApproveScopes()) : null);
        clone.setIdentityProviders(this.getIdentityProviders() != null ? new TreeSet<>(this.getIdentityProviders()) : null);
        clone.setFactorSettings(this.getFactorSettings());
        clone.setJwks(this.getJwks() != null ? this.getJwks().clone() : null);
        Optional.ofNullable(this.passwordSettings).ifPresent(ps -> clone.setPasswordSettings(new PasswordSettings(ps)));
        clone.setSecretSettings(this.getSecretSettings() != null ? new ArrayList<>(this.getSecretSettings()) : new ArrayList<>());
        clone.setClientSecrets(this.getClientSecrets() != null ? this.getClientSecrets().stream().map(ClientSecret::new).collect(Collectors.toList()) : new ArrayList<>());
        clone.setSecretExpirationSettings(this.getSecretExpirationSettings() != null ? new SecretExpirationSettings(this.getSecretExpirationSettings()) : null);
        return clone;
    }

    public Client asSafeClient() {
        Client safeClient = new Client(this);
        safeClient.setClientSecret(null);
        safeClient.setClientSecrets(List.of());
        return safeClient;
    }
}
