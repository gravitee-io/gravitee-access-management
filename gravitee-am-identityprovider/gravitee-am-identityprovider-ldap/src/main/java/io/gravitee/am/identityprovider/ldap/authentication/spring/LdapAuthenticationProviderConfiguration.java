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
import org.ldaptive.*;
import org.ldaptive.auth.Authenticator;
import org.ldaptive.auth.BindAuthenticationHandler;
import org.ldaptive.auth.SearchDnResolver;
import org.ldaptive.auth.ext.PasswordPolicyAuthenticationResponseHandler;
import org.ldaptive.control.PasswordPolicyControl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

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

    @Bean
    public ConnectionFactory connectionFactory() {
        return new DefaultConnectionFactory(connectionConfig());
    }

    @Bean
    public ConnectionConfig connectionConfig() {
        ConnectionConfig connectionConfig = new ConnectionConfig();
        connectionConfig.setConnectTimeout(Duration.ofMillis(configuration.getConnectTimeout()));
        connectionConfig.setResponseTimeout(Duration.ofMillis(configuration.getResponseTimeout()));
        connectionConfig.setLdapUrl(configuration.getContextSourceUrl());
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
        return searchExecutor;
    }

    @Bean("groupSearchExecutor")
    public SearchExecutor groupSearchExecutor() {
        SearchExecutor searchExecutor = new SearchExecutor();
        searchExecutor.setBaseDn(configuration.getContextSourceBase());
        String groupSearchBase = configuration.getGroupSearchBase();
        if (groupSearchBase != null && !groupSearchBase.isEmpty()) {
            searchExecutor.setBaseDn(groupSearchBase + LDAP_SEPARATOR + searchExecutor.getBaseDn());
        }
        searchExecutor.setSearchFilter(new SearchFilter(configuration.getGroupSearchFilter()));
        searchExecutor.setReturnAttributes(new String[] { configuration.getGroupRoleAttribute() });
        searchExecutor.setSearchScope(SearchScope.SUBTREE);
        return searchExecutor;
    }

    @Bean
    public Authenticator authenticator() {
        SearchDnResolver dnResolver = new SearchDnResolver(connectionFactory());
        dnResolver.setBaseDn(configuration.getContextSourceBase());
        dnResolver.setUserFilter(configuration.getUserSearchFilter());
        dnResolver.setSubtreeSearch(true);
        BindAuthenticationHandler authHandler = new BindAuthenticationHandler(connectionFactory());
        authHandler.setAuthenticationControls(new PasswordPolicyControl());

        Authenticator auth = new Authenticator(dnResolver, authHandler);
        auth.setAuthenticationResponseHandlers(new PasswordPolicyAuthenticationResponseHandler());
        return auth;
    }
}
