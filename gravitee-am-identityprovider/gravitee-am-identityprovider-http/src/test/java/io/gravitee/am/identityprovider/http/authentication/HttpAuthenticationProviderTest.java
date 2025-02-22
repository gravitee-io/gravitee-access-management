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

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.gravitee.am.common.exception.authentication.BadCredentialsException;
import io.gravitee.am.common.exception.authentication.UsernameNotFoundException;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.AuthenticationContext;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.DefaultIdentityProviderGroupMapper;
import io.gravitee.am.identityprovider.api.DefaultIdentityProviderMapper;
import io.gravitee.am.identityprovider.api.DefaultIdentityProviderRoleMapper;
import io.gravitee.am.identityprovider.api.DummyRequest;
import io.gravitee.am.identityprovider.api.SimpleAuthenticationContext;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.http.authentication.spring.HttpAuthenticationProviderConfiguration;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.unauthorized;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { HttpAuthenticationProviderTestConfiguration.class, HttpAuthenticationProviderConfiguration.class }, loader = AnnotationConfigContextLoader.class)
public class HttpAuthenticationProviderTest {

    @Autowired
    private AuthenticationProvider authenticationProvider;

    @Autowired
    private DefaultIdentityProviderRoleMapper roleMapper;

    @Autowired
    private DefaultIdentityProviderGroupMapper groupMapper;

    @Autowired
    private DefaultIdentityProviderMapper mapper;

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().port(19999));

    @Test
    public void shouldLoadUserByUsername_authentication() {
        stubFor(any(urlPathEqualTo("/api/authentication"))
                .withRequestBody(matching(".*"))
                .willReturn(okJson("{\"sub\" : \"123456789\", \"preferred_username\" : \"johndoe\"}")));

        TestObserver<User> testObserver = authenticationProvider.loadUserByUsername(new Authentication() {
            @Override
            public Object getCredentials() {
                return "johndoepassword\"";
            }

            @Override
            public Object getPrincipal() {
                return "johndoe";
            }

            @Override
            public AuthenticationContext getContext() {
                return new SimpleAuthenticationContext(new DummyRequest());
            }
        }).test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> "123456789".equals(u.getId()));
        testObserver.assertValue(u -> "johndoe".equals(u.getUsername()));
    }

    @Test
    public void shouldLoadUserByUsername_authentication_badCredentials() {
        stubFor(any(urlPathEqualTo("/api/authentication"))
                .withRequestBody(matching(".*"))
                .willReturn(unauthorized()));

        TestObserver<User> testObserver = authenticationProvider.loadUserByUsername(new Authentication() {
            @Override
            public Object getCredentials() {
                return "johndoepassword";
            }

            @Override
            public Object getPrincipal() {
                return "johndoe";
            }

            @Override
            public AuthenticationContext getContext() {
                return new SimpleAuthenticationContext(new DummyRequest());
            }
        }).test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(BadCredentialsException.class);
    }

    @Test
    public void shouldLoadUserByUsername_authentication_usernameNotFound() {
        stubFor(any(urlPathEqualTo("/api/authentication"))
                .withRequestBody(matching(".*"))
                .willReturn(notFound()));

        TestObserver<User> testObserver = authenticationProvider.loadUserByUsername(new Authentication() {
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
        }).test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(UsernameNotFoundException.class);
    }

    @Test
    public void shouldLoadUserByUsername_userMapping() {
        // configure role mapping
        Map<String, String> attributes = new HashMap<>();
        attributes.put("sub", "id");
        attributes.put("preferred_username", "username");
        mapper.setMappers(attributes);

        stubFor(any(urlPathEqualTo("/api/authentication"))
                .withRequestBody(matching(".*"))
                .willReturn(okJson("{\"id\" : \"123456789\", \"username\" : \"johndoe\"}")));

        TestObserver<User> testObserver = authenticationProvider.loadUserByUsername(new Authentication() {
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
        }).test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> "123456789".equals(u.getId()));
        testObserver.assertValue(u -> "johndoe".equals(u.getUsername()));
    }


    @Test
    public void shouldLoadUserByUsername_roleAndGroupMapping() {
        // configure role mapping
        Map<String, String[]> roles = new HashMap<>();
        roles.put("admin", new String[] { "preferred_username=johndoe"});
        roleMapper.setRoles(roles);

        // configure group mapping
        groupMapper.setGroups(Map.of("GR1", new String[] { "preferred_username=johndoe"}));

        stubFor(any(urlPathEqualTo("/api/authentication"))
                .withRequestBody(matching(".*"))
                .willReturn(okJson("{\"sub\" : \"123456789\", \"preferred_username\" : \"johndoe\"}")));

        TestObserver<User> testObserver = authenticationProvider.loadUserByUsername(new Authentication() {
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
        }).test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> "123456789".equals(u.getId()));
        testObserver.assertValue(u -> "johndoe".equals(u.getUsername()));
        testObserver.assertValue(u -> u.getRoles().contains("admin"));
        testObserver.assertValue(u -> u.getGroups().contains("GR1"));
    }

}
