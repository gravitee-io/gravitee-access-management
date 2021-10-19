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

package io.gravitee.am.gateway.handler.root.resources.handler.rememberdevice;

import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.RememberDeviceSettings;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.Objects;

import static io.gravitee.am.gateway.handler.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;
import static io.gravitee.am.gateway.handler.common.utils.ConstantKeys.DEVICE_ALREADY_EXISTS_KEY;
import static io.gravitee.am.gateway.handler.manager.deviceidentifiers.DeviceIdentifierManagerImpl.REMEMBER_DEVICE_IS_ACTIVE;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.util.Objects.isNull;
import static java.util.Optional.ofNullable;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RememberDeviceHandler implements Handler<RoutingContext> {

    public RememberDeviceHandler() {
    }

    @Override
    public void handle(RoutingContext routingContext) {
        HttpServerRequest req = routingContext.request();
        switch (req.method().name()) {
            case "GET":
                handleGetRememberDevice(routingContext);
                break;
            case "POST":
                routingContext.next();
                break;
            default:
                routingContext.fail(405);
        }
    }

    private void handleGetRememberDevice(RoutingContext routingContext) {
        final Client client = ofNullable(routingContext.<Client>get(CLIENT_CONTEXT_KEY)).orElse(new Client());
        var rememberDeviceSettings = getRememberDeviceSettings(client);
        routingContext.put(REMEMBER_DEVICE_IS_ACTIVE, rememberDeviceSettings.isActive());
        routingContext.put(DEVICE_ALREADY_EXISTS_KEY, TRUE.equals(routingContext.session().get(DEVICE_ALREADY_EXISTS_KEY)));
        if (rememberDeviceSettings.isActive() && FALSE.equals(routingContext.session().get(DEVICE_ALREADY_EXISTS_KEY))) {
            final Long expirationTimeSeconds = rememberDeviceSettings.getExpirationTimeSeconds();
            final Long consentTime = isNull(expirationTimeSeconds) ? 7200L : expirationTimeSeconds;
            routingContext.put(ConstantKeys.REMEMBER_DEVICE_CONSENT_TIME_SECONDS, consentTime);
        }
        routingContext.next();
    }

    private RememberDeviceSettings getRememberDeviceSettings(Client client) {
        return ofNullable(client.getMfaSettings()).filter(Objects::nonNull)
                .map(MFASettings::getRememberDevice)
                .orElse(new RememberDeviceSettings());
    }
}
