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
package io.gravitee.am.gateway.handler.vertx.auth.webauthn.attestation;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.webauthn.AttestationCertificates;
import io.vertx.ext.auth.webauthn.WebAuthnOptions;
import io.vertx.ext.auth.webauthn.impl.AuthData;
import io.vertx.ext.auth.webauthn.impl.attestation.Attestation;
import io.vertx.ext.auth.webauthn.impl.attestation.AttestationException;
import io.vertx.ext.auth.webauthn.impl.metadata.MetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GraviteeNoneAttestation implements Attestation {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public String fmt() {
        return "none";
    }

    public AttestationCertificates validate(WebAuthnOptions options, MetaData metadata, byte[] clientDataJSON, JsonObject attestation, AuthData authData) throws AttestationException {
        if (!"00000000-0000-0000-0000-000000000000".equals(authData.getAaguidString())) {
            // Some browsers don't respect the need to send 16 zero bytes values
            // we only log in debug this fact, and we just check that the attStmt is empty
            logger.debug("AAGUID is not 00000000-0000-0000-0000-000000000000 for None attestation (provided AAGUID : {})", authData.getAaguidString());
        }

        if (attestation.containsKey("attStmt") && !attestation.getJsonObject("attStmt").isEmpty()) {
            throw new AttestationException("attStmt is present!");
        } else {
            return new AttestationCertificates();
        }
    }
}
