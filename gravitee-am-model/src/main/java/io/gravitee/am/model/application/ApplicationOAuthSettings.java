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
package io.gravitee.am.model.application;

import io.gravitee.am.model.TokenClaim;
import io.gravitee.am.model.oidc.JWKSet;

import java.util.*;

/**
 * See <a href="https://openid.net/specs/openid-connect-registration-1_0.html#ClientMetadata">2. Client Metadata</a>
 * See <a href="https://tools.ietf.org/html/rfc7591">OAuth 2.0 Dynamic Client Registration Protocol</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ApplicationOAuthSettings {

    private final static int DEFAULT_ACCESS_TOKEN_VALIDITY_SECONDS = 7200;
    private final static int DEFAULT_REFRESH_TOKEN_VALIDITY_SECONDS = 14400;
    private final static int DEFAULT_ID_TOKEN_VALIDITY_SECONDS = 14400;

    /**
     * The client identifier
     */
    private String clientId;
    /**
     * The client secret
     */
    private String clientSecret;
    /**
     * The client type (OAuth 2.0 perspective public or confidential)
     */
    private String clientType;
    /**
     * Array of Redirection URI values used by the Client.
     * One of these registered Redirection URI values MUST exactly match the redirect_uri parameter value used in each Authorization Requests
     */
    private List<String> redirectUris;
    /**
     * JSON array containing a list of the OAuth 2.0 response_type values that the Client is declaring that it will restrict itself to using.
     * If omitted, the default is that the Client will use only the code Response Type.
     */
    private List<String> responseTypes;
    /**
     * JSON array containing a list of the OAuth 2.0 Grant Types that the Client is declaring that it will restrict itself to using.
     * If omitted, the default is that the Client will use only the authorization_code Grant Type.
     */
    private List<String> grantTypes;
    /**
     * Kind of the application. The default, if omitted, is web. The defined values are native or web.
     */
    private String applicationType;
    /**
     * Array of e-mail addresses of people responsible for this Client
     */
    private List<String> contacts;
    /**
     * Name of the Client to be presented to the End-User.
     */
    private String clientName;
    /**
     * URL that references a logo for the Client application. If present, the server SHOULD display this image to the End-User during approval.
     */
    private String logoUri;
    /**
     * URL of the home page of the Client. The value of this field MUST point to a valid Web page.
     */
    private String clientUri;
    /**
     * URL that the Relying Party Client provides to the End-User to read about the how the profile data will be used.
     */
    private String policyUri;
    /**
     * URL that the Relying Party Client provides to the End-User to read about the Relying Party's terms of service.
     */
    private String tosUri;
    /**
     * URL for the Client's JSON Web Key Set [JWK] document.
     * If the Client signs requests to the Server, it contains the signing key(s) the Server uses to validate signatures from the Client.
     */
    private String jwksUri;
    /**
     * Client's JSON Web Key Set [JWK] document, passed by value.
     * The semantics of the jwks parameter are the same as the jwks_uri parameter, other than that the JWK Set is passed by value, rather than by reference.
     */
    private JWKSet jwks;
    /**
     * URL using the https scheme to be used in calculating Pseudonymous Identifiers by the OP.
     */
    private String sectorIdentifierUri;
    /**
     * subject_type requested for responses to this Client
     */
    private String subjectType;
    /**
     * JWS alg algorithm [JWA] REQUIRED for signing the ID Token issued to this Client.
     */
    private String idTokenSignedResponseAlg;
    /**
     * JWE alg algorithm [JWA] REQUIRED for encrypting the ID Token issued to this Client.
     */
    private String idTokenEncryptedResponseAlg;
    /**
     * JWE enc algorithm [JWA] REQUIRED for encrypting the ID Token issued to this Client.
     */
    private String idTokenEncryptedResponseEnc;
    /**
     * JWS alg algorithm [JWA] REQUIRED for signing UserInfo Responses.
     */
    private String userinfoSignedResponseAlg;
    /**
     * JWE [JWE] alg algorithm [JWA] REQUIRED for encrypting UserInfo Responses.
     */
    private String userinfoEncryptedResponseAlg;
    /**
     * JWE enc algorithm [JWA] REQUIRED for encrypting UserInfo Responses.
     */
    private String userinfoEncryptedResponseEnc;
    /**
     * JWS [JWS] alg algorithm [JWA] that MUST be used for signing Request Objects sent to the OP.
     */
    private String requestObjectSigningAlg;
    /**
     * JWE [JWE] alg algorithm [JWA] the RP is declaring that it may use for encrypting Request Objects sent to the OP.
     */
    private String requestObjectEncryptionAlg;
    /**
     * JWE enc algorithm [JWA] the RP is declaring that it may use for encrypting Request Objects sent to the OP.
     */
    private String requestObjectEncryptionEnc;
    /**
     * Requested Client Authentication method for the Token Endpoint.
     */
    private String tokenEndpointAuthMethod;
    /**
     * [JWS] alg algorithm [JWA] that MUST be used for signing the JWT [JWT] used to authenticate the Client at the Token Endpoint for the private_key_jwt and client_secret_jwt authentication methods.
     */
    private String tokenEndpointAuthSigningAlg;
    /**
     * Default Maximum Authentication Age.
     */
    private Integer defaultMaxAge;
    /**
     * Boolean value specifying whether the auth_time Claim in the ID Token is REQUIRED.
     */
    private Boolean requireAuthTime = false;
    /**
     * Default requested Authentication Context Class Reference values.
     */
    private List<String> defaultACRvalues;
    /**
     * URI using the https scheme that a third party can use to initiate a login by the RP,
     */
    private String initiateLoginUri;
    /**
     * Array of request_uri values that are pre-registered by the RP for use at the OP.
     */
    private List<String> requestUris;
    /**
     * A unique identifier string (e.g., a Universally Unique Identifier (UUID)) assigned by the client developer or software publisher
     * used by registration endpoints to identify the client software to be dynamically registered.
     */
    private String softwareId;
    /**
     * A version identifier string for the client software identified by "software_id".
     */
    private String softwareVersion;
    /**
     * A software statement containing client metadata values about the client software as claims.
     * This is a string value containing the entire signed JWT.
     */
    private String softwareStatement;

    private String registrationAccessToken;

    private String registrationClientUri;
    /**
     * Time at which the client identifier was issued.
     * The time is represented as the number of seconds from 1970-01-01T00:00:00Z as measured in UTC until the date/time of issuance.
     */
    private Date clientIdIssuedAt;
    /**
     * REQUIRED if "client_secret" is issued.  Time at which the client secret will expire or 0 if it will not expire.
     */
    private Date clientSecretExpiresAt;
    /**
     * String containing a space-separated list of scope values (as described in Section 3.3 of OAuth 2.0 [RFC6749]) that the client can use when requesting access tokens.
     */
    private List<String> scopes;
    /**
     * Scope approval duration times
     */
    private Map<String, Integer> scopeApprovals;
    /**
     * Enhance scopes with user roles' permissions
     */
    private boolean enhanceScopesWithUserPermissions;
    /**
     * Access Token validity in seconds
     */
    private int accessTokenValiditySeconds = DEFAULT_ACCESS_TOKEN_VALIDITY_SECONDS;
    /**
     * Refresh Token validity in seconds
     */
    private int refreshTokenValiditySeconds = DEFAULT_REFRESH_TOKEN_VALIDITY_SECONDS;
    /**
     * ID Token validity in seconds
     */
    private int idTokenValiditySeconds = DEFAULT_ID_TOKEN_VALIDITY_SECONDS;
    /**
     * Token claims mapping settings
     */
    private List<TokenClaim> tokenCustomClaims;

    private String tlsClientAuthSubjectDn;

    private String tlsClientAuthSanDns;

    private String tlsClientAuthSanUri;

    private String tlsClientAuthSanIp;

    private String tlsClientAuthSanEmail;

    /**
     * JWS alg algorithm [JWA] REQUIRED for signing Authorization Responses.
     */
    private String authorizationSignedResponseAlg;

    /**
     * JWE [JWE] alg algorithm [JWA] REQUIRED for encrypting Authorization Responses.
     */
    private String authorizationEncryptedResponseAlg;

    /**
     * JWE enc algorithm [JWA] REQUIRED for encrypting Authorization Responses.
     */
    private String authorizationEncryptedResponseEnc;

    /**
     * Ensure usage of the Authorization Code Flow with Proof Key for Code Exchange (PKCE)
     * especially for SPA and Native apps
     */
    private boolean forcePKCE;

    public ApplicationOAuthSettings() {
    }

    public ApplicationOAuthSettings(ApplicationOAuthSettings other) {
        this.clientId = other.clientId;
        this.clientSecret = other.clientSecret;
        this.clientType = other.clientType;
        this.redirectUris = other.redirectUris != null ? new ArrayList<>(other.redirectUris) : null;
        this.responseTypes = other.responseTypes != null ? new ArrayList<>(other.responseTypes) : null;
        this.grantTypes = other.grantTypes != null ? new ArrayList<>(other.grantTypes) : null;
        this.applicationType = other.applicationType;
        this.contacts = other.contacts != null ? new ArrayList<>(other.contacts) : null;
        this.clientName = other.clientName;
        this.logoUri = other.logoUri;
        this.clientUri = other.clientUri;
        this.policyUri = other.policyUri;
        this.tosUri = other.tosUri;
        this.jwksUri = other.jwksUri;
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
        this.defaultACRvalues = other.defaultACRvalues != null ? new ArrayList<>(other.defaultACRvalues): null;
        this.initiateLoginUri = other.initiateLoginUri;
        this.requestUris = other.requestUris != null ? new ArrayList<>(other.requestUris) : null;
        this.softwareId = other.softwareId;
        this.softwareVersion = other.softwareVersion;
        this.softwareStatement = other.softwareStatement;
        this.registrationAccessToken = other.registrationAccessToken;
        this.registrationClientUri = other.registrationClientUri;
        this.clientIdIssuedAt = other.clientIdIssuedAt;
        this.clientSecretExpiresAt = other.clientSecretExpiresAt;
        this.scopes = other.scopes != null ? new ArrayList<>(other.scopes) : null;
        this.scopeApprovals = other.scopeApprovals != null ? new HashMap<>(other.scopeApprovals): null;
        this.enhanceScopesWithUserPermissions = other.enhanceScopesWithUserPermissions;
        this.accessTokenValiditySeconds = other.accessTokenValiditySeconds;
        this.refreshTokenValiditySeconds = other.refreshTokenValiditySeconds;
        this.idTokenValiditySeconds = other.idTokenValiditySeconds;
        this.tokenCustomClaims = other.tokenCustomClaims != null ? new ArrayList<>(other.tokenCustomClaims): null;
        this.tlsClientAuthSubjectDn = other.tlsClientAuthSubjectDn;
        this.tlsClientAuthSanDns = other.tlsClientAuthSanDns;
        this.tlsClientAuthSanEmail = other.tlsClientAuthSanEmail;
        this.tlsClientAuthSanIp = other.tlsClientAuthSanIp;
        this.tlsClientAuthSanUri = other.tlsClientAuthSanUri;
        this.authorizationSignedResponseAlg = other.authorizationSignedResponseAlg;
        this.authorizationEncryptedResponseAlg = other.authorizationEncryptedResponseAlg;
        this.authorizationEncryptedResponseEnc = other.authorizationEncryptedResponseEnc;
        this.forcePKCE = other.forcePKCE;
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
        this.clientSecretExpiresAt = clientSecretExpiresAt;
    }

    public List<String> getScopes() {
        return scopes;
    }

    public void setScopes(List<String> scopes) {
        this.scopes = scopes;
    }

    public Map<String, Integer> getScopeApprovals() {
        return scopeApprovals;
    }

    public void setScopeApprovals(Map<String, Integer> scopeApprovals) {
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

    public List<TokenClaim> getTokenCustomClaims() {
        return tokenCustomClaims;
    }

    public void setTokenCustomClaims(List<TokenClaim> tokenCustomClaims) {
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
