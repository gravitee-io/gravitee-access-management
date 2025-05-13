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
package io.gravitee.am.management.service.impl.notifications.template;

import freemarker.cache.FileTemplateLoader;
import freemarker.core.TemplateClassResolver;
import freemarker.template.Configuration;
import freemarker.template.Template;
import lombok.SneakyThrows;

import java.io.File;
import java.io.StringWriter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class FreemarkerTemplateProvider implements TemplateProvider{
    private final String templatesPath;
    private final String extension;
    private final Configuration config = new Configuration(Configuration.VERSION_2_3_28);

    public FreemarkerTemplateProvider(String templatesPath, String extension) {
        this.templatesPath = templatesPath;
        this.extension = extension;
        initSource();
    }

    @SneakyThrows
    private void initSource() {
        config.setNewBuiltinClassResolver(TemplateClassResolver.SAFER_RESOLVER);
        config.setTemplateLoader(new FileTemplateLoader(new File(URLDecoder.decode(templatesPath, StandardCharsets.UTF_8))));
    }

    @Override
    @SneakyThrows
    public String getNotificationContent(String name, Map<String, Object> parameters) {
        String templateName = name + extension;
        final Template template = config.getTemplate(templateName);
        StringWriter result = new StringWriter();
        template.process(parameters, result);
        return result.toString();
    }

    @Override
    @SneakyThrows
    public String getNotificationTemplate(String name) {
        String templateName = name + extension;
        final Template template = config.getTemplate(templateName);
        StringWriter writer = new StringWriter();
        template.dump(writer);
        return writer.toString();
    }
}
