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
package io.gravitee.am.repository.mongodb.management.internal.model;

import org.bson.Document;

import java.util.Date;
import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ApplicationOAuthSettingsMongo {

    private String clientId;
    private String clientSecret;
    private String clientType;
    private List<String> redirectUris;
    private List<String> responseTypes;
    private List<String> grantTypes;
    private String applicationType;
    private List<String> contacts;
    private String clientName;
    private String logoUri;
    private String clientUri;
    private String policyUri;
    private String tosUri;
    private String jwksUri;
    private List<JWKMongo> jwks;
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
    private String tokenEndpointAuthMethod;
    private String tokenEndpointAuthSigningAlg;
    private Integer defaultMaxAge;
    private Boolean requireAuthTime = false;
    private List<String> defaultACRvalues;
    private String initiateLoginUri;
    private List<String> requestUris;
    private String softwareId;
    private String softwareVersion;
    private String softwareStatement;
    private String registrationAccessToken;
    private String registrationClientUri;
    private Date clientIdIssuedAt;
    private Date clientSecretExpiresAt;
    private List<String> scopes;
    private Document scopeApprovals;
    private boolean enhanceScopesWithUserPermissions;
    private int accessTokenValiditySeconds;
    private int refreshTokenValiditySeconds;
    private int idTokenValiditySeconds;
    private List<TokenClaimMongo> tokenCustomClaims;
    private String tlsClientAuthSubjectDn;
    private String tlsClientAuthSanDns;
    private String tlsClientAuthSanUri;
    private String tlsClientAuthSanIp;
    private String tlsClientAuthSanEmail;
    private String authorizationSignedResponseAlg;
    private String authorizationEncryptedResponseAlg;
    private String authorizationEncryptedResponseEnc;
    private boolean forcePKCE;

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

    public String getClientType() {
        return clientType;
    }

    public void setClientType(String clientType) {
        this.clientType = clientType;
    }

    public List<String> getRedirectUris() {
        return redirectUris;
    }

    public void setRedirectUris(List<String> redirectUris) {
        this.redirectUris = redirectUris;
    }

    public List<String> getResponseTypes() {
        return responseTypes;
    }

    public void setResponseTypes(List<String> responseTypes) {
        this.responseTypes = responseTypes;
    }

    public List<String> getGrantTypes() {
        return grantTypes;
    }

    public void setGrantTypes(List<String> grantTypes) {
        this.grantTypes = grantTypes;
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

    public List<JWKMongo> getJwks() {
        return jwks;
    }

    public void setJwks(List<JWKMongo> jwks) {
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
        this.clientSecretExpiresAt = clientSecretExpiresAt;
    }

    public List<String> getScopes() {
        return scopes;
    }

    public void setScopes(List<String> scopes) {
        this.scopes = scopes;
    }

    public Document getScopeApprovals() {
        return scopeApprovals;
    }

    public void setScopeApprovals(Document scopeApprovals) {
        this.scopeApprovals = scopeApprovals;
    }

    public boolean isEnhanceScopesWithUserPermissions() {
        return enhanceScopesWithUserPermissions;
    }

    public void setEnhanceScopesWithUserPermissions(boolean enhanceScopesWithUserPermissions) {
        this.enhanceScopesWithUserPermissions = enhanceScopesWithUserPermissions;
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

    public int getIdTokenValiditySeconds() {
        return idTokenValiditySeconds;
    }

    public void setIdTokenValiditySeconds(int idTokenValiditySeconds) {
        this.idTokenValiditySeconds = idTokenValiditySeconds;
    }

    public List<TokenClaimMongo> getTokenCustomClaims() {
        return tokenCustomClaims;
    }

    public void setTokenCustomClaims(List<TokenClaimMongo> tokenCustomClaims) {
        this.tokenCustomClaims = tokenCustomClaims;
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
}
