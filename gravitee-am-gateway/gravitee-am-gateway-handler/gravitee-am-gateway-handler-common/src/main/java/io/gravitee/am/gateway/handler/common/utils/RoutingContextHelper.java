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
package io.gravitee.am.gateway.handler.common.utils;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User;
import io.vertx.rxjava3.ext.web.RoutingContext;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.gravitee.am.common.utils.ConstantKeys.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RoutingContextHelper {
    private static final List<String> BLACKLIST_CONTEXT_ATTRIBUTES = Arrays.asList("X-XSRF-TOKEN", "_csrf", "__body-handled");
    private static final List<String> SESSION_ATTRIBUTES =
            Arrays.asList(
                    RISK_ASSESSMENT_KEY,
                    MFA_ENROLLMENT_COMPLETED_KEY,
                    ENROLLED_FACTOR_ID_KEY,
                    MFA_CHALLENGE_COMPLETED_KEY,
                    STRONG_AUTH_COMPLETED_KEY,
                    WEBAUTHN_CREDENTIAL_ID_CONTEXT_KEY,
                    PASSWORDLESS_AUTH_ACTION_KEY);

    /**
     * Return the {@link RoutingContext#data()} entries without technical attributes defined in {@link #BLACKLIST_CONTEXT_ATTRIBUTES}
     * If {@link RoutingContext#data()} doesn't contain {@link ConstantKeys#USER_CONTEXT_KEY}, then the {@link RoutingContext#user()} is added if present
     *
     * @param routingContext
     * @return
     */
    public static Map<String, Object> getEvaluableAttributes(RoutingContext routingContext) {
        Map<String, Object> contextData = new HashMap<>(routingContext.data());

        Object user = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);
        if (user != null) {
            contextData.put(ConstantKeys.USER_CONTEXT_KEY, user);
        } else if (routingContext.user() != null) {
            contextData.put(ConstantKeys.USER_CONTEXT_KEY, ((User) routingContext.user().getDelegate()).getUser());
        }

        if (routingContext.session() != null) {
            SESSION_ATTRIBUTES.forEach(attribute -> {
                if (routingContext.session().get(attribute) != null) {
                    contextData.put(attribute, routingContext.session().get(attribute));
                }
            });
        }

        // remove technical attributes
        BLACKLIST_CONTEXT_ATTRIBUTES.forEach(attribute -> contextData.remove(attribute));
        return contextData;
    }
}
