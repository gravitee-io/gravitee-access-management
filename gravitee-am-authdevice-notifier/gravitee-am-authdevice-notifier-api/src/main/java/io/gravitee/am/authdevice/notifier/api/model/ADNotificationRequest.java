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
package io.gravitee.am.authdevice.notifier.api.model;

import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.context.EvaluableExecutionContext;
import io.gravitee.am.gateway.handler.context.EvaluableRequest;
import io.gravitee.el.TemplateContext;
import io.gravitee.el.TemplateEngine;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Set;

import static io.gravitee.am.gateway.handler.common.utils.RoutingContextUtils.getEvaluableAttributes;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Setter
@Getter
public class ADNotificationRequest {

    private static final String TEMPLATE_ATTRIBUTE_REQUEST = "request";
    private static final String TEMPLATE_ATTRIBUTE_CONTEXT = "context";

    private String deviceNotifierId;
    private String transactionId;
    private String subject;
    private int expiresIn;
    private Set<String> scopes;
    private List<String> acrValues;
    private String state;
    private String message;
    private RoutingContext context;

    private TemplateEngine templateEngine;

    public TemplateEngine getTemplateEngine() {
        if (templateEngine == null) {
            templateEngine = TemplateEngine.templateEngine();
            TemplateContext templateContext = templateEngine.getTemplateContext();
            templateContext.setVariable(TEMPLATE_ATTRIBUTE_REQUEST, new EvaluableRequest(new VertxHttpServerRequest(this.context.request().getDelegate())));
            templateContext.setVariable(TEMPLATE_ATTRIBUTE_CONTEXT, new EvaluableExecutionContext(getEvaluableAttributes(this.context)));
        }
        return templateEngine;
    }
}
