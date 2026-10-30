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
package io.gravitee.am.gateway.handler.oauth2.resources.endpoint.device;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;

import static io.gravitee.am.gateway.handler.common.utils.ThymeleafDataHelper.generateData;

/**
 * Renders one of the device flow pages, resolving the per-application override of its template.
 *
 * @author GraviteeSource Team
 */
@CustomLog
@RequiredArgsConstructor
public class DeviceFlowPageRenderer {

    public static final String OUTCOME_CONTEXT_KEY = "outcome";

    private final ThymeleafTemplateEngine engine;
    private final Domain domain;

    public void renderCompletion(RoutingContext context, DeviceFlowOutcome outcome) {
        context.put(OUTCOME_CONTEXT_KEY, outcome.value());
        render(context, Template.DEVICE_COMPLETION);
    }

    public void render(RoutingContext context, Template template) {
        final Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        engine.render(generateData(context, domain, client), templateFileName(template, client))
                .subscribe(
                        buffer -> context.response()
                                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML)
                                .end(buffer),
                        throwable -> {
                            log.error("Unable to render the {} page", template.template(), throwable);
                            context.fail(throwable.getCause());
                        });
    }

    private String templateFileName(Template template, Client client) {
        return template.template() + (client != null ? "|" + client.getId() : "");
    }
}
