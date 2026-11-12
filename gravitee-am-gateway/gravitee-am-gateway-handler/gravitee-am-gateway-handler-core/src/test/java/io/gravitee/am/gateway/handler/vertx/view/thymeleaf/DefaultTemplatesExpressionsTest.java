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
package io.gravitee.am.gateway.handler.vertx.view.thymeleaf;

import static org.junit.jupiter.api.Assertions.assertFalse;

import io.gravitee.am.service.i18n.FormContentValidator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Custom forms are copies of the default templates, so every expression they contain must stay allowed.
 */
class DefaultTemplatesExpressionsTest {

    private static final Path VIEWS = Path.of("src/main/resources/webroot/views");

    private final FormContentValidator validator = new FormContentValidator();

    @Test
    void should_allow_every_variable_expression_of_the_default_templates() throws IOException {
        List<Path> templates;
        try (Stream<Path> files = Files.walk(VIEWS)) {
            templates = files.filter(p -> p.toString().endsWith(".html")).toList();
        }
        assertFalse(templates.isEmpty());
        for (Path template : templates) {
            try {
                validator.validate(Files.readString(template));
            } catch (RuntimeException e) {
                throw new AssertionError("A rejected expression was found in " + template, e);
            }
        }
    }
}
