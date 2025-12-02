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
package io.gravitee.am.gateway.handler.common.oauth2.impl;

import io.gravitee.am.common.exception.oauth2.InvalidTokenException;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceManager;
import io.gravitee.am.model.ProtectedResource;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.Environment;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static io.gravitee.am.gateway.handler.common.jwt.JWTService.TokenType.ACCESS_TOKEN;
import static io.gravitee.am.gateway.handler.common.oauth2.impl.BaseIntrospectionTokenService.LEGACY_RFC8707_ENABLED;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentMatchers;

@RunWith(MockitoJUnitRunner.class)
public class BaseIntrospectionTokenServiceTest {

    private static final String TOKEN = "token";
    private static final String DOMAIN = "domain";

    @Mock
    private JWTService jwtService;
    @Mock
    private ClientSyncService clientService;
    @Mock
    private ProtectedResourceManager protectedResourceManager;
    @Mock
    private Environment environment;

    private TestIntrospectionTokenService introspectionTokenService;

    @Before
    public void setup() {
        initService(true);
    }

    @Test
    public void shouldValidateSingleAudienceClient() {
        JWT jwt = buildJwtWithAudiences(List.of("client-id"));
        Client client = buildClient("client-id");
        client.setCertificate("certificate-id");

        mockDecode(jwt);
        when(clientService.findByDomainAndClientId(DOMAIN, "client-id")).thenReturn(Maybe.just(client));
        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(ACCESS_TOKEN))).thenReturn(Single.just(jwt));

        TestObserver<JWT> observer = introspectionTokenService.introspect(TOKEN, true, null).test();

        observer.assertResult(jwt);
        verify(protectedResourceManager, never()).get(anyString());
    }

    @Test
    public void shouldValidateSingleAudienceClientWhenCertificateIsNull() {
        JWT jwt = buildJwtWithAudiences(List.of("client-id"));
        Client client = buildClient("client-id");
        // certificate is intentionally left null to simulate HMAC-signed JWT

        mockDecode(jwt);
        when(clientService.findByDomainAndClientId(DOMAIN, "client-id")).thenReturn(Maybe.just(client));
        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(ACCESS_TOKEN))).thenReturn(Single.just(jwt));

        TestObserver<JWT> observer = introspectionTokenService.introspect(TOKEN, true, null).test();

        observer.assertResult(jwt);
        verify(protectedResourceManager, never()).getByIdentifier(anyString());
    }

    @Test
    public void shouldValidateSingleAudienceProtectedResource() {
        JWT jwt = buildJwtWithAudiences(List.of("resource-id"));
        Client backendClient = buildClient("backend-client");
        ProtectedResource resource = buildProtectedResource("resource-id", DOMAIN, backendClient.getClientId());

        mockDecode(jwt);
        when(clientService.findByDomainAndClientId(DOMAIN, "resource-id")).thenReturn(Maybe.empty());
        when(protectedResourceManager.getByIdentifier("resource-id")).thenReturn(Set.of(resource));
        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(ACCESS_TOKEN))).thenReturn(Single.just(jwt));

        TestObserver<JWT> observer = introspectionTokenService.introspect(TOKEN, true, null).test();

        observer.assertResult(jwt);
    }

    @Test
    public void shouldValidateMultipleProtectedResources() {
        JWT jwt = buildJwtWithAudiences(Arrays.asList("resource-one", "resource-two"));
        Client backendClient = buildClient("backend-client");
        ProtectedResource resourceOne = buildProtectedResource("resource-one", DOMAIN, backendClient.getClientId());
        ProtectedResource resourceTwo = buildProtectedResource("resource-two", DOMAIN, backendClient.getClientId());

        mockDecode(jwt);
        when(protectedResourceManager.getByIdentifier("resource-one")).thenReturn(Set.of(resourceOne));
        when(protectedResourceManager.getByIdentifier("resource-two")).thenReturn(Set.of(resourceTwo));
        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(ACCESS_TOKEN))).thenReturn(Single.just(jwt));

        TestObserver<JWT> observer = introspectionTokenService.introspect(TOKEN, true, null).test();

        observer.assertResult(jwt);
        verify(clientService, never()).findByDomainAndClientId(DOMAIN, "resource-one");
    }

    @Test
    public void shouldFailWhenNoAudienceClaim() {
        JWT jwt = buildJwtWithoutAudience();

        mockDecode(jwt);

        TestObserver<JWT> observer = introspectionTokenService.introspect(TOKEN, true, null).test();

        observer.assertError(throwable -> throwable instanceof InvalidTokenException e
                && "The token is invalid".equals(e.getMessage())
                && "Token has no audience claim".equals(e.getDetails()));
    }

    @Test
    public void shouldFailWhenAudienceDoesNotMatchClientOrResource() {
        JWT jwt = buildJwtWithAudiences(List.of("unknown-resource"));

        mockDecode(jwt);
        when(clientService.findByDomainAndClientId(DOMAIN, "unknown-resource")).thenReturn(Maybe.empty());
        when(protectedResourceManager.getByIdentifier("unknown-resource")).thenReturn(Set.of());

        TestObserver<JWT> observer = introspectionTokenService.introspect(TOKEN, true, null).test();

        observer.assertError(throwable -> throwable instanceof InvalidTokenException e
                && e.getMessage().equals("The token is invalid")
                && e.getDetails().equals("Token audience values [unknown-resource] do not match any client or protected resource identifiers in domain [" + DOMAIN + "]"));
        verify(clientService, never()).findByDomainAndClientId(DOMAIN, "backend-client");
    }

    @Test
    public void shouldFailWhenLegacyFlagRequiresMatchingClient() {
        JWT jwt = buildJwtWithAudiences(List.of("resource-id"));
        ProtectedResource resource = buildProtectedResource("resource-id", DOMAIN, "backend-client");

        mockDecode(jwt);
        when(clientService.findByDomainAndClientId(DOMAIN, "resource-id")).thenReturn(Maybe.empty());
        when(protectedResourceManager.getByIdentifier("resource-id")).thenReturn(Set.of(resource));

        TestObserver<JWT> observer = introspectionTokenService.introspect(TOKEN, true, "caller-client").test();

        observer.assertError(throwable -> throwable instanceof InvalidTokenException e
                && e.getMessage().equals("The token is invalid")
                && e.getDetails().equals("Protected resources matched by token audience have client IDs [backend-client] that do not match the introspecting client ID [caller-client]"));
        verify(clientService, never()).findByDomainAndClientId(DOMAIN, "backend-client");
    }

    @Test
    public void shouldBypassLegacyValidationWhenFlagDisabled() {
        initService(false);

        JWT jwt = buildJwtWithAudiences(List.of("resource-id"));
        Client backendClient = buildClient("backend-client");
        ProtectedResource resource = buildProtectedResource("resource-id", DOMAIN, backendClient.getClientId());

        mockDecode(jwt);
        when(clientService.findByDomainAndClientId(DOMAIN, "resource-id")).thenReturn(Maybe.empty());
        when(protectedResourceManager.getByIdentifier("resource-id")).thenReturn(Set.of(resource));
        when(jwtService.decodeAndVerify(eq(TOKEN), ArgumentMatchers.<Supplier<String>>any(), eq(ACCESS_TOKEN))).thenReturn(Single.just(jwt));

        TestObserver<JWT> observer = introspectionTokenService.introspect(TOKEN, true, "caller-client").test();

        observer.assertResult(jwt);
    }

    private void initService(boolean legacyFlagEnabled) {
        when(environment.getProperty(LEGACY_RFC8707_ENABLED, Boolean.class, true)).thenReturn(legacyFlagEnabled);
        introspectionTokenService = new TestIntrospectionTokenService(jwtService, clientService, protectedResourceManager, environment);
    }

    private void mockDecode(JWT jwt) {
        when(jwtService.decode(TOKEN, ACCESS_TOKEN)).thenReturn(Single.just(jwt));
    }

    private JWT buildJwtWithAudiences(List<String> audiences) {
        JWT jwt = buildBaseJwt();
        if (audiences.size() == 1) {
            jwt.setAud(audiences.getFirst());
        } else {
            jwt.setAudList(audiences);
        }
        return jwt;
    }

    private JWT buildJwtWithoutAudience() {
        return buildBaseJwt();
    }

    private JWT buildBaseJwt() {
        JWT jwt = new JWT();
        jwt.setDomain(DOMAIN);
        jwt.setJti("jti");
        jwt.setIat(Instant.now().getEpochSecond());
        return jwt;
    }

    private Client buildClient(String clientId) {
        Client client = new Client();
        client.setClientId(clientId);
        return client;
    }

    private ProtectedResource buildProtectedResource(String id, String domainId, String clientId) {
        ProtectedResource resource = new ProtectedResource();
        resource.setId(id);
        resource.setDomainId(domainId);
        resource.setClientId(clientId);
        return resource;
    }

    private static class TestIntrospectionTokenService extends BaseIntrospectionTokenService {

        TestIntrospectionTokenService(JWTService jwtService,
                                      ClientSyncService clientService,
                                      ProtectedResourceManager protectedResourceManager,
                                      Environment environment) {
            super(ACCESS_TOKEN, jwtService, clientService, protectedResourceManager, environment);
        }

        @Override
        protected Maybe<? extends io.gravitee.am.repository.oauth2.model.Token> findByToken(String token) {
            return Maybe.empty();
        }

        Maybe<JWT> introspect(String token, boolean offlineVerification, String callerClientId) {
            return super.introspectToken(token, offlineVerification, callerClientId);
        }
    }
}

