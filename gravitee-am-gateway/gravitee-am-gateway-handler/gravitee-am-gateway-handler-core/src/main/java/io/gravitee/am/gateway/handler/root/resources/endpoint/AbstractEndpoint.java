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
package io.gravitee.am.gateway.handler.root.resources.endpoint;

import io.gravitee.am.gateway.handler.manager.form.FormManager;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.common.template.TemplateEngine;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Optional;

/**
 * @author Boualem DJELAILI (boualem.djelaili at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractEndpoint {

    private final TemplateEngine templateEngine;

    protected AbstractEndpoint(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public abstract String getTemplateSuffix();

    protected final String getTemplateFileName(Client client) {
        return getTemplateSuffix() +
                Optional.ofNullable(client).map(c -> FormManager.TEMPLATE_NAME_SEPARATOR + c.getId()).orElse("");
    }

    protected final void copyValue(HttpServerRequest request, RoutingContext routingContext, String paramKey) {
        routingContext.put(paramKey, request.getParam(paramKey));
    }

    protected void renderPage(RoutingContext routingContext, Map<String, Object> data, Client client, Logger logger, String errorMessage) {
        this.renderPage(data, client, res -> {
            if (res.succeeded()) {
                routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML);
                routingContext.response().end(res.result());
            } else {
                logger.error(errorMessage, res.cause());
                routingContext.fail(res.cause());
            }
        });
    }

    protected void renderPage(Map<String, Object> data, Client client, Handler<AsyncResult<Buffer>> handler) {
        templateEngine.render(data, getTemplateFileName(client), handler);
    }
}
