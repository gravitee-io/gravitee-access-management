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
package io.gravitee.am.factor.api;

import io.gravitee.am.gateway.handler.common.vertx.core.http.VertxHttpServerRequest;
import io.gravitee.am.gateway.handler.context.EvaluableRequest;
import io.gravitee.el.TemplateContext;
import io.gravitee.el.TemplateEngine;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.Session;
import org.springframework.context.ApplicationContext;

import java.util.HashMap;
import java.util.Map;

import static io.gravitee.am.gateway.handler.common.utils.RoutingContextHelper.getEvaluableAttributes;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class FactorContext {
    public static final String KEY_ENROLLED_FACTOR = "enrolledFactor";
    public static final String KEY_CODE = "code";

    private final ApplicationContext appContext;
    private final RoutingContext routingContext;
    private final Map<String, Object> data;
    private TemplateEngine templateEngine;

    public FactorContext(ApplicationContext appContext, RoutingContext routingContext, Map<String, Object> data) {
        this.routingContext = routingContext;
        this.appContext = appContext;
        this.data = data;
    }

    public <T> T getComponent(Class<T> componentClass) {
        return appContext.getBean(componentClass);
    }

    public Map<String, Object> getData() {
        return data;
    }

    public <T> T getData(String key, Class<T> type) {
        if (data != null) {
            return type.cast(getData().get(key));
        }
        return null;
    }

    public <V> void registerData(String key, V value) {
        this.data.put(key, value);
    }

    public Session getSession() {
        return routingContext.session();
    }

    public TemplateEngine getTemplateEngine() {
        if (templateEngine == null) {
            templateEngine = TemplateEngine.templateEngine();

            TemplateContext templateContext = templateEngine.getTemplateContext();
            templateContext.setVariable("request", new EvaluableRequest(new VertxHttpServerRequest(routingContext.request().getDelegate())));
            templateContext.setVariable("context", new EvaluableFactorContext(this));

        }

        return templateEngine;
    }

    Map<String, Object> getAttributes() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.putAll(this.getData());
        attributes.putAll(getEvaluableAttributes(this.routingContext));
        attributes.put("user", ((io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User) routingContext.user().getDelegate()).getUser());
        return attributes;
    }
}

class EvaluableFactorContext {
    private final FactorContext evaluatedContext;

    public EvaluableFactorContext(FactorContext evaluatedContext) {
        this.evaluatedContext = evaluatedContext;
    }

    public Map<String, Object> getAttributes() {
        return evaluatedContext.getAttributes();
    }
}
