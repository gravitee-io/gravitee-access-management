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
import org.springframework.core.task.support.ExecutorServiceAdapter;

import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ThreadLocalDomainDictionaryProviderTest extends AbstractDynamicDictionaryProviderTest {

    private final ThreadLocalDomainDictionaryProvider provider = new ThreadLocalDomainDictionaryProvider();

    @Override
    protected DynamicDictionaryProvider provider() {
        return this.provider;
    }

    @Test
    public void shouldGetRightDictionary_inMultiThreadContext() throws Exception{
        I18nDictionary i18nDictionary = new I18nDictionary();
        i18nDictionary.setLocale("test-locale");
        i18nDictionary.setEntries(Collections.singletonMap("key", "value"));
        provider().loadDictionary(i18nDictionary);
        // check value
        assertFalse(provider().getDictionaryFor(new Locale(i18nDictionary.getLocale())).isEmpty());
        assertTrue(provider().getDictionaryFor(new Locale(i18nDictionary.getLocale())).getProperty("key").equals("value"));

        final ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            final Future<String> future = executorService.submit(() -> {
                I18nDictionary localI18nDictionary = new I18nDictionary();
                localI18nDictionary.setLocale("test-locale");
                localI18nDictionary.setEntries(Collections.singletonMap("key", "value-locale"));
                provider().loadDictionary(localI18nDictionary);
                return provider().getDictionaryFor(new Locale(localI18nDictionary.getLocale())).getProperty("key");
            });
            // inside a thread the value is different
            assertEquals("value-locale", future.get());
        } finally {
            executorService.shutdown();
        }

        // back to the main thread values are unchanged
        assertFalse(provider().getDictionaryFor(new Locale(i18nDictionary.getLocale())).isEmpty());
        assertTrue(provider().getDictionaryFor(new Locale(i18nDictionary.getLocale())).getProperty("key").equals("value"));
    }
}
