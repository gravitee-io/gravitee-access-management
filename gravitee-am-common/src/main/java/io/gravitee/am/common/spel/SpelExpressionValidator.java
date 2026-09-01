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
package io.gravitee.am.common.spel;

import lombok.CustomLog;
import org.springframework.expression.ParseException;
import org.springframework.expression.ParserContext;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;

@CustomLog
public class SpelExpressionValidator {

    private static final SpelExpressionParser EXPRESSION_PARSER = new SpelExpressionParser();

    private static final ParserContext TEMPLATE_CONTEXT = new TemplateParserContext("{#", "}");

    public static boolean parses(String expression) {
        if (expression == null) {
            return true;
        }
        try {
            EXPRESSION_PARSER.parseExpression(expression, TEMPLATE_CONTEXT);
            return true;
        } catch (ParseException e) {
            if(log.isDebugEnabled()) {
                log.debug("Parsing error", e);
            }
            return false;
        }
    }
}
