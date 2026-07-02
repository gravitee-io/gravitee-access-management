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
package io.gravitee.am.authdevice.notifier.cibafederation.provider;

import java.util.List;
import java.util.Map;

public interface ConsentRelayStrategy {
    /** Transform (or pass through) the inbound authorization_details for relay to the downstream IdP. */
    List<Map<String, Object>> relay(List<Map<String, Object>> authorizationDetails);

    /** The configured strategy variants. Resolved fail-fast so a config typo can't silently fall through. */
    enum Type {
        PASSTHROUGH("passthrough"),
        AUTH0_USER_PROFILE("auth0-user-profile");

        private final String configValue;
        Type(String configValue) { this.configValue = configValue; }

        /** Blank/absent selects the backward-compatible default; an unknown non-blank value fails fast. */
        public static Type fromConfig(String value) {
            if (value == null || value.isBlank()) {
                return AUTH0_USER_PROFILE;
            }
            for (Type t : values()) {
                if (t.configValue.equalsIgnoreCase(value)) return t;
            }
            throw new IllegalArgumentException("CIBA federation: unknown consentRelayStrategy '" + value
                    + "' (expected one of: passthrough, auth0-user-profile)");
        }
    }
}
