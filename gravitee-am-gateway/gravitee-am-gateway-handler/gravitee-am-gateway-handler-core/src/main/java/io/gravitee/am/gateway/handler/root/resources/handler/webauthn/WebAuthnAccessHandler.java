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
package io.gravitee.am.gateway.handler.root.resources.handler.webauthn;

import io.gravitee.am.common.factor.FactorType;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.model.ApplicationFactorSettings;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.springframework.util.CollectionUtils;

import java.util.Set;

import static io.gravitee.am.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class WebAuthnAccessHandler implements Handler<RoutingContext> {

    private final Domain domain;
    private final FactorManager factorManager;

    public WebAuthnAccessHandler(Domain domain, FactorManager factorManager) {
        this.domain = domain;
        this.factorManager = factorManager;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        Client client = routingContext.get(CLIENT_CONTEXT_KEY);
        LoginSettings loginSettings = LoginSettings.getInstance(domain, client);
        if (loginSettings == null || (!loginSettings.isPasswordlessEnabled() && !hasFidoFactor(client))) {
            routingContext.fail(404);
            return;
        }
        routingContext.next();
    }

    private boolean hasFidoFactor(Client client) {
        if (client.getFactorSettings() == null || CollectionUtils.isEmpty(client.getFactorSettings().getApplicationFactors())) {
            return false;
        }

        return client.getFactorSettings()
                .getApplicationFactors()
                .stream()
                .map(ApplicationFactorSettings::getId)
                .filter(f -> factorManager.get(f) != null)
                .map(factorManager::getFactor)
                .anyMatch(f -> f.is(FactorType.FIDO2));
    }
}
