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
package io.gravitee.am.service.validators.dictionary;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LocaleValidatorTest {

    @ParameterizedTest
    @MethodSource("validLocales")
    void isValid(String locale) {
        assertTrue(new LocaleValidator().isValid(locale, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "x", "y", "z", "123"})
    void isNotValid(String locale) {
        assertFalse(new LocaleValidator().isValid(locale, null));
    }

    public static Stream<String> validLocales() {
        return Stream.of(Locale.getAvailableLocales()).map(Locale::getLanguage).filter(l -> !"".equals(l));
    }
}