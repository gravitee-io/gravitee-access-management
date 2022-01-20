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
package io.gravitee.am.gateway.handler.root.resources.handler.login;

import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.gravitee.am.gateway.handler.root.resources.handler.login.LoginSocialAuthenticationHandler.SOCIAL_AUTHORIZE_URL_CONTEXT_KEY;
import static io.gravitee.am.gateway.handler.root.resources.handler.login.LoginSocialAuthenticationHandler.SOCIAL_PROVIDER_CONTEXT_KEY;
import static java.util.Optional.ofNullable;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginHideFormHandler implements Handler<RoutingContext> {

    private Domain domain;

    public LoginHideFormHandler(Domain domain) {
        this.domain = domain;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        final List<IdentityProvider> socialProviders = routingContext.get(SOCIAL_PROVIDER_CONTEXT_KEY);
        final LoginSettings loginSettings = LoginSettings.getInstance(domain, client);
        var optionalSettings = ofNullable(loginSettings).filter(Objects::nonNull);
        boolean isHideForm = optionalSettings.map(LoginSettings::isHideForm).orElse(false);

        // hide form option disabled, continue
        if (!isHideForm) {
            routingContext.next();
            return;
        }

        // no external provider, continue
        if (socialProviders == null) {
            routingContext.next();
            return;
        }

        // more than one external provider, continue
        if (socialProviders.size() != 1) {
            routingContext.next();
            return;
        }

        doRedirect(routingContext, socialProviders.get(0));
    }

    private void doRedirect(RoutingContext routingContext, IdentityProvider identityProvider) {
        Map<String, String> urls = routingContext.get(SOCIAL_AUTHORIZE_URL_CONTEXT_KEY);
        String redirectUrl = urls.get(identityProvider.getId());
        routingContext.response()
                .putHeader(io.vertx.core.http.HttpHeaders.LOCATION, redirectUrl)
                .setStatusCode(302)
                .end();
    }
}
