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
package io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal;

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.auth.User;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.Objects;
import java.util.Optional;

import static io.gravitee.am.gateway.handler.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;
import static io.gravitee.am.gateway.handler.common.utils.ConstantKeys.USERNAME_PARAM_KEY;
import static java.util.Optional.ofNullable;
import static java.util.function.Predicate.not;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class FormIdentifierFirstLoginStep extends AuthenticationFlowStep {

    private final Domain domain;

    public FormIdentifierFirstLoginStep(Handler<RoutingContext> wrapper, Domain domain) {
        super(wrapper);
        this.domain = domain;
    }

    @Override
    public void execute(RoutingContext routingContext, AuthenticationFlowChain flow) {
        Optional<User> user = ofNullable(routingContext.user());
        Optional<Client> client = ofNullable(routingContext.<Client>get(CLIENT_CONTEXT_KEY)).filter(Objects::nonNull);
        boolean identifierFirstLoginEnabled = isIdentifierFirstLoginEnabled(client);
        Optional<String> username = getOptionalUsername(routingContext);
        if (user.isEmpty() && identifierFirstLoginEnabled && username.isEmpty()) {
            flow.exit(this);
        } else {
            flow.doNext(routingContext);
        }
    }

    private Boolean isIdentifierFirstLoginEnabled(Optional<Client> optionalClient) {
        return optionalClient.map(client -> LoginSettings.getInstance(domain, client))
                .map(LoginSettings::isIdentifierFirstEnabled)
                .orElse(false);
    }

    private Optional<String> getOptionalUsername(RoutingContext routingContext) {
        return ofNullable(routingContext.request().getParam(USERNAME_PARAM_KEY))
                .filter(Objects::nonNull)
                .filter(not(String::isEmpty));
    }
}
