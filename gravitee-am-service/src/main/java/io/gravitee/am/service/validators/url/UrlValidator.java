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
package io.gravitee.am.service.validators.url;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class UrlValidator implements ConstraintValidator<Url, String> {
    private static final Pattern URL_PATTERN = Pattern.compile(
            "\\b[a-zA-Z][a-zA-Z0-9+.-]*://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(?:[^\\s]*)?\\b"
    );

    @Override
    public boolean isValid(String url, ConstraintValidatorContext constraintValidatorContext) {
        if(url == null || url.isEmpty()) {
            return true;
        }
        return URL_PATTERN.matcher(url).matches();
    }
}
