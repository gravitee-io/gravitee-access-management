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
package io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.model.EnrollmentSettings;
import io.gravitee.am.model.MFASettings;
import io.gravitee.am.model.RememberDeviceSettings;
import io.gravitee.am.model.oidc.Client;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static io.gravitee.am.common.utils.ConstantKeys.DEVICE_ALREADY_EXISTS_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.FULLY_AUTH_CLIENTS_KEY;
import static java.lang.Boolean.TRUE;
import static java.util.Optional.ofNullable;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MfaUtils {


    public static void updateStronglyAuthClient(RoutingContext routingContext) {
        Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        var clients = routingContext.session().get(ConstantKeys.STRONG_AUTH_CLIENTS_KEY);
        if (clients instanceof ArrayList<?>) {
            if (!((ArrayList<String>) clients).contains(client.getClientId())) {
                ((ArrayList<String>) clients).add(client.getClientId());
            }
        } else {
            List<String> clientIds = new ArrayList<>();
            clientIds.add(client.getClientId());

            routingContext.session().put(ConstantKeys.STRONG_AUTH_CLIENTS_KEY, clientIds);
        }
    }

    public static boolean isUserStronglyAuth(Client client, Session session) {
        var clients = session.get(ConstantKeys.STRONG_AUTH_CLIENTS_KEY);
        if (clients instanceof ArrayList<?>) {
            return ((ArrayList<String>) clients).contains(client.getClientId());
        }
        return false;
    }


    public static String getMfaStepUpRule(Client client) {
        return ofNullable(client.getMfaSettings()).orElse(new MFASettings()).getStepUpAuthenticationRule();
    }

    public static String getAdaptiveMfaStepUpRule(Client client) {
        return ofNullable(client.getMfaSettings()).orElse(new MFASettings()).getAdaptiveAuthenticationRule();
    }

    public static RememberDeviceSettings getRememberDeviceSettings(Client client) {
        return ofNullable(client.getMfaSettings()).filter(Objects::nonNull)
                .map(MFASettings::getRememberDevice)
                .orElse(new RememberDeviceSettings());
    }

    public static boolean deviceAlreadyExists(Session session) {
        return TRUE.equals(session.get(DEVICE_ALREADY_EXISTS_KEY));
    }

    public static EnrollmentSettings getEnrollmentSettings(Client client) {
        return ofNullable(client.getMfaSettings())
                .filter(Objects::nonNull)
                .map(MFASettings::getEnrollment)
                .orElse(new EnrollmentSettings());
    }

    public static boolean isFullyAuthClient(Client client, Session session) {
        var clients = session.get(FULLY_AUTH_CLIENTS_KEY);
        if (clients instanceof ArrayList<?>) {
            return ((ArrayList<String>) clients).contains(client.getClientId());
        }
        return false;
    }

    public static void setFullyAuthClient(RoutingContext context, Client client) {
        var clients = context.session().get(ConstantKeys.FULLY_AUTH_CLIENTS_KEY);
        if (clients instanceof ArrayList<?>) {
            if (!((ArrayList<String>) clients).contains(client.getClientId())) {
                ((ArrayList<String>) clients).add(client.getClientId());
            }

        } else {
            List<String> clientIds = new ArrayList<>();
            clientIds.add(client.getClientId());
            context.session().put(ConstantKeys.FULLY_AUTH_CLIENTS_KEY, clientIds);
        }
    }
}
