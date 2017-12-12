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
package io.gravitee.am.gateway.handler.oauth2;

import io.gravitee.am.gateway.handler.oauth2.authentication.OAuth2LoginUrlAuthenticationEntryPoint;
import io.gravitee.am.gateway.handler.oauth2.filter.CORSFilter;
import io.gravitee.am.gateway.handler.oauth2.handler.CustomLogoutSuccessHandler;
import io.gravitee.am.gateway.handler.oauth2.provider.approval.DefaultApprovalStore;
import io.gravitee.am.gateway.handler.oauth2.provider.code.RepositoryAuthorizationCodeServices;
import io.gravitee.am.gateway.handler.oauth2.provider.request.CustomOAuth2RequestFactory;
import io.gravitee.am.gateway.handler.oauth2.provider.security.ClientBasedAuthenticationProvider;
import io.gravitee.am.gateway.handler.oauth2.provider.security.web.authentication.ClientAwareAuthenticationDetailsSource;
import io.gravitee.am.gateway.handler.oauth2.provider.security.web.authentication.ClientAwareAuthenticationFailureHandler;
import io.gravitee.am.gateway.handler.oauth2.provider.token.AuthenticationKeyGenerator;
import io.gravitee.am.gateway.handler.oauth2.provider.token.DefaultAuthenticationKeyGenerator;
import io.gravitee.am.gateway.handler.oauth2.provider.token.RepositoryTokenStore;
import io.gravitee.am.gateway.handler.oauth2.security.listener.AuthenticationSuccessListener;
import io.gravitee.am.gateway.handler.oauth2.security.web.XForwardedAwareRedirectStrategy;
import io.gravitee.am.gateway.handler.oauth2.userdetails.CustomUserDetailsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.provider.ClientDetailsService;
import org.springframework.security.oauth2.provider.approval.*;
import org.springframework.security.oauth2.provider.code.AuthorizationCodeServices;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import javax.servlet.Filter;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
@EnableWebSecurity
public class OAuth2SecurityConfiguration extends WebSecurityConfigurerAdapter {

    /**
     * Logger
     */
    private final Logger logger = LoggerFactory.getLogger(OAuth2SecurityConfiguration.class);

    @Autowired
    private ClientDetailsService clientDetailsService;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    protected void globalUserDetails(AuthenticationManagerBuilder auth) throws Exception {
        logger.info("Loading identity providers to handle user authentication");

        // By default we are associating users added to the domain
        auth.authenticationProvider(userAuthenticationProvider());
        auth.authenticationEventPublisher(new DefaultAuthenticationEventPublisher(applicationEventPublisher));
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.
            requestMatchers()
                .antMatchers("/oauth/**", "/authorize", "/login", "/logout")
                .and()
            .authorizeRequests()
                .antMatchers(HttpMethod.OPTIONS, "**").permitAll()
                .antMatchers("/login").permitAll()
                .anyRequest().authenticated()
                .and()
            .formLogin()
                .authenticationDetailsSource(new ClientAwareAuthenticationDetailsSource())
                .failureHandler(authenticationFailureHandler())
                .successHandler(authenticationSuccessHandler())
                .permitAll()
                .and()
            .logout()
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                .logoutSuccessHandler(new CustomLogoutSuccessHandler())
                .and()
            .exceptionHandling()
                .authenticationEntryPoint(new OAuth2LoginUrlAuthenticationEntryPoint("/login"))
                .and()
            .addFilterAfter(corsFilter(), AbstractPreAuthenticatedProcessingFilter.class);

    }

    @Override
    @Bean
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }

    @Override
    @Bean
    public UserDetailsService userDetailsServiceBean() throws Exception {
        // Do not use userDetailsService from WebSecurityConfigurerAdapter because this later
        // register only the latest UserDetailsService even we have multiple authentication providers.
        return new CustomUserDetailsService();
    }

    @Bean
    public TokenStore tokenStore() {
        return new RepositoryTokenStore();
    }

    @Bean
    public AuthenticationKeyGenerator authenticationKeyGenerator() {
        return new DefaultAuthenticationKeyGenerator();
    }

    @Bean
    public AuthorizationCodeServices authorizationCodeServices() {
        return new RepositoryAuthorizationCodeServices();
    }

    @Bean
    public ClientBasedAuthenticationProvider userAuthenticationProvider() {
        return new ClientBasedAuthenticationProvider();
    }

    /*
    @Bean
    @Autowired
    public TokenStoreUserApprovalHandler userApprovalHandler(TokenStore tokenStore){
        TokenStoreUserApprovalHandler handler = new TokenStoreUserApprovalHandler();
        handler.setTokenStore(tokenStore);
        handler.setRequestFactory(new CustomOAuth2RequestFactory(clientDetailsService));
        handler.setClientDetailsService(clientDetailsService);
        return handler;
    }

    @Bean
    @Autowired
    public ApprovalStore approvalStore(TokenStore tokenStore) throws Exception {
        TokenApprovalStore store = new TokenApprovalStore();
        store.setTokenStore(tokenStore);
        return store;
    }
    */

    @Bean
    public Filter corsFilter() {
        return new CORSFilter();
    }

    @Bean
    public AuthenticationFailureHandler authenticationFailureHandler() {
        return new ClientAwareAuthenticationFailureHandler("/login?error");
    }

    @Bean
    public AuthenticationSuccessListener authenticationSuccessListener() {
        return new AuthenticationSuccessListener();
    }

    private AuthenticationSuccessHandler authenticationSuccessHandler() {
        SavedRequestAwareAuthenticationSuccessHandler successHandler = new SavedRequestAwareAuthenticationSuccessHandler();
        successHandler.setRedirectStrategy(new XForwardedAwareRedirectStrategy());
        return successHandler;
    }
}
