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
import io.gravitee.am.model.UserActivity.Type;
import io.gravitee.am.service.UserActivityService;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.gravitee.am.common.utils.ConstantKeys.*;
import static io.gravitee.am.service.impl.user.activity.utils.IPUtils.canSaveIp;
import static io.gravitee.am.service.impl.user.activity.utils.IPUtils.canSaveUserAgent;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginFormHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(LoginFormHandler.class);

    private final UserAuthProvider authProvider;
    private final UserActivityService userActivityService;

    public LoginFormHandler(
            UserAuthProvider authProvider,
            UserActivityService userActivityService
    ) {
        this.authProvider = authProvider;
        this.userActivityService = userActivityService;
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
                        .put(Parameters.CLIENT_ID, clientId);

                final String ipAddress = RequestUtils.remoteAddress(req);
                final String userAgent = RequestUtils.userAgent(req);

                if (canSaveIp(context)) {
                    authInfo.put(Claims.ip_address, ipAddress);
                }

                if (canSaveUserAgent(context)) {
                    authInfo.put(Claims.user_agent, userAgent);
                }

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
                    final io.gravitee.am.model.User user = result.getUser();
                    context.put(ConstantKeys.USER_CONTEXT_KEY, user);

                    if (userActivityService.canSaveUserActivity()) {
                        saveUserActivity(context, userAgent, user);
                    } else {
                        context.next();
                    }
                });
            }
        }
    }

    private void saveUserActivity(RoutingContext context, String userAgent, io.gravitee.am.model.User user) {
        var data = new HashMap<String, Object>();
        if (canSaveIp(context) && context.get(GEOIP_KEY) != null) {
            data.putAll(context.get(GEOIP_KEY));
        }
        if (canSaveUserAgent(context) && userAgent != null) {
            data.put(Claims.user_agent, userAgent);
        }
        if (context.session() != null) {
            data.put(LOGIN_ATTEMPT_KEY, context.session().get(LOGIN_ATTEMPT_KEY));
        }
        userActivityService.save(user.getReferenceId(), user.getId(), Type.LOGIN, data)
                .doOnComplete(() -> logger.debug("User Activity saved successfully"))
                .doOnError(err -> logger.error("An unexpected error has occurred '{}'", err.getMessage(), err))
                .doFinally(context::next)
                .subscribe();
    }
}
