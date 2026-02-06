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
package io.gravitee.am.gateway.handler.common.vertx.web.auth.handler.impl;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.handler.OAuth2AuthResponse;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.OAuth2AuthProvider;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.ErrorHandler;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpStatusCode;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.http.HttpClientRequest;
import org.assertj.core.api.AssertionsForClassTypes;
import org.assertj.core.api.AssertionsForInterfaceTypes;
import org.junit.Test;

import java.util.Map;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

public class    OAuth2AuthHandlerImplTest extends RxWebTestBase {

    private static final String TEST_SUB = "test-sub";
    private OAuth2AuthHandlerImpl handler;
    private OAuth2AuthProvider provider;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        provider = mock();
        handler = new OAuth2AuthHandlerImpl(provider, mock());
        router.route("/test")
                .handler(handler)
                .handler(checkContextAssertions())
                .handler(rc -> rc.response().setStatusCode(200).setStatusMessage("OK").end())
                .failureHandler(new ErrorHandler());
    }

    @Test
    public void noToken_unauthorized() throws Exception {
        testRequest(HttpMethod.GET, "/test", HttpStatusCode.UNAUTHORIZED_401, "Unauthorized");
    }


    @Test
    public void malformedAuthorization_badRequest() throws Exception {
        testRequest(HttpMethod.GET, "/test", withAuthorization("this-wont-work"), HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);
    }

    @Test
    public void notBearer_unauthorized() throws Exception {
        testRequest(HttpMethod.GET, "/test", withAuthorization("NotBearer eyrajwtsomething=="), HttpStatusCode.UNAUTHORIZED_401, "Unauthorized", null);
    }

    @Test
    public void validToken_basic() throws Exception {
        handler.extractRawToken(true);
        givenTokenDecodesTo(Map.of(Claims.SUB, TEST_SUB));

        testRequest(HttpMethod.GET, "/test", withAuthorization("Bearer eyrajwtsomething=="), HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void validToken_shouldExtractDataToContext() throws Exception {
        handler.extractToken(true);
        handler.extractClient(true);

        var tokenClaims = Map.of(Claims.SUB, TEST_SUB);
        givenTokenDecodesTo(tokenClaims);
        assertAfterRequest(
                rc -> AssertionsForInterfaceTypes.assertThat((Map) rc.get(ConstantKeys.TOKEN_CONTEXT_KEY)).containsExactlyInAnyOrderEntriesOf(tokenClaims),
                rc -> AssertionsForClassTypes.assertThat((Object) rc.get(ConstantKeys.CLIENT_CONTEXT_KEY)).isNotNull());

        testRequest(HttpMethod.GET, "/test", withAuthorization("Bearer pretend-its-a-token"), HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void forceEndUserToken_notEndUserToken_unauthorized() throws Exception{
        handler.forceEndUserToken(true);
        var tokenClaims = Map.of(Claims.SUB, "test", Claims.AUD, "test");
        givenTokenDecodesTo(tokenClaims);
        testRequest(HttpMethod.GET, "/test", withAuthorization("Bearer pretend-its-a-token"), HttpStatusCode.UNAUTHORIZED_401, "Unauthorized", null);
    }

    @Test
    public void forceClientToken_notClientToken_unauthorized() throws Exception{
        handler.forceClientToken(true);
        var tokenClaims = Map.of(Claims.SUB, "sub", Claims.AUD, "aud");
        givenTokenDecodesTo(tokenClaims);
        testRequest(HttpMethod.GET, "/test", withAuthorization("Bearer pretend-its-a-token"), HttpStatusCode.UNAUTHORIZED_401, "Unauthorized", null);
    }

    @Test
    public void noToken_shouldReturnMeaningfulErrorMessage() throws Exception {
        testRequest(HttpMethod.GET, "/test", HttpStatusCode.UNAUTHORIZED_401, "Unauthorized",
                errorJson("Missing access token. The access token must be sent using the Authorization header field (Bearer scheme) or the 'access_token' body parameter", 401));
    }

    @Test
    public void notBearer_shouldReturnMeaningfulErrorMessage() throws Exception {
        testRequest(HttpMethod.GET, "/test", withAuthorization("Basic dXNlcjpwYXNz"),
                HttpStatusCode.UNAUTHORIZED_401, "Unauthorized",
                errorJson("Authorization header must use Bearer scheme", 401));
    }

    private String errorJson(String message, int httpStatus) {
        return "{\n  \"message\" : \"" + message + "\",\n  \"http_status\" : " + httpStatus + "\n}";
    }

    private void givenTokenDecodesTo(Map<String, ?> claims) {
        doAnswer(invocation -> {
            var handler = (Handler<AsyncResult<OAuth2AuthResponse>>) invocation.getArguments()[2];
            var token = new JWT(claims);
            var client = new Client();

            handler.handle(Future.succeededFuture(new OAuth2AuthResponse(token, client)));
            return null;
        }).when(provider).decodeToken(any(), anyBoolean(), any());
    }


    private Consumer<HttpClientRequest> withAuthorization(String authorization) {
        return req -> req.putHeader(HttpHeaders.AUTHORIZATION, authorization);
    }

}
