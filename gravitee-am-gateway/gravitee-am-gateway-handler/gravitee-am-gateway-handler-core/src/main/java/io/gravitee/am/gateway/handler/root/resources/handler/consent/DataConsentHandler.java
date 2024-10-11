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

import io.gravitee.am.gateway.handler.common.vertx.web.handler.AmContext;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.AmRequestHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.AmSession;
import io.vertx.core.Handler;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.apache.commons.lang3.function.BooleanConsumer;
import org.checkerframework.checker.units.qual.A;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static io.gravitee.am.common.utils.ConstantKeys.USER_CONSENT_IP_LOCATION;
import static io.gravitee.am.common.utils.ConstantKeys.USER_CONSENT_USER_AGENT;
import static io.gravitee.common.http.MediaType.APPLICATION_JSON;
import static io.vertx.rxjava3.core.http.HttpHeaders.CONTENT_TYPE;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DataConsentHandler extends AmRequestHandler {

    public static final String CONFIG_KEY_IMPLICIT_CONSENT_IP = "consent.ip";
    public static final String CONFIG_KEY_IMPLICIT_CONSENT_USER_AGENT = "consent.user-agent";
    private final boolean IMPLICIT_IP_CONSENT;
    private final boolean IMPLICIT_USER_AGENT_CONSENT;

    public static final Map<String, BiConsumer<AmSession, Boolean>> CONSENT_KEYS = Map.of(
            USER_CONSENT_IP_LOCATION, AmSession::setIpLocationConsent,
            USER_CONSENT_USER_AGENT, AmSession::setUserAgentConsent
    );

    private static final String DATA_CONSENT_ON = "on";

    public DataConsentHandler(Environment environment) {
        IMPLICIT_IP_CONSENT = environment.getProperty(CONFIG_KEY_IMPLICIT_CONSENT_IP, boolean.class, false);
        IMPLICIT_USER_AGENT_CONSENT = environment.getProperty(CONFIG_KEY_IMPLICIT_CONSENT_USER_AGENT, boolean.class, false);
    }

    @Override
    public void handle(AmContext context) {
        final HttpServerRequest request = context.request();
        if (context.session() != null) {
            // keep consent for IP & Agent along the session life, so put the value only if present
            context.session().setIpLocationConsent(IMPLICIT_IP_CONSENT, false);
            context.session().setUserAgentConsent(IMPLICIT_USER_AGENT_CONSENT, false);

            CONSENT_KEYS.forEach((key, setter) -> ofNullable(request.params())
                    .filter(params -> params.contains(key))
                    .ifPresentOrElse(
                            params -> {
                                var consentEnabled = DATA_CONSENT_ON.equalsIgnoreCase(request.params().get(key));
                                setter.accept(context.session(),  consentEnabled);
                            },
                            () -> handleBody(context, key, setter)
                    )
            );
        }
        context.next();
    }

    private void handleBody(AmContext context, String key, BiConsumer<AmSession, Boolean> consumer) {
        if (isContentTypeJson(context.request().headers())) {
            var body = context.body().asJsonObject();
            if (body != null && body.containsKey(key)) {
                var consent =DATA_CONSENT_ON.equalsIgnoreCase(body.getString(key));
                consumer.accept(context.session(), consent);
            }
        }
    }

    private boolean isContentTypeJson(MultiMap headers) {
        return nonNull(headers) &&
                APPLICATION_JSON.equalsIgnoreCase(headers.get(CONTENT_TYPE));
    }
}
