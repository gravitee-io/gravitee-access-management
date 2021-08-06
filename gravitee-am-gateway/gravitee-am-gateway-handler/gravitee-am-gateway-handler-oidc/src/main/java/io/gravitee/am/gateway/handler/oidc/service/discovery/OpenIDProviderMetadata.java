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
package io.gravitee.am.gateway.handler.oidc.service.discovery;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 *
 * See <a href="https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata"></a>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenIDProviderMetadata {

    @JsonProperty("issuer")
    private String issuer;

    @JsonProperty("authorization_endpoint")
    private String authorizationEndpoint;

    @JsonProperty("token_endpoint")
    private String tokenEndpoint;

    @JsonProperty("userinfo_endpoint")
    private String userinfoEndpoint;

    @JsonProperty("jwks_uri")
    private String jwksUri;

    @JsonProperty("end_session_endpoint")
    private String endSessionEndpoint;

    @JsonProperty("revocation_endpoint")
    private String revocationEndpoint;

    @JsonProperty("introspection_endpoint")
    private String introspectionEndpoint;

    @JsonProperty("registration_endpoint")
    private String registrationEndpoint;

    @JsonProperty("registration_renew_secret_endpoint")
    private String registrationRenewSecretEndpoint;

    @JsonProperty("registration_templates_endpoint")
    private String registrationTemplatesEndpoint;

    @JsonProperty("scopes_supported")
    private List<String> scopesSupported;

    @JsonProperty("response_types_supported")
    private List<String> responseTypesSupported;

    @JsonProperty("response_modes_supported")
    private List<String> responseModesSupported;

    @JsonProperty("grant_types_supported")
    private List<String> grantTypesSupported;

    @JsonProperty("acr_values_supported")
    private List<String> acrValuesSupported;

    @JsonProperty("subject_types_supported")
    private List<String> subjectTypesSupported;

    @JsonProperty("id_token_signing_alg_values_supported")
    private List<String> idTokenSigningAlgValuesSupported;

    @JsonProperty("id_token_encryption_alg_values_supported")
    private List<String> idTokenEncryptionAlgValuesSupported;

    @JsonProperty("id_token_encryption_enc_values_supported")
    private List<String> idTokenEncryptionEncValuesSupported;

    @JsonProperty("userinfo_signing_alg_values_supported")
    private List<String> userinfoSigningAlgValuesSupported;

    @JsonProperty("userinfo_encryption_alg_values_supported")
    private List<String> userinfoEncryptionAlgValuesSupported;

    @JsonProperty("userinfo_encryption_enc_values_supported")
    private List<String> userinfoEncryptionEncValuesSupported;

    @JsonProperty("request_object_signing_alg_values_supported")
    private List<String> requestObjectSigningAlgValuesSupported;

    @JsonProperty("request_object_encryption_alg_values_supported")
    private List<String> requestObjectEncryptionAlgValuesSupported;

    @JsonProperty("request_object_encryption_enc_values_supported")
    private List<String> requestObjectEncryptionEncValuesSupported;

    @JsonProperty("token_endpoint_auth_methods_supported")
    private List<String> tokenEndpointAuthMethodsSupported;

    @JsonProperty("token_endpoint_auth_signing_alg_values_supported")
    private List<String> tokenEndpointAuthSigningAlgValuesSupported;

    @JsonProperty("display_values_supported")
    private List<String> displayValuesSupported;

    @JsonProperty("claim_types_supported")
    private List<String> claimTypesSupported;

    @JsonProperty("claims_supported")
    private List<String> claimsSupported;

    @JsonProperty("service_documentation")
    private String serviceDocumentation;

    @JsonProperty("claims_locales_supported")
    private List<String> claimsLocalesSupported;

    @JsonProperty("ui_locales_supported")
    private List<String> uiLocalesSupported;

    @JsonProperty("code_challenge_methods_supported")
    private List<String> codeChallengeMethodsSupported;

    @JsonProperty("claims_parameter_supported")
    private Boolean claimsParameterSupported = Boolean.FALSE;

    @JsonProperty("request_parameter_supported")
    private Boolean requestParameterSupported = Boolean.TRUE;

    @JsonProperty("request_uri_parameter_supported")
    private Boolean requestUriParameterSupported = Boolean.TRUE;

    @JsonProperty("require_request_uri_registration")
    private Boolean requireRequestUriRegistration = Boolean.FALSE;

    @JsonProperty("op_policy_uri")
    private String opPolicyUri;

    @JsonProperty("op_tos_uri")
    private String opTosUri;

    @JsonProperty("authorization_signing_alg_values_supported")
    private List<String> authorizationSigningAlgValuesSupported;

    @JsonProperty("authorization_encryption_alg_values_supported")
    private List<String> authorizationEncryptionAlgValuesSupported;

    @JsonProperty("authorization_encryption_enc_values_supported")
    private List<String> authorizationEncryptionEncValuesSupported;

    @JsonProperty("request_object_endpoint")
    private String requestObjectEndpoint;
    public String getIssuer() {
        return issuer;
    }

    @JsonProperty("tls_client_certificate_bound_access_tokens")
    private Boolean tlsClientCertificateBoundAccessTokens = Boolean.FALSE;

    @JsonProperty("pushed_authorization_request_endpoint")
    private String parEndpoint;

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAuthorizationEndpoint() {
        return authorizationEndpoint;
    }

    public void setAuthorizationEndpoint(String authorizationEndpoint) {
        this.authorizationEndpoint = authorizationEndpoint;
    }

    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    public void setTokenEndpoint(String tokenEndpoint) {
        this.tokenEndpoint = tokenEndpoint;
    }

    public String getUserinfoEndpoint() {
        return userinfoEndpoint;
    }

    public void setUserinfoEndpoint(String userinfoEndpoint) {
        this.userinfoEndpoint = userinfoEndpoint;
    }

    public String getJwksUri() {
        return jwksUri;
    }

    public void setJwksUri(String jwksUri) {
        this.jwksUri = jwksUri;
    }

    public String getEndSessionEndpoint() {
        return endSessionEndpoint;
    }

    public void setEndSessionEndpoint(String endSessionEndpoint) {
        this.endSessionEndpoint = endSessionEndpoint;
    }

    public String getRevocationEndpoint() {
        return revocationEndpoint;
    }

    public void setRevocationEndpoint(String revocationEndpoint) {
        this.revocationEndpoint = revocationEndpoint;
    }

    public String getIntrospectionEndpoint() {
        return introspectionEndpoint;
    }

    public void setIntrospectionEndpoint(String introspectionEndpoint) {
        this.introspectionEndpoint = introspectionEndpoint;
    }

    public String getRegistrationEndpoint() {
        return registrationEndpoint;
    }

    public void setRegistrationEndpoint(String registrationEndpoint) {
        this.registrationEndpoint = registrationEndpoint;
    }

    public String getRegistrationRenewSecretEndpoint() {
        return registrationRenewSecretEndpoint;
    }

    public void setRegistrationRenewSecretEndpoint(String registrationRenewSecretEndpoint) {
        this.registrationRenewSecretEndpoint = registrationRenewSecretEndpoint;
    }

    public String getRegistrationTemplatesEndpoint() {
        return registrationTemplatesEndpoint;
    }

    public void setRegistrationTemplatesEndpoint(String registrationTemplatesEndpoint) {
        this.registrationTemplatesEndpoint = registrationTemplatesEndpoint;
    }

    public List<String> getScopesSupported() {
        return scopesSupported;
    }

    public void setScopesSupported(List<String> scopesSupported) {
        this.scopesSupported = scopesSupported;
    }

    public List<String> getResponseTypesSupported() {
        return responseTypesSupported;
    }

    public void setResponseTypesSupported(List<String> responseTypesSupported) {
        this.responseTypesSupported = responseTypesSupported;
    }

    public List<String> getResponseModesSupported() {
        return responseModesSupported;
    }

    public void setResponseModesSupported(List<String> responseModesSupported) {
        this.responseModesSupported = responseModesSupported;
    }

    public List<String> getGrantTypesSupported() {
        return grantTypesSupported;
    }

    public void setGrantTypesSupported(List<String> grantTypesSupported) {
        this.grantTypesSupported = grantTypesSupported;
    }

    public List<String> getAcrValuesSupported() {
        return acrValuesSupported;
    }

    public void setAcrValuesSupported(List<String> acrValuesSupported) {
        this.acrValuesSupported = acrValuesSupported;
    }

    public List<String> getSubjectTypesSupported() {
        return subjectTypesSupported;
    }

    public void setSubjectTypesSupported(List<String> subjectTypesSupported) {
        this.subjectTypesSupported = subjectTypesSupported;
    }

    public List<String> getIdTokenSigningAlgValuesSupported() {
        return idTokenSigningAlgValuesSupported;
    }

    public void setIdTokenSigningAlgValuesSupported(List<String> idTokenSigningAlgValuesSupported) {
        this.idTokenSigningAlgValuesSupported = idTokenSigningAlgValuesSupported;
    }

    public List<String> getIdTokenEncryptionAlgValuesSupported() {
        return idTokenEncryptionAlgValuesSupported;
    }

    public void setIdTokenEncryptionAlgValuesSupported(List<String> idTokenEncryptionAlgValuesSupported) {
        this.idTokenEncryptionAlgValuesSupported = idTokenEncryptionAlgValuesSupported;
    }

    public List<String> getIdTokenEncryptionEncValuesSupported() {
        return idTokenEncryptionEncValuesSupported;
    }

    public void setIdTokenEncryptionEncValuesSupported(List<String> idTokenEncryptionEncValuesSupported) {
        this.idTokenEncryptionEncValuesSupported = idTokenEncryptionEncValuesSupported;
    }

    public List<String> getUserinfoSigningAlgValuesSupported() {
        return userinfoSigningAlgValuesSupported;
    }

    public void setUserinfoSigningAlgValuesSupported(List<String> userinfoSigningAlgValuesSupported) {
        this.userinfoSigningAlgValuesSupported = userinfoSigningAlgValuesSupported;
    }

    public List<String> getUserinfoEncryptionAlgValuesSupported() {
        return userinfoEncryptionAlgValuesSupported;
    }

    public void setUserinfoEncryptionAlgValuesSupported(List<String> userinfoEncryptionAlgValuesSupported) {
        this.userinfoEncryptionAlgValuesSupported = userinfoEncryptionAlgValuesSupported;
    }

    public List<String> getUserinfoEncryptionEncValuesSupported() {
        return userinfoEncryptionEncValuesSupported;
    }

    public void setUserinfoEncryptionEncValuesSupported(List<String> userinfoEncryptionEncValuesSupported) {
        this.userinfoEncryptionEncValuesSupported = userinfoEncryptionEncValuesSupported;
    }

    public List<String> getRequestObjectSigningAlgValuesSupported() {
        return requestObjectSigningAlgValuesSupported;
    }

    public void setRequestObjectSigningAlgValuesSupported(List<String> requestObjectSigningAlgValuesSupported) {
        this.requestObjectSigningAlgValuesSupported = requestObjectSigningAlgValuesSupported;
    }

    public List<String> getRequestObjectEncryptionAlgValuesSupported() {
        return requestObjectEncryptionAlgValuesSupported;
    }

    public void setRequestObjectEncryptionAlgValuesSupported(List<String> requestObjectEncryptionAlgValuesSupported) {
        this.requestObjectEncryptionAlgValuesSupported = requestObjectEncryptionAlgValuesSupported;
    }

    public List<String> getRequestObjectEncryptionEncValuesSupported() {
        return requestObjectEncryptionEncValuesSupported;
    }

    public void setRequestObjectEncryptionEncValuesSupported(List<String> requestObjectEncryptionEncValuesSupported) {
        this.requestObjectEncryptionEncValuesSupported = requestObjectEncryptionEncValuesSupported;
    }

    public List<String> getTokenEndpointAuthMethodsSupported() {
        return tokenEndpointAuthMethodsSupported;
    }

    public void setTokenEndpointAuthMethodsSupported(List<String> tokenEndpointAuthMethodsSupported) {
        this.tokenEndpointAuthMethodsSupported = tokenEndpointAuthMethodsSupported;
    }

    public List<String> getTokenEndpointAuthSigningAlgValuesSupported() {
        return tokenEndpointAuthSigningAlgValuesSupported;
    }

    public void setTokenEndpointAuthSigningAlgValuesSupported(List<String> tokenEndpointAuthSigningAlgValuesSupported) {
        this.tokenEndpointAuthSigningAlgValuesSupported = tokenEndpointAuthSigningAlgValuesSupported;
    }

    public List<String> getDisplayValuesSupported() {
        return displayValuesSupported;
    }

    public void setDisplayValuesSupported(List<String> displayValuesSupported) {
        this.displayValuesSupported = displayValuesSupported;
    }

    public List<String> getClaimTypesSupported() {
        return claimTypesSupported;
    }

    public void setClaimTypesSupported(List<String> claimTypesSupported) {
        this.claimTypesSupported = claimTypesSupported;
    }

    public List<String> getClaimsSupported() {
        return claimsSupported;
    }

    public void setClaimsSupported(List<String> claimsSupported) {
        this.claimsSupported = claimsSupported;
    }

    public String getServiceDocumentation() {
        return serviceDocumentation;
    }

    public void setServiceDocumentation(String serviceDocumentation) {
        this.serviceDocumentation = serviceDocumentation;
    }

    public List<String> getClaimsLocalesSupported() {
        return claimsLocalesSupported;
    }

    public void setClaimsLocalesSupported(List<String> claimsLocalesSupported) {
        this.claimsLocalesSupported = claimsLocalesSupported;
    }

    public List<String> getUiLocalesSupported() {
        return uiLocalesSupported;
    }

    public List<String> getCodeChallengeMethodsSupported() {
        return codeChallengeMethodsSupported;
    }

    public void setCodeChallengeMethodsSupported(List<String> codeChallengeMethodsSupported) {
        this.codeChallengeMethodsSupported = codeChallengeMethodsSupported;
    }

    public void setUiLocalesSupported(List<String> uiLocalesSupported) {
        this.uiLocalesSupported = uiLocalesSupported;
    }

    public Boolean getClaimsParameterSupported() {
        return claimsParameterSupported;
    }

    public void setClaimsParameterSupported(Boolean claimsParameterSupported) {
        this.claimsParameterSupported = claimsParameterSupported;
    }

    public Boolean getRequestParameterSupported() {
        return requestParameterSupported;
    }

    public void setRequestParameterSupported(Boolean requestParameterSupported) {
        this.requestParameterSupported = requestParameterSupported;
    }

    public Boolean getRequestUriParameterSupported() {
        return requestUriParameterSupported;
    }

    public void setRequestUriParameterSupported(Boolean requestUriParameterSupported) {
        this.requestUriParameterSupported = requestUriParameterSupported;
    }

    public Boolean getRequireRequestUriRegistration() {
        return requireRequestUriRegistration;
    }

    public void setRequireRequestUriRegistration(Boolean requireRequestUriRegistration) {
        this.requireRequestUriRegistration = requireRequestUriRegistration;
    }

    public String getOpPolicyUri() {
        return opPolicyUri;
    }

    public void setOpPolicyUri(String opPolicyUri) {
        this.opPolicyUri = opPolicyUri;
    }

    public String getOpTosUri() {
        return opTosUri;
    }

    public void setOpTosUri(String opTosUri) {
        this.opTosUri = opTosUri;
    }

    public List<String> getAuthorizationSigningAlgValuesSupported() {
        return authorizationSigningAlgValuesSupported;
    }

    public void setAuthorizationSigningAlgValuesSupported(List<String> authorizationSigningAlgValuesSupported) {
        this.authorizationSigningAlgValuesSupported = authorizationSigningAlgValuesSupported;
    }

    public List<String> getAuthorizationEncryptionAlgValuesSupported() {
        return authorizationEncryptionAlgValuesSupported;
    }

    public void setAuthorizationEncryptionAlgValuesSupported(List<String> authorizationEncryptionAlgValuesSupported) {
        this.authorizationEncryptionAlgValuesSupported = authorizationEncryptionAlgValuesSupported;
    }

    public List<String> getAuthorizationEncryptionEncValuesSupported() {
        return authorizationEncryptionEncValuesSupported;
    }

    public void setAuthorizationEncryptionEncValuesSupported(List<String> authorizationEncryptionEncValuesSupported) {
        this.authorizationEncryptionEncValuesSupported = authorizationEncryptionEncValuesSupported;
    }

    public String getRequestObjectEndpoint() {
        return requestObjectEndpoint;
    }

    public void setRequestObjectEndpoint(String requestObjectEndpoint) {
        this.requestObjectEndpoint = requestObjectEndpoint;
    }

    public Boolean getTlsClientCertificateBoundAccessTokens() {
        return tlsClientCertificateBoundAccessTokens;
    }

    public void setTlsClientCertificateBoundAccessTokens(Boolean tlsClientCertificateBoundAccessTokens) {
        this.tlsClientCertificateBoundAccessTokens = tlsClientCertificateBoundAccessTokens;
    }

    public String getParEndpoint() {
        return parEndpoint;
    }

    public void setParEndpoint(String parEndpoint) {
        this.parEndpoint = parEndpoint;
    }
}
