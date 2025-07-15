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
package io.gravitee.am.extensiongrant.jwtbearer.parser;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.gravitee.am.common.exception.jwt.SignatureException;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.extensiongrant.api.exceptions.InvalidGrantException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JWKSJwtParserTest {

    private HttpServer mockServer;
    private RSAKey rsaKey;
    private ECKey ecKey;
    private JWKSet jwkSet;
    private String jwksUrl;

    @BeforeEach
    void setUp() throws Exception {
        rsaKey = new RSAKeyGenerator(2048)
                .keyID("rsa-key-1")
                .generate();

        ecKey = new ECKeyGenerator(Curve.P_256)
                .keyID("ec-key-1")
                .generate();

        jwkSet = new JWKSet(List.of(rsaKey.toPublicJWK(), ecKey.toPublicJWK()));

        mockServer = HttpServer.create(new InetSocketAddress(0), 0);
        mockServer.createContext("/jwks", new JWKSHandler());
        mockServer.setExecutor(null);
        mockServer.start();

        jwksUrl = "http://localhost:" + mockServer.getAddress().getPort() + "/jwks";
    }

    @AfterEach
    void tearDown() {
        if (mockServer != null) {
            mockServer.stop(0);
        }
    }

    @Test
    void should_parse_valid_rsa_signed_jwt() throws Exception {
        // Given
        JWKSJwtParser parser = new JWKSJwtParser(jwksUrl);
        String signedToken = createSignedJWT(rsaKey, "rsa-key-1", JWSAlgorithm.RS256);

        // When
        JWT result = parser.parse(signedToken);

        // Then
        assertNotNull(result);
        assertEquals("test-subject", result.getSub());
        assertEquals("test-issuer", result.getIss());
    }

    @Test
    void should_parse_valid_ec_signed_jwt() throws Exception {
        // Given
        JWKSJwtParser parser = new JWKSJwtParser(jwksUrl);
        String signedToken = createSignedJWT(ecKey, "ec-key-1", JWSAlgorithm.ES256);

        // When
        JWT result = parser.parse(signedToken);

        // Then
        assertNotNull(result);
        assertEquals("test-subject", result.getSub());
        assertEquals("test-issuer", result.getIss());
    }

    @Test
    void should_throw_exception_when_signature_is_invalid() throws Exception {
        // Given
        JWKSJwtParser parser = new JWKSJwtParser(jwksUrl);

        RSAKey differentKey = new RSAKeyGenerator(2048)
                .keyID("rsa-key-1")
                .generate();
        String invalidToken = createSignedJWT(differentKey, "rsa-key-1", JWSAlgorithm.RS256);

        // When & Then
        InvalidGrantException exception = assertThrows(InvalidGrantException.class,
                () -> parser.parse(invalidToken));
        assertEquals("Token's signature is invalid", exception.getMessage());
    }

    @Test
    void should_throw_exception_when_key_not_found() throws Exception {
        // Given
        JWKSJwtParser parser = new JWKSJwtParser(jwksUrl);
        String signedToken = createSignedJWT(rsaKey, "unknown-key-id", JWSAlgorithm.RS256);

        // When & Then
        SignatureException exception = assertThrows(SignatureException.class,
                () -> parser.parse(signedToken));
        assertTrue(exception.getMessage().contains("No matching key found for kid: unknown-key-id"));
    }

    @Test
    void should_throw_exception_when_token_is_malformed() throws Exception {
        // Given
        JWKSJwtParser parser = new JWKSJwtParser(jwksUrl);
        String malformedToken = "invalid.jwt.token";

        // When & Then
        SignatureException exception = assertThrows(SignatureException.class,
                () -> parser.parse(malformedToken));
        assertTrue(exception.getMessage().contains("Unabled to parse token"));
    }

    private String createSignedJWT(JWK key, String keyId, JWSAlgorithm algorithm) throws JOSEException {
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("test-subject")
                .issuer("test-issuer")
                .audience("test-audience")
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                .issueTime(new Date())
                .claim("custom", "value")
                .build();

        JWSHeader header = new JWSHeader.Builder(algorithm)
                .keyID(keyId)
                .build();

        SignedJWT signedJWT = new SignedJWT(header, claimsSet);

        JWSSigner signer;
        if (key instanceof RSAKey) {
            signer = new RSASSASigner((RSAKey) key);
        } else if (key instanceof ECKey) {
            signer = new ECDSASigner((ECKey) key);
        } else {
            throw new IllegalArgumentException("Unsupported key type");
        }

        signedJWT.sign(signer);
        return signedJWT.serialize();
    }

    private class JWKSHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = jwkSet.toString();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }
}
