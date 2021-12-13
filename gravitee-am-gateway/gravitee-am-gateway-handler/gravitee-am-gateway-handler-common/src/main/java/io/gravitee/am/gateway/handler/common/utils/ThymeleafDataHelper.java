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

import io.gravitee.am.model.User;
import io.gravitee.am.model.safe.ClientProperties;
import io.gravitee.am.model.safe.DomainProperties;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.safe.UserProperties;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ThymeleafDataHelper {

    public static Map<String, Object> generateData(RoutingContext context, Domain domain, Client client) {
        final Map<String, Object> data = new HashMap<>(context.data());
        if (domain != null) {
            data.put(ConstantKeys.DOMAIN_CONTEXT_KEY, new DomainProperties(domain));
        }
        if (client != null) {
            data.put(ConstantKeys.CLIENT_CONTEXT_KEY, new ClientProperties(client));
        }
        Object user = context.get(ConstantKeys.USER_CONTEXT_KEY); // context may contain User or UserProperties according to the execution path
        if (user != null && user instanceof User) {
            data.put(ConstantKeys.USER_CONTEXT_KEY, new UserProperties((User)user));
        }
        return data;
    }
}
