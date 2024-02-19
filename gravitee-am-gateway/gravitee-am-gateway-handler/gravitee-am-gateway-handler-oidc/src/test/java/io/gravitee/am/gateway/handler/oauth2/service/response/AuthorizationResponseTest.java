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
package io.gravitee.am.gateway.handler.oauth2.service.response;

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.gateway.handler.oauth2.service.token.impl.AccessToken;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Date;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
class AuthorizationResponseTest {

    private final static String STATE = "state+value==";

    @ParameterizedTest
    @MethodSource("responseProvider")
    void should_manage_state_encoding(AuthorizationResponse response) {
        assertEquals(UriBuilder.encodeURIComponent(STATE), response.params().get(Parameters.STATE));
        assertEquals(UriBuilder.encodeURIComponent(STATE), response.params(true).get(Parameters.STATE));
        assertEquals(STATE, response.params(false).get(Parameters.STATE));
    }

    static Stream<AuthorizationResponse> responseProvider() {
        final var codeResponse = new AuthorizationCodeResponse();
        codeResponse.setCode(UUID.randomUUID().toString());
        codeResponse.setState(STATE);

        final var hybridResponse = new HybridResponse();
        hybridResponse.setCode(UUID.randomUUID().toString());
        hybridResponse.setState(STATE);
        AccessToken accessToken = new AccessToken(UUID.randomUUID().toString());
        accessToken.setExpireAt(new Date());
        hybridResponse.setAccessToken(accessToken);

        final var implicitResponse = new ImplicitResponse();
        implicitResponse.setState(STATE);
        implicitResponse.setAccessToken(accessToken);

        final var idTokenResponse = new IDTokenResponse();
        idTokenResponse.setIdToken(UUID.randomUUID().toString());
        idTokenResponse.setState(STATE);

        return Stream.of(codeResponse, implicitResponse, hybridResponse, idTokenResponse);
    }

}
