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

import io.gravitee.am.gateway.handler.oauth2.provider.approval.DefaultApprovalStore;
import io.gravitee.am.gateway.handler.oauth2.provider.approval.DefaultApprovalStoreUserApprovalHandler;
import io.gravitee.am.gateway.handler.oauth2.provider.jwt.CustomJwtAccessTokenConverter;
import io.gravitee.am.gateway.handler.oauth2.provider.request.CustomOAuth2RequestFactory;
import io.gravitee.am.gateway.handler.oauth2.provider.security.web.authentication.ClientAwareAuthenticationDetailsSource;
import io.gravitee.am.gateway.handler.oauth2.provider.token.CustomTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.security.ExtensionGrantManager;
import io.gravitee.am.model.Domain;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.config.annotation.configurers.ClientDetailsServiceConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configuration.AuthorizationServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableAuthorizationServer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerEndpointsConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.ClientDetailsService;
import org.springframework.security.oauth2.provider.CompositeTokenGranter;
import org.springframework.security.oauth2.provider.TokenGranter;
import org.springframework.security.oauth2.provider.approval.ApprovalStore;
import org.springframework.security.oauth2.provider.approval.ApprovalStoreUserApprovalHandler;
import org.springframework.security.oauth2.provider.approval.UserApprovalHandler;
import org.springframework.security.oauth2.provider.client.ClientDetailsUserDetailsService;
import org.springframework.security.oauth2.provider.code.AuthorizationCodeServices;
import org.springframework.security.oauth2.provider.error.OAuth2AccessDeniedHandler;
import org.springframework.security.oauth2.provider.error.OAuth2AuthenticationEntryPoint;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
@EnableAuthorizationServer
//@Import({CustomAuthorizationServerEndpointsConfiguration.class, CustomAuthorizationServerSecurityConfiguration.class})
public class AuthorizationServerConfiguration extends AuthorizationServerConfigurerAdapter {

    @Autowired
    private Domain domain;

    @Autowired
    private TokenStore tokenStore;

    @Autowired
    private AuthorizationCodeServices authorizationCodeServices;

    /*
    @Autowired
    private UserApprovalHandler userApprovalHandler;
    */

    @Autowired
    @Qualifier("authenticationManagerBean")
    private AuthenticationManager authenticationManager;

    @Autowired
    private ClientDetailsService clientDetailsService;

    @Autowired
    @Qualifier("userDetailsServiceBean")
    private UserDetailsService userDetailsService;

    @Autowired
    private ExtensionGrantManager extensionGrantManager;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    /*
    @Autowired
    private AuthorizationEndpoint authorizationEndpoint;

    @PostConstruct
    public void init() {
        authorizationEndpoint.setUserApprovalPage("forward:/oauth/scope_approval");
        authorizationEndpoint.setErrorPage("forward:/oauth/scope_approval_error");
    }
*/
    /*
    @Bean
    public ScopeApprovalEndpoint approvalEndpoint() {
        ScopeApprovalEndpoint endpoint = new ScopeApprovalEndpoint();
        endpoint.setClientDetailsService(clientDetailsService);
        return endpoint;
    }
    */

    /*
    @Bean
    public ScopeApprovalErrorEndpoint approvalErrorEndpoint() {
        return new ScopeApprovalErrorEndpoint();
    }
    */

    @Override
    public void configure(ClientDetailsServiceConfigurer clients) throws Exception {
        clients.withClientDetails(clientDetailsService);
    }

    @Bean
    public ClientDetailsUserDetailsService clientDetailsUserDetailsService() {
        return new ClientDetailsUserDetailsService(clientDetailsService);
    }

    @Bean
    public AuthenticationManager clientAuthenticationManager() {
        DaoAuthenticationProvider clientAuthenticationProvider = new DaoAuthenticationProvider();
        clientAuthenticationProvider.setUserDetailsService(clientDetailsUserDetailsService());
        clientAuthenticationProvider.setHideUserNotFoundExceptions(false);
        return new ProviderManager(Collections.singletonList(clientAuthenticationProvider));
    }

