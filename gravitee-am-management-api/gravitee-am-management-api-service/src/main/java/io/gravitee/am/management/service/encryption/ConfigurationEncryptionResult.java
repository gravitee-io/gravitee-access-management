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
package io.gravitee.am.management.service.encryption;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Outcome of the encryption of the configurations of one plugin type.
 *
 * @param plugin    the plugin type
 * @param status    what happened
 * @param encrypted the number of configurations rewritten by this call
 * @param message   why it failed, when it failed
 * @author GraviteeSource Team
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConfigurationEncryptionResult(String plugin, Status status, long encrypted, String message) {

    public enum Status {
        /** encrypted by this call */
        DONE,
        /** a previous call already encrypted them with this key */
        ALREADY_DONE,
        /** another call, possibly on another node, is encrypting them */
        IN_PROGRESS,
        /** a later call runs it again */
        FAILED
    }
}
