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
package io.gravitee.am.gateway.handler.root.resources.endpoint.logout;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.certificate.CertificateProvider;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.root.resources.handler.error.ErrorHandler;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.gateway.handler.root.service.user.model.UserToken;
import io.gravitee.am.identityprovider.api.common.Request;
import io.gravitee.am.identityprovider.api.social.CloseSessionMode;
import io.gravitee.am.identityprovider.api.social.SocialAuthenticationProvider;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.service.AuthenticationFlowContextService;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.ext.web.client.HttpResponse;
import io.vertx.rxjava3.ext.web.client.WebClient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class LogoutEndpointHandlerTest extends RxWebTestBase {

    @Mock
    private Domain domain;
    @Mock
    private ClientSyncService clientSyncService;
    @Mock
    private JWTService jwtService;
    @Mock
    private UserService userService;
    @Mock
    private AuthenticationFlowContextService authenticationFlowContextService;
    @Mock
    private IdentityProviderManager identityProviderManager;
    @Mock
    private CertificateManager certificateManager;
    @Mock
    private WebClient webClient;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        router.route(HttpMethod.GET, "/logout")
                .handler(new LogoutEndpoint(domain, clientSyncService, jwtService, userService, authenticationFlowContextService, identityProviderManager, certificateManager, webClient))
                .failureHandler(new ErrorHandler("/error"));
    }

    @Test
    public void shouldInvokeLogoutEndpoint_noTargetUrl() throws Exception {
        testRequest(
                HttpMethod.GET, "/logout",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.equals("/"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeLogoutEndpoint_targetUrl_noClient() throws Exception {
        testRequest(
                HttpMethod.GET, "/logout?target_url=https%3A%2F%2Ftest",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.equals("https://test"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeLogoutEndpoint_targetUrl_withDeepLink() throws Exception {
        testRequest(
                HttpMethod.GET, "/logout?target_url=net.openid.appauthdemo:/oauth2redirect",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.equals("net.openid.appauthdemo:/oauth2redirect"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeLogoutEndpoint_Invalid_targetUrl_noClient() throws Exception {
        testRequest(
                HttpMethod.GET, "/logout?target_url=https%3A%2F%2Fsomewhere\\@test",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("error=redirect_uri_mismatch"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }


    @Test
    public void shouldInvokeLogoutEndpoint_targetUrl_alloaw_atDomainLevel_appUrls_not_defined() throws Exception {
        Client client = new Client();
        client.setClientId("123");
        // no redirectUris registered for logout at App Level
        client.setPostLogoutRedirectUris(null);
        when(clientSyncService.findById("client-id")).thenReturn(Maybe.just(client));
        when(userService.logout(any(), eq(false), any())).thenReturn(Completable.complete());

        final OIDCSettings oidcSettings = new OIDCSettings();
        // redirect logout URI defined at domain level
        oidcSettings.setPostLogoutRedirectUris(List.of("https://test"));
        when(domain.getOidc()).thenReturn(oidcSettings);

        router.route().order(-1).handler(routingContext -> {
            User endUser = new User();
            endUser.setClient("client-id");
            routingContext.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET, "/logout?target_url=https%3A%2F%2Ftest",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.equals("https://test"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeLogoutEndpoint_targetUrl_allow_atDomainLevel_appUrls_not_defined_emptyList() throws Exception {
        Client client = new Client();
        client.setClientId("123");
        // no redirectUris registered for logout at App Level
        client.setPostLogoutRedirectUris(Collections.emptyList());
        when(clientSyncService.findById("client-id")).thenReturn(Maybe.just(client));
        when(userService.logout(any(), eq(false), any())).thenReturn(Completable.complete());

        final OIDCSettings oidcSettings = new OIDCSettings();
        // redirect logout URI defined at domain level
        oidcSettings.setPostLogoutRedirectUris(List.of("https://test"));
        when(domain.getOidc()).thenReturn(oidcSettings);

        router.route().order(-1).handler(routingContext -> {
            User endUser = new User();
            endUser.setClient("client-id");
            routingContext.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET, "/logout?target_url=https%3A%2F%2Ftest",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.equals("https://test"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeLogoutEndpoint_targetUrl_not_allowed_atDomainLevel_appUrls_not_defined() throws Exception {
        Client client = new Client();
        client.setClientId("123");
        // no redirectUris registered for logout at App Level
        client.setPostLogoutRedirectUris(null);
        when(clientSyncService.findById("client-id")).thenReturn(Maybe.just(client));
        when(userService.logout(any(), eq(false), any())).thenReturn(Completable.complete());

        final OIDCSettings oidcSettings = new OIDCSettings();
        // redirect logout URI defined at domain level
        oidcSettings.setPostLogoutRedirectUris(List.of("https://test"));
        when(domain.getOidc()).thenReturn(oidcSettings);

        router.route().order(-1).handler(routingContext -> {
            User endUser = new User();
            endUser.setClient("client-id");
            routingContext.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET, "/logout?target_url=https%3A%2F%2Fnot-allowed",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("invalid_request&error_description=The+post_logout_redirect_uri+MUST+match+the+registered+callback+URLs"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }


    @Test
    public void shouldInvokeLogoutEndpoint_targetUrl_client_noRestriction() throws Exception {
        Client client = new Client();
        client.setPostLogoutRedirectUris(null);
        when(clientSyncService.findById("client-id")).thenReturn(Maybe.just(client));
        when(userService.logout(any(), eq(false), any())).thenReturn(Completable.complete());

        router.route().order(-1).handler(routingContext -> {
            User endUser = new User();
            endUser.setClient("client-id");
            routingContext.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET, "/logout?target_url=https%3A%2F%2Ftest",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.equals("https://test"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeLogoutEndpoint_targetUrl_client_restriction() throws Exception {
        Client client = new Client();
        client.setPostLogoutRedirectUris(Arrays.asList("https://test"));
        when(clientSyncService.findById("client-id")).thenReturn(Maybe.just(client));
        when(userService.logout(any(), eq(false), any())).thenReturn(Completable.complete());

        router.route().order(-1).handler(routingContext -> {
            User endUser = new User();
            endUser.setClient("client-id");
            routingContext.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET, "/logout?target_url=https%3A%2F%2Ftest",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.equals("https://test"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeLogoutEndpoint_targetUrl_client_with_param_wildcard_restriction() throws Exception {
        Client client = new Client();
        client.setPostLogoutRedirectUris(Arrays.asList("https://test/*"));
        when(clientSyncService.findById("client-id")).thenReturn(Maybe.just(client));
        when(userService.logout(any(), eq(false), any())).thenReturn(Completable.complete());

        router.route().order(-1).handler(routingContext -> {
            User endUser = new User();
            endUser.setClient("client-id");
            routingContext.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET, "/logout?target_url=https%3A%2F%2Ftest?u=200",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.equals("https://test?u=200"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeLogoutEndpoint_targetUrl_with_param_client_wildcard_not_allowed() throws Exception {
        Client client = new Client();
        client.setPostLogoutRedirectUris(Arrays.asList("https://test.com"));
        when(clientSyncService.findById("client-id")).thenReturn(Maybe.just(client));
        when(userService.logout(any(), eq(false), any())).thenReturn(Completable.complete());

        router.route().order(-1).handler(routingContext -> {
            User endUser = new User();
            endUser.setClient("client-id");
            routingContext.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET, "/logout?target_url=https%3A%2F%2Ftest.com?u=200",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.equals("https://test.com?u=200"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeLogoutEndpoint_targetUrl_with_param_client_strict_match_true() throws Exception {
        Client client = new Client();
        client.setClientId("123");
        client.setPostLogoutRedirectUris(Arrays.asList("https://test.com"));
        when(clientSyncService.findById("client-id")).thenReturn(Maybe.just(client));
        when(userService.logout(any(), eq(false), any())).thenReturn(Completable.complete());
        when(domain.isRedirectUriStrictMatching()).thenReturn(true);

        router.route().order(-1).handler(routingContext -> {
            User endUser = new User();
            endUser.setClient("client-id");
            routingContext.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET, "/logout?target_url=https%3A%2F%2Ftest.com?u=200",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("invalid_request&error_description=The+post_logout_redirect_uri+MUST+match+the+registered+callback+URLs"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeLogoutEndpoint_targetUrl_with_param_client_strict_match_false() throws Exception {
        Client client = new Client();
        client.setClientId("123");
        client.setPostLogoutRedirectUris(Arrays.asList("https://test.com"));
        when(clientSyncService.findById("client-id")).thenReturn(Maybe.just(client));
        when(userService.logout(any(), eq(false), any())).thenReturn(Completable.complete());
        when(domain.isRedirectUriStrictMatching()).thenReturn(false);

        router.route().order(-1).handler(routingContext -> {
            User endUser = new User();
            endUser.setClient("client-id");
            routingContext.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET, "/logout?target_url=https%3A%2F%2Ftest.com?u=200",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("https://test.com?u=200"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeLogoutEndpoint_targetUrl_client_restriction_2() throws Exception {
        Client client = new Client();
        client.setPostLogoutRedirectUris(Arrays.asList("https://test", "https://dev"));
        when(clientSyncService.findById("client-id")).thenReturn(Maybe.just(client));
        when(userService.logout(any(), eq(false), any())).thenReturn(Completable.complete());

        router.route().order(-1).handler(routingContext -> {
            User endUser = new User();
            endUser.setClient("client-id");
            routingContext.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET, "/logout?target_url=https%3A%2F%2Ftest",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.equals("https://test"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeLogoutEndpoint_targetUrl_client_restriction_mismatch() throws Exception {
        Client client = new Client();
        client.setClientId("client-id");
        client.setPostLogoutRedirectUris(Arrays.asList("https://dev"));
        when(clientSyncService.findById("client-id")).thenReturn(Maybe.just(client));
        when(userService.logout(any(), eq(false), any())).thenReturn(Completable.complete());

        router.route().order(-1).handler(routingContext -> {
            User endUser = new User();
            endUser.setClient("client-id");
            routingContext.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET, "/logout?target_url=https%3A%2F%2Ftest",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("/error?target_url=https%3A%2F%2Ftest&client_id=client-id&error=invalid_request&error_description=The+post_logout_redirect_uri+MUST+match+the+registered+callback+URLs"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeLogoutEndpoint_targetUrl_client_id_token_restriction_mismatch() throws Exception {
        JWT jwt = new JWT();
        jwt.setAud("client-id");

        Client client = new Client();
        client.setClientId("client-id");
        client.setPostLogoutRedirectUris(Arrays.asList("https://dev"));

        User endUser = new User();
        endUser.setId("user-id");
        endUser.setClient("client-id");

        when(userService.extractSessionFromIdToken("idToken")).thenReturn(Single.just(new UserToken(endUser, client)));
        when(userService.logout(any(), eq(false), any())).thenReturn(Completable.complete());

        router.route().order(-1).handler(routingContext -> {
            routingContext.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET, "/logout?post_logout_redirect_uri=https%3A%2F%2Ftest&id_token_hint=idToken",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("/error?post_logout_redirect_uri=https%3A%2F%2Ftest&id_token_hint=idToken&client_id=client-id&error=invalid_request&error_description=The+post_logout_redirect_uri+MUST+match+the+registered+callback+URLs"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeLogoutEndpoint_targetUrl_client_id_authorize_redirect_uri_client_id() throws Exception {
        Client clientParam = new Client();
        clientParam.setClientId("client-id-param");
        clientParam.setPostLogoutRedirectUris(Arrays.asList("https://test"));

        Client client = new Client();
        client.setClientId("client-id");
        client.setPostLogoutRedirectUris(Arrays.asList("https://dev"));

        User endUser = new User();
        endUser.setId("user-id");
        endUser.setClient("client-id");

        when(userService.extractSessionFromIdToken("idToken")).thenReturn(Single.just(new UserToken(endUser, client)));
        when(userService.logout(any(), eq(false), any())).thenReturn(Completable.complete());

        router.route().order(-1).handler(routingContext -> {
            routingContext.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, clientParam);
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET, "/logout?post_logout_redirect_uri=https%3A%2F%2Ftest&id_token_hint=idToken&client_id=client-id-param",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.startsWith("https://test"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeLogoutEndpoint_targetUrl_noClient_restriction_mismatch() throws Exception {
        OIDCSettings oidcSettings = mock(OIDCSettings.class);
        when(oidcSettings.getPostLogoutRedirectUris()).thenReturn(Arrays.asList("https://dev"));
        when(domain.getOidc()).thenReturn(oidcSettings);

        testRequest(
                HttpMethod.GET, "/logout?target_url=https%3A%2F%2Ftest",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("/error?target_url=https%3A%2F%2Ftest&error=invalid_request&error_description=The+post_logout_redirect_uri+MUST+match+the+registered+callback+URLs"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeExternalOIDCLogoutEndpoint_targetUrl_restriction_mismatch() throws Exception {
        JWT jwt = new JWT();
        jwt.setAud("client-id");

        Client client = new Client();
        client.setClientId("client-id");
        client.setPostLogoutRedirectUris(Arrays.asList("https://dev"));
        client.setSingleSignOut(true);

        when(certificateManager.defaultCertificateProvider()).thenReturn(mock(CertificateProvider.class));
        when(jwtService.encode(any(JWT.class), any(CertificateProvider.class))).thenReturn(Single.just("jwtstatevalue"));

        when(clientSyncService.findById("client-id")).thenReturn(Maybe.empty());
        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        final SocialAuthenticationProvider authProvider = mock(SocialAuthenticationProvider.class);
        final Request req = new Request();
        req.setUri("https://oidc/logout");
        req.setMethod(io.gravitee.common.http.HttpMethod.GET);
        when(authProvider.signOutUrl(any())).thenReturn(Maybe.just(req));
        when(authProvider.closeSessionAfterSignIn()).thenReturn(CloseSessionMode.KEEP_ACTIVE);
        when(identityProviderManager.get(any())).thenReturn(Maybe.just(authProvider));

        router.route().order(-1).handler(routingContext -> {
            User endUser = new User();
            endUser.setClient("client-id");
            final HashMap<String, Object> additionalInformation = new HashMap<>();
            additionalInformation.put(ConstantKeys.OIDC_PROVIDER_ID_TOKEN_KEY, "opidtokenvalue");
            endUser.setAdditionalInformation(additionalInformation);
            routingContext.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET, "/logout?target_url=https%3A%2F%2Ftest",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.endsWith("/error?target_url=https%3A%2F%2Ftest&client_id=client-id&error=invalid_request&error_description=The+post_logout_redirect_uri+MUST+match+the+registered+callback+URLs"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldInvokeExternalOIDCLogoutEndpoint_targetUrlOk() throws Exception {
        JWT jwt = new JWT();
        jwt.setAud("client-id");

        Client client = new Client();
        client.setClientId("client-id");
        client.setPostLogoutRedirectUris(Arrays.asList("https://dev"));
        client.setSingleSignOut(true);

        when(certificateManager.defaultCertificateProvider()).thenReturn(mock(CertificateProvider.class));
        when(jwtService.encode(any(JWT.class), any(CertificateProvider.class))).thenReturn(Single.just("jwtstatevalue"));

        when(clientSyncService.findById("client-id")).thenReturn(Maybe.empty());
        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        final SocialAuthenticationProvider authProvider = mock(SocialAuthenticationProvider.class);
        final Request req = new Request();
        req.setUri("https://oidc/logout");
        req.setMethod(io.gravitee.common.http.HttpMethod.GET);
        when(authProvider.signOutUrl(any())).thenReturn(Maybe.just(req));
        when(authProvider.closeSessionAfterSignIn()).thenReturn(CloseSessionMode.KEEP_ACTIVE);
        when(identityProviderManager.get(any())).thenReturn(Maybe.just(authProvider));

        router.route().order(-1).handler(routingContext -> {
            User endUser = new User();
            endUser.setClient("client-id");
            final HashMap<String, Object> additionalInformation = new HashMap<>();
            additionalInformation.put(ConstantKeys.OIDC_PROVIDER_ID_TOKEN_KEY, "opidtokenvalue");
            endUser.setAdditionalInformation(additionalInformation);
            routingContext.put(UriBuilderRequest.CONTEXT_PATH, "/domain");
            routingContext.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET, "/logout?target_url=https%3A%2F%2Fdev",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.startsWith("https://oidc/logout?post_logout_redirect_uri=http://localhost:"));
                    assertTrue(location.endsWith("/domain/logout/callback&id_token_hint=opidtokenvalue&state=jwtstatevalue"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldNotInvokeExternalOIDCLogoutEndpoint_targetUrlOk_butRemoteSessionClosed() throws Exception {
        JWT jwt = new JWT();
        jwt.setAud("client-id");

        Client client = new Client();
        client.setClientId("client-id");
        client.setPostLogoutRedirectUris(Arrays.asList("https://dev"));
        client.setSingleSignOut(true);

        when(clientSyncService.findById("client-id")).thenReturn(Maybe.empty());
        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));
        when(userService.logout(any(), anyBoolean(), any())).thenReturn(Completable.complete());

        final SocialAuthenticationProvider authProvider = mock(SocialAuthenticationProvider.class);
        when(identityProviderManager.get(any())).thenReturn(Maybe.just(authProvider));

        router.route().order(-1).handler(routingContext -> {
            User endUser = new User();
            endUser.setClient("client-id");
            final HashMap<String, Object> additionalInformation = new HashMap<>();
            additionalInformation.put(ConstantKeys.OIDC_PROVIDER_ID_TOKEN_KEY, "opidtokenvalue");
            endUser.setAdditionalInformation(additionalInformation);
            routingContext.put(UriBuilderRequest.CONTEXT_PATH, "/domain");
            routingContext.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET, "/logout?target_url=https%3A%2F%2Fdev",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.startsWith("https://dev"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
        verify(authProvider, never()).signOutUrl(any());
    }

    @Test
    public void shouldInvokeExternalOIDCLogoutEndpoint_noTargetUrl() throws Exception {
        JWT jwt = new JWT();
        jwt.setAud("client-id");

        Client client = new Client();
        client.setClientId("client-id");
        client.setPostLogoutRedirectUris(Arrays.asList("https://dev"));
        client.setSingleSignOut(true);

        when(certificateManager.defaultCertificateProvider()).thenReturn(mock(CertificateProvider.class));
        when(jwtService.encode(any(JWT.class), any(CertificateProvider.class))).thenReturn(Single.just("jwtstatevalue"));

        when(clientSyncService.findById("client-id")).thenReturn(Maybe.empty());
        when(clientSyncService.findByClientId("client-id")).thenReturn(Maybe.just(client));

        final SocialAuthenticationProvider authProvider = mock(SocialAuthenticationProvider.class);
        final Request req = new Request();
        req.setUri("https://oidc/logout");
        req.setMethod(io.gravitee.common.http.HttpMethod.GET);
        when(authProvider.signOutUrl(any())).thenReturn(Maybe.just(req));
        when(authProvider.closeSessionAfterSignIn()).thenReturn(CloseSessionMode.KEEP_ACTIVE);
        when(identityProviderManager.get(any())).thenReturn(Maybe.just(authProvider));

        io.vertx.rxjava3.ext.web.client.HttpRequest<io.vertx.rxjava3.core.buffer.Buffer> httpRequest = mock(io.vertx.rxjava3.ext.web.client.HttpRequest.class);
        io.vertx.ext.web.client.HttpResponse<io.vertx.rxjava3.core.buffer.Buffer> httpResponse = mock(io.vertx.ext.web.client.HttpResponse.class);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpRequest.rxSend()).thenReturn(Single.just(new HttpResponse<>(httpResponse)));
        when(webClient.getAbs(anyString())).thenReturn(httpRequest);

        when(userService.logout(any(), eq(false), any())).thenReturn(Completable.complete());

        router.route().order(-1).handler(routingContext -> {
            User endUser = new User();
            endUser.setClient("client-id");
            final HashMap<String, Object> additionalInformation = new HashMap<>();
            additionalInformation.put(ConstantKeys.OIDC_PROVIDER_ID_TOKEN_KEY, "opidtokenvalue");
            endUser.setAdditionalInformation(additionalInformation);
            routingContext.put(UriBuilderRequest.CONTEXT_PATH, "/domain");
            routingContext.getDelegate().setUser(new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET, "/logout",
                null,
                HttpStatusCode.OK_200, "OK", null);
    }

    // see https://github.com/gravitee-io/issues/issues/5163
    @Test
    public void shouldInvokeLogoutEndpoint_postRedirectUri_lax_id_token_hint() throws Exception {
        OIDCSettings oidcSettings = mock(OIDCSettings.class);
        when(oidcSettings.getPostLogoutRedirectUris()).thenReturn(Arrays.asList("https://dev"));
        when(domain.getOidc()).thenReturn(oidcSettings);

        testRequest(
                HttpMethod.GET, "/logout?post_logout_redirect_uri=https%3A%2F%2Fdev",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.equals("https://dev"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }
}
