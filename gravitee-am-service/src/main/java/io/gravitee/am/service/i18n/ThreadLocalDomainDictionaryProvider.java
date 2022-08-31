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

import io.gravitee.am.model.I18nDictionary;

import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import static java.util.Optional.ofNullable;

/**
 * DictionaryProvider that allow the usage of a dictionary in an ephemeral context (REST Call).
 * This provider is used to provide the i18n dictionary in order to allow preview in ManagementAPI with right translation.
 *
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ThreadLocalDomainDictionaryProvider implements DynamicDictionaryProvider {

    private ThreadLocal<Map<String, Properties>> propertiesMap = new ThreadLocal<>();

    @Override
    public Properties getDictionaryFor(Locale locale) {
        final Map<String, Properties> map = this.propertiesMap.get();
        if (locale != null && map != null) {
            return ofNullable(map.get(locale.toString()))
                    .or(() -> ofNullable(map.get(locale.getLanguage())))
                    .orElse(new Properties());
        }
        return new Properties();
    }

    @Override
    public boolean hasDictionaryFor(Locale locale) {
        final Map<String, Properties> map = this.propertiesMap.get();
        return map != null && map.containsKey(locale.toString());
    }

    public void loadDictionary(I18nDictionary i18nDictionary) {
        final String locale = i18nDictionary.getLocale();
        Properties properties = new Properties();
        properties.putAll(i18nDictionary.getEntries());
        propertiesMap.set(Map.of(locale, properties));
    }

    public void removeDictionary(String locale) {
        propertiesMap.remove();
    }
}
