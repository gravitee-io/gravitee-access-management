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
package io.gravitee.am.identityprovider.ldap.authentication;

import io.gravitee.am.common.exception.authentication.BadCredentialsException;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.AuthenticationContext;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
import io.gravitee.am.identityprovider.api.User;
import io.reactivex.observers.TestObserver;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ldaptive.pool.ConnectionPool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.zapodot.junit.ldap.EmbeddedLdapRule;
import org.zapodot.junit.ldap.EmbeddedLdapRuleBuilder;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(SpringJUnit4ClassRunner.class)
public abstract class LdapAuthenticationProviderTest {

    @Autowired
    protected AuthenticationProvider authenticationProvider;

    @Autowired
    private ConnectionPool bindConnectionPool;

    @Autowired
    private ConnectionPool searchConnectionPool;

    @Rule
    public EmbeddedLdapRule embeddedLdapRule = EmbeddedLdapRuleBuilder
        .newInstance()
        .bindingToAddress("localhost")
        .bindingToPort(61000)
        .usingDomainDsn("dc=example,dc=org")
        .importingLdifs("test-server.ldif")
        .build();

    @Before
    public void init() {
        bindConnectionPool.initialize();
        searchConnectionPool.initialize();
    }

    @After
    public void close() {
        bindConnectionPool.close();
        searchConnectionPool.close();
    }

    @Test
    public void shouldLoadUserByUsername_authentication_badCredentials() throws Exception {
        embeddedLdapRule.ldapConnection();
        TestObserver<User> testObserver = authenticationProvider
            .loadUserByUsername(
                new Authentication() {
                    @Override
                    public Object getCredentials() {
                        return "wrongpassword";
                    }

                    @Override
                    public Object getPrincipal() {
                        return "ben";
                    }

                    @Override
                    public AuthenticationContext getContext() {
                        return null;
                    }
                }
            )
            .test();

        testObserver.assertError(BadCredentialsException.class);
    }

    @Test
    public void shouldLoadUserByUsername_authentication_usernameNotFound() throws Exception {
        embeddedLdapRule.ldapConnection();
        TestObserver<User> testObserver = authenticationProvider
            .loadUserByUsername(
                new Authentication() {
                    @Override
                    public Object getCredentials() {
                        return "benspassword";
                    }

                    @Override
                    public Object getPrincipal() {
                        return "unknownUsername";
                    }

                    @Override
                    public AuthenticationContext getContext() {
                        return null;
                    }
                }
            )
            .test();

        testObserver.assertError(BadCredentialsException.class);
    }
}
