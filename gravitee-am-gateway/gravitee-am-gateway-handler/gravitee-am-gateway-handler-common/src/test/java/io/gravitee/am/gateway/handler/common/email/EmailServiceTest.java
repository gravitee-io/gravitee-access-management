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
package io.gravitee.am.gateway.handler.common.email;

import freemarker.cache.ConditionalTemplateConfigurationFactory;
import freemarker.cache.FileExtensionMatcher;
import freemarker.cache.FileTemplateLoader;
import freemarker.core.HTMLOutputFormat;
import freemarker.core.TemplateClassResolver;
import freemarker.core.TemplateConfiguration;
import freemarker.template.Configuration;
import io.gravitee.am.common.email.Email;
import io.gravitee.am.gateway.handler.common.email.impl.EmailServiceImpl;
import io.gravitee.am.jwt.JWTBuilder;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.DomainReadService;
import io.gravitee.am.service.EmailService;
import io.gravitee.am.service.i18n.CompositeDictionaryProvider;
import io.gravitee.am.service.i18n.DomainBasedDictionaryProvider;
import io.gravitee.am.service.i18n.FileSystemDictionaryProvider;
import io.gravitee.am.service.i18n.GraviteeMessageResolver;
import io.gravitee.am.service.impl.I18nDictionaryService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.util.Map;

import static java.util.concurrent.TimeUnit.DAYS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class EmailServiceTest {

    @InjectMocks
    private EmailServiceImpl cut = new EmailServiceImpl(
            true,
            "Please reset your password",
            300,
            "Account has been locked",
            86400,
            "Verification Code",
            300,
            "Verify your MFA attempt",
            "Complete your registration",
            Long.valueOf(DAYS.toSeconds(7)).intValue(),
            "Verify your registration",
            Long.valueOf(DAYS.toSeconds(7)).intValue(),
            "Sing in",
            Long.valueOf(DAYS.toSeconds(7)).intValue()
    );

    @Mock
    private EmailService emailService;

    @Mock
    private AuditService auditService;

    @Spy
    private Configuration freemarkerConfiguration = new freemarker.template.Configuration(freemarker.template.Configuration.VERSION_2_3_22);

    @Mock
    private JWTBuilder jwtBuilder;

    @Mock
    private DomainReadService domainService;

    @Mock
    private Domain domain;

    @Mock
    private I18nDictionaryService i18nDictionaryService;

    @Mock
    private GraviteeMessageResolver graviteeMessageResolver;

    @Spy
    private DomainBasedDictionaryProvider domainBasedDictionaryProvider = new DomainBasedDictionaryProvider();

    @Before
    public void init() throws Exception {
        when(domain.getId()).thenReturn("id");
        freemarkerConfiguration.setLocalizedLookup(false);
        freemarkerConfiguration.setNewBuiltinClassResolver(TemplateClassResolver.SAFER_RESOLVER);
        TemplateConfiguration tcHTML = new TemplateConfiguration();
        tcHTML.setOutputFormat(HTMLOutputFormat.INSTANCE);
        freemarkerConfiguration.setTemplateConfigurations(
                new ConditionalTemplateConfigurationFactory(new FileExtensionMatcher("html"), tcHTML));
        freemarkerConfiguration.setTemplateLoader(new FileTemplateLoader(new File("src/test/resources/templates")));

        when(this.emailService.getDefaultDictionaryProvider()).thenReturn(FileSystemDictionaryProvider.getInstance("src/test/resources/templates/i18n"));

        when(this.graviteeMessageResolver.getDynamicDictionaryProvider()).thenReturn(this.domainBasedDictionaryProvider);
    }

    @Test
    public void sendEmail_i18n_fr() throws Exception {
        final User user = new User();
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setPreferredLanguage("fr");

        final var email = new Email();
        email.setFrom("no-reply@gravitee.io");
        email.setFromName("no-reply@gravitee.io");
        email.setSubject("${msg('reset.password.email.subject')}");
        email.setContent("${msg('reset.password.email.say.hello', user.firstName, user.lastName)}");
        email.setTo(new String[]{"user@acme.com"});
        email.setTemplate(Template.RESET_PASSWORD.template());
        email.setParams(Map.of("user", user));

        cut.send(email);

        verify(domainBasedDictionaryProvider, times(5)).getDictionaryFor(any());
        verify(emailService).send(argThat(msg -> msg.getSubject().equals("Veuillez reinitialiser votre mot de passe") &&
                msg.getContent().contains("Bonjour John Doe,")));
    }

    @Test
    public void sendEmail_i18n_en() throws Exception {
        final User user = new User();
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setPreferredLanguage("en");

        final var email = new Email();
        email.setFrom("no-reply@gravitee.io");
        email.setFromName("no-reply@gravitee.io");
        email.setSubject("${msg('reset.password.email.subject')}");
        email.setContent("${msg('reset.password.email.say.hello', user.firstName, user.lastName)}");
        email.setTo(new String[]{"user@acme.com"});
        email.setTemplate(Template.RESET_PASSWORD.template());
        email.setParams(Map.of("user", user));

        cut.send(email);

        verify(domainBasedDictionaryProvider, times(5)).getDictionaryFor(any());
        verify(emailService).send(argThat(msg -> msg.getSubject().equals("Please reset your password") &&
                msg.getContent().contains("Hi John Doe,")));
    }
}
