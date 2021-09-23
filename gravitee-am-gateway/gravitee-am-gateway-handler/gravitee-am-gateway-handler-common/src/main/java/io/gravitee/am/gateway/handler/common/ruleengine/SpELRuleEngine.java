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

package io.gravitee.am.gateway.handler.common.ruleengine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Map;
import java.util.Optional;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SpELRuleEngine implements RuleEngine {

    private static final SpelExpressionParser SPEL_EXPRESSION_PARSER = new SpelExpressionParser();
    private static final Logger logger = LoggerFactory.getLogger(SpELRuleEngine.class);

    @Override
    public <E> E evaluate(String rule, Map<String, Object> parameters, Class<E> clazz, E defaultValue) {
        try {
            var expression = SPEL_EXPRESSION_PARSER.parseExpression(rule);
            var evaluationContext = new StandardEvaluationContext();
            evaluationContext.setVariables(parameters);
            return Optional.ofNullable(expression.getValue(evaluationContext, clazz)).orElse(defaultValue);
        } catch (ParseException | EvaluationException ex) {
            logger.debug("Unable to evaluate the following ruleExpression : {}", rule, ex);
            return defaultValue;
        }
    }
}
