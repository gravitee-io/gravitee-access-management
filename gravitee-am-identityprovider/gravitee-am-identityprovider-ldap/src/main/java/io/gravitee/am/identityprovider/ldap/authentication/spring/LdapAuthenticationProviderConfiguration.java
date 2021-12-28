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

import io.gravitee.am.identityprovider.ldap.LdapIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.ldap.common.authentication.CompareAuthenticationHandler;
import io.gravitee.am.identityprovider.ldap.common.authentication.GroupSearchEntryHandler;
import io.gravitee.am.identityprovider.ldap.common.authentication.encoding.*;
import io.gravitee.am.identityprovider.ldap.common.pool.CustomBlockingConnectionPool;
import org.ldaptive.*;
import org.ldaptive.auth.*;
import org.ldaptive.handler.SearchEntryHandler;
import org.ldaptive.pool.*;
import org.ldaptive.provider.unboundid.UnboundIDProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class LdapAuthenticationProviderConfiguration {

    private static final String LDAP_SEPARATOR = ",";

    @Autowired
    private LdapIdentityProviderConfiguration configuration;

    /**
     * Bind OPERATIONS configuration
     * We must split the connections for search and bind operations because bind operation change the connection context to the last user authenticated
     * If the user has no role to search in the LDAP/AD directory the authentication will failed for the next users
     */

    @Bean
    public ConnectionPool bindConnectionPool() {
        PoolConfig poolConfig = new PoolConfig();
        poolConfig.setMinPoolSize(configuration.getMinPoolSize());
        poolConfig.setMaxPoolSize(configuration.getMaxPoolSize());
        poolConfig.setValidatePeriodically(true);
        BlockingConnectionPool connectionPool =
                new CustomBlockingConnectionPool(poolConfig, (DefaultConnectionFactory) bindConnectionFactory(), configuration.getMaxPoolRetries());
        connectionPool.setValidator(new SearchValidator());
        return connectionPool;
    }

    @Bean
    public PooledConnectionFactory bindPooledConnectionFactory() {
        return new PooledConnectionFactory(bindConnectionPool());
    }

    @Bean
    public ConnectionFactory bindConnectionFactory() {
        UnboundIDProvider unboundIDProvider = new UnboundIDProvider();
        DefaultConnectionFactory connectionFactory = new DefaultConnectionFactory();
        connectionFactory.setConnectionConfig(connectionConfig());
        connectionFactory.setProvider(unboundIDProvider);
        return connectionFactory;
    }

    /**
     * Search OPERATIONS configuration
     * We must split the connections for search and bind operations because bind operation change the connection context to the last user authenticated
     * If the user has no role to search in the LDAP/AD directory the authentication will failed for the next users
     */

    @Bean
    public ConnectionPool searchConnectionPool() {
        PoolConfig poolConfig = new PoolConfig();
        poolConfig.setMinPoolSize(configuration.getMinPoolSize());
        poolConfig.setMaxPoolSize(configuration.getMaxPoolSize());
        poolConfig.setValidatePeriodically(true);
        BlockingConnectionPool connectionPool =
                new CustomBlockingConnectionPool(poolConfig, (DefaultConnectionFactory) searchConnectionFactory(), configuration.getMaxPoolRetries());
        connectionPool.setValidator(new SearchValidator());
        return connectionPool;
    }

    @Bean
    public PooledConnectionFactory searchPooledConnectionFactory() {
        return new PooledConnectionFactory(searchConnectionPool());
    }

    @Bean
    public ConnectionFactory searchConnectionFactory() {
        UnboundIDProvider unboundIDProvider = new UnboundIDProvider();
        DefaultConnectionFactory connectionFactory = new DefaultConnectionFactory();
        connectionFactory.setConnectionConfig(connectionConfig());
        connectionFactory.setProvider(unboundIDProvider);
        return connectionFactory;
    }

    @Bean
    public ConnectionConfig connectionConfig() {
        ConnectionConfig connectionConfig = new ConnectionConfig();
        connectionConfig.setConnectTimeout(Duration.ofMillis(configuration.getConnectTimeout()));
        connectionConfig.setResponseTimeout(Duration.ofMillis(configuration.getResponseTimeout()));
        connectionConfig.setLdapUrl(configuration.getContextSourceUrl());
        connectionConfig.setUseStartTLS(configuration.isUseStartTLS());
        BindConnectionInitializer connectionInitializer =
                new BindConnectionInitializer(configuration.getContextSourceUsername(), new Credential(configuration.getContextSourcePassword()));
        connectionConfig.setConnectionInitializer(connectionInitializer);
        return connectionConfig;
    }

    @Bean("userSearchExecutor")
    public SearchExecutor userSearchExecutor() {
        SearchExecutor searchExecutor = new SearchExecutor();
        searchExecutor.setBaseDn(configuration.getContextSourceBase());
        String userSearchBase = configuration.getUserSearchBase();
        if (userSearchBase != null && !userSearchBase.isEmpty()) {
            searchExecutor.setBaseDn(userSearchBase + LDAP_SEPARATOR + searchExecutor.getBaseDn());
        }
        searchExecutor.setSearchFilter(new SearchFilter(configuration.getUserSearchFilter()));
        if (configuration.isFetchGroups()) {
            searchExecutor.setSearchEntryHandlers(groupSearchEntryHandler());
        }
        return searchExecutor;
    }

    @Bean
    public Authenticator authenticator() {
        PooledSearchDnResolver dnResolver = new PooledSearchDnResolver(searchPooledConnectionFactory());
        String userSearchBase = configuration.getUserSearchBase();
        dnResolver.setBaseDn(configuration.getContextSourceBase());
        if (userSearchBase != null && !userSearchBase.isEmpty()) {
            dnResolver.setBaseDn(userSearchBase + LDAP_SEPARATOR + dnResolver.getBaseDn());
        }
        // unable *={0} authentication filter (ldaptive use *={user})
        dnResolver.setUserFilter(configuration.getUserSearchFilter().replaceAll("\\{0\\}", "{user}"));
        dnResolver.setSubtreeSearch(true);
        dnResolver.setAllowMultipleDns(false);

        AbstractAuthenticationHandler authHandler =
                (configuration.getPasswordAlgorithm() == null)
                        ? new PooledBindAuthenticationHandler(bindPooledConnectionFactory())
                        : new CompareAuthenticationHandler(searchPooledConnectionFactory(), passwordEncoder(configuration.getPasswordAlgorithm()), binaryToTextEncoder(), configuration);


        PooledSearchEntryResolver pooledSearchEntryResolver = new PooledSearchEntryResolver(searchPooledConnectionFactory());
        if (configuration.isFetchGroups()) {
            pooledSearchEntryResolver.setSearchEntryHandlers(groupSearchEntryHandler());
        }

        Authenticator auth = new Authenticator(dnResolver, authHandler);
        auth.setEntryResolver(pooledSearchEntryResolver);
        return auth;
    }

    @Bean
    public BinaryToTextEncoder binaryToTextEncoder() {
        if (configuration.getPasswordEncoding() != null) {
            switch (configuration.getPasswordEncoding()) {
                case "Base64":
                    return new Base64Encoder();
                case "Hex":
                    return new HexEncoder();
            }
        }
        return new NoneEncoder();
    }

    private SearchEntryHandler groupSearchEntryHandler() {
        GroupSearchEntryHandler groupSearchEntryHandler = new GroupSearchEntryHandler(this.configuration.isRecursiveGroupFetch());
        String groupSearchBase = configuration.getGroupSearchBase();
        if (groupSearchBase != null && !groupSearchBase.isEmpty()) {
            groupSearchEntryHandler.setBaseDn(groupSearchBase + LDAP_SEPARATOR + configuration.getContextSourceBase());
        } else {
            groupSearchEntryHandler.setBaseDn(configuration.getContextSourceBase());
        }
        groupSearchEntryHandler.setSearchFilter(configuration.getGroupSearchFilter());
        groupSearchEntryHandler.setReturnAttributes(new String[] { configuration.getGroupRoleAttribute() });
        groupSearchEntryHandler.setSearchScope(SearchScope.SUBTREE);

        return groupSearchEntryHandler;
    }

    private PasswordEncoder passwordEncoder(String passwordAlgorithm) {
        if ("MD5".equals(passwordAlgorithm)) {
            return new MD5PasswordEncoder();
        }

        if ("SHA".equals(passwordAlgorithm)) {
            return new SHAPasswordEncoder();
        }

        if (passwordAlgorithm.startsWith("SHA")) {
            if (passwordAlgorithm.endsWith("+MD5")) {
                PasswordEncoder passwordEncoder = new SHAMD5PasswordEncoder();
                String parts = passwordAlgorithm.split(Pattern.quote("+"))[0];
                String[] strengthParts = parts.split("-");
                if (strengthParts.length == 2) {
                    ((SHAMD5PasswordEncoder) passwordEncoder).setStrength(Integer.valueOf(parts.split("-")[1]));
                }
                // set Password encoding if exists
                ((SHAMD5PasswordEncoder) passwordEncoder).setBinaryToTextEncoder(binaryToTextEncoder());
                return passwordEncoder;
            } else {
                return new SHAPasswordEncoder(Integer.valueOf(passwordAlgorithm.split("-")[1]));
            }
        }

        throw new IllegalArgumentException("Unknown password encoder algorithm");
    }
}
