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
package io.gravitee.am.gateway.handler.vertx.endpoint;

import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.gateway.handler.oauth2.token.AccessToken;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.gravitee.am.gateway.handler.oauth2.token.impl.DefaultAccessToken;
import io.gravitee.am.gateway.handler.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.vertx.handler.ExceptionHandler;
import io.gravitee.am.gateway.handler.vertx.handler.oidc.endpoint.UserInfoEndpoint;
import io.gravitee.am.gateway.handler.vertx.handler.oidc.handler.UserInfoRequestParseHandler;
import io.gravitee.am.gateway.service.UserService;
import io.gravitee.am.model.User;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.Maybe;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class UserInfoEndpointHandlerTest extends RxWebTestBase {

    @InjectMocks
    private UserInfoEndpoint userInfoEndpoint = new UserInfoEndpoint();

    @InjectMocks
    private UserInfoRequestParseHandler userInfoRequestParseHandler = new UserInfoRequestParseHandler();

    @Mock
    private UserService userService;

    @Mock
    private TokenService tokenService;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        router.route(HttpMethod.GET, "/userinfo")
                .handler(userInfoRequestParseHandler)
                .handler(userInfoEndpoint);
        router.route().failureHandler(new ExceptionHandler());
    }

    @Test
    public void shouldNotInvokeUserEndpoint_noBearerToken() throws Exception {
        testRequest(
                HttpMethod.GET, "/userinfo",
                HttpStatusCode.BAD_REQUEST_400, "Bad Request");
    }

    @Test
    public void shouldNotInvokeUserEndpoint_invalidHeaderBearerToken() throws Exception {
        testRequest(
                HttpMethod.GET, "/userinfo", req -> req.putHeader(HttpHeaders.AUTHORIZATION, "Error token"),
                HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);
    }

    @Test
    public void shouldNotInvokeUserEndpoint_invalidToken() throws Exception {
        when(tokenService.getAccessToken(anyString())).thenReturn(Maybe.empty());

        testRequest(
                HttpMethod.GET, "/userinfo", req -> req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token"),
                HttpStatusCode.UNAUTHORIZED_401, "Unauthorized", null);
    }

    @Test
    public void shouldNotInvokeUserEndpoint_expiredToken() throws Exception {
        AccessToken accessToken = new DefaultAccessToken("token");
        ((DefaultAccessToken) accessToken).setExpiresIn(0);

        when(tokenService.getAccessToken(anyString())).thenReturn(Maybe.just(accessToken));

        testRequest(
                HttpMethod.GET, "/userinfo", req -> req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token"),
                HttpStatusCode.UNAUTHORIZED_401, "Unauthorized", null);
    }

    @Test
    public void shouldNotInvokeUserEndpoint_clientOnlyToken() throws Exception {
        AccessToken accessToken = new DefaultAccessToken("token");
        ((DefaultAccessToken) accessToken).setExpiresIn(100);
        ((DefaultAccessToken) accessToken).setScope("openid");

        when(tokenService.getAccessToken(anyString())).thenReturn(Maybe.just(accessToken));

        testRequest(
                HttpMethod.GET, "/userinfo", req -> req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token"),
                HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);
    }

    @Test
    public void shouldNotInvokeUserEndpoint_userNotFound() throws Exception {
        AccessToken accessToken = new DefaultAccessToken("token");
        ((DefaultAccessToken) accessToken).setExpiresIn(100);
        ((DefaultAccessToken) accessToken).setScope("openid");

        when(tokenService.getAccessToken(anyString())).thenReturn(Maybe.just(accessToken));
        when(userService.findById(anyString())).thenReturn(Maybe.empty());

        testRequest(
                HttpMethod.GET, "/userinfo", req -> req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token"),
                HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);
    }

    @Test
    public void shouldInvokeUserEndpoint_noOpenIDScope_noScope() throws Exception {
        AccessToken accessToken = new DefaultAccessToken("token");
        ((DefaultAccessToken) accessToken).setSubject("user");
        ((DefaultAccessToken) accessToken).setExpiresIn(100);

        User user = new User();
        user.setAdditionalInformation(Collections.singletonMap("sub", "user"));

        when(tokenService.getAccessToken(anyString())).thenReturn(Maybe.just(accessToken));
        when(userService.findById(anyString())).thenReturn(Maybe.just(user));

        testRequest(
                HttpMethod.GET, "/userinfo", req -> req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token"),
                HttpStatusCode.UNAUTHORIZED_401, "Unauthorized", null);
    }

    @Test
    public void shouldInvokeUserEndpoint_noOpenIDScope() throws Exception {
        AccessToken accessToken = new DefaultAccessToken("token");
        ((DefaultAccessToken) accessToken).setSubject("user");
        ((DefaultAccessToken) accessToken).setExpiresIn(100);
        ((DefaultAccessToken) accessToken).setScope("read write");

        User user = new User();
        user.setAdditionalInformation(Collections.singletonMap("sub", "user"));

        when(tokenService.getAccessToken(anyString())).thenReturn(Maybe.just(accessToken));
        when(userService.findById(anyString())).thenReturn(Maybe.just(user));

        testRequest(
                HttpMethod.GET, "/userinfo", req -> req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token"),
                HttpStatusCode.UNAUTHORIZED_401, "Unauthorized", null);
    }

    @Test
    public void shouldInvokeUserEndpoint() throws Exception {
        AccessToken accessToken = new DefaultAccessToken("token");
        ((DefaultAccessToken) accessToken).setSubject("user");
        ((DefaultAccessToken) accessToken).setExpiresIn(100);
        ((DefaultAccessToken) accessToken).setScope("openid");

        User user = new User();
        user.setAdditionalInformation(Collections.singletonMap("sub", "user"));

        when(tokenService.getAccessToken(anyString())).thenReturn(Maybe.just(accessToken));
        when(userService.findById(anyString())).thenReturn(Maybe.just(user));

        testRequest(
                HttpMethod.GET, "/userinfo", req -> req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token"),
                HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void shouldInvokeUserEndpoint_claimsRequest() throws Exception {
        AccessToken accessToken = new DefaultAccessToken("token");
        ((DefaultAccessToken) accessToken).setSubject("user");
        ((DefaultAccessToken) accessToken).setExpiresIn(100);
        ((DefaultAccessToken) accessToken).setScope("openid");
        ((DefaultAccessToken) accessToken).setRequestedParameters(Collections.singletonMap("claims", "{\"userinfo\":{\"name\":{\"essential\":true}}}"));

        User user = createUser();

        when(tokenService.getAccessToken(anyString())).thenReturn(Maybe.just(accessToken));
        when(userService.findById(anyString())).thenReturn(Maybe.just(user));

        testRequest(
                HttpMethod.GET,
                "/userinfo",
                req -> req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token"),
                resp -> resp.bodyHandler(body -> {
                    final Map<String, Object> claims = Json.decodeValue(body.toString(), Map.class);
                    assertNotNull(claims);
                    assertEquals(2, claims.size());
                    assertFalse(claims.containsKey(StandardClaims.FAMILY_NAME));
                }),
                HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void shouldInvokeUserEndpoint_scopesRequest() throws Exception {
        AccessToken accessToken = new DefaultAccessToken("token");
        ((DefaultAccessToken) accessToken).setSubject("user");
        ((DefaultAccessToken) accessToken).setExpiresIn(100);
        ((DefaultAccessToken) accessToken).setScope("openid profile");

        User user = createUser();

        when(tokenService.getAccessToken(anyString())).thenReturn(Maybe.just(accessToken));
        when(userService.findById(anyString())).thenReturn(Maybe.just(user));

        testRequest(
                HttpMethod.GET,
                "/userinfo",
                req -> req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token"),
                resp -> resp.bodyHandler(body -> {
                    final Map<String, Object> claims = Json.decodeValue(body.toString(), Map.class);
                    assertNotNull(claims);
                    assertEquals(15, claims.size());
                }),
                HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void shouldInvokeUserEndpoint_scopesRequest_email() throws Exception {
        AccessToken accessToken = new DefaultAccessToken("token");
        ((DefaultAccessToken) accessToken).setSubject("user");
        ((DefaultAccessToken) accessToken).setExpiresIn(100);
        ((DefaultAccessToken) accessToken).setScope("openid email");

        User user = createUser();

        when(tokenService.getAccessToken(anyString())).thenReturn(Maybe.just(accessToken));
        when(userService.findById(anyString())).thenReturn(Maybe.just(user));

        testRequest(
                HttpMethod.GET,
                "/userinfo",
                req -> req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token"),
                resp -> resp.bodyHandler(body -> {
                    final Map<String, Object> claims = Json.decodeValue(body.toString(), Map.class);
                    assertNotNull(claims);
                    assertEquals(3, claims.size());
                    assertTrue(claims.containsKey(StandardClaims.EMAIL));
                    assertTrue(claims.containsKey(StandardClaims.EMAIL_VERIFIED));
                }),
                HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void shouldInvokeUserEndpoint_scopesRequest_email_address() throws Exception {
        AccessToken accessToken = new DefaultAccessToken("token");
        ((DefaultAccessToken) accessToken).setSubject("user");
        ((DefaultAccessToken) accessToken).setExpiresIn(100);
        ((DefaultAccessToken) accessToken).setScope("openid email address");

        User user = createUser();

        when(tokenService.getAccessToken(anyString())).thenReturn(Maybe.just(accessToken));
        when(userService.findById(anyString())).thenReturn(Maybe.just(user));

        testRequest(
                HttpMethod.GET,
                "/userinfo",
                req -> req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token"),
                resp -> resp.bodyHandler(body -> {
                    final Map<String, Object> claims = Json.decodeValue(body.toString(), Map.class);
                    assertNotNull(claims);
                    assertEquals(4, claims.size());
                    assertTrue(claims.containsKey(StandardClaims.ADDRESS));
                    assertTrue(claims.containsKey(StandardClaims.EMAIL));
                    assertTrue(claims.containsKey(StandardClaims.EMAIL_VERIFIED));
                }),
                HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void shouldInvokeUserEndpoint_scopesRequest_and_claimsRequest() throws Exception {
        AccessToken accessToken = new DefaultAccessToken("token");
        ((DefaultAccessToken) accessToken).setSubject("user");
        ((DefaultAccessToken) accessToken).setExpiresIn(100);
        ((DefaultAccessToken) accessToken).setScope("openid email address");
        ((DefaultAccessToken) accessToken).setRequestedParameters(Collections.singletonMap("claims", "{\"userinfo\":{\"name\":{\"essential\":true}}}"));

        User user = createUser();

        when(tokenService.getAccessToken(anyString())).thenReturn(Maybe.just(accessToken));
        when(userService.findById(anyString())).thenReturn(Maybe.just(user));

        testRequest(
                HttpMethod.GET,
                "/userinfo",
                req -> req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token"),
                resp -> resp.bodyHandler(body -> {
                    final Map<String, Object> claims = Json.decodeValue(body.toString(), Map.class);
                    assertNotNull(claims);
                    assertEquals(5, claims.size());
                    assertTrue(claims.containsKey(StandardClaims.NAME));
                    assertTrue(claims.containsKey(StandardClaims.ADDRESS));
                    assertTrue(claims.containsKey(StandardClaims.EMAIL));
                    assertTrue(claims.containsKey(StandardClaims.EMAIL_VERIFIED));
                }),
                HttpStatusCode.OK_200, "OK", null);
    }

    private User createUser() {
        User user = new User();
        Map<String, Object> additionalInformation  = new HashMap<>();
        additionalInformation.put(StandardClaims.SUB, "user");
        additionalInformation.put(StandardClaims.NAME, "gravitee user");
        additionalInformation.put(StandardClaims.FAMILY_NAME, "gravitee");
        additionalInformation.put(StandardClaims.GIVEN_NAME, "gravitee");
        additionalInformation.put(StandardClaims.MIDDLE_NAME, "gravitee");
        additionalInformation.put(StandardClaims.NICKNAME, "gravitee");
        additionalInformation.put(StandardClaims.PREFERRED_USERNAME, "gravitee");
        additionalInformation.put(StandardClaims.PROFILE, "gravitee");
        additionalInformation.put(StandardClaims.PICTURE, "gravitee");
        additionalInformation.put(StandardClaims.WEBSITE, "gravitee");
        additionalInformation.put(StandardClaims.GENDER, "gravitee");
        additionalInformation.put(StandardClaims.BIRTHDATE, "gravitee");
        additionalInformation.put(StandardClaims.ZONEINFO, "gravitee");
        additionalInformation.put(StandardClaims.LOCALE, "gravitee");
        additionalInformation.put(StandardClaims.UPDATED_AT, "gravitee");
        additionalInformation.put(StandardClaims.EMAIL, "gravitee");
        additionalInformation.put(StandardClaims.EMAIL_VERIFIED, "gravitee");
        additionalInformation.put(StandardClaims.ADDRESS, "gravitee");
        additionalInformation.put(StandardClaims.PHONE_NUMBER, "gravitee");
        additionalInformation.put(StandardClaims.PHONE_NUMBER_VERIFIED, "gravitee");
        user.setAdditionalInformation(additionalInformation);

        return user;
    }
}
