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
package io.gravitee.am.gateway.handler.common.command;

/**
 * Outcome of a command token POST to a single RP command endpoint.
 *
 * @author GraviteeSource Team
 */
public enum CommandDeliveryStatus {

    /**
     * The RP acknowledged the command (2xx): terminal.
     */
    DELIVERED,

    /**
     * The RP answered the spec-defined 409 incompatible_state / unknown account
     * response: a benign no-op, terminal.
     */
    UNKNOWN_ACCOUNT,

    /**
     * Network error or any other response: retried until the attempts cap.
     */
    FAILED
}
