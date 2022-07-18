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
package io.gravitee.am.management.service.impl;

import freemarker.cache.ConditionalTemplateConfigurationFactory;
import freemarker.cache.FileExtensionMatcher;
import freemarker.cache.FileTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.core.HTMLOutputFormat;
import freemarker.core.TemplateClassResolver;
import freemarker.core.TemplateConfiguration;
import freemarker.template.Configuration;
import io.gravitee.am.common.i18n.FileSystemDictionaryProvider;
import io.gravitee.am.jwt.JWTBuilder;
import io.gravitee.am.management.service.EmailManager;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Email;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.EmailService;
import io.reactivex.Maybe;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class EmailServiceTest {

    @InjectMocks
    private EmailServiceImpl cut;

    @Mock
    private EmailManager emailManager;

    @Mock
    private EmailService emailService;

    @Spy
    private Configuration freemarkerConfiguration = new freemarker.template.Configuration(freemarker.template.Configuration.VERSION_2_3_22);

    @Mock
    private JWTBuilder jwtBuilder;

    @Mock
    private DomainService domainService;

    @Before
    public void init() throws Exception {
        ReflectionTestUtils.setField(cut, "enabled", true);
        ReflectionTestUtils.setField(cut, "registrationExpireAfter", 86400);

        freemarkerConfiguration.setLocalizedLookup(false);
        freemarkerConfiguration.setNewBuiltinClassResolver(TemplateClassResolver.SAFER_RESOLVER);
        TemplateConfiguration tcHTML = new TemplateConfiguration();
        tcHTML.setOutputFormat(HTMLOutputFormat.INSTANCE);
        freemarkerConfiguration.setTemplateConfigurations(
                new ConditionalTemplateConfigurationFactory(new FileExtensionMatcher("html"), tcHTML));
        freemarkerConfiguration.setTemplateLoader(new FileTemplateLoader(new File("src/test/resources/templates")));

        when(emailService.getDefaultDictionaryProvider()).thenReturn(new FileSystemDictionaryProvider("src/test/resources/templates/i18n"));
        when(domainService.buildUrl(any(), any())).thenReturn("http://url");
    }

    @Test
    public void sendEmail_i18n_fr() throws Exception {
        final var registrationTpl = Template.REGISTRATION_CONFIRMATION;
        final var email = new Email();
        email.setFrom("no-reply@gravitee.io");
        email.setSubject("${msg('registration.confirmation.email.subject')}");
        email.setTemplate(registrationTpl.template());
        when(emailManager.getEmail(any(), any(), any(), anyInt())).thenReturn(Maybe.just(email));

        final User user = new User();
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setPreferredLanguage("fr");
        cut.send(new Domain(), null, registrationTpl, user).blockingGet();

        verify(emailService).send(argThat(msg -> msg.getSubject().equals("Nouvel enregistrement d'utilisateur") &&
                msg.getContent().contains("Bonjour John Doe,")));
    }

    @Test
    public void sendEmail_i18n_en() throws Exception {
        final var registrationTpl = Template.REGISTRATION_CONFIRMATION;
        final var email = new Email();
        email.setFrom("no-reply@gravitee.io");
        email.setSubject("${msg('registration.confirmation.email.subject')}");
        email.setTemplate(registrationTpl.template());
        when(emailManager.getEmail(any(), any(), any(), anyInt())).thenReturn(Maybe.just(email));

        final User user = new User();
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setPreferredLanguage("en");
        cut.send(new Domain(), null, registrationTpl, user).blockingGet();

        verify(emailService).send(argThat(msg -> msg.getSubject().equals("New user registration") &&
                msg.getContent().contains("You have been") &&
                msg.getContent().contains("Hi John Doe,")));
    }
}
