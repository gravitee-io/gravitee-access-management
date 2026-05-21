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
package io.gravitee.am.model.application;

import java.util.Arrays;

/**
 * Persona carried on a Blueprint application ({@code Application.type = AGENT}).
 * <p>
 * A Blueprint application is a normal persisted {@code Application} that acts
 * as the trust anchor for one or more ephemeral agent instances. Instances are
 * not persisted — they authenticate with {@code jwt-bearer} client assertions
 * signed by a key registered in the blueprint's JWKS, with the instance id
 * carried in the {@code sub} claim.
 * <p>
 * Blueprints are <b>not</b> template applications. Templates are factories used
 * at creation time to seed new applications; blueprints are live OAuth clients
 * that authenticate N agent instances at runtime. The two concepts share
 * "protected / do not casually delete" semantics but are otherwise unrelated.
 *
 * <ul>
 *   <li>{@link #USER_EMBEDDED} (Type A) — PKCE + public client + act claim injection</li>
 *   <li>{@link #HOSTED_DELEGATED} (Type B) — Confidential client with token exchange (RFC 8693)</li>
 *   <li>{@link #AUTONOMOUS} (Type C) — Confidential client with client_credentials + token exchange</li>
 * </ul>
 */
public enum AgentType {
    USER_EMBEDDED,
    HOSTED_DELEGATED,
    AUTONOMOUS;

    public static AgentType orNull(String type) {
        if (type == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(v -> v.name().equals(type))
                .findFirst()
                .orElse(null);
    }
}
