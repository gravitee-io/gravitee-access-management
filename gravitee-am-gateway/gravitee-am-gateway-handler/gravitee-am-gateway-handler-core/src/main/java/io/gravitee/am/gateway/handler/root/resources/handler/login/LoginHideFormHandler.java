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

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.gravitee.am.common.utils.ConstantKeys.*;
import static io.gravitee.am.gateway.handler.root.resources.handler.login.LoginSocialAuthenticationHandler.SOCIAL_AUTHORIZE_URL_CONTEXT_KEY;
import static java.util.Optional.ofNullable;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginHideFormHandler implements Handler<RoutingContext> {

    private final Domain domain;

    public LoginHideFormHandler(Domain domain) {
        this.domain = domain;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        final List<IdentityProvider> socialProviders = routingContext.get(SOCIAL_PROVIDER_CONTEXT_KEY);
        boolean isHideFormEnabled = ofNullable(LoginSettings.getInstance(domain, client))
                .map(LoginSettings::isHideForm)
                .orElse(false);
        boolean hasExactlyOneSocialProvider = socialProviders != null && socialProviders.size() == 1;

        boolean shouldSkipLoginForm = isHideFormEnabled && hasExactlyOneSocialProvider;
        // assume there's an error if any of the known error params is present
        boolean hasError = Stream.of(ERROR_PARAM_KEY, ERROR_CODE_PARAM_KEY, ERROR_DESCRIPTION_PARAM_KEY)
                .anyMatch(param -> !routingContext.queryParam(param).isEmpty());


        // hide form option disabled, continue
        if (!shouldSkipLoginForm) {
            routingContext.next();
            return;
        }

        if (hasError) {
            redirectWithError(routingContext);
        } else {
            redirectToProvider(routingContext, socialProviders.get(0));
        }
    }

    private void redirectToProvider(RoutingContext routingContext, IdentityProvider identityProvider) {
        Map<String, String> urls = routingContext.get(SOCIAL_AUTHORIZE_URL_CONTEXT_KEY);
        String redirectUrl = urls.get(identityProvider.getId());
        routingContext.response()
                .putHeader(io.vertx.core.http.HttpHeaders.LOCATION, redirectUrl)
                .setStatusCode(302)
                .end();
    }

    private void redirectWithError(RoutingContext context) {
        /* AM-3381: normally if we're at /login with an error, the form displays it.
               However, when the login form is hidden, we don't have a place to display it, and redirecting to the provider
               may cause a redirect loop. */

        var error = context.queryParam(ERROR_PARAM_KEY).stream().findFirst().orElse("");
        var errorCode = context.queryParam(ERROR_CODE_PARAM_KEY).stream().findFirst().orElse("");
        var errorDescription = context.queryParam(ERROR_DESCRIPTION_PARAM_KEY).stream().findFirst().orElse("");

        var redirectUri = context.request().getParam(Parameters.REDIRECT_URI);
        if (StringUtils.isBlank(redirectUri)) {
            context.fail(500, new IllegalStateException("Received an error but there's nowhere to redirect to. error=%s, error_code=%s, error_description=%s".formatted(error, errorCode, errorDescription)));
            return;
        }
        var targetUri = UriBuilder.fromURIString(redirectUri)
                .addParameterIfHasValue(ConstantKeys.ERROR_PARAM_KEY, error)
                .addParameterIfHasValue(ERROR_CODE_PARAM_KEY, errorCode)
                .addParameterIfHasValue(ERROR_DESCRIPTION_PARAM_KEY, UriBuilder.encodeURIComponent(errorDescription))
                .buildString();
        context.response()
                .putHeader(HttpHeaders.LOCATION, targetUri)
                .setStatusCode(302)
                .end();
    }
}
