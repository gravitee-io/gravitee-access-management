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
package io.gravitee.am.gateway.handler.vertx.auth.handler.impl;

import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.gravitee.am.gateway.handler.vertx.utils.UriBuilderRequest;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Extends default {@link io.vertx.ext.web.handler.RedirectAuthHandler} with X-Forwarded Strategy
 * We also append {@link io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants#CLIENT_ID} query parameter which will be use in LoginEndpoint
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RedirectAuthHandlerImpl extends io.vertx.ext.web.handler.impl.RedirectAuthHandlerImpl {

    private static final Logger logger = LoggerFactory.getLogger(RedirectAuthHandlerImpl.class);

    private String loginRedirectURL;
    private String returnURLParam;

    public RedirectAuthHandlerImpl(AuthProvider authProvider, String loginRedirectURL, String returnURLParam) {
        super(authProvider, loginRedirectURL, returnURLParam);
        this.loginRedirectURL = loginRedirectURL;
        this.returnURLParam = returnURLParam;
    }

    @Override
    public void parseCredentials(RoutingContext context, Handler<AsyncResult<JsonObject>> handler) {
        Session session = context.session();
        if (session != null) {
            try {
                // Save current request in session - we'll get redirected back here after successful login
                HttpServerRequest request = context.request();
                session.put(returnURLParam,
                        UriBuilderRequest.resolveProxyRequest(
                                new io.vertx.reactivex.core.http.HttpServerRequest(request),
                                request.path(), request.params().entries().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)), true, false));

                // Now redirect to the login url
                String uri =
                        UriBuilderRequest.resolveProxyRequest(
                                new io.vertx.reactivex.core.http.HttpServerRequest(request),
                                loginRedirectURL,
                                Collections.singletonMap(OAuth2Constants.CLIENT_ID, request.getParam(OAuth2Constants.CLIENT_ID)), false, false);

                handler.handle(Future.failedFuture(new HttpStatusException(302, uri)));
            } catch (Exception e) {
                logger.warn("Failed to decode login redirect url", e);
                handler.handle(Future.failedFuture(new HttpStatusException(302, loginRedirectURL)));
            }
        } else {
            handler.handle(Future.failedFuture("No session - did you forget to include a SessionHandler?"));
        }
    }
}
