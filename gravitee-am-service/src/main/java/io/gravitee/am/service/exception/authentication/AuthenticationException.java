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
package io.gravitee.am.service.exception.authentication;

import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class AuthenticationException extends RuntimeException {

    private Map<String, String> details;

    public AuthenticationException() {
    }

    public AuthenticationException(String msg, Throwable t) {
        super(msg, t);
    }

    public AuthenticationException(String msg) {
        super(msg);
    }

    public AuthenticationException(Map<String, String> details) {
        this.details = details;
    }

    public AuthenticationException(String msg, Map<String, String> details) {
        this(msg);
        this.details = details;
    }

    public AuthenticationException(String msg, Throwable throwable, Map<String, String> details) {
        this(msg, throwable);
        this.details = details;
    }

    public abstract String getErrorCode();

    public Map<String, String> getDetails() {
        return details;
    }

    public void setDetails(Map<String, String> details) {
        this.details = details;
    }
}
