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

import io.gravitee.am.gateway.handler.vertx.auth.webauthn.AuthenticatorIntegrityVerifier;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.WebAuthn4JFactory;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.anchor.ShippedTrustAnchors;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.store.RepositoryCredentialStorage;
import io.gravitee.am.model.Credential;
import io.gravitee.am.model.Domain;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.webauthn4j.WebAuthn4J;
import io.vertx.ext.auth.webauthn4j.WebAuthn4JCredentials;
import io.vertx.rxjava3.core.Vertx;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.GeneralSecurityException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * {@link WebAuthnCeremonies} backed by the webauthn4j based implementation, built by
 * {@link WebAuthn4JFactory} with the trust anchors shipped with the distribution.
 *
 * @author GraviteeSource Team
 */
final class WebAuthn4JCeremonies implements WebAuthnCeremonies {

    private final WebAuthn4J webAuthn;
    private final AuthenticatorIntegrityVerifier integrityVerifier = new AuthenticatorIntegrityVerifier();

    WebAuthn4JCeremonies(Vertx vertx, Domain domain, RepositoryCredentialStorage credentialStorage) {
        WebAuthn4JFactory factory = new WebAuthn4JFactory();
        ReflectionTestUtils.setField(factory, "vertx", vertx);
        ReflectionTestUtils.setField(factory, "domain", domain);
        ReflectionTestUtils.setField(factory, "credentialStorage", credentialStorage);
        ReflectionTestUtils.setField(factory, "trustAnchorRegistry", ShippedTrustAnchors.registry());
        this.webAuthn = factory.getObject();
    }

    @Override
    public JsonObject registrationOptions(String username) {
        return await(webAuthn.createCredentialsOptions(new JsonObject().put("name", username).put("displayName", username)));
    }

    @Override
    public void register(Ceremony ceremony, JsonObject credential) {
        await(webAuthn.authenticate(credentials(ceremony, credential)));
    }

    @Override
    public JsonObject loginOptions(String username) {
        return await(webAuthn.getCredentialsOptions(username));
    }

    @Override
    public void login(Ceremony ceremony, JsonObject assertion) {
        await(webAuthn.authenticate(credentials(ceremony, assertion)));
    }

    @Override
    public void checkIntegrity(Credential credential) {
        try {
            integrityVerifier.verify(credential);
        } catch (GeneralSecurityException e) {
            throw new CeremonyFailedException(e);
        }
    }

    private static WebAuthn4JCredentials credentials(Ceremony ceremony, JsonObject webauthn) {
        // the handlers set the relying party id they sent to the browser
        return new WebAuthn4JCredentials()
                .setOrigin(ceremony.origin())
                .setDomain(ceremony.relyingPartyId())
                .setChallenge(ceremony.challenge())
                .setUsername(ceremony.username())
                .setWebauthn(webauthn);
    }

    private static <T> T await(Future<T> future) {
        try {
            return future.toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw new CeremonyFailedException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (TimeoutException e) {
            throw new IllegalStateException("WebAuthn ceremony timed out", e);
        }
    }
}
