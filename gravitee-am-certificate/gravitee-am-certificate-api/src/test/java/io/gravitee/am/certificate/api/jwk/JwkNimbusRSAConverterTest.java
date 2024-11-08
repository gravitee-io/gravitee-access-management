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
package io.gravitee.am.certificate.api.jwk;

import com.nimbusds.jose.jwk.RSAKey;
import io.gravitee.am.model.jose.JWK;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwkNimbusRSAConverterTest {

    @Test
    void should_generate_jwk() throws Exception{
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(1024);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();

        RSAKey rsaKey = new RSAKey.Builder(publicKey).build();

        JwkNimbusConverter nimbusConverter = new JwkNimbusRSAConverter(rsaKey, false, Set.of("sig"), "RSA256");
        JWK jwk = nimbusConverter.createJwk().findFirst().orElseThrow();

        assertTrue(jwk instanceof io.gravitee.am.model.jose.RSAKey);
        assertSame("RSA", jwk.getKty());
        assertSame("sig", jwk.getUse());
        assertSame("RSA256", jwk.getAlg());
    }

    @Test
    void include_private_requires_private_key() throws Exception{
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(1024);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();

        RSAKey rsaKey = new RSAKey.Builder(publicKey).privateKey(keyPair.getPrivate()).build();

        JwkNimbusConverter nimbusConverter = new JwkNimbusRSAConverter(rsaKey, false, Set.of("sig"), "RSA256");
        JWK jwk = nimbusConverter.createJwk().findFirst().orElseThrow();

        assertTrue(jwk instanceof io.gravitee.am.model.jose.RSAKey);
        assertSame("RSA", jwk.getKty());
        assertSame("sig", jwk.getUse());
        assertSame("RSA256", jwk.getAlg());
    }

}