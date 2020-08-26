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
package io.gravitee.am.common.webauthn;

/**
 * This enumeration’s values describe authenticators' attachment modalities.
 * Relying Parties use this for two purposes:
 *   - to express a preferred authenticator attachment modality when calling navigator.credentials.create() to create a credential, and
 *   - to inform the client of the Relying Party's best belief about how to locate the managing authenticators of the credentials listed in allowCredentials when calling navigator.credentials.get().
 *
 * platform
 * This value indicates platform attachment.
 *
 * cross-platform
 * This value indicates cross-platform attachment.
 *
 * See <a href="https://www.w3.org/TR/webauthn/#attachment">Authenticator Attachment Enumeration (enum AuthenticatorAttachment)</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public enum AuthenticatorAttachment {

    CROSS_PLATFORM("cross-platform"),
    PLATFORM("platform");

    private final String value;

    AuthenticatorAttachment(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static AuthenticatorAttachment fromString(String value) {
        for (AuthenticatorAttachment a : AuthenticatorAttachment.values()) {
            if (a.value.equalsIgnoreCase(value)) {
                return a;
            }
        }
        throw new IllegalArgumentException("No user authenticator attachement with value [" + value + "] found");
    }
}
