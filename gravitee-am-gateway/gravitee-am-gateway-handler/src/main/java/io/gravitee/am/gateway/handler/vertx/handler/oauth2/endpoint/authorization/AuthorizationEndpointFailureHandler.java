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
package io.gravitee.am.gateway.handler.vertx.handler.oauth2.endpoint.authorization;

import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidRequestException;
import io.gravitee.am.gateway.handler.oauth2.exception.OAuth2Exception;
import io.gravitee.am.gateway.handler.oauth2.exception.RedirectMismatchException;
import io.gravitee.am.gateway.handler.oauth2.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.gravitee.am.gateway.handler.utils.UriBuilder;
import io.gravitee.am.gateway.handler.vertx.handler.oauth2.request.AuthorizationRequestFactory;
import io.gravitee.am.gateway.handler.vertx.utils.UriBuilderRequest;
import io.gravitee.am.model.Domain;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.Maybe;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * If the request fails due to a missing, invalid, or mismatching redirection URI, or if the client identifier is missing or invalid,
 * the authorization server SHOULD inform the resource owner of the error and MUST NOT automatically redirect the user-agent to the
 * invalid redirection URI.
 *
 * If the resource owner denies the access request or if the request fails for reasons other than a missing or invalid redirection URI,
 * the authorization server informs the client by adding the following parameters to the fragment component of the redirection URI using the
 * "application/x-www-form-urlencoded" format
 *
 * See <a href="https://tools.ietf.org/html/rfc6749#section-4.2.2.1">4.2.2.1. Error Response</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthorizationEndpointFailureHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(AuthorizationEndpointFailureHandler.class);
    private final AuthorizationRequestFactory authorizationRequestFactory = new AuthorizationRequestFactory();
    private Domain domain;
    private ClientService clientService;

    public AuthorizationEndpointFailureHandler(Domain domain, ClientService clientService) {
        this.domain = domain;
        this.clientService = clientService;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        if (routingContext.failed()) {
            try {
                AuthorizationRequest request = resolveInitialAuthorizeRequest(routingContext);
                String defaultProxiedOAuthErrorPage =  UriBuilderRequest.resolveProxyRequest(routingContext.request(),  "/" + domain.getPath() + "/oauth/error", null, false);
                String clientId = request.getClientId();
                if (request == null || request.getRedirectUri() == null || clientId == null) {
                    // no authorization request
                    // or no client available to get a registered redirect uri, go to default error page
                    doRedirect(routingContext.response(), defaultProxiedOAuthErrorPage);
                    return;
                }
                Throwable throwable = routingContext.failure();
                if (throwable instanceof OAuth2Exception) {
                    OAuth2Exception oAuth2Exception = (OAuth2Exception) throwable;
                    clientService.findByClientId(clientId)
                            .switchIfEmpty(Maybe.error(new InvalidRequestException("Client id " + clientId + " not found")))
                            .subscribe(client -> {
                                        if (oAuth2Exception instanceof RedirectMismatchException) {
                                            // user set a wrong redirect_uri
                                            // redirect to a registered (first one) client redirect_uri
                                            request.setRedirectUri(client.getRedirectUris().iterator().next());
                                        }
                                        try {
                                            doRedirect(routingContext.response(), buildRedirectUri(oAuth2Exception, request));
                                        } catch (Exception ex) {
                                            logger.error("Unable to redirect to redirect uri " + request.getRedirectUri(), ex);
                                            // go to default error page
                                            doRedirect(routingContext.response(), defaultProxiedOAuthErrorPage);
                                        }
                                    },
                                    error -> {
                                        // fail to get client, go to default error page
                                        doRedirect(routingContext.response(), defaultProxiedOAuthErrorPage);
                                    });
                } else {
                    if (routingContext.statusCode() != -1) {
                        routingContext
                                .response()
                                .setStatusCode(routingContext.statusCode())
                                .end();
                    } else {
                        routingContext
                                .response()
                                .setStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR_500)
                                .end();
                    }
                }
            } catch (Exception e) {
                logger.error("Unable to handle authorization error response", e);
                doRedirect(routingContext.response(),  "/" + domain.getPath() + "/oauth/error");
            }
        }
    }

    private AuthorizationRequest resolveInitialAuthorizeRequest(RoutingContext routingContext) {
        AuthorizationRequest authorizationRequest = routingContext.session().get(OAuth2Constants.AUTHORIZATION_REQUEST);
        // we have the authorization request in session if we come from the approval user page
        if (authorizationRequest != null) {
            // remove OAuth2Constants.AUTHORIZATION_REQUEST session value
            // should not be used after this step
            routingContext.session().remove(OAuth2Constants.AUTHORIZATION_REQUEST);
            return authorizationRequest;
        }

        // the initial request failed for some reasons, we have the required request parameters to re-create the authorize request
        return authorizationRequestFactory.create(routingContext.request());
    }

    private String buildRedirectUri(OAuth2Exception oAuth2Exception, AuthorizationRequest authorizationRequest) throws URISyntaxException {
        // prepare query
        Map<String, String> query = new LinkedHashMap<>();
        query.put("error", oAuth2Exception.getOAuth2ErrorCode());
        if (oAuth2Exception.getMessage() != null) {
            query.put("error_description", oAuth2Exception.getMessage());
        }
        if (authorizationRequest.getState() != null) {
            query.put(OAuth2Constants.STATE, authorizationRequest.getState());
        }

        boolean fragment = authorizationRequest.getResponseType() == null ? false : authorizationRequest.getResponseType().equals(OAuth2Constants.TOKEN);
        return append(authorizationRequest.getRedirectUri(), query, fragment);
    }


    private void doRedirect(HttpServerResponse response, String url) {
        response.putHeader(HttpHeaders.LOCATION, url).setStatusCode(302).end();
    }

    private String append(String base, Map<String, String> query, boolean fragment) throws URISyntaxException {
        // prepare final redirect uri
        UriBuilder template = UriBuilder.newInstance();

        // get URI from the redirect_uri parameter
        UriBuilder builder = UriBuilder.fromURIString(base);
        URI redirectUri = builder.build();

        // create final redirect uri
        template.scheme(redirectUri.getScheme())
                .host(redirectUri.getHost())
                .port(redirectUri.getPort())
                .userInfo(redirectUri.getUserInfo())
                .path(redirectUri.getPath());

        // append error parameters in "application/x-www-form-urlencoded" format
        if (fragment) {
            query.forEach((k, v) -> template.addFragmentParameter(k, UriBuilder.encodeURIComponent(v)));
        } else {
            query.forEach((k, v) -> template.addParameter(k, UriBuilder.encodeURIComponent(v)));
        }
        return template.build().toString();
    }
}
