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
package io.gravitee.am.gateway.handler.oauth2.service.request;


import io.gravitee.am.common.oidc.ResponseType;
import io.gravitee.am.common.oidc.Scope;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidScopeException;
import io.gravitee.am.model.uma.PermissionRequest;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.handler.Handler;
import io.gravitee.gateway.api.http2.HttpFrame;

import java.util.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */

public class OAuth2Request extends BaseRequest {

    /**
     * OAuth 2.0 Client Identifier parameter
     *
     * The authorization server issues the registered client a client
     * identifier -- a unique string representing the registration
     * information provided by the client.  The client identifier is not a
     * secret; it is exposed to the resource owner and MUST NOT be used
     * alone for client authentication.  The client identifier is unique to
     * the authorization server.
     *
     * The client identifier string size is left undefined by this
     * specification.  The client should avoid making assumptions about the
     * identifier size.  The authorization server SHOULD document the size
     * of any identifier it issues.
     *
     * See <a href="https://tools.ietf.org/html/rfc6749#section-2.2">Client Identifier</a>
     */
    private String clientId;

    /**
     * OAuth 2.0 Grant Type parameter.
     */
    private String grantType;

    /**
     * OAuth 2.0 Response Type parameter.
     */
    private String responseType;

    /**
     * OAuth 2.0 scope parameter.
     */
    private Set<String> scopes = new HashSet<>();

    /**
     * OPTIONAL
     *
     * After completing its interaction with the resource owner, the
     * authorization server directs the resource owner's user-agent back to
     * the client.  The authorization server redirects the user-agent to the
     * client's redirection endpoint previously established with the
     * authorization server during the client registration process or when
     * making the authorization request.
     *
     * See <a href="https://tools.ietf.org/html/rfc6749#section-3.1.2"></a>
     */
    private String redirectUri;

    /**
     * Resource owner technical id
     */
    private String subject;

    /**
     * Resource uri
     */
    private String resource;

    /**
     * Boolean indicates if the current request support OAuth 2.0 Refresh Token
     */
    private boolean supportRefreshToken;

    /**
     * Decoded refresh token used for the oauth 2.0 request (refresh grant flow)
     */
    private Map<String, Object> refreshToken;

    /**
     * Decoded authorization code used for the oauth 2.0 request (authorization code flow step 2)
     */
    private Map<String, Object> authorizationCode;

    /**
     * OAuth 2.0 contextual data
     */
    private Map<String, Object> context = new HashMap<>();

    /**
     * OAuth 2.0 contextual version
     */
    private int contextVersion;

    /**
     * Gravitee execution context
     */
    private Map<String, Object> executionContext = new HashMap<>();

    /**
     * UMA 2.0 permissions
     */
    private List<PermissionRequest> permissions;

    private MultiValueMap<String, String> pathParameters = null;

    /**
     * REQUIRED for <a href=" https://datatracker.ietf.org/doc/html/rfc8705#section-3.1">Certificate Bound Access Token</a>
     */
    private String confirmationMethodX5S256;

    public OAuth2Request(OAuth2Request other){
        this.clientId = other.clientId;
        this.grantType = other.grantType;
        this.responseType = other.responseType;
        this.scopes = other.scopes;
        this.redirectUri = other.redirectUri;
        this.subject = other.subject;
        this.supportRefreshToken = other.supportRefreshToken;
        this.refreshToken = other.refreshToken;
        this.authorizationCode = other.authorizationCode;
        this.context = other.context;
        this.contextVersion = other.contextVersion;
        this.executionContext = other.executionContext;
        this.permissions = other.permissions;
        this.pathParameters = other.pathParameters;
        this.confirmationMethodX5S256 = other.confirmationMethodX5S256;
        this.resource = other.resource;

        //BaseRequest
        this.setId(other.getId());
        this.setTransactionId(other.getTransactionId());
        this.setUri(other.getUri());
        this.setPath(other.getPath());
        this.setPathInfo(other.getPathInfo());
        this.setContextPath(other.getContextPath());
        this.setParameters(other.getParameters());
        this.setHeaders(other.getHeaders());
        this.setMethod(other.getMethod());
        this.setScheme(other.getScheme());
        this.setTimestamp(other.getTimestamp());
        this.setRemoteAddress(other.getRemoteAddress());
        this.setLocalAddress(other.getLocalAddress());
        this.setVersion(other.getVersion());
        this.setSslSession(other.getSslSession());
        this.setHttpResponse(other.getHttpResponse());
        this.setHost(other.host());
    }

