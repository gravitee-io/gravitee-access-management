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

import com.google.common.net.HttpHeaders;
import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.gravitee.am.gateway.handler.vertx.utils.UriBuilderRequest;
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

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

/**
 * Extends default {@link io.vertx.ext.web.handler.FormLoginHandler} and appends
 * {@link io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants#CLIENT_ID} to the AuthInfo to retrieve client identity providers
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
    private AuthProvider authProvider;

    public FormLoginHandlerImpl(AuthProvider authProvider, String usernameParam, String passwordParam, String returnURLParam, String directLoggedInOKURL) {
        super(authProvider, usernameParam, passwordParam, returnURLParam, directLoggedInOKURL);
        this.usernameParam = usernameParam;
        this.passwordParam = passwordParam;
        this.authProvider = authProvider;
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
            String clientId = params.get(OAuth2Constants.CLIENT_ID);
            if (username == null || password == null) {
                log.warn("No username or password provided in form - did you forget to include a BodyHandler?");
                context.fail(400);
            } else if (clientId == null) {
                log.warn("No client id in form - did you forget to include client_id query parameter ?");
                context.fail(400);
            } else {
                Session session = context.session();
                JsonObject authInfo = new JsonObject().put("username", username).put("password", password).put(OAuth2Constants.CLIENT_ID, clientId);
                authProvider.authenticate(authInfo, res -> {
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
                        try {
                            Map<String, String> parameters = new HashMap<>();
                            parameters.put(OAuth2Constants.CLIENT_ID, clientId);
                            parameters.put("error", "login_failed");
                            String uri = UriBuilderRequest.resolveProxyRequest(
                                    new io.vertx.reactivex.core.http.HttpServerRequest(req),
                                    req.uri(),
                                    parameters);

                            doRedirect(context.response(), uri);
                        } catch (URISyntaxException e) {
                            context.fail(503);
                        }
                    }
                });
            }
        }
    }

    private void doRedirect(HttpServerResponse response, String url) {
        response.putHeader(HttpHeaders.LOCATION, url).setStatusCode(302).end();
    }
}
