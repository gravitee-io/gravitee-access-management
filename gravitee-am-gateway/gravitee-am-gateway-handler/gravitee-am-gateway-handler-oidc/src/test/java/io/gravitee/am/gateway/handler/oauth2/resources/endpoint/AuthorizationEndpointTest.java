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


import io.gravitee.am.common.jwt.EncodedJWT;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.ResponseType;
import io.gravitee.am.common.oidc.ResponseMode;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceManager;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.context.ExecutionContextFactory;
import io.gravitee.am.gateway.handler.oauth2.resources.endpoint.authorization.AuthorizationEndpoint;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.*;
import io.gravitee.am.gateway.handler.oauth2.service.par.PushedAuthorizationRequestService;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.response.*;
import io.gravitee.am.gateway.handler.oauth2.service.response.jwt.JWTAuthorizationCodeResponse;
import io.gravitee.am.gateway.handler.oauth2.service.scope.ScopeManager;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.gateway.handler.oauth2.service.token.impl.AccessToken;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDProviderMetadata;
import io.gravitee.am.gateway.handler.oidc.service.flow.Flow;
import io.gravitee.am.gateway.handler.oidc.service.idtoken.IDTokenService;
import io.gravitee.am.gateway.handler.oidc.service.jwe.JWEService;
import io.gravitee.am.gateway.handler.root.resources.handler.common.RedirectUriValidationHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.common.ReturnUrlValidationHandler;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.application.ApplicationScopeSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.rxjava3.ext.auth.User;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.handler.SessionHandler;
import io.vertx.rxjava3.ext.web.sstore.LocalSessionStore;
import io.vertx.rxjava3.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static io.gravitee.am.common.oauth2.GrantType.*;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static io.gravitee.am.gateway.handler.dummies.TestCertificateInfoFactory.createTestCertificateInfo;
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

    @Mock
    private ThymeleafTemplateEngine thymeleafTemplateEngine;

    @Mock
    private PushedAuthorizationRequestService parService;

    @Mock
    private IDTokenService idTokenService;

    @Mock
    private ScopeManager scopeManager;

    @Mock
    private ProtectedResourceManager protectedResourceManager;

    @Mock
    private ExecutionContextFactory executionContextFactory;

    @Mock
    private Environment environment;

    private RoutingContext finalRoutingContext;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        AuthorizationEndpoint authorizationEndpointHandler = new AuthorizationEndpoint(flow, thymeleafTemplateEngine, parService);

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
        when(environment.getProperty("authorization.code.validity", Integer.class, 60000)).thenReturn(60000);

        // set technical routes
        SessionHandler sessionHandler = SessionHandler.create(LocalSessionStore.create(vertx));
        router.route().order(-1)
                .handler(sessionHandler)
                .handler(routingContext -> {
                    routingContext.put(CONTEXT_PATH, "/test");
                    routingContext.next();
                });

        // set Authorization endpoint routes
        router.route(HttpMethod.GET, "/oauth/authorize")
                .handler(new AuthorizationRequestParseProviderConfigurationHandler(openIDDiscoveryService))
                .handler(new AuthorizationRequestParseRequiredParametersHandler())
                .handler(new AuthorizationRequestParseClientHandler(clientSyncService))
                .handler(new AuthorizationRequestParseIdTokenHintHandler(idTokenService))
                .handler(new AuthorizationRequestParseParametersHandler(domain))
                .handler(new RedirectUriValidationHandler(domain))
                .handler(new ReturnUrlValidationHandler(domain))
                .handler(new AuthorizationRequestResolveHandler(domain, scopeManager, protectedResourceManager, executionContextFactory))
                .handler(ctx -> {
                    authorizationEndpointHandler.handle(ctx);
                    finalRoutingContext = ctx;
                });

        router.route()
                .failureHandler(new AuthorizationRequestFailureHandler(openIDDiscoveryService, jwtService, jweService, environment));

        when(parService.deleteRequestUri(any())).thenReturn(Completable.complete());
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
        authorizationRequest.setState("#statewithencodedvalue#");

        AuthorizationCodeResponse authorizationResponse = new AuthorizationCodeResponse();
        authorizationResponse.setRedirectUri(authorizationRequest.getRedirectUri());
        authorizationResponse.setState("#statewithencodedvalue#");
        authorizationResponse.setCode("test-code");

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
                HttpMethod.GET, "/oauth/authorize?response_type=code&client_id=client-id&state=%23statewithencodedvalue%23&redirect_uri=http://localhost:9999/callback&scope=",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertEquals("http://localhost:9999/callback?code=test-code&state=%23statewithencodedvalue%23", location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNotInvokeAuthorizationEndpoint_invalidScope() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setScopeSettings(Collections.singletonList(new ApplicationScopeSettings("read")));
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
                    assertEquals("http://localhost:9999/callback?error=invalid_scope&error_description=Invalid+scope%28s%29%3A+unknown", location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNotInvokeAuthorizationEndpoint_emptyRedirectUri() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setScopeSettings(Collections.singletonList(new ApplicationScopeSettings("read")));

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
        client.setScopeSettings(Collections.singletonList(new ApplicationScopeSettings("read")));
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
                    assertTrue(location.contains("/test/oauth/error?client_id=client-id&error=invalid_request&error_description=Unable+to+find+suitable+redirect_uri%2C+a+redirect_uri+must+be+supplied"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNotInvokeAuthorizationEndpoint_mismatchRedirectUri() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setScopeSettings(Collections.singletonList(new ApplicationScopeSettings("read")));
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
                    assertTrue(location.contains("/test/oauth/error?client_id=client-id&error=redirect_uri_mismatch&error_description=The+redirect_uri+%5B+http%3A%2F%2Flocalhost%3A9999%2Fwrong%2Fcallback+%5D+MUST+match+the+registered+callback+URL+for+this+application"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNotInvokeAuthorizationEndpoint_mismatchRedirectUri_strictMatching() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setScopeSettings(Collections.singletonList(new ApplicationScopeSettings("read")));
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
                    assertTrue(location.contains("/test/oauth/error?client_id=client-id&error=redirect_uri_mismatch&error_description=The+redirect_uri+%5B+http%3A%2F%2Flocalhost%3A9999%2Fauthorize%2Fcallback%3Fparam%3Dparam1+%5D+MUST+match+the+registered+callback+URL+for+this+application"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeAuthorizationEndpoint_noStrictMatching() throws Exception {
        io.gravitee.am.model.User user = new io.gravitee.am.model.User();

        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setScopeSettings(Collections.singletonList(new ApplicationScopeSettings("read")));
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/authorize/callback"));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(ResponseType.CODE);
        authorizationRequest.setRedirectUri("http://localhost:9999/authorize/callback?param=param1");

        AuthorizationCodeResponse authorizationResponse = new AuthorizationCodeResponse();
        authorizationResponse.setRedirectUri(authorizationRequest.getRedirectUri());
        authorizationResponse.setCode("test-code");

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
        client.setScopeSettings(Collections.singletonList(new ApplicationScopeSettings("read")));
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("/test/oauth/error?client_id=client-id&error=invalid_request&error_description=Parameter+%5Bresponse_type%5D+is+included+more+than+once"));
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

        AuthorizationCodeResponse authorizationResponse = new AuthorizationCodeResponse();
        authorizationResponse.setRedirectUri(authorizationRequest.getRedirectUri());
        authorizationResponse.setCode("test-code");

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

        ImplicitResponse authorizationResponse = new ImplicitResponse();
        authorizationResponse.setRedirectUri(authorizationRequest.getRedirectUri());
        authorizationResponse.setAccessToken(accessToken);

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

        ImplicitResponse authorizationResponse = new ImplicitResponse();
        authorizationResponse.setRedirectUri(authorizationRequest.getRedirectUri());
        authorizationResponse.setAccessToken(accessToken);

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

        ImplicitResponse authorizationResponse = new ImplicitResponse();
        authorizationResponse.setRedirectUri(authorizationRequest.getRedirectUri());
        authorizationResponse.setAccessToken(accessToken);

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
        client.setScopeSettings(Collections.singletonList(new ApplicationScopeSettings("read")));
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
        client.setScopeSettings(Collections.singletonList(new ApplicationScopeSettings("read")));
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

        AuthorizationCodeResponse authorizationResponse = new AuthorizationCodeResponse();
        authorizationResponse.setRedirectUri(authorizationRequest.getRedirectUri());
        authorizationResponse.setCode("test-code");

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
                    assertEquals("http://localhost:9999/callback?error=invalid_request&error_description=Missing+parameter%3A+code_challenge", location);
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
                    assertEquals("http://localhost:9999/callback?error=invalid_request&error_description=Invalid+parameter%3A+code_challenge_method", location);
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
                    assertEquals("http://localhost:9999/callback?error=invalid_request&error_description=Invalid+parameter%3A+code_challenge", location);
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

        AuthorizationCodeResponse authorizationResponse = new AuthorizationCodeResponse();
        authorizationResponse.setRedirectUri(authorizationRequest.getRedirectUri());
        authorizationResponse.setCode("test-code");

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

        AuthorizationCodeResponse authorizationResponse = new AuthorizationCodeResponse();
        authorizationResponse.setRedirectUri(authorizationRequest.getRedirectUri());
        authorizationResponse.setCode("test-code");


        router.route().order(-1).handler(routingContext -> {
            routingContext.setUser(new User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(new io.gravitee.am.model.User())));
            routingContext.next();
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
        client.setScopeSettings(Collections.singletonList(new ApplicationScopeSettings("read")));
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
        client.setScopeSettings(Collections.singletonList(new ApplicationScopeSettings("read")));
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(ResponseType.CODE);
        authorizationRequest.setRedirectUri("http://localhost:9999/callback");

        AuthorizationCodeResponse authorizationResponse = new AuthorizationCodeResponse();
        authorizationResponse.setRedirectUri(authorizationRequest.getRedirectUri());
        authorizationResponse.setCode("test-code");

        router.route().order(-1).handler(routingContext -> {
            routingContext.session().put(ConstantKeys.USER_LOGIN_COMPLETED_KEY, true);
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
        client.setScopeSettings(Collections.singletonList(new ApplicationScopeSettings("read")));
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(ResponseType.CODE);
        authorizationRequest.setRedirectUri("http://localhost:9999/callback");

        AuthorizationCodeResponse authorizationResponse = new AuthorizationCodeResponse();
        authorizationResponse.setRedirectUri(authorizationRequest.getRedirectUri());
        authorizationResponse.setCode("test-code");

        router.route().order(-1).handler(routingContext -> {
            routingContext.session().put(ConstantKeys.USER_LOGIN_COMPLETED_KEY, true);
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
    public void shouldInvokeAuthorizationEndpoint_on_silent_auth() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setSilentReAuthentication(true);
        client.setScopeSettings(Collections.singletonList(new ApplicationScopeSettings("read")));
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(ResponseType.CODE);
        authorizationRequest.setRedirectUri("http://localhost:9999/callback");

        AuthorizationCodeResponse authorizationResponse = new AuthorizationCodeResponse();
        authorizationResponse.setRedirectUri(authorizationRequest.getRedirectUri());
        authorizationResponse.setCode("test-code");

        when(idTokenService.extractUser(eq("hint"), any())).thenReturn(Single.just(new io.gravitee.am.model.User()));
        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));
        when(flow.run(any(), any(), any())).thenReturn(Single.just(authorizationResponse));

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/callback&prompt=none&id_token_hint=hint",
                HttpStatusCode.FOUND_302,
                "Found");

        Assertions.assertEquals(finalRoutingContext.get(ConstantKeys.SILENT_AUTH_CONTEXT_KEY), true);
    }

    @Test
    public void shouldInvokeAuthorizationEndpoint_prompt_login_social_auth_step() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setScopeSettings(Collections.singletonList(new ApplicationScopeSettings("read")));
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(ResponseType.CODE);
        authorizationRequest.setRedirectUri("http://localhost:9999/callback");

        AuthorizationCodeResponse authorizationResponse = new AuthorizationCodeResponse();
        authorizationResponse.setRedirectUri(authorizationRequest.getRedirectUri());
        authorizationResponse.setCode("test-code");

        router.route().order(-1).handler(routingContext -> {
            routingContext.session().put(ConstantKeys.USER_LOGIN_COMPLETED_KEY, true);
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
        client.setScopeSettings(Collections.singletonList(new ApplicationScopeSettings("read")));
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));
        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=id_token&client_id=client-id&redirect_uri=http://localhost:9999/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("error=invalid_request&error_description=Missing+parameter%3A+nonce+is+required+for+Implicit+and+Hybrid+Flow"));
                    },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNotInvokeAuthorizationEndpoint_hybridFlow_nonceMissing() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setScopeSettings(Collections.singletonList(new ApplicationScopeSettings("read")));
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));
        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code+id_token&client_id=client-id&redirect_uri=http://localhost:9999/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("error=invalid_request&error_description=Missing+parameter%3A+nonce+is+required+for+Implicit+and+Hybrid+Flow"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeAuthorizationEndpoint_hybridFlow_code_IDToken() throws Exception {
        shouldInvokeAuthorizationEndpoint_hybridFlow(io.gravitee.am.common.oidc.ResponseType.CODE_ID_TOKEN, "code=test-code&state=%23statewithencodedvalue%23&id_token=test-id-token", null, "test-id-token");
    }

    @Test
    public void shouldInvokeAuthorizationEndpoint_hybridFlow_code_token() throws Exception {
        Token accessToken = new AccessToken("token");
        shouldInvokeAuthorizationEndpoint_hybridFlow(io.gravitee.am.common.oidc.ResponseType.CODE_TOKEN, "code=test-code&state=%23statewithencodedvalue%23&access_token=token&token_type=bearer&expires_in=0", accessToken, null);
    }

    @Test
    public void shouldInvokeAuthorizationEndpoint_hybridFlow_code_IDToken_token() throws Exception {
        AccessToken accessToken = new AccessToken("token");
        accessToken.setAdditionalInformation(Collections.singletonMap("id_token", "test-id-token"));
        shouldInvokeAuthorizationEndpoint_hybridFlow(io.gravitee.am.common.oidc.ResponseType.CODE_ID_TOKEN_TOKEN, "code=test-code&state=%23statewithencodedvalue%23&access_token=token&token_type=bearer&expires_in=0&id_token=test-id-token", accessToken, null);
    }

    @Test
    public void shouldInvokeAuthorizationEndpoint_implicitFlow_IDToken() throws Exception {
        shouldInvokeAuthorizationEndpoint_implicitFlow(io.gravitee.am.common.oidc.ResponseType.ID_TOKEN, "id_token=test-id-token&state=%23statewithencodedvalue%23", null, "test-id-token");
    }

    @Test
    public void shouldInvokeAuthorizationEndpoint_implicitFlow_IDToken_token() throws Exception {
        AccessToken accessToken = new AccessToken("token");
        accessToken.setAdditionalInformation(Collections.singletonMap("id_token", "test-id-token"));
        shouldInvokeAuthorizationEndpoint_implicitFlow(io.gravitee.am.common.oidc.ResponseType.ID_TOKEN_TOKEN, "access_token=token&token_type=bearer&expires_in=0&state=%23statewithencodedvalue%23&id_token=test-id-token", accessToken, null);
    }

    @Test
    public void shouldInvokeAuthorizationEndpoint_implicitFlow_token() throws Exception {
        Token accessToken = new AccessToken("token");
        shouldInvokeAuthorizationEndpoint_implicitFlow(ResponseType.TOKEN, "access_token=token&token_type=bearer&expires_in=0&state=%23statewithencodedvalue%23", accessToken, null);
    }

    private void shouldInvokeAuthorizationEndpoint_hybridFlow(String responseType, String expectedCallback, Token accessToken, String idToken) throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setScopeSettings(Collections.singletonList(new ApplicationScopeSettings("read")));
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));
        client.setAuthorizedGrantTypes(Arrays.asList(GrantType.AUTHORIZATION_CODE,GrantType.IMPLICIT));
        client.setResponseTypes(Arrays.asList(ResponseType.CODE,ResponseType.TOKEN, io.gravitee.am.common.oidc.ResponseType.ID_TOKEN));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(responseType);
        authorizationRequest.setRedirectUri("http://localhost:9999/callback");


        HybridResponse authorizationResponse = new HybridResponse();
        authorizationResponse.setRedirectUri(authorizationRequest.getRedirectUri());
        authorizationResponse.setCode("test-code");
        authorizationResponse.setState("#statewithencodedvalue#");
        if (accessToken != null) {
            authorizationResponse.setAccessToken(accessToken);
        }
        if (idToken != null) {
            authorizationResponse.setIdToken(idToken);
        }

        router.route().order(-1).handler(routingContext -> {
            routingContext.setUser(new User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(new io.gravitee.am.model.User())));
            routingContext.next();
        });

        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));
        when(flow.run(any(), any(), any())).thenReturn(Single.just(authorizationResponse));

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=token&client_id=client-id&nonce=123&state=%23statewithencodedvalue%23&redirect_uri=http://localhost:9999/callback",
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
        client.setScopeSettings(Collections.singletonList(new ApplicationScopeSettings("read")));
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));
        client.setAuthorizedGrantTypes(Arrays.asList(GrantType.IMPLICIT));
        client.setResponseTypes(Arrays.asList(responseType));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(responseType);
        authorizationRequest.setRedirectUri("http://localhost:9999/callback");
        authorizationRequest.setState("#statewithencodedvalue#");

        AuthorizationResponse authorizationResponse = null;
        if (accessToken != null) {
            authorizationResponse = new ImplicitResponse();
            authorizationResponse.setState("#statewithencodedvalue#");
            ((ImplicitResponse) authorizationResponse).setAccessToken(accessToken);
        }
        if (idToken != null) {
            authorizationResponse = new IDTokenResponse();
            authorizationResponse.setState("#statewithencodedvalue#");
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
                "/oauth/authorize?response_type="+responseType.replaceAll("\\s","%20")+"&client_id=client-id&nonce=123&state=%23statewithencodedvalue%23&redirect_uri=http://localhost:9999/callback",
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
        client.setScopeSettings(Collections.singletonList(new ApplicationScopeSettings("read")));
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/authorize/callback"));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(ResponseType.CODE);
        authorizationRequest.setRedirectUri("http://localhost:9999/wrong/callback");

        when(jwtService.encodeAuthorization(any(JWT.class), eq(client))).thenReturn(Single.just(new EncodedJWT("my-jwt", createTestCertificateInfo())));
        when(jweService.encryptAuthorization(anyString(), eq(client))).then(invocation -> Single.just((String) invocation.getArguments()[0]));

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
                    assertEquals("http://localhost:9999/callback?error=invalid_request&error_description=Missing+parameter%3A+code_challenge", location);
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNotInvokeAuthorizationEndpoint_invalidFormatRequest_withRedirectUri() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?response_type=code&response_type=code&client_id=client-id&redirect_uri=http://dummy",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    org.assertj.core.api.Assertions.assertThat(location)
                            .endsWith("/test/oauth/error?client_id=client-id&error=invalid_request&error_description=Parameter+%5Bresponse_type%5D+is+included+more+than+once");
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNotInvokeAuthorizationEndpoint_ClientCredentials_JwtBearer_withRedirectUri() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setAuthorizedGrantTypes(List.of(CLIENT_CREDENTIALS, JWT_BEARER));
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(CLIENT_CONTEXT_KEY, client);
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?client_id=client-id&redirect_uri=http://dummy",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("/test/oauth/error?client_id=client-id&error=invalid_request&error_description=Missing+parameter%3A+response_type"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNotInvokeAuthorizationEndpoint_ClientCredentials_withRedirectUri() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setAuthorizedGrantTypes(List.of(CLIENT_CREDENTIALS));
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(CLIENT_CONTEXT_KEY, client);
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?client_id=client-id&redirect_uri=http://dummy",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("/test/oauth/error?client_id=client-id&error=invalid_request&error_description=Missing+parameter%3A+response_type"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNotInvokeAuthorizationEndpoint_JwtBearer_withRedirectUri() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setAuthorizedGrantTypes(List.of(JWT_BEARER));
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(CLIENT_CONTEXT_KEY, client);
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?client_id=client-id&redirect_uri=http://dummy",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("/test/oauth/error?client_id=client-id&error=invalid_request&error_description=Missing+parameter%3A+response_type"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNotInvokeAuthEndpointIfReturnUrlIsWrong() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(ResponseType.CODE);
        authorizationRequest.setRedirectUri("http://localhost:9999/callback");

        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?return_url=http://wrong.url&response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("error=return_url_mismatch"));
                },
                HttpStatusCode.FOUND_302, "Found", null);

    }

    @Test
    public void shouldInvokeAuthEndpointIfReturnUrlIsCorrect_matchRedirectUri() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(ResponseType.CODE);
        authorizationRequest.setRedirectUri("http://localhost:9999/callback");

        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?return_url=http://localhost:9999/callback&response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertFalse(location.contains("error=return_url_mismatch"));
                },
                HttpStatusCode.FOUND_302, "Found", null);

    }

    @Test
    public void shouldInvokeAuthEndpointIfReturnUrlIsCorrect_matchRequestDomain() throws Exception {
        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(ResponseType.CODE);
        authorizationRequest.setRedirectUri("http://localhost:9999/callback");

        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        HttpServerOptions httpServerOptions = getHttpServerOptions();
        System.err.println(httpServerOptions);

        String serverRequestHost = "http://localhost:" + httpServerOptions.getPort() + "/test";

        testRequest(
                HttpMethod.GET,
                "/oauth/authorize?return_url=" + serverRequestHost + "&response_type=code&client_id=client-id&redirect_uri=http://localhost:9999/callback",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertFalse(location.contains("error=return_url_mismatch"));
                },
                HttpStatusCode.FOUND_302, "Found", null);

    }

    @Test
    public void shouldInvokeAuthorizationEndpoint_withRedirectUri() throws Exception {
        io.gravitee.am.model.User user = new io.gravitee.am.model.User();

        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setAuthorizedGrantTypes(List.of(AUTHORIZATION_CODE, JWT_BEARER, CLIENT_CREDENTIALS));
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(ResponseType.CODE);
        authorizationRequest.setRedirectUri("http://localhost:9999/callback");

        AuthorizationResponse authorizationResponse = new AuthorizationCodeResponse();
        authorizationResponse.setRedirectUri(authorizationRequest.getRedirectUri());
        ((AuthorizationCodeResponse) authorizationResponse).setCode("test-code");

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(CLIENT_CONTEXT_KEY, client);
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
    public void shouldCleanSessionIncludingAuthFlowVersion() throws Exception {
        io.gravitee.am.model.User user = new io.gravitee.am.model.User();

        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");
        client.setRedirectUris(Collections.singletonList("http://localhost:9999/callback"));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setApproved(true);
        authorizationRequest.setResponseType(ResponseType.CODE);
        authorizationRequest.setRedirectUri("http://localhost:9999/callback");

        AuthorizationCodeResponse authorizationResponse = new AuthorizationCodeResponse();
        authorizationResponse.setRedirectUri(authorizationRequest.getRedirectUri());
        authorizationResponse.setCode("test-code");

        router.route().order(-1).handler(routingContext -> {
            routingContext.setUser(new User(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user)));
            // Set transaction ID and auth flow version in the session
            routingContext.session().put(ConstantKeys.TRANSACTION_ID_KEY, "test-transaction-id");
            routingContext.session().put(ConstantKeys.AUTH_FLOW_CONTEXT_VERSION_KEY, 2);
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
                    // Verify that both TRANSACTION_ID_KEY and AUTH_FLOW_CONTEXT_VERSION_KEY are removed
                    assertNull(finalRoutingContext.session().get(ConstantKeys.TRANSACTION_ID_KEY));
                    assertNull(finalRoutingContext.session().get(ConstantKeys.AUTH_FLOW_CONTEXT_VERSION_KEY));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }
}
