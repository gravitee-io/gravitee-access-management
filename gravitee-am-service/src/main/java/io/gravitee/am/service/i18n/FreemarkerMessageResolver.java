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

import freemarker.template.SimpleDate;
import freemarker.template.SimpleNumber;
import freemarker.template.SimpleScalar;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModelException;

import java.util.List;
import java.util.Properties;

import static java.text.MessageFormat.format;
import static java.util.Optional.ofNullable;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class FreemarkerMessageResolver implements TemplateMethodModelEx {
    public static final String METHOD_NAME = "msg";
    private final Properties messages;

    public FreemarkerMessageResolver(Properties messages) {
        this.messages = messages;
    }

    @Override
    public Object exec(List list) throws TemplateModelException {
        if (!list.isEmpty()) {
            var key = list.get(0).toString();
            try {
                return ofNullable(messages.getProperty(key))
                        // double the single quote to avoid erasure by MessageFormatter.format
                        .map(msg -> msg.replace("'", "''"))
                        .map(msg -> format(msg, toArguments(list)))
                        .orElse(key);
            } catch (Exception e) {
                throw new TemplateModelException("Unable to format i18n message", e);
            }
        } else {
            return null;
        }
    }

    private Object[] toArguments(List list) {
        if (list.size() >= 2) {
            return list.subList(1, list.size())
                    .stream()
                    .map(this::convert)
                    .toArray();
        } else {
            return new Object[0];
        }
    }

    private Object convert(Object input) {
        if (input != null) {
            if (input instanceof SimpleScalar) {
                return ((SimpleScalar) input).getAsString();
            }
            if (input instanceof SimpleNumber) {
                return ((SimpleNumber) input).getAsNumber();
            }
            if (input instanceof SimpleDate) {
                return ((SimpleDate) input).getAsDate();
            }
        }
        return input;
    }
}