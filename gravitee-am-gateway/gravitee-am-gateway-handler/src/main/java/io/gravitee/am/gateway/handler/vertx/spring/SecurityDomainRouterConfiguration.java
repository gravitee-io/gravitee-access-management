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
package io.gravitee.am.gateway.handler.vertx.spring;

import io.gravitee.am.gateway.handler.vertx.VertxSecurityDomainHandler;
import io.gravitee.am.gateway.handler.vertx.email.EmailConfiguration;
import io.gravitee.am.gateway.handler.vertx.handler.oauth2.OAuth2Router;
import io.gravitee.am.gateway.handler.vertx.handler.oidc.OIDCRouter;
import io.gravitee.am.gateway.handler.vertx.handler.root.RootRouter;
import io.gravitee.am.gateway.handler.vertx.handler.scim.SCIMRouter;
import io.gravitee.am.gateway.handler.vertx.view.FreeMarkerConfiguration;
import io.gravitee.am.gateway.handler.vertx.view.ThymeleafConfiguration;
import io.gravitee.am.service.spring.ServiceConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Import({
        ThymeleafConfiguration.class,
        FreeMarkerConfiguration.class,
        EmailConfiguration.class,
        ServiceConfiguration.class
})
@Configuration
public class SecurityDomainRouterConfiguration {

    @Bean
    public VertxSecurityDomainHandler securityDomainHandler() {
        return new VertxSecurityDomainHandler();
    }

    @Bean
    public RootRouter rootRouter() {
        return new RootRouter();
    }

    @Bean
    public OIDCRouter oidcRouter() {
        return new OIDCRouter();
    }

    @Bean
    public OAuth2Router oAuth2Router() {
        return new OAuth2Router();
    }

    @Bean
    public SCIMRouter scimRouter() {
        return new SCIMRouter();
    }
}
