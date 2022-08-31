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

import java.util.Locale;
import java.util.Properties;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface DictionaryProvider {
    /**
     * Get the Properties object with all i18n message translated for the given locale value.
     * If there are no properties matching the Locale, then the default translation are provided.
     *
     * @param locale
     * @return
     */
    Properties getDictionaryFor(Locale locale);

    /**
     * use the local.toString instead of local.getLanguage() to evaluate language linked to a country code (fr-FR / en-GB)
     *
     * @param locale
     * @return true if the locale has dictionary
     */
    boolean hasDictionaryFor(Locale locale);
}
