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

import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.RememberDeviceSettings;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.DeviceService;
import io.reactivex.Maybe;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.Objects;

import static com.google.common.base.Strings.isNullOrEmpty;
import static io.gravitee.am.common.utils.ConstantKeys.*;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DeviceIdentifierHandler implements Handler<RoutingContext> {

    private final DeviceService deviceService;

    public DeviceIdentifierHandler(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final Client client = ofNullable(routingContext.<Client>get(CLIENT_CONTEXT_KEY)).orElse(new Client());
        final User user = routingContext.get(USER_CONTEXT_KEY);
        var rememberDeviceSettings = getRememberDeviceSettings(client);
        if (nonNull(user) && rememberDeviceSettings.isActive()) {
            checkIfDeviceExists(routingContext, client, user.getId(), rememberDeviceSettings);
        } else {
            routingContext.next();
        }
    }

    private RememberDeviceSettings getRememberDeviceSettings(Client client) {
        return ofNullable(client.getMfaSettings()).filter(Objects::nonNull)
                .map(MFASettings::getRememberDevice)
                .orElse(new RememberDeviceSettings());
    }

    private void checkIfDeviceExists(RoutingContext routingContext, Client client, String userId, RememberDeviceSettings rememberDeviceSettings) {
        var domain = client.getDomain();
        var deviceId = routingContext.request().getParam(DEVICE_ID);
        var deviceIdentifierId = rememberDeviceSettings.getDeviceIdentifierId();
        if (isNullOrEmpty(deviceId)) {
            routingContext.session().put(DEVICE_ALREADY_EXISTS_KEY, false);
            routingContext.next();
        } else {
            this.deviceService.deviceExists(domain, client.getClientId(), userId, deviceIdentifierId, deviceId).flatMapMaybe(isEmpty -> {
                routingContext.session().put(DEVICE_ID, deviceId);
                if (!isEmpty) {
                    routingContext.session().put(DEVICE_ALREADY_EXISTS_KEY, true);
                } else {
                    var deviceType = routingContext.request().getParam(DEVICE_TYPE);
                    routingContext.session().put(DEVICE_ALREADY_EXISTS_KEY, false);
                    routingContext.session().put(DEVICE_TYPE, deviceType);
                }
                return Maybe.just(true);
            }).doFinally(routingContext::next).subscribe();
        }
    }
}
