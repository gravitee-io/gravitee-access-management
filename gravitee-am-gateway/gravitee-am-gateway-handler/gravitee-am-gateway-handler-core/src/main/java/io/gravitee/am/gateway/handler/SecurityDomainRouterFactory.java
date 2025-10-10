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

import io.gravitee.am.gateway.handler.common.alert.AlertEventProcessor;
import io.gravitee.am.gateway.handler.common.audit.AuditReporterManager;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.auth.listener.AuthenticationEventListener;
import io.gravitee.am.gateway.handler.common.authorizationengine.impl.AuthorizationEngineManagerImpl;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.client.ClientManager;
import io.gravitee.am.gateway.handler.common.email.EmailManager;
import io.gravitee.am.gateway.handler.common.factor.FactorManager;
import io.gravitee.am.gateway.handler.common.flow.FlowManager;
import io.gravitee.am.gateway.handler.common.password.PasswordPolicyManager;
import io.gravitee.am.gateway.handler.common.role.impl.InMemoryRoleManager;
import io.gravitee.am.gateway.handler.common.service.RevokeTokenGatewayService;
import io.gravitee.am.gateway.handler.common.service.mfa.UserEventListener;
import io.gravitee.am.gateway.handler.common.service.mfa.impl.DomainEventListenerImpl;
import io.gravitee.am.gateway.handler.common.utils.ConfigurationHelper;
import io.gravitee.am.gateway.handler.manager.authdevice.notifier.AuthenticationDeviceNotifierManager;
import io.gravitee.am.gateway.handler.manager.botdetection.BotDetectionManager;
import io.gravitee.am.gateway.handler.manager.deviceidentifiers.DeviceIdentifierManager;
import io.gravitee.am.gateway.handler.manager.dictionary.I18nDictionaryManager;
import io.gravitee.am.gateway.handler.manager.domain.CrossDomainManager;
import io.gravitee.am.gateway.handler.manager.form.FormManager;
import io.gravitee.am.gateway.handler.manager.resource.ResourceManager;
import io.gravitee.am.gateway.handler.manager.theme.ThemeManager;
import io.gravitee.am.gateway.handler.spring.HandlerConfiguration;
import io.gravitee.am.gateway.handler.vertx.VertxSecurityDomainHandler;
import io.gravitee.am.model.Domain;
import io.gravitee.am.gateway.handler.manager.ComponentInitializer;
import io.gravitee.common.component.LifecycleComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

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

    @Autowired
    private Environment environment;

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
        configurer.setIgnoreUnresolvablePlaceholders(false);
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
        components.add(IdentityProviderManager.class);
        components.add(FormManager.class);
        components.add(EmailManager.class);
        components.add(AuditReporterManager.class);
        components.add(FlowManager.class);
        components.add(AuthenticationEventListener.class);
        components.add(AlertEventProcessor.class);
        components.add(FactorManager.class);
        components.add(ResourceManager.class);
        components.add(BotDetectionManager.class);
        components.add(CrossDomainManager.class);
        components.add(ClientManager.class);
        components.add(CertificateManager.class);
        components.add(DeviceIdentifierManager.class);
        components.add(AuthenticationDeviceNotifierManager.class);
        components.add(I18nDictionaryManager.class);
        components.add(ThemeManager.class);
        components.add(PasswordPolicyManager.class);
        components.add(RevokeTokenGatewayService.class);
        components.add(UserEventListener.class);
        components.add(DomainEventListenerImpl.class);
        components.add(AuthorizationEngineManagerImpl.class);

        if (ConfigurationHelper.useInMemoryRoleAndGroupManager(environment)) {
            components.add(InMemoryRoleManager.class);
            // FIXME: sync process can not be done anymore, need to convert as a classical cache.
            //        Since the first implementation of the DataPlane split, groups are managed on the GW
            //        as consequence Sync is not possible.
            //        we may have to rethink the way users are linked to the group to keep track of the groups into the user profile
            //        so the Group can be request only of the user profile has at least one group and group can be cached for a short living time
            // components.add(InMemoryGroupManager.class);
        }

        ComponentInitializer.builder()
                .components(components)
                .applicationContext(applicationContext)
                .build()
                .initialize();
    }


}
