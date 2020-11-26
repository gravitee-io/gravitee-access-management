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
package io.gravitee.am.gateway.handler.common.spring.web;

import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.*;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.AuthenticationFlowHandlerImpl;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.CookieHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.CookieSessionHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.PolicyChainHandlerImpl;
import io.gravitee.am.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class WebConfiguration {

    @Bean
    public CorsHandlerFactory corsHandler() {
        return new CorsHandlerFactory();
    }

    @Bean
    public CookieSessionHandler sessionHandler(JWTService jwtService, CertificateManager certificateManager, UserService userService) {
        return new CookieSessionHandler(jwtService, certificateManager, userService);
    }

    @Bean
    public SSOSessionHandler ssoSessionHandler(ClientSyncService clientSyncService) {
        return new SSOSessionHandler(clientSyncService);
    }

    @Bean
    public CookieHandler cookieHandler(@Value("${http.cookie.secure:false}") boolean cookieSecure) {
        return new CookieHandler(cookieSecure);
    }

    @Bean
    public CSRFHandlerFactory csrfHandler() {
        return new CSRFHandlerFactory();
    }

    @Bean
    public PolicyChainHandler policyChainHandler() {
        return new PolicyChainHandlerImpl();
    }

    @Bean
    public AuthenticationFlowHandler authenticationFlowHandler() {
        return new AuthenticationFlowHandlerImpl();
    }
}
