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
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpMethod;
import io.gravitee.common.http.HttpVersion;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.List;
import java.util.Map;

import static io.gravitee.am.gateway.handler.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;
import static io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.ParamUtils.*;

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

        CibaAuthenticationRequest oAuth2Request = new CibaAuthenticationRequest();

        // set technical information
        oAuth2Request.setTimestamp(System.currentTimeMillis());
        oAuth2Request.setId(RandomString.generate());
        oAuth2Request.setUri(request.uri());
        oAuth2Request.setContextPath(request.path() != null ? request.path().split("/")[0] : null);
        oAuth2Request.setPath(request.path());
        oAuth2Request.setHeaders(extractHeaders(request));
        oAuth2Request.setParameters(extractRequestParameters(request));
        oAuth2Request.setSslSession(request.sslSession());
        oAuth2Request.setMethod(request.method() != null ? HttpMethod.valueOf(request.method().name()) : null);
        oAuth2Request.setScheme(request.scheme());
        oAuth2Request.setVersion(request.version() != null ? HttpVersion.valueOf(request.version().name()) : null);
        oAuth2Request.setRemoteAddress(request.remoteAddress() != null ? request.remoteAddress().host() : null);
        oAuth2Request.setLocalAddress(request.localAddress() != null ? request.localAddress().host() : null);

        final Client client = context.get(CLIENT_CONTEXT_KEY);
        oAuth2Request.setClientId(client.getClientId());

        oAuth2Request.setScopes(splitScopes(getOAuthParameter(context, io.gravitee.am.common.oauth2.Parameters.SCOPE)));
        oAuth2Request.setClientNotificationToken(getOAuthParameter(context, Parameters.CLIENT_NOTIFICATION_TOKEN));
        oAuth2Request.setLoginHintToken(getOAuthParameter(context, Parameters.LOGIN_HINT_TOKEN));
        oAuth2Request.setIdTokenHint(getOAuthParameter(context, io.gravitee.am.common.oidc.Parameters.ID_TOKEN_HINT));
        oAuth2Request.setLoginHint(getOAuthParameter(context, io.gravitee.am.common.oidc.Parameters.LOGIN_HINT));
        oAuth2Request.setAcrValues(splitAcrValues(getOAuthParameter(context, io.gravitee.am.common.oidc.Parameters.ACR_VALUES)));
        oAuth2Request.setBindingMessage(getOAuthParameter(context, Parameters.BINDING_MESSAGE));
        oAuth2Request.setUserCode(getOAuthParameter(context, Parameters.USER_CODE));
        final String reqExpiry = getOAuthParameter(context, Parameters.REQUESTED_EXPIRY);
        if (reqExpiry != null) {
            oAuth2Request.setRequestedExpiry(Integer.parseInt(reqExpiry));
        }

        return oAuth2Request;
    }

    private static MultiValueMap<String, String> extractRequestParameters(HttpServerRequest request) {
        MultiValueMap<String, String> requestParameters = new LinkedMultiValueMap<>(request.params().size());
        request.params().entries().forEach(entry -> requestParameters.add(entry.getKey(), entry.getValue()));
        return requestParameters;
    }

    private static HttpHeaders extractHeaders(HttpServerRequest request) {
        MultiMap vertxHeaders = request.headers();
        if (vertxHeaders != null) {
            HttpHeaders headers = new HttpHeaders(vertxHeaders.size());
            for (Map.Entry<String, String> header : vertxHeaders.entries()) {
                headers.add(header.getKey(), header.getValue());
            }
            return headers;
        }
        return null;
    }
}
