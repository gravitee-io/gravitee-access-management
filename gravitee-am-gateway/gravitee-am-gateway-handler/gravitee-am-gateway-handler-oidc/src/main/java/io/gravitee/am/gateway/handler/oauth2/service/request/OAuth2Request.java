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


import io.gravitee.am.common.oauth2.TokenType;
import io.gravitee.am.common.oidc.ResponseType;
import io.gravitee.am.common.oidc.Scope;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidScopeException;
import io.gravitee.am.model.uma.PermissionRequest;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.handler.Handler;
import io.gravitee.gateway.api.http2.HttpFrame;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */

@Getter
@Setter
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
     * Resource uris
     */
    private Set<String> resources = new HashSet<>();

    /**
     * Original resources approved during the authorization step (RFC 8707)
     * Used to preserve the initial grant when issuing refresh tokens
     */
    private Set<String> originalAuthorizationResources = new HashSet<>();

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

    /**
     * Token Exchange (RFC 8693) - issued token type
     */
    private String issuedTokenType;

    /**
     * Token Exchange (RFC 8693) - expiration from subject token
     */
    private Date exchangeExpiration;

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
        this.issuedTokenType = other.issuedTokenType;
        this.exchangeExpiration = other.exchangeExpiration;
        this.resources = other.resources != null ? new HashSet<>(other.resources) : new HashSet<>();
        this.originalAuthorizationResources = other.originalAuthorizationResources != null ? new HashSet<>(other.originalAuthorizationResources) : new HashSet<>();

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

    public boolean isClientOnly() {
        return subject == null;
    }

    public boolean shouldGenerateIDToken() {
        return shouldGenerateIDToken(false);
    }

    public boolean shouldGenerateIDToken(boolean acceptOpenidForServiceApp) {
        // Token Exchange (RFC 8693) - only generate id_token when explicitly requested via requested_token_type
        // Standard token exchange response does not include a separate id_token field
        if (issuedTokenType != null && !TokenType.ID_TOKEN.equals(issuedTokenType)) {
            return false;
        }
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
