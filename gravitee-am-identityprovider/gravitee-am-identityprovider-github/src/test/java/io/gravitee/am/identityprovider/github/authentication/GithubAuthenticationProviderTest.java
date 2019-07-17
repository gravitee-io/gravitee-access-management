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
package io.gravitee.am.identityprovider.github.authentication;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.AuthenticationContext;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.github.authentication.spring.GithubAuthenticationProviderConfiguration;
import io.gravitee.am.identityprovider.github.utils.URLEncodedUtils;
import io.gravitee.am.service.exception.authentication.BadCredentialsException;
import io.gravitee.common.http.HttpHeaders;
import io.reactivex.observers.TestObserver;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.util.Collections;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { GithubAuthenticationProviderTestConfiguration.class, GithubAuthenticationProviderConfiguration.class }, loader = AnnotationConfigContextLoader.class)
public class GithubAuthenticationProviderTest {

    @Autowired
    private AuthenticationProvider authenticationProvider;

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().port(19998));

    @Test
    public void shouldLoadUserByUsername_authentication() {
        stubFor(any(urlPathEqualTo("/oauth/token"))
                .withHeader(HttpHeaders.CONTENT_TYPE, containing(URLEncodedUtils.CONTENT_TYPE))
                .withRequestBody(matching(".*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("access_token=test_token&token_type=bearer")));

        stubFor(any(urlPathEqualTo("/profile"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("token test_token"))
                .willReturn(okJson("{ \"login\": \"bob\" }")));

        TestObserver<User> testObserver = authenticationProvider.loadUserByUsername(new Authentication() {
            @Override
            public Object getCredentials() {
                return "test-code";
            }

            @Override
            public Object getPrincipal() {
                return "__oauth2__";
            }

            @Override
            public AuthenticationContext getContext() {
                return new DummyAuthenticationContext(Collections.singletonMap("redirect_uri", "http://redirect_uri"));
            }
        }).test();

        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> "bob".equals(u.getUsername()));
    }

    @Test
    public void shouldLoadUserByUsername_authentication_badCredentials() {
        stubFor(any(urlPathEqualTo("/oauth/token"))
                .withHeader(HttpHeaders.CONTENT_TYPE, containing(URLEncodedUtils.CONTENT_TYPE))
                .withRequestBody(matching(".*"))
                .willReturn(unauthorized()));

        TestObserver<User> testObserver = authenticationProvider.loadUserByUsername(new Authentication() {
            @Override
            public Object getCredentials() {
                return "wrongpassword";
            }

            @Override
            public Object getPrincipal() {
                return "bob";
            }

            @Override
            public AuthenticationContext getContext() {
                return new DummyAuthenticationContext(Collections.singletonMap("redirect_uri", "http://redirect_uri"));
            }
        }).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertError(BadCredentialsException.class);
    }

    @Test
    public void shouldLoadUserByUsername_authentication_usernameNotFound() {
        stubFor(any(urlPathEqualTo("/oauth/token"))
                .withHeader(HttpHeaders.CONTENT_TYPE, containing(URLEncodedUtils.CONTENT_TYPE))
                .withRequestBody(matching(".*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("access_token=test_token&token_type=bearer")));

        stubFor(any(urlPathEqualTo("/profile"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("token test_token"))
                .willReturn(notFound()));

        TestObserver<User> testObserver = authenticationProvider.loadUserByUsername(new Authentication() {
            @Override
            public Object getCredentials() {
                return "bobspassword";
            }

            @Override
            public Object getPrincipal() {
                return "unknownUsername";
            }

            @Override
            public AuthenticationContext getContext() {
                return new DummyAuthenticationContext(Collections.singletonMap("redirect_uri", "http://redirect_uri"));
            }
        }).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertError(BadCredentialsException.class);
    }
}
