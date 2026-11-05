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
package io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.consent;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Domain;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.vertx.rxjava3.ext.web.RoutingContext;

import java.util.HashMap;
import java.util.Map;

import static io.gravitee.am.service.dataplane.user.activity.utils.ConsentUtils.canSaveIp;
import static io.gravitee.am.service.dataplane.user.activity.utils.ConsentUtils.canSaveUserAgent;

/**
 * The user as the actor of a consent audit, with the IP address and user agent when the user allows them to be kept.
 */
final class ConsentPrincipal {

    private ConsentPrincipal() {
    }

    static io.gravitee.am.identityprovider.api.User of(RoutingContext context, io.gravitee.am.model.User user, Domain domain) {
        DefaultUser authenticatedUser = new DefaultUser(user.getUsername());
        authenticatedUser.setId(user.getId());
        Map<String, Object> additionalInformation = new HashMap<>(user.getAdditionalInformation());
        if (canSaveIp(context)) {
            additionalInformation.put(Claims.IP_ADDRESS, RequestUtils.remoteAddress(context.request()));
        }
        if (canSaveUserAgent(context)) {
            additionalInformation.put(Claims.USER_AGENT, RequestUtils.userAgent(context.request()));
        }
        additionalInformation.put(Claims.DOMAIN, domain.getId());
        authenticatedUser.setAdditionalInformation(additionalInformation);
        return authenticatedUser;
    }
}
