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
import io.gravitee.am.authdevice.notifier.api.model.ADNotificationResponse;
import io.gravitee.am.common.exception.oauth2.OAuth2Exception;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.ciba.CIBAProvider;
import io.gravitee.am.gateway.handler.ciba.service.AuthenticationRequestService;
import io.gravitee.am.gateway.handler.ciba.service.request.CibaAuthenticationRequest;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.CIBASettingNotifier;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.oidc.model.CibaAuthRequest;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.Single;
import io.vertx.core.http.HttpMethod;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.Date;
import java.util.List;

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

        handlerUnderTest = new AuthenticationRequestAcknowledgeHandler(authReqService, domain, jwtService);
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

}
