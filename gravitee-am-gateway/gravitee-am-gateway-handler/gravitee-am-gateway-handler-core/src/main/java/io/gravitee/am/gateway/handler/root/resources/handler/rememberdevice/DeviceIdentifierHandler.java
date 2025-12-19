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

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.manager.deviceidentifiers.DeviceIdentifierManager;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.RememberDeviceSettings;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.DeviceService;
import io.reactivex.rxjava3.core.Maybe;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Strings.isNullOrEmpty;
import static io.gravitee.am.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.DEVICE_ALREADY_EXISTS_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.DEVICE_ID;
import static io.gravitee.am.common.utils.ConstantKeys.DEVICE_TYPE;
import static io.gravitee.am.common.utils.ConstantKeys.USER_CONTEXT_KEY;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class DeviceIdentifierHandler implements Handler<RoutingContext> {

    private final DeviceService deviceService;
    private final DeviceIdentifierManager deviceIdentifierManager;
    private final  JWTService jwtService;
    private final String rememberDeviceCookiName;

    public DeviceIdentifierHandler(DeviceService deviceService, DeviceIdentifierManager deviceIdentifierManager, JWTService jwtService, String rememberDeviceCookiName) {
        this.deviceIdentifierManager = deviceIdentifierManager;
        this.deviceService = deviceService;
        this.jwtService = jwtService;
        this.rememberDeviceCookiName = rememberDeviceCookiName;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final Client client = ofNullable(routingContext.<Client>get(CLIENT_CONTEXT_KEY)).orElse(new Client());
        final User user = getUser(routingContext);
        var rememberDeviceSettings = getRememberDeviceSettings(client);
        if (nonNull(user) && rememberDeviceSettings.isActive()) {
            checkIfDeviceExists(routingContext, client, user, rememberDeviceSettings);
        } else {
            routingContext.next();
        }
    }

    private User getUser(RoutingContext routingContext) {
        final User user = routingContext.get(USER_CONTEXT_KEY);
        if (user != null) {
            return user;
        }
        if (routingContext.user() != null
            && routingContext.user().getDelegate() instanceof io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User vertxUser) {
            return vertxUser.getUser();
        }
        return null;    
    }

    private RememberDeviceSettings getRememberDeviceSettings(Client client) {
        return ofNullable(client.getMfaSettings())
                .map(MFASettings::getRememberDevice)
                .orElse(new RememberDeviceSettings());
    }

    private void checkIfDeviceExists(RoutingContext routingContext, Client client, User user, RememberDeviceSettings rememberDeviceSettings) {
        final var deviceIdentifierId = rememberDeviceSettings.getDeviceIdentifierId();
        final var domain = client.getDomain();
        extractDeviceId(routingContext, client)
                .flatMap(deviceId -> this.deviceService.deviceExists(domain, client.getClientId(), user.getFullId(), deviceIdentifierId, deviceId)
                        .map(isEmpty -> {
                    routingContext.session().put(DEVICE_ID, deviceId);
                    if (!isEmpty) {
                        return Boolean.TRUE;
                    } else {
                        var deviceType = routingContext.request().getParam(DEVICE_TYPE);
                        routingContext.session().put(DEVICE_TYPE, deviceType);
                        return Boolean.FALSE;
                    }
                }).toMaybe())
                .switchIfEmpty(Maybe.just(Boolean.FALSE))
                .map(deviceExist -> {
                    routingContext.session().put(DEVICE_ALREADY_EXISTS_KEY, deviceExist);
                    return deviceExist;
                })
                .doFinally(routingContext::next)
                .subscribe();
    }

    private Maybe<String> extractDeviceId(RoutingContext routingContext, Client client) {
        var deviceId = routingContext.request().getParam(DEVICE_ID);
        final var deviceIdCookie = routingContext.request().getCookie(rememberDeviceCookiName);
        if (deviceIdentifierManager.useCookieBasedDeviceIdentifier(client) && deviceIdCookie != null) {
            return jwtService.decodeAndVerify(deviceIdCookie.getValue(), client, JWTService.TokenType.SESSION)
                    .map(JWT::getJti)
                    .toMaybe()
                    .onErrorResumeNext((err) -> {
                        log.debug("Remember device cookie validation fails for clientID '{}', fallback to the new deviceId", client.getClientId(), err);
                        // validation fail, remove the cookie to force new generation.
                        routingContext.response().removeCookie(rememberDeviceCookiName);
                        return isNullOrEmpty(deviceId) ? Maybe.empty() : Maybe.just(deviceId);
                    });
        } else {
            return isNullOrEmpty(deviceId) ? Maybe.empty() : Maybe.just(deviceId);
        }
    }
}
