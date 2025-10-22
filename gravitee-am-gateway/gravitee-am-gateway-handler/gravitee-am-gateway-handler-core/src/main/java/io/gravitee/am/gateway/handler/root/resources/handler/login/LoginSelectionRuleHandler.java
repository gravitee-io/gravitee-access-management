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

import org.springframework.beans.factory.annotation.Autowired;

import io.gravitee.am.common.oidc.Parameters;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.identityprovider.api.SimpleAuthenticationContext;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.vertx.rxjava3.ext.web.RoutingContext;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.gravitee.am.common.utils.ConstantKeys.REMEMBER_ME_PARAM_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.SOCIAL_PROVIDER_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.USERNAME_PARAM_KEY;
import static io.gravitee.am.gateway.handler.root.resources.handler.login.LoginAuthenticationHandler.SOCIAL_AUTHORIZE_URL_CONTEXT_KEY;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginSelectionRuleHandler extends LoginAbstractHandler  {
    private final boolean fromIdentifierFirstLogin;

    @Autowired
    private Domain domain;

    public LoginSelectionRuleHandler(boolean fromIdentifierFirstLogin) {
        this.fromIdentifierFirstLogin = fromIdentifierFirstLogin;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);

        List<IdentityProvider> socialProviders = routingContext.get(SOCIAL_PROVIDER_CONTEXT_KEY);
        if ((socialProviders != null && !socialProviders.isEmpty())
                && (client.getIdentityProviders() != null && !client.getIdentityProviders().isEmpty())) {

        var socialProviderMap = socialProviders.stream().collect(Collectors.toMap(
                    IdentityProvider::getId, Function.identity()
            ));

            var context = new SimpleAuthenticationContext(new VertxHttpServerRequest(routingContext.request().getDelegate()), routingContext.data());
            context.setDomain(domain);
            var templateEngine = context.getTemplateEngine();
            var identityProvider = client.getIdentityProviders().stream()
                    .filter(appIdp -> socialProviderMap.containsKey(appIdp.getIdentity()))
                    .filter(appIdp -> evaluateIdPSelectionRule(appIdp, socialProviderMap.get(appIdp.getIdentity()), templateEngine))
                    .findFirst();

            if (identityProvider.isPresent()) {
                Map<String, String> urls = routingContext.get(SOCIAL_AUTHORIZE_URL_CONTEXT_KEY);
                UriBuilder uriBuilder = UriBuilder.fromHttpUrl(urls.get(identityProvider.get().getIdentity()));

                if (fromIdentifierFirstLogin) {
                    // encode login_hint parameter for external provider (Azure AD replace the '+' sign by a space ' ')
                    // we do not need to test the StaticEnvironmentProvider.sanitizeParametersEncoding() here as
                    // we are not relying on the UriBuilderRequest.resolveProxyRequest method...
                    uriBuilder.addParameter(Parameters.LOGIN_HINT,
                            UriBuilder.encodeURIComponent(routingContext.request().getParam(USERNAME_PARAM_KEY))
                    );
                    if (routingContext.request().getParam(REMEMBER_ME_PARAM_KEY) != null){
                        uriBuilder.addParameter(Parameters.REMEMBER_ME_HINT, UriBuilder.encodeURIComponent(routingContext.request().getParam(REMEMBER_ME_PARAM_KEY)));
                    }
                }

                doRedirect(routingContext, uriBuilder.buildString());
                return;
            }
        }

        routingContext.next();
    }
}
