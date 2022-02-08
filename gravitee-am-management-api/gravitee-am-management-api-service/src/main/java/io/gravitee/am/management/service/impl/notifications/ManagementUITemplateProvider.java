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
package io.gravitee.am.management.service.impl.notifications;

import freemarker.cache.FileTemplateLoader;
import freemarker.core.TemplateClassResolver;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class ManagementUITemplateProvider implements InitializingBean {

    public static final String TEMPLATE_EXT = ".yml";

    @Value("${notifiers.ui.templates.path:${gravitee.home}/templates/notifications/management}")
    private String templatesPath;

    private final Configuration config = new Configuration(Configuration.VERSION_2_3_28);

    @Override
    public void afterPropertiesSet() throws IOException {
        config.setNewBuiltinClassResolver(TemplateClassResolver.SAFER_RESOLVER);
        config.setTemplateLoader(new FileTemplateLoader(new File(URLDecoder.decode(templatesPath, StandardCharsets.UTF_8))));
    }

    public String getNotificationContent(String name, Map<String, Object> parameters) throws IOException, TemplateException {
        String templateName = name + TEMPLATE_EXT;
        final Template template = config.getTemplate(templateName);
        StringWriter result = new StringWriter();
        template.process(parameters, result);
        return result.toString();
    }

}
