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

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.thymeleaf.exceptions.TemplateProcessingException;

class FormContentValidatorTest {

    private final FormContentValidator validator = new FormContentValidator();

    @Test
    void should_accept_null_content() {
        validator.validate(null);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<form th:object=\"${command}\"><input th:field=\"*{username}\"/>[[${name}]]</form>",
            "<p data-th-text=\"${name.toUpperCase()}\"></p>",
            "<p th:with=\"label=${client.name}\" th:text=\"${label}\"></p>",
            "<p th:text=\"${#temporals.format(#temporals.createNow(), 'dd MMM yyyy')}\"></p>",
            "<p th:text=\"${'Show ${name} as-is'}\"></p>",
            "<p th:text=\"${'a}b'}\"></p>",
            "<p>[[${items[0][1]}]]</p>"
    })
    void should_accept_expressions_that_are_allowed(String content) {
        validator.validate(content);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<script>\n// js comment\n/*{example*/\nconst stamp = `${new Date().toISOString()}`;\n</script>",
            "<script>const m = [[1, 2], [3]]; const s = `${new Date()}`;</script>",
            "<style>*{box-sizing:border-box}</style>",
            "<!-- ${new java.util.Date()} -->",
            "<!--/* ${new java.util.Date()} */-->",
            "<p>${new java.util.Date()}</p>"
    })
    void should_accept_content_that_is_not_evaluated(String content) {
        validator.validate(content);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<p th:text=\"${new java.util.Date()}\"></p>",
            "<p data-th-text=\"${new java.util.Date()}\"></p>",
            "<p th:text=\"${@java.lang.Math@max(3, 7)}\"></p>",
            "<p th:text=\"${client.name = 'visitor'}\"></p>",
            "<p th:text=\"${param.username instanceof java.lang.String}\"></p>",
            "<p th:text=\"${('3 + 4')(0)}\"></p>",
            "<p th:text=\"${name.getClass()}\"></p>",
            "<input th:field=\"*{getClass()}\"/>",
            "<p th:text=\"${name}\"></p><p th:text=\"${new java.util.Date()}\"></p>",
            "<p th:text=\"${name\"></p>",
            "<p th:text=\"${name.}\"></p>",
            "<p>[[${new java.util.Date()}]]</p>",
            "<p>[(${new java.util.Date()})]</p>",
            "<script th:inline=\"javascript\">const v = /*[[${new java.util.Date()}]]*/ null;</script>",
            "<script>const v = '[[${new java.util.Date()}]]';</script>",
            "<style>.a::after{content:'[[${new java.util.Date()}]]'}</style>",
            "<!--/*/ <span th:text=\"${new java.util.Date()}\"></span> /*/-->"
    })
    void should_reject_expressions_that_are_not_allowed(String content) {
        assertThrows(TemplateProcessingException.class, () -> validator.validate(content));
    }

    @Test
    void shouldRejectInstantiationWrappedInEvalExpression() {
        assertThrows(TemplateProcessingException.class, () ->
                validator.validate("<p th:text=\"${('new com.example.Whatever()')(0)}\"></p>"));
    }
}
