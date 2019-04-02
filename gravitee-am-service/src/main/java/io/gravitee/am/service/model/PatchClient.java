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
package io.gravitee.am.service.model;

import io.gravitee.am.model.Client;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.service.utils.SetterUtils;

import java.util.*;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class PatchClient {

    private Optional<List<String>> redirectUris;
    private Optional<List<String>> authorizedGrantTypes;
    private Optional<List<String>> responseTypes;
    private Optional<String> applicationType;
    private Optional<List<String>> contacts;
    private Optional<String> clientName;
    private Optional<String> logoUri;
    private Optional<String> ClientUri;
    private Optional<String> policyUri;
    private Optional<String> tosUri;
    private Optional<String> jwksUri;
    private Optional<JWKSet> jwks;
    private Optional<String> sectorIdentifierUri;
    private Optional<String> subjectType;
    private Optional<String> idTokenSignedResponseAlg;
    private Optional<String> idTokenEncryptedResponseAlg;
    private Optional<String> idTokenEncryptedResponseEnc;
    private Optional<String> userinfoSignedResponseAlg;
    private Optional<String> userinfoEncryptedResponseAlg;
    private Optional<String> userinfoEncryptedResponseEnc;
    private Optional<String> requestObjectSigningAlg;
    private Optional<String> requestObjectEncryptionAlg;
    private Optional<String> requestObjectEncryptionEnc;
    private Optional<String> tokenEndpointAuthMethod;
    private Optional<String> tokenEndpointAuthSigningAlg;
    private Optional<Integer> defaultMaxAge;
    private Optional<Boolean> requireAuthTime;
    private Optional<List<String>> defaultACRvalues;
    private Optional<String> initiateLoginUri;
    private Optional<List<String>> requestUris;
    private Optional<List<String>> scopes;
    private Optional<String> softwareId; //Should be UUID
    private Optional<String> softwareVersion;
    private Optional<String> softwareStatement; //Should be JWT
    private Optional<String> registrationAccessToken;
    private Optional<String> registrationClientUri;
    private Optional<Date> clientIdIssuedAt;
    private Optional<Date> clientSecretExpiresAt;
    private Optional<List<String>> autoApproveScopes;
    private Optional<Integer> accessTokenValiditySeconds;
    private Optional<Integer> refreshTokenValiditySeconds;
    private Optional<Integer> idTokenValiditySeconds;
    private Optional<Map<String, Object>> idTokenCustomClaims;
    private Optional<Boolean> enabled;
    private Optional<Set<String>> identities;
    private Optional<Set<String>> oauth2Identities;
    private Optional<String> certificate;
    private Optional<Boolean> enhanceScopesWithUserPermissions;
    private Optional<Map<String, Integer>> scopeApprovals;

    public Optional<List<String>> getRedirectUris() {
        return redirectUris;
    }

    public void setRedirectUris(Optional<List<String>> redirectUris) {
        this.redirectUris = redirectUris;
    }

    public Optional<List<String>> getAuthorizedGrantTypes() {
        return authorizedGrantTypes;
    }

    public void setAuthorizedGrantTypes(Optional<List<String>> authorizedGrantTypes) {
        this.authorizedGrantTypes = authorizedGrantTypes;
    }

    public Optional<List<String>> getResponseTypes() {
        return responseTypes;
    }

    public void setResponseTypes(Optional<List<String>> responseTypes) {
        this.responseTypes = responseTypes;
    }

    public Optional<String> getApplicationType() {
        return applicationType;
    }

    public void setApplicationType(Optional<String> applicationType) {
        this.applicationType = applicationType;
    }

    public Optional<List<String>> getContacts() {
        return contacts;
    }

    public void setContacts(Optional<List<String>> contacts) {
        this.contacts = contacts;
    }

    public Optional<String> getClientName() {
        return clientName;
    }

    public void setClientName(Optional<String> clientName) {
        this.clientName = clientName;
    }

    public Optional<String> getLogoUri() {
        return logoUri;
    }

    public void setLogoUri(Optional<String> logoUri) {
        this.logoUri = logoUri;
    }

    public Optional<String> getClientUri() {
        return ClientUri;
    }

    public void setClientUri(Optional<String> clientUri) {
        ClientUri = clientUri;
    }

    public Optional<String> getPolicyUri() {
        return policyUri;
    }

    public void setPolicyUri(Optional<String> policyUri) {
        this.policyUri = policyUri;
    }

    public Optional<String> getTosUri() {
        return tosUri;
    }

    public void setTosUri(Optional<String> tosUri) {
        this.tosUri = tosUri;
    }

    public Optional<String> getJwksUri() {
        return jwksUri;
    }

    public void setJwksUri(Optional<String> jwksUri) {
        this.jwksUri = jwksUri;
    }

    public Optional<JWKSet> getJwks() {
        return jwks;
    }

    public void setJwks(Optional<JWKSet> jwks) {
        this.jwks = jwks;
    }

    public Optional<String> getSectorIdentifierUri() {
        return sectorIdentifierUri;
    }

    public void setSectorIdentifierUri(Optional<String> sectorIdentifierUri) {
        this.sectorIdentifierUri = sectorIdentifierUri;
    }

    public Optional<String> getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(Optional<String> subjectType) {
        this.subjectType = subjectType;
    }

    public Optional<String> getIdTokenSignedResponseAlg() {
        return idTokenSignedResponseAlg;
    }

    public void setIdTokenSignedResponseAlg(Optional<String> idTokenSignedResponseAlg) {
        this.idTokenSignedResponseAlg = idTokenSignedResponseAlg;
    }

    public Optional<String> getIdTokenEncryptedResponseAlg() {
        return idTokenEncryptedResponseAlg;
    }

    public void setIdTokenEncryptedResponseAlg(Optional<String> idTokenEncryptedResponseAlg) {
        this.idTokenEncryptedResponseAlg = idTokenEncryptedResponseAlg;
    }

    public Optional<String> getIdTokenEncryptedResponseEnc() {
        return idTokenEncryptedResponseEnc;
    }

    public void setIdTokenEncryptedResponseEnc(Optional<String> idTokenEncryptedResponseEnc) {
        this.idTokenEncryptedResponseEnc = idTokenEncryptedResponseEnc;
    }

    public Optional<String> getUserinfoSignedResponseAlg() {
        return userinfoSignedResponseAlg;
    }

    public void setUserinfoSignedResponseAlg(Optional<String> userinfoSignedResponseAlg) {
        this.userinfoSignedResponseAlg = userinfoSignedResponseAlg;
    }

    public Optional<String> getUserinfoEncryptedResponseAlg() {
        return userinfoEncryptedResponseAlg;
    }

    public void setUserinfoEncryptedResponseAlg(Optional<String> userinfoEncryptedResponseAlg) {
        this.userinfoEncryptedResponseAlg = userinfoEncryptedResponseAlg;
    }

    public Optional<String> getUserinfoEncryptedResponseEnc() {
        return userinfoEncryptedResponseEnc;
    }

    public void setUserinfoEncryptedResponseEnc(Optional<String> userinfoEncryptedResponseEnc) {
        this.userinfoEncryptedResponseEnc = userinfoEncryptedResponseEnc;
    }

    public Optional<String> getRequestObjectSigningAlg() {
        return requestObjectSigningAlg;
    }

    public void setRequestObjectSigningAlg(Optional<String> requestObjectSigningAlg) {
        this.requestObjectSigningAlg = requestObjectSigningAlg;
    }

    public Optional<String> getRequestObjectEncryptionAlg() {
        return requestObjectEncryptionAlg;
    }

    public void setRequestObjectEncryptionAlg(Optional<String> requestObjectEncryptionAlg) {
        this.requestObjectEncryptionAlg = requestObjectEncryptionAlg;
    }

    public Optional<String> getRequestObjectEncryptionEnc() {
        return requestObjectEncryptionEnc;
    }

    public void setRequestObjectEncryptionEnc(Optional<String> requestObjectEncryptionEnc) {
        this.requestObjectEncryptionEnc = requestObjectEncryptionEnc;
    }

    public Optional<String> getTokenEndpointAuthMethod() {
        return tokenEndpointAuthMethod;
    }

    public void setTokenEndpointAuthMethod(Optional<String> tokenEndpointAuthMethod) {
        this.tokenEndpointAuthMethod = tokenEndpointAuthMethod;
    }

    public Optional<String> getTokenEndpointAuthSigningAlg() {
        return tokenEndpointAuthSigningAlg;
    }

    public void setTokenEndpointAuthSigningAlg(Optional<String> tokenEndpointAuthSigningAlg) {
        this.tokenEndpointAuthSigningAlg = tokenEndpointAuthSigningAlg;
    }

    public Optional<Integer> getDefaultMaxAge() {
        return defaultMaxAge;
    }

    public void setDefaultMaxAge(Optional<Integer> defaultMaxAge) {
        this.defaultMaxAge = defaultMaxAge;
    }

    public Optional<Boolean> getRequireAuthTime() {
        return requireAuthTime;
    }

    public void setRequireAuthTime(Optional<Boolean> requireAuthTime) {
        this.requireAuthTime = requireAuthTime;
    }

    public Optional<List<String>> getDefaultACRvalues() {
        return defaultACRvalues;
    }

    public void setDefaultACRvalues(Optional<List<String>> defaultACRvalues) {
        this.defaultACRvalues = defaultACRvalues;
    }

    public Optional<String> getInitiateLoginUri() {
        return initiateLoginUri;
    }

    public void setInitiateLoginUri(Optional<String> initiateLoginUri) {
        this.initiateLoginUri = initiateLoginUri;
    }

    public Optional<List<String>> getRequestUris() {
        return requestUris;
    }

    public void setRequestUris(Optional<List<String>> requestUris) {
        this.requestUris = requestUris;
    }

    public Optional<List<String>> getScopes() {
        return scopes;
    }

    public void setScopes(Optional<List<String>> scopes) {
        this.scopes = scopes;
    }

    public Optional<String> getSoftwareId() {
        return softwareId;
    }

    public void setSoftwareId(Optional<String> softwareId) {
        this.softwareId = softwareId;
    }

    public Optional<String> getSoftwareVersion() {
        return softwareVersion;
    }

    public void setSoftwareVersion(Optional<String> softwareVersion) {
        this.softwareVersion = softwareVersion;
    }

    public Optional<String> getSoftwareStatement() {
        return softwareStatement;
    }

    public void setSoftwareStatement(Optional<String> softwareStatement) {
        this.softwareStatement = softwareStatement;
    }

    public Optional<String> getRegistrationAccessToken() {
        return registrationAccessToken;
    }

    public void setRegistrationAccessToken(Optional<String> registrationAccessToken) {
        this.registrationAccessToken = registrationAccessToken;
    }

    public Optional<String> getRegistrationClientUri() {
        return registrationClientUri;
    }

    public void setRegistrationClientUri(Optional<String> registrationClientUri) {
        this.registrationClientUri = registrationClientUri;
    }

    public Optional<Date> getClientIdIssuedAt() {
        return clientIdIssuedAt;
    }

    public void setClientIdIssuedAt(Optional<Date> clientIdIssuedAt) {
        this.clientIdIssuedAt = clientIdIssuedAt;
    }

    public Optional<Date> getClientSecretExpiresAt() {
        return clientSecretExpiresAt;
    }

    public void setClientSecretExpiresAt(Optional<Date> clientSecretExpiresAt) {
        this.clientSecretExpiresAt = clientSecretExpiresAt;
    }

    public Optional<List<String>> getAutoApproveScopes() {
        return autoApproveScopes;
    }

    public void setAutoApproveScopes(Optional<List<String>> autoApproveScopes) {
        this.autoApproveScopes = autoApproveScopes;
    }

    public Optional<Integer> getAccessTokenValiditySeconds() {
        return accessTokenValiditySeconds;
    }

    public void setAccessTokenValiditySeconds(Optional<Integer> accessTokenValiditySeconds) {
        this.accessTokenValiditySeconds = accessTokenValiditySeconds;
    }

    public Optional<Integer> getRefreshTokenValiditySeconds() {
        return refreshTokenValiditySeconds;
    }

    public void setRefreshTokenValiditySeconds(Optional<Integer> refreshTokenValiditySeconds) {
        this.refreshTokenValiditySeconds = refreshTokenValiditySeconds;
    }

    public Optional<Integer> getIdTokenValiditySeconds() {
        return idTokenValiditySeconds;
    }

    public void setIdTokenValiditySeconds(Optional<Integer> idTokenValiditySeconds) {
        this.idTokenValiditySeconds = idTokenValiditySeconds;
    }

    public Optional<Map<String, Object>> getIdTokenCustomClaims() {
        return idTokenCustomClaims;
    }

    public void setIdTokenCustomClaims(Optional<Map<String, Object>> idTokenCustomClaims) {
        this.idTokenCustomClaims = idTokenCustomClaims;
    }

    public Optional<Boolean> getEnabled() {
        return enabled;
    }

    public void setEnabled(Optional<Boolean> enabled) {
        this.enabled = enabled;
    }

    public Optional<Set<String>> getIdentities() {
        return identities;
    }

    public void setIdentities(Optional<Set<String>> identities) {
        this.identities = identities;
    }

    public Optional<Set<String>> getOauth2Identities() {
        return oauth2Identities;
    }

    public void setOauth2Identities(Optional<Set<String>> oauth2Identities) {
        this.oauth2Identities = oauth2Identities;
    }

    public Optional<String> getCertificate() {
        return certificate;
    }

    public void setCertificate(Optional<String> certificate) {
        this.certificate = certificate;
    }

    public Optional<Boolean> getEnhanceScopesWithUserPermissions() {
        return enhanceScopesWithUserPermissions;
    }

    public void setEnhanceScopesWithUserPermissions(Optional<Boolean> enhanceScopesWithUserPermissions) {
        this.enhanceScopesWithUserPermissions = enhanceScopesWithUserPermissions;
    }

    public Optional<Map<String, Integer>> getScopeApprovals() {
        return scopeApprovals;
    }

    public void setScopeApprovals(Optional<Map<String, Integer>> scopeApprovals) {
        this.scopeApprovals = scopeApprovals;
    }

    public Client patch(Client _toPatch, boolean forceNull) {
        // create new object for audit purpose (patch json result)
        Client toPatch = new Client(_toPatch);

        SetterUtils.safeSet(toPatch::setRedirectUris, this.getRedirectUris());
        SetterUtils.safeSet(toPatch::setAuthorizedGrantTypes, this.getAuthorizedGrantTypes());
        SetterUtils.safeSet(toPatch::setResponseTypes, this.getResponseTypes());
        SetterUtils.safeSet(toPatch::setApplicationType, this.getApplicationType());
        SetterUtils.safeSet(toPatch::setContacts,this.getContacts());
        SetterUtils.safeSet(toPatch::setClientName, this.getClientName());
        SetterUtils.safeSet(toPatch::setLogoUri,this.getLogoUri());
        SetterUtils.safeSet(toPatch::setClientUri,this.getClientUri());
        SetterUtils.safeSet(toPatch::setPolicyUri,this.getPolicyUri());
        SetterUtils.safeSet(toPatch::setTosUri,this.getTosUri());
        SetterUtils.safeSet(toPatch::setJwksUri,this.getJwksUri());
        SetterUtils.safeSet(toPatch::setJwks,this.getJwks());
        SetterUtils.safeSet(toPatch::setSectorIdentifierUri,this.getSectorIdentifierUri());
        SetterUtils.safeSet(toPatch::setSubjectType,this.getSubjectType());
        SetterUtils.safeSet(toPatch::setIdTokenSignedResponseAlg,this.getIdTokenSignedResponseAlg());
        SetterUtils.safeSet(toPatch::setIdTokenEncryptedResponseAlg,this.getIdTokenSignedResponseAlg());
        SetterUtils.safeSet(toPatch::setIdTokenEncryptedResponseEnc,this.getIdTokenEncryptedResponseEnc());
        SetterUtils.safeSet(toPatch::setUserinfoSignedResponseAlg,this.getUserinfoSignedResponseAlg());
        SetterUtils.safeSet(toPatch::setUserinfoEncryptedResponseAlg,this.getUserinfoEncryptedResponseAlg());
        SetterUtils.safeSet(toPatch::setUserinfoEncryptedResponseEnc,this.getUserinfoEncryptedResponseEnc());
        SetterUtils.safeSet(toPatch::setRequestObjectSigningAlg,this.getRequestObjectSigningAlg());
        SetterUtils.safeSet(toPatch::setRequestObjectEncryptionAlg,this.getRequestObjectEncryptionAlg());
        SetterUtils.safeSet(toPatch::setRequestObjectEncryptionEnc,this.getRequestObjectEncryptionEnc());
        SetterUtils.safeSet(toPatch::setTokenEndpointAuthMethod,this.getTokenEndpointAuthMethod());
        SetterUtils.safeSet(toPatch::setTokenEndpointAuthSigningAlg,this.getTokenEndpointAuthSigningAlg());
        SetterUtils.safeSet(toPatch::setDefaultMaxAge,this.getDefaultMaxAge());
        SetterUtils.safeSet(toPatch::setRequireAuthTime,this.getRequireAuthTime());
        SetterUtils.safeSet(toPatch::setDefaultACRvalues,this.getDefaultACRvalues());
        SetterUtils.safeSet(toPatch::setInitiateLoginUri,this.getInitiateLoginUri());
        SetterUtils.safeSet(toPatch::setRequestUris,this.getRequestUris());
        SetterUtils.safeSet(toPatch::setScopes, this.getScopes());
        SetterUtils.safeSet(toPatch::setSoftwareId,this.getSoftwareId());
        SetterUtils.safeSet(toPatch::setSoftwareVersion,this.getSoftwareVersion());
        SetterUtils.safeSet(toPatch::setSoftwareStatement,this.getSoftwareStatement());
        SetterUtils.safeSet(toPatch::setRegistrationAccessToken,this.getRegistrationAccessToken());
        SetterUtils.safeSet(toPatch::setRegistrationClientUri,this.getRegistrationClientUri());
        SetterUtils.safeSet(toPatch::setClientIdIssuedAt,this.getClientIdIssuedAt());
        SetterUtils.safeSet(toPatch::setClientSecretExpiresAt,this.getClientSecretExpiresAt());
        SetterUtils.safeSet(toPatch::setAutoApproveScopes, this.getAutoApproveScopes());
        SetterUtils.safeSet(toPatch::setAccessTokenValiditySeconds, this.getAccessTokenValiditySeconds(), int.class);
        SetterUtils.safeSet(toPatch::setRefreshTokenValiditySeconds, this.getRefreshTokenValiditySeconds(), int.class);
        SetterUtils.safeSet(toPatch::setIdTokenValiditySeconds, this.getIdTokenValiditySeconds(), int.class);
        SetterUtils.safeSet(toPatch::setIdTokenCustomClaims, this.getIdTokenCustomClaims());
        SetterUtils.safeSet(toPatch::setEnabled, this.getEnabled(), boolean.class);
        SetterUtils.safeSet(toPatch::setIdentities, this.getIdentities());
        SetterUtils.safeSet(toPatch::setOauth2Identities, this.getOauth2Identities());
        // we should be able to unset the certificate
        if (this.getCertificate() == null && forceNull) {
            toPatch.setCertificate(null);
        } else {
            SetterUtils.safeSet(toPatch::setCertificate, this.getCertificate());
        }
        SetterUtils.safeSet(toPatch::setEnhanceScopesWithUserPermissions, this.getEnhanceScopesWithUserPermissions(), boolean.class);
        SetterUtils.safeSet(toPatch::setScopeApprovals, this.getScopeApprovals());

        return toPatch;
    }
}
