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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class FileSystemDictionaryProvider implements DictionaryProvider {
    public static final String DEFAULT_LOCALE = "default";

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final Map<String, Properties> propertiesMap;

    public FileSystemDictionaryProvider(String i18nDirectory) {
        final var directory = Paths.get(i18nDirectory).toFile();
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
                            String locale = DEFAULT_LOCALE;
                            if (localIndex > 0) {
                                final String[] s = file.getName().substring(localIndex + 1, suffixIndex).split("_");
                                if (s.length == 1) {
                                    locale = new Locale(s[0]).getLanguage();
                                } else {
                                    locale = new Locale(s[0], s[1]).toString();
                                }
                            }
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
    }

    public Properties getDictionaryFor(Locale locale) {
        if (locale != null) {
            return ofNullable(this.propertiesMap.get(locale.toString()))
                    .or(() -> ofNullable(this.propertiesMap.get(locale.getLanguage())))
                    .or(() -> ofNullable(this.propertiesMap.get(Locale.ENGLISH.getLanguage())))
                    .orElse(this.propertiesMap.get(DEFAULT_LOCALE));
        }
        return this.propertiesMap.get(DEFAULT_LOCALE);
    }

    @Override
    public boolean hasDictionaryFor(Locale locale) {
        return this.propertiesMap.containsKey(locale.toString()) || this.propertiesMap.containsKey(locale.getLanguage());
    }
}
