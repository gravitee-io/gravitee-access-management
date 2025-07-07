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
package io.gravitee.am.gateway.handler.oauth2.resources.auth.handler;

import io.gravitee.am.common.oidc.ClientAuthenticationMethod;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.ExceptionHandler;
import io.gravitee.am.gateway.handler.oauth2.service.assertion.ClientAssertionService;
import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.impl.ApplicationClientSecretService;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Maybe;
import io.vertx.core.http.HttpMethod;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ClientAuthHandlerTest extends RxWebTestBase {

    @Mock
    private ClientSyncService clientSyncService;

    @Mock
    private ClientAssertionService clientAssertionService;

    @Mock
    private JWKService jwkService;

    @Mock
    private Domain domain;

    @Mock
    private ApplicationClientSecretService applicationClientSecretService;

    @Mock
    private AuditService auditService;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        when(domain.getId()).thenReturn("id");
        router.post("/oauth/token")
                .handler(ClientAuthHandler.create(clientSyncService, clientAssertionService, jwkService, domain, applicationClientSecretService, null, auditService))
                .handler(rc -> rc.response().setStatusCode(200).end())
                .failureHandler(new ExceptionHandler());
    }

    @After
    public void after() throws Exception {
        verify(auditService, times(1)).report(any());
        super.after();
    }

    @Test
    public void shouldNotInvoke_noClientCredentials() throws Exception {
        testRequest(
                HttpMethod.POST,
                "/oauth/token",
                HttpStatusCode.UNAUTHORIZED_401, "Unauthorized");
    }

    @Test
    public void shouldNotInvoke_badClientAuthenticationMethod() throws Exception {
        testRequest(
                HttpMethod.POST,
                "/oauth/token",
                req -> req.putHeader("Authorization", "Custom test"),
                HttpStatusCode.UNAUTHORIZED_401, "Unauthorized", null);
    }

    @Test
    public void shouldNotInvoke_clientCredentials_post_basicTokenAuthMethod() throws Exception {
        final String clientId = "client-id";
        Client client = mock(Client.class);
        when(client.getTokenEndpointAuthMethod()).thenReturn(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        when(clientSyncService.findByClientId(clientId)).thenReturn(Maybe.just(client));

        testRequest(
                HttpMethod.POST,
                "/oauth/token?client_id=client-id&client_secret=client-secret",
                HttpStatusCode.UNAUTHORIZED_401, "Unauthorized");

    }

    @Test
    public void shouldInvoke_clientCredentials_post_postTokenAuthMethod() throws Exception {
        final String clientId = "client-id";
        final String clientSecret = "client-secret";
        Client client = mock(Client.class);
        when(client.getClientId()).thenReturn(clientId);
        when(client.getClientSecret()).thenReturn(clientSecret);
        when(client.getTokenEndpointAuthMethod()).thenReturn(ClientAuthenticationMethod.CLIENT_SECRET_POST);
        when(clientSyncService.findByClientId(clientId)).thenReturn(Maybe.just(client));

        testRequest(
                HttpMethod.POST,
                "/oauth/token?client_id=client-id&client_secret=client-secret",
                HttpStatusCode.OK_200, "OK");
    }

    @Test
    public void shouldInvoke_clientCredentials_basic_basicTokenAuthMethod() throws Exception {
        final String clientId = "client-id";
        final String clientSecret = "client-secret";
        Client client = mock(Client.class);
        when(client.getClientId()).thenReturn(clientId);
        when(client.getClientSecret()).thenReturn(clientSecret);
        when(client.getTokenEndpointAuthMethod()).thenReturn(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        when(clientSyncService.findByClientId(clientId)).thenReturn(Maybe.just(client));

        testRequest(
                HttpMethod.POST,
                "/oauth/token",
                req -> req.putHeader("Authorization", "Basic Y2xpZW50LWlkOmNsaWVudC1zZWNyZXQ="),
                HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void shouldNotInvoke_invalidClientCredentials_basic_basicTokenAuthMethod() throws Exception {
        final String clientId = "client-id";
        final String clientSecret = "client-secret";
        Client client = mock(Client.class);
        when(client.getClientId()).thenReturn(clientId);
        when(client.getClientSecret()).thenReturn(clientSecret);
        when(client.getTokenEndpointAuthMethod()).thenReturn(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        when(clientSyncService.findByClientId(clientId)).thenReturn(Maybe.just(client));

        testRequest(
                HttpMethod.POST,
                "/oauth/token",
                req -> req.putHeader("Authorization", "Basic Y2xpZW50LWlkOnRlc3Q="),
                resp -> {
                    String wwwAuth = resp.headers().get("WWW-Authenticate");
                    assertNotNull(wwwAuth);
                },
                401, "Unauthorized", null);
    }

    @Test
    public void shouldInvoke_clientCredentials_privateJWT_privateJWTTokenAuthMethod() throws Exception {
        Client client = mock(Client.class);
        when(clientAssertionService.assertClient(eq("urn:ietf:params:oauth:client-assertion-type:jwt-bearer"), eq("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJjbGllbnRfaWQifQ.fcf-gV3uZ6P-ecrAc-g9YDcQQYRwKPbqIq_HFSOOrQw"), anyString())).thenReturn(Maybe.just(client));
        when(clientSyncService.findByClientId("client_id")).thenReturn(Maybe.just(client));
        testRequest(
                HttpMethod.POST,
                "/oauth/token?client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer&client_assertion=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJjbGllbnRfaWQifQ.fcf-gV3uZ6P-ecrAc-g9YDcQQYRwKPbqIq_HFSOOrQw",
                HttpStatusCode.OK_200, "OK");
    }


    @Test
    public void shouldNotInvoke_clientCredentials_privateJWT_privateJWTTokenAuthMethod_MissingSSLCert() throws Exception {
        Client client = mock(Client.class);
        when(client.isTlsClientCertificateBoundAccessTokens()).thenReturn(true);
        when(clientSyncService.findByClientId("client_id")).thenReturn(Maybe.just(client));

        when(clientAssertionService.assertClient(eq("urn:ietf:params:oauth:client-assertion-type:jwt-bearer"), eq("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJjbGllbnRfaWQifQ.fcf-gV3uZ6P-ecrAc-g9YDcQQYRwKPbqIq_HFSOOrQw"), anyString())).thenReturn(Maybe.just(client));

        testRequest(
                HttpMethod.POST,
                "/oauth/token?client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer&client_assertion=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJjbGllbnRfaWQifQ.fcf-gV3uZ6P-ecrAc-g9YDcQQYRwKPbqIq_HFSOOrQw",
                HttpStatusCode.UNAUTHORIZED_401, "Unauthorized");
    }

    @Test
    public void shouldInvoke_clientCredentials_clientSecret_clientSecretJWTTokenAuthMethod() throws Exception {
        Client client = mock(Client.class);
        when(clientAssertionService.assertClient(eq("urn:ietf:params:oauth:client-assertion-type:jwt-bearer"), eq("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJjbGllbnRfaWQifQ.fcf-gV3uZ6P-ecrAc-g9YDcQQYRwKPbqIq_HFSOOrQw"), anyString())).thenReturn(Maybe.just(client));
        when(clientSyncService.findByClientId("client_id")).thenReturn(Maybe.just(client));
        testRequest(
                HttpMethod.POST,
                "/oauth/token?client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer&client_assertion=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJjbGllbnRfaWQifQ.fcf-gV3uZ6P-ecrAc-g9YDcQQYRwKPbqIq_HFSOOrQw",
                HttpStatusCode.OK_200, "OK");
    }

    @Test
    public void shouldNotInvoke_clientCredentials_publicClient() throws Exception {
        final String clientId = "public-client-id";
        Client client = mock(Client.class);
        when(client.getTokenEndpointAuthMethod()).thenReturn(ClientAuthenticationMethod.NONE);
        when(clientSyncService.findByClientId(clientId)).thenReturn(Maybe.just(client));

        testRequest(
                HttpMethod.POST,
                "/oauth/token?grant_type=client_credentials&client_id="+clientId,
                HttpStatusCode.UNAUTHORIZED_401, "Unauthorized");
    }

    @Test
    public void shouldInvoke_noClientCredentials_publicClient() throws Exception {
        final String clientId = "public-client-id";
        Client client = mock(Client.class);
        when(client.getTokenEndpointAuthMethod()).thenReturn(ClientAuthenticationMethod.NONE);
        when(clientSyncService.findByClientId(clientId)).thenReturn(Maybe.just(client));
        testRequest(
                HttpMethod.POST,
                "/oauth/token?client_id=public-client-id",
                HttpStatusCode.OK_200, "OK");

    }
}
