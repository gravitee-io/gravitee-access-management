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
package io.gravitee.am.identityprovider.oauth2;

import io.gravitee.am.common.jwt.SignatureAlgorithm;
import io.gravitee.am.common.oidc.ClientAuthenticationMethod;
import io.gravitee.am.identityprovider.api.oidc.OpenIDConnectIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.api.oidc.jwt.KeyResolver;
import io.gravitee.am.identityprovider.oauth2.jwt.algo.Signature;

import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class OAuth2GenericIdentityProviderConfiguration implements OpenIDConnectIdentityProviderConfiguration {

    private static final String CODE_PARAMETER = "code";
    private String clientId;
    private String clientSecret;
    private String wellKnownUri;
    private String userAuthorizationUri;
    private String accessTokenUri;
    private String userProfileUri;
    private String logoutUri;
    private Set<String> scopes;
    private String responseType;
    private boolean useIdTokenForUserInfo;
    private Signature signature = Signature.RSA_RS256;
    private KeyResolver publicKeyResolver;
    private String resolverParameter;
    private boolean encodeRedirectUri;
    private Integer connectTimeout = 10000;
    private Integer idleTimeout = 10000;
    private Integer maxPoolSize = 200;
    private String clientAuthenticationMethod = ClientAuthenticationMethod.CLIENT_SECRET_POST;
    private boolean storeOriginalTokens;

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

    public String getWellKnownUri() {
        return wellKnownUri;
    }

    public void setWellKnownUri(String wellKnownUri) {
        this.wellKnownUri = wellKnownUri;
    }

    public String getUserAuthorizationUri() {
        return userAuthorizationUri;
    }

    public void setUserAuthorizationUri(String userAuthorizationUri) {
        this.userAuthorizationUri = userAuthorizationUri;
    }

    public String getAccessTokenUri() {
        return accessTokenUri;
    }

    public void setAccessTokenUri(String accessTokenUri) {
        this.accessTokenUri = accessTokenUri;
    }

    public String getUserProfileUri() {
        return userProfileUri;
    }

    public void setUserProfileUri(String userProfileUri) {
        this.userProfileUri = userProfileUri;
    }

    public String getCodeParameter() {
        return CODE_PARAMETER;
    }

    public Set<String> getScopes() {
        return scopes;
    }

    public void setScopes(Set<String> scopes) {
        this.scopes = scopes;
    }

    public String getResponseType() {
        return responseType;
    }

    public void setResponseType(String responseType) {
        this.responseType = responseType;
    }

    public boolean isUseIdTokenForUserInfo() {
        return useIdTokenForUserInfo;
    }

    public void setUseIdTokenForUserInfo(boolean useIdTokenForUserInfo) {
        this.useIdTokenForUserInfo = useIdTokenForUserInfo;
    }

    public Signature getSignature() {
        return signature;
    }

    public void setSignature(Signature signature) {
        this.signature = signature;
    }

    public KeyResolver getPublicKeyResolver() {
        return publicKeyResolver;
    }

    public void setPublicKeyResolver(KeyResolver publicKeyResolver) {
        this.publicKeyResolver = publicKeyResolver;
    }

    public String getResolverParameter() {
        return resolverParameter;
    }

    public void setResolverParameter(String resolverParameter) {
        this.resolverParameter = resolverParameter;
    }

    public boolean isEncodeRedirectUri() {
        return encodeRedirectUri;
    }

    public void setEncodeRedirectUri(boolean encodeRedirectUri) {
        this.encodeRedirectUri = encodeRedirectUri;
    }

    public Integer getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Integer connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Integer getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(Integer idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public Integer getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(Integer maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    @Override
    public boolean isStoreOriginalTokens() {
        return storeOriginalTokens;
    }

    public void setStoreOriginalTokens(boolean storeOriginalTokens) {
        this.storeOriginalTokens = storeOriginalTokens;
    }
    @Override
    public SignatureAlgorithm getSignatureAlgorithm() {
        return this.signature == null ? null : this.signature.getAlg();
    }

    @Override
    public String getLogoutUri() {
        return this.logoutUri;
    }

    public void setLogoutUri(String logoutUri) {
        this.logoutUri = logoutUri;
    }

    public String getClientAuthenticationMethod() {
        return clientAuthenticationMethod;
    }

    public void setClientAuthenticationMethod(String clientAuthenticationMethod) {
        this.clientAuthenticationMethod = clientAuthenticationMethod;
    }
}
