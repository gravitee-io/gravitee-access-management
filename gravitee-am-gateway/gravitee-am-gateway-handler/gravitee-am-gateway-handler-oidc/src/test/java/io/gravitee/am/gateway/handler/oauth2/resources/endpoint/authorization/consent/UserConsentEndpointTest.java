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
package io.gravitee.am.gateway.handler.oauth2.resources.endpoint.authorization.consent;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.oauth2.service.consent.UserConsentService;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.application.ApplicationScopeSettings;
import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class UserConsentEndpointTest extends RxWebTestBase {

    @Mock
    private UserConsentService userConsentService;

    @Mock
    private ThymeleafTemplateEngine templateEngine;

    @Mock
    private Domain domain;

    private Client client;
    private AuthorizationRequest authorizationRequest;
    private RoutingContext capturedContext;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        client = new Client();
        client.setClientId("client-id");

        authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setPrompts(Set.of("consent"));

        when(templateEngine.render(anyMap(), any())).thenReturn(Single.just(Buffer.buffer()));

        router.route().order(-1)
                .handler(rc -> {
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    rc.put(ConstantKeys.AUTHORIZATION_REQUEST_CONTEXT_KEY, authorizationRequest);
                    rc.next();
                });

        router.route(HttpMethod.GET, "/oauth/confirm_access")
                .handler(rc -> {
                    capturedContext = rc;
                    new UserConsentEndpoint(userConsentService, templateEngine, domain).handle(rc);
                });
    }

    @SuppressWarnings("unchecked")
    private List<Scope> requiredScopes() {
        return (List<Scope>) capturedContext.get(ConstantKeys.REQUIRED_SCOPES_CONTEXT_KEY);
    }

    @SuppressWarnings("unchecked")
    private List<Scope> optionalScopes() {
        return (List<Scope>) capturedContext.get(ConstantKeys.OPTIONAL_SCOPES_CONTEXT_KEY);
    }

    @SuppressWarnings("unchecked")
    private List<Scope> orderedScopes() {
        return (List<Scope>) capturedContext.get(ConstantKeys.SCOPES_CONTEXT_KEY);
    }

    private static Scope scope(String key) {
        Scope scope = new Scope(key);
        scope.setDescription(key + "-description");
        return scope;
    }

    private static Set<String> keys(List<Scope> scopes) {
        return scopes.stream().map(Scope::getKey).collect(Collectors.toSet());
    }

    private static ApplicationScopeSettings requiredScopeSetting(String scope) {
        ApplicationScopeSettings settings = new ApplicationScopeSettings(scope);
        settings.setRequiredScope(true);
        return settings;
    }

    @Test
    public void shouldSplitRequiredAndOptionalScopes_andOrderRequiredFirst() throws Exception {
        authorizationRequest.setScopes(Set.of("read", "write", "admin"));
        client.setScopeSettings(List.of(requiredScopeSetting("admin")));
        when(userConsentService.getConsentInformation(authorizationRequest.getScopes()))
                .thenReturn(Single.just(List.of(scope("read"), scope("write"), scope("admin"))));

        testRequest(HttpMethod.GET, "/oauth/confirm_access", HttpStatusCode.OK_200, "OK");

        assertEquals(Set.of("admin"), keys(requiredScopes()));
        assertEquals(Set.of("read", "write"), keys(optionalScopes()));
        assertEquals(List.of("admin", "read", "write"), orderedScopes().stream().map(Scope::getKey).collect(Collectors.toList()));
    }

    @Test
    public void shouldTreatAllScopesAsOptional_whenNoneAreRequired() throws Exception {
        authorizationRequest.setScopes(Set.of("read", "write"));
        client.setScopeSettings(List.of(new ApplicationScopeSettings("read"), new ApplicationScopeSettings("write")));
        when(userConsentService.getConsentInformation(authorizationRequest.getScopes()))
                .thenReturn(Single.just(List.of(scope("read"), scope("write"))));

        testRequest(HttpMethod.GET, "/oauth/confirm_access", HttpStatusCode.OK_200, "OK");

        assertEquals(Set.of(), keys(requiredScopes()));
        assertEquals(Set.of("read", "write"), keys(optionalScopes()));
    }

    @Test
    public void shouldIgnoreRequiredScopeSetting_whenScopeIsNotRequested() throws Exception {
        // "admin" is flagged required on the application, but the client did not request it this time
        authorizationRequest.setScopes(Set.of("read"));
        client.setScopeSettings(List.of(requiredScopeSetting("admin")));
        when(userConsentService.getConsentInformation(authorizationRequest.getScopes()))
                .thenReturn(Single.just(List.of(scope("read"))));

        testRequest(HttpMethod.GET, "/oauth/confirm_access", HttpStatusCode.OK_200, "OK");

        assertEquals(Set.of(), keys(requiredScopes()));
        assertEquals(Set.of("read"), keys(optionalScopes()));
    }

    @Test
    public void shouldHandleAbsentScopeSettings_treatingEverythingAsOptional() throws Exception {
        authorizationRequest.setScopes(Set.of("read"));
        client.setScopeSettings(null);
        when(userConsentService.getConsentInformation(authorizationRequest.getScopes()))
                .thenReturn(Single.just(List.of(scope("read"))));

        testRequest(HttpMethod.GET, "/oauth/confirm_access", HttpStatusCode.OK_200, "OK");

        assertEquals(Set.of(), keys(requiredScopes()));
        assertEquals(Set.of("read"), keys(optionalScopes()));
    }
}
