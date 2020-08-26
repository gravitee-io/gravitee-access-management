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
 * A WebAuthn Relying Party may require user verification for some of its operations but not for others, and may use this type to express its needs.
 *
 * required
 * This value indicates that the Relying Party requires user verification for the operation and will fail the operation if the response does not have the UV flag set.
 *
 * preferred
 * This value indicates that the Relying Party prefers user verification for the operation if possible, but will not fail the operation if the response does not have the UV flag set.
 *
 * discouraged
 * This value indicates that the Relying Party does not want user verification employed during the operation (e.g., in the interest of minimizing disruption to the user interaction flow).
 *
 * Se <a href="https://www.w3.org/TR/webauthn/#enumdef-userverificationrequirement">User Verification Requirement Enumeration (enum UserVerificationRequirement)</a>
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public enum UserVerification {

    REQUIRED("required"),
    PREFERRED("preferred"),
    DISCOURAGED("discouraged");

    private final String value;

    UserVerification(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static UserVerification fromString(String value) {
        for (UserVerification u : UserVerification.values()) {
            if (u.value.equalsIgnoreCase(value)) {
                return u;
            }
        }
        throw new IllegalArgumentException("No user verification with value " + value + " found");
    }
}
