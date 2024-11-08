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

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import io.gravitee.am.model.jose.JWK;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwkNimbusECConverterTest {

    @Test
    void should_generate_jwk() throws Exception{
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(256);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        ECPublicKey publicKey = (ECPublicKey) keyPair.getPublic();

        ECKey ecKey = new ECKey.Builder(Curve.P_256, publicKey).build();

        JwkNimbusConverter nimbusConverter = new JwkNimbusECConverter(ecKey, false, Set.of("sig"), "EC256");
        JWK jwk = nimbusConverter.createJwk().findFirst().orElseThrow();

        assertTrue(jwk instanceof io.gravitee.am.model.jose.ECKey);
        assertSame("EC", jwk.getKty());
        assertSame("sig", jwk.getUse());
        assertSame("EC256", jwk.getAlg());
    }

    @Test
    void include_private_requires_private_key() throws Exception{
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(256);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        ECPublicKey publicKey = (ECPublicKey) keyPair.getPublic();

        ECKey ecKey = new ECKey.Builder(Curve.P_256, publicKey)
                .privateKey(keyPair.getPrivate())
                .build();

        JwkNimbusConverter nimbusConverter = new JwkNimbusECConverter(ecKey, true, Set.of("sig"), "EC256");
        JWK jwk = nimbusConverter.createJwk().findFirst().orElseThrow();

        assertTrue(jwk instanceof io.gravitee.am.model.jose.ECKey);
        assertSame("EC", jwk.getKty());
        assertSame("sig", jwk.getUse());
        assertSame("EC256", jwk.getAlg());
    }

}