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

package io.gravitee.am.gateway.handler.root.resources.handler.consent;

import io.vertx.core.Handler;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.springframework.core.env.Environment;

import java.util.List;

import static io.gravitee.am.common.utils.ConstantKeys.USER_CONSENT_IP_LOCATION;
import static io.gravitee.am.common.utils.ConstantKeys.USER_CONSENT_USER_AGENT;
import static io.gravitee.common.http.MediaType.APPLICATION_JSON;
import static io.vertx.reactivex.core.http.HttpHeaders.CONTENT_TYPE;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DataConsentHandler implements Handler<RoutingContext> {

    public static final String CONFIG_KEY_IMPLICIT_CONSENT_IP = "consent.ip";
    public static final String CONFIG_KEY_IMPLICIT_CONSENT_USER_AGENT = "consent.user-agent";
    private final boolean IMPLICIT_IP_CONSENT;
    private final boolean IMPLICIT_USER_AGENT_CONSENT;

    public static final List<String> CONSENT_KEYS = List.of(
            USER_CONSENT_IP_LOCATION,
            USER_CONSENT_USER_AGENT
    );

    private static final String DATA_CONSENT_ON = "on";

    public DataConsentHandler(Environment environment) {
        IMPLICIT_IP_CONSENT = environment.getProperty(CONFIG_KEY_IMPLICIT_CONSENT_IP, boolean.class, false);
        IMPLICIT_USER_AGENT_CONSENT = environment.getProperty(CONFIG_KEY_IMPLICIT_CONSENT_USER_AGENT, boolean.class, false);
    }

    @Override
    public void handle(RoutingContext context) {
        final HttpServerRequest request = context.request();
        if (context.session() != null) {
            context.session().put(USER_CONSENT_IP_LOCATION, IMPLICIT_IP_CONSENT);
            context.session().put(USER_CONSENT_USER_AGENT, IMPLICIT_USER_AGENT_CONSENT);

            CONSENT_KEYS.forEach(key -> ofNullable(request.params())
                    .filter(params -> params.contains(key))
                    .ifPresentOrElse(
                            params -> context.session().put(key, DATA_CONSENT_ON.equalsIgnoreCase(request.params().get(key))),
                            () -> handleBody(context, key)
                    )
            );
        }
        context.next();
    }

    private void handleBody(RoutingContext context, String key) {
        if (isContentTypeJson(context.request().headers())) {
            var body = context.getBodyAsJson();
            if (body != null && body.containsKey(key)) {
                context.session().put(key, DATA_CONSENT_ON.equalsIgnoreCase(body.getString(key)));
            }
        }
    }

    private boolean isContentTypeJson(MultiMap headers) {
        return nonNull(headers) &&
                APPLICATION_JSON.equalsIgnoreCase(headers.get(CONTENT_TYPE));
    }
}
