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
package io.gravitee.am.extensiongrant.api;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.util.Base64URL;
import io.gravitee.am.repository.oauth2.model.request.TokenRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdJagAssertionsTest {

    @Test
    void shouldRecognizeIdJagTypedAssertion() {
        assertTrue(IdJagAssertions.isIdJag(request(assertion(new JOSEObjectType("oauth-id-jag+jwt")))));
    }

    @Test
    void shouldRecognizeIdJagTypeRegardlessOfCase() {
        assertTrue(IdJagAssertions.isIdJag(request(assertion(new JOSEObjectType("OAUTH-ID-JAG+JWT")))));
    }

    @Test
    void shouldNotRecognizeJwtTypedAssertion() {
        assertFalse(IdJagAssertions.isIdJag(request(assertion(JOSEObjectType.JWT))));
    }

    @Test
    void shouldNotRecognizeUntypedAssertion() {
        assertFalse(IdJagAssertions.isIdJag(request(assertion(null))));
    }

    @Test
    void shouldNotRecognizeUnparsableAssertion() {
        assertFalse(IdJagAssertions.isIdJag(request("not-a-jwt")));
        assertFalse(IdJagAssertions.isIdJag(request("@@@.e30.c2ln")));
    }

    @Test
    void shouldNotRecognizeRequestWithoutAssertion() {
        assertFalse(IdJagAssertions.isIdJag(request(null)));
        assertFalse(IdJagAssertions.isIdJag(new TokenRequest()));
    }

    private static TokenRequest request(String assertion) {
        TokenRequest request = new TokenRequest();
        request.setRequestParameters(assertion == null ? Map.of() : Map.of("assertion", assertion));
        return request;
    }

    private static String assertion(JOSEObjectType type) {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).type(type).build();
        return header.toBase64URL() + "." + Base64URL.encode("{}") + "." + Base64URL.encode("signature");
    }
}
