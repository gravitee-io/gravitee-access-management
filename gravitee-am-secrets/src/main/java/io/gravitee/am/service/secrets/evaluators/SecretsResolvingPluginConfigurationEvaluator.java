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
package io.gravitee.am.service.secrets.evaluators;

import io.gravitee.am.plugins.handlers.api.core.PluginConfigurationEvaluator;
import io.gravitee.am.service.secrets.resolver.SecretResolver;
import io.gravitee.el.TemplateContext;
import io.gravitee.el.TemplateEngine;
import io.gravitee.secrets.api.annotation.Secret;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author GraviteeSource Team
 */
@Slf4j
@RequiredArgsConstructor
public class SecretsResolvingPluginConfigurationEvaluator implements PluginConfigurationEvaluator {

    public static final String SECRETS_VAR_NAME = "secrets";

    private final SecretResolver secretResolver;

    @Override
    public <T> void evaluate(T configuration) {
        evaluateConfigObject(configuration, createTemplateEngine(), new HashSet<>());
    }

    private TemplateEngine createTemplateEngine() {
        TemplateEngine templateEngine = TemplateEngine.templateEngine();

        TemplateContext templateContext = templateEngine.getTemplateContext();
        templateContext.setVariable(SECRETS_VAR_NAME, new RefBasedEvaluatedSecretMethods(secretResolver));

        return templateEngine;
    }

    private void evaluateConfigObject(Object obj, TemplateEngine templateEngine, Set<Object> visited) {
        if (obj == null || visited.contains(obj) || !isComplexType(obj)) {
            return;
        }
        visited.add(obj);

        ReflectionUtils.doWithFields(obj.getClass(), field -> {
            if (field.trySetAccessible()) {
                Object fieldValue = ReflectionUtils.getField(field, obj);
                if (isAnnotatedAsSecret(field)) {
                    evaluateAnnotatedField(field, obj, templateEngine);
                } else {
                    if (isComplexType(fieldValue)) {
                        evaluateConfigObject(fieldValue, templateEngine, visited);
                    } else if (fieldValue instanceof Collection<?> collection) {
                        collection.forEach(item -> evaluateConfigObject(item, templateEngine, visited));
                    } else if (fieldValue instanceof Map<?, ?> map) {
                        map.values().forEach(item -> evaluateConfigObject(item, templateEngine, visited));
                    }
                }
            }
        });
    }

    private void evaluateAnnotatedField(Field field, Object obj, TemplateEngine templateEngine) {
        try {
            String actualValue = (String) field.get(obj);
            if (actualValue == null) {
                log.debug("Field {} is null, skipping evaluation", field.getName());
                return;
            }
            String newValue = templateEngine.eval(actualValue, String.class).blockingGet();
            ReflectionUtils.setField(field, obj, newValue);
        } catch (Exception e) {
            log.error("Unable to evaluate secrets for field: {}", field.getName(), e);
        }
    }

    private static boolean isAnnotatedAsSecret(Field field) {
        return field.isAnnotationPresent(Secret.class);
    }

    private static boolean isComplexType(Object obj) {
        if (obj == null) {
            return false;
        }

        Class<?> clazz = obj.getClass();
        return !clazz.isPrimitive() &&
                !clazz.getName().startsWith("java.") &&
                !(obj instanceof String) &&
                !(obj instanceof Number) &&
                !(obj instanceof Boolean) &&
                !(obj instanceof Character);
    }

}
