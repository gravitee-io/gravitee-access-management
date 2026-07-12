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
package io.gravitee.am.gateway.handler.scim.resources.users;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.common.dpop.DPoPProofValidator;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.handler.OAuth2AuthHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.handler.OAuth2AuthResponse;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.OAuth2AuthProvider;
import io.gravitee.am.gateway.handler.scim.mapper.ScimErrorMapper;
import io.gravitee.am.gateway.handler.scim.model.ListResponse;
import io.gravitee.am.gateway.handler.scim.resources.ErrorHandler;
import io.gravitee.am.gateway.handler.scim.service.ProvisioningUserService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.http.HttpClientRequest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Map;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end wiring test: a DPoP-bound (cnf.jkt) access token is enforced by the shared OAuth2 bearer
 * gate before it can reach the SCIM Users endpoint. Mirrors the gate wiring set up in SCIMProvider.
 *
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ScimUsersDPoPEnforcementTest extends RxWebTestBase {

    private static final String THE_JKT = "the-jwk-thumbprint";

    @Mock
    private ProvisioningUserService userService;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ObjectWriter objectWriter;
    @Mock
    private Domain domain;
    @Mock
    private SubjectManager subjectManager;
    @Mock
    private OAuth2AuthProvider oAuth2AuthProvider;
    @Mock
    private DPoPProofValidator dpopProofValidator;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        when(objectWriter.writeValueAsString(any())).thenReturn("UserObject");
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(objectWriter);
        UsersEndpoint usersEndpoint = new UsersEndpoint(domain, userService, objectMapper, subjectManager);

        OAuth2AuthHandler oAuth2AuthHandler = OAuth2AuthHandler.create(oAuth2AuthProvider, "scim");
        oAuth2AuthHandler.extractToken(true);
        oAuth2AuthHandler.extractClient(true);
        oAuth2AuthHandler.dpopProofValidator(dpopProofValidator);

        router.route().handler(oAuth2AuthHandler);
        router.get("/Users")
                .handler(usersEndpoint::list)
                .failureHandler(new ErrorHandler(new ScimErrorMapper(false)));
    }

    @Test
    public void dpopBoundToken_withValidProof_reachesScimUsers() throws Exception {
        givenAccessTokenDecodesToDpopBound();
        when(dpopProofValidator.validateForResource(any(), any(), any())).thenReturn(Single.just(THE_JKT));
        when(userService.list(any(), anyInt(), anyInt(), anyString())).thenReturn(Single.just(new ListResponse<>()));

        testRequest(HttpMethod.GET, "/Users", withAuthorization("DPoP the-token"), 200, "OK", "UserObject");

        verify(userService).list(any(), anyInt(), anyInt(), anyString());
    }

    @Test
    public void dpopBoundToken_underBearer_isBlockedBeforeScimUsers() throws Exception {
        givenAccessTokenDecodesToDpopBound();

        testRequest(HttpMethod.GET, "/Users", withAuthorization("Bearer the-token"), 401, "Unauthorized", null);

        verify(userService, never()).list(any(), anyInt(), anyInt(), anyString());
        verify(dpopProofValidator, never()).validateForResource(any(), any(), any());
    }

    private void givenAccessTokenDecodesToDpopBound() {
        doAnswer(invocation -> {
            Handler<AsyncResult<OAuth2AuthResponse>> handler = invocation.getArgument(2);
            JWT token = new JWT(Map.of(
                    Claims.SUB, "client-id",
                    Claims.AUD, "client-id",
                    Claims.SCOPE, "scim",
                    Claims.CNF, Map.of("jkt", THE_JKT)));
            handler.handle(Future.succeededFuture(new OAuth2AuthResponse(token, new Client())));
            return null;
        }).when(oAuth2AuthProvider).decodeToken(any(), anyBoolean(), any());
    }

    private Consumer<HttpClientRequest> withAuthorization(String authorization) {
        return req -> req.putHeader(HttpHeaders.AUTHORIZATION, authorization);
    }
}
