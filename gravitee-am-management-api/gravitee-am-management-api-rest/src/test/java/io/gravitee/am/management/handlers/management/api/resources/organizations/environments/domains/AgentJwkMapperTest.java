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
package io.gravitee.am.management.handlers.management.api.resources.organizations.environments.domains;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import io.gravitee.am.model.jose.ECKey;
import io.gravitee.am.model.jose.JWK;
import io.gravitee.am.model.jose.OKPKey;
import io.gravitee.am.model.jose.RSAKey;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AgentJwkMapper parsing and validation of public asymmetric JWK keys.
 * Rejects private keys, symmetric (oct) keys, and invalid JWKs.
 */
class AgentJwkMapperTest {
    private static final String TEST_KID = "test-key-id";

    @Test
    void shouldParseValidRsaPublicKey() throws Exception {
        // Arrange: Generate a real RSA public key
        var rsa = new RSAKeyGenerator(2048)
                .keyID(TEST_KID)
                .generate();
        Map<String, Object> rawKey = (Map<String, Object>) rsa.toPublicJWK().toJSONObject();

        // Act
        JWK result = AgentJwkMapper.fromRaw(rawKey);

        // Assert
        assertNotNull(result);
        assertInstanceOf(RSAKey.class, result);
        RSAKey rsaKey = (RSAKey) result;
        assertEquals(TEST_KID, rsaKey.getKid());
        assertEquals("RSA", rsaKey.getKty());
        assertNotNull(rsaKey.getN());
        assertNotNull(rsaKey.getE());
    }

    @Test
    void shouldPreserveKidForRsaKey() throws Exception {
        // Arrange
        String customKid = "my-rsa-key-2024";
        var rsa = new RSAKeyGenerator(2048)
                .keyID(customKid)
                .generate();
        Map<String, Object> rawKey = (Map<String, Object>) rsa.toPublicJWK().toJSONObject();

        // Act
        RSAKey result = (RSAKey) AgentJwkMapper.fromRaw(rawKey);

        // Assert
        assertEquals(customKid, result.getKid());
    }

    @Test
    void shouldRejectRsaKeyWithPrivateComponent() throws Exception {
        // Arrange: Generate RSA key WITH private key material
        var rsa = new RSAKeyGenerator(2048)
                .keyID(TEST_KID)
                .generate();
        // Convert to full (private) JWK
        Map<String, Object> rawKey = (Map<String, Object>) rsa.toJSONObject();

        // Act & Assert
        InvalidClientMetadataException ex = assertThrows(
                InvalidClientMetadataException.class,
                () -> AgentJwkMapper.fromRaw(rawKey)
        );
        assertTrue(ex.getMessage().contains("private key material"));
    }

    @Test
    void shouldPreserveAlgorithmAndUseForRsa() throws Exception {
        // Arrange
        var rsa = new RSAKeyGenerator(2048)
                .keyID(TEST_KID)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .generate();
        Map<String, Object> rawKey = (Map<String, Object>) rsa.toPublicJWK().toJSONObject();

        // Act
        RSAKey result = (RSAKey) AgentJwkMapper.fromRaw(rawKey);

        // Assert
        assertEquals("RS256", result.getAlg());
        assertEquals("sig", result.getUse());
    }

    @Test
    void shouldParseValidEcP256PublicKey() throws Exception {
        // Arrange: Generate EC key with P-256 curve
        var ec = new ECKeyGenerator(Curve.P_256)
                .keyID(TEST_KID)
                .generate();
        Map<String, Object> rawKey = (Map<String, Object>) ec.toPublicJWK().toJSONObject();

        // Act
        JWK result = AgentJwkMapper.fromRaw(rawKey);

        // Assert
        assertNotNull(result);
        assertInstanceOf(ECKey.class, result);
        ECKey ecKey = (ECKey) result;
        assertEquals(TEST_KID, ecKey.getKid());
        assertEquals("EC", ecKey.getKty());
        assertEquals("P-256", ecKey.getCrv());
        assertNotNull(ecKey.getX());
        assertNotNull(ecKey.getY());
    }

    @Test
    void shouldRejectEcKeyWithPrivateComponent() throws Exception {
        // Arrange: Generate EC key WITH private key material
        var ec = new ECKeyGenerator(Curve.P_256)
                .keyID(TEST_KID)
                .generate();
        // Convert to full (private) JWK
        Map<String, Object> rawKey = (Map<String, Object>) ec.toJSONObject();

        // Act & Assert
        InvalidClientMetadataException ex = assertThrows(
                InvalidClientMetadataException.class,
                () -> AgentJwkMapper.fromRaw(rawKey)
        );
        assertTrue(ex.getMessage().contains("private key material"));
    }

