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

package io.gravitee.am.gateway.handler.root.resources.handler.user.activity;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserActivity.Type;
import io.gravitee.am.service.UserActivityService;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.gravitee.am.common.utils.ConstantKeys.GEOIP_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.LOGIN_ATTEMPT_KEY;
import static io.gravitee.am.service.impl.user.activity.utils.IPUtils.canSaveIp;
import static io.gravitee.am.service.impl.user.activity.utils.IPUtils.canSaveUserAgent;
import static java.util.Optional.ofNullable;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserActivityHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(UserActivityHandler.class);
    private final UserActivityService userActivityService;

    public UserActivityHandler(UserActivityService userActivityService) {
        this.userActivityService = userActivityService;
    }

    @Override
    public void handle(RoutingContext context) {
        var optionalUser = ofNullable(context.user())
                .filter(__ -> userActivityService.canSaveUserActivity())
                .map(u -> (io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) u.getDelegate())
                .map(io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User::getUser);
        
        optionalUser.ifPresentOrElse(
                user -> saveUserActivity(context, user),
                context::next
        );
    }

    private void saveUserActivity(RoutingContext context, User user) {
        var data = new HashMap<String, Object>();
        addGeoIP(context, data);
        addUserAgent(context, data);
        addLoginAttempt(context, data);

        userActivityService.save(user.getReferenceId(), user.getId(), Type.LOGIN, data)
                .doOnComplete(() -> logger.debug("User Activity saved successfully"))
                .doOnError(err -> logger.error("An unexpected error has occurred '{}'", err.getMessage(), err))
                .doFinally(context::next)
                .subscribe();
    }

    private void addLoginAttempt(RoutingContext context, HashMap<String, Object> data) {
        if (context.session() != null) {
            data.put(LOGIN_ATTEMPT_KEY, context.session().get(LOGIN_ATTEMPT_KEY));
        }
    }

    private void addUserAgent(RoutingContext context, HashMap<String, Object> data) {
        final String userAgent = RequestUtils.userAgent(context.request());
        if (canSaveUserAgent(context) && userAgent != null) {
            data.put(Claims.user_agent, userAgent);
        }
    }

    private void addGeoIP(RoutingContext context, HashMap<String, Object> data) {
        if (canSaveIp(context) && context.get(GEOIP_KEY) != null) {
            data.putAll(context.get(GEOIP_KEY));
        }
    }
}
