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

package io.gravitee.am.gateway.handler.root.resources.handler.geoip;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.UserActivityService;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.eventbus.EventBus;
import io.vertx.reactivex.ext.web.RoutingContext;
import java.util.Optional;

import static com.google.common.base.Strings.isNullOrEmpty;
import static io.gravitee.am.common.utils.ConstantKeys.GEOIP_KEY;
import static io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils.remoteAddress;
import static java.util.Objects.isNull;
import static java.util.Optional.ofNullable;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GeoIpHandler implements Handler<RoutingContext> {

    private static final String GEOIP_SERVICE = "service:geoip";
    private final EventBus eventBus;
    private final UserActivityService userActivityService;

    public GeoIpHandler(
            UserActivityService userActivityService,
            EventBus eventBus
    ) {
        this.eventBus = eventBus;
        this.userActivityService = userActivityService;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final Optional<Client> client = ofNullable(routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY));
        var adaptiveRule = client.map(Client::getMfaSettings)
                .map(MFASettings::getAdaptiveAuthenticationRule)
                .orElse("");
        if ((!adaptiveRule.isEmpty() || userActivityService.canSaveUserActivity()) && isNull(routingContext.data().get(GEOIP_KEY))) {
            var ip = remoteAddress(routingContext.request());
            if (!isNullOrEmpty(ip)) {
                getGeoipData(routingContext, ip);
            } else {
                routingContext.next();
            }
        } else {
            routingContext.next();
        }
    }

    private void getGeoipData(RoutingContext routingContext, String ip) {
        eventBus.<JsonObject>request(GEOIP_SERVICE, ip, message -> {
            if (message.succeeded()) {
                final JsonObject body = message.result().body();
                routingContext.data().put(GEOIP_KEY, body.getMap());
            }
            routingContext.next();
        });
    }

}