    @Test
    void shouldPreserveAlgorithmAndUseForEc() throws Exception {
        // Arrange
        var ec = new ECKeyGenerator(Curve.P_256)
                .keyID(TEST_KID)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.ES256)
                .generate();
        Map<String, Object> rawKey = (Map<String, Object>) ec.toPublicJWK().toJSONObject();

        // Act
        ECKey result = (ECKey) AgentJwkMapper.fromRaw(rawKey);

        // Assert
        assertEquals("ES256", result.getAlg());
        assertEquals("sig", result.getUse());
    }

    @Test
    void shouldParseValidOkpEd25519PublicKey() throws Exception {
        // Arrange: Generate OKP key with Ed25519 curve
        var okp = new OctetKeyPairGenerator(Curve.Ed25519)
                .keyID(TEST_KID)
                .generate();
        Map<String, Object> rawKey = (Map<String, Object>) okp.toPublicJWK().toJSONObject();

        // Act
        JWK result = AgentJwkMapper.fromRaw(rawKey);

        // Assert
        assertNotNull(result);
        assertInstanceOf(OKPKey.class, result);
        OKPKey okpKey = (OKPKey) result;
        assertEquals(TEST_KID, okpKey.getKid());
        assertEquals("OKP", okpKey.getKty());
        assertEquals("Ed25519", okpKey.getCrv());
        assertNotNull(okpKey.getX());
    }

    @Test
    void shouldRejectOkpKeyWithPrivateComponent() throws Exception {
        // Arrange: Generate OKP key WITH private key material
        var okp = new OctetKeyPairGenerator(Curve.Ed25519)
                .keyID(TEST_KID)
                .generate();
        // Convert to full (private) JWK
        Map<String, Object> rawKey = (Map<String, Object>) okp.toJSONObject();

        // Act & Assert
        InvalidClientMetadataException ex = assertThrows(
                InvalidClientMetadataException.class,
                () -> AgentJwkMapper.fromRaw(rawKey)
        );
        assertTrue(ex.getMessage().contains("private key material"));
    }

    @Test
    void shouldPreserveAlgorithmAndUseForOkp() throws Exception {
        // Arrange
        var okp = new OctetKeyPairGenerator(Curve.Ed25519)
                .keyID(TEST_KID)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.EdDSA)
                .generate();
        Map<String, Object> rawKey = (Map<String, Object>) okp.toPublicJWK().toJSONObject();

        // Act
        OKPKey result = (OKPKey) AgentJwkMapper.fromRaw(rawKey);

        // Assert
        assertEquals("EdDSA", result.getAlg());
        assertEquals("sig", result.getUse());
    }

    @Test
    void shouldRejectKeyWithoutKid() throws Exception {
        // Arrange: Generate RSA key and remove the kid
        var rsa = new RSAKeyGenerator(2048)
                .generate();
        Map<String, Object> rawKey = new HashMap<>((Map<String, Object>) rsa.toPublicJWK().toJSONObject());
        rawKey.remove("kid");

        // Act & Assert
        InvalidClientMetadataException ex = assertThrows(
                InvalidClientMetadataException.class,
                () -> AgentJwkMapper.fromRaw(rawKey)
        );
        assertTrue(ex.getMessage().contains("kid"));
    }

    @Test
    void shouldRejectKeyWithBlankKid() throws Exception {
        // Arrange: Generate RSA key and set kid to blank
        var rsa = new RSAKeyGenerator(2048)
                .generate();
        Map<String, Object> rawKey = new HashMap<>((Map<String, Object>) rsa.toPublicJWK().toJSONObject());
        rawKey.put("kid", "");

        // Act & Assert
        InvalidClientMetadataException ex = assertThrows(
                InvalidClientMetadataException.class,
                () -> AgentJwkMapper.fromRaw(rawKey)
        );
        assertTrue(ex.getMessage().contains("kid"));
    }

    @Test
    void shouldRejectSymmetricOctKey() {
        // Arrange: Create a symmetric oct key manually.
        // Note: oct (symmetric) keys are always considered private, so this will be rejected
        // at the isPrivate() check before we reach the key type check. This test validates
        // that symmetric keys are properly rejected (along with all other private keys).
        Map<String, Object> rawKey = new HashMap<>();
        rawKey.put("kty", "oct");
        rawKey.put("kid", TEST_KID);
        rawKey.put("k", "AyM1SysPpbyDfgZld3umj1qzKObwVMkoqQ-EstJQLr_T-1qS0gZH75aKtMN3Yj0iPS4hcgUuTwjAzZr1Z9CAow");

        // Act & Assert
        InvalidClientMetadataException ex = assertThrows(
                InvalidClientMetadataException.class,
                () -> AgentJwkMapper.fromRaw(rawKey)
        );
        // oct keys are treated as private, so they're rejected on the isPrivate() check
        assertTrue(
                ex.getMessage().contains("private key material"),
                "Expected message to mention private key material, but got: " + ex.getMessage()
        );
    }

    @Test
    void shouldRejectNullKey() {
        // Act & Assert
        InvalidClientMetadataException ex = assertThrows(
                InvalidClientMetadataException.class,
                () -> AgentJwkMapper.fromRaw(null)
        );
        assertTrue(ex.getMessage().contains("empty"));
    }

    @Test
    void shouldRejectEmptyKey() {
        // Act & Assert
        InvalidClientMetadataException ex = assertThrows(
                InvalidClientMetadataException.class,
                () -> AgentJwkMapper.fromRaw(new HashMap<>())
        );
        assertTrue(ex.getMessage().contains("empty"));
    }

    @Test
    void shouldRejectKeyWithoutKty() {
        // Arrange: Create a malformed key without kty
        Map<String, Object> rawKey = new HashMap<>();
        rawKey.put("kid", TEST_KID);
        rawKey.put("n", "some-modulus");
        rawKey.put("e", "AQAB");

        // Act & Assert
        InvalidClientMetadataException ex = assertThrows(
                InvalidClientMetadataException.class,
                () -> AgentJwkMapper.fromRaw(rawKey)
        );
        assertTrue(ex.getMessage().contains("Invalid JWK"));
    }

    @Test
    void shouldRejectKeyWithUnknownKty() {
        // Arrange: Create a key with an unknown key type
        Map<String, Object> rawKey = new HashMap<>();
        rawKey.put("kty", "UNKNOWN");
        rawKey.put("kid", TEST_KID);

        // Act & Assert
        InvalidClientMetadataException ex = assertThrows(
                InvalidClientMetadataException.class,
                () -> AgentJwkMapper.fromRaw(rawKey)
        );
        assertTrue(ex.getMessage().contains("Unsupported"));
    }

    @Test
    void shouldRejectMalformedRsaKey() {
        // Arrange: Create a malformed RSA key (missing modulus)
        Map<String, Object> rawKey = new HashMap<>();
        rawKey.put("kty", "RSA");
        rawKey.put("kid", TEST_KID);
        rawKey.put("e", "AQAB");
        // Missing 'n' (modulus)

        // Act & Assert
        InvalidClientMetadataException ex = assertThrows(
                InvalidClientMetadataException.class,
                () -> AgentJwkMapper.fromRaw(rawKey)
        );
        assertTrue(ex.getMessage().contains("Invalid JWK"));
    }

    @Test
    void shouldRejectMalformedEcKey() {
        // Arrange: Create a malformed EC key (missing coordinates)
        Map<String, Object> rawKey = new HashMap<>();
        rawKey.put("kty", "EC");
        rawKey.put("kid", TEST_KID);
        rawKey.put("crv", "P-256");
        rawKey.put("x", "WKn-ZIGevcwGIyyrzFoZNBdaq9_TsqzGl96oc0CWuis");
        // Missing 'y' coordinate

        // Act & Assert
        InvalidClientMetadataException ex = assertThrows(
                InvalidClientMetadataException.class,
                () -> AgentJwkMapper.fromRaw(rawKey)
        );
        assertTrue(ex.getMessage().contains("Invalid JWK"));
    }

    @Test
    void shouldRejectMalformedOkpKey() {
        // Arrange: Create a malformed OKP key (missing x coordinate)
        Map<String, Object> rawKey = new HashMap<>();
        rawKey.put("kty", "OKP");
        rawKey.put("kid", TEST_KID);
        rawKey.put("crv", "Ed25519");
        // Missing 'x' coordinate

        // Act & Assert
        InvalidClientMetadataException ex = assertThrows(
                InvalidClientMetadataException.class,
                () -> AgentJwkMapper.fromRaw(rawKey)
        );
        assertTrue(ex.getMessage().contains("Invalid JWK"));
    }

}
