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

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Properties;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(Parameterized.class)
public class FileSystemDictionaryProviderTest {

    @Parameterized.Parameters
    public static Collection<Object[]> params() {
        return Arrays.asList(
                new Object[] { "src/test/resources/i18n_default"},
                new Object[] { "src/test/resources/i18n_no_default"}
        );
    }

    private final String directory;

    private DictionaryProvider directoryProvider;

    public FileSystemDictionaryProviderTest(final String dir) {
        this.directory = dir;
    }

    @Before
    public void initProvider() {
        this.directoryProvider = new FileSystemDictionaryProvider(this.directory);
    }

    @Test
    public void should_Fallback_ToDefault() {
        Assume.assumeTrue(this.directory.endsWith("i18n_default"));
        Properties prop = this.directoryProvider.getDictionaryFor(new Locale("it"));
        Assert.assertEquals("Should contains the default message", "value-default", prop.getProperty("key"));
    }

    @Test
    public void should_Match_Most_Specific_Locale_enGB() {
        Properties prop = this.directoryProvider.getDictionaryFor(new Locale("en", "GB"));
        Assert.assertEquals("Should contains the en-GB message", "value-en-GB", prop.getProperty("key"));
        Assert.assertTrue("Should contains the en-GB multiline message", prop.getProperty("key.multi.lines").contains("lines")
                && prop.getProperty("key.multi.lines").contains("en-GB"));
    }

    @Test
    public void should_Match_Most_Specific_Locale_frFR() {
        Properties prop = this.directoryProvider.getDictionaryFor(new Locale("fr", "FR"));
        Assert.assertEquals("Should contains the fr-FR message", "value-fr-FR", prop.getProperty("key"));
        Assert.assertTrue("Should contains the fr-FR multiline message", prop.getProperty("key.multi.lines").contains("lines")
                && prop.getProperty("key.multi.lines").contains("fr-FR"));
    }

    @Test
    public void should_Fallback_to_language() {
        // fr-CA is not define, fallback to fr
        Properties prop = this.directoryProvider.getDictionaryFor(new Locale("fr", "CA"));
        Assert.assertEquals("Should contains the fr message", "value-fr", prop.getProperty("key"));
        Assert.assertTrue("Should contains the fr multiline message", prop.getProperty("key.multi.lines").contains("lines")
                && prop.getProperty("key.multi.lines").contains("fr")
                && !prop.getProperty("key.multi.lines").contains("fr-FR"));
    }
}
