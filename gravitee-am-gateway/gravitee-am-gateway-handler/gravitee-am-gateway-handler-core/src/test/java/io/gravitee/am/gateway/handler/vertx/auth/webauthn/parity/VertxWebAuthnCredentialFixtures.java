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

import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.attestation.authenticator.RSACOSEKey;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.util.Base64UrlUtil;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.parity.WebAuthnCeremonies.Ceremony;
import io.gravitee.am.model.Credential;
import io.gravitee.am.model.ReferenceType;
import io.vertx.core.json.JsonObject;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;

/**
 * Conversion between stored {@link Credential}s and the fixture JSON, plus the hand-built RS256
 * credential (no emulated authenticator supports RS256, while Windows Hello registers RS256 keys).
 *
 * @author GraviteeSource Team
 */
final class VertxWebAuthnCredentialFixtures {

    private VertxWebAuthnCredentialFixtures() {
    }

    static JsonObject json(Credential credential) {
        return new JsonObject()
                .put("username", credential.getUsername())
                .put("credentialId", credential.getCredentialId())
                .put("publicKey", credential.getPublicKey())
                .put("counter", credential.getCounter())
                .put("aaguid", credential.getAaguid())
                .put("attestationStatementFormat", credential.getAttestationStatementFormat())
                .put("attestationStatement", credential.getAttestationStatement());
    }

    static Credential credential(JsonObject json) {
        Credential credential = new Credential();
        credential.setReferenceType(ReferenceType.DOMAIN);
        credential.setReferenceId("domain-id");
        credential.setUsername(json.getString("username"));
        credential.setCredentialId(json.getString("credentialId"));
        credential.setPublicKey(json.getString("publicKey"));
        credential.setCounter(json.getLong("counter"));
        credential.setAaguid(json.getString("aaguid"));
        credential.setAttestationStatementFormat(json.getString("attestationStatementFormat"));
        credential.setAttestationStatement(json.getString("attestationStatement"));
        return credential;
    }

    static JsonObject fixture(String name, Credential credential, Ceremony ceremony, JsonObject assertion, long expectedCounter) {
        return new JsonObject()
                .put("name", name)
                .put("credential", json(credential))
                .put("ceremony", new JsonObject()
                        .put("username", ceremony.username())
                        .put("challenge", ceremony.challenge())
                        .put("origin", ceremony.origin())
                        .put("relyingPartyId", ceremony.relyingPartyId()))
                .put("assertion", assertion)
                .put("expectedCounter", expectedCounter);
    }

    /**
     * An RS256 credential stored the way vertx-auth-webauthn stores it (raw COSE_Key, none attestation) and
     * an assertion for it, signed like a platform authenticator would.
     */
    static JsonObject rs256(String username, String origin, String relyingPartyId) throws Exception {
        SecureRandom random = new SecureRandom();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        byte[] credentialId = new byte[32];
        random.nextBytes(credentialId);
        byte[] challengeBytes = new byte[64];
        random.nextBytes(challengeBytes);
        String challenge = Base64UrlUtil.encodeToString(challengeBytes);

        byte[] coseKey = new ObjectConverter().getCborConverter()
                .writeValueAsBytes(RSACOSEKey.create((RSAPublicKey) keyPair.getPublic(), COSEAlgorithmIdentifier.RS256));

        Credential credential = new Credential();
        credential.setUsername(username);
        credential.setCredentialId(Base64UrlUtil.encodeToString(credentialId));
        credential.setPublicKey(Base64UrlUtil.encodeToString(coseKey));
        credential.setCounter(0L);
        credential.setAaguid("00000000-0000-0000-0000-000000000000");
        credential.setAttestationStatementFormat("none");
        credential.setAttestationStatement("{}");

        long counter = 1;
        byte[] authenticatorData = ByteBuffer.allocate(37)
                .put(MessageDigest.getInstance("SHA-256").digest(relyingPartyId.getBytes(StandardCharsets.UTF_8)))
                .put((byte) 0x05) // user present + user verified
                .putInt((int) counter)
                .array();
        byte[] clientDataJSON = new JsonObject()
                .put("type", "webauthn.get")
                .put("challenge", challenge)
                .put("origin", origin)
                .put("crossOrigin", false)
                .encode().getBytes(StandardCharsets.UTF_8);
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(authenticatorData);
        signature.update(MessageDigest.getInstance("SHA-256").digest(clientDataJSON));

        JsonObject assertion = new JsonObject()
                .put("id", credential.getCredentialId())
                .put("rawId", credential.getCredentialId())
                .put("type", "public-key")
                .put("response", new JsonObject()
                        .put("clientDataJSON", Base64UrlUtil.encodeToString(clientDataJSON))
                        .put("authenticatorData", Base64UrlUtil.encodeToString(authenticatorData))
                        .put("signature", Base64UrlUtil.encodeToString(signature.sign())));

        return fixture("rs256-none-windows-hello-like", credential, new Ceremony(username, challenge, origin, relyingPartyId), assertion, counter);
    }
}
