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
package io.gravitee.am.gateway.handler.oauth2.service.request;

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceManager;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidScopeException;
import io.gravitee.am.gateway.handler.oauth2.service.scope.ScopeManager;
import io.gravitee.am.identityprovider.api.SimpleAuthenticationContext;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.User;
import io.gravitee.am.model.application.ApplicationScopeSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import io.gravitee.gateway.api.ExecutionContext;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.*;

import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class AuthorizationRequestResolverTest {

    private final AuthorizationRequestResolver authorizationRequestResolver = new AuthorizationRequestResolver(Mockito.mock(), Mockito.mock());

    @Mock
    private ScopeManager scopeManager;

    @Mock
    private ProtectedResourceManager protectedResourceManager;

    @Before
    public void init() {
        reset(scopeManager);
        authorizationRequestResolver.setManagers(scopeManager, protectedResourceManager);
    }

    @Test
    public void shouldNotResolveAuthorizationRequest_unknownScope() {
        final String scope = "read";
        final String redirectUri = "http://localhost:8080/callback";
        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setScopes(Collections.singleton(scope));
        authorizationRequest.setRedirectUri(redirectUri);
        Client client = new Client();

        TestObserver<AuthorizationRequest> testObserver = authorizationRequestResolver.resolve(authorizationRequest, client, null).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidScopeException.class);
    }

    @Test
    public void shouldResolveAuthorizationRequest_emptyScope() {
        final String redirectUri = "http://localhost:8080/callback";
        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setRedirectUri(redirectUri);
        Client client = new Client();

        TestObserver<AuthorizationRequest> testObserver = authorizationRequestResolver.resolve(authorizationRequest, client, null).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }

    @Test
    public void shouldResolveAuthorizationRequest_openIdScope() {
        final String redirectUri = "http://localhost:8080/callback";
        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setScopes(Set.of("openid"));
        authorizationRequest.setRedirectUri(redirectUri);
        Client client = new Client();
        ApplicationScopeSettings setting = new ApplicationScopeSettings();
        setting.setScope("openid");
        setting.setDefaultScope(true);
        client.setScopeSettings(List.of(setting));

        TestObserver<AuthorizationRequest> testObserver = authorizationRequestResolver.resolve(authorizationRequest, client, null).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }

    @Test
    public void shouldResolveAuthorizationRequest_noRequestedScope() {
        final String scope = "read";
        final String redirectUri = "http://localhost:8080/callback";
        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setRedirectUri(redirectUri);
        Client client = new Client();
        ApplicationScopeSettings setting = new ApplicationScopeSettings();
        setting.setScope(scope);
        setting.setDefaultScope(true);
        client.setScopeSettings(Collections.singletonList(setting));

        TestObserver<AuthorizationRequest> testObserver = authorizationRequestResolver.resolve(authorizationRequest, client, null).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(request -> request.getScopes().iterator().next().equals(scope));
    }

    @Test
    public void shouldResolveAuthorizationRequest_invalidScope() {
        final String scope = "read";
        final String redirectUri = "http://localhost:8080/callback";
        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setScopes(Collections.singleton(scope));
        authorizationRequest.setRedirectUri(redirectUri);
        Client client = new Client();
        ApplicationScopeSettings setting = new ApplicationScopeSettings();
        setting.setScope("write");
        client.setScopeSettings(Collections.singletonList(setting));

        TestObserver<AuthorizationRequest> testObserver = authorizationRequestResolver.resolve(authorizationRequest, client, null).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidScopeException.class);
    }


    @Test
    public void shouldResolveAuthorizationRequest_ParameterizedScope() {
        final String scope = "read";
        final String redirectUri = "http://localhost:8080/callback";
        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        final String parameterizedScope = scope + ":36fc67776";
        authorizationRequest.setScopes(Collections.singleton(parameterizedScope));
        authorizationRequest.setRedirectUri(redirectUri);
        Client client = new Client();
        ApplicationScopeSettings setting = new ApplicationScopeSettings(scope);
        client.setScopeSettings(Collections.singletonList(setting));
        when(scopeManager.isParameterizedScope(scope)).thenReturn(true);
        TestObserver<AuthorizationRequest> testObserver = authorizationRequestResolver.resolve(authorizationRequest, client, null).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(request -> request.getScopes().iterator().next().equals(parameterizedScope));
    }

    @Test
    public void shouldResolveAuthorizationRequest_invalidScope_withUser() {
        final String scope = "read";
        final String redirectUri = "http://localhost:8080/callback";
        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setScopes(Collections.singleton(scope));
        authorizationRequest.setRedirectUri(redirectUri);
        Client client = new Client();
        ApplicationScopeSettings setting = new ApplicationScopeSettings();
        setting.setScope("write");
        client.setScopeSettings(Collections.singletonList(setting));

        User user = new User();
        Role role = new Role();
        role.setOauthScopes(Collections.singletonList("user"));
        user.setRolesPermissions(Collections.singleton(role));

        TestObserver<AuthorizationRequest> testObserver = authorizationRequestResolver.resolve(authorizationRequest, client, user).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidScopeException.class);
    }

    @Test
    public void shouldResolveAuthorizationRequest_userPermissionsRequestedAll() {
        final String scope = "read";
        final List<String> userScopes = Arrays.asList("user1", "user2", "user3");
        final String redirectUri = "http://localhost:8080/callback";

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setRedirectUri(redirectUri);
        List<String> authScopes = new ArrayList<>();
        authScopes.add(scope);
        authScopes.addAll(userScopes);
        authorizationRequest.setScopes(new HashSet<>(authScopes));

        Client client = new Client();
        ApplicationScopeSettings setting = new ApplicationScopeSettings();
        setting.setScope(scope);
        client.setScopeSettings(Collections.singletonList(setting));
        client.setEnhanceScopesWithUserPermissions(true);

        User user = new User();
        Role role = new Role();
        role.setOauthScopes(userScopes);
        user.setRolesPermissions(Collections.singleton(role));

        TestObserver<AuthorizationRequest> testObserver = authorizationRequestResolver.resolve(authorizationRequest, client, user).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        // Request should have been enhanced with all of user's permissions, even though none of them has been requested
        List<String> expectedScopes = new ArrayList<>();
        expectedScopes.add(scope);
        expectedScopes.addAll(userScopes);
        testObserver.assertValue(request -> request.getScopes().containsAll(expectedScopes) && request.getScopes().contains(scope) && request.getScopes().size() == 4);
    }

    @Test
    public void shouldResolveAuthorizationRequest_userPermissionsRequestedAny() {
    	final String scope = "read";
        final List<String> userScopes = Arrays.asList("user1", "user2", "user3");
        final String redirectUri = "http://localhost:8080/callback";

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setRedirectUri(redirectUri);
        List<String> authScopes = new ArrayList<>();
        authScopes.add(scope);
        authScopes.add(userScopes.get(1)); // Request only the second of the three user scopes
        authorizationRequest.setScopes(new HashSet<>(authScopes));

        Client client = new Client();
        ApplicationScopeSettings setting = new ApplicationScopeSettings();
        setting.setScope(scope);
        client.setScopeSettings(Collections.singletonList(setting));
        client.setEnhanceScopesWithUserPermissions(true);

        User user = new User();
        Role role = new Role();
        role.setOauthScopes(userScopes);
        user.setRolesPermissions(Collections.singleton(role));

        TestObserver<AuthorizationRequest> testObserver = authorizationRequestResolver.resolve(authorizationRequest, client, user).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        // Request should have been enhanced with all of user's permissions, even though only one has been requested
        List<String> expectedScopes = new ArrayList<>(authScopes);
        testObserver.assertValue(request -> request.getScopes().containsAll(expectedScopes) && request.getScopes().contains(scope) && request.getScopes().size() == 2);
    }

    @Test
    public void shouldResolveAuthorizationRequest_userPermissionsRequestedAny_LegacyMode() {
        final String scope = "read";
        final List<String> userScopes = Arrays.asList("user1", "user2", "user3");
        final String redirectUri = "http://localhost:8080/callback";

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setRedirectUri(redirectUri);
        List<String> authScopes = new ArrayList<>();
        authScopes.add(scope);
        authScopes.add(userScopes.get(1)); // Request only the second of the three user scopes
        authorizationRequest.setScopes(new HashSet<>(authScopes));

        Client client = new Client();
        ApplicationScopeSettings setting = new ApplicationScopeSettings();
        setting.setScope(scope);
        client.setScopeSettings(Collections.singletonList(setting));
        client.setEnhanceScopesWithUserPermissions(true);

        User user = new User();
        Role role = new Role();
        role.setOauthScopes(userScopes);
        user.setRolesPermissions(Collections.singleton(role));

        when(scopeManager.alwaysProvideEnhancedScopes()).thenReturn(true);
        TestObserver<AuthorizationRequest> testObserver = authorizationRequestResolver.resolve(authorizationRequest, client, user).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        // Request should have been enhanced with all of user's permissions, even though only one has been requested
        List<String> expectedScopes = new ArrayList<>(authScopes);
        testObserver.assertValue(request -> request.getScopes().containsAll(expectedScopes) && request.getScopes().contains(scope) && request.getScopes().size() == 4);
    }

    @Test
    public void shouldResolveAuthorizationRequest_userPermissionsRequestedNone() {
    	final String scope = "read";
        final List<String> userScopes = Arrays.asList("user1", "user2", "user3");
        final String redirectUri = "http://localhost:8080/callback";

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setRedirectUri(redirectUri);
        // Request none of the three user scopes
        authorizationRequest.setScopes(new HashSet<>(Arrays.asList(scope)));

        Client client = new Client();
        ApplicationScopeSettings setting = new ApplicationScopeSettings();
        setting.setScope(scope);
        client.setScopeSettings(Collections.singletonList(setting));
        client.setEnhanceScopesWithUserPermissions(true);

        User user = new User();
        Role role = new Role();
        role.setOauthScopes(userScopes);
        user.setRolesPermissions(Collections.singleton(role));

        TestObserver<AuthorizationRequest> testObserver = authorizationRequestResolver.resolve(authorizationRequest, client, user).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        List<String> expectedScopes = new ArrayList<>();
        expectedScopes.add(scope);

        // Request should have been enhanced with all of user's permissions, even though none of them has been requested
        testObserver.assertValue(request -> request.getScopes().containsAll(expectedScopes) && request.getScopes().contains(scope) && request.getScopes().size() == 1);
    }

    @Test
    public void shouldResolveAuthorizationRequest_userPermissionsRequestedNone_legacyMode() {
    	final String scope = "read";
        final List<String> userScopes = Arrays.asList("user1", "user2", "user3");
        final String redirectUri = "http://localhost:8080/callback";

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setRedirectUri(redirectUri);
        // Request none of the three user scopes
        authorizationRequest.setScopes(new HashSet<>(Arrays.asList(scope)));

        Client client = new Client();
        ApplicationScopeSettings setting = new ApplicationScopeSettings();
        setting.setScope(scope);
        client.setScopeSettings(Collections.singletonList(setting));
        client.setEnhanceScopesWithUserPermissions(true);

        User user = new User();
        Role role = new Role();
        role.setOauthScopes(userScopes);
        user.setRolesPermissions(Collections.singleton(role));

        when(scopeManager.alwaysProvideEnhancedScopes()).thenReturn(true);
        TestObserver<AuthorizationRequest> testObserver = authorizationRequestResolver.resolve(authorizationRequest, client, user).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        List<String> expectedScopes = new ArrayList<>();
        expectedScopes.add(scope);

        // Request should have been enhanced with all of user's permissions, even though none of them has been requested
        testObserver.assertValue(request -> request.getScopes().containsAll(expectedScopes) && request.getScopes().contains(scope) && request.getScopes().size() == 4);
    }

    @Test
    public void shouldEnrichRedirectUriWithEl() {
        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setRedirectUri("https://test.com/callback");
        Client client = new Client();
        client.setRedirectUris(List.of("https://test.com/callback?param={#context.attributes['test']}"));

        ExecutionContext executionContext = new SimpleAuthenticationContext(null, Map.of("test", "value"));

        TestObserver<AuthorizationRequest> testObserver = authorizationRequestResolver.evaluateELQueryParams(authorizationRequest, client, executionContext).test();
        testObserver.assertValue(value -> value.getRedirectUri().equals("https://test.com/callback?param=value"));
    }

    @Test
    public void shouldEnrichRedirectUriWithEl_2() {
        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setRedirectUri("https://test2.com/callback");
        Client client = new Client();
        client.setRedirectUris(List.of("https://test.com/callback?param={#context.attributes['test']}", "https://test2.com/callback?param2={#context.attributes['test2']}"));

        ExecutionContext executionContext = new SimpleAuthenticationContext(null, Map.of("test", "value", "test2", "value2"));

        TestObserver<AuthorizationRequest> testObserver = authorizationRequestResolver.evaluateELQueryParams(authorizationRequest, client, executionContext).test();
        testObserver.assertValue(value -> value.getRedirectUri().equals("https://test2.com/callback?param2=value2"));
    }

    @Test
    public void shouldResolveAuthorizationRequest_withProtectedResourceScopes_requestedAll() {
        final String scope = "read";
        final List<String> protectedResourceScopes = Arrays.asList("resource1", "resource2", "resource3");
        final String redirectUri = "http://localhost:8080/callback";

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setRedirectUri(redirectUri);
        
        // Set resource in parameters (AuthorizationRequest.getResources() reads from parameters)
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.RESOURCE, "https://api.example.com");
        authorizationRequest.setParameters(parameters);
        
        List<String> authScopes = new ArrayList<>();
        authScopes.add(scope);
        authScopes.addAll(protectedResourceScopes);
        authorizationRequest.setScopes(new HashSet<>(authScopes));

        Client client = new Client();
        ApplicationScopeSettings setting = new ApplicationScopeSettings();
        setting.setScope(scope);
        client.setScopeSettings(Collections.singletonList(setting));

        when(protectedResourceManager.getScopesForResources(Set.of("https://api.example.com")))
                .thenReturn(new HashSet<>(protectedResourceScopes));

        TestObserver<AuthorizationRequest> testObserver = authorizationRequestResolver.resolve(authorizationRequest, client, null).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        // Should have client scope + all requested protected resource scopes
        List<String> expectedScopes = new ArrayList<>();
        expectedScopes.add(scope);
        expectedScopes.addAll(protectedResourceScopes);
        testObserver.assertValue(request -> request.getScopes().containsAll(expectedScopes) && request.getScopes().size() == 4);
    }

    @Test
    public void shouldResolveAuthorizationRequest_withProtectedResourceScopes_requestedSome() {
        final String scope = "read";
        final List<String> protectedResourceScopes = Arrays.asList("resource1", "resource2", "resource3");
        final String redirectUri = "http://localhost:8080/callback";

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setRedirectUri(redirectUri);
        
        // Set resource in parameters (AuthorizationRequest.getResources() reads from parameters)
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.RESOURCE, "https://api.example.com");
        authorizationRequest.setParameters(parameters);
        
        List<String> authScopes = new ArrayList<>();
        authScopes.add(scope);
        authScopes.add(protectedResourceScopes.get(1)); // Request only the second protected resource scope
        authorizationRequest.setScopes(new HashSet<>(authScopes));

        Client client = new Client();
        ApplicationScopeSettings setting = new ApplicationScopeSettings();
        setting.setScope(scope);
        client.setScopeSettings(Collections.singletonList(setting));

        when(protectedResourceManager.getScopesForResources(Set.of("https://api.example.com")))
                .thenReturn(new HashSet<>(protectedResourceScopes));

        TestObserver<AuthorizationRequest> testObserver = authorizationRequestResolver.resolve(authorizationRequest, client, null).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        // Should only have client scope + the one requested protected resource scope
        List<String> expectedScopes = Arrays.asList(scope, protectedResourceScopes.get(1));
        testObserver.assertValue(request -> request.getScopes().containsAll(expectedScopes) && request.getScopes().size() == 2);
    }

    @Test
    public void shouldNotResolveAuthorizationRequest_withProtectedResourceScopes_invalidScope() {
        final String scope = "read";
        final String invalidScope = "invalid";
        final List<String> protectedResourceScopes = Arrays.asList("resource1", "resource2", "resource3");
        final String redirectUri = "http://localhost:8080/callback";

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setRedirectUri(redirectUri);
        
        // Set resource in parameters (AuthorizationRequest.getResources() reads from parameters)
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.RESOURCE, "https://api.example.com");
        authorizationRequest.setParameters(parameters);
        
        authorizationRequest.setScopes(Set.of(scope, invalidScope));

        Client client = new Client();
        ApplicationScopeSettings setting = new ApplicationScopeSettings();
        setting.setScope(scope);
        client.setScopeSettings(Collections.singletonList(setting));

        when(protectedResourceManager.getScopesForResources(Set.of("https://api.example.com")))
                .thenReturn(new HashSet<>(protectedResourceScopes));

        TestObserver<AuthorizationRequest> testObserver = authorizationRequestResolver.resolve(authorizationRequest, client, null).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidScopeException.class);
    }

    @Test
    public void shouldResolveAuthorizationRequest_withProtectedResourceScopes_removesFromInvalidScopes() {
        final String invalidScope = "invalidScope";
        final List<String> protectedResourceScopes = Arrays.asList("resource1", "resource2");
        final String redirectUri = "http://localhost:8080/callback";

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setRedirectUri(redirectUri);
        
        // Set resource in parameters (AuthorizationRequest.getResources() reads from parameters)
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.RESOURCE, "https://api.example.com");
        authorizationRequest.setParameters(parameters);
        
        // Request scopes that are not in client but ARE in protected resources
        authorizationRequest.setScopes(Set.of("resource1", invalidScope));

        Client client = new Client();
        // Client has no scopes configured

        when(protectedResourceManager.getScopesForResources(Set.of("https://api.example.com")))
                .thenReturn(new HashSet<>(protectedResourceScopes));

        TestObserver<AuthorizationRequest> testObserver = authorizationRequestResolver.resolve(authorizationRequest, client, null).test();
        testObserver.assertNotComplete();
        // Should fail because invalidScope is not in protected resources
        testObserver.assertError(InvalidScopeException.class);
    }

    @Test
    public void shouldResolveAuthorizationRequest_withProtectedResourceScopes_withUserScopes() {
        final String clientScope = "read";
        final List<String> userScopes = Arrays.asList("user1", "user2");
        final List<String> protectedResourceScopes = Arrays.asList("resource1", "resource2");
        final String redirectUri = "http://localhost:8080/callback";

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setRedirectUri(redirectUri);
        
        // Set resource in parameters (AuthorizationRequest.getResources() reads from parameters)
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.RESOURCE, "https://api.example.com");
        authorizationRequest.setParameters(parameters);

        List<String> authScopes = new ArrayList<>();
        authScopes.add(clientScope);
        authScopes.addAll(userScopes);
        authScopes.addAll(protectedResourceScopes);
        authorizationRequest.setScopes(new HashSet<>(authScopes));

        Client client = new Client();
        ApplicationScopeSettings setting = new ApplicationScopeSettings();
        setting.setScope(clientScope);
        client.setScopeSettings(Collections.singletonList(setting));
        client.setEnhanceScopesWithUserPermissions(true);

        User user = new User();
        Role role = new Role();
        role.setOauthScopes(userScopes);
        user.setRolesPermissions(Collections.singleton(role));

        when(protectedResourceManager.getScopesForResources(Set.of("https://api.example.com")))
                .thenReturn(new HashSet<>(protectedResourceScopes));

        TestObserver<AuthorizationRequest> testObserver = authorizationRequestResolver.resolve(authorizationRequest, client, user).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        // Should have client scope + user scopes + requested protected resource scopes
        List<String> expectedScopes = new ArrayList<>();
        expectedScopes.add(clientScope);
        expectedScopes.addAll(userScopes);
        expectedScopes.addAll(protectedResourceScopes);
        testObserver.assertValue(request -> request.getScopes().containsAll(expectedScopes) && request.getScopes().size() == 5);
    }


}
