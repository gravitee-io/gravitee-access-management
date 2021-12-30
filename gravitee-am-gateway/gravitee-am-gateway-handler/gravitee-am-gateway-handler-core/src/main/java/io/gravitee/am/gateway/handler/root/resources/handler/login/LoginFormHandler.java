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
package io.gravitee.am.gateway.handler.root.resources.handler.login;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.UserAuthProvider;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.gravitee.am.common.utils.ConstantKeys.PASSWORD_PARAM_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.USERNAME_PARAM_KEY;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginFormHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(LoginFormHandler.class);

    private final UserAuthProvider authProvider;

    public LoginFormHandler(UserAuthProvider authProvider) {
        this.authProvider = authProvider;
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
            String username = params.get(USERNAME_PARAM_KEY);
            String password = params.get(PASSWORD_PARAM_KEY);
            String clientId = params.get(Parameters.CLIENT_ID);
            if (username == null || password == null) {
                logger.warn("No username or password provided in form - did you forget to include a BodyHandler?");
                context.fail(400);
            } else if (clientId == null) {
                logger.warn("No client id in form - did you forget to include client_id query parameter ?");
                context.fail(400);
            } else {
                // build authentication object with ip address and user agent
                JsonObject authInfo = new JsonObject()
                        .put(USERNAME_PARAM_KEY, username)
                        .put(PASSWORD_PARAM_KEY, password)
                        .put(Claims.ip_address, RequestUtils.remoteAddress(req))
                        .put(Claims.user_agent, RequestUtils.userAgent(req))
                        .put(Parameters.CLIENT_ID, clientId);

                authProvider.authenticate(context, authInfo, res -> {
                    if (res.failed()) {
                        logger.debug("An error has occurred during the authentication process", res.cause());
                        context.fail(res.cause());
                        return;
                    }
                    // authentication success
                    // set user into the context and continue
                    final User result = res.result();
                    context.getDelegate().setUser(result);
                    context.put(ConstantKeys.USER_CONTEXT_KEY, result.getUser());
                    context.next();
                });
            }
        }
    }
}
