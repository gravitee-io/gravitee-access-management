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
package io.gravitee.am.gateway.handler.scim.exception;

import io.gravitee.am.gateway.handler.scim.model.ScimType;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UniquenessException extends SCIMException {

    private String existingUserId;
    private String existingUsername;

    public UniquenessException() {
    }

    public UniquenessException(String message) {
        super(message);
    }

    public UniquenessException(String message, Throwable cause) {
        super(message, cause);
    }

    public UniquenessException(String message, String existingUserId, String existingUsername) {
        super(message);
        this.existingUserId = existingUserId;
        this.existingUsername = existingUsername;
    }

    public String getExistingUserId() {
        return existingUserId;
    }

    public String getExistingUsername() {
        return existingUsername;
    }

    public boolean hasExistingUserDetails() {
        return existingUserId != null && existingUsername != null;
    }

    @Override
    public int getHttpStatusCode() {
        return 409;
    }

    @Override
    public ScimType getScimType() {
        return ScimType.UNIQUENESS;
    }
}
