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
package io.gravitee.am.gateway.handler.oidc.service.clientregistration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.gravitee.am.gateway.handler.oidc.service.jwk.converter.JWKSetDeserializer;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.service.utils.SetterUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static io.gravitee.am.common.oidc.Scope.SCOPE_DELIMITER;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DynamicClientRegistrationRequest {

    /*******************************************************************************
     * Metadata in same order than the openid specification
     * https://openid.net/specs/openid-connect-registration-1_0.html#ClientMetadata
     ********************************************************************************/

    @JsonProperty("redirect_uris")
    private Optional<List<String>> redirectUris;

    @JsonProperty("response_types")
    private Optional<List<String>> responseTypes;

    @JsonProperty("grant_types")
    private Optional<List<String>> grantTypes;

    @JsonProperty("application_type")
    private Optional<String> applicationType;

    @JsonProperty("contacts")
    private Optional<List<String>> contacts;

    @JsonProperty("client_name")
    private Optional<String> clientName;

    @JsonProperty("logo_uri")
    private Optional<String> logoUri;

    @JsonProperty("client_uri")
    private Optional<String> clientUri;

    @JsonProperty("policy_uri")
    private Optional<String> policyUri;

    @JsonProperty("tos_uri")
    private Optional<String> tosUri;

    @JsonProperty("jwks_uri")
    private Optional<String> jwksUri;

    @JsonProperty("jwks")
    @JsonDeserialize(converter = JWKSetDeserializer.class)
    private Optional<JWKSet> jwks;

    @JsonProperty("sector_identifier_uri")
    private Optional<String> sectorIdentifierUri;

    @JsonProperty("subject_type")
    private Optional<String> subjectType;

    @JsonProperty("id_token_signed_response_alg")
    private Optional<String> idTokenSignedResponseAlg;

    @JsonProperty("id_token_encrypted_response_alg")
    private Optional<String> idTokenEncryptedResponseAlg;

    @JsonProperty("id_token_encrypted_response_enc")
    private Optional<String> idTokenEncryptedResponseEnc;

    @JsonProperty("userinfo_signed_response_alg")
    private Optional<String> userinfoSignedResponseAlg;

    @JsonProperty("userinfo_encrypted_response_alg")
    private Optional<String> userinfoEncryptedResponseAlg;

    @JsonProperty("userinfo_encrypted_response_enc")
    private Optional<String> userinfoEncryptedResponseEnc;

    @JsonProperty("request_object_signing_alg")
    private Optional<String> requestObjectSigningAlg;

    @JsonProperty("request_object_encryption_alg")
    private Optional<String> requestObjectEncryptionAlg;

    @JsonProperty("request_object_encryption_enc")
    private Optional<String> requestObjectEncryptionEnc;

    @JsonProperty("token_endpoint_auth_method")
    private Optional<String> tokenEndpointAuthMethod;

    @JsonProperty("token_endpoint_auth_signing_alg")
    private Optional<String> tokenEndpointAuthSigningAlg;

    @JsonProperty("default_max_age")
    private Optional<Integer> defaultMaxAge;

    @JsonProperty("require_auth_time")
    private Optional<Boolean> requireAuthTime;

    @JsonProperty("default_acr_values")
    private Optional<List<String>> defaultACRvalues;

    @JsonProperty("initiate_login_uri")
    private Optional<String> initiateLoginUri;

    @JsonProperty("request_uris")
    private Optional<List<String>> requestUris;

    /*******************************************************************************
     * Oauth2 metadata in addition to RFC specification
     * https://tools.ietf.org/html/rfc7591#section-2
     * https://tools.ietf.org/html/rfc7591#section-3.1.1
     ********************************************************************************/

    //https://tools.ietf.org/html/rfc7591#section-4.1.2 : scope is Optional<String> space delimited
    @JsonProperty("scope")
    private Optional<String> scope;

    @JsonProperty("software_id")
    private Optional<String> softwareId; //Should be UUID

    @JsonProperty("software_version")
    private Optional<String> softwareVersion;

    @JsonProperty("software_statement")
    private Optional<String> softwareStatement; //Should be JWT

    /*******************************************************************************
     * Client certificate authentication metadata in addition to RFC specification
     * https://tools.ietf.org/html/rfc8705#section-2.1.2
     ********************************************************************************/
    @JsonProperty("tls_client_auth_subject_dn")
    private Optional<String> tlsClientAuthSubjectDn;

    @JsonProperty("tls_client_auth_san_dns")
    private Optional<String> tlsClientAuthSanDns;

    @JsonProperty("tls_client_auth_san_uri")
    private Optional<String> tlsClientAuthSanUri;

    @JsonProperty("tls_client_auth_san_ip")
    private Optional<String> tlsClientAuthSanIp;

    @JsonProperty("tls_client_auth_san_email")
    private Optional<String> tlsClientAuthSanEmail;

    // GETTER AND SETTERS //

    public Optional<List<String>> getRedirectUris() {
        return redirectUris;
    }

    public void setRedirectUris(Optional<List<String>> redirectUris) {
        this.redirectUris = redirectUris;
    }

    public Optional<List<String>> getResponseTypes() {
        return responseTypes;
    }

    public void setResponseTypes(Optional<List<String>> responseTypes) {
        this.responseTypes = responseTypes;
    }

    public Optional<List<String>> getGrantTypes() {
        return grantTypes;
    }

    public void setGrantTypes(Optional<List<String>> grantTypes) {
        this.grantTypes = grantTypes;
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
        return clientUri;
    }

    public void setClientUri(Optional<String> clientUri) {
        this.clientUri = clientUri;
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

    public Optional<List<String>> getScope() {
        if (this.scope == null) return null; //Keep null to avoid patch...
        if (!this.scope.isPresent() || this.scope.get().trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Arrays.asList(scope.get().split(SCOPE_DELIMITER)));
    }

    public void setScope(Optional<String> scope) {
        this.scope = scope;
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

    public Optional<String> getTlsClientAuthSubjectDn() {
        return tlsClientAuthSubjectDn;
    }

    public void setTlsClientAuthSubjectDn(Optional<String> tlsClientAuthSubjectDn) {
        this.tlsClientAuthSubjectDn = tlsClientAuthSubjectDn;
    }

    public Optional<String> getTlsClientAuthSanDns() {
        return tlsClientAuthSanDns;
    }

    public void setTlsClientAuthSanDns(Optional<String> tlsClientAuthSanDns) {
        this.tlsClientAuthSanDns = tlsClientAuthSanDns;
    }

    public Optional<String> getTlsClientAuthSanUri() {
        return tlsClientAuthSanUri;
    }

    public void setTlsClientAuthSanUri(Optional<String> tlsClientAuthSanUri) {
        this.tlsClientAuthSanUri = tlsClientAuthSanUri;
    }

    public Optional<String> getTlsClientAuthSanIp() {
        return tlsClientAuthSanIp;
    }

    public void setTlsClientAuthSanIp(Optional<String> tlsClientAuthSanIp) {
        this.tlsClientAuthSanIp = tlsClientAuthSanIp;
    }

    public Optional<String> getTlsClientAuthSanEmail() {
        return tlsClientAuthSanEmail;
    }

    public void setTlsClientAuthSanEmail(Optional<String> tlsClientAuthSanEmail) {
        this.tlsClientAuthSanEmail = tlsClientAuthSanEmail;
    }

    @Override
    public String toString() {
        return "ClientPayload{clientName='" + (clientName!=null?clientName.orElse(""):"") + "\'}";
    }

    public Client patch(Client client) {
        /* set openid request metadata */
        SetterUtils.safeSet(client::setRedirectUris, this.getRedirectUris());
        SetterUtils.safeSet(client::setResponseTypes, this.getResponseTypes());
        SetterUtils.safeSet(client::setAuthorizedGrantTypes, this.getGrantTypes());
        SetterUtils.safeSet(client::setApplicationType, this.getApplicationType());
        SetterUtils.safeSet(client::setContacts, this.getContacts());
        SetterUtils.safeSet(client::setClientName, this.getClientName());
        SetterUtils.safeSet(client::setLogoUri, this.getLogoUri());
        SetterUtils.safeSet(client::setClientUri, this.getClientUri());
        SetterUtils.safeSet(client::setPolicyUri, this.getPolicyUri());
        SetterUtils.safeSet(client::setTosUri, this.getTosUri());

        SetterUtils.safeSet(client::setJwksUri, this.getJwksUri());
        SetterUtils.safeSet(client::setJwks, this.getJwks());
        SetterUtils.safeSet(client::setSectorIdentifierUri, this.getSectorIdentifierUri());
        SetterUtils.safeSet(client::setSubjectType, this.getSubjectType());
        SetterUtils.safeSet(client::setIdTokenSignedResponseAlg, this.getIdTokenSignedResponseAlg());
        SetterUtils.safeSet(client::setIdTokenEncryptedResponseAlg, this.getIdTokenEncryptedResponseAlg());
        SetterUtils.safeSet(client::setIdTokenEncryptedResponseEnc, this.getIdTokenEncryptedResponseEnc());
        SetterUtils.safeSet(client::setUserinfoSignedResponseAlg, this.getUserinfoSignedResponseAlg());
        SetterUtils.safeSet(client::setUserinfoEncryptedResponseAlg, this.getUserinfoEncryptedResponseAlg());
        SetterUtils.safeSet(client::setUserinfoEncryptedResponseEnc, this.getUserinfoEncryptedResponseEnc());
        SetterUtils.safeSet(client::setRequestObjectSigningAlg, this.getRequestObjectSigningAlg());
        SetterUtils.safeSet(client::setRequestObjectEncryptionAlg, this.getRequestObjectEncryptionAlg());
        SetterUtils.safeSet(client::setRequestObjectEncryptionEnc, this.getRequestObjectEncryptionEnc());
        SetterUtils.safeSet(client::setTokenEndpointAuthMethod, this.getTokenEndpointAuthMethod());
        SetterUtils.safeSet(client::setTokenEndpointAuthSigningAlg, this.getTokenEndpointAuthSigningAlg());
        SetterUtils.safeSet(client::setDefaultMaxAge, this.getDefaultMaxAge());
        SetterUtils.safeSet(client::setRequireAuthTime, this.getRequireAuthTime(), boolean.class);
        SetterUtils.safeSet(client::setDefaultACRvalues, this.getDefaultACRvalues());
        SetterUtils.safeSet(client::setInitiateLoginUri, this.getInitiateLoginUri());
        SetterUtils.safeSet(client::setRequestUris, this.getRequestUris());

        /* set oauth2 request metadata */
        SetterUtils.safeSet(client::setScopes, this.getScope());
        SetterUtils.safeSet(client::setSoftwareId, this.getSoftwareId());
        SetterUtils.safeSet(client::setSoftwareVersion, this.getSoftwareVersion());
        SetterUtils.safeSet(client::setSoftwareStatement, this.getSoftwareStatement());

        /* set client certificate-based authentication */
        SetterUtils.safeSet(client::setTlsClientAuthSubjectDn, this.getTlsClientAuthSubjectDn());
        SetterUtils.safeSet(client::setTlsClientAuthSanDns, this.getTlsClientAuthSanDns());
        SetterUtils.safeSet(client::setTlsClientAuthSanEmail, this.getTlsClientAuthSanEmail());
        SetterUtils.safeSet(client::setTlsClientAuthSanIp, this.getTlsClientAuthSanIp());
        SetterUtils.safeSet(client::setTlsClientAuthSanUri, this.getTlsClientAuthSanUri());

        return client;
    }

    public Client update(Client client) {
        /* set openid request metadata */
        SetterUtils.set(client::setRedirectUris, this.getRedirectUris());
        SetterUtils.set(client::setResponseTypes, this.getResponseTypes());
        SetterUtils.set(client::setAuthorizedGrantTypes, this.getGrantTypes());
        SetterUtils.set(client::setApplicationType, this.getApplicationType());
        SetterUtils.set(client::setContacts, this.getContacts());
        SetterUtils.set(client::setClientName, this.getClientName());
        SetterUtils.set(client::setLogoUri, this.getLogoUri());
        SetterUtils.set(client::setClientUri, this.getClientUri());
        SetterUtils.set(client::setPolicyUri, this.getPolicyUri());
        SetterUtils.set(client::setTosUri, this.getTosUri());

        SetterUtils.set(client::setJwksUri, this.getJwksUri());
        SetterUtils.set(client::setJwks, this.getJwks());
        SetterUtils.set(client::setSectorIdentifierUri, this.getSectorIdentifierUri());
        SetterUtils.set(client::setSubjectType, this.getSubjectType());
        SetterUtils.set(client::setIdTokenSignedResponseAlg, this.getIdTokenSignedResponseAlg());
        SetterUtils.set(client::setIdTokenEncryptedResponseAlg, this.getIdTokenEncryptedResponseAlg());
        SetterUtils.set(client::setIdTokenEncryptedResponseEnc, this.getIdTokenEncryptedResponseEnc());
        SetterUtils.set(client::setUserinfoSignedResponseAlg, this.getUserinfoSignedResponseAlg());
        SetterUtils.set(client::setUserinfoEncryptedResponseAlg, this.getUserinfoEncryptedResponseAlg());
        SetterUtils.set(client::setUserinfoEncryptedResponseEnc, this.getUserinfoEncryptedResponseEnc());
        SetterUtils.set(client::setRequestObjectSigningAlg, this.getRequestObjectSigningAlg());
        SetterUtils.set(client::setRequestObjectEncryptionAlg, this.getRequestObjectEncryptionAlg());
        SetterUtils.set(client::setRequestObjectEncryptionEnc, this.getRequestObjectEncryptionEnc());
        SetterUtils.set(client::setTokenEndpointAuthMethod, this.getTokenEndpointAuthMethod());
        SetterUtils.set(client::setTokenEndpointAuthSigningAlg, this.getTokenEndpointAuthSigningAlg());
        SetterUtils.set(client::setDefaultMaxAge, this.getDefaultMaxAge());
        SetterUtils.set(client::setRequireAuthTime, this.getRequireAuthTime());
        SetterUtils.set(client::setDefaultACRvalues, this.getDefaultACRvalues());
        SetterUtils.set(client::setInitiateLoginUri, this.getInitiateLoginUri());
        SetterUtils.set(client::setRequestUris, this.getRequestUris());

        /* set oauth2 request metadata */
        SetterUtils.set(client::setScopes, this.getScope());
        SetterUtils.set(client::setSoftwareId, this.getSoftwareId());
        SetterUtils.set(client::setSoftwareVersion, this.getSoftwareVersion());
        SetterUtils.set(client::setSoftwareStatement, this.getSoftwareStatement());

        /* set client certificate-based authentication */
        SetterUtils.safeSet(client::setTlsClientAuthSubjectDn, this.getTlsClientAuthSubjectDn());
        SetterUtils.safeSet(client::setTlsClientAuthSanDns, this.getTlsClientAuthSanDns());
        SetterUtils.safeSet(client::setTlsClientAuthSanEmail, this.getTlsClientAuthSanEmail());
        SetterUtils.safeSet(client::setTlsClientAuthSanIp, this.getTlsClientAuthSanIp());
        SetterUtils.safeSet(client::setTlsClientAuthSanUri, this.getTlsClientAuthSanUri());

        return client;
    }
}

