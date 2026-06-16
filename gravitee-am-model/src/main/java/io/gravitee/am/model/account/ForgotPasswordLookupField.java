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
package io.gravitee.am.model.account;

import java.util.regex.Pattern;

/**
 * Maps forgot-password form field keys to user search filter paths.
 *
 * @author GraviteeSource Team
 */
public final class ForgotPasswordLookupField {

    public static final String EMAIL = "email";
    public static final String USERNAME = "username";

    private static final Pattern KEY_PATTERN = Pattern.compile("[a-zA-Z][a-zA-Z0-9_-]{0,63}");

    private ForgotPasswordLookupField() {
    }

    public static boolean isValidKey(String key) {
        return key != null && KEY_PATTERN.matcher(key).matches();
    }

    public static String toFilterFieldName(String key) {
        if (EMAIL.equals(key)) {
            return "emails.value";
        }
        if (USERNAME.equals(key)) {
            return "userName";
        }
        return "additionalInformation." + key;
    }

    public static String defaultInputType(String key) {
        return EMAIL.equals(key) ? "email" : "text";
    }
}
