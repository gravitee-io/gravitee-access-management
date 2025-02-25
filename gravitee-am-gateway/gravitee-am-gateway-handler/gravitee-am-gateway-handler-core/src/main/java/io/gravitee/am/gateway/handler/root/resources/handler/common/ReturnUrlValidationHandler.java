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
package io.gravitee.am.gateway.handler.root.resources.handler.common;

import io.gravitee.am.common.exception.oauth2.ReturnUrlMismatchException;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.MalformedURLException;
import java.net.URL;

import java.util.ArrayList;
import java.util.List;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.resolveProxyRequest;
import static io.gravitee.am.gateway.handler.root.resources.endpoint.ParamUtils.*;

@RequiredArgsConstructor
@Slf4j
public class ReturnUrlValidationHandler implements Handler<RoutingContext> {
    private final Domain domain;

    @Override
    public void handle(RoutingContext context) {
        String returnUrl = getOAuthParameter(context, ConstantKeys.RETURN_URL_KEY);
        if (returnUrl == null) {
            context.next();
        } else {
            if (!checkProxyRequest(returnUrl, context) && !checkRegisteredRedirectUris(returnUrl, context)) {
                context.fail(new ReturnUrlMismatchException(String.format("The return_url [ %s ] MUST match the registered callback URL for this application OR this request", returnUrl)));
            }
            else if (userInfoPresent(returnUrl)) {
                context.fail(new ReturnUrlMismatchException(String.format("The return_url [ %s ] MUST NOT contain userInfo part", returnUrl)));
            }
            else {
                context.next();
            }
        }
    }



    private boolean checkProxyRequest(String requestedReturnUrl, RoutingContext context) {
        requestedReturnUrl = requestedReturnUrl.endsWith("/") ? requestedReturnUrl : requestedReturnUrl + "/";
        return requestedReturnUrl.startsWith(resolveProxyRequest(context.request(), context.get(CONTEXT_PATH) + "/"));
    }

    private boolean checkRegisteredRedirectUris(String requestedReturnUrl, RoutingContext context) {
        Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        List<String> redirectUris = (client.getRedirectUris() == null) ? new ArrayList<>() : client.getRedirectUris();

        boolean uriStrictMatch = this.domain.isRedirectUriStrictMatching() || this.domain.usePlainFapiProfile();
        return redirectUris
                .stream()
                .anyMatch(registeredClientUri -> redirectMatches(requestedReturnUrl, registeredClientUri, uriStrictMatch));
    }

    private boolean userInfoPresent(String urlAddress) {
        try {
            URL url = new URL(urlAddress);
            return url.getUserInfo() != null;
        } catch (MalformedURLException ex){
            log.debug("Return URL malformed", ex);
            return true;
        }
    }
}
