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
import io.gravitee.am.model.Client;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.User;
import io.reactivex.observers.TestObserver;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

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
    public void shouldResolveTokenRequest_withUser() {
        final String scope = "read";
        final String userScope = "user";
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setScopes(new HashSet<>(Arrays.asList(scope, userScope)));
        Client client = new Client();
        client.setEnhanceScopesWithUserPermissions(true);
        client.setScopes(Collections.singletonList(scope));

        User user = new User();
        Role role = new Role();
        role.setPermissions(Collections.singletonList("user"));
        user.setRolesPermissions(Collections.singleton(role));

        TestObserver<TokenRequest> testObserver = tokenRequestResolver.resolve(tokenRequest, client, user).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
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
        role.setPermissions(Collections.singletonList("user"));
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
        role.setPermissions(Collections.singletonList(scope));
        user.setRolesPermissions(Collections.singleton(role));

        TestObserver<TokenRequest> testObserver = tokenRequestResolver.resolve(authorizationRequest, client, user).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(request -> request.getScopes().iterator().next().equals(scope));
    }
}
