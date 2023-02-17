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

import io.gravitee.am.identityprovider.api.*;
import io.gravitee.am.identityprovider.ldap.LdapIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.ldap.authentication.spring.LdapAuthenticationProviderConfiguration;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

import java.util.concurrent.TimeUnit;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@ContextConfiguration(classes = { LdapAuthenticationProviderConfiguration.class,
        LdapComparePasswordAuthenticationProviderTest.LdapAuthenticationConfiguration.class })
public class LdapComparePasswordAuthenticationProviderTest extends LdapAuthenticationProviderTest {

    @Test
    public void shouldLoadUserByUsername_authentication() throws Exception {
        embeddedLdapRule.ldapConnection();

        String credentials = "benspassword";
        String principal = "ben";

        TestObserver<User> testObserver = authenticationProvider.loadUserByUsername(new Authentication() {
            @Override
            public Object getCredentials() {
                return credentials;
            }

            @Override
            public Object getPrincipal() {
                return principal;
            }

            @Override
            public AuthenticationContext getContext() {
                return null;
            }
        }).test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> principal.equals(u.getUsername()));
    }

    @Configuration
    static class LdapAuthenticationConfiguration {

        @Bean
        public LdapIdentityProviderConfiguration configuration() {
            LdapIdentityProviderConfiguration configuration = new LdapIdentityProviderConfiguration();

            configuration.setContextSourceUsername("uid=bob,ou=people,dc=example,dc=org");
            configuration.setContextSourcePassword("bobspassword");
            configuration.setContextSourceBase("dc=example,dc=org");
            configuration.setContextSourceUrl("ldap://localhost:61000");

            configuration.setUserSearchBase("ou=people");
            configuration.setUserSearchFilter("uid={0}");

            configuration.setGroupSearchBase("ou=GRAVITEE,ou=company,ou=applications");
            configuration.setGroupSearchFilter("member={0}");
            configuration.setGroupRoleAttribute("cn");

            configuration.setPasswordAlgorithm("SHA");
            configuration.setPasswordEncoding("Base64");
            configuration.setHashEncodedByThirdParty(false);
            return configuration;
        }

        @Bean
        public AuthenticationProvider authenticationProvider() {
            return new LdapAuthenticationProvider();
        }

        @Bean
        public IdentityProviderMapper mapper() {
            return new DefaultIdentityProviderMapper();
        }

        @Bean
        public IdentityProviderRoleMapper roleMapper() {
            return new DefaultIdentityProviderRoleMapper();
        }
    }
}
