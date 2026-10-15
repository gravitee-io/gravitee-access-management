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
package io.gravitee.am.gateway.handler.common.oauth2;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.jwt.JwtType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.gravitee.am.gateway.handler.common.jwt.JWTService.TokenType.ACCESS_TOKEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class IntrospectionResultTest {

    @Test
    void shouldMergeWithheldClaimsIntoCopyKeepingSignedClaims() {
        JWT jwt = new JWT(Map.of("sub", "user", "role", "signed"));
        jwt.setType(JwtType.ID_JAG);
        IntrospectionResult result = new IntrospectionResult(jwt, "client", ACCESS_TOKEN, Map.of("role", "stored", "email", "user@acme.com"));

        JWT merged = result.jwtWithWithheldClaims();

        assertEquals(Map.of("sub", "user", "role", "signed", "email", "user@acme.com"), merged);
        assertEquals(JwtType.ID_JAG, merged.getType());
        assertFalse(jwt.containsKey("email"));
    }
}
