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
package io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.impl;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.SimpleAuthenticationContext;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class UserAuthProviderImplTest {

    @Mock
    private UserAuthenticationManager userAuthenticationManager;
    @Mock
    private ClientSyncService clientSyncService;
    @Mock
    private Domain domain;
    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpServerRequest;

    @InjectMocks
    private UserAuthProviderImpl userAuthProvider;

    private void setupHttpRequestMocks() {
        when(routingContext.request().getDelegate()).thenReturn(httpServerRequest);
        when(routingContext.request().getParam(anyString())).thenReturn("device123");
        when(httpServerRequest.method()).thenReturn(HttpMethod.POST);
    }

    private Client createClient(String clientId, String domainId) {
        Client client = new Client();
        client.setClientId(clientId);
        client.setDomain(domainId);
        return client;
    }

    private JsonObject createAuthInfo(String clientId) {
        return new JsonObject()
                .put("username", "testuser")
                .put("password", "testpass")
                .put("client_id", clientId)
                .put(Claims.IP_ADDRESS, "127.0.0.1")
                .put(Claims.USER_AGENT, "TestAgent");
    }

    @Test
    public void shouldInjectDomainIntoAuthenticationContextAndMakeItAccessibleViaEl() {
        // Given
        String domainId = "test-domain-id";
        String domainName = "Test Domain";
        String domainPath = "/test-domain";
        String clientId = "test-client";
        
        Client client = createClient(clientId, domainId);
        when(domain.getId()).thenReturn(domainId);
        when(domain.getName()).thenReturn(domainName);
        when(domain.getPath()).thenReturn(domainPath);
        when(clientSyncService.findByClientId(clientId)).thenReturn(Maybe.just(client));
        setupHttpRequestMocks();
        when(userAuthenticationManager.authenticate(any(Client.class), any(Authentication.class)))
                .thenReturn(Single.just(new io.gravitee.am.model.User()));

        JsonObject authInfo = createAuthInfo(clientId);
        ArgumentCaptor<Authentication> authenticationCaptor = ArgumentCaptor.forClass(Authentication.class);

        // When
        userAuthProvider.authenticate(routingContext, authInfo, result -> {
            assertTrue("Authentication should succeed", result.succeeded());
        });

        // Then
        verify(userAuthenticationManager, timeout(1000)).authenticate(eq(client), authenticationCaptor.capture());
        
        Authentication captured = authenticationCaptor.getValue();
        var context = captured.getContext();
        
        // Verify domain is set on the authentication context
        assertTrue("Context should be SimpleAuthenticationContext", context instanceof SimpleAuthenticationContext);
        SimpleAuthenticationContext simpleContext = (SimpleAuthenticationContext) context;
        
        // Test that domain is accessible via template engine (this tests the EL evaluation)
        var templateEngine = simpleContext.getTemplateEngine();
        
        // Test the new EL expressions that this feature enables
        String evaluatedDomainName = templateEngine.getValue("{#context.domain.name}", String.class);
        String evaluatedDomainId = templateEngine.getValue("{#context.domain.id}", String.class);
        String evaluatedDomainPath = templateEngine.getValue("{#context.domain.path}", String.class);
        
        assertEquals("Domain name should be accessible via {#context.domain.name}", domainName, evaluatedDomainName);
        assertEquals("Domain ID should be accessible via {#context.domain.id}", domainId, evaluatedDomainId);
        assertEquals("Domain path should be accessible via {#context.domain.path}", domainPath, evaluatedDomainPath);
        
        // Verify other essential context attributes
        var attributes = context.getAttributes();
        assertEquals(domainId, attributes.get(Claims.DOMAIN));
        assertEquals(client, attributes.get(ConstantKeys.CLIENT_CONTEXT_KEY));
    }

}
