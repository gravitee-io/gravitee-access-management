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
package io.gravitee.am.gateway.handler.aauth.service;

import com.nimbusds.jose.jwk.JWKSet;
import io.gravitee.am.gateway.handler.aauth.test.fixtures.TestAgentKeyPairFactory;
import org.junit.Test;

import static org.junit.Assert.*;

public class JWKSDocumentTest {

    @Test
    public void shouldFindKeyByKid() throws Exception {
        String x = TestAgentKeyPairFactory.ed25519PublicKeyX();
        String jwksJson = """
                {"keys":[{"kty":"OKP","crv":"Ed25519","kid":"key-1","x":"%s"}]}""".formatted(x);

        JWKSDocument doc = new JWKSDocument(JWKSet.parse(jwksJson));

        assertNotNull(doc.findByKid("key-1"));
        assertEquals(1, doc.size());
    }

    @Test
    public void shouldReturnNull_whenKidNotFound() throws Exception {
        String x = TestAgentKeyPairFactory.ed25519PublicKeyX();
        String jwksJson = """
                {"keys":[{"kty":"OKP","crv":"Ed25519","kid":"key-1","x":"%s"}]}""".formatted(x);

        JWKSDocument doc = new JWKSDocument(JWKSet.parse(jwksJson));

        assertNull(doc.findByKid("key-999"));
    }

    @Test
    public void shouldHandleMultipleKeys() throws Exception {
        String x = TestAgentKeyPairFactory.ed25519PublicKeyX();
        String jwksJson = """
                {"keys":[
                    {"kty":"OKP","crv":"Ed25519","kid":"ed-key","x":"%s"},
                    {"kty":"OKP","crv":"Ed25519","kid":"ed-key-2","x":"%s"}
                ]}""".formatted(x, x);

        JWKSDocument doc = new JWKSDocument(JWKSet.parse(jwksJson));

        assertEquals(2, doc.size());
        assertNotNull(doc.findByKid("ed-key"));
        assertNotNull(doc.findByKid("ed-key-2"));
    }
}
