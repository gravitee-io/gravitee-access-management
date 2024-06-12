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
import io.gravitee.am.model.ChallengeSettings;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.UserActivityService;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.eventbus.EventBus;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static com.google.common.base.Strings.isNullOrEmpty;
import static io.gravitee.am.common.utils.ConstantKeys.GEOIP_KEY;
import static java.util.Objects.isNull;
import static java.util.Optional.ofNullable;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GeoIpHandler implements Handler<RoutingContext> {

    private static final String GEOIP_SERVICE = "service:geoip";
    private final Logger logger = LoggerFactory.getLogger(GeoIpHandler.class);
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
                .map(MFASettings::getChallenge)
                .map(ChallengeSettings::getChallengeRule)
                .orElse("");
        if ((!adaptiveRule.isEmpty() || userActivityService.canSaveUserActivity()) && isNull(routingContext.data().get(GEOIP_KEY))) {
            var ip = RequestUtils.remoteAddress(routingContext.request());
            if (!isNullOrEmpty(ip)) {
                getGeoIpData(routingContext, ip);
            } else {
                routingContext.next();
            }
        } else {
            routingContext.next();
        }
    }

    private void getGeoIpData(RoutingContext routingContext, String ip) {
        eventBus.<JsonObject>request(GEOIP_SERVICE, ip)
                .doOnSuccess(jsonObjectMessage -> routingContext.data().put(GEOIP_KEY, jsonObjectMessage.body().getMap()))
                .doOnError(error -> logger.debug("Plugin GeoIp is not available, message: {}", error.getMessage()))
                .onErrorComplete()
                .doFinally(routingContext::next)
                .subscribe();
    }

}
