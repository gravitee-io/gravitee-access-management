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
import io.gravitee.am.common.exception.authentication.BadCredentialsException;
import io.gravitee.am.identityprovider.api.*;
import io.gravitee.am.identityprovider.common.oauth2.utils.URLEncodedUtils;
import io.gravitee.am.identityprovider.github.GithubIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.github.authentication.spring.GithubAuthenticationProviderConfiguration;
import io.gravitee.common.http.HttpHeaders;
import io.reactivex.observers.TestObserver;
import java.util.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.util.Collections.singletonMap;
import static org.junit.Assert.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {GithubAuthenticationProviderTestConfiguration.class, GithubAuthenticationProviderConfiguration.class}, loader = AnnotationConfigContextLoader.class)
public class GithubAuthenticationProviderTest {

    @Autowired
    private AuthenticationProvider authenticationProvider;

    @Autowired
    private DefaultIdentityProviderRoleMapper roleMapper;

    @Autowired
    private GithubIdentityProviderConfiguration configuration;

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().port(19998));

    @Test
    public void shouldLoadUserByUsername_authentication() {
        configuration.setStoreOriginalTokens(false);

        stubFor(any(urlPathEqualTo("/oauth/token"))
                .withHeader(HttpHeaders.CONTENT_TYPE, containing(URLEncodedUtils.CONTENT_TYPE))
                .withRequestBody(matching(".*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("access_token=test_token&token_type=bearer")));

        stubFor(any(urlPathEqualTo("/profile"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("token test_token"))
                .willReturn(okJson("{ \"login\": \"bob\" }")));

        final HashMap<String, Object> attributes = new HashMap<>(singletonMap("redirect_uri", "http://redirect_uri"));
        final Map<String, List<String>> parameters = singletonMap("code", Arrays.asList("test-code"));
        var authentication = new DummySocialAuthentication(parameters, attributes);
        TestObserver<User> testObserver = authenticationProvider.loadUserByUsername(authentication).test();

        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> "bob".equals(u.getUsername()));

        assertNull(authentication.getContext().get("access_token"));
    }

    @Test
    public void shouldLoadUserByUsername_authentication_badCredentials() {
        configuration.setStoreOriginalTokens(false);

        stubFor(any(urlPathEqualTo("/oauth/token"))
                .withHeader(HttpHeaders.CONTENT_TYPE, containing(URLEncodedUtils.CONTENT_TYPE))
                .withRequestBody(matching(".*"))
                .willReturn(unauthorized()));

        final HashMap<String, Object> attributes = new HashMap<>(singletonMap("redirect_uri", "http://redirect_uri"));
        final Map<String, List<String>> parameters = singletonMap("code", Arrays.asList("test-code"));
        var authentication = new DummySocialAuthentication(parameters, attributes);
        TestObserver<User> testObserver = authenticationProvider.loadUserByUsername(authentication).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertError(BadCredentialsException.class);
    }

    @Test
    public void shouldLoadUserByUsername_authentication_usernameNotFound() {
        configuration.setStoreOriginalTokens(false);

        stubFor(any(urlPathEqualTo("/oauth/token"))
                .withHeader(HttpHeaders.CONTENT_TYPE, containing(URLEncodedUtils.CONTENT_TYPE))
                .withRequestBody(matching(".*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("access_token=test_token&token_type=bearer")));

        stubFor(any(urlPathEqualTo("/profile"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("token test_token"))
                .willReturn(notFound()));

        final HashMap<String, Object> attributes = new HashMap<>(singletonMap("redirect_uri", "http://redirect_uri"));
        final Map<String, List<String>> parameters = singletonMap("code", Arrays.asList("test-code"));
        var authentication = new DummySocialAuthentication(parameters, attributes);
        TestObserver<User> testObserver = authenticationProvider.loadUserByUsername(authentication).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertError(BadCredentialsException.class);

        assertNull(authentication.getContext().get("access_token"));
    }

    @Test
    public void shouldLoadUserByUsername_roleMapping() {
        configuration.setStoreOriginalTokens(false);

        // configure role mapping
        Map<String, String[]> roles = new HashMap<>();
        roles.put("admin", new String[]{"preferred_username=bob"});
        roleMapper.setRoles(roles);

        stubFor(any(urlPathEqualTo("/oauth/token"))
                .withHeader(HttpHeaders.CONTENT_TYPE, containing(URLEncodedUtils.CONTENT_TYPE))
                .withRequestBody(matching(".*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("access_token=test_token&token_type=bearer")));

        stubFor(any(urlPathEqualTo("/profile"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("token test_token"))
                .willReturn(okJson("{ \"login\": \"bob\", \"preferred_username\": \"bob\"}")));

        final HashMap<String, Object> attributes = new HashMap<>(singletonMap("redirect_uri", "http://redirect_uri"));
        final Map<String, List<String>> parameters = singletonMap("code", Arrays.asList("test-code"));
        var authentication = new DummySocialAuthentication(parameters, attributes);
        TestObserver<User> testObserver = authenticationProvider.loadUserByUsername(authentication).test();

        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> "bob".equals(u.getUsername()));
        testObserver.assertValue(u -> u.getRoles().contains("admin"));

        assertNull(authentication.getContext().get("access_token"));
    }


    @Test
    public void shouldLoadUserByUsername_authentication_and_storeToken() {
        configuration.setStoreOriginalTokens(true);

        stubFor(any(urlPathEqualTo("/oauth/token"))
                .withHeader(HttpHeaders.CONTENT_TYPE, containing(URLEncodedUtils.CONTENT_TYPE))
                .withRequestBody(matching(".*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("access_token=test_token&token_type=bearer")));

        stubFor(any(urlPathEqualTo("/profile"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("token test_token"))
                .willReturn(okJson("{ \"login\": \"bob\" }")));

        final HashMap<String, Object> attributes = new HashMap<>(singletonMap("redirect_uri", "http://redirect_uri"));
        final Map<String, List<String>> parameters = singletonMap("code", Arrays.asList("test-code"));
        var authentication = new DummySocialAuthentication(parameters, attributes);
        TestObserver<User> testObserver = authenticationProvider.loadUserByUsername(authentication).test();

        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> "bob".equals(u.getUsername()));

        assertNotNull(authentication.getContext().get("access_token"));
        assertEquals(authentication.getContext().get("access_token"), "test_token");
    }

    @Test
    public void shouldLoadUserByUsername_authentication_badCredentials_and_storeToken() {
        configuration.setStoreOriginalTokens(true);

        stubFor(any(urlPathEqualTo("/oauth/token"))
                .withHeader(HttpHeaders.CONTENT_TYPE, containing(URLEncodedUtils.CONTENT_TYPE))
                .withRequestBody(matching(".*"))
                .willReturn(unauthorized()));

        final HashMap<String, Object> attributes = new HashMap<>(singletonMap("redirect_uri", "http://redirect_uri"));
        final Map<String, List<String>> parameters = singletonMap("code", Arrays.asList("test-code"));
        var authentication = new DummySocialAuthentication(parameters, attributes);
        TestObserver<User> testObserver = authenticationProvider.loadUserByUsername(authentication).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertError(BadCredentialsException.class);

        assertNull(authentication.getContext().get("access_token"));
    }

    @Test
    public void shouldLoadUserByUsername_authentication_usernameNotFound_and_storeToken() {
        configuration.setStoreOriginalTokens(true);

        stubFor(any(urlPathEqualTo("/oauth/token"))
                .withHeader(HttpHeaders.CONTENT_TYPE, containing(URLEncodedUtils.CONTENT_TYPE))
                .withRequestBody(matching(".*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("access_token=test_token&token_type=bearer")));

        stubFor(any(urlPathEqualTo("/profile"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("token test_token"))
                .willReturn(notFound()));

        final HashMap<String, Object> attributes = new HashMap<>(singletonMap("redirect_uri", "http://redirect_uri"));
        final Map<String, List<String>> parameters = singletonMap("code", Arrays.asList("test-code"));
        var authentication = new DummySocialAuthentication(parameters, attributes);
        TestObserver<User> testObserver = authenticationProvider.loadUserByUsername(authentication).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertError(BadCredentialsException.class);

        assertNotNull(authentication.getContext().get("access_token"));
        assertEquals(authentication.getContext().get("access_token"), "test_token");
    }

    @Test
    public void shouldLoadUserByUsername_roleMapping_and_storeToken() {
        configuration.setStoreOriginalTokens(true);

        // configure role mapping
        Map<String, String[]> roles = new HashMap<>();
        roles.put("admin", new String[]{"preferred_username=bob"});
        roleMapper.setRoles(roles);

        stubFor(any(urlPathEqualTo("/oauth/token"))
                .withHeader(HttpHeaders.CONTENT_TYPE, containing(URLEncodedUtils.CONTENT_TYPE))
                .withRequestBody(matching(".*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("access_token=test_token&token_type=bearer")));

        stubFor(any(urlPathEqualTo("/profile"))
                .withHeader(HttpHeaders.AUTHORIZATION, equalTo("token test_token"))
                .willReturn(okJson("{ \"login\": \"bob\", \"preferred_username\": \"bob\"}")));

        final HashMap<String, Object> attributes = new HashMap<>(singletonMap("redirect_uri", "http://redirect_uri"));
        final Map<String, List<String>> parameters = singletonMap("code", Arrays.asList("test-code"));
        var authentication = new DummySocialAuthentication(parameters, attributes);
        TestObserver<User> testObserver = authenticationProvider.loadUserByUsername(authentication).test();

        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> "bob".equals(u.getUsername()));
        testObserver.assertValue(u -> u.getRoles().contains("admin"));

        assertNotNull(authentication.getContext().get("access_token"));
        assertEquals(authentication.getContext().get("access_token"), "test_token");
    }
}
