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

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;

import java.util.Locale;
import java.util.Properties;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class FileSystemDictionaryProviderTest {

    public static Stream<Arguments> params() {
        return Stream.of(
                Arguments.of("src/test/resources/i18n_default"),
                Arguments.of("src/test/resources/i18n_default_no_en"),
                Arguments.of("src/test/resources/i18n_no_default")
        );
    }

    @ParameterizedTest(name = "Must fallback to default")
    @MethodSource("params")
    public void should_Fallback_ToDefault(String directory) {
        assumeTrue(directory.endsWith("i18n_default_no_en"));
        var directoryProvider = new FileSystemDictionaryProvider(directory);
        Properties prop = directoryProvider.getDictionaryFor(new Locale("it"));
        assertEquals("value-default", prop.getProperty("key"));
    }

    @ParameterizedTest(name = "Must match most specific locale en_GB")
    @MethodSource("params")
    public void should_Match_Most_Specific_Locale_enGB(String directory) {
        assumeFalse(directory.endsWith("i18n_default_no_en"));
        var directoryProvider = new FileSystemDictionaryProvider(directory);
        Properties prop = directoryProvider.getDictionaryFor(new Locale("en", "GB"));
        assertEquals("Should contains the en-GB message", "value-en-GB", prop.getProperty("key"));
        assertTrue(prop.getProperty("key.multi.lines").contains("lines")
                && prop.getProperty("key.multi.lines").contains("en-GB"));
    }

    @ParameterizedTest(name = "Must match most specific locale en_FR")
    @MethodSource("params")
    public void should_Match_Most_Specific_Locale_frFR(String directory) {
        var directoryProvider = new FileSystemDictionaryProvider(directory);
        Properties prop = directoryProvider.getDictionaryFor(new Locale("fr", "FR"));
        assertEquals("value-fr-FR", prop.getProperty("key"));
        assertTrue(prop.getProperty("key.multi.lines").contains("lines")
                && prop.getProperty("key.multi.lines").contains("fr-FR"));
    }

    @ParameterizedTest(name = "Must fallback to language")
    @MethodSource("params")
    public void should_Fallback_to_language(String directory) {
        // fr-CA is not define, fallback to fr
        var directoryProvider = new FileSystemDictionaryProvider(directory);
        Properties prop = directoryProvider.getDictionaryFor(new Locale("fr", "CA"));
        assertEquals("Should contains the fr message", "value-fr", prop.getProperty("key"));
        assertTrue(prop.getProperty("key.multi.lines").contains("lines")
                && prop.getProperty("key.multi.lines").contains("fr")
                && !prop.getProperty("key.multi.lines").contains("fr-FR"));
    }
}
