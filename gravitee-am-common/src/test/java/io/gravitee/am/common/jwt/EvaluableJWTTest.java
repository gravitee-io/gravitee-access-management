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
package io.gravitee.am.common.jwt;

import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author GraviteeSource Team
 */
public class EvaluableJWTTest {

    @Test
    public void shouldExposeScopesAsSet() {
        JWT jwt = new JWT();
        jwt.setSub("user");
        jwt.setScope("openid profile email");

        EvaluableJWT evaluable = new EvaluableJWT(jwt);

        assertEquals("user", evaluable.getSub());
        assertEquals("openid profile email", evaluable.getScope());
        assertTrue(evaluable.get("scopes") instanceof Set);
        assertEquals(Set.of("openid", "profile", "email"), evaluable.get("scopes"));
    }

    @Test
    public void shouldExposeEmptyScopesWhenScopeMissing() {
        JWT jwt = new JWT();
        jwt.setSub("user");

        EvaluableJWT evaluable = new EvaluableJWT(jwt);

        assertEquals(Set.of(), evaluable.get("scopes"));
    }

    @Test
    public void shouldExposeEmptyScopesWhenScopeIsEmpty() {
        JWT jwt = new JWT();
        jwt.setScope("");

        EvaluableJWT evaluable = new EvaluableJWT(jwt);

        assertEquals(Set.of(), evaluable.get("scopes"));
    }

    @Test
    public void shouldDeduplicateScopes() {
        JWT jwt = new JWT();
        jwt.setScope("openid openid profile");

        EvaluableJWT evaluable = new EvaluableJWT(jwt);

        assertEquals(Set.of("openid", "profile"), evaluable.get("scopes"));
    }

    @Test
    public void shouldCopyAllJwtClaims() {
        JWT jwt = new JWT();
        jwt.setSub("user");
        jwt.setAud("client-id");
        jwt.setIss("https://example.com");
        jwt.setScope("openid");
        jwt.put("custom", "value");

        EvaluableJWT evaluable = new EvaluableJWT(jwt);

        assertEquals("user", evaluable.get(Claims.SUB));
        assertEquals("client-id", evaluable.get(Claims.AUD));
        assertEquals("https://example.com", evaluable.get(Claims.ISS));
        assertEquals("value", evaluable.get("custom"));
    }
}
