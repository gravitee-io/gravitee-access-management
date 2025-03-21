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

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.exception.oauth2.RedirectMismatchException;
import io.gravitee.am.common.exception.oauth2.ReturnUrlMismatchException;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.gravitee.common.http.HttpHeaders;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
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
            checkReturnUrl(context, returnUrl);
        }
    }

    private void checkReturnUrl(RoutingContext context, String returnUrl) {
        if (!checkProxyRequest(returnUrl, context) && !checkRegisteredRedirectUris(returnUrl, context)) {
            context.fail(new ReturnUrlMismatchException(String.format("The return_url [ %s ] MUST match the registered callback URL for this application OR this request", returnUrl)));
        }
        else {
            try {
                // use UriBuilder to sanitize the uri so non urlEncoded character will be encoded
                // to avoid URISyntaxException due to the space in the scope parameter value
                URI uri = UriBuilder.fromURIString(returnUrl).build();
                if(uri.getUserInfo() != null) {
                    context.fail(new ReturnUrlMismatchException(String.format("The return_url [ %s ] MUST NOT contain userInfo part", returnUrl)));
                } else {
                    context.next();
                }
            } catch (URISyntaxException ex){
                log.debug("Return URL syntax error", ex);
                context.fail(new InvalidRequestException(String.format("The return_url [ %s ] syntax error", returnUrl)));
            }
        }
    }

    private boolean checkProxyRequest(String requestedReturnUrl, RoutingContext context) {
        requestedReturnUrl = requestedReturnUrl.endsWith("/") ? requestedReturnUrl : requestedReturnUrl + "/";
        String resolvedUrl = resolveProxyRequest(context.request(), context.get(CONTEXT_PATH) + "/");
        if (log.isDebugEnabled()) {
            log.debug("CheckProxyRequest - return_url '{}' should start with '{}'", requestedReturnUrl, resolvedUrl);
            String scheme = context.request().getHeader(HttpHeaders.X_FORWARDED_PROTO);
            String host = context.request().getHeader(HttpHeaders.X_FORWARDED_HOST);
            String forwardedPath = context.request().getHeader(HttpHeaders.X_FORWARDED_PREFIX);
            log.debug("CheckProxyRequest - X-Forward-Proto={} / X-Forward-Host={} / X-Forward-Prefix={}", scheme, host, forwardedPath);
        }
        return requestedReturnUrl.startsWith(resolvedUrl);
    }

    private boolean checkRegisteredRedirectUris(String requestedReturnUrl, RoutingContext context) {
        Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        List<String> redirectUris = (client.getRedirectUris() == null) ? new ArrayList<>() : client.getRedirectUris();

        boolean uriStrictMatch = this.domain.isRedirectUriStrictMatching() || this.domain.usePlainFapiProfile();
        return redirectUris
                .stream()
                .anyMatch(registeredClientUri -> redirectMatches(requestedReturnUrl, registeredClientUri, uriStrictMatch));
    }

}
