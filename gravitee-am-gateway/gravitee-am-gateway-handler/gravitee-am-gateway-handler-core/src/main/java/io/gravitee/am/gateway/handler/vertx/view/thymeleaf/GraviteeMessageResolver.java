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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.messageresolver.AbstractMessageResolver;
import org.thymeleaf.messageresolver.IMessageResolver;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GraviteeMessageResolver extends AbstractMessageResolver {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public static final String DEFAULT_LOCALE = "default";
    private final Map<String, Properties> propertiesMap;

    public GraviteeMessageResolver(String i18nLocation) {
        final File directory = Paths.get(i18nLocation).toFile();
        if (directory.exists() && directory.isDirectory()) {
            this.propertiesMap = Stream.of(directory.listFiles())
                    .filter(file -> !file.isDirectory())
                    .filter(file -> file.getName().startsWith("messages"))
                    .filter(file -> file.getName().endsWith(".properties"))
                    .map(file -> {
                        try (InputStream input = new FileInputStream(file)) {
                            Properties prop = new Properties();
                            prop.load(input);
                            final int localIndex = file.getName().indexOf("_");
                            final int suffixIndex = file.getName().indexOf(".");
                            final String locale = localIndex > 0 ? new Locale(file.getName().substring(localIndex + 1, suffixIndex)).getLanguage() : DEFAULT_LOCALE;
                            return Map.of(locale, prop);
                        } catch (IOException e) {
                            logger.debug("i18n File '{}' can't be loaded", file.getName(), e);
                            return Map.<String, Properties>of();
                        }
                    })
                    .map(Map::entrySet)
                    .flatMap(Set::stream)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        } else {
            this.propertiesMap = new HashMap<>();
        }

        // create empty default properties to avoid NPE during message lookup
        this.propertiesMap.putIfAbsent(DEFAULT_LOCALE, new Properties());

        this.setOrder(0);
    }

    public boolean isSupported(Locale locale) {
        // use the local.toString instead of local.getLanguage() to evaluate language linked to a country code (fr-FR / en-GB)
        return this.propertiesMap.containsKey(locale.toString());
    }

    @Override
    public String resolveMessage(ITemplateContext context, Class<?> origin, String key, Object[] messageParameters) {
        if (context.getLocale() != null) {
            return ofNullable(this.propertiesMap.get(context.getLocale().toString()))
                    .or(() -> ofNullable(this.propertiesMap.get(context.getLocale().getLanguage())))
                    .map(prop -> prop.getProperty(key))
                    .orElse(this.propertiesMap.get(DEFAULT_LOCALE).getProperty(key));
        }
        return this.propertiesMap.get(DEFAULT_LOCALE).getProperty(key);
    }

    @Override
    public String createAbsentMessageRepresentation(ITemplateContext context, Class<?> origin, String key, Object[] messageParameters) {
        // leave this method blank to let thymeleaf generate the default form
        return null;
    }
}
