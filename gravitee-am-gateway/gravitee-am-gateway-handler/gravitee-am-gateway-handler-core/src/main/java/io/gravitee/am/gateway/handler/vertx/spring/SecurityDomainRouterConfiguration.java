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

import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.spring.FreemarkerConfiguration;
import io.gravitee.am.gateway.handler.common.user.UserGatewayService;
import io.gravitee.am.gateway.handler.manager.subject.SubjectManagerV1;
import io.gravitee.am.gateway.handler.manager.subject.SubjectManagerV2;
import io.gravitee.am.gateway.handler.vertx.VertxSecurityDomainHandler;
import io.gravitee.am.gateway.handler.vertx.view.thymeleaf.ThymeleafConfiguration;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.DomainVersion;
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
        FreemarkerConfiguration.class
})
@Configuration
public class SecurityDomainRouterConfiguration {

    @Bean
    public VertxSecurityDomainHandler securityDomainHandler() {
        return new VertxSecurityDomainHandler();
    }

    @Bean
    public SubjectManager subjectManager(Domain domain, UserGatewayService userService) {
        if (domain.getVersion() == DomainVersion.V1_0) {
            return new SubjectManagerV1(userService);
        } else {
            return new SubjectManagerV2(userService, domain);
        }
    }
}
