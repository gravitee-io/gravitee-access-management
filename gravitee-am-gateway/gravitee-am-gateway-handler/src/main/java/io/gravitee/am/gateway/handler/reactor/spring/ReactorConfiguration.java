package io.gravitee.am.gateway.handler.reactor.spring;

import io.gravitee.am.gateway.handler.SecurityDomainRouterFactory;
import io.gravitee.am.gateway.handler.reactor.Reactor;
import io.gravitee.am.gateway.handler.reactor.ReactorHandlerResolver;
import io.gravitee.am.gateway.handler.reactor.SecurityDomainHandlerRegistry;
import io.gravitee.am.gateway.handler.reactor.impl.DefaultReactor;
import io.gravitee.am.gateway.handler.reactor.impl.DefaultReactorHandlerResolver;
import io.gravitee.am.gateway.handler.reactor.impl.DefaultSecurityDomainHandlerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
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
    public ReactorHandlerResolver reactorHandlerResolver() {
        return new DefaultReactorHandlerResolver();
    }

    @Bean
    public SecurityDomainRouterFactory securityDomainRouterFactory() {
        return new SecurityDomainRouterFactory();
    }
}