    @Bean
    public JwtAccessTokenConverter accessTokenConverter() {
        return new CustomJwtAccessTokenConverter();
    }

    @Override
    public void configure(AuthorizationServerEndpointsConfigurer endpoints) throws Exception {
        endpoints
            .tokenStore(tokenStore)
            .accessTokenConverter(accessTokenConverter())
            .reuseRefreshTokens(false)
            .userDetailsService(userDetailsService)
            .authorizationCodeServices(authorizationCodeServices)
            .requestFactory(new CustomOAuth2RequestFactory(clientDetailsService))
            .authenticationManager(authenticationManager)
            .userApprovalHandler(userApprovalHandler())
            .approvalStore(approvalStore())
            .tokenGranter(tokenGranter(endpoints))
                .addInterceptor(new HandlerInterceptorAdapter() {
                    @Override
                    public boolean preHandle(HttpServletRequest hsr, HttpServletResponse rs, Object o) throws Exception {
                        rs.setHeader("Access-Control-Allow-Origin", hsr.getHeader("origin"));
                        rs.setHeader("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
                        rs.setHeader("Access-Control-Max-Age", "3600");
                        rs.setHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization");
                        return true;
                    }
                });
    }

    @Override
    public void configure(AuthorizationServerSecurityConfigurer oauthServer) throws Exception {
        oauthServer
                .realm("realm/" + domain.getName())
                .checkTokenAccess("isAuthenticated()")
                .accessDeniedHandler(oAuth2AccessDeniedHandler())
                .allowFormAuthenticationForClients()
                .authenticationEntryPoint(oAuth2AuthenticationEntryPoint());

        BasicAuthenticationFilter basicAuthenticationFilter = new BasicAuthenticationFilter(clientAuthenticationManager(), oAuth2AuthenticationEntryPoint());
        basicAuthenticationFilter.setAuthenticationDetailsSource(new ClientAwareAuthenticationDetailsSource());
        oauthServer.addTokenEndpointAuthenticationFilter(basicAuthenticationFilter);
    }

    @Bean
    public OAuth2AuthenticationEntryPoint oAuth2AuthenticationEntryPoint() {
        OAuth2AuthenticationEntryPoint oAuth2AuthenticationEntryPoint = new OAuth2AuthenticationEntryPoint();
        oAuth2AuthenticationEntryPoint.setRealmName("realm/" + domain.getName());
        oAuth2AuthenticationEntryPoint.setTypeName("Basic");

        return oAuth2AuthenticationEntryPoint;
    }

    @Bean
    public OAuth2AccessDeniedHandler oAuth2AccessDeniedHandler() {
        return new OAuth2AccessDeniedHandler();
    }

    private TokenGranter tokenGranter(final AuthorizationServerEndpointsConfigurer endpoints) {
        List<TokenGranter> granters = new ArrayList<>(Arrays.asList(endpoints.getTokenGranter()));
        extensionGrantManager.providers().forEach((id, tokenGranterProvider) -> {
            CustomTokenGranter customTokenGranter = new CustomTokenGranter(endpoints.getTokenServices(), endpoints.getClientDetailsService(), endpoints.getOAuth2RequestFactory(), extensionGrantManager.getTokenGranter(id));
            customTokenGranter.setExtensionGrantProvider(tokenGranterProvider);
            customTokenGranter.setAuthenticationEventPublisher(new DefaultAuthenticationEventPublisher(applicationEventPublisher));
            granters.add(customTokenGranter);
        });
        return new CompositeTokenGranter(granters);
    }

    //
    // Approval store
    //

    @Bean
    public UserApprovalHandler userApprovalHandler() {
        DefaultApprovalStoreUserApprovalHandler approvalHandler = new DefaultApprovalStoreUserApprovalHandler();
        approvalHandler.setClientDetailsService(clientDetailsService);
        approvalHandler.setApprovalStore(approvalStore());
        approvalHandler.setRequestFactory(new CustomOAuth2RequestFactory(clientDetailsService));
        return approvalHandler;
    }

    @Bean
    public ApprovalStore approvalStore() {
        return new DefaultApprovalStore();
    }
}
