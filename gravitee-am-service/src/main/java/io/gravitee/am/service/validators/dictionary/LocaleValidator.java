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

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.stream.Stream;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * Validator for {@link ValidLocale}. This will let null locales pass as it is expected that those constraints will be
 * provided by other annotations as required.
 */
public class LocaleValidator implements ConstraintValidator<ValidLocale, String> {
    @Override
    public boolean isValid(@Nullable String locale, ConstraintValidatorContext context) {
        return locale == null || Stream.of(Locale.getAvailableLocales()).filter(l -> !isNullOrEmpty(l.getLanguage()))
              .anyMatch(l -> l.getLanguage().equals(locale));
    }
}
