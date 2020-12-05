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
package io.gravitee.am.gateway.handler.oauth2.resources.endpoint;


import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.ResponseType;
import io.gravitee.am.common.oidc.ResponseMode;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.oauth2.resources.endpoint.authorization.AuthorizationEndpoint;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.*;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.response.*;
import io.gravitee.am.gateway.handler.oauth2.service.response.jwt.JWTAuthorizationCodeResponse;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.gateway.handler.oauth2.service.token.impl.AccessToken;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDProviderMetadata;
import io.gravitee.am.gateway.handler.oidc.service.flow.Flow;
import io.gravitee.am.gateway.handler.oidc.service.jwe.JWEService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.reactivex.ext.auth.User;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.SessionHandler;
import io.vertx.reactivex.ext.web.sstore.LocalSessionStore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class AuthorizationEndpointTest extends RxWebTestBase {

    private static final String CLIENT_CONTEXT_KEY = "client";

    @Mock
    private Flow flow;

    @Mock
    private Domain domain;

    @Mock
    private ClientSyncService clientSyncService;

    @Mock
    private OpenIDDiscoveryService openIDDiscoveryService;

    @Mock
    private JWTService jwtService;

    @Mock
    private JWEService jweService;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        AuthorizationEndpoint authorizationEndpointHandler = new AuthorizationEndpoint(flow);

        // set openid provider service
        OpenIDProviderMetadata openIDProviderMetadata = new OpenIDProviderMetadata();
        openIDProviderMetadata.setResponseTypesSupported(Arrays.asList(ResponseType.CODE,
                ResponseType.TOKEN,
                io.gravitee.am.common.oidc.ResponseType.CODE_ID_TOKEN,
                io.gravitee.am.common.oidc.ResponseType.CODE_TOKEN,
                io.gravitee.am.common.oidc.ResponseType.CODE_ID_TOKEN_TOKEN,
                io.gravitee.am.common.oidc.ResponseType.ID_TOKEN_TOKEN,
                io.gravitee.am.common.oidc.ResponseType.ID_TOKEN));

        openIDProviderMetadata.setResponseModesSupported(Arrays.asList(
                io.gravitee.am.common.oauth2.ResponseMode.QUERY,
                io.gravitee.am.common.oauth2.ResponseMode.FRAGMENT,
                ResponseMode.QUERY_JWT,
                ResponseMode.FRAGMENT_JWT,
                ResponseMode.JWT));

        when(openIDDiscoveryService.getConfiguration(anyString())).thenReturn(openIDProviderMetadata);

        // set Authorization endpoint routes
        SessionHandler sessionHandler = SessionHandler.create(LocalSessionStore.create(vertx));
        router.route("/oauth/authorize")
                .handler(sessionHandler);
        router.route(HttpMethod.GET, "/oauth/authorize")
                .handler(new AuthorizationRequestParseProviderConfigurationHandler(openIDDiscoveryService))
                .handler(new AuthorizationRequestParseRequiredParametersHandler())
                .handler(new AuthorizationRequestParseClientHandler(clientSyncService))
                .handler(new AuthorizationRequestParseParametersHandler(domain))
                .handler(new AuthorizationRequestResolveHandler())
                .handler(authorizationEndpointHandler);
        router.route()
                .failureHandler(new AuthorizationRequestFailureHandler(openIDDiscoveryService, jwtService, jweService));

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(CONTEXT_PATH, "/test");
            routingContext.next();
        });
    }

    @Test
    public void shouldNotInvokeAuthorizationEndpoint_noUser_noRedirectUri() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&client_id=client-id",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("http://localhost:9999/callback?error=access_denied"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNotInvokeAuthorizationEndpoint_noUser_withRedirectUri() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("http://localhost:9999/callback?error=access_denied", location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeAuthorizationEndpoint_emptyScope() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(ResponseType.CODE);
        authorizationRequest.setRedirectUri("http://localhost:9999/callback");

        AuthorizationResponse authorizationResponse = new AuthorizationCodeResponse();
        authorizationResponse.setRedirectUri(authorizationRequest.getRedirectUri());
        ((AuthorizationCodeResponse) authorizationResponse).setCode("test-code");

        router.route().order(-1).handler(routingContext -> {
            routingContext.setUser(new User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(new io.gravitee.am.model.User())));
            routingContext.next();
        });

        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));
        when(flow.run(any(), any(), any())).thenReturn(Single.just(authorizationResponse));

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(CLIENT_CONTEXT_KEY, client);
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET, "/oauth/authorize?response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/callback&scope=",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("http://localhost:9999/callback?code=test-code", location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNotInvokeAuthorizationEndpoint_invalidScope() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setScopes(Collections.singletonList("read"));
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        router.route().order(-1).handler(routingContext -> {
            routingContext.setUser(new User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(new io.gravitee.am.model.User())));
            routingContext.next();
        });

        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/callback&scope=unknown",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("http://localhost:9999/callback?error=invalid_scope&error_description=Invalid+scope%2528s%2529%253A+unknown", location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNotInvokeAuthorizationEndpoint_emptyRedirectUri() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setScopes(Collections.singletonList("read"));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(ResponseType.CODE);

        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&client_id=client-id",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("/test/oauth/error?client_id=client-id&error=invalid_request&error_description=A+redirect_uri+must+be+supplied"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNotInvokeAuthorizationEndpoint_emptyRedirectUri_clientHasSeveralRedirectUris() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setScopes(Collections.singletonList("read"));
        client.setRedirectUris(Arrays.asList("http://redirect1", "http://redirect2"));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(ResponseType.CODE);

        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&client_id=client-id",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("/test/oauth/error?client_id=client-id&error=invalid_request&error_description=Unable+to+find+suitable+redirect_uri%252C+a+redirect_uri+must+be+supplied"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNotInvokeAuthorizationEndpoint_mismatchRedirectUri() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setScopes(Collections.singletonList("read"));
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/authorize/callback"));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(ResponseType.CODE);
        authorizationRequest.setRedirectUri("http://localhost:9999/wrong/callback");

        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/wrong/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("/test/oauth/error?client_id=client-id&error=redirect_uri_mismatch&error_description=The+redirect_uri+MUST+match+the+registered+callback+URL+for+this+application"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNotInvokeAuthorizationEndpoint_mismatchRedirectUri_strictMatching() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setScopes(Collections.singletonList("read"));
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/authorize/callback"));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(ResponseType.CODE);
        authorizationRequest.setRedirectUri("http://localhost:9999/authorize/callback?param=param1");

        when(domain.isRedirectUriStrictMatching()).thenReturn(true);
        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/authorize/callback?param=param1",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("/test/oauth/error?client_id=client-id&error=redirect_uri_mismatch&error_description=The+redirect_uri+MUST+match+the+registered+callback+URL+for+this+application"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeAuthorizationEndpoint_noStrictMatching() throws Exception {
        io.gravitee.am.model.User user = new io.gravitee.am.model.User();

        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setScopes(Collections.singletonList("read"));
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/authorize/callback"));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(ResponseType.CODE);
        authorizationRequest.setRedirectUri("http://localhost:9999/authorize/callback?param=param1");

        AuthorizationResponse authorizationResponse = new AuthorizationCodeResponse();
        authorizationResponse.setRedirectUri(authorizationRequest.getRedirectUri());
        ((AuthorizationCodeResponse) authorizationResponse).setCode("test-code");

        router.route().order(-1).handler(routingContext -> {
            routingContext.setUser(new User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
            routingContext.next();
        });

        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));
        when(flow.run(any(), any(), any())).thenReturn(Single.just(authorizationResponse));

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/authorize/callback?param=param1",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("http://localhost:9999/authorize/callback?param=param1&code=test-code", location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNotInvokeAuthorizationEndpoint_duplicateParameters() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setScopes(Collections.singletonList("read"));
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("/test/oauth/error?client_id=client-id&error=invalid_request&error_description=Parameter+%255Bresponse_type%255D+is+included+more+than+once"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeAuthorizationEndpoint_responseTypeCode() throws Exception {
        io.gravitee.am.model.User user = new io.gravitee.am.model.User();

        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(ResponseType.CODE);
        authorizationRequest.setRedirectUri("http://localhost:9999/callback");

        AuthorizationResponse authorizationResponse = new AuthorizationCodeResponse();
        authorizationResponse.setRedirectUri(authorizationRequest.getRedirectUri());
        ((AuthorizationCodeResponse) authorizationResponse).setCode("test-code");

        router.route().order(-1).handler(routingContext -> {
            routingContext.setUser(new User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
            routingContext.next();
        });

        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));
        when(flow.run(any(), any(), any())).thenReturn(Single.just(authorizationResponse));

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("http://localhost:9999/callback?code=test-code", location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeAuthorizationEndpoint_noClientResponseType() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setResponseTypes(null);
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(ResponseType.TOKEN);
        authorizationRequest.setRedirectUri("http://localhost:9999/callback");

        Token accessToken = new AccessToken("token");

        AuthorizationResponse authorizationResponse = new ImplicitResponse();
        authorizationResponse.setRedirectUri(authorizationRequest.getRedirectUri());
        ((ImplicitResponse) authorizationResponse).setAccessToken(accessToken);

        router.route().order(-1).handler(routingContext -> {
            routingContext.setUser(new User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(new io.gravitee.am.model.User())));
            routingContext.next();
        });

        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=token&client_id=client-id&nonce=123&redirect_uri=http://localhost:9999/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("http://localhost:9999/callback#error=unauthorized_client&error_description=Client+should+have+response_type.", location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeAuthorizationEndpoint_missingClientResponseType() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setResponseTypes(Arrays.asList(io.gravitee.am.common.oidc.ResponseType.ID_TOKEN));
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(ResponseType.TOKEN);
        authorizationRequest.setRedirectUri("http://localhost:9999/callback");

        Token accessToken = new AccessToken("token");

        AuthorizationResponse authorizationResponse = new ImplicitResponse();
        authorizationResponse.setRedirectUri(authorizationRequest.getRedirectUri());
        ((ImplicitResponse) authorizationResponse).setAccessToken(accessToken);

        router.route().order(-1).handler(routingContext -> {
            routingContext.setUser(new User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(new io.gravitee.am.model.User())));
            routingContext.next();
        });

        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=token&client_id=client-id&nonce=123&redirect_uri=http://localhost:9999/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("http://localhost:9999/callback#error=unauthorized_client&error_description=Client+should+have+all+requested+response_type", location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeAuthorizationEndpoint_responseTypeToken() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));
        client.setAuthorizedGrantTypes(Arrays.asList(GrantType.IMPLICIT));
        client.setResponseTypes(Arrays.asList(ResponseType.TOKEN));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(ResponseType.TOKEN);
        authorizationRequest.setRedirectUri("http://localhost:9999/callback");

        Token accessToken = new AccessToken("token");

        AuthorizationResponse authorizationResponse = new ImplicitResponse();
        authorizationResponse.setRedirectUri(authorizationRequest.getRedirectUri());
        ((ImplicitResponse) authorizationResponse).setAccessToken(accessToken);

        router.route().order(-1).handler(routingContext -> {
            routingContext.setUser(new User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(new io.gravitee.am.model.User())));
            routingContext.next();
        });

        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));
        when(flow.run(any(), any(), any())).thenReturn(Single.just(authorizationResponse));

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=token&client_id=client-id&nonce=123&redirect_uri=http://localhost:9999/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("http://localhost:9999/callback#access_token=token&token_type=bearer&expires_in=0", location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNotInvokeAuthorizationEndpoint_noUser_prompt_none() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(CLIENT_CONTEXT_KEY, client);
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/callback&prompt=none",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("http://localhost:9999/callback?error=login_required&error_description=Login+required", location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNotInvokeAuthorizationEndpoint_user_max_age() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setScopes(Collections.singletonList("read"));
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(false);

        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        router.route().order(-1).handler(routingContext -> {
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setLoggedAt(new Date(System.currentTimeMillis()-24*60*60*1000));
            routingContext.setUser(new User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser)));
            routingContext.next();
        });

        // user is logged since yesterday, he must be redirected to the login page
        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/callback&max_age=1",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("http://localhost:9999/callback?error=access_denied", location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNotInvokeAuthorizationEndpoint_user_max_age_prompt_none() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setScopes(Collections.singletonList("read"));
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(false);

        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        router.route().order(-1).handler(routingContext -> {
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setLoggedAt(new Date(System.currentTimeMillis()-24*60*60*1000));
            routingContext.setUser(new User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser)));
            routingContext.put(CLIENT_CONTEXT_KEY, client);
            routingContext.next();
        });

        // user is logged since yesterday, he must be redirected to the login page
        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/callback&max_age=1&prompt=none",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("http://localhost:9999/callback?error=login_required&error_description=Login+required", location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeAuthorizationEndpoint_max_age() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(ResponseType.CODE);
        authorizationRequest.setRedirectUri("http://localhost:9999/callback");

        AuthorizationResponse authorizationResponse = new AuthorizationCodeResponse();
        authorizationResponse.setRedirectUri(authorizationRequest.getRedirectUri());
        ((AuthorizationCodeResponse) authorizationResponse).setCode("test-code");

        router.route().order(-1).handler(routingContext -> {
            io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
            endUser.setLoggedAt(new Date(System.currentTimeMillis()- 60*1000));
            routingContext.setUser(new User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(new io.gravitee.am.model.User())));
            routingContext.next();
        });

        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));
        when(flow.run(any(), any(), any())).thenReturn(Single.just(authorizationResponse));


        // user is logged for 1 min, the max_age is big enough to validate the request
        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/callback&max_age=120",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("http://localhost:9999/callback?code=test-code", location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNotInvokeAuthorizationEndpoint_noUser_code_challenge_method_without_code_challenge() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(CLIENT_CONTEXT_KEY, client);
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/callback&code_challenge_method=plain",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("http://localhost:9999/callback?error=invalid_request&error_description=Missing+parameter%253A+code_challenge", location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNotInvokeAuthorizationEndpoint_noUser_invalid_code_challenge_method() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(CLIENT_CONTEXT_KEY, client);
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/callback&code_challenge_method=invalid&code_challenge=challenge",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("http://localhost:9999/callback?error=invalid_request&error_description=Invalid+parameter%253A+code_challenge_method", location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNotInvokeAuthorizationEndpoint_noUser_invalid_code_challenge() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(CLIENT_CONTEXT_KEY, client);
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/callback&code_challenge_method=plain&code_challenge=challenge",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("http://localhost:9999/callback?error=invalid_request&error_description=Invalid+parameter%253A+code_challenge", location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNotInvokeAuthorizationEndpoint_noUser_code_challenge_valid_plain() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(ResponseType.CODE);
        authorizationRequest.setRedirectUri("http://localhost:9999/callback");

        AuthorizationResponse authorizationResponse = new AuthorizationCodeResponse();
        authorizationResponse.setRedirectUri(authorizationRequest.getRedirectUri());
        ((AuthorizationCodeResponse) authorizationResponse).setCode("test-code");

        router.route().order(-1).handler(routingContext -> {
            routingContext.setUser(new User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(new io.gravitee.am.model.User())));
            routingContext.next();
        });

        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));
        when(flow.run(any(), any(), any())).thenReturn(Single.just(authorizationResponse));

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/callback&code_challenge_method=plain&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("http://localhost:9999/callback?code=test-code", location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNotInvokeAuthorizationEndpoint_noUser_code_challenge_valid_s256() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(ResponseType.CODE);
        authorizationRequest.setRedirectUri("http://localhost:9999/callback");

        AuthorizationResponse authorizationResponse = new AuthorizationCodeResponse();
        authorizationResponse.setRedirectUri(authorizationRequest.getRedirectUri());
        ((AuthorizationCodeResponse) authorizationResponse).setCode("test-code");


        router.route().order(-1).handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext routingContext) {
                routingContext.setUser(new User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(new io.gravitee.am.model.User())));
                routingContext.next();
            }
        });

        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));
        when(flow.run(any(), any(), any())).thenReturn(Single.just(authorizationResponse));

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/callback&code_challenge_method=S256&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("http://localhost:9999/callback?code=test-code", location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNotInvokeAuthorizationEndpoint_prompt_login() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setScopes(Collections.singletonList("read"));
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        router.route().order(-1).handler(routingContext -> {
            routingContext.setUser(new User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(new io.gravitee.am.model.User())));
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/callback&prompt=login",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("http://localhost:9999/callback?error=access_denied", location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeAuthorizationEndpoint_prompt_login_consent_step() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setScopes(Collections.singletonList("read"));
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(ResponseType.CODE);
        authorizationRequest.setRedirectUri("http://localhost:9999/callback");

        AuthorizationResponse authorizationResponse = new AuthorizationCodeResponse();
        authorizationResponse.setRedirectUri(authorizationRequest.getRedirectUri());
        ((AuthorizationCodeResponse) authorizationResponse).setCode("test-code");

        router.route().order(-1).handler(routingContext -> {
            routingContext.request().headers().set(HttpHeaders.REFERER, "http://localhost:8092/" + routingContext.get(CONTEXT_PATH) + "/oauth/consent");
            routingContext.setUser(new User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(new io.gravitee.am.model.User())));
            routingContext.next();
        });

        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));
        when(flow.run(any(), any(), any())).thenReturn(Single.just(authorizationResponse));

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/callback&prompt=login",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("http://localhost:9999/callback?code=test-code", location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeAuthorizationEndpoint_prompt_login_mfa_step() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setScopes(Collections.singletonList("read"));
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(ResponseType.CODE);
        authorizationRequest.setRedirectUri("http://localhost:9999/callback");

        AuthorizationResponse authorizationResponse = new AuthorizationCodeResponse();
        authorizationResponse.setRedirectUri(authorizationRequest.getRedirectUri());
        ((AuthorizationCodeResponse) authorizationResponse).setCode("test-code");

        router.route().order(-1).handler(routingContext -> {
            routingContext.request().headers().set(HttpHeaders.REFERER, "http://localhost:8092/" + routingContext.get(CONTEXT_PATH) + "/mfa/challenge");
            routingContext.setUser(new User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(new io.gravitee.am.model.User())));
            routingContext.next();
        });

        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));
        when(flow.run(any(), any(), any())).thenReturn(Single.just(authorizationResponse));

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/callback&prompt=login",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("http://localhost:9999/callback?code=test-code", location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNotInvokeAuthorizationEndpoint_invalidClient() throws Exception {
        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.empty());

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&client_id=client-id",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("/test/oauth/error?client_id=client-id&error=invalid_request&error_description=No+client+found+for+client_id+client-id"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNotInvokeAuthorizationEndpoint_implicitFlow_nonceMissing() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setScopes(Collections.singletonList("read"));
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));
        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=id_token&client_id=client-id&redirect_uri=http://localhost:9999/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("error=invalid_request&error_description=Missing+parameter%253A+nonce+is+required+for+Implicit+and+Hybrid+Flow"));
                    },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNotInvokeAuthorizationEndpoint_hybridFlow_nonceMissing() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setScopes(Collections.singletonList("read"));
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));
        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code+id_token&client_id=client-id&redirect_uri=http://localhost:9999/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("error=invalid_request&error_description=Missing+parameter%253A+nonce+is+required+for+Implicit+and+Hybrid+Flow"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeAuthorizationEndpoint_hybridFlow_code_IDToken() throws Exception {
        shouldInvokeAuthorizationEndpoint_hybridFlow(io.gravitee.am.common.oidc.ResponseType.CODE_ID_TOKEN, "code=test-code&id_token=test-id-token", null, "test-id-token");
    }

    @Test
    public void shouldInvokeAuthorizationEndpoint_hybridFlow_code_token() throws Exception {
        Token accessToken = new AccessToken("token");
        shouldInvokeAuthorizationEndpoint_hybridFlow(io.gravitee.am.common.oidc.ResponseType.CODE_TOKEN, "code=test-code&access_token=token&token_type=bearer&expires_in=0", accessToken, null);
    }

    @Test
    public void shouldInvokeAuthorizationEndpoint_hybridFlow_code_IDToken_token() throws Exception {
        Token accessToken = new AccessToken("token");
        ((AccessToken) accessToken).setAdditionalInformation(Collections.singletonMap("id_token", "test-id-token"));
        shouldInvokeAuthorizationEndpoint_hybridFlow(io.gravitee.am.common.oidc.ResponseType.CODE_ID_TOKEN_TOKEN, "code=test-code&access_token=token&token_type=bearer&expires_in=0&id_token=test-id-token", accessToken, null);
    }

    @Test
    public void shouldInvokeAuthorizationEndpoint_implicitFlow_IDToken() throws Exception {
        shouldInvokeAuthorizationEndpoint_implicitFlow(io.gravitee.am.common.oidc.ResponseType.ID_TOKEN, "id_token=test-id-token", null, "test-id-token");
    }

    @Test
    public void shouldInvokeAuthorizationEndpoint_implicitFlow_IDToken_token() throws Exception {
        Token accessToken = new AccessToken("token");
        ((AccessToken) accessToken).setAdditionalInformation(Collections.singletonMap("id_token", "test-id-token"));
        shouldInvokeAuthorizationEndpoint_implicitFlow(io.gravitee.am.common.oidc.ResponseType.ID_TOKEN_TOKEN, "access_token=token&token_type=bearer&expires_in=0&id_token=test-id-token", accessToken, null);
    }

    @Test
    public void shouldInvokeAuthorizationEndpoint_implicitFlow_token() throws Exception {
        Token accessToken = new AccessToken("token");
        shouldInvokeAuthorizationEndpoint_implicitFlow(ResponseType.TOKEN, "access_token=token&token_type=bearer&expires_in=0", accessToken, null);
    }

    private void shouldInvokeAuthorizationEndpoint_hybridFlow(String responseType, String expectedCallback, Token accessToken, String idToken) throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setScopes(Collections.singletonList("read"));
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));
        client.setAuthorizedGrantTypes(Arrays.asList(GrantType.AUTHORIZATION_CODE,GrantType.IMPLICIT));
        client.setResponseTypes(Arrays.asList(ResponseType.CODE,ResponseType.TOKEN, io.gravitee.am.common.oidc.ResponseType.ID_TOKEN));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(responseType);
        authorizationRequest.setRedirectUri("http://localhost:9999/callback");


        AuthorizationResponse authorizationResponse = new HybridResponse();
        authorizationResponse.setRedirectUri(authorizationRequest.getRedirectUri());
        ((HybridResponse) authorizationResponse).setCode("test-code");
        if (accessToken != null) {
            ((HybridResponse) authorizationResponse).setAccessToken(accessToken);
        }
        if (idToken != null) {
            ((HybridResponse) authorizationResponse).setIdToken(idToken);
        }

        router.route().order(-1).handler(routingContext -> {
            routingContext.setUser(new User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(new io.gravitee.am.model.User())));
            routingContext.next();
        });

        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));
        when(flow.run(any(), any(), any())).thenReturn(Single.just(authorizationResponse));

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=token&client_id=client-id&nonce=123&redirect_uri=http://localhost:9999/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("http://localhost:9999/callback#" + expectedCallback, location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    private void shouldInvokeAuthorizationEndpoint_implicitFlow(String responseType, String expectedCallback, Token accessToken, String idToken) throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setScopes(Collections.singletonList("read"));
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));
        client.setAuthorizedGrantTypes(Arrays.asList(GrantType.IMPLICIT));
        client.setResponseTypes(Arrays.asList(responseType.split("\\s")));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(responseType);
        authorizationRequest.setRedirectUri("http://localhost:9999/callback");

        AuthorizationResponse authorizationResponse = null;
        if (accessToken != null) {
            authorizationResponse = new ImplicitResponse();
            ((ImplicitResponse) authorizationResponse).setAccessToken(accessToken);
        }
        if (idToken != null) {
            authorizationResponse = new IDTokenResponse();
            ((IDTokenResponse) authorizationResponse).setIdToken(idToken);
        }
        authorizationResponse.setRedirectUri(authorizationRequest.getRedirectUri());

        router.route().order(-1).handler(routingContext -> {
            routingContext.setUser(new User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(new io.gravitee.am.model.User())));
            routingContext.next();
        });

        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));
        when(flow.run(any(), any(), any())).thenReturn(Single.just(authorizationResponse));

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type="+responseType.replaceAll("\\s","%20")+"&client_id=client-id&nonce=123&redirect_uri=http://localhost:9999/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("http://localhost:9999/callback#" + expectedCallback, location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeAuthorizationEndpoint_responseModeQueryJWT() throws Exception {
        io.gravitee.am.model.User user = new io.gravitee.am.model.User();

        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(ResponseType.CODE);
        authorizationRequest.setResponseMode(ResponseMode.QUERY_JWT);
        authorizationRequest.setRedirectUri("http://localhost:9999/callback");

        AuthorizationCodeResponse authorizationResponse = new AuthorizationCodeResponse();
        authorizationResponse.setRedirectUri(authorizationRequest.getRedirectUri());
        authorizationResponse.setCode("test-code");

        JWTAuthorizationCodeResponse jwtAuthorizationCodeResponse = new JWTAuthorizationCodeResponse(authorizationResponse);
        jwtAuthorizationCodeResponse.setResponseType(authorizationRequest.getResponseType());
        jwtAuthorizationCodeResponse.setResponseMode(authorizationRequest.getResponseMode());
        jwtAuthorizationCodeResponse.setToken("my-jwt");


        router.route().order(-1).handler(routingContext -> {
            routingContext.setUser(new User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
            routingContext.next();
        });

        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));
        when(flow.run(any(), any(), any())).thenReturn(Single.just(jwtAuthorizationCodeResponse));

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_mode=query.jwt&response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("http://localhost:9999/callback?response=my-jwt", location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeAuthorizationEndpoint_responseModeFragmentJWT() throws Exception {
        io.gravitee.am.model.User user = new io.gravitee.am.model.User();

        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(ResponseType.CODE);
        authorizationRequest.setResponseMode(ResponseMode.FRAGMENT_JWT);
        authorizationRequest.setRedirectUri("http://localhost:9999/callback");

        AuthorizationCodeResponse authorizationResponse = new AuthorizationCodeResponse();
        authorizationResponse.setRedirectUri(authorizationRequest.getRedirectUri());
        authorizationResponse.setCode("test-code");
        JWTAuthorizationCodeResponse jwtAuthorizationCodeResponse = new JWTAuthorizationCodeResponse(authorizationResponse);
        jwtAuthorizationCodeResponse.setResponseType(authorizationRequest.getResponseType());
        jwtAuthorizationCodeResponse.setResponseMode(authorizationRequest.getResponseMode());
        jwtAuthorizationCodeResponse.setToken("my-jwt");

        router.route().order(-1).handler(routingContext -> {
            routingContext.setUser(new User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
            routingContext.next();
        });

        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));
        when(flow.run(any(), any(), any())).thenReturn(Single.just(jwtAuthorizationCodeResponse));

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_mode=fragment.jwt&response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("http://localhost:9999/callback#response=my-jwt", location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNotInvokeAuthorizationEndpoint_mismatchRedirectUri_responseModeQueryJWT() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setScopes(Collections.singletonList("read"));
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/authorize/callback"));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(ResponseType.CODE);
        authorizationRequest.setRedirectUri("http://localhost:9999/wrong/callback");

        when(jwtService.encodeAuthorization(any(JWT.class), eq(client))).thenReturn(Single.just("my-jwt"));
        when(jweService.encryptAuthorization(anyString(), eq(client))).then(new Answer<Single<String>>() {
            @Override
            public Single<String> answer(InvocationOnMock invocation) throws Throwable {
                return Single.just((String) invocation.getArguments()[0]);
            }
        });

        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_mode=query.jwt&response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/wrong/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("/test/oauth/error?response="));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNotInvokeAuthorizationEndpoint_forcePKCE_noCodeChallenge() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));
        client.setForcePKCE(true);

        router.route().order(-1).handler(routingContext -> {
            routingContext.setUser(new User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(new io.gravitee.am.model.User())));
            routingContext.next();
        });

        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("http://localhost:9999/callback?error=invalid_request&error_description=Missing+parameter%253A+code_challenge", location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }
}
