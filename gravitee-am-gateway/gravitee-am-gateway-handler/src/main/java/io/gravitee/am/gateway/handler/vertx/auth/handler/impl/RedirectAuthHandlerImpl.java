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
import io.gravitee.am.gateway.handler.utils.URIBuilder;
import io.gravitee.common.http.HttpHeaders;
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

import java.net.URI;

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
            // Now redirect to the login url - we'll get redirected back here after successful login
            session.put(returnURLParam, context.request().uri());

            try {
                HttpServerRequest request = context.request();
                URIBuilder builder = URIBuilder.newInstance();

                // scheme
                String scheme = request.getHeader(HttpHeaders.X_FORWARDED_PROTO);
                if (scheme != null && !scheme.isEmpty()) {
                    builder.scheme(scheme);
                }

                // host + port
                String host = request.getHeader(HttpHeaders.X_FORWARDED_HOST);
                if (host != null && !host.isEmpty()) {
                    if (host.contains(":")) {
                        // Forwarded host contains both host and port
                        String [] parts = host.split(":");
                        builder.host(parts[0]);
                        builder.port(Integer.valueOf(parts[1]));
                    } else {
                        builder.host(host);
                    }
                }

                // path
                builder.path(loginRedirectURL);

                // query parameters
                builder.addParameter(OAuth2Constants.CLIENT_ID, request.getParam(OAuth2Constants.CLIENT_ID));

                URI uri = builder.build();
                handler.handle(Future.failedFuture(new HttpStatusException(302, uri.toString())));
            } catch (Exception e) {
                logger.warn("Failed to decode login redirect url", e);
                handler.handle(Future.failedFuture(new HttpStatusException(302, loginRedirectURL)));
            }
        } else {
            handler.handle(Future.failedFuture("No session - did you forget to include a SessionHandler?"));
        }
    }
}
