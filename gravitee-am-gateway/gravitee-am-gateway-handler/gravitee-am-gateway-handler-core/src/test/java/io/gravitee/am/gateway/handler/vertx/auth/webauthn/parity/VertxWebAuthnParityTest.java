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

import io.gravitee.am.common.webauthn.AttestationConveyancePreference;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.parity.WebAuthnCeremonies.Ceremony;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.store.RepositoryCredentialStore;
import io.gravitee.am.model.Credential;
import io.gravitee.am.model.Domain;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.Vertx;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The parity scenarios run against vertx-auth-webauthn 5.0.0.CR2: this is the reference behavior.
 *
 * @author GraviteeSource Team
 */
class VertxWebAuthnParityTest extends AbstractWebAuthnParityTest {

    @Override
    protected WebAuthnCeremonies createCeremonies(Vertx vertx, Domain domain, RepositoryCredentialStore credentialStore) {
        return new VertxWebAuthnCeremonies(vertx, domain, credentialStore);
    }

    @Override
    protected boolean acceptsAssertionSignedForAnotherRelyingPartyId() {
        return true;
    }

    @Override
    protected boolean acceptsAndroidKeyChainWithoutItsRoot() {
        return false;
    }

    /**
     * Fixture generator, not a test: regenerates
     * {@code src/test/resources/webauthn/parity/vertx-webauthn-credentials.json}, the credentials registered by
     * vertx-auth-webauthn that {@link AbstractWebAuthnParityTest.VertxWebAuthnStoredCredentials} replays.
     * <p>
     * <b>It is reported as skipped in every regular build, on purpose</b>: it rewrites a resource of the
     * source tree and is only meaningful while vertx-auth-webauthn is the implementation, so it only runs
     * when asked for:
     * <pre>
     * mvn test -Dtest='VertxWebAuthnParityTest#generateVertxWebAuthnCredentialFixtures' -Dwebauthn.parity.generateFixtures=true
     * </pre>
     */
    @Test
    @EnabledIfSystemProperty(named = "webauthn.parity.generateFixtures", matches = "true")
    void generateVertxWebAuthnCredentialFixtures() throws Exception {
        JsonArray fixtures = new JsonArray()
                .add(registeredByVertxWebAuthn("es256-none-passkey", browser(passkey(0))))
                .add(VertxWebAuthnCredentialFixtures.rs256(USERNAME, ORIGIN, RP_ID));

        setUpDomain();
        settings.setAttestationConveyancePreference(AttestationConveyancePreference.DIRECT);
        fixtures.add(registeredByVertxWebAuthn("es256-packed-full-attestation", browser(new ChainShapeAuthenticators.Packed(ChainShapeAuthenticators.Shape.UP_TO_ROOT))));

        Path target = Path.of("src/test/resources/webauthn/parity/vertx-webauthn-credentials.json");
        Files.createDirectories(target.getParent());
        Files.writeString(target, fixtures.encodePrettily() + "\n", StandardCharsets.UTF_8);
    }

    private JsonObject registeredByVertxWebAuthn(String name, EmulatedBrowser browser) {
        Credential credential = register(browser, USERNAME);
        // copy the record as stored right after the registration, before the login updates it
        JsonObject stored = VertxWebAuthnCredentialFixtures.json(credential);
        JsonObject options = ceremonies().loginOptions(USERNAME);
        JsonObject assertion = browser.get(options);
        Ceremony ceremony = new Ceremony(USERNAME, options.getString("challenge"), ORIGIN, options.getString("rpId"));
        return VertxWebAuthnCredentialFixtures.fixture(name, VertxWebAuthnCredentialFixtures.credential(stored), ceremony, assertion, credential.getCounter() + 1);
    }
}
