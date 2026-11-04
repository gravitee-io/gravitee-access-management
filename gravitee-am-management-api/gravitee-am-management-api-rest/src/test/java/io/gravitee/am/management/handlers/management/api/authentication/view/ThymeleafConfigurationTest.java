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
package io.gravitee.am.management.handlers.management.api.authentication.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.gravitee.am.model.Form;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.spring6.SpringTemplateEngine;

class ThymeleafConfigurationTest {

    private final SpringTemplateEngine engine = new ThymeleafConfiguration().getTemplateEngine();

    private String render(String content) {
        Form form = new Form();
        form.setReferenceId("org");
        form.setTemplate("login");
        form.setContent(content);
        TemplateResolver resolver = (TemplateResolver) engine.getTemplateResolvers().iterator().next();
        resolver.addForm(form);
        Context context = new Context();
        context.setVariable("name", "alice");
        return engine.process(resolver.getTemplateKey(form), context);
    }

    @Test
    void should_render_custom_form_expressions() {
        assertEquals("<p>alice</p>", render("<p th:text=\"${name}\"></p>"));
    }

    @Test
    void should_reject_object_creation_in_custom_form() {
        assertThrows(TemplateProcessingException.class, () -> render("<p th:text=\"${new java.util.Date()}\"></p>"));
    }

    @Test
    void should_reject_reflective_access_in_custom_form() {
        assertThrows(TemplateProcessingException.class, () -> render("<p th:text=\"${name.getClass()}\"></p>"));
    }
}
