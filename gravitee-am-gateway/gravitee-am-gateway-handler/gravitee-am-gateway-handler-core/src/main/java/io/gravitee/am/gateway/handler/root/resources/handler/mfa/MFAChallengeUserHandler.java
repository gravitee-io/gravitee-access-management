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
package io.gravitee.am.gateway.handler.root.resources.handler.mfa;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.gravitee.am.common.utils.ConstantKeys.ENROLLED_FACTOR_INIT_REGISTRATION;
import static io.gravitee.am.common.utils.ConstantKeys.MFA_ENROLLMENT_COMPLETED_KEY;

/**
 * Handler to retrieve the user from the context
 *
 * MFA Challenge can be triggered via the login flow or the MFA Challenge policy
 *
 * In the second option, the user is not yet in the session but lies in the "token" query parameter
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MFAChallengeUserHandler implements Handler<RoutingContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MFAChallengeUserHandler.class);

    private final UserService userService;

    public MFAChallengeUserHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        Boolean enrollmentCompleted = routingContext.session().get(MFA_ENROLLMENT_COMPLETED_KEY);
        routingContext.put(ENROLLED_FACTOR_INIT_REGISTRATION, Boolean.TRUE.equals(enrollmentCompleted));

        // user already signed in, continue
        if (routingContext.user() != null) {
            routingContext.next();
            return;
        }

        final String token = routingContext.request().getParam(ConstantKeys.TOKEN_PARAM_KEY);
        // no token, continue
        if (token == null) {
            routingContext.next();
            return;
        }

        // decode the token and the user in the context
        userService.verifyToken(token)
                .subscribe(
                        userToken -> {
                            // the user is put in the context and not set (routingContext.setUser(...))
                            // to not automatically sign in the user and store it in the session
                            routingContext.put(ConstantKeys.USER_CONTEXT_KEY, userToken.getUser());
                            routingContext.next();
                        },
                        error -> {
                            LOGGER.error("Unable to extract user from the token", error);
                            routingContext.next();
                        },
                        () -> {
                            LOGGER.error("No user found from the token");
                            routingContext.next();
                        }
                );

    }
}
