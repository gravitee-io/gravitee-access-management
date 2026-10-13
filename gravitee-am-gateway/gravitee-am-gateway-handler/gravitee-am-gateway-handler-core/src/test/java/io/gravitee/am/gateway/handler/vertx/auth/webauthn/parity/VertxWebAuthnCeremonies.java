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

import io.gravitee.am.gateway.handler.vertx.auth.webauthn.WebAuthnFactory;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.anchor.ShippedTrustAnchors;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.store.RepositoryCredentialStore;
import io.gravitee.am.model.Credential;
import io.gravitee.am.model.Domain;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.webauthn.AttestationCertificates;
import io.vertx.ext.auth.webauthn.Authenticator;
import io.vertx.ext.auth.webauthn.WebAuthn;
import io.vertx.ext.auth.webauthn.WebAuthnCredentials;
import io.vertx.ext.auth.webauthn.impl.attestation.Attestation;
import io.vertx.rxjava3.core.Vertx;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * {@link WebAuthnCeremonies} backed by vertx-auth-webauthn 5.0.0.CR2, built by the production
 * {@link WebAuthnFactory} and called the way the AM handlers call it.
 *
 * @author GraviteeSource Team
 */
final class VertxWebAuthnCeremonies implements WebAuthnCeremonies {

    private final WebAuthn webAuthn;

    VertxWebAuthnCeremonies(Vertx vertx, Domain domain, RepositoryCredentialStore credentialStore) {
        WebAuthnFactory factory = new WebAuthnFactory();
        ReflectionTestUtils.setField(factory, "vertx", vertx);
        ReflectionTestUtils.setField(factory, "domain", domain);
        ReflectionTestUtils.setField(factory, "credentialStore", credentialStore);
        ReflectionTestUtils.setField(factory, "trustAnchorRegistry", ShippedTrustAnchors.registry());
        this.webAuthn = factory.getObject();
        applyProductionAttestationOrder(webAuthn);
    }

    /**
     * vertx-auth-webauthn keeps, for each format, the last {@link Attestation} its {@code ServiceLoader}
     * returns. The distribution puts third-party jars, vertx-auth-webauthn included, in {@code lib/ext}, a
     * parent of the class loader holding the AM jars: vertx-auth-webauthn's provider file is read first and
     * AM's last, so AM's attestations replace vertx-auth-webauthn's for the formats both declare. The flat
     * test classpath lists AM's classes first and gives the opposite result; apply AM's providers again to
     * get the production mapping.
     */
    @SuppressWarnings("unchecked")
    private static void applyProductionAttestationOrder(WebAuthn webAuthn) {
        Map<String, Attestation> attestations = (Map<String, Attestation>) ReflectionTestUtils.getField(webAuthn, "attestations");
        try {
            Enumeration<URL> providerFiles = VertxWebAuthnCeremonies.class.getClassLoader().getResources("META-INF/services/" + Attestation.class.getName());
            for (URL providerFile : Collections.list(providerFiles)) {
                try (var input = providerFile.openStream()) {
                    for (String provider : new String(input.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                        if (provider.trim().startsWith("io.gravitee.")) {
                            Attestation attestation = (Attestation) Class.forName(provider.trim()).getDeclaredConstructor().newInstance();
                            attestations.put(attestation.fmt(), attestation);
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load the AM WebAuthn attestations", e);
        }
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
        // same mapping as WebAuthnLoginHandler#checkAuthenticatorConformity
        Authenticator authenticator = new Authenticator();
        authenticator.setUserName(credential.getUsername());
        authenticator.setCredID(credential.getCredentialId());
        authenticator.setAaguid(credential.getAaguid());
        authenticator.setCounter(credential.getCounter());
        authenticator.setPublicKey(credential.getPublicKey());
        authenticator.setFmt(credential.getAttestationStatementFormat());
        authenticator.setAttestationCertificates(new AttestationCertificates(new JsonObject(credential.getAttestationStatement())));
        try {
            webAuthn.metaDataService().verify(authenticator);
        } catch (RuntimeException e) {
            throw new CeremonyFailedException(e);
        }
    }

    private static WebAuthnCredentials credentials(Ceremony ceremony, JsonObject webauthn) {
        // the handlers never set the domain, so vertx-auth-webauthn does not verify the rpIdHash
        return new WebAuthnCredentials()
                .setOrigin(ceremony.origin())
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