    public OAuth2Request() {
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getGrantType() {
        return grantType;
    }

    public void setGrantType(String grantType) {
        this.grantType = grantType;
    }

    public String getResponseType() {
        return responseType;
    }

    public void setResponseType(String responseType) {
        this.responseType = responseType;
    }

    public Set<String> getScopes() {
        return scopes;
    }

    public void setScopes(Set<String> scopes) {
        this.scopes = scopes;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public boolean isClientOnly() {
        return subject == null;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public boolean isSupportRefreshToken() {
        return supportRefreshToken;
    }

    public void setSupportRefreshToken(boolean supportRefreshToken) {
        this.supportRefreshToken = supportRefreshToken;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public void setContext(Map<String, Object> context) {
        this.context = context;
    }

    public int getContextVersion() {
        return contextVersion;
    }

    public void setContextVersion(int contextVersion) {
        this.contextVersion = contextVersion;
    }

    public Map<String, Object> getExecutionContext() {
        return executionContext;
    }

    public void setExecutionContext(Map<String, Object> executionContext) {
        this.executionContext = executionContext;
    }

    public Map<String, Object> getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(Map<String, Object> refreshToken) {
        this.refreshToken = refreshToken;
    }

    public Map<String, Object> getAuthorizationCode() {
        return authorizationCode;
    }

    public void setAuthorizationCode(Map<String, Object> authorizationCode) {
        this.authorizationCode = authorizationCode;
    }

    public List<PermissionRequest> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<PermissionRequest> permissions) {
        this.permissions = permissions;
    }

    public String getConfirmationMethodX5S256() {
        return confirmationMethodX5S256;
    }

    public void setConfirmationMethodX5S256(String confirmationMethodX5S256) {
        this.confirmationMethodX5S256 = confirmationMethodX5S256;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public boolean shouldGenerateIDToken() {
        return shouldGenerateIDToken(false);
    }

    public boolean shouldGenerateIDToken(boolean acceptOpenidForServiceApp) {
        if (getResponseType() != null && ResponseType.CODE_TOKEN.equals(getResponseType())) {
            return false;
        }
        if (getResponseType() != null
                && (ResponseType.CODE_ID_TOKEN_TOKEN.equals(getResponseType()) || ResponseType.ID_TOKEN_TOKEN.equals(getResponseType()))) {
            return true;
        }
        if (getScopes() != null && getScopes().contains(Scope.OPENID.getKey())) {
            if (isClientOnly() && acceptOpenidForServiceApp) {
                return false;
            } else if (isClientOnly()) {
                throw new InvalidScopeException("Invalid scope: " + Scope.OPENID);
            } else {
                return true;
            }
        }
        return false;
    }

    public boolean isSupportAtHashValue() {
        return getResponseType() != null
                && (ResponseType.ID_TOKEN_TOKEN.equals(getResponseType())
                || ResponseType.CODE_ID_TOKEN_TOKEN.equals(getResponseType()));
    }

    @Override
    public MultiValueMap<String, String> pathParameters() {
        if (pathParameters == null) {
            pathParameters = new LinkedMultiValueMap<>();
        }

        return pathParameters;
    }

    @Override
    public Request customFrameHandler(Handler<HttpFrame> frameHandler) {
        return this;
    }
}
