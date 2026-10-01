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
package io.gravitee.am.management.handlers.automation.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.management.handlers.automation.resource.AutomationResourceResolver;
import io.gravitee.am.management.handlers.automation.spring.security.AutomationSecurityConfiguration;
import io.gravitee.am.management.service.CertificateServiceProxy;
import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.management.service.IdentityProviderServiceProxy;
import io.gravitee.am.management.service.MaskingMode;
import io.gravitee.am.management.service.ReporterServiceProxy;
import io.gravitee.am.management.service.impl.CertificateServiceProxyImpl;
import io.gravitee.am.management.service.impl.IdentityProviderServiceProxyImpl;
import io.gravitee.am.management.service.impl.ReporterServiceProxyImpl;
import io.gravitee.am.management.service.impl.notifications.notifiers.NotifierSettings;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.CertificatePluginService;
import io.gravitee.am.service.CertificateService;
import io.gravitee.am.service.DataPlaneDefinitionService;
import io.gravitee.am.service.IdentityProviderService;
import io.gravitee.am.service.ReporterService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

/**
 * Root Spring configuration for the Automation API child context.
 * <p>
 * This context is created as a child of the management API's parent application context,
 * inheriting shared service beans (DomainService, EnvironmentService, IdentityProviderService,
 * PermissionService, etc.) while maintaining isolated security configuration.
 * <p>
 * Mirrors the APIM pattern where each API surface (Management, Portal, Automation)
 * gets its own {@code AnnotationConfigWebApplicationContext} with dedicated security.
 *
 * @author Stuart Clark
 * @author GraviteeSource Team
 */
@Configuration
@EnableWebSecurity
@Import(AutomationSecurityConfiguration.class)
public class AutomationConfiguration {

    @Bean
    public AutomationResourceResolver automationResourceResolver(DomainService domainService,
            IdentityProviderService identityProviderService,
            CertificateService certificateService,
            ReporterService reporterService,
            DataPlaneDefinitionService dataPlaneDefinitionService) {
        return new AutomationResourceResolver(domainService, identityProviderService, certificateService, reporterService,
                dataPlaneDefinitionService);
    }

    // Each proxy bean takes the name of the management API's, which it hides in this context.

    @Bean
    public IdentityProviderServiceProxy identityProviderServiceProxyImpl() {
        var proxy = new IdentityProviderServiceProxyImpl();
        proxy.setMaskingMode(MaskingMode.PRESENT_ONLY);
        return proxy;
    }

    @Bean
    public CertificateServiceProxy certificateServiceProxyImpl(CertificateService certificateService,
            IdentityProviderService identityProviderService,
            ApplicationService applicationService,
            CertificatePluginService certificatePluginService,
            AuditService auditService,
            ObjectMapper objectMapper,
            @Qualifier("certificateNotifierSettings") NotifierSettings certificateNotifierSettings) {
        var proxy = new CertificateServiceProxyImpl(certificateService, identityProviderService, applicationService,
                certificatePluginService, auditService, objectMapper, certificateNotifierSettings);
        proxy.setMaskingMode(MaskingMode.PRESENT_ONLY);
        return proxy;
    }

    @Bean
    public ReporterServiceProxy reporterServiceProxyImpl() {
        var proxy = new ReporterServiceProxyImpl();
        proxy.setMaskingMode(MaskingMode.PRESENT_ONLY);
        return proxy;
    }
}
