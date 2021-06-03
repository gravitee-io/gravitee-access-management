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
package io.gravitee.am.gateway.handler.oidc.resources.endpoint;

import io.gravitee.am.common.exception.oauth2.InvalidTokenException;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oidc.CustomClaims;
import io.gravitee.am.common.oidc.Scope;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.handler.OAuth2AuthHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.handler.OAuth2AuthResponse;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.OAuth2AuthProvider;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidClientException;
import io.gravitee.am.gateway.handler.oauth2.exception.ServerErrorException;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.ExceptionHandler;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.service.jwe.JWEService;
import io.gravitee.am.model.Group;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.GroupService;
import io.gravitee.am.service.RoleService;
import io.gravitee.am.service.UserService;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.*;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class UserInfoEndpointHandlerTest extends RxWebTestBase {

    @Mock
    private UserService userService;

    @Mock
    private RoleService roleService;

    @Mock
    private GroupService groupService;

    @Mock
    private JWTService jwtService;

    @Mock
    private JWEService jweService;

    @Mock
    private OpenIDDiscoveryService openIDDiscoveryService;

    @InjectMocks
    private UserInfoEndpoint userInfoEndpoint = new UserInfoEndpoint(userService, roleService, groupService, jwtService, jweService, openIDDiscoveryService);

    @Override
    public void setUp() throws Exception {
        super.setUp();

        router.route(HttpMethod.GET, "/userinfo")
                .handler(userInfoEndpoint);
        router.route(HttpMethod.POST, "/userinfo")
                .consumes(MediaType.APPLICATION_FORM_URLENCODED)
                .handler(userInfoEndpoint);
        router.route().failureHandler(new ExceptionHandler());
    }

    @Test
    public void shouldNotInvokeUserEndpoint_noBearerToken() throws Exception {
        router.route().order(-1).handler(createOAuth2AuthHandler(oAuth2AuthProvider()));

        testRequest(
                HttpMethod.GET, "/userinfo",
                HttpStatusCode.UNAUTHORIZED_401, "Unauthorized");
    }

    @Test
    public void shouldNotInvokeUserEndpoint_noBearerToken_post() throws Exception {
        router.route().order(-1)
                .handler(BodyHandler.create())
                .handler(createOAuth2AuthHandler(oAuth2AuthProvider()));

        testRequest(
                HttpMethod.POST, "/userinfo",
                HttpStatusCode.UNAUTHORIZED_401, "Unauthorized");
    }

    @Test
    public void shouldNotInvokeUserEndpoint_invalidHeaderBearerToken() throws Exception {
        router.route().order(-1).handler(createOAuth2AuthHandler(oAuth2AuthProvider()));

        testRequest(
                HttpMethod.GET, "/userinfo", req -> req.putHeader(HttpHeaders.AUTHORIZATION, "Error token"),
                HttpStatusCode.UNAUTHORIZED_401, "Unauthorized", null);
    }

    @Test
    public void shouldNotInvokeUserEndpoint_invalidToken_jwtDecode() throws Exception {
        router.route().order(-1).handler(createOAuth2AuthHandler(oAuth2AuthProvider(new ServerErrorException())));

        testRequest(
                HttpMethod.GET, "/userinfo", req -> req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token"),
                HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);
    }

    @Test
    public void shouldNotInvokeUserEndpoint_invalidToken_jwtDecode_post() throws Exception {
        router.route().order(-1)
                .handler(BodyHandler.create())
                .handler(createOAuth2AuthHandler(oAuth2AuthProvider(new ServerErrorException())));

        testRequest(
                HttpMethod.POST, "/userinfo", req -> {
                    final String body = "access_token=test-token";
                    req.putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED);
                    req.putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(body.length()));
                    req.write(Buffer.buffer(body));
                },
                HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);
    }

    @Test
    public void shouldNotInvokeUserEndpoint_invalidToken_noClient() throws Exception {
        JWT jwt = new JWT();
        jwt.setAud("client-id");

        router.route().order(-1).handler(createOAuth2AuthHandler(oAuth2AuthProvider(new InvalidClientException())));

        testRequest(
                HttpMethod.GET, "/userinfo", req -> req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token"),
                HttpStatusCode.UNAUTHORIZED_401, "Unauthorized", null);
    }

    @Test
    public void shouldNotInvokeUserEndpoint_unknownToken() throws Exception {
        JWT jwt = new JWT();
        jwt.setAud("client-id");

        router.route().order(-1).handler(createOAuth2AuthHandler(oAuth2AuthProvider(new InvalidTokenException())));

        testRequest(
                HttpMethod.GET, "/userinfo", req -> req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token"),
                HttpStatusCode.UNAUTHORIZED_401, "Unauthorized", null);
    }

    @Test
    public void shouldNotInvokeUserEndpoint_clientOnlyToken() throws Exception {
        Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");

        JWT token = new JWT();
        token.setSub("client-id");
        token.setAud("client-id");

        router.route().order(-1).handler(createOAuth2AuthHandler(oAuth2AuthProvider(token, client)));

        testRequest(
                HttpMethod.GET, "/userinfo", req -> req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token"),
                HttpStatusCode.UNAUTHORIZED_401, "Unauthorized", null);
    }

    @Test
    public void shouldNotInvokeUserEndpoint_userNotFound() throws Exception {
        JWT jwt = new JWT();
        jwt.setJti("id-token");
        jwt.setAud("client-id");
        jwt.setSub("id-subject");
        jwt.setScope("openid");

        Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");

        router.route().order(-1).handler(createOAuth2AuthHandler(oAuth2AuthProvider(jwt, client)));

        when(userService.findById(anyString())).thenReturn(Maybe.empty());

        testRequest(
                HttpMethod.GET, "/userinfo", req -> req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token"),
                HttpStatusCode.UNAUTHORIZED_401, "Unauthorized", null);
    }

    @Test
    public void shouldInvokeUserEndpoint_noOpenIDScope_noScope() throws Exception {
        JWT jwt = new JWT();
        jwt.setJti("id-token");
        jwt.setAud("client-id");
        jwt.setSub("id-subject");

        Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");

        router.route().order(-1).handler(createOAuth2AuthHandler(oAuth2AuthProvider(jwt, client)));

        testRequest(
                HttpMethod.GET, "/userinfo", req -> req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token"),
                HttpStatusCode.FORBIDDEN_403, "Forbidden", null);
    }

    @Test
    public void shouldInvokeUserEndpoint_noOpenIDScope() throws Exception {
        JWT jwt = new JWT();
        jwt.setJti("id-token");
        jwt.setAud("client-id");
        jwt.setSub("id-subject");
        jwt.setScope("read");

        Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");

        router.route().order(-1).handler(createOAuth2AuthHandler(oAuth2AuthProvider(jwt, client)));

        testRequest(
                HttpMethod.GET, "/userinfo", req -> req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token"),
                HttpStatusCode.FORBIDDEN_403, "Forbidden", null);
    }

    @Test
    public void shouldInvokeUserEndpoint() throws Exception {
        User user = new User();
        user.setAdditionalInformation(Collections.singletonMap("sub", "user"));

        JWT jwt = new JWT();
        jwt.setJti("id-token");
        jwt.setAud("client-id");
        jwt.setSub("id-subject");
        jwt.setScope("openid");

        Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");

        router.route().order(-1).handler(createOAuth2AuthHandler(oAuth2AuthProvider(jwt, client)));

        when(userService.findById(anyString())).thenReturn(Maybe.just(user));

        testRequest(
                HttpMethod.GET, "/userinfo", req -> req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token"),
                HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void shouldInvokeUserEndpoint_post() throws Exception {
        User user = new User();
        user.setAdditionalInformation(Collections.singletonMap("sub", "user"));

        JWT jwt = new JWT();
        jwt.setJti("id-token");
        jwt.setAud("client-id");
        jwt.setSub("id-subject");
        jwt.setScope("openid");

        Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");

        router.route().order(-1)
                .handler(BodyHandler.create())
                .handler(createOAuth2AuthHandler(oAuth2AuthProvider(jwt, client)));

        when(userService.findById(anyString())).thenReturn(Maybe.just(user));

        testRequest(
                HttpMethod.POST, "/userinfo", req ->
                {
                    final String body = "access_token=test-token";
                    req.putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED);
                    req.putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(body.length()));
                    req.write(Buffer.buffer(body));
                },
                HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void shouldInvokeUserEndpoint_get_queryParam() throws Exception {
        User user = new User();
        user.setAdditionalInformation(Collections.singletonMap("sub", "user"));

        JWT jwt = new JWT();
        jwt.setJti("id-token");
        jwt.setAud("client-id");
        jwt.setSub("id-subject");
        jwt.setScope("openid");

        Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");

        router.route().order(-1).handler(createOAuth2AuthHandler(oAuth2AuthProvider(jwt, client)));

        when(userService.findById(anyString())).thenReturn(Maybe.just(user));

        testRequest(
                HttpMethod.GET, "/userinfo?access_token=test-token",
                HttpStatusCode.OK_200, "OK");
    }

    @Test
    public void shouldInvokeUserEndpoint_claimsRequest() throws Exception {
        JWT jwt = new JWT();
        jwt.setJti("id-token");
        jwt.setAud("client-id");
        jwt.setSub("id-subject");
        jwt.setScope("openid");
        jwt.setClaimsRequestParameter("{\"userinfo\":{\"name\":{\"essential\":true}}}");

        Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");

        router.route().order(-1).handler(createOAuth2AuthHandler(oAuth2AuthProvider(jwt, client)));

        User user = createUser();

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
        JWT jwt = new JWT();
        jwt.setJti("id-token");
        jwt.setAud("client-id");
        jwt.setSub("id-subject");
        jwt.setScope("openid profile");

        Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");

        router.route().order(-1).handler(createOAuth2AuthHandler(oAuth2AuthProvider(jwt, client)));

        User user = createUser();

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
        JWT jwt = new JWT();
        jwt.setJti("id-token");
        jwt.setAud("client-id");
        jwt.setSub("id-subject");
        jwt.setScope("openid email");

        Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");

        router.route().order(-1).handler(createOAuth2AuthHandler(oAuth2AuthProvider(jwt, client)));

        User user = createUser();

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
        JWT jwt = new JWT();
        jwt.setJti("id-token");
        jwt.setAud("client-id");
        jwt.setSub("id-subject");
        jwt.setScope("openid email address");

        Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");

        router.route().order(-1).handler(createOAuth2AuthHandler(oAuth2AuthProvider(jwt, client)));

        User user = createUser();

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
    public void shouldInvokeUserEndpoint_scopesRequest_roles_noRole() throws Exception {
        JWT jwt = new JWT();
        jwt.setJti("id-token");
        jwt.setAud("client-id");
        jwt.setSub("id-subject");
        jwt.setScope("openid roles");

        Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");

        router.route().order(-1).handler(createOAuth2AuthHandler(oAuth2AuthProvider(jwt, client)));

        User user = createUser();

        when(userService.findById(anyString())).thenReturn(Maybe.just(user));

        testRequest(
                HttpMethod.GET,
                "/userinfo",
                req -> req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token"),
                resp -> resp.bodyHandler(body -> {
                    final Map<String, Object> claims = Json.decodeValue(body.toString(), Map.class);
                    assertNotNull(claims);
                    assertEquals(1, claims.size());
                    assertTrue(!claims.containsKey(CustomClaims.ROLES));
                }),
                HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void shouldInvokeUserEndpoint_scopesRequest_roles() throws Exception {
        JWT jwt = new JWT();
        jwt.setJti("id-token");
        jwt.setAud("client-id");
        jwt.setSub("id-subject");
        jwt.setScope("openid roles");

        Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");

        Role role1 = new Role();
        role1.setId("role1");
        role1.setName("role-1");

        Role role2 = new Role();
        role2.setId("role2");
        role2.setName("role-2");

        router.route().order(-1).handler(createOAuth2AuthHandler(oAuth2AuthProvider(jwt, client)));

        User user = createUser();
        user.setRoles(Arrays.asList("role1", "role2"));
        when(userService.findById(anyString())).thenReturn(Maybe.just(user));
        when(roleService.findByIdIn(anyList())).thenReturn(Single.just(new HashSet<>(Arrays.asList(role1, role2))));

        testRequest(
                HttpMethod.GET,
                "/userinfo",
                req -> req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token"),
                resp -> resp.bodyHandler(body -> {
                    final Map<String, Object> claims = Json.decodeValue(body.toString(), Map.class);
                    assertNotNull(claims);
                    assertEquals(2, claims.size());
                    assertTrue(claims.containsKey(CustomClaims.ROLES));
                    assertTrue(((List) claims.get(CustomClaims.ROLES)).containsAll(Arrays.asList("role-1", "role-2")));
                }),
                HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void shouldInvokeUserEndpoint_scopesRequest_groups_noGroup() throws Exception {
        JWT jwt = new JWT();
        jwt.setJti("id-token");
        jwt.setAud("client-id");
        jwt.setSub("id-subject");
        jwt.setScope("openid groups");

        Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");

        router.route().order(-1).handler(createOAuth2AuthHandler(oAuth2AuthProvider(jwt, client)));

        User user = createUser();

        when(userService.findById(anyString())).thenReturn(Maybe.just(user));
        when(groupService.findByMember(user.getId())).thenReturn(Flowable.empty());

        testRequest(
                HttpMethod.GET,
                "/userinfo",
                req -> req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token"),
                resp -> resp.bodyHandler(body -> {
                    final Map<String, Object> claims = Json.decodeValue(body.toString(), Map.class);
                    assertNotNull(claims);
                    assertEquals(1, claims.size());
                    assertTrue(!claims.containsKey(CustomClaims.GROUPS));
                }),
                HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void shouldInvokeUserEndpoint_scopesRequest_groups() throws Exception {
        JWT jwt = new JWT();
        jwt.setJti("id-token");
        jwt.setAud("client-id");
        jwt.setSub("id-subject");
        jwt.setScope("openid groups");

        Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");

        Group group1 = new Group();
        group1.setId("group1");
        group1.setName("group-1");

        Group group2 = new Group();
        group2.setId("group2");
        group2.setName("group-2");

        router.route().order(-1).handler(createOAuth2AuthHandler(oAuth2AuthProvider(jwt, client)));

        User user = createUser();
        when(userService.findById(anyString())).thenReturn(Maybe.just(user));
        when(groupService.findByMember(user.getId())).thenReturn(Flowable.just(group1, group2));

        testRequest(
                HttpMethod.GET,
                "/userinfo",
                req -> req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token"),
                resp -> resp.bodyHandler(body -> {
                    final Map<String, Object> claims = Json.decodeValue(body.toString(), Map.class);
                    assertNotNull(claims);
                    assertEquals(2, claims.size());
                    assertTrue(claims.containsKey(CustomClaims.GROUPS));
                    assertTrue(((List) claims.get(CustomClaims.GROUPS)).containsAll(Arrays.asList("group-1", "group-2")));
                }),
                HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void shouldInvokeUserEndpoint_scopesRequest_roles_groups() throws Exception {
        JWT jwt = new JWT();
        jwt.setJti("id-token");
        jwt.setAud("client-id");
        jwt.setSub("id-subject");
        jwt.setScope("openid roles groups");

        Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");

        Role role1 = new Role();
        role1.setId("role1");
        role1.setName("role-1");

        Role role2 = new Role();
        role2.setId("role2");
        role2.setName("role-2");

        Group group1 = new Group();
        group1.setId("group1");
        group1.setName("group-1");

        Group group2 = new Group();
        group2.setId("group2");
        group2.setName("group-2");

        router.route().order(-1).handler(createOAuth2AuthHandler(oAuth2AuthProvider(jwt, client)));

        User user = createUser();
        user.setRoles(Arrays.asList("role1", "role2"));
        when(userService.findById(anyString())).thenReturn(Maybe.just(user));
        when(roleService.findByIdIn(anyList())).thenReturn(Single.just(new HashSet<>(Arrays.asList(role1, role2))));
        when(groupService.findByMember(user.getId())).thenReturn(Flowable.just(group1, group2));

        testRequest(
                HttpMethod.GET,
                "/userinfo",
                req -> req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token"),
                resp -> resp.bodyHandler(body -> {
                    final Map<String, Object> claims = Json.decodeValue(body.toString(), Map.class);
                    assertNotNull(claims);
                    assertEquals(3, claims.size());
                    assertTrue(claims.containsKey(CustomClaims.ROLES));
                    assertTrue(((List) claims.get(CustomClaims.ROLES)).containsAll(Arrays.asList("role-1", "role-2")));
                    assertTrue(claims.containsKey(CustomClaims.GROUPS));
                    assertTrue(((List) claims.get(CustomClaims.GROUPS)).containsAll(Arrays.asList("group-1", "group-2")));
                }),
                HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void shouldInvokeUserEndpoint_scopesRequest_roles_groups_full_profile() throws Exception {
        JWT jwt = new JWT();
        jwt.setJti("id-token");
        jwt.setAud("client-id");
        jwt.setSub("id-subject");
        jwt.setScope("openid full_profile roles groups");

        Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");

        Role role1 = new Role();
        role1.setId("role1");
        role1.setName("role-1");

        Role role2 = new Role();
        role2.setId("role2");
        role2.setName("role-2");

        Group group1 = new Group();
        group1.setId("group1");
        group1.setName("group-1");

        Group group2 = new Group();
        group2.setId("group2");
        group2.setName("group-2");

        router.route().order(-1).handler(createOAuth2AuthHandler(oAuth2AuthProvider(jwt, client)));

        User user = createUser();
        user.setRoles(Arrays.asList("role1", "role2"));
        when(userService.findById(anyString())).thenReturn(Maybe.just(user));
        when(roleService.findByIdIn(anyList())).thenReturn(Single.just(new HashSet<>(Arrays.asList(role1, role2))));
        when(groupService.findByMember(user.getId())).thenReturn(Flowable.just(group1, group2));

        testRequest(
                HttpMethod.GET,
                "/userinfo",
                req -> req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token"),
                resp -> resp.bodyHandler(body -> {
                    final Map<String, Object> claims = Json.decodeValue(body.toString(), Map.class);
                    assertNotNull(claims);
                    assertEquals(23, claims.size());
                    assertTrue(claims.containsKey(CustomClaims.ROLES));
                    assertTrue(((List) claims.get(CustomClaims.ROLES)).containsAll(Arrays.asList("role-1", "role-2")));
                    assertTrue(claims.containsKey(CustomClaims.GROUPS));
                    assertTrue(((List) claims.get(CustomClaims.GROUPS)).containsAll(Arrays.asList("group-1", "group-2")));
                    assertTrue(claims.containsKey("custom-claim"));
                    assertTrue("gravitee".equals(claims.get("custom-claim")));
                }),
                HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void shouldInvokeUserEndpoint_scopesRequest_and_claimsRequest() throws Exception {
        JWT jwt = new JWT();
        jwt.setJti("id-token");
        jwt.setAud("client-id");
        jwt.setSub("id-subject");
        jwt.setScope("openid email address");
        jwt.setClaimsRequestParameter("{\"userinfo\":{\"name\":{\"essential\":true}}}");

        Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");

        router.route().order(-1).handler(createOAuth2AuthHandler(oAuth2AuthProvider(jwt, client)));

        User user = createUser();

        when(userService.findById(anyString())).thenReturn(Maybe.just(user));

        testRequest(
                HttpMethod.GET,
                "/userinfo",
                req -> req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token"),
                resp -> {
                    assertEquals(MediaType.APPLICATION_JSON,resp.getHeader(HttpHeaders.CONTENT_TYPE));
                    resp.bodyHandler(body -> {
                        final Map<String, Object> claims = Json.decodeValue(body.toString(), Map.class);
                        assertNotNull(claims);
                        assertEquals(5, claims.size());
                        assertTrue(claims.containsKey(StandardClaims.NAME));
                        assertTrue(claims.containsKey(StandardClaims.ADDRESS));
                        assertTrue(claims.containsKey(StandardClaims.EMAIL));
                        assertTrue(claims.containsKey(StandardClaims.EMAIL_VERIFIED));
                    });
                },
                HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void shouldInvokeUserEndpoint_scopesRequest_and_claimsRequest_signedResponse() throws Exception {
        JWT jwt = new JWT();
        jwt.setJti("id-token");
        jwt.setAud("client-id");
        jwt.setSub("id-subject");
        jwt.setScope("openid email address");
        jwt.setClaimsRequestParameter("{\"userinfo\":{\"name\":{\"essential\":true}}}");

        Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setUserinfoSignedResponseAlg("algorithm");

        router.route().order(-1).handler(createOAuth2AuthHandler(oAuth2AuthProvider(jwt, client)));

        User user = createUser();

        when(userService.findById(anyString())).thenReturn(Maybe.just(user));
        when(jwtService.encodeUserinfo(any(),any())).thenReturn(Single.just("signedJwtBearer"));
        when(jweService.encryptUserinfo("signedJwtBearer",client)).thenReturn(Single.just("signedJwtBearer"));

        testRequest(
                HttpMethod.GET,
                "/userinfo",
                req -> req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token"),
                resp -> {
                    assertEquals(MediaType.APPLICATION_JWT,resp.getHeader(HttpHeaders.CONTENT_TYPE));
                    resp.bodyHandler(body -> assertEquals("signedJwtBearer",body.toString()));
                },
                HttpStatusCode.OK_200, "OK", null);
    }

    private User createUser() {
        User user = new User();
        user.setId("user-id");
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
        additionalInformation.put("custom-claim", "gravitee");
        user.setAdditionalInformation(additionalInformation);

        return user;
    }

    private OAuth2AuthHandler createOAuth2AuthHandler(OAuth2AuthProvider oAuth2AuthProvider) {
        OAuth2AuthHandler userInfoAuthHandler = OAuth2AuthHandler.create(oAuth2AuthProvider, Scope.OPENID.getKey());
        userInfoAuthHandler.extractToken(true);
        userInfoAuthHandler.extractClient(true);
        userInfoAuthHandler.forceEndUserToken(true);

        return userInfoAuthHandler;
    }

    private OAuth2AuthProvider oAuth2AuthProvider() {
        return new OAuth2AuthProvider() {
            @Override
            public void decodeToken(String token, boolean offlineVerification, Handler<AsyncResult<OAuth2AuthResponse>> handler) {
                handler.handle(Future.succeededFuture(new OAuth2AuthResponse()));
            }
        };
    }

    private OAuth2AuthProvider oAuth2AuthProvider(Throwable throwable) {
        return new OAuth2AuthProvider() {
            @Override
            public void decodeToken(String token, boolean offlineVerification, Handler<AsyncResult<OAuth2AuthResponse>> handler) {
                handler.handle(Future.failedFuture(throwable));
            }
        };
    }

    private OAuth2AuthProvider oAuth2AuthProvider(JWT jwt, Client client) {
        return new OAuth2AuthProvider() {
            @Override
            public void decodeToken(String token, boolean offlineVerification, Handler<AsyncResult<OAuth2AuthResponse>> handler) {
                handler.handle(Future.succeededFuture(new OAuth2AuthResponse(jwt, client)));
            }
        };
    }
}
