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
package io.gravitee.am.service.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.standard.StandardDialect;
import org.thymeleaf.templateresolver.StringTemplateResolver;

class RestrictedExpressionEvaluatorTest {

    private final TemplateEngine engine = engine();

    private static TemplateEngine engine() {
        StandardDialect dialect = new StandardDialect();
        dialect.setVariableExpressionEvaluator(new RestrictedExpressionEvaluator());
        TemplateEngine engine = new TemplateEngine();
        engine.setDialect(dialect);
        engine.setTemplateResolver(new StringTemplateResolver());
        return engine;
    }

    private String render(String template) {
        Context context = new Context();
        context.setVariable("name", "alice");
        context.setVariable("items", List.of("a", "b"));
        context.setVariable("map", Map.of("key", "value"));
        context.setVariable("param", Map.of("client_id", "c1"));
        return engine.process(template, context);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<p th:text=\"${name}\"></p>",
            "<p th:text=\"${name.toUpperCase()}\"></p>",
            "<p th:text=\"${items.get(0)}\"></p>",
            "<p th:text=\"${map['key']}\"></p>",
            "<p th:text=\"${param.client_id}\"></p>",
            "<p th:text=\"${name == 'alice' ? 'yes' : 'no'}\"></p>",
            "<p th:text=\"${#strings.isEmpty(name)}\"></p>",
            "<p th:if=\"${items != null and !items.isEmpty()}\">ok</p>",
            "<p>[[${name}]]</p>"
    })
    void should_render_legitimate_expressions(String template) {
        render(template);
    }

    @Test
    void should_render_expression_value() {
        assertEquals("<p>ALICE</p>", render("<p th:text=\"${name.toUpperCase()}\"></p>"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<p th:text=\"${new java.util.Date()}\"></p>",
            "<p th:text=\"${new com.example.Whatever()}\"></p>",
            "<p th:text=\"${@com.example.Whatever@run()}\"></p>",
            "<p th:text=\"${@com.example.Whatever@FIELD}\"></p>",
            "<p th:text=\"${name.getClass()}\"></p>",
            "<p th:text=\"${name.class}\"></p>",
            "<p th:text=\"${name['class']}\"></p>",
            "<p th:text=\"${name.getClass().getClassLoader()}\"></p>",
            "<p th:text=\"${name.getClass().forName('x')}\"></p>",
            "<p th:text=\"${map['cl' + 'ass']}\"></p>",
            "<p th:text=\"${map[param.client_id]}\"></p>",
            "<p th:text=\"${#context}\"></p>",
            "<p th:text=\"${#_memberAccess}\"></p>",
            "<p th:text=\"${name = 'x'}\"></p>",
            "<p th:text=\"${name instanceof java.lang.String}\"></p>",
            "<p>[[${new com.example.Whatever()}]]</p>",
            "<p th:text=\"${items.![#this.getClass()]}\"></p>"
    })
    void should_reject_dangerous_expressions(String template) {
        assertThrows(TemplateProcessingException.class, () -> render(template));
    }

    @Test
    void should_reject_expression_that_is_not_valid_ognl() {
        assertThrows(TemplateProcessingException.class, () -> render("<p th:text=\"${name.}\"></p>"));
    }
}
