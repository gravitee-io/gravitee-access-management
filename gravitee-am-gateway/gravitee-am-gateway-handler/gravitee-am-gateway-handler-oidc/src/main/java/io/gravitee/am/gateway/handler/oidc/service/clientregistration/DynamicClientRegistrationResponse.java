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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.gravitee.am.gateway.handler.oidc.model.jwk.JWKSet;
import io.gravitee.am.gateway.handler.oidc.model.jwk.converter.JWKConverter;
import io.gravitee.am.model.oidc.Client;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static io.gravitee.am.common.oidc.Scope.SCOPE_DELIMITER;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DynamicClientRegistrationResponse {

    //Force to build response from a Client.
    private DynamicClientRegistrationResponse() {}

    /*******************************************************************************
     * Metadata in same order than the openid specification
     * https://openid.net/specs/openid-connect-registration-1_0.html#ClientMetadata
     ********************************************************************************/

    @JsonProperty("redirect_uris")
    private List<String> redirectUris;

    @JsonProperty("response_types")
    private List<String> responseTypes;

    @JsonProperty("grant_types")
    private List<String> grantTypes;

    @JsonProperty("application_type")
    private String applicationType;

    @JsonProperty("contacts")
    private List<String> contacts;

    @JsonProperty("client_name")
    private String clientName;

    @JsonProperty("logo_uri")
    private String logoUri;

    @JsonProperty("client_uri")
    private String clientUri;

    @JsonProperty("policy_uri")
    private String policyUri;

    @JsonProperty("tos_uri")
    private String tosUri;

    @JsonProperty("jwks_uri")
    private String jwksUri;

    @JsonProperty("jwks")
    private JWKSet jwks;

    @JsonProperty("sector_identifier_uri")
    private String sectorIdentifierUri;

    @JsonProperty("subject_type")
    private String subjectType;

    @JsonProperty("id_token_signed_response_alg")
    private String idTokenSignedResponseAlg;

    @JsonProperty("id_token_encrypted_response_alg")
    private String idTokenEncryptedResponseAlg;

    @JsonProperty("id_token_encrypted_response_enc")
    private String idTokenEncryptedResponseEnc;

    @JsonProperty("userinfo_signed_response_alg")
    private String userinfoSignedResponseAlg;

    @JsonProperty("userinfo_encrypted_response_alg")
    private String userinfoEncryptedResponseAlg;

    @JsonProperty("userinfo_encrypted_response_enc")
    private String userinfoEncryptedResponseEnc;

    @JsonProperty("request_object_signing_alg")
    private String requestObjectSigningAlg;

    @JsonProperty("request_object_encryption_alg")
    private String requestObjectEncryptionAlg;

    @JsonProperty("request_object_encryption_enc")
    private String requestObjectEncryptionEnc;

    @JsonProperty("token_endpoint_auth_method")
    private String tokenEndpointAuthMethod;

    @JsonProperty("token_endpoint_auth_signing_alg")
    private String tokenEndpointAuthSigningAlg;

    @JsonProperty("default_max_age")
    private Integer defaultMaxAge;

    @JsonProperty("require_auth_time")
    private Boolean requireAuthTime;

    @JsonProperty("default_acr_values")
    private List<String> defaultACRvalues;

    @JsonProperty("initiate_login_uri")
    private String initiateLoginUri;

    @JsonProperty("request_uris")
    private List<String> requestUris;


    /*******************************************************************************
     * Oauth2 metadata in addition to RFC specification
     * https://tools.ietf.org/html/rfc7591#section-2
     * https://tools.ietf.org/html/rfc7591#section-3.1.1
     ********************************************************************************/

    //https://tools.ietf.org/html/rfc7591#section-4.1.2 : scope is string space delimited
    @JsonProperty("scope")
    private String scope;

    @JsonProperty("software_id")
    private String softwareId; //Should be UUID

    @JsonProperty("software_version")
    private String softwareVersion;

    @JsonProperty("software_statement")
    private String softwareStatement; //Should be JWT


    /*******************************************************************************
     * Additional metadata for the response
     * https://openid.net/specs/openid-connect-registration-1_0.html#RegistrationResponse
     ********************************************************************************/

    @JsonProperty("client_id")
    private String clientId;

    @JsonProperty("client_secret")
    private String clientSecret;

    @JsonProperty("registration_access_token")
    private String registrationAccessToken;

    @JsonProperty("registration_client_uri")
    private String registrationClientUri;

    @JsonProperty("client_id_issued_at")
    private Date clientIdIssuedAt;

    @JsonProperty("client_secret_expires_at")
    private long clientSecretExpiresAt;

    /*******************************************************************************
     * Gravitee.io additional metadata
     ********************************************************************************/

    @JsonProperty("domain")
    private String domain;

    @JsonProperty("id")
    private String id;

    @JsonProperty("enabled")
    private boolean enabled;

    @JsonProperty("created_at")
    private Date createdAt;

    @JsonProperty("updated_at")
    private Date updatedAt;

    /*******************************************************************************
     * Metadata in same order than the openid JARM specification
     * https://openid.net//specs/openid-financial-api-jarm.html#client-metadata
     ********************************************************************************/

    @JsonProperty("authorization_signed_response_alg")
    private String authorizationSignedResponseAlg;

    @JsonProperty("authorization_encrypted_response_alg")
    private String authorizationEncryptedResponseAlg;

    @JsonProperty("authorization_encrypted_response_enc")
    private String authorizationEncryptedResponseEnc;

    /*******************************************************************************
     * Additional metadata
     ********************************************************************************/

    // GETTER AND SETTERS //

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

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
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

    public long getClientSecretExpiresAt() {
        return clientSecretExpiresAt;
    }

    /*
     * Time at which the client_secret will expire or 0 if it will not expire.
     * Its value is representing the number of seconds from 1970-01-01T0:0:0Z as measured in UTC until the date/time.
     * https://openid.net/specs/openid-connect-registration-1_0.html#RegistrationResponse
     */
    public void setClientSecretExpiresAt(Date clientSecretExpiresAt) {
        this.clientSecretExpiresAt=clientSecretExpiresAt!=null?clientSecretExpiresAt.getTime():0;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public static DynamicClientRegistrationResponse fromClient(Client client) {
        DynamicClientRegistrationResponse response = new DynamicClientRegistrationResponse();

        response.setRedirectUris(client.getRedirectUris());
        response.setResponseTypes(client.getResponseTypes());
        response.setGrantTypes(client.getAuthorizedGrantTypes());
        response.setApplicationType(client.getApplicationType());
        response.setContacts(client.getContacts());
        response.setClientName(client.getClientName());
        response.setLogoUri(client.getLogoUri());
        response.setClientUri(client.getClientUri());
        response.setPolicyUri(client.getPolicyUri());
        response.setTosUri(client.getTosUri());
        response.setJwksUri(client.getJwksUri());
        response.setJwks(JWKConverter.convert(client.getJwks()));
        response.setSectorIdentifierUri(client.getSectorIdentifierUri());
        response.setSubjectType(client.getSubjectType());
        response.setIdTokenSignedResponseAlg(client.getIdTokenSignedResponseAlg());
        response.setIdTokenEncryptedResponseAlg(client.getIdTokenEncryptedResponseAlg());
        response.setIdTokenEncryptedResponseEnc(client.getIdTokenEncryptedResponseEnc());
        response.setUserinfoSignedResponseAlg(client.getUserinfoSignedResponseAlg());
        response.setUserinfoEncryptedResponseAlg(client.getUserinfoEncryptedResponseAlg());
        response.setUserinfoEncryptedResponseEnc(client.getUserinfoEncryptedResponseEnc());
        response.setRequestObjectSigningAlg(client.getRequestObjectSigningAlg());
        response.setRequestObjectEncryptionAlg(client.getRequestObjectEncryptionAlg());
        response.setRequestObjectEncryptionEnc(client.getRequestObjectEncryptionEnc());
        response.setTokenEndpointAuthMethod(client.getTokenEndpointAuthMethod());
        response.setTokenEndpointAuthSigningAlg(client.getTokenEndpointAuthSigningAlg());
        response.setDefaultMaxAge(client.getDefaultMaxAge());
        response.setRequireAuthTime(client.getRequireAuthTime());
        response.setDefaultACRvalues(client.getDefaultACRvalues());
        response.setInitiateLoginUri(client.getInitiateLoginUri());
        response.setRequestUris(client.getRequestUris());
        response.setScope(client.getScopes()!=null?String.join(SCOPE_DELIMITER,client.getScopes()):null);
        response.setSoftwareId(client.getSoftwareId());
        response.setSoftwareVersion(client.getSoftwareVersion());
        response.setSoftwareStatement(client.getSoftwareStatement());
        response.setClientId(client.getClientId());
        response.setClientSecret(client.getClientSecret());
        response.setRegistrationAccessToken(client.getRegistrationAccessToken());
        response.setRegistrationClientUri(client.getRegistrationClientUri());
        response.setClientIdIssuedAt(client.getClientIdIssuedAt());
        response.setClientSecretExpiresAt(client.getClientSecretExpiresAt());
        response.setDomain(client.getDomain());
        response.setId(client.getId());
        response.setEnabled(client.isEnabled());
        response.setCreatedAt(client.getCreatedAt());
        response.setUpdatedAt(client.getUpdatedAt());
        response.setAuthorizationSignedResponseAlg(client.getAuthorizationSignedResponseAlg());
        response.setAuthorizationEncryptedResponseAlg(client.getAuthorizationEncryptedResponseAlg());
        response.setAuthorizationEncryptedResponseEnc(client.getAuthorizationEncryptedResponseEnc());

        return response;
    }
}
