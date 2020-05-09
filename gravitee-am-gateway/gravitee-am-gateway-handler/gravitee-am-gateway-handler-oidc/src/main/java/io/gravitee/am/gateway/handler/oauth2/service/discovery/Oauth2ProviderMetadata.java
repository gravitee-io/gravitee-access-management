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
package io.gravitee.am.gateway.handler.oauth2.service.discovery;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * OAuth 2.0 Authorization Server Metadata as described in RFC 8414.
 * This object follow RFC metadata available <a href="https://tools.ietf.org/html/rfc8414#section-2">here</a>
 *
 * This metadata should be exposed under "/.well-known/oauth-authorization-server" configuration endpoint.
 * See <a href="https://tools.ietf.org/html/rfc8414#section-3">here</a> for more details.
 *
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Oauth2ProviderMetadata {

    @JsonProperty("issuer")
    private String issuer;

    @JsonProperty("authorization_endpoint")
    private String authorizationEndpoint;

    @JsonProperty("token_endpoint")
    private String tokenEndpoint;

    @JsonProperty("jwks_uri")
    private String jwksUri;

    @JsonProperty("registration_endpoint")
    private String registrationEndpoint;

    @JsonProperty("scopes_supported")
    private List<String> scopesSupported;

    @JsonProperty("response_types_supported")
    private List<String> responseTypesSupported;

    @JsonProperty("response_modes_supported")
    private List<String> responseModesSupported;

    @JsonProperty("grant_types_supported")
    private List<String> grantTypesSupported;

    @JsonProperty("token_endpoint_auth_methods_supported")
    private List<String> tokenEndpointAuthMethodsSupported;

    @JsonProperty("token_endpoint_auth_signing_alg_values_supported")
    private List<String> tokenEndpointAuthSigningAlgValuesSupported;

    @JsonProperty("service_documentation")
    private String serviceDocumentation;

    @JsonProperty("ui_locales_supported")
    private List<String> uiLocalesSupported;

    @JsonProperty("op_policy_uri")
    private String opPolicyUri;

    @JsonProperty("op_tos_uri")
    private String opTosUri;

    @JsonProperty("revocation_endpoint")
    private String revocationEndpoint;

    @JsonProperty("revocation_endpoint_auth_methods_supported")
    private List<String> revocationEndpointAuthMethodsSupported;

    @JsonProperty("revocation_endpoint_auth_signing_alg_values_supported")
    private List<String> revocationEndpointAuthSigningAlgValuesSupported;

    @JsonProperty("introspection_endpoint")
    private String introspectionEndpoint;

    @JsonProperty("introspection_endpoint_auth_methods_supported")
    private List<String> introspectionEndpointAuthMethodsSupported;

    @JsonProperty("introspection_endpoint_auth_signing_alg_values_supported")
    private List<String> introspectionEndpointAuthSigningAlgValuesSupported;

    @JsonProperty("code_challenge_methods_supported")
    private List<String> codeChallengeMethodsSupported;

    public String getIssuer() {
        return issuer;
    }

    public Oauth2ProviderMetadata setIssuer(String issuer) {
        this.issuer = issuer;
        return this;
    }

    public String getAuthorizationEndpoint() {
        return authorizationEndpoint;
    }

    public Oauth2ProviderMetadata setAuthorizationEndpoint(String authorizationEndpoint) {
        this.authorizationEndpoint = authorizationEndpoint;
        return this;
    }

    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    public Oauth2ProviderMetadata setTokenEndpoint(String tokenEndpoint) {
        this.tokenEndpoint = tokenEndpoint;
        return this;
    }

    public String getJwksUri() {
        return jwksUri;
    }

    public Oauth2ProviderMetadata setJwksUri(String jwksUri) {
        this.jwksUri = jwksUri;
        return this;
    }

    public String getRegistrationEndpoint() {
        return registrationEndpoint;
    }

    public Oauth2ProviderMetadata setRegistrationEndpoint(String registrationEndpoint) {
        this.registrationEndpoint = registrationEndpoint;
        return this;
    }

    public List<String> getScopesSupported() {
        return scopesSupported;
    }

    public Oauth2ProviderMetadata setScopesSupported(List<String> scopesSupported) {
        this.scopesSupported = scopesSupported;
        return this;
    }

    public List<String> getResponseTypesSupported() {
        return responseTypesSupported;
    }

    public Oauth2ProviderMetadata setResponseTypesSupported(List<String> responseTypesSupported) {
        this.responseTypesSupported = responseTypesSupported;
        return this;
    }

    public List<String> getResponseModesSupported() {
        return responseModesSupported;
    }

    public Oauth2ProviderMetadata setResponseModesSupported(List<String> responseModesSupported) {
        this.responseModesSupported = responseModesSupported;
        return this;
    }

    public List<String> getGrantTypesSupported() {
        return grantTypesSupported;
    }

    public Oauth2ProviderMetadata setGrantTypesSupported(List<String> grantTypesSupported) {
        this.grantTypesSupported = grantTypesSupported;
        return this;
    }

    public List<String> getTokenEndpointAuthMethodsSupported() {
        return tokenEndpointAuthMethodsSupported;
    }

    public Oauth2ProviderMetadata setTokenEndpointAuthMethodsSupported(List<String> tokenEndpointAuthMethodsSupported) {
        this.tokenEndpointAuthMethodsSupported = tokenEndpointAuthMethodsSupported;
        return this;
    }

    public List<String> getTokenEndpointAuthSigningAlgValuesSupported() {
        return tokenEndpointAuthSigningAlgValuesSupported;
    }

    public Oauth2ProviderMetadata setTokenEndpointAuthSigningAlgValuesSupported(List<String> tokenEndpointAuthSigningAlgValuesSupported) {
        this.tokenEndpointAuthSigningAlgValuesSupported = tokenEndpointAuthSigningAlgValuesSupported;
        return this;
    }

    public String getServiceDocumentation() {
        return serviceDocumentation;
    }

    public Oauth2ProviderMetadata setServiceDocumentation(String serviceDocumentation) {
        this.serviceDocumentation = serviceDocumentation;
        return this;
    }

    public List<String> getUiLocalesSupported() {
        return uiLocalesSupported;
    }

    public Oauth2ProviderMetadata setUiLocalesSupported(List<String> uiLocalesSupported) {
        this.uiLocalesSupported = uiLocalesSupported;
        return this;
    }

    public String getOpPolicyUri() {
        return opPolicyUri;
    }

    public Oauth2ProviderMetadata setOpPolicyUri(String opPolicyUri) {
        this.opPolicyUri = opPolicyUri;
        return this;
    }

    public String getOpTosUri() {
        return opTosUri;
    }

    public Oauth2ProviderMetadata setOpTosUri(String opTosUri) {
        this.opTosUri = opTosUri;
        return this;
    }

    public String getRevocationEndpoint() {
        return revocationEndpoint;
    }

    public Oauth2ProviderMetadata setRevocationEndpoint(String revocationEndpoint) {
        this.revocationEndpoint = revocationEndpoint;
        return this;
    }

    public List<String> getRevocationEndpointAuthMethodsSupported() {
        return revocationEndpointAuthMethodsSupported;
    }

    public Oauth2ProviderMetadata setRevocationEndpointAuthMethodsSupported(List<String> revocationEndpointAuthMethodsSupported) {
        this.revocationEndpointAuthMethodsSupported = revocationEndpointAuthMethodsSupported;
        return this;
    }

    public List<String> getRevocationEndpointAuthSigningAlgValuesSupported() {
        return revocationEndpointAuthSigningAlgValuesSupported;
    }

    public Oauth2ProviderMetadata setRevocationEndpointAuthSigningAlgValuesSupported(List<String> revocationEndpointAuthSigningAlgValuesSupported) {
        this.revocationEndpointAuthSigningAlgValuesSupported = revocationEndpointAuthSigningAlgValuesSupported;
        return this;
    }

    public String getIntrospectionEndpoint() {
        return introspectionEndpoint;
    }

    public Oauth2ProviderMetadata setIntrospectionEndpoint(String introspectionEndpoint) {
        this.introspectionEndpoint = introspectionEndpoint;
        return this;
    }

    public List<String> getIntrospectionEndpointAuthMethodsSupported() {
        return introspectionEndpointAuthMethodsSupported;
    }

    public Oauth2ProviderMetadata setIntrospectionEndpointAuthMethodsSupported(List<String> introspectionEndpointAuthMethodsSupported) {
        this.introspectionEndpointAuthMethodsSupported = introspectionEndpointAuthMethodsSupported;
        return this;
    }

    public List<String> getIntrospectionEndpointAuthSigningAlgValuesSupported() {
        return introspectionEndpointAuthSigningAlgValuesSupported;
    }

    public Oauth2ProviderMetadata setIntrospectionEndpointAuthSigningAlgValuesSupported(List<String> introspectionEndpointAuthSigningAlgValuesSupported) {
        this.introspectionEndpointAuthSigningAlgValuesSupported = introspectionEndpointAuthSigningAlgValuesSupported;
        return this;
    }

    public List<String> getCodeChallengeMethodsSupported() {
        return codeChallengeMethodsSupported;
    }

    public Oauth2ProviderMetadata setCodeChallengeMethodsSupported(List<String> codeChallengeMethodsSupported) {
        this.codeChallengeMethodsSupported = codeChallengeMethodsSupported;
        return this;
    }
}
