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

package io.gravitee.am.service.impl.user.activity.utils;

import io.vertx.reactivex.ext.web.RoutingContext;

import static io.gravitee.am.common.utils.ConstantKeys.USER_CONSENT_IP_LOCATION;
import static io.gravitee.am.common.utils.ConstantKeys.USER_CONSENT_USER_AGENT;
import static java.lang.Boolean.TRUE;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IPUtils {

    private IPUtils(){}

    public static boolean canSaveIp(RoutingContext context) {
        return TRUE.equals(context.session().get(USER_CONSENT_IP_LOCATION));
    }

    public static boolean canSaveUserAgent(RoutingContext context) {
        return TRUE.equals(context.session().get(USER_CONSENT_USER_AGENT));
    }
}
