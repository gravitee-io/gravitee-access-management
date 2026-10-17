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
package io.gravitee.am.gateway.handler.vertx.auth.webauthn.parity;

import io.gravitee.am.model.Credential;
import io.vertx.core.json.JsonObject;

/**
 * The relying-party side of the WebAuthn ceremonies as the AM handlers use it, independent of the
 * library doing the verification. The parity scenarios only talk to this interface, so the same scenarios
 * pin the behavior of vertx-auth-webauthn 5.0.0.CR2 today and check the webauthn4j based implementation
 * against it once it replaces vertx-auth-webauthn.
 *
 * @author GraviteeSource Team
 */
interface WebAuthnCeremonies {

    /**
     * {@code createCredentialsOptions} for the given user, as {@code WebAuthnRegisterHandler} requests it.
     */
    JsonObject registrationOptions(String username);

    /**
     * Verify a registration response and persist the credential through the credential store.
     *
     * @throws CeremonyFailedException when the response is rejected
     */
    void register(Ceremony ceremony, JsonObject credential);

    /**
     * {@code getCredentialsOptions} for the given user (may be null for discoverable credentials).
     */
    JsonObject loginOptions(String username);

    /**
     * Verify an assertion and update the stored counter.
     *
     * @throws CeremonyFailedException when the assertion is rejected
     */
    void login(Ceremony ceremony, JsonObject assertion);

    /**
     * The authenticator integrity check run by {@code WebAuthnLoginHandler} when
     * {@code enforceAuthenticatorIntegrity} is enabled, on a stored credential.
     *
     * @throws CeremonyFailedException when the stored authenticator is not trusted anymore
     */
    void checkIntegrity(Credential credential);

    /**
     * What the handler takes from the session and the request to verify a response.
     *
     * @param username the username saved with the challenge (may be null)
     * @param challenge the challenge saved in the session
     * @param origin the origin the request came from
     * @param relyingPartyId the relying party id sent to the browser in the options
     */
    record Ceremony(String username, String challenge, String origin, String relyingPartyId) {
    }

    class CeremonyFailedException extends RuntimeException {
        CeremonyFailedException(Throwable cause) {
            super(cause.getMessage(), cause);
        }
    }
}
