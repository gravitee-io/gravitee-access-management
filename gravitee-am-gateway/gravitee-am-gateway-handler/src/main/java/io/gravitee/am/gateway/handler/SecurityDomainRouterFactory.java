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
package io.gravitee.am.gateway.handler;

import io.gravitee.am.gateway.handler.audit.AuditReporterManager;
import io.gravitee.am.gateway.handler.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.email.EmailManager;
import io.gravitee.am.gateway.handler.form.FormManager;
import io.gravitee.am.gateway.handler.oauth2.client.ClientSyncService;
import io.gravitee.am.gateway.handler.oauth2.granter.extensiongrant.ExtensionGrantManager;
import io.gravitee.am.gateway.handler.oauth2.scope.ScopeManager;
import io.gravitee.am.gateway.handler.spring.HandlerConfiguration;
import io.gravitee.am.gateway.handler.vertx.VertxSecurityDomainHandler;
import io.gravitee.am.model.Domain;
import io.gravitee.common.component.LifecycleComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.ConfigurableEnvironment;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SecurityDomainRouterFactory {

    private final Logger logger = LoggerFactory.getLogger(SecurityDomainRouterFactory.class);

    @Autowired
    private ApplicationContext gatewayApplicationContext;

    public VertxSecurityDomainHandler create(Domain domain) {
        if (domain.isEnabled()) {
            AbstractApplicationContext internalApplicationContext = createApplicationContext(domain);
            startComponents(internalApplicationContext);
            VertxSecurityDomainHandler handler = internalApplicationContext.getBean(VertxSecurityDomainHandler.class);
            return handler;
        } else {
            logger.warn("Domain is disabled !");
            return null;
        }
    }

    AbstractApplicationContext createApplicationContext(Domain domain) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.setParent(gatewayApplicationContext);
        context.setClassLoader(new ReactorHandlerClassLoader(gatewayApplicationContext.getClassLoader()));
        context.setEnvironment((ConfigurableEnvironment) gatewayApplicationContext.getEnvironment());

        PropertySourcesPlaceholderConfigurer configurer = new PropertySourcesPlaceholderConfigurer();
        configurer.setIgnoreUnresolvablePlaceholders(true);
        configurer.setEnvironment(gatewayApplicationContext.getEnvironment());
        context.addBeanFactoryPostProcessor(configurer);

        context.getBeanFactory().registerSingleton("domain", domain);
        context.register(HandlerConfiguration.class);
        context.setId("context-domain-" + domain.getId());
        context.refresh();

        return context;
    }

    private static class ReactorHandlerClassLoader extends URLClassLoader {
        public ReactorHandlerClassLoader(ClassLoader parent) {
            super(new URL[]{}, parent);
        }
    }

    private void startComponents(ApplicationContext applicationContext) {
        // register components that require event listener feature
        List<Class<? extends LifecycleComponent>> components = new ArrayList<>();
        components.add(ClientSyncService.class);
        components.add(CertificateManager.class);
        components.add(IdentityProviderManager.class);
        components.add(ExtensionGrantManager.class);
        components.add(FormManager.class);
        components.add(EmailManager.class);
        components.add(ScopeManager.class);
        components.add(AuditReporterManager.class);

        components.forEach(componentClass -> {
            LifecycleComponent lifecyclecomponent = applicationContext.getBean(componentClass);
            try {
                lifecyclecomponent.start();
            } catch (Exception e) {
                logger.error("An error occurs while starting component {}", componentClass.getSimpleName(), e);
            }
        });
    }
}
