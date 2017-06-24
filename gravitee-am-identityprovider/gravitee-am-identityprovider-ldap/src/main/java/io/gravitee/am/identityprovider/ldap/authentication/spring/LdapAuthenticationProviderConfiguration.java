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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ldap.core.support.BaseLdapPathContextSource;
import org.springframework.security.ldap.DefaultSpringSecurityContextSource;
import org.springframework.security.ldap.authentication.BindAuthenticator;
import org.springframework.security.ldap.authentication.LdapAuthenticator;
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch;
import org.springframework.security.ldap.search.LdapUserSearch;
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator;
import org.springframework.security.ldap.userdetails.LdapAuthoritiesPopulator;
import org.springframework.security.ldap.userdetails.LdapUserDetailsMapper;
import org.springframework.security.ldap.userdetails.UserDetailsContextMapper;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class LdapAuthenticationProviderConfiguration {

    @Autowired
    private LdapIdentityProviderConfiguration configuration;

    @Bean
    public BaseLdapPathContextSource contextSource() {
        DefaultSpringSecurityContextSource contextSource = new DefaultSpringSecurityContextSource(configuration.getContextSourceUrl());
        contextSource.setBase(configuration.getContextSourceBase());
        contextSource.setUserDn(configuration.getContextSourceUsername());
        contextSource.setPassword(configuration.getContextSourcePassword());
        contextSource.afterPropertiesSet();
        return contextSource;
    }

    @Bean
    public LdapAuthoritiesPopulator authoritiesPopulator() {
        DefaultLdapAuthoritiesPopulator populator = new DefaultLdapAuthoritiesPopulator(
                contextSource(),
                configuration.getGroupSearchBase());
        populator.setGroupSearchFilter(configuration.getGroupSearchFilter());
        populator.setGroupRoleAttribute(configuration.getGroupRoleAttribute());
        populator.setSearchSubtree(true);
        populator.setRolePrefix("");
        return populator;
    }

    @Bean
    public LdapUserSearch userSearch() {
        return new FilterBasedLdapUserSearch(
                configuration.getUserSearchBase(), configuration.getUserSearchFilter(),
                contextSource());
    }

    @Bean
    public UserDetailsContextMapper userDetailsContextMapper() {
        return new LdapUserDetailsMapper();
    }

    @Bean
    public LdapAuthenticator authenticator() {
        BindAuthenticator authenticator = new BindAuthenticator(contextSource());
        authenticator.setUserSearch(userSearch());
        return authenticator;
    }
}
