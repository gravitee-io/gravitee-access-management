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
package io.gravitee.am.gateway.handler.root.resources.endpoint.webauthn;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.root.resources.endpoint.AbstractEndpoint;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;

import static io.gravitee.am.gateway.handler.common.vertx.utils.RedirectHelper.getReturnUrl;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class WebAuthnRegisterPostEndpoint extends AbstractEndpoint implements Handler<RoutingContext>  {

    private final Domain domain;

    public WebAuthnRegisterPostEndpoint(Domain domain) {
        this.domain = domain;
    }

    @Override
    public void handle(RoutingContext ctx) {
        // support for potential cached javascript files
        // see https://github.com/gravitee-io/issues/issues/7158
        if (MediaType.APPLICATION_JSON.equals(ctx.request().getHeader(HttpHeaders.CONTENT_TYPE))) {
            registerV0(ctx);
            return;
        }

        // nominal case
        registerV1(ctx);
    }

    private void registerV0(RoutingContext ctx) {
        // at this stage the assertion has been generated
        // respond with the payload
        ctx.response()
                .putHeader(io.vertx.core.http.HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                .end(Json.encodePrettily(ctx.get(ConstantKeys.PASSWORDLESS_ASSERTION)));
    }

    private void registerV1(RoutingContext ctx) {
        // at this stage the registration has been done
        // redirect the user to the original request or device naming if the option has been enabled
        final Client client = ctx.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        final LoginSettings loginSettings = LoginSettings.getInstance(domain, client);
        final HttpServerRequest request = ctx.request();
        final MultiMap queryParams = RequestUtils.getCleanedQueryParams(request);
        String redirectUri = getReturnUrl(ctx, queryParams);

        if (loginSettings != null
                && loginSettings.isPasswordlessEnabled()
                && loginSettings.isPasswordlessDeviceNamingEnabled()) {
            String path = ctx.get(UriBuilderRequest.CONTEXT_PATH) + Template.WEBAUTHN_REGISTER_SUCCESS.redirectUri();
            redirectUri = UriBuilderRequest.resolveProxyRequest(request, path, queryParams, true);
        }

        ctx.response()
                .putHeader(io.vertx.core.http.HttpHeaders.LOCATION, redirectUri)
                .setStatusCode(302)
                .end();
    }
}
