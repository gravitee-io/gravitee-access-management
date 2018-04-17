package io.gravitee.am.gateway.handler.vertx.spring;

import io.gravitee.am.gateway.handler.vertx.VertxSecurityDomainHandler;
import io.gravitee.am.gateway.handler.vertx.oauth2.OAuth2Router;
import io.gravitee.am.gateway.handler.vertx.oidc.OIDCRouter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class SecurityDomainRouterConfiguration {

    @Bean
    public VertxSecurityDomainHandler securityDomainHandler() {
        return new VertxSecurityDomainHandler();
    }

    @Bean
    public OIDCRouter oidcRouter() {
        return new OIDCRouter();
    }

    @Bean
    public OAuth2Router oAuth2Router() {
        return new OAuth2Router();
    }
}
