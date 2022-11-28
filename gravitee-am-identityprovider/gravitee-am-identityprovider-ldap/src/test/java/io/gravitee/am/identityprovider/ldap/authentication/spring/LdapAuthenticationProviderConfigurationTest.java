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
package io.gravitee.am.identityprovider.ldap.authentication.spring;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.ldaptive.ActivePassiveConnectionStrategy;
import org.ldaptive.ConnectionConfig;
import org.ldaptive.ConnectionStrategy;
import org.ldaptive.DefaultConnectionStrategy;

import java.util.Arrays;
import java.util.Collection;

import static io.gravitee.am.identityprovider.ldap.authentication.spring.LdapAuthenticationProviderConfiguration.configureConnection;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

@RunWith(Parameterized.class)
public class LdapAuthenticationProviderConfigurationTest {

    private final String connectionUrl;
    private final String expectedUrl;
    private final Class<ConnectionStrategy> expectedConnectionStrategy;

    public LdapAuthenticationProviderConfigurationTest(String connectionUrl, String expectedUrl, Class<ConnectionStrategy> expectedConnectionStrategy) {
        this.connectionUrl = connectionUrl;
        this.expectedUrl = expectedUrl;
        this.expectedConnectionStrategy = expectedConnectionStrategy;
    }

    @Parameters
    public static Collection<Object[]> ldapConnectionUrls() {
        return Arrays.asList(new Object[][]{
                {
                        "ldap://localhost:6700",
                        "ldap://localhost:6700",
                        DefaultConnectionStrategy.class},
                {
                        "ldap://localhost:6700 ldap://localhost:6800 ldap://localhost:6900",
                        "ldap://localhost:6700 ldap://localhost:6800 ldap://localhost:6900",
                        ActivePassiveConnectionStrategy.class
                },
                {
                        "ldap://localhost:6700        ldap://localhost:6800    ldap://localhost:6900    ",
                        "ldap://localhost:6700 ldap://localhost:6800 ldap://localhost:6900",
                        ActivePassiveConnectionStrategy.class
                },
                {
                        "ldap://localhost:6700,ldap://localhost:6800,ldap://localhost:6900",
                        "ldap://localhost:6700 ldap://localhost:6800 ldap://localhost:6900",
                        ActivePassiveConnectionStrategy.class
                }
        });
    }

    @Test
    public void shouldUseAppropriateConnectionStrategy() {
        ConnectionConfig connectionConfig = new ConnectionConfig();
        configureConnection(connectionUrl, connectionConfig);
        assertThat(connectionConfig.getConnectionStrategy(), instanceOf(expectedConnectionStrategy));
        assertEquals(expectedUrl, connectionConfig.getLdapUrl());
    }
}