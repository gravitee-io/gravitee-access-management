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
package io.gravitee.am.gateway.handler.ciba.service.request;

import io.gravitee.am.common.ciba.Parameters;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpHeaders;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpMethod;
import io.gravitee.common.http.HttpVersion;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;

import java.util.List;

import static io.gravitee.am.gateway.handler.root.resources.endpoint.ParamUtils.getOAuthParameter;
import static io.gravitee.am.gateway.handler.root.resources.endpoint.ParamUtils.splitAcrValues;
import static io.gravitee.am.gateway.handler.root.resources.endpoint.ParamUtils.splitScopes;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CibaAuthenticationRequest extends OAuth2Request {
    String clientNotificationToken;
    List<String> acrValues;
    String loginHintToken;
    String idTokenHint;
    String loginHint;
    String bindingMessage;
    String userCode;
    Integer requestedExpiry;

    public String getClientNotificationToken() {
        return clientNotificationToken;
    }

    public void setClientNotificationToken(String clientNotificationToken) {
        this.clientNotificationToken = clientNotificationToken;
    }

    public String getLoginHintToken() {
        return loginHintToken;
    }

    public void setLoginHintToken(String loginHintToken) {
        this.loginHintToken = loginHintToken;
    }

    public String getIdTokenHint() {
        return idTokenHint;
    }

    public void setIdTokenHint(String idTokenHint) {
        this.idTokenHint = idTokenHint;
    }

    public String getLoginHint() {
        return loginHint;
    }

    public void setLoginHint(String loginHint) {
        this.loginHint = loginHint;
    }

    public String getBindingMessage() {
        return bindingMessage;
    }

    public void setBindingMessage(String bindingMessage) {
        this.bindingMessage = bindingMessage;
    }

    public String getUserCode() {
        return userCode;
    }

    public void setUserCode(String userCode) {
        this.userCode = userCode;
    }

    public Integer getRequestedExpiry() {
        return requestedExpiry;
    }

    public void setRequestedExpiry(Integer requestedExpiry) {
        this.requestedExpiry = requestedExpiry;
    }

    public List<String> getAcrValues() {
        return acrValues;
    }

    public void setAcrValues(List<String> acrValues) {
        this.acrValues = acrValues;
    }

    public static CibaAuthenticationRequest createFrom(RoutingContext context) {
        final HttpServerRequest request = context.request();

        CibaAuthenticationRequest cibaRequest = new CibaAuthenticationRequest();

        // set technical information
        cibaRequest.setTimestamp(System.currentTimeMillis());
        cibaRequest.setId(SecureRandomString.generate());
        cibaRequest.setUri(request.uri());
        cibaRequest.setContextPath(request.path() != null ? request.path().split("/")[0] : null);
        cibaRequest.setPath(request.path());
        cibaRequest.setHeaders(new VertxHttpHeaders(request.getDelegate().headers()));
        cibaRequest.setParameters(extractRequestParameters(request));
        cibaRequest.setSslSession(request.sslSession());
        cibaRequest.setMethod(request.method() != null ? HttpMethod.valueOf(request.method().name()) : null);
        cibaRequest.setScheme(request.scheme());
        cibaRequest.setVersion(request.version() != null ? HttpVersion.valueOf(request.version().name()) : null);
        cibaRequest.setRemoteAddress(request.remoteAddress() != null ? request.remoteAddress().host() : null);
        cibaRequest.setLocalAddress(request.localAddress() != null ? request.localAddress().host() : null);
        cibaRequest.setHost(request.host());

        final Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        cibaRequest.setClientId(client.getClientId());

        cibaRequest.setScopes(splitScopes(getOAuthParameter(context, io.gravitee.am.common.oauth2.Parameters.SCOPE)));
        cibaRequest.setClientNotificationToken(getOAuthParameter(context, Parameters.CLIENT_NOTIFICATION_TOKEN));
        cibaRequest.setLoginHintToken(getOAuthParameter(context, Parameters.LOGIN_HINT_TOKEN));
        cibaRequest.setIdTokenHint(getOAuthParameter(context, io.gravitee.am.common.oidc.Parameters.ID_TOKEN_HINT));
        cibaRequest.setLoginHint(getOAuthParameter(context, io.gravitee.am.common.oidc.Parameters.LOGIN_HINT));
        cibaRequest.setAcrValues(splitAcrValues(getOAuthParameter(context, io.gravitee.am.common.oidc.Parameters.ACR_VALUES)));
        cibaRequest.setBindingMessage(getOAuthParameter(context, Parameters.BINDING_MESSAGE));
        cibaRequest.setUserCode(getOAuthParameter(context, Parameters.USER_CODE));
        final String reqExpiry = getOAuthParameter(context, Parameters.REQUESTED_EXPIRY);
        if (reqExpiry != null) {
            cibaRequest.setRequestedExpiry(Integer.parseInt(reqExpiry));
        }

        return cibaRequest;
    }

    private static MultiValueMap<String, String> extractRequestParameters(HttpServerRequest request) {
        MultiValueMap<String, String> requestParameters = new LinkedMultiValueMap<>(request.params().size());
        request.params().entries().forEach(entry -> requestParameters.add(entry.getKey(), entry.getValue()));
        return requestParameters;
    }
}
