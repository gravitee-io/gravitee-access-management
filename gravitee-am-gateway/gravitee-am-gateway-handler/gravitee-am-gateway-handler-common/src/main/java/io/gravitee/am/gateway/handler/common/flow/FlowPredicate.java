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
package io.gravitee.am.gateway.handler.common.flow;

import io.gravitee.el.TemplateEngine;
import io.gravitee.el.exceptions.ExpressionEvaluationException;
import io.gravitee.gateway.api.ExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Predicate;

import static org.springframework.util.StringUtils.isEmpty;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class FlowPredicate {
    private final Logger LOGGER = LoggerFactory.getLogger(FlowPredicate.class);

    private final TemplateEngine templateEngine;

    private final boolean value;

    private FlowPredicate() {
        this.value = true;
        this.templateEngine = null;
    }

    private FlowPredicate(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
        this.value = false;
    }

    public boolean evaluate(String expression) {
        try {
            return value ||
                    (templateEngine != null && (isEmpty(expression) || templateEngine.getValue(expression, boolean.class)));
        } catch (ExpressionEvaluationException e) {
            LOGGER.warn("Unable to evaluate the expression '{}' as a boolean value", expression, LOGGER.isDebugEnabled() ? e : null);
            return false;
        }
    }

    public static FlowPredicate alwaysTrue() {
        return new FlowPredicate();
    }

    public static FlowPredicate from(ExecutionContext executionContext) {
        return new FlowPredicate(executionContext.getTemplateEngine());
    }
}
