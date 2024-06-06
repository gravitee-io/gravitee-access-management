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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CompositeDictionaryProviderTest {

    private DictionaryProvider directoryProvider;

    @Before
    public void initProvider() {
        DomainBasedDictionaryProvider mainProvider = new DomainBasedDictionaryProvider();
        final I18nDictionary mainI18nDictFR = new I18nDictionary();
        mainI18nDictFR.setLocale("fr");
        mainI18nDictFR.setEntries(Map.of("mainEntry", "value1FR", "overriddenEntry", "value2FR"));
        mainProvider.loadDictionary(mainI18nDictFR);
        final I18nDictionary mainI18nDictEN = new I18nDictionary();
        mainI18nDictEN.setLocale("en");
        mainI18nDictEN.setEntries(Map.of("mainEntry", "value1EN", "overriddenEntry", "value2EN"));
        mainProvider.loadDictionary(mainI18nDictEN);
        final I18nDictionary mainI18NDictDE = new I18nDictionary();
        mainI18NDictDE.setLocale("de");
        mainI18NDictDE.setEntries(Map.of("mainEntry", "value1DE", "overriddenEntry", "value2DE"));
        mainProvider.loadDictionary(mainI18NDictDE);

        DomainBasedDictionaryProvider secondaryProvider = new DomainBasedDictionaryProvider();
        final I18nDictionary secondaryI18nDictFR = new I18nDictionary();
        secondaryI18nDictFR.setLocale("fr");
        secondaryI18nDictFR.setEntries(Map.of("overriddenEntry", "shouldBeIgnoredFR", "onlySecondary", "secondaryFR"));
        secondaryProvider.loadDictionary(secondaryI18nDictFR);
        final I18nDictionary secondaryI18nDictEN = new I18nDictionary();
        secondaryI18nDictEN.setLocale("en");
        secondaryI18nDictEN.setEntries(Map.of("overriddenEntry", "shouldBeIgnoredEN", "onlySecondary", "secondaryEN"));
        secondaryProvider.loadDictionary(secondaryI18nDictEN);
        final I18nDictionary secondaryI18nDictJapanese = new I18nDictionary();
        secondaryI18nDictJapanese.setLocale("ja");
        secondaryI18nDictJapanese.setEntries(Map.of("overriddenEntry", "shouldBeIgnoredJAPANESE", "onlySecondary", "secondaryJAPANESE"));
        secondaryProvider.loadDictionary(secondaryI18nDictJapanese);

        this.directoryProvider = new CompositeDictionaryProvider(mainProvider, secondaryProvider);
    }

    @Test
    public void should_return_empty_if_local_not_supported_by_embedded_provider() {
        Properties prop = this.directoryProvider.getDictionaryFor(new Locale("it"));
        Assert.assertTrue("Should provide empty properties",  prop.isEmpty());
    }

    @Test
    public void should_fallback_to_secondary_entries() {
        Properties prop = this.directoryProvider.getDictionaryFor(new Locale("en", "GB"));
        Assert.assertEquals("Should contains the en message", "secondaryEN", prop.getProperty("onlySecondary"));

        prop = this.directoryProvider.getDictionaryFor(new Locale("fr", "FR"));
        Assert.assertEquals("Should contains the fr message", "secondaryFR", prop.getProperty("onlySecondary"));
    }

    @Test
    public void should_get_primary_entries() {
        Properties prop = this.directoryProvider.getDictionaryFor(new Locale("en", "GB"));
        Assert.assertEquals("Should contains the en message", "value1EN", prop.getProperty("mainEntry"));
        Assert.assertEquals("Should contains the en message", "value2EN", prop.getProperty("overriddenEntry"));

        prop = this.directoryProvider.getDictionaryFor(new Locale("fr", "FR"));
        Assert.assertEquals("Should contains the fr message", "value1FR", prop.getProperty("mainEntry"));
        Assert.assertEquals("Should contains the fr message", "value2FR", prop.getProperty("overriddenEntry"));
    }

    @Test
    public void should_know_if_locale_is_supported() {
        Assert.assertTrue(this.directoryProvider.hasDictionaryFor(new Locale("ja", "JP")));
        Assert.assertTrue(this.directoryProvider.hasDictionaryFor(new Locale("de", "DE")));

        Assert.assertFalse(this.directoryProvider.hasDictionaryFor(new Locale("ko", "KR")));
    }
}
