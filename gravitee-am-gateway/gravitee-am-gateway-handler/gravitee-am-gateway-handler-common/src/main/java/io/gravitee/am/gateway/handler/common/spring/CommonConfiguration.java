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
package io.gravitee.am.gateway.handler.common.spring;

import io.gravitee.am.gateway.handler.common.audit.AuditReporterManager;
import io.gravitee.am.gateway.handler.common.audit.impl.AuditReporterManagerImpl;
import io.gravitee.am.gateway.handler.common.auth.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.auth.UserAuthenticationService;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.auth.idp.impl.IdentityProviderManagerImpl;
import io.gravitee.am.gateway.handler.common.auth.impl.UserAuthenticationManagerImpl;
import io.gravitee.am.gateway.handler.common.auth.impl.UserAuthenticationServiceImpl;
import io.gravitee.am.gateway.handler.common.authentication.listener.AuthenticationEventListener;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.certificate.CertificateProviderManager;
import io.gravitee.am.gateway.handler.common.certificate.impl.CertificateManagerImpl;
import io.gravitee.am.gateway.handler.common.certificate.impl.CertificateProviderManagerImpl;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.client.impl.ClientSyncServiceImpl;
import io.gravitee.am.gateway.handler.common.jwe.JWEService;
import io.gravitee.am.gateway.handler.common.jwe.impl.JWEServiceImpl;
import io.gravitee.am.gateway.handler.common.jwk.JWKService;
import io.gravitee.am.gateway.handler.common.jwk.impl.JWKServiceImpl;
import io.gravitee.am.gateway.handler.common.jws.JWSService;
import io.gravitee.am.gateway.handler.common.jws.impl.JWSServiceImpl;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.jwt.impl.JWTServiceImpl;
import io.gravitee.am.gateway.handler.common.oauth2.IntrospectionTokenService;
import io.gravitee.am.gateway.handler.common.oauth2.impl.IntrospectionTokenServiceImpl;
import io.gravitee.am.gateway.handler.common.policy.PolicyManager;
import io.gravitee.am.gateway.handler.common.policy.impl.PolicyManagerImpl;
import io.gravitee.am.gateway.handler.common.spring.web.WebConfiguration;
import io.gravitee.am.gateway.handler.common.user.UserManager;
import io.gravitee.am.gateway.handler.common.user.UserService;
import io.gravitee.am.gateway.handler.common.user.UserStore;
import io.gravitee.am.gateway.handler.common.user.impl.InMemoryUserStore;
import io.gravitee.am.gateway.handler.common.user.impl.UserManagerImpl;
import io.gravitee.am.gateway.handler.common.user.impl.UserServiceImpl;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.OAuth2AuthProvider;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.UserAuthProvider;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.impl.OAuth2AuthProviderImpl;
import io.gravitee.am.gateway.handler.common.vertx.web.auth.provider.impl.UserAuthProviderImpl;
import io.gravitee.am.gateway.handler.context.ExecutionContextFactory;
import io.gravitee.am.gateway.handler.context.TemplateVariableProviderFactory;
import io.gravitee.am.gateway.handler.context.spring.ContextConfiguration;
import io.gravitee.am.gateway.policy.spring.PolicyConfiguration;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
@Import({WebConfiguration.class, PolicyConfiguration.class, ContextConfiguration.class})
public class CommonConfiguration {

    @Autowired
    private Environment environment;

    @Autowired
    private Vertx vertx;

    @Bean
    public WebClient webClient() {
        WebClientOptions options = new WebClientOptions()
                .setConnectTimeout(Integer.valueOf(environment.getProperty("oidc.http.connectionTimeout", "10")) * 1000)
                .setMaxPoolSize(Integer.valueOf(environment.getProperty("oidc.http.pool.maxTotalConnection", "200")))
                .setTrustAll(Boolean.valueOf(environment.getProperty("oidc.http.client.trustAll", "true")));

        return WebClient.create(vertx,options);
    }

    @Bean
    public IdentityProviderManager identityProviderManager() {
        return new IdentityProviderManagerImpl();
    }

    @Bean
    public UserAuthenticationManager userAuthenticationManager() {
        return new UserAuthenticationManagerImpl();
    }

    @Bean
    public UserAuthenticationService userAuthenticationService() {
        return new UserAuthenticationServiceImpl();
    }

    @Bean
    public AuditReporterManager auditReporterManager() {
        return new AuditReporterManagerImpl();
    }

    @Bean
    public CertificateManager certificateManager() {
        return new CertificateManagerImpl();
    }

    @Bean
    public CertificateProviderManager certificateProviderManager() {
        return new CertificateProviderManagerImpl();
    }

    @Bean
    public JWEService jweService() {
        return new JWEServiceImpl();
    }

    @Bean
    public JWTService jwtService() {
        return new JWTServiceImpl();
    }

    @Bean
    public JWKService jwkService() {
        return new JWKServiceImpl();
    }

    @Bean
    public JWSService jwsService() {
        return new JWSServiceImpl();
    }

    @Bean
    public ClientSyncService clientService() {
        return new ClientSyncServiceImpl();
    }

    @Bean
    public UserAuthProvider userAuthProvider() {
        return new UserAuthProviderImpl();
    }

    @Bean
    public OAuth2AuthProvider oAuth2AuthProvider() {
        return new OAuth2AuthProviderImpl();
    }

    @Bean
    public AuthenticationEventListener authenticationEventListener() {
        return new AuthenticationEventListener();
    }

    @Bean
    public PolicyManager policyManager() {
        return new PolicyManagerImpl();
    }

    @Bean
    public ExecutionContextFactory executionContextFactory() {
        return new ExecutionContextFactory();
    }

    @Bean
    public TemplateVariableProviderFactory templateVariableProviderFactory() {
        return new TemplateVariableProviderFactory();
    }

    @Bean
    public IntrospectionTokenService introspectiontokenservice() {
        return new IntrospectionTokenServiceImpl();
    }

    @Bean
    public UserService userService() {
        return new UserServiceImpl();
    }

    @Bean
    public UserManager userManager() {
        UserStore userStore = new InMemoryUserStore(vertx, environment.getProperty("http.cookie.session.timeout", Long.class, io.vertx.reactivex.ext.web.handler.SessionHandler.DEFAULT_SESSION_TIMEOUT));
        return new UserManagerImpl(userStore);
    }
}
