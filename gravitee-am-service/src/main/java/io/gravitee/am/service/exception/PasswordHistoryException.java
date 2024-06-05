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

import io.gravitee.am.model.PasswordPolicy;
import io.gravitee.common.http.HttpStatusCode;

public class PasswordHistoryException extends AbstractManagementException {

    protected PasswordHistoryException(String message) {
        super(message);
    }

    @Override
    public int getHttpStatusCode() {
        return HttpStatusCode.BAD_REQUEST_400;
    }

    public static PasswordHistoryException passwordAlreadyInHistory() {
        return new PasswordHistoryException("Password has already been used.");
    }

    public static PasswordHistoryException passwordAlreadyInHistory(PasswordPolicy passwordSettings) {
        return new PasswordHistoryException(String.format("Password must not match the last %d passwords", passwordSettings.getOldPasswords()));
    }
}
