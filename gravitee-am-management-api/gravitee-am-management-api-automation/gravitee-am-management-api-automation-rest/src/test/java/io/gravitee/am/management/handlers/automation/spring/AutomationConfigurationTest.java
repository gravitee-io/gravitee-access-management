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
import io.gravitee.am.management.service.AbstractSensitiveProxy;
import io.gravitee.am.management.service.MaskingMode;
import io.gravitee.am.management.service.impl.CertificateServiceProxyImpl;
import io.gravitee.am.management.service.impl.IdentityProviderServiceProxyImpl;
import io.gravitee.am.management.service.impl.ReporterServiceProxyImpl;
import io.gravitee.am.management.service.impl.notifications.notifiers.NotifierSettings;
import io.gravitee.am.model.Template;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.CertificatePluginService;
import io.gravitee.am.service.CertificateService;
import io.gravitee.am.service.IdentityProviderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;
import org.springframework.context.annotation.Bean;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * @author GraviteeSource Team
 */
class AutomationConfigurationTest {

    private final AutomationConfiguration configuration = new AutomationConfiguration();

    @Test
    void shouldNameEachProxyAfterTheManagementApiBeanItReplaces() {
        List.of(IdentityProviderServiceProxyImpl.class, CertificateServiceProxyImpl.class, ReporterServiceProxyImpl.class)
                .forEach(component -> assertThat(Arrays.stream(AutomationConfiguration.class.getMethods())
                        .filter(method -> method.isAnnotationPresent(Bean.class))
                        .map(method -> method.getName()))
                        .contains(componentName(component)));
    }

    @Test
    void shouldMaskOnlyTheSensitiveValuesThatAreSet() {
        var notifierSettings = new NotifierSettings(true, Template.CERTIFICATE_EXPIRATION, "* * * * *", List.of(30), "subject");

        List.<AbstractSensitiveProxy>of(
                (AbstractSensitiveProxy) configuration.identityProviderServiceProxyImpl(),
                (AbstractSensitiveProxy) configuration.certificateServiceProxyImpl(mock(CertificateService.class),
                        mock(IdentityProviderService.class), mock(ApplicationService.class), mock(CertificatePluginService.class),
                        mock(AuditService.class), new ObjectMapper(), notifierSettings),
                (AbstractSensitiveProxy) configuration.reporterServiceProxyImpl())
                .forEach(proxy -> assertThat(proxy.getMaskingMode()).isEqualTo(MaskingMode.PRESENT_ONLY));
    }

    private static String componentName(Class<?> component) {
        return new AnnotationBeanNameGenerator().generateBeanName(new AnnotatedGenericBeanDefinition(component), new DefaultListableBeanFactory());
    }
}
