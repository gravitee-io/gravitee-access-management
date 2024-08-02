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
package io.gravitee.am.gateway.handler.oauth2.service.code.impl;

import io.gravitee.am.common.utils.SecureRandomString;

import java.util.function.Supplier;
import java.util.regex.Pattern;

class AuthorizationCodeGenerator {
    private static final String FORBIDDEN_SUBSTRING = "--";
    private static final Pattern FORBIDDEN_SUBSTRING_PATTERN = Pattern.compile(FORBIDDEN_SUBSTRING);

    private final Supplier<String> generator;

    AuthorizationCodeGenerator(Supplier<String> generator) {
        this.generator = generator;
    }

    AuthorizationCodeGenerator() {
        this(SecureRandomString::generate);
    }

    public String generate() {
        String code = generator.get();
        if (code.contains(FORBIDDEN_SUBSTRING)) {
            code = FORBIDDEN_SUBSTRING_PATTERN.matcher(code).replaceAll("-A");
        }
        return code;
    }
}
