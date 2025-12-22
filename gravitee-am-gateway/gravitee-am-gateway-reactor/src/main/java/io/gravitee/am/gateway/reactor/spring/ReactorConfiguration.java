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
package io.gravitee.am.gateway.reactor.spring;

import io.gravitee.am.gateway.certificate.spring.CertificateConfiguration;
import io.gravitee.am.gateway.handler.SecurityDomainRouterFactory;
import io.gravitee.am.gateway.reactor.Reactor;
import io.gravitee.am.gateway.reactor.SecurityDomainHandlerRegistry;
import io.gravitee.am.gateway.reactor.SecurityDomainManager;
import io.gravitee.am.gateway.reactor.impl.DefaultReactor;
import io.gravitee.am.gateway.reactor.impl.DefaultSecurityDomainHandlerRegistry;
import io.gravitee.am.gateway.reactor.impl.DefaultSecurityDomainManager;
import io.gravitee.am.gateway.reactor.impl.transaction.TransactionHandlerFactory;
import io.gravitee.am.monitoring.provider.GatewayMetricProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
@Import(CertificateConfiguration.class)
public class ReactorConfiguration {

    @Bean
    public Reactor reactor() {
        return new DefaultReactor();
    }

    @Bean
    public SecurityDomainHandlerRegistry securityDomainHandlerRegistry() {
        return new DefaultSecurityDomainHandlerRegistry();
    }

    @Bean
    public SecurityDomainRouterFactory securityDomainRouterFactory() {
        return new SecurityDomainRouterFactory();
    }

    @Bean
    public TransactionHandlerFactory transactionHandlerFactory() {
        return new TransactionHandlerFactory();
    }

    @Bean
    public SecurityDomainManager securityDomainManager() {
        return new DefaultSecurityDomainManager();
    }

    @Bean
    public GatewayMetricProvider gatewayMetricProvider() {
        return new GatewayMetricProvider();
    }
}
