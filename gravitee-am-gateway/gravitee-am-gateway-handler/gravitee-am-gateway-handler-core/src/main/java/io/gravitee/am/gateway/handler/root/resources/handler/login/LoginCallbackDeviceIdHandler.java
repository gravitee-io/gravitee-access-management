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

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.manager.deviceidentifiers.DeviceIdentifierManager;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.core.Handler;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static io.gravitee.am.common.utils.ConstantKeys.ACTION_KEY;
import static io.gravitee.am.gateway.handler.common.vertx.utils.RequestUtils.getCleanedQueryParams;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.resolveProxyRequest;
import static java.lang.Boolean.TRUE;

/**
 * Handler to fetch the device ID for a social / OpenID authentication
 * The user will be redirected to a temporary HTML page with the Device Identifier Javascript to collect the data
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginCallbackDeviceIdHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(LoginCallbackDeviceIdHandler.class);
    private final ThymeleafTemplateEngine engine;
    private final DeviceIdentifierManager deviceIdentifierManager;

    public LoginCallbackDeviceIdHandler(ThymeleafTemplateEngine engine,
                                        DeviceIdentifierManager deviceIdentifierManager) {
        this.engine = engine;
        this.deviceIdentifierManager = deviceIdentifierManager;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        // remember device feature is disabled, continue
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        if (!TRUE.equals(routingContext.get(ConstantKeys.REMEMBER_DEVICE_IS_ACTIVE))) {
            routingContext.next();
            return;
        }

        // add context data
        Map<String, Object> data = new HashMap<>(routingContext.data());
        final MultiMap queryParams = getCleanedQueryParams(routingContext.request());
        data.put(ACTION_KEY, resolveProxyRequest(routingContext.request(), routingContext.request().path(), queryParams, true));
        data.putAll(deviceIdentifierManager.getTemplateVariables(client));

        // render the page
        engine.render(data, "login_callback_device_identifier")
                .subscribe(buffer -> {
                    routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML);
                    routingContext.response().end(buffer);
                }, error -> {
                    logger.error("Unable to render login callback device identifier page", error);
                    routingContext.fail(error);
                });
    }
}
