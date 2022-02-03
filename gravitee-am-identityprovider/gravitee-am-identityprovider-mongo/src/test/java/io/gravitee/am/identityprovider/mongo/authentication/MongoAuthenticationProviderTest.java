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
package io.gravitee.am.identityprovider.mongo.authentication;

import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.AuthenticationContext;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.mongo.authentication.spring.MongoAuthenticationProviderConfiguration;
import io.gravitee.am.common.exception.authentication.BadCredentialsException;
import io.gravitee.am.common.exception.authentication.UsernameNotFoundException;
import io.reactivex.observers.TestObserver;
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
@ContextConfiguration(classes = { MongoAuthenticationProviderTestConfiguration.class, MongoAuthenticationProviderConfiguration.class }, loader = AnnotationConfigContextLoader.class)
public class MongoAuthenticationProviderTest {

    @Autowired
    private AuthenticationProvider authenticationProvider;

    /** This is a workaround for Macs with M1 ships
     * Link to the github issue:
     * https://github.com/flapdoodle-oss/de.flapdoodle.embed.mongo/issues/337#issuecomment-794303570
     * */
    static { System.setProperty("os.arch", "i686_64"); }

    @Test
    public void shouldLoadUserByUsername_authentication() {
        TestObserver<User> testObserver = authenticationProvider.loadUserByUsername(new Authentication() {
            @Override
            public Object getCredentials() {
                return "bobspassword";
            }

            @Override
            public Object getPrincipal() {
                return "bob";
            }

            @Override
            public AuthenticationContext getContext() {
                return null;
            }
        }).test();

        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> "bob".equals(u.getUsername()));
    }

    @Test
    public void shouldLoadUserByUsername_authentication_multifield_username() {
        TestObserver<User> testObserver = authenticationProvider.loadUserByUsername(new Authentication() {
            @Override
            public Object getCredentials() {
                return "user01";
            }

            @Override
            public Object getPrincipal() {
                return "user01";
            }

            @Override
            public AuthenticationContext getContext() {
                return null;
            }
        }).test();

        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> "user01".equals(u.getUsername()));
    }

    @Test
    public void shouldLoadUserByUsername_authentication_multifield_email() {
        TestObserver<User> testObserver = authenticationProvider.loadUserByUsername(new Authentication() {
            @Override
            public Object getCredentials() {
                return "user01";
            }

            @Override
            public Object getPrincipal() {
                return "user01@acme.com";
            }

            @Override
            public AuthenticationContext getContext() {
                return null;
            }
        }).test();

        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> "user01".equals(u.getUsername()));
    }

    @Test
    public void shouldLoadUserByUsername_authentication_case_insensitive() {
        TestObserver<User> testObserver = authenticationProvider.loadUserByUsername(new Authentication() {
            @Override
            public Object getCredentials() {
                return "bobspassword";
            }

            @Override
            public Object getPrincipal() {
                return "BOB";
            }

            @Override
            public AuthenticationContext getContext() {
                return null;
            }
        }).test();

        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> "bob".equals(u.getUsername()));
    }

    @Test
    public void shouldLoadUserByUsername_authentication_badCredentials() {
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
                return null;
            }
        }).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertError(BadCredentialsException.class);
    }

    @Test
    public void shouldLoadUserByUsername_authentication_usernameNotFound() {
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
                return null;
            }
        }).test();

        testObserver.awaitTerminalEvent();
        testObserver.assertError(UsernameNotFoundException.class);
    }

}
