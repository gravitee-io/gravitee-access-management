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
package io.gravitee.am.identityprovider.http.authentication;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.gravitee.am.common.exception.authentication.BadCredentialsException;
import io.gravitee.am.common.exception.authentication.UsernameNotFoundException;
import io.gravitee.am.identityprovider.api.*;
import io.gravitee.am.identityprovider.http.HttpIdentityProviderMapper;
import io.gravitee.am.identityprovider.http.HttpIdentityProviderRoleMapper;
import io.gravitee.am.identityprovider.http.authentication.spring.HttpAuthenticationProviderConfiguration;
import io.reactivex.observers.TestObserver;
import java.util.HashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(
    classes = { HttpAuthenticationProviderTestConfiguration.class, HttpAuthenticationProviderConfiguration.class },
    loader = AnnotationConfigContextLoader.class
)
public class HttpAuthenticationProviderTest {

    @Autowired
    private AuthenticationProvider authenticationProvider;

    @Autowired
    private HttpIdentityProviderRoleMapper roleMapper;

    @Autowired
    private HttpIdentityProviderMapper mapper;

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().port(19999));

    @Test
    public void shouldLoadUserByUsername_authentication() {
        stubFor(
            any(urlPathEqualTo("/api/authentication"))
                .withRequestBody(matching(".*"))
                .willReturn(okJson("{\"sub\" : \"123456789\", \"preferred_username\" : \"johndoe\"}"))
        );

        TestObserver<User> testObserver = authenticationProvider
            .loadUserByUsername(
                new Authentication() {
                    @Override
                    public Object getCredentials() {
                        return "johndoe";
                    }

                    @Override
                    public Object getPrincipal() {
                        return "johndoepassword";
                    }

                    @Override
                    public AuthenticationContext getContext() {
                        return new SimpleAuthenticationContext(new DummyRequest());
                    }
                }
            )
            .test();

        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> "123456789".equals(u.getId()));
        testObserver.assertValue(u -> "johndoe".equals(u.getUsername()));
    }

    @Test
    public void shouldLoadUserByUsername_authentication_badCredentials() {
        stubFor(any(urlPathEqualTo("/api/authentication")).withRequestBody(matching(".*")).willReturn(unauthorized()));

        TestObserver<User> testObserver = authenticationProvider
            .loadUserByUsername(
                new Authentication() {
                    @Override
                    public Object getCredentials() {
                        return "johndoe";
                    }

                    @Override
                    public Object getPrincipal() {
                        return "johndoepassword";
                    }

                    @Override
                    public AuthenticationContext getContext() {
                        return new SimpleAuthenticationContext(new DummyRequest());
                    }
                }
            )
            .test();

        testObserver.awaitTerminalEvent();
        testObserver.assertError(BadCredentialsException.class);
    }

    @Test
    public void shouldLoadUserByUsername_authentication_usernameNotFound() {
        stubFor(any(urlPathEqualTo("/api/authentication")).withRequestBody(matching(".*")).willReturn(notFound()));

        TestObserver<User> testObserver = authenticationProvider
            .loadUserByUsername(
                new Authentication() {
                    @Override
                    public Object getCredentials() {
                        return "johndoe";
                    }

                    @Override
                    public Object getPrincipal() {
                        return "johndoepassword";
                    }

                    @Override
                    public AuthenticationContext getContext() {
                        return new SimpleAuthenticationContext(new DummyRequest());
                    }
                }
            )
            .test();

        testObserver.awaitTerminalEvent();
        testObserver.assertError(UsernameNotFoundException.class);
    }

    @Test
    public void shouldLoadUserByUsername_userMapping() {
        // configure role mapping
        Map<String, String> attributes = new HashMap<>();
        attributes.put("sub", "id");
        attributes.put("preferred_username", "username");
        mapper.setMappers(attributes);

        stubFor(
            any(urlPathEqualTo("/api/authentication"))
                .withRequestBody(matching(".*"))
                .willReturn(okJson("{\"id\" : \"123456789\", \"username\" : \"johndoe\"}"))
        );

        TestObserver<User> testObserver = authenticationProvider
            .loadUserByUsername(
                new Authentication() {
                    @Override
                    public Object getCredentials() {
                        return "johndoe";
                    }

                    @Override
                    public Object getPrincipal() {
                        return "johndoepassword";
                    }

                    @Override
                    public AuthenticationContext getContext() {
                        return new SimpleAuthenticationContext(new DummyRequest());
                    }
                }
            )
            .test();

        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> "123456789".equals(u.getId()));
        testObserver.assertValue(u -> "johndoe".equals(u.getUsername()));
    }

    @Test
    public void shouldLoadUserByUsername_roleMapping() {
        // configure role mapping
        Map<String, String[]> roles = new HashMap<>();
        roles.put("admin", new String[] { "preferred_username=johndoe" });
        roleMapper.setRoles(roles);

        stubFor(
            any(urlPathEqualTo("/api/authentication"))
                .withRequestBody(matching(".*"))
                .willReturn(okJson("{\"sub\" : \"123456789\", \"preferred_username\" : \"johndoe\"}"))
        );

        TestObserver<User> testObserver = authenticationProvider
            .loadUserByUsername(
                new Authentication() {
                    @Override
                    public Object getCredentials() {
                        return "johndoe";
                    }

                    @Override
                    public Object getPrincipal() {
                        return "johndoepassword";
                    }

                    @Override
                    public AuthenticationContext getContext() {
                        return new SimpleAuthenticationContext(new DummyRequest());
                    }
                }
            )
            .test();

        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> "123456789".equals(u.getId()));
        testObserver.assertValue(u -> "johndoe".equals(u.getUsername()));
        testObserver.assertValue(u -> u.getRoles().contains("admin"));
    }
}
