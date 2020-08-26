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
package io.gravitee.am.gateway.handler.root.resources.auth.handler.impl;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.UserAuthProvider;
import io.gravitee.common.http.HttpHeaders;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Extends default {@link io.vertx.ext.web.handler.FormLoginHandler} and appends
 * client_id to the AuthInfo to retrieve client identity providers
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class FormLoginHandlerImpl extends io.vertx.ext.web.handler.impl.FormLoginHandlerImpl {

    private static final Logger log = LoggerFactory.getLogger(FormLoginHandlerImpl.class);
    private static final String DEFAULT_DIRECT_LOGGED_IN_OK_PAGE = "<html><body><h1>Login successful</h1></body></html>";
    private String usernameParam;
    private String passwordParam;
    private String returnURLParam;
    private String directLoggedInOKURL;
    private UserAuthProvider authProvider;

    public FormLoginHandlerImpl(AuthProvider authProvider, String usernameParam, String passwordParam, String returnURLParam, String directLoggedInOKURL) {
        super(authProvider, usernameParam, passwordParam, returnURLParam, directLoggedInOKURL);
        this.usernameParam = usernameParam;
        this.passwordParam = passwordParam;
        this.authProvider = (UserAuthProvider) authProvider;
        this.returnURLParam = returnURLParam;
        this.directLoggedInOKURL = directLoggedInOKURL;
    }

    @Override
    public void handle(RoutingContext context) {
        HttpServerRequest req = context.request();
        if (req.method() != HttpMethod.POST) {
            context.fail(405); // Must be a POST
        } else {
            if (!req.isExpectMultipart()) {
                throw new IllegalStateException("Form body not parsed - do you forget to include a BodyHandler?");
            }
            MultiMap params = req.formAttributes();
            String username = params.get(usernameParam);
            String password = params.get(passwordParam);
            String clientId = params.get(Parameters.CLIENT_ID);
            if (username == null || password == null) {
                log.warn("No username or password provided in form - did you forget to include a BodyHandler?");
                context.fail(400);
            } else if (clientId == null) {
                log.warn("No client id in form - did you forget to include client_id query parameter ?");
                context.fail(400);
            } else {
                Session session = context.session();

                // build authentication object with ip address and user agent
                JsonObject authInfo = new JsonObject()
                        .put("username", username)
                        .put("password", password)
                        .put(Claims.ip_address, RequestUtils.remoteAddress(req))
                        .put(Claims.user_agent, RequestUtils.userAgent(req))
                        .put(Parameters.CLIENT_ID, clientId);

                authProvider.authenticate(context, authInfo, res -> {
                    if (res.succeeded()) {
                        User user = res.result();
                        context.setUser(user);
                        if (session != null) {
                            // the user has upgraded from unauthenticated to authenticated
                            // session should be upgraded as recommended by owasp
                            session.regenerateId();

                            // Note : keep returnURLParam in session in case the user go to previous page
                            // String returnURL = session.remove(returnURLParam);
                            String returnURL = session.get(returnURLParam);
                            if (returnURL != null) {
                                // Now redirect back to the original url
                                doRedirect(req.response(), returnURL);
                                return;
                            }
                        }
                        // Either no session or no return url
                        if (directLoggedInOKURL != null) {
                            // Redirect to the default logged in OK page - this would occur
                            // if the user logged in directly at this URL without being redirected here first from another
                            // url
                            doRedirect(req.response(), directLoggedInOKURL);
                        } else {
                            // Just show a basic page
                            req.response().end(DEFAULT_DIRECT_LOGGED_IN_OK_PAGE);
                        }
                    } else {
                        handleException(context);
                    }
                });
            }
        }
    }

    private void doRedirect(HttpServerResponse response, String url) {
        response.putHeader(HttpHeaders.LOCATION, url).setStatusCode(302).end();
    }

    private void handleException(RoutingContext context) {
        final io.vertx.reactivex.core.http.HttpServerRequest req = new io.vertx.reactivex.core.http.HttpServerRequest(context.request());
        final HttpServerResponse resp = context.response();
        final String returnUrl = context.session().get(returnURLParam);

        // build login url with error message
        QueryStringDecoder queryStringDecoder = new QueryStringDecoder(returnUrl);
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.putAll(queryStringDecoder.parameters().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get(0))));
        parameters.put("error", "login_failed");
        String uri = UriBuilderRequest.resolveProxyRequest(req, req.uri(), parameters, true);
        doRedirect(resp, uri);
    }
}
