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
package io.gravitee.am.gateway.handler.oauth2.service.grant;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.util.Base64URL;
import io.gravitee.am.common.jwt.JwtType;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;

public final class AssertionFixtures {

    private static final JWSAlgorithm UNCHECKED_ALGORITHM = JWSAlgorithm.RS256;
    private static final String UNCHECKED_SIGNATURE = "sig";

    private AssertionFixtures() {
    }

    public static TokenRequest jwtBearerRequest(String assertion) {
        TokenRequest request = new TokenRequest();
        request.setClientId("client-id");
        request.setGrantType(GrantType.JWT_BEARER);
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        if (assertion != null) {
            parameters.add(Parameters.ASSERTION, assertion);
        }
        request.setParameters(parameters);
        return request;
    }

    public static String idJagAssertion() {
        return idJagAssertionCarrying("{}");
    }

    public static String idJagAssertionCarrying(String payload) {
        return assertion(new JWSHeader.Builder(UNCHECKED_ALGORITHM).type(new JOSEObjectType(JwtType.ID_JAG.getValue())).build(), payload);
    }

    public static String plainJwtAssertion() {
        return assertionWithHeader(new JWSHeader.Builder(UNCHECKED_ALGORITHM).type(JOSEObjectType.JWT).build());
    }

    public static String untypedAssertion() {
        return assertionWithHeader(new JWSHeader.Builder(UNCHECKED_ALGORITHM).build());
    }

    public static String assertionWithHeader(JWSHeader header) {
        return assertion(header, "{}");
    }

    private static String assertion(JWSHeader header, String payload) {
        return header.toBase64URL() + "." + Base64URL.encode(payload) + "." + Base64URL.encode(UNCHECKED_SIGNATURE);
    }
}
