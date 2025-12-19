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
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.gravitee.common.http.MediaType;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.resolveProxyRequest;

/**
 * @author GraviteeSource Team
 */
public class RememberedLoginPageHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(RememberedLoginPageHandler.class);

    private final ThymeleafTemplateEngine engine;
    private final DeviceIdentifierManager deviceIdentifierManager;

    public RememberedLoginPageHandler(ThymeleafTemplateEngine engine, DeviceIdentifierManager deviceIdentifierManager) {
        this.engine = engine;
        this.deviceIdentifierManager = deviceIdentifierManager;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);

        // add context data
        Map<String, Object> data = new HashMap<>(routingContext.data());
        final MultiMap queryParams = RequestUtils.getCleanedQueryParams(routingContext.request());
        data.put(ConstantKeys.ACTION_KEY, resolveProxyRequest(routingContext.request(), routingContext.request().path(), queryParams, true));
        data.putAll(deviceIdentifierManager.getTemplateVariables(client));

        // render the page
        engine.render(data, "remembered_login")
                .subscribe(buffer -> {
                    routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML);
                    routingContext.response().end(buffer);
                }, error -> {
                    logger.error("Unable to render remembered login page", error);
                    routingContext.fail(error);
                });
    }
}


