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

import io.gravitee.am.common.oauth2.ResponseType;
import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.gateway.handler.oauth2.exception.AccessDeniedException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidScopeException;
import io.gravitee.am.gateway.handler.oauth2.exception.RedirectMismatchException;
import io.gravitee.am.gateway.handler.oauth2.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.response.AuthorizationCodeResponse;
import io.gravitee.am.gateway.handler.oauth2.response.AuthorizationResponse;
import io.gravitee.am.gateway.handler.oauth2.response.HybridResponse;
import io.gravitee.am.gateway.handler.oauth2.response.ImplicitResponse;
import io.gravitee.am.gateway.handler.oauth2.token.AccessToken;
import io.gravitee.am.gateway.handler.oauth2.token.impl.DefaultAccessToken;
import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.gravitee.am.gateway.handler.oidc.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.discovery.OpenIDProviderMetadata;
import io.gravitee.am.gateway.handler.oidc.flow.Flow;
import io.gravitee.am.gateway.handler.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.vertx.handler.oauth2.endpoint.authorization.AuthorizationEndpointFailureHandler;
import io.gravitee.am.gateway.handler.vertx.handler.oauth2.endpoint.authorization.AuthorizationEndpointHandler;
import io.gravitee.am.gateway.handler.vertx.handler.oauth2.endpoint.authorization.AuthorizationRequestParseHandler;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.Domain;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class AuthorizationEndpointHandlerTest  extends RxWebTestBase {

    @Mock
    private Flow flow;

    @Mock
    private Domain domain;

    @Mock
    private ClientService clientService;

    @Mock
    private OpenIDDiscoveryService openIDDiscoveryService;

    @InjectMocks
    private AuthorizationEndpointHandler authorizationEndpointHandler = new AuthorizationEndpointHandler(flow, domain);

    @Override
    public void setUp() throws Exception {
        super.setUp();
        SessionHandler sessionHandler = SessionHandler.create(LocalSessionStore.create(vertx));
        OpenIDProviderMetadata openIDProviderMetadata = new OpenIDProviderMetadata();
        openIDProviderMetadata.setResponseTypesSupported(Arrays.asList(ResponseType.CODE, ResponseType.TOKEN, io.gravitee.am.common.oidc.ResponseType.CODE_ID_TOKEN, io.gravitee.am.common.oidc.ResponseType.CODE_TOKEN, io.gravitee.am.common.oidc.ResponseType.CODE_ID_TOKEN_TOKEN));
        when(openIDDiscoveryService.getConfiguration(anyString())).thenReturn(openIDProviderMetadata);
        AuthorizationRequestParseHandler authorizationRequestParseHandler = AuthorizationRequestParseHandler.create(domain, openIDDiscoveryService);
        router.route("/oauth/authorize").handler(sessionHandler);
        router.route(HttpMethod.GET, "/oauth/authorize").handler(authorizationRequestParseHandler).handler(authorizationEndpointHandler);
        router.route().failureHandler(new AuthorizationEndpointFailureHandler(domain, clientService));
    }

    @Test
    public void shouldNotInvokeAuthorizationEndpoint_noUser_noRedirectUri() throws Exception {
        when(domain.getPath()).thenReturn("test");
        testRequest(
                HttpMethod.GET,
                "/oauth/authorize",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("/test/oauth/error", location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNotInvokeAuthorizationEndpoint_noUser_withRedirectUri() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        when(domain.getPath()).thenReturn("test");
        when(clientService.findByClientId("client-id")).thenReturn(Maybe.just(client));

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
    public void shouldNotInvokeAuthorizationEndpoint_emptyScope() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        when(domain.getPath()).thenReturn("test");
        when(clientService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        testRequest(
                HttpMethod.GET, "/oauth/authorize?response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/callback&scope=",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("http://localhost:9999/callback?error=invalid_scope&error_description=Invalid+parameter%253A+scope+must+not+be+empty", location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNotInvokeAuthorizationEndpoint_invalidScope() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setScopes(Collections.singletonList("read"));
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/authorize/callback"));

        router.route().order(-1).handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext routingContext) {
                routingContext.setUser(new User(new io.gravitee.am.gateway.handler.vertx.auth.user.User(new io.gravitee.am.model.User())));
                routingContext.next();
            }
        });

        when(domain.getPath()).thenReturn("test");
        when(clientService.findByClientId("client-id")).thenReturn(Maybe.just(client));
        when(flow.run(any(), any())).thenReturn(Single.error(new InvalidScopeException("Invalid scope(s): unknown")));

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
    public void shouldNotInvokeAuthorizationEndpoint_mismatchRedirectUri() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setScopes(Collections.singletonList("read"));
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/authorize/callback"));

        when(domain.getPath()).thenReturn("test");
        when(clientService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        router.route().order(-1).handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext routingContext) {
                routingContext.setUser(new User(new io.gravitee.am.gateway.handler.vertx.auth.user.User(new io.gravitee.am.model.User())));
                routingContext.next();
            }
        });

        when(flow.run(any(), any())).thenReturn(Single.error(new RedirectMismatchException("The redirect_uri MUST match the registered callback URL for this application")));

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("http://localhost:9999/authorize/callback?error=redirect_uri_mismatch&error_description=The+redirect_uri+MUST+match+the+registered+callback+URL+for+this+application", location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeAuthorizationEndpoint_approvalPage() throws Exception {
        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(false);

        when(domain.getPath()).thenReturn("test");

        router.route().order(-1).handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext routingContext) {
                routingContext.setUser(new User(new io.gravitee.am.gateway.handler.vertx.auth.user.User(new io.gravitee.am.model.User())));
                routingContext.next();
            }
        });

        when(flow.run(any(), any())).thenReturn(Single.error(new AccessDeniedException("User denied access")));

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.startsWith("/test/oauth/confirm_access"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeAuthorizationEndpoint_responseTypeCode() throws Exception {
        io.gravitee.am.model.User user = new io.gravitee.am.model.User();

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(OAuth2Constants.CODE);
        authorizationRequest.setRedirectUri("http://localhost:9999/callback");

        AuthorizationResponse authorizationResponse = new AuthorizationCodeResponse();
        authorizationResponse.setRedirectUri(authorizationRequest.getRedirectUri());
        ((AuthorizationCodeResponse) authorizationResponse).setCode("test-code");

        router.route().order(-1).handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext routingContext) {
                routingContext.setUser(new User(new io.gravitee.am.gateway.handler.vertx.auth.user.User(user)));
                routingContext.next();
            }
        });

        when(domain.getPath()).thenReturn("test");
        when(flow.run(any(), any())).thenReturn(Single.just(authorizationResponse));

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
    public void shouldInvokeAuthorizationEndpoint_responseTypeToken() throws Exception {
        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(OAuth2Constants.TOKEN);
        authorizationRequest.setRedirectUri("http://localhost:9999/callback");

        AccessToken accessToken = new DefaultAccessToken("token");

        AuthorizationResponse authorizationResponse = new ImplicitResponse();
        authorizationResponse.setRedirectUri(authorizationRequest.getRedirectUri());
        ((ImplicitResponse) authorizationResponse).setAccessToken(accessToken);

        router.route().order(-1).handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext routingContext) {
                routingContext.setUser(new User(new io.gravitee.am.gateway.handler.vertx.auth.user.User(new io.gravitee.am.model.User())));
                routingContext.next();
            }
        });

        when(domain.getPath()).thenReturn("test");
        when(flow.run(any(), any())).thenReturn(Single.just(authorizationResponse));

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=token&client_id=client-id&redirect_uri=http://localhost:9999/callback",
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

        when(domain.getPath()).thenReturn("test");
        when(clientService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/callback&prompt=none",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("http://localhost:9999/callback?error=login_required", location);
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

        when(domain.getPath()).thenReturn("test");
        when(clientService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        router.route().order(-1).handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext routingContext) {
                io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
                endUser.setLoggedAt(new Date(System.currentTimeMillis()-24*60*60*1000));
                routingContext.setUser(new User(new io.gravitee.am.gateway.handler.vertx.auth.user.User(endUser)));
                routingContext.next();
            }
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

        when(domain.getPath()).thenReturn("test");
        when(clientService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        router.route().order(-1).handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext routingContext) {
                io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
                endUser.setLoggedAt(new Date(System.currentTimeMillis()-24*60*60*1000));
                routingContext.setUser(new User(new io.gravitee.am.gateway.handler.vertx.auth.user.User(endUser)));
                routingContext.next();
            }
        });

        // user is logged since yesterday, he must be redirected to the login page
        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/callback&max_age=1&prompt=none",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("http://localhost:9999/callback?error=login_required", location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeAuthorizationEndpoint_max_age() throws Exception {
        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(OAuth2Constants.CODE);
        authorizationRequest.setRedirectUri("http://localhost:9999/callback");

        AuthorizationResponse authorizationResponse = new AuthorizationCodeResponse();
        authorizationResponse.setRedirectUri(authorizationRequest.getRedirectUri());
        ((AuthorizationCodeResponse) authorizationResponse).setCode("test-code");

        router.route().order(-1).handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext routingContext) {
                io.gravitee.am.model.User endUser = new io.gravitee.am.model.User();
                endUser.setLoggedAt(new Date(System.currentTimeMillis()- 60*1000));
                routingContext.setUser(new User(new io.gravitee.am.gateway.handler.vertx.auth.user.User(new io.gravitee.am.model.User())));
                routingContext.next();
            }
        });

        when(domain.getPath()).thenReturn("test");
        when(flow.run(any(), any())).thenReturn(Single.just(authorizationResponse));


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

        when(domain.getPath()).thenReturn("test");
        when(clientService.findByClientId("client-id")).thenReturn(Maybe.just(client));

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

        when(domain.getPath()).thenReturn("test");
        when(clientService.findByClientId("client-id")).thenReturn(Maybe.just(client));

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

        when(domain.getPath()).thenReturn("test");
        when(clientService.findByClientId("client-id")).thenReturn(Maybe.just(client));

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
        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(OAuth2Constants.CODE);
        authorizationRequest.setRedirectUri("http://localhost:9999/callback");

        AuthorizationResponse authorizationResponse = new AuthorizationCodeResponse();
        authorizationResponse.setRedirectUri(authorizationRequest.getRedirectUri());
        ((AuthorizationCodeResponse) authorizationResponse).setCode("test-code");

        router.route().order(-1).handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext routingContext) {
                routingContext.setUser(new User(new io.gravitee.am.gateway.handler.vertx.auth.user.User(new io.gravitee.am.model.User())));
                routingContext.next();
            }
        });

        when(domain.getPath()).thenReturn("test");
        when(flow.run(any(), any())).thenReturn(Single.just(authorizationResponse));

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
        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(OAuth2Constants.CODE);
        authorizationRequest.setRedirectUri("http://localhost:9999/callback");

        AuthorizationResponse authorizationResponse = new AuthorizationCodeResponse();
        authorizationResponse.setRedirectUri(authorizationRequest.getRedirectUri());
        ((AuthorizationCodeResponse) authorizationResponse).setCode("test-code");


        router.route().order(-1).handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext routingContext) {
                routingContext.setUser(new User(new io.gravitee.am.gateway.handler.vertx.auth.user.User(new io.gravitee.am.model.User())));
                routingContext.next();
            }
        });

        when(domain.getPath()).thenReturn("test");
        when(flow.run(any(), any())).thenReturn(Single.just(authorizationResponse));

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
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/authorize/callback"));

        when(domain.getPath()).thenReturn("test");
        when(clientService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        router.route().order(-1).handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext routingContext) {
                routingContext.setUser(new User(new io.gravitee.am.gateway.handler.vertx.auth.user.User(new io.gravitee.am.model.User())));
                routingContext.next();
            }
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
    public void shouldInvokeAuthorizationEndpoint_hybridFlow_code_IDToken() throws Exception {
        shouldInvokeAuthorizationEndpoint_hybridFlow(io.gravitee.am.common.oidc.ResponseType.CODE_ID_TOKEN, "code=test-code&id_token=test-id-token", null, "test-id-token");
    }

    @Test
    public void shouldInvokeAuthorizationEndpoint_hybridFlow_code_token() throws Exception {
        AccessToken accessToken = new DefaultAccessToken("token");
        shouldInvokeAuthorizationEndpoint_hybridFlow(io.gravitee.am.common.oidc.ResponseType.CODE_TOKEN, "code=test-code&access_token=token&token_type=bearer&expires_in=0", accessToken, null);
    }

    @Test
    public void shouldInvokeAuthorizationEndpoint_hybridFlow_code_IDToken_token() throws Exception {
        AccessToken accessToken = new DefaultAccessToken("token");
        ((DefaultAccessToken) accessToken).setAdditionalInformation(Collections.singletonMap("id_token", "test-id-token"));
        shouldInvokeAuthorizationEndpoint_hybridFlow(io.gravitee.am.common.oidc.ResponseType.CODE_ID_TOKEN_TOKEN, "code=test-code&access_token=token&token_type=bearer&expires_in=0&id_token=test-id-token", accessToken, null);
    }

    private void shouldInvokeAuthorizationEndpoint_hybridFlow(String responseType, String expectedCallback, AccessToken accessToken, String idToken) throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setScopes(Collections.singletonList("read"));
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

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

        router.route().order(-1).handler(new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext routingContext) {
                routingContext.setUser(new User(new io.gravitee.am.gateway.handler.vertx.auth.user.User(new io.gravitee.am.model.User())));
                routingContext.next();
            }
        });

        when(domain.getPath()).thenReturn("test");
        when(flow.run(any(), any())).thenReturn(Single.just(authorizationResponse));

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=token&client_id=client-id&redirect_uri=http://localhost:9999/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("http://localhost:9999/callback#" + expectedCallback, location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

}