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

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.ResponseType;
import io.gravitee.am.common.oidc.AcrValues;
import io.gravitee.am.gateway.handler.ciba.CIBAProvider;
import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.AuthorizationRequestParseParametersHandler;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDProviderMetadata;
import io.gravitee.am.gateway.handler.oidc.service.request.RequestObjectService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.Single;
import io.vertx.core.http.HttpMethod;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class AuthenticationRequestParseRequestObjectHandlerTest extends RxWebTestBase {

    @Mock
    private Domain domain;

    @Mock
    private RequestObjectService requestObjectService;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        router.route(HttpMethod.POST, "/oidc/ciba/authenticate")
                .handler(new AuthenticationRequestParseRequestObjectHandler(requestObjectService, domain))
                .handler(rc -> rc.response().end())
                .failureHandler(rc -> rc.response().setStatusCode(400).end());
    }

    @Test
    public void shouldAcceptRequest() throws Exception {
        OpenIDProviderMetadata openIDProviderMetadata = new OpenIDProviderMetadata();
        openIDProviderMetadata.setIssuer("https://op");

        Client client = new Client();
        client.setAuthorizedGrantTypes(Collections.singletonList(GrantType.CIBA_GRANT_TYPE));
        client.setClientId("client_id_iss");

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, openIDProviderMetadata);
            routingContext.next();
        });

        PlainJWT jwt = new PlainJWT(new JWTClaimsSet.Builder()
                .audience("https://op")
                .issuer(client.getClientId())
                .issueTime(new Date())
                .expirationTime(new Date(Instant.now().plusSeconds(60).toEpochMilli()))
                .notBeforeTime(new Date(Instant.now().minusSeconds(1).toEpochMilli()))
                .jwtID(UUID.randomUUID().toString())
                .build());

        when(requestObjectService.readRequestObject(anyString(), any(), anyBoolean())).thenReturn(Single.just(jwt));

        testRequest(
                HttpMethod.POST,
                CIBAProvider.CIBA_PATH+CIBAProvider.AUTHENTICATION_ENDPOINT+"?request=fakejwt",
                null,
                HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void shouldRejectRequest_MissingAud() throws Exception {
        OpenIDProviderMetadata openIDProviderMetadata = new OpenIDProviderMetadata();
        openIDProviderMetadata.setIssuer("https://op");

        Client client = new Client();
        client.setAuthorizedGrantTypes(Collections.singletonList(GrantType.CIBA_GRANT_TYPE));
        client.setClientId("client_id_iss");

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, openIDProviderMetadata);
            routingContext.next();
        });

        PlainJWT jwt = new PlainJWT(new JWTClaimsSet.Builder()
                .issuer(client.getClientId())
                .issueTime(new Date())
                .expirationTime(new Date(Instant.now().plusSeconds(60).toEpochMilli()))
                .notBeforeTime(new Date(Instant.now().minusSeconds(1).toEpochMilli()))
                .jwtID(UUID.randomUUID().toString())
                .build());

        when(requestObjectService.readRequestObject(anyString(), any(), anyBoolean())).thenReturn(Single.just(jwt));

        testRequest(
                HttpMethod.POST,
                CIBAProvider.CIBA_PATH+CIBAProvider.AUTHENTICATION_ENDPOINT+"?request=fakejwt",
                null,
                HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);
    }

    @Test
    public void shouldRejectRequest_InvalidAud() throws Exception {
        OpenIDProviderMetadata openIDProviderMetadata = new OpenIDProviderMetadata();
        openIDProviderMetadata.setIssuer("https://op");

        Client client = new Client();
        client.setAuthorizedGrantTypes(Collections.singletonList(GrantType.CIBA_GRANT_TYPE));
        client.setClientId("client_id_iss");

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, openIDProviderMetadata);
            routingContext.next();
        });

        PlainJWT jwt = new PlainJWT(new JWTClaimsSet.Builder()
                .audience("https://not-op")
                .issuer(client.getClientId())
                .issueTime(new Date())
                .expirationTime(new Date(Instant.now().plusSeconds(60).toEpochMilli()))
                .notBeforeTime(new Date(Instant.now().minusSeconds(1).toEpochMilli()))
                .jwtID(UUID.randomUUID().toString())
                .build());

        when(requestObjectService.readRequestObject(anyString(), any(), anyBoolean())).thenReturn(Single.just(jwt));

        testRequest(
                HttpMethod.POST,
                CIBAProvider.CIBA_PATH+CIBAProvider.AUTHENTICATION_ENDPOINT+"?request=fakejwt",
                null,
                HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);
    }

    @Test
    public void shouldRejectRequest_NoClientId() throws Exception {
        OpenIDProviderMetadata openIDProviderMetadata = new OpenIDProviderMetadata();
        openIDProviderMetadata.setIssuer("https://op");

        Client client = new Client();
        client.setAuthorizedGrantTypes(Collections.singletonList(GrantType.CIBA_GRANT_TYPE));
        client.setClientId("client_id_iss");

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, openIDProviderMetadata);
            routingContext.next();
        });

        PlainJWT jwt = new PlainJWT(new JWTClaimsSet.Builder()
                .audience("https://op")
                //.issuer(client.getClientId())
                .issueTime(new Date())
                .expirationTime(new Date(Instant.now().plusSeconds(60).toEpochMilli()))
                .notBeforeTime(new Date(Instant.now().minusSeconds(1).toEpochMilli()))
                .jwtID(UUID.randomUUID().toString())
                .build());

        when(requestObjectService.readRequestObject(anyString(), any(), anyBoolean())).thenReturn(Single.just(jwt));

        testRequest(
                HttpMethod.POST,
                CIBAProvider.CIBA_PATH+CIBAProvider.AUTHENTICATION_ENDPOINT+"?request=fakejwt",
                null,
                HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);
    }

    @Test
    public void shouldRejectRequest_InvalidClientId() throws Exception {
        OpenIDProviderMetadata openIDProviderMetadata = new OpenIDProviderMetadata();
        openIDProviderMetadata.setIssuer("https://op");

        Client client = new Client();
        client.setAuthorizedGrantTypes(Collections.singletonList(GrantType.CIBA_GRANT_TYPE));
        client.setClientId("client_id_iss");

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, openIDProviderMetadata);
            routingContext.next();
        });

        PlainJWT jwt = new PlainJWT(new JWTClaimsSet.Builder()
                .audience("https://op")
                .issuer("invalid")
                .issueTime(new Date())
                .expirationTime(new Date(Instant.now().plusSeconds(60).toEpochMilli()))
                .notBeforeTime(new Date(Instant.now().minusSeconds(1).toEpochMilli()))
                .jwtID(UUID.randomUUID().toString())
                .build());

        when(requestObjectService.readRequestObject(anyString(), any(), anyBoolean())).thenReturn(Single.just(jwt));

        testRequest(
                HttpMethod.POST,
                CIBAProvider.CIBA_PATH+CIBAProvider.AUTHENTICATION_ENDPOINT+"?request=fakejwt",
                null,
                HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);
    }

    @Test
    public void shouldRejectRequest_InvalidExp() throws Exception {
        OpenIDProviderMetadata openIDProviderMetadata = new OpenIDProviderMetadata();
        openIDProviderMetadata.setIssuer("https://op");

        Client client = new Client();
        client.setAuthorizedGrantTypes(Collections.singletonList(GrantType.CIBA_GRANT_TYPE));
        client.setClientId("client_id_iss");

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, openIDProviderMetadata);
            routingContext.next();
        });

        PlainJWT jwt = new PlainJWT(new JWTClaimsSet.Builder()
                .audience("https://op")
                .issuer(client.getClientId())
                .issueTime(new Date())
                .expirationTime(new Date(Instant.now().minusSeconds(60).toEpochMilli()))
                .notBeforeTime(new Date(Instant.now().minusSeconds(100).toEpochMilli()))
                .jwtID(UUID.randomUUID().toString())
                .build());

        when(requestObjectService.readRequestObject(anyString(), any(), anyBoolean())).thenReturn(Single.just(jwt));

        testRequest(
                HttpMethod.POST,
                CIBAProvider.CIBA_PATH+CIBAProvider.AUTHENTICATION_ENDPOINT+"?request=fakejwt",
                null,
                HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);
    }

    @Test
    public void shouldRejectRequest_InvalidNbf() throws Exception {
        OpenIDProviderMetadata openIDProviderMetadata = new OpenIDProviderMetadata();
        openIDProviderMetadata.setIssuer("https://op");

        Client client = new Client();
        client.setAuthorizedGrantTypes(Collections.singletonList(GrantType.CIBA_GRANT_TYPE));
        client.setClientId("client_id_iss");

        router.route().order(-1).handler(routingContext -> {
            routingContext.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
            routingContext.put(ConstantKeys.PROVIDER_METADATA_CONTEXT_KEY, openIDProviderMetadata);
            routingContext.next();
        });

        PlainJWT jwt = new PlainJWT(new JWTClaimsSet.Builder()
                .audience("https://op")
                .issuer(client.getClientId())
                .issueTime(new Date())
                .expirationTime(new Date(Instant.now().plusSeconds(60).toEpochMilli()))
                .notBeforeTime(new Date(Instant.now().plusSeconds(5).toEpochMilli()))
                .jwtID(UUID.randomUUID().toString())
                .build());

        when(requestObjectService.readRequestObject(anyString(), any(), anyBoolean())).thenReturn(Single.just(jwt));

        testRequest(
                HttpMethod.POST,
                CIBAProvider.CIBA_PATH+CIBAProvider.AUTHENTICATION_ENDPOINT+"?request=fakejwt",
                null,
                HttpStatusCode.BAD_REQUEST_400, "Bad Request", null);
    }
}
