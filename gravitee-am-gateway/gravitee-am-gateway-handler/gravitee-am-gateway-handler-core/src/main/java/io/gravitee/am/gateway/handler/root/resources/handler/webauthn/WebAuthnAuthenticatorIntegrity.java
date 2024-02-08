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
package io.gravitee.am.gateway.handler.root.resources.handler.webauthn;

import io.gravitee.am.model.Credential;
import io.gravitee.am.model.login.WebAuthnSettings;

import java.time.Instant;
import java.util.Date;
import java.util.function.Supplier;

import static java.util.Optional.ofNullable;

class WebAuthnAuthenticatorIntegrity {
    private final boolean forceAuthIntegrity;
    private final Integer authIntegrityMaxAgeSeconds;
    private final Supplier<Date> timestamp;

    WebAuthnAuthenticatorIntegrity(boolean forceAuthIntegrity,
                                   Integer authIntegrityMaxAgeSeconds,
                                   Supplier<Date> timestamp) {
        this.forceAuthIntegrity = forceAuthIntegrity;
        this.authIntegrityMaxAgeSeconds = authIntegrityMaxAgeSeconds;
        this.timestamp = timestamp;
    }

    static WebAuthnAuthenticatorIntegrity authIntegrity(WebAuthnSettings webAuthnSettings) {
        if (webAuthnSettings == null) {
            return new WebAuthnAuthenticatorIntegrity(false, 0, Date::new);
        } else {
            return new WebAuthnAuthenticatorIntegrity(
                    webAuthnSettings.isEnforceAuthenticatorIntegrity(),
                    ofNullable(webAuthnSettings.getEnforceAuthenticatorIntegrityMaxAge()).orElse(0),
                    Date::new);
        }
    }

    Credential updateLastCheckedDate(Credential credential) {
        if (forceAuthIntegrity) {
            Date now = timestamp.get();
            Date lastCheckedAt = credential.getLastCheckedAt();

            if (lastCheckedAt == null) {
                credential.setLastCheckedAt(now);
            } else {
                Instant maxAge = lastCheckedAt.toInstant().plusSeconds(authIntegrityMaxAgeSeconds);
                if (now.toInstant().isAfter(maxAge)) {
                    credential.setLastCheckedAt(now);
                }
            }
        }
        return credential;
    }
}
