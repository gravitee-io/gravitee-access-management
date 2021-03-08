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
package io.gravitee.am.service.exception;

/**
 * @author Boualem DJELAILI (boualem.djelaili at graviteesource.com)
 * @author GraviteeSource Team
 */
public class InvalidPasswordException extends RuntimeException {

    private final String errorKey;

    private InvalidPasswordException(String message, String errorKey) {
        super(message);
        this.errorKey = errorKey;
    }

    public static InvalidPasswordException of(String message, String errorKey) {
        return new InvalidPasswordException(message, errorKey);
    }

    public String getErrorKey() {
        return errorKey != null ? errorKey : "invalid_password_value";
    }
}
