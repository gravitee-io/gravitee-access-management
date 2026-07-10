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
package io.gravitee.am.jwt;

import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.common.jwt.JWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DefaultJWTBuilderTest {

    private DefaultJWTBuilder jwtBuilder;

    @BeforeEach
    public void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        jwtBuilder = new DefaultJWTBuilder(keyPair.getPrivate(), "RS256", "my-kid");
    }

    @Test
    public void shouldSignWithDefaultJwtType() throws Exception {
        final var jwt = new JWT();
        jwt.setSub("user-1");

        final var signedJWT = SignedJWT.parse(jwtBuilder.sign(jwt));

        assertEquals("JWT", signedJWT.getHeader().getType().getType());
        assertEquals("my-kid", signedJWT.getHeader().getKeyID());
        assertEquals("user-1", signedJWT.getJWTClaimsSet().getSubject());
    }

    @Test
    public void shouldSignWithCustomJoseType() throws Exception {
        final var jwt = new JWT();
        jwt.setSub("user-1");

        final var signedJWT = SignedJWT.parse(jwtBuilder.sign(jwt, "command+jwt"));

        assertEquals("command+jwt", signedJWT.getHeader().getType().getType());
        assertEquals("my-kid", signedJWT.getHeader().getKeyID());
        assertEquals("user-1", signedJWT.getJWTClaimsSet().getSubject());
    }

    @Test
    public void customTypeShouldBeUnsupportedByDefaultBuilders() {
        final var noJwtBuilder = new NoJWTBuilder();
        assertThrows(UnsupportedOperationException.class, () -> noJwtBuilder.sign(new JWT(), "command+jwt"));
    }
}
