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
package io.gravitee.am.gateway.handler.root.resources.endpoint.user.register;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.root.resources.endpoint.ParamUtils;
import io.gravitee.am.gateway.handler.root.service.response.RegistrationResponse;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.gravitee.common.http.HttpHeaders;
import io.vertx.core.Handler;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.core.http.HttpServerResponse;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class RegisterSubmissionEndpoint implements Handler<RoutingContext> {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public static final String GATEWAY_ENDPOINT_REGISTRATION_KEEP_PARAMS = "legacy.registration.keepParams";

    private final boolean keepParams;

    public RegisterSubmissionEndpoint(Environment environment) {
        this.keepParams = environment.getProperty(GATEWAY_ENDPOINT_REGISTRATION_KEEP_PARAMS, boolean.class, true);
    }

    @Override
    public void handle(RoutingContext context) {
        // prepare response
        final RegistrationResponse registrationResponse = context.get(ConstantKeys.REGISTRATION_RESPONSE_KEY);
        final MultiMap queryParams = RequestUtils.getCleanedQueryParams(context.request());

        // no redirect uri has been set, redirect to the default page
        if (registrationResponse.getRedirectUri() == null || registrationResponse.getRedirectUri().isEmpty()) {
            queryParams.set(ConstantKeys.SUCCESS_PARAM_KEY, "registration_succeed");
            String uri = UriBuilderRequest.resolveProxyRequest(context.request(), context.request().path(), queryParams, true);
            doRedirect(context.response(), uri);
            return;
        }
        // else, redirect to the custom redirect_uri
        var redirectTo = keepParams ? ParamUtils.appendQueryParameter(registrationResponse.getRedirectUri(), queryParams) : registrationResponse.getRedirectUri();
        doRedirect(context.response(), redirectTo);
    }

    private void doRedirect(HttpServerResponse response, String url) {
        response
                .putHeader(HttpHeaders.LOCATION, url)
                .setStatusCode(302)
                .end();
    }
}
