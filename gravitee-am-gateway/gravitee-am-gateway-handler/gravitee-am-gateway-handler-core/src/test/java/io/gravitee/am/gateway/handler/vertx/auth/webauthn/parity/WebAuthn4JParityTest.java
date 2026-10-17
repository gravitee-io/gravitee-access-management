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

import io.gravitee.am.gateway.handler.vertx.auth.webauthn.store.RepositoryCredentialStorage;
import io.gravitee.am.model.Domain;
import io.vertx.rxjava3.core.Vertx;

/**
 * The parity scenarios run against the webauthn4j based implementation: same verdicts as
 * vertx-auth-webauthn, except the deviations its hooks declare.
 *
 * @author GraviteeSource Team
 */
class WebAuthn4JParityTest extends AbstractWebAuthnParityTest {

    @Override
    protected WebAuthnCeremonies createCeremonies(Vertx vertx, Domain domain) {
        return new WebAuthn4JCeremonies(vertx, domain, configureCredentialStore(new RepositoryCredentialStorage()));
    }

    /**
     * The {@code packed-surrogate-attestation} sample registers an RS1
     * key, an algorithm AM does not offer (ES256, RS256). webauthn4j rejects a credential using an
     * algorithm absent from the registration options, as the specification requires; vertx-auth-webauthn
     * did not check it. Browsers only create credentials with an offered algorithm.
     */
    @Override
    protected boolean acceptsRecordedAttestation(String name, boolean acceptedByVertxWebAuthn) {
        return acceptedByVertxWebAuthn && !"packed-surrogate-attestation".equals(name);
    }
}
