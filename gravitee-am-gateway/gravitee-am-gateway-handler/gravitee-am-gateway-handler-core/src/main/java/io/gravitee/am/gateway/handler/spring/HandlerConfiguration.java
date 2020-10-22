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
package io.gravitee.am.gateway.handler.spring;

import io.gravitee.am.gateway.handler.common.spring.CommonConfiguration;
import io.gravitee.am.gateway.handler.factor.FactorManager;
import io.gravitee.am.gateway.handler.factor.impl.FactorManagerImpl;
import io.gravitee.am.gateway.handler.form.FormManager;
import io.gravitee.am.gateway.handler.form.impl.FormManagerImpl;
import io.gravitee.am.gateway.handler.root.spring.RootConfiguration;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.WebAuthnFactory;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.store.RepositoryCredentialStore;
import io.gravitee.am.gateway.handler.vertx.spring.SecurityDomainRouterConfiguration;
import io.gravitee.am.service.authentication.crypto.password.PasswordEncoder;
import io.gravitee.am.service.authentication.crypto.password.bcrypt.BCryptPasswordEncoder;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.Router;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
@Import({
        CommonConfiguration.class,
        RootConfiguration.class,
        SecurityDomainRouterConfiguration.class
})
public class HandlerConfiguration {

    @Autowired
    private Vertx vertx;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public FormManager pageManager() {
        return new FormManagerImpl();
    }

    @Bean
    public FactorManager factorManager() {
        return new FactorManagerImpl();
    }

    @Bean
    public RepositoryCredentialStore credentialStore() {
        return new RepositoryCredentialStore();
    }

    @Bean
    public WebAuthnFactory webAuthn() {
        return new WebAuthnFactory();
    }

    @Bean
    public Router router() {
        return Router.router(vertx);
    }

}
