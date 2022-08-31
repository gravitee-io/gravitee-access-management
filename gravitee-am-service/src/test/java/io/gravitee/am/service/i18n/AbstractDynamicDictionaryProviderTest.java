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
import org.junit.Test;

import java.util.Collections;
import java.util.Locale;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AbstractDynamicDictionaryProviderTest {

    protected abstract DynamicDictionaryProvider provider();
    
    @Test
    public void shouldHasDictionaryFor() {
        I18nDictionary i18nDictionary = new I18nDictionary();
        i18nDictionary.setLocale("test-locale");
        provider().loadDictionary(i18nDictionary);
        assertTrue(provider().hasDictionaryFor(new Locale(i18nDictionary.getLocale())));
    }

    @Test
    public void shouldNotHasDictionaryFor() {
        I18nDictionary i18nDictionary = new I18nDictionary();
        i18nDictionary.setLocale("test-locale");
        provider().loadDictionary(i18nDictionary);
        assertFalse(provider().hasDictionaryFor(new Locale("unknown-locale")));
    }

    @Test
    public void shouldNotHasDictionaryFor_after_removal() {
        I18nDictionary i18nDictionary = new I18nDictionary();
        i18nDictionary.setLocale("test-locale");
        provider().loadDictionary(i18nDictionary);
        provider().removeDictionary(i18nDictionary.getLocale());
        assertFalse(provider().hasDictionaryFor(new Locale(i18nDictionary.getLocale())));
    }

    @Test
    public void shouldGetDictionaryFor() {
        I18nDictionary i18nDictionary = new I18nDictionary();
        i18nDictionary.setLocale("test-locale");
        i18nDictionary.setEntries(Collections.singletonMap("key", "value"));
        provider().loadDictionary(i18nDictionary);
        assertFalse(provider().getDictionaryFor(new Locale(i18nDictionary.getLocale())).isEmpty());
        assertTrue(provider().getDictionaryFor(new Locale(i18nDictionary.getLocale())).getProperty("key").equals("value"));
    }

    @Test
    public void shouldGetDictionaryFor_empty_entries() {
        I18nDictionary i18nDictionary = new I18nDictionary();
        i18nDictionary.setLocale("test-locale");
        provider().loadDictionary(i18nDictionary);
        assertTrue(provider().getDictionaryFor(new Locale(i18nDictionary.getLocale())).isEmpty());
    }

    @Test
    public void shouldGetDictionaryFor_locale_is_null() {
        I18nDictionary i18nDictionary = new I18nDictionary();
        i18nDictionary.setLocale("test-locale");
        i18nDictionary.setEntries(Collections.singletonMap("key", "value"));
        provider().loadDictionary(i18nDictionary);
        assertTrue(provider().getDictionaryFor(null).isEmpty());
    }
}
