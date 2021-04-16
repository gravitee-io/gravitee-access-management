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

import io.gravitee.am.gateway.handler.oauth2.exception.InvalidScopeException;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.observers.TestObserver;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.Test;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TokenRequestResolverTest {

    private final TokenRequestResolver tokenRequestResolver = new TokenRequestResolver();

    @Test
    public void shouldNotResolveTokenRequest_unknownScope() {
        final String scope = "read";
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setScopes(Collections.singleton(scope));
        Client client = new Client();

        TestObserver<TokenRequest> testObserver = tokenRequestResolver.resolve(tokenRequest, client, null).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidScopeException.class);
    }

    @Test
    public void shouldResolveTokenRequest_withUser_permissionsRequestedAll() {
        final String scope = "read";
        final List<String> userScopes = Arrays.asList("user1", "user2", "user3");

        TokenRequest tokenRequest = new TokenRequest();
        List<String> reqScopes = new ArrayList<>();
        reqScopes.add(scope);
        reqScopes.addAll(userScopes);
        tokenRequest.setScopes(new HashSet<>(reqScopes));

        Client client = new Client();
        client.setEnhanceScopesWithUserPermissions(true);
        client.setScopes(Collections.singletonList(scope));

        User user = new User();
        Role role = new Role();
        role.setOauthScopes(userScopes);
        user.setRolesPermissions(Collections.singleton(role));

        TestObserver<TokenRequest> testObserver = tokenRequestResolver.resolve(tokenRequest, client, user).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        List<String> expectedScopes = new ArrayList<>();
        expectedScopes.add(scope);
        expectedScopes.addAll(userScopes);
        testObserver.assertValue(
            request ->
                request.getScopes().containsAll(expectedScopes) && request.getScopes().contains(scope) && request.getScopes().size() == 4
        );
    }

    @Test
    public void shouldResolveTokenRequest_withUser_permissionsRequestedAny() {
        final String scope = "read";
        final List<String> userScopes = Arrays.asList("user1", "user2", "user3");

        TokenRequest tokenRequest = new TokenRequest();
        List<String> reqScopes = new ArrayList<>();
        reqScopes.add(scope);
        reqScopes.add(userScopes.get(1)); // Request only the second of the three user scopes
        tokenRequest.setScopes(new HashSet<>(reqScopes));

        Client client = new Client();
        client.setEnhanceScopesWithUserPermissions(true);
        client.setScopes(Collections.singletonList(scope));

        User user = new User();
        Role role = new Role();
        role.setOauthScopes(userScopes);
        user.setRolesPermissions(Collections.singleton(role));

        TestObserver<TokenRequest> testObserver = tokenRequestResolver.resolve(tokenRequest, client, user).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        // Request should have been enhanced with all of user's permissions, even though only one has been requested
        List<String> expectedScopes = new ArrayList<>();
        expectedScopes.add(scope);
        expectedScopes.addAll(userScopes);
        testObserver.assertValue(
            request ->
                request.getScopes().containsAll(expectedScopes) && request.getScopes().contains(scope) && request.getScopes().size() == 4
        );
    }

    @Test
    public void shouldResolveTokenRequest_withUser_permissionsRequestedNone() {
        final String scope = "read";
        final List<String> userScopes = Arrays.asList("user1", "user2", "user3");

        TokenRequest tokenRequest = new TokenRequest();
        List<String> reqScopes = new ArrayList<>();
        reqScopes.add(scope);
        // Request none of the three user scopes
        tokenRequest.setScopes(new HashSet<>(reqScopes));

        Client client = new Client();
        client.setEnhanceScopesWithUserPermissions(true);
        client.setScopes(Collections.singletonList(scope));

        User user = new User();
        Role role = new Role();
        role.setOauthScopes(userScopes);
        user.setRolesPermissions(Collections.singleton(role));

        TestObserver<TokenRequest> testObserver = tokenRequestResolver.resolve(tokenRequest, client, user).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        // Request should have been enhanced with all of user's permissions, even though none of them has been requested
        List<String> expectedScopes = new ArrayList<>();
        expectedScopes.add(scope);
        expectedScopes.addAll(userScopes);
        testObserver.assertValue(
            request ->
                request.getScopes().containsAll(expectedScopes) && request.getScopes().contains(scope) && request.getScopes().size() == 4
        );
    }

    @Test
    public void shouldResolveTokenRequest_emptyScope() {
        TokenRequest tokenRequest = new TokenRequest();
        Client client = new Client();

        TestObserver<TokenRequest> testObserver = tokenRequestResolver.resolve(tokenRequest, client, null).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }

    @Test
    public void shouldNotResolveTokenRequest_invalidScope() {
        final String scope = "read";
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setScopes(Collections.singleton(scope));
        Client client = new Client();
        client.setScopes(Collections.singletonList("write"));

        TestObserver<TokenRequest> testObserver = tokenRequestResolver.resolve(tokenRequest, client, null).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidScopeException.class);
    }

    @Test
    public void shouldNotResolveTokenRequest_invalidScope_withUser() {
        final String scope = "read";
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setScopes(Collections.singleton(scope));
        Client client = new Client();
        client.setScopes(Collections.singletonList("write"));

        User user = new User();
        Role role = new Role();
        role.setOauthScopes(Collections.singletonList("user"));
        user.setRolesPermissions(Collections.singleton(role));

        TestObserver<TokenRequest> testObserver = tokenRequestResolver.resolve(tokenRequest, client, user).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidScopeException.class);
    }

    @Test
    public void shouldResolveAuthorizationRequest_noRequestedScope_clientDefaultScopes() {
        final String scope = "read";
        TokenRequest authorizationRequest = new TokenRequest();

        Client client = new Client();
        client.setScopes(Collections.singletonList(scope));

        TestObserver<TokenRequest> testObserver = tokenRequestResolver.resolve(authorizationRequest, client, null).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(request -> request.getScopes().iterator().next().equals(scope));
    }

    @Test
    public void shouldResolveAuthorizationRequest_noRequestedScope_userDefaultScopes() {
        final String scope = "read";
        TokenRequest authorizationRequest = new TokenRequest();

        Client client = new Client();
        client.setEnhanceScopesWithUserPermissions(true);

        User user = new User();
        Role role = new Role();
        role.setOauthScopes(Collections.singletonList(scope));
        user.setRolesPermissions(Collections.singleton(role));

        TestObserver<TokenRequest> testObserver = tokenRequestResolver.resolve(authorizationRequest, client, user).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(request -> request.getScopes().iterator().next().equals(scope));
    }
}
