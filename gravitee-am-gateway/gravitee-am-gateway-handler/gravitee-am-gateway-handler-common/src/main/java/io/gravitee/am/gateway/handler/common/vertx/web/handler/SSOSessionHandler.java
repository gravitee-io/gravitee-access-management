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
package io.gravitee.am.gateway.handler.common.vertx.web.handler;

import io.gravitee.am.common.exception.authentication.AccountDisabledException;
import io.gravitee.am.gateway.handler.common.user.UserManager;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.auth.User;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SSO Session Handler to check if the user stored in the HTTP session is still "valid" upon the incoming request
 *
 * - a user is invalid if he is disabled
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SSOSessionHandler implements Handler<RoutingContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SSOSessionHandler.class);
    private UserManager userManager;

    SSOSessionHandler(UserManager userManager) {
        this.userManager = userManager;
    }

    @Override
    public void handle(RoutingContext context) {
        // if no user in context, continue
        if (context.user() == null) {
            context.next();
            return;
        }

        // retrieve end user and check if it's authorized to call the subsequence handlers
        User authenticatedUser = context.user();
        io.gravitee.am.model.User endUser = ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) authenticatedUser.getDelegate()).getUser();

        authorize(endUser, h -> {
            if (h.failed()) {
                Throwable cause = h.cause();
                LOGGER.debug("An error occurs while checking SSO Session upon the current user : {}", endUser.getId(), cause);
                if (cause instanceof AccountDisabledException) {
                    // user has been disabled, invalidate session
                    context.clearUser();
                    context.session().destroy();
                }
            }
            context.next();
        });

    }

    private void authorize(io.gravitee.am.model.User user, Handler<AsyncResult<Void>> handler) {
        userManager.get(user.getId())
                .subscribe(
                        user1 -> {
                            // if user is disabled, throw exception
                            if (!user1.isEnabled()) {
                                handler.handle(Future.failedFuture(new AccountDisabledException(user1.getId())));
                                return;
                            }
                            handler.handle(Future.succeededFuture());
                        },
                        error -> handler.handle(Future.failedFuture(error)),
                        () -> handler.handle(Future.succeededFuture()));
    }
}
