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
package io.gravitee.am.gateway.handler.ciba.resources.handler;

import io.gravitee.am.authdevice.notifier.api.AuthenticationDeviceNotifierProvider;
import io.gravitee.am.authdevice.notifier.api.IdentityProviderDependent;
import io.gravitee.am.authdevice.notifier.api.model.ADNotificationResponse;
import io.gravitee.am.authdevice.notifier.api.model.FederatedConnection;
import io.gravitee.am.common.exception.oauth2.OAuth2Exception;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.ciba.CIBAProvider;
import io.gravitee.am.gateway.handler.ciba.service.AuthenticationRequestService;
import io.gravitee.am.gateway.handler.ciba.service.request.CibaAuthenticationRequest;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.manager.authdevice.notifier.AuthenticationDeviceNotifierManager;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.oidc.OpenIDConnectAuthenticationProvider;
import io.gravitee.am.identityprovider.api.oidc.OpenIDConnectIdentityProviderConfiguration;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.oidc.CIBASettingNotifier;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.oidc.model.CibaAuthRequest;
import io.gravitee.am.authdevice.notifier.api.model.ADNotificationRequest;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpMethod;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class AuthenticationRequestAcknowledgeHandlerTest extends RxWebTestBase {
    @Mock
    private Domain domain;

    @Mock
    private JWTService jwtService;

    @Mock
    private AuthenticationDeviceNotifierProvider notifier;

    @Mock
    private AuthenticationRequestService authReqService;

    @Mock
    private IdentityProviderManager identityProviderManager;

    @Mock
    private AuthenticationDeviceNotifierManager deviceNotifierManager;

    private Client client;

    private AuthenticationRequestAcknowledgeHandler handlerUnderTest;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        final OIDCSettings oidcSettings = OIDCSettings.defaultSettings();
        final CIBASettingNotifier notifierSetting = new CIBASettingNotifier();
        notifierSetting.setId("notifierid");
        oidcSettings.getCibaSettings().setDeviceNotifiers(List.of(notifierSetting));
        when(domain.getOidc()).thenReturn(oidcSettings);

        handlerUnderTest = new AuthenticationRequestAcknowledgeHandler(authReqService, domain, jwtService, identityProviderManager, deviceNotifierManager);
        router.route(HttpMethod.POST, "/oidc/ciba/authenticate")
                .handler(handlerUnderTest)
                .failureHandler(rc -> {
                    final Throwable failure = rc.failure();
                    if (failure instanceof OAuth2Exception) {
                        rc.response().setStatusCode(((OAuth2Exception) failure).getHttpStatusCode()).end();
                    } else {
                        rc.response().setStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR_500).end();
                    }
                });
        ;

        this.client = new Client();
        this.client.setAuthorizedGrantTypes(Collections.singletonList(GrantType.CIBA_GRANT_TYPE));
        this.client.setClientId("client_id_iss");
        this.client.setDomain("domain_uuid");
    }

    @Test
    public void shouldGenerateAuthReqId() throws Exception {
        CibaAuthenticationRequest cibaRequest = new CibaAuthenticationRequest();
        cibaRequest.setLoginHint("username");
        cibaRequest.setSubject("usernameuuid");

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.CIBA_AUTH_REQUEST_KEY, cibaRequest);
            routingContext.next();
        });

        when(jwtService.encode(any(JWT.class), any(Client.class))).thenReturn(Single.just("signed_jwt"));
        final CibaAuthRequest req = new CibaAuthRequest();
        req.setCreatedAt(new Date());
        req.setExpireAt(new Date());
        when(authReqService.register(any(), any())).thenReturn(Single.just(req));
        when(authReqService.updateAuthDeviceInformation(any())).thenReturn(Single.just(req));
        when(authReqService.notify(any())).thenReturn(Single.just(new ADNotificationResponse("jit")));

        testRequest(
                HttpMethod.POST,
                CIBAProvider.CIBA_PATH+CIBAProvider.AUTHENTICATION_ENDPOINT+"?request=fakejwt",
                null,
                HttpStatusCode.OK_200, "OK", null);

        verify(authReqService).register(any(), any());
        verify(authReqService).updateAuthDeviceInformation(any());
        verify(authReqService).notify(any());
    }

    @Test
    public void notification_request_carries_login_hint_verbatim() throws Exception {
        CibaAuthenticationRequest cibaRequest = new CibaAuthenticationRequest();
        cibaRequest.setLoginHint("acme|abc");
        cibaRequest.setLoginHintToken("hint-token");
        cibaRequest.setSubject("usernameuuid");

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.CIBA_AUTH_REQUEST_KEY, cibaRequest);
            routingContext.next();
        });

        when(jwtService.encode(any(JWT.class), any(Client.class))).thenReturn(Single.just("signed_jwt"));
        final CibaAuthRequest req = new CibaAuthRequest();
        req.setCreatedAt(new Date());
        req.setExpireAt(new Date());
        when(authReqService.register(any(), any())).thenReturn(Single.just(req));
        when(authReqService.updateAuthDeviceInformation(any())).thenReturn(Single.just(req));
        when(authReqService.notify(any())).thenReturn(Single.just(new ADNotificationResponse("jit")));

        testRequest(
                HttpMethod.POST,
                CIBAProvider.CIBA_PATH+CIBAProvider.AUTHENTICATION_ENDPOINT+"?request=fakejwt",
                null,
                HttpStatusCode.OK_200, "OK", null);

        ArgumentCaptor<ADNotificationRequest> captor = ArgumentCaptor.forClass(ADNotificationRequest.class);
        verify(authReqService).notify(captor.capture());
        assertEquals("acme|abc", captor.getValue().getLoginHint());
        assertEquals("hint-token", captor.getValue().getLoginHintToken());
    }

    @Test
    public void shouldNotGenerateAuthReqId_RegistrationFailure() throws Exception {
        CibaAuthenticationRequest cibaRequest = new CibaAuthenticationRequest();
        cibaRequest.setLoginHint("username");
        cibaRequest.setSubject("usernameuuid");

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.CIBA_AUTH_REQUEST_KEY, cibaRequest);
            routingContext.next();
        });

        when(jwtService.encode(any(JWT.class), any(Client.class))).thenReturn(Single.just("signed_jwt"));
        when(authReqService.register(any(), any())).thenReturn(Single.error(new TechnicalException()));

        testRequest(
                HttpMethod.POST,
                CIBAProvider.CIBA_PATH+CIBAProvider.AUTHENTICATION_ENDPOINT+"?request=fakejwt",
                null,
                HttpStatusCode.INTERNAL_SERVER_ERROR_500, "Internal Server Error", null);

        verify(authReqService).register(any(), any());
        verify(authReqService, never()).updateAuthDeviceInformation(any());
        verify(notifier, never()).notify(any());
    }

    @Test
    public void shouldNotGenerateAuthReqId_NotificationFailure() throws Exception {
        CibaAuthenticationRequest cibaRequest = new CibaAuthenticationRequest();
        cibaRequest.setLoginHint("username");
        cibaRequest.setSubject("usernameuuid");

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.CIBA_AUTH_REQUEST_KEY, cibaRequest);
            routingContext.next();
        });

        when(authReqService.notify(any())).thenReturn(Single.error(new TechnicalException()));

        when(jwtService.encode(any(JWT.class), any(Client.class))).thenReturn(Single.just("signed_jwt"));
        when(authReqService.register(any(), any())).thenReturn(Single.just(new CibaAuthRequest()));

        testRequest(
                HttpMethod.POST,
                CIBAProvider.CIBA_PATH+CIBAProvider.AUTHENTICATION_ENDPOINT+"?request=fakejwt",
                null,
                HttpStatusCode.INTERNAL_SERVER_ERROR_500, "Internal Server Error", null);

        verify(authReqService).register(any(), any());
        verify(authReqService).notify(any());
        verify(authReqService, never()).updateAuthDeviceInformation(any());
    }

    @Test
    public void shouldNotGenerateAuthReqId_MissingCibaRequest() throws Exception {
        CibaAuthenticationRequest cibaRequest = new CibaAuthenticationRequest();
        cibaRequest.setLoginHint("username");
        cibaRequest.setSubject("usernameuuid");

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.next();
        });

        testRequest(
                HttpMethod.POST,
                CIBAProvider.CIBA_PATH+CIBAProvider.AUTHENTICATION_ENDPOINT+"?request=fakejwt",
                null,
                HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);

        verify(authReqService, never()).register(any(), any());
        verify(notifier, never()).notify(any());
    }

    @Test
    public void buildsFederatedConnection_fromOidcIdpConfig() {
        OpenIDConnectIdentityProviderConfiguration cfg = mock(OpenIDConnectIdentityProviderConfiguration.class);
        when(cfg.getClientId()).thenReturn("cid");
        when(cfg.getClientSecret()).thenReturn("sec");
        when(cfg.getScopes()).thenReturn(Set.of("openid", "profile", "email"));
        when(cfg.getWellKnownUri()).thenReturn("https://idp.acme.example/.well-known/openid-configuration");
        when(cfg.getClientAuthenticationMethod()).thenReturn("client_secret_basic");
        OpenIDConnectAuthenticationProvider oidcProvider = mock(OpenIDConnectAuthenticationProvider.class);
        when(oidcProvider.getConfiguration()).thenReturn(cfg);

        FederatedConnection conn = AuthenticationRequestAcknowledgeHandler.toFederatedConnection(oidcProvider);
        assertEquals("cid", conn.clientId());
        assertEquals("sec", conn.clientSecret());
        assertEquals("https://idp.acme.example/.well-known/openid-configuration", conn.wellKnownUri());
        assertEquals(Set.of("openid", "profile", "email"), Set.of(conn.scope().split(" ")));
        assertEquals("client_secret_basic", conn.clientAuthMethod());
    }

    @Test
    public void federatedPath_idpFound_notifyCalledWithNonNullConnection() throws Exception {
        // Configure the notifier — the IdP id is self-described via IdentityProviderDependent
        final OIDCSettings oidcSettings = OIDCSettings.defaultSettings();
        final CIBASettingNotifier notifierSetting = new CIBASettingNotifier();
        notifierSetting.setId("notifierid");
        oidcSettings.getCibaSettings().setDeviceNotifiers(List.of(notifierSetting));
        when(domain.getOidc()).thenReturn(oidcSettings);

        CibaAuthenticationRequest cibaRequest = new CibaAuthenticationRequest();
        cibaRequest.setLoginHint("acme|user1");
        cibaRequest.setSubject("user1uuid");

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.CIBA_AUTH_REQUEST_KEY, cibaRequest);
            routingContext.next();
        });

        // Notifier provider self-describes its IdP via the IdentityProviderDependent interface
        AuthenticationDeviceNotifierProvider notifierProvider = mock(AuthenticationDeviceNotifierProvider.class,
                withSettings().extraInterfaces(IdentityProviderDependent.class));
        when(((IdentityProviderDependent) notifierProvider).getIdentityProviderId()).thenReturn(Optional.of("idp-1"));
        when(deviceNotifierManager.getAuthDeviceNotifierProvider("notifierid")).thenReturn(notifierProvider);

        // notifier links an IdP — model is present (preserves "IdP not found" semantics)
        IdentityProvider idpModel = new IdentityProvider();
        when(identityProviderManager.getIdentityProvider("idp-1")).thenReturn(idpModel);

        // live provider exposes typed config
        OpenIDConnectIdentityProviderConfiguration cfg = mock(OpenIDConnectIdentityProviderConfiguration.class);
        when(cfg.getClientId()).thenReturn("cid");
        when(cfg.getClientSecret()).thenReturn("secret");
        when(cfg.getScopes()).thenReturn(Set.of("openid"));
        when(cfg.getWellKnownUri()).thenReturn("https://op.example.test/.well-known/openid-configuration");
        OpenIDConnectAuthenticationProvider oidcProvider = mock(OpenIDConnectAuthenticationProvider.class);
        when(oidcProvider.getConfiguration()).thenReturn(cfg);
        when(identityProviderManager.get("idp-1")).thenReturn(Maybe.just((AuthenticationProvider) oidcProvider));

        when(jwtService.encode(any(JWT.class), any(Client.class))).thenReturn(Single.just("signed_jwt"));
        final CibaAuthRequest req = new CibaAuthRequest();
        req.setCreatedAt(new Date());
        req.setExpireAt(new Date());
        when(authReqService.register(any(), any())).thenReturn(Single.just(req));
        when(authReqService.updateAuthDeviceInformation(any())).thenReturn(Single.just(req));

        ArgumentCaptor<ADNotificationRequest> captor = ArgumentCaptor.forClass(ADNotificationRequest.class);
        when(authReqService.notify(captor.capture())).thenReturn(Single.just(new ADNotificationResponse("jit-fed")));

        testRequest(
                HttpMethod.POST,
                CIBAProvider.CIBA_PATH + CIBAProvider.AUTHENTICATION_ENDPOINT + "?request=fakejwt",
                null,
                HttpStatusCode.OK_200, "OK", null);

        FederatedConnection conn = captor.getValue().getConnection();
        assertEquals("cid", conn.clientId());
        assertEquals("secret", conn.clientSecret());
        assertEquals("https://op.example.test/.well-known/openid-configuration", conn.wellKnownUri());
        assertEquals("openid", conn.scope());
    }

    @Test
    public void federatedPath_idpNotFound_requestFails() throws Exception {
        // Configure the notifier — the IdP id is self-described via IdentityProviderDependent
        final OIDCSettings oidcSettings = OIDCSettings.defaultSettings();
        final CIBASettingNotifier notifierSetting = new CIBASettingNotifier();
        notifierSetting.setId("notifierid");
        oidcSettings.getCibaSettings().setDeviceNotifiers(List.of(notifierSetting));
        when(domain.getOidc()).thenReturn(oidcSettings);

        CibaAuthenticationRequest cibaRequest = new CibaAuthenticationRequest();
        cibaRequest.setLoginHint("acme|user2");
        cibaRequest.setSubject("user2uuid");

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.CIBA_AUTH_REQUEST_KEY, cibaRequest);
            routingContext.next();
        });

        // Notifier provider self-describes a non-existent IdP id
        AuthenticationDeviceNotifierProvider notifierProvider = mock(AuthenticationDeviceNotifierProvider.class,
                withSettings().extraInterfaces(IdentityProviderDependent.class));
        when(((IdentityProviderDependent) notifierProvider).getIdentityProviderId()).thenReturn(Optional.of("idp-missing"));
        when(deviceNotifierManager.getAuthDeviceNotifierProvider("notifierid")).thenReturn(notifierProvider);

        when(identityProviderManager.getIdentityProvider("idp-missing")).thenReturn(null);
        when(jwtService.encode(any(JWT.class), any(Client.class))).thenReturn(Single.just("signed_jwt"));
        when(authReqService.register(any(), any())).thenReturn(Single.just(new CibaAuthRequest()));

        testRequest(
                HttpMethod.POST,
                CIBAProvider.CIBA_PATH + CIBAProvider.AUTHENTICATION_ENDPOINT + "?request=fakejwt",
                null,
                HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);

        verify(authReqService, never()).notify(any());
        verify(authReqService, never()).updateAuthDeviceInformation(any());
    }

    @Test
    public void idpId_readFromNotifierProvider_setsConnectionAndCallback() throws Exception {
        // Notifier provider self-describes its IdP via the IdentityProviderDependent interface
        CibaAuthenticationRequest cibaRequest = new CibaAuthenticationRequest();
        cibaRequest.setLoginHint("acme|user3");
        cibaRequest.setSubject("user3uuid");

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.CIBA_AUTH_REQUEST_KEY, cibaRequest);
            routingContext.put(CONTEXT_PATH, "");
            routingContext.next();
        });

        // Stub deviceNotifierManager to return a provider whose IdentityProviderDependent
        // interface carries the identityProviderId
        AuthenticationDeviceNotifierProvider notifierProvider = mock(AuthenticationDeviceNotifierProvider.class,
                withSettings().extraInterfaces(IdentityProviderDependent.class));
        when(((IdentityProviderDependent) notifierProvider).getIdentityProviderId()).thenReturn(Optional.of("idp-x"));
        when(deviceNotifierManager.getAuthDeviceNotifierProvider("notifierid")).thenReturn(notifierProvider);

        IdentityProvider idpModel = new IdentityProvider();
        when(identityProviderManager.getIdentityProvider("idp-x")).thenReturn(idpModel);

        OpenIDConnectIdentityProviderConfiguration cfg = mock(OpenIDConnectIdentityProviderConfiguration.class);
        when(cfg.getClientId()).thenReturn("cid-x");
        when(cfg.getClientSecret()).thenReturn("secret-x");
        when(cfg.getScopes()).thenReturn(Set.of("openid"));
        when(cfg.getWellKnownUri()).thenReturn("https://notifier.example.test/.well-known/openid-configuration");
        OpenIDConnectAuthenticationProvider oidcProvider = mock(OpenIDConnectAuthenticationProvider.class);
        when(oidcProvider.getConfiguration()).thenReturn(cfg);
        when(identityProviderManager.get("idp-x")).thenReturn(Maybe.just((AuthenticationProvider) oidcProvider));

        when(jwtService.encode(any(JWT.class), any(Client.class))).thenReturn(Single.just("signed_jwt"));
        final CibaAuthRequest req = new CibaAuthRequest();
        req.setCreatedAt(new Date());
        req.setExpireAt(new Date());
        when(authReqService.register(any(), any())).thenReturn(Single.just(req));
        when(authReqService.updateAuthDeviceInformation(any())).thenReturn(Single.just(req));

        ArgumentCaptor<ADNotificationRequest> captor = ArgumentCaptor.forClass(ADNotificationRequest.class);
        when(authReqService.notify(captor.capture())).thenReturn(Single.just(new ADNotificationResponse("jit-x")));

        testRequest(
                HttpMethod.POST,
                CIBAProvider.CIBA_PATH + CIBAProvider.AUTHENTICATION_ENDPOINT + "?request=fakejwt",
                null,
                HttpStatusCode.OK_200, "OK", null);

        FederatedConnection conn = captor.getValue().getConnection();
        assertNotNull("connection must be set when notifier config carries identityProviderId", conn);
        assertEquals("cid-x", conn.clientId());
        assertEquals("https://notifier.example.test/.well-known/openid-configuration", conn.wellKnownUri());

        String callbackUrl = captor.getValue().getCallbackUrl();
        assertNotNull("callbackUrl must be non-null on the federated notification request", callbackUrl);
        assertTrue("callbackUrl must end with /callback", callbackUrl.endsWith("/callback"));
    }

    @Test
    public void callbackUrl_setToAuthenticateCallbackEndpoint() throws Exception {
        CibaAuthenticationRequest cibaRequest = new CibaAuthenticationRequest();
        cibaRequest.setLoginHint("user4");
        cibaRequest.setSubject("user4uuid");

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.CIBA_AUTH_REQUEST_KEY, cibaRequest);
            routingContext.put(CONTEXT_PATH, "");
            routingContext.next();
        });

        when(jwtService.encode(any(JWT.class), any(Client.class))).thenReturn(Single.just("signed_jwt"));
        final CibaAuthRequest req = new CibaAuthRequest();
        req.setCreatedAt(new Date());
        req.setExpireAt(new Date());
        when(authReqService.register(any(), any())).thenReturn(Single.just(req));
        when(authReqService.updateAuthDeviceInformation(any())).thenReturn(Single.just(req));

        ArgumentCaptor<ADNotificationRequest> captor = ArgumentCaptor.forClass(ADNotificationRequest.class);
        when(authReqService.notify(captor.capture())).thenReturn(Single.just(new ADNotificationResponse("jit-cb")));

        testRequest(
                HttpMethod.POST,
                CIBAProvider.CIBA_PATH + CIBAProvider.AUTHENTICATION_ENDPOINT + "?request=fakejwt",
                null,
                HttpStatusCode.OK_200, "OK", null);

        String callbackUrl = captor.getValue().getCallbackUrl();
        assertNotNull("callbackUrl must be set on the notification request", callbackUrl);
        assertTrue("callbackUrl must be the absolute CIBA callback path",
                callbackUrl.endsWith(CIBAProvider.CIBA_PATH + CIBAProvider.AUTHENTICATION_CALLBACK_ENDPOINT));
        assertTrue("callbackUrl must be absolute against the request host", callbackUrl.startsWith("http://localhost"));
        assertTrue("callbackUrl must not contain query string", !callbackUrl.contains("?"));
        assertTrue("callbackUrl must not contain a null context-path segment", !callbackUrl.contains("null"));
    }

    @Test
    public void callbackUrl_isProxyAware_honoursForwardedHeaders() throws Exception {
        // The callback URL must be built through AM's proxy-aware UriBuilderRequest (like every other
        // self-referential gateway URL), so X-Forwarded-* headers determine the externally-reachable
        // scheme/host — not the internal absoluteURI the gateway happened to receive.
        CibaAuthenticationRequest cibaRequest = new CibaAuthenticationRequest();
        cibaRequest.setLoginHint("user5");
        cibaRequest.setSubject("user5uuid");

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.CIBA_AUTH_REQUEST_KEY, cibaRequest);
            routingContext.put(CONTEXT_PATH, "");
            routingContext.next();
        });

        when(jwtService.encode(any(JWT.class), any(Client.class))).thenReturn(Single.just("signed_jwt"));
        final CibaAuthRequest req = new CibaAuthRequest();
        req.setCreatedAt(new Date());
        req.setExpireAt(new Date());
        when(authReqService.register(any(), any())).thenReturn(Single.just(req));
        when(authReqService.updateAuthDeviceInformation(any())).thenReturn(Single.just(req));

        ArgumentCaptor<ADNotificationRequest> captor = ArgumentCaptor.forClass(ADNotificationRequest.class);
        when(authReqService.notify(captor.capture())).thenReturn(Single.just(new ADNotificationResponse("jit-proxy")));

        testRequest(
                HttpMethod.POST,
                CIBAProvider.CIBA_PATH + CIBAProvider.AUTHENTICATION_ENDPOINT + "?request=fakejwt",
                httpReq -> httpReq.putHeader("X-Forwarded-Proto", "https").putHeader("X-Forwarded-Host", "gw.acme.test"),
                HttpStatusCode.OK_200, "OK", null);

        String callbackUrl = captor.getValue().getCallbackUrl();
        assertEquals("https://gw.acme.test" + CIBAProvider.CIBA_PATH + CIBAProvider.AUTHENTICATION_CALLBACK_ENDPOINT, callbackUrl);
    }

}
