package io.gravitee.am.gateway.handler.openid.discovery.spring;

import io.gravitee.am.gateway.handler.openid.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.openid.discovery.impl.OpenIDDiscoveryServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class OpenIDDiscoveryConfiguration {

    @Bean
    public OpenIDDiscoveryService openIDConfigurationService() {
        return new OpenIDDiscoveryServiceImpl();
    }
}
