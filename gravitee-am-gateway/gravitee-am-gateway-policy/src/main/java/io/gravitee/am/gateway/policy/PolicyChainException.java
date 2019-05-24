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
package io.gravitee.am.gateway.policy;

import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PolicyChainException extends Exception {

    private int statusCode;
    private String key;
    private Map<String, Object> parameters;
    private String contentType;

    public PolicyChainException() {
        super();
    }

    public PolicyChainException(String message) {
        super(message);
    }

    public PolicyChainException(String message, Throwable cause) {
        super(message, cause);
    }

    public PolicyChainException(Throwable cause) {
        super(cause);
    }

    public PolicyChainException(String message, int statusCode, String key, Map<String, Object> parameters, String contentType) {
        super(message);
        this.statusCode = statusCode;
        this.key = key;
        this.parameters = parameters;
        this.contentType = contentType;
    }

    public int statusCode() {
        return statusCode;
    }

    public String key() {
        return key;
    }

    public Map<String, Object> parameters() {
        return parameters;
    }

    public String contentType() {
        return contentType;
    }
}
