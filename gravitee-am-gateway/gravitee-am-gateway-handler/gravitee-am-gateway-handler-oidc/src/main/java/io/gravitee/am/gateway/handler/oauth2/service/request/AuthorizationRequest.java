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

import io.gravitee.am.gateway.handler.oauth2.service.response.AuthorizationResponse;
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpMethod;
import io.gravitee.common.http.HttpVersion;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthorizationRequest extends OAuth2Request {

    /**
     * RECOMMENDED
     *
     * An opaque value used by the client to maintain
     * state between the request and callback.  The authorization
     * server includes this value when redirecting the user-agent back
     * to the client.  The parameter SHOULD be used for preventing
     * cross-site request forgery as described in Section 10.12.
     *
     * See <a href="https://tools.ietf.org/html/rfc6749#section-10.12"></a>
     */
    private String state;

    /**
     * Indicates if the authorization request has been approved by the end-user (user consent)
     */
    private boolean approved;

    /**
     * OAuth 2.0 Authorization Response
     */
    private AuthorizationResponse response;

    /**
     * OpenID Connect Prompt values
     */
    private Set<String> prompts;

    /**
     * User consents if any
     */
    private List<ScopeApproval> consents;

    private String responseMode;

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public boolean isApproved() {
        return approved;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    public AuthorizationResponse getResponse() {
        return response;
    }

    public void setResponse(AuthorizationResponse response) {
        this.response = response;
    }

    public Set<String> getPrompts() {
        return prompts;
    }

    public void setPrompts(Set<String> prompts) {
        this.prompts = prompts;
    }

    public List<ScopeApproval> getConsents() {
        return consents;
    }

    public void setConsents(List<ScopeApproval> consents) {
        this.consents = consents;
    }

    public String getResponseMode() {
        return responseMode;
    }

    public void setResponseMode(String responseMode) {
        this.responseMode = responseMode;
    }

    public OAuth2Request createOAuth2Request() {
        AuthorizationRequest oAuth2Request = new AuthorizationRequest();

        // set technical information
        oAuth2Request.setId(id());
        oAuth2Request.setTransactionId(transactionId());
        oAuth2Request.setTimestamp(timestamp());
        oAuth2Request.setPath(path());
        oAuth2Request.setOrigin(getOrigin());
        oAuth2Request.setUri(uri());
        oAuth2Request.setScheme(scheme());
        oAuth2Request.setContextPath(contextPath());
        oAuth2Request.setMethod(method());
        oAuth2Request.setVersion(version());
        oAuth2Request.setHeaders(headers());
        oAuth2Request.setParameters(parameters());
        oAuth2Request.setHttpResponse(getHttpResponse());

        // set OAuth 2.0 information
        oAuth2Request.setClientId(getClientId());
        oAuth2Request.setScopes(getScopes());
        oAuth2Request.setResponseType(getResponseType());
        oAuth2Request.setAdditionalParameters(getAdditionalParameters());
        oAuth2Request.setState(getState());

        return oAuth2Request;
    }

    public static JsonObject writeToSession(AuthorizationRequest authorizationRequest) {
        if (authorizationRequest == null) {
            return null;
        }
        JsonObject jsonObject = new JsonObject();
        jsonObject.put("id", authorizationRequest.id());
        jsonObject.put("transactionId", authorizationRequest.transactionId());
        jsonObject.put("timestamp", authorizationRequest.timestamp());
        jsonObject.put("path", authorizationRequest.path());
        jsonObject.put("origin", authorizationRequest.getOrigin());
        jsonObject.put("uri", authorizationRequest.uri());
        jsonObject.put("scheme", authorizationRequest.scheme());
        jsonObject.put("contextPath", authorizationRequest.contextPath());
        jsonObject.put("method", authorizationRequest.method());
        jsonObject.put("version", authorizationRequest.version());
        jsonObject.put("headers", authorizationRequest.headers());
        jsonObject.put("parameters", authorizationRequest.parameters());
        jsonObject.put("clientId", authorizationRequest.getClientId());
        jsonObject.put("scopes", authorizationRequest.getScopes() != null ? new ArrayList<>(authorizationRequest.getScopes()) : null);
        jsonObject.put("responseType", authorizationRequest.getResponseType());
        jsonObject.put("additionalParameters", authorizationRequest.getAdditionalParameters());
        jsonObject.put("state", authorizationRequest.getState());
        jsonObject.put("redirectUri", authorizationRequest.getRedirectUri());
        jsonObject.put("approved", authorizationRequest.isApproved());
        jsonObject.put("consents", authorizationRequest.getConsents());
        return jsonObject;
    }

    public static AuthorizationRequest readFromSession(JsonObject jsonObject) {
        if (jsonObject == null) {
            return null;
        }
        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setId(jsonObject.getString("id"));
        authorizationRequest.setTransactionId(jsonObject.getString("transactionId"));
        authorizationRequest.setTimestamp(jsonObject.getLong("timestamp"));
        authorizationRequest.setPath(jsonObject.getString("path"));
        authorizationRequest.setOrigin(jsonObject.getString("origin"));
        authorizationRequest.setUri(jsonObject.getString("uri"));
        authorizationRequest.setScheme(jsonObject.getString("scheme"));
        authorizationRequest.setContextPath(jsonObject.getString("contextPath"));
        authorizationRequest.setMethod(jsonObject.getValue("method") != null ? HttpMethod.valueOf(jsonObject.getString("method")) : null);
        authorizationRequest.setVersion(jsonObject.getValue("version") != null ? HttpVersion.valueOf(jsonObject.getString("version")) : null);
        authorizationRequest.setHeaders(jsonObject.getValue("headers") != null ?jsonObject.getJsonObject("headers").mapTo(HttpHeaders.class) : null);
        authorizationRequest.setParameters(jsonObject.getValue("parameters") != null ? jsonObject.getJsonObject("parameters").mapTo(LinkedMultiValueMap.class) : null);
        authorizationRequest.setClientId(jsonObject.getString("clientId"));
        authorizationRequest.setScopes(jsonObject.getValue("scopes") != null ? new HashSet<>(jsonObject.getJsonArray("scopes").getList()) : null);
        authorizationRequest.setResponseType(jsonObject.getString("responseType"));
        authorizationRequest.setAdditionalParameters(jsonObject.getValue("additionalParameters") != null ? jsonObject.getJsonObject("additionalParameters").mapTo(LinkedMultiValueMap.class) : null);
        authorizationRequest.setState(jsonObject.getString("state"));
        authorizationRequest.setRedirectUri(jsonObject.getString("redirectUri"));
        authorizationRequest.setApproved(jsonObject.getBoolean("approved"));
        authorizationRequest.setConsents(jsonObject.getValue("consents") != null ? jsonObject.getJsonArray("consents").getList() : null);
        return authorizationRequest;
    }
}
