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
package io.gravitee.am.gateway.handler.oauth2.request;

import io.gravitee.am.gateway.handler.oauth2.exception.InvalidRequestException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidScopeException;
import io.gravitee.am.gateway.handler.oauth2.exception.UnauthorizedClientException;
import io.gravitee.am.model.Client;
import io.reactivex.observers.TestObserver;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthorizationRequestResolverTest {

    private final AuthorizationRequestResolver authorizationRequestResolver = new AuthorizationRequestResolver();

    @Test
    public void shouldResolveAuthorizationRequest() {
        final String scope = "read";
        final String redirectUri = "http://localhost:8080/callback";
        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setScopes(Collections.singleton(scope));
        authorizationRequest.setRedirectUri(redirectUri);
        Client client = new Client();

        TestObserver<AuthorizationRequest> testObserver = authorizationRequestResolver.resolve(authorizationRequest, client).test();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }

    @Test
    public void shouldResolveAuthorizationRequest_emptyAuthorizedGrantType() {
        final String scope = "read";
        final String redirectUri = "http://localhost:8080/callback";
        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setScopes(Collections.singleton(scope));
        authorizationRequest.setRedirectUri(redirectUri);
        Client client = new Client();
        client.setAuthorizedGrantTypes(Collections.emptyList());

        TestObserver<AuthorizationRequest> testObserver = authorizationRequestResolver.resolve(authorizationRequest, client).test();
        testObserver.assertNotComplete();
        testObserver.assertError(UnauthorizedClientException.class);
    }

    @Test
    public void shouldResolveAuthorizationRequest_invalidAuthorizedGrantType() {
        final String scope = "read";
        final String redirectUri = "http://localhost:8080/callback";
        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setScopes(Collections.singleton(scope));
        authorizationRequest.setRedirectUri(redirectUri);
        Client client = new Client();
        client.setAuthorizedGrantTypes(Collections.singletonList("client_credentials"));

        TestObserver<AuthorizationRequest> testObserver = authorizationRequestResolver.resolve(authorizationRequest, client).test();
        testObserver.assertNotComplete();
        testObserver.assertError(UnauthorizedClientException.class);
    }

    @Test
    public void shouldResolveAuthorizationRequest_emptyScope() {
        final String redirectUri = "http://localhost:8080/callback";
        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setRedirectUri(redirectUri);
        Client client = new Client();

        TestObserver<AuthorizationRequest> testObserver = authorizationRequestResolver.resolve(authorizationRequest, client).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidScopeException.class);
    }

    @Test
    public void shouldResolveAuthorizationRequest_noRequestedScope() {
        final String scope = "read";
        final String redirectUri = "http://localhost:8080/callback";
        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setRedirectUri(redirectUri);
        Client client = new Client();
        client.setScopes(Collections.singletonList(scope));

        TestObserver<AuthorizationRequest> testObserver = authorizationRequestResolver.resolve(authorizationRequest, client).test();
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
        client.setScopes(Collections.singletonList("write"));

        TestObserver<AuthorizationRequest> testObserver = authorizationRequestResolver.resolve(authorizationRequest, client).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidScopeException.class);
    }

    @Test
    public void shouldResolveAuthorizationRequest_matchingRedirectUri() {
        final String scope = "read";
        final String redirectUri = "http://localhost:8080/callback";
        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setScopes(Collections.singleton(scope));
        authorizationRequest.setRedirectUri(redirectUri);
        Client client = new Client();
        client.setRedirectUris(Arrays.asList("http://localhost:8080/callback", "http://localhost:8080/callback2"));

        TestObserver<AuthorizationRequest> testObserver = authorizationRequestResolver.resolve(authorizationRequest, client).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
    }

    @Test
    public void shouldResolveAuthorizationRequest_mismatchingRedirectUri() {
        final String scope = "read";
        final String redirectUri = "http://localhost:8080/callback";
        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setScopes(Collections.singleton(scope));
        authorizationRequest.setRedirectUri(redirectUri);
        Client client = new Client();
        client.setRedirectUris(Arrays.asList("http://localhost:8080/allowRedirect", "http://localhost:8080/allowRedirect2"));

        TestObserver<AuthorizationRequest> testObserver = authorizationRequestResolver.resolve(authorizationRequest, client).test();
        testObserver.assertNotComplete();
        testObserver.assertError(InvalidRequestException.class);
    }
}
