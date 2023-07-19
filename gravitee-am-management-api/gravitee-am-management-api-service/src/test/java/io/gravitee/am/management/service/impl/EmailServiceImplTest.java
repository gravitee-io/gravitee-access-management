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

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import freemarker.cache.ConditionalTemplateConfigurationFactory;
import freemarker.cache.FileExtensionMatcher;
import freemarker.cache.FileTemplateLoader;
import freemarker.core.HTMLOutputFormat;
import freemarker.core.TemplateClassResolver;
import freemarker.core.TemplateConfiguration;
import freemarker.template.Configuration;
import io.gravitee.am.jwt.JWTBuilder;
import io.gravitee.am.management.service.EmailManager;
import io.gravitee.am.management.service.EmailService;
import io.gravitee.am.management.service.assertions.MimeMessageParserAssert;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Email;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.management.service.impl.utils.MimeMessageParser;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.impl.DomainServiceImpl;
import io.gravitee.am.service.impl.I18nDictionaryService;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class EmailServiceImplTest {

    public static final String TEMPLATES_PATH = "../../gravitee-am-management-api/gravitee-am-management-api-standalone/gravitee-am-management-api-standalone-distribution/src/main/resources/templates";
    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP.dynamicPort());

    @Mock
    private EmailManager emailManager;

    private io.gravitee.am.service.impl.EmailServiceImpl emailService;

    private final Configuration freemarkerConfiguration = new freemarker.template.Configuration(freemarker.template.Configuration.VERSION_2_3_22);

    @Mock
    private JWTBuilder jwtBuilder;

    @Mock
    private AuditService auditService;

    JavaMailSenderImpl mailSender;

    I18nDictionaryService i18nDictionaryService;

    @BeforeEach
    public void init() throws Exception {

        mailSender = new JavaMailSenderImpl();
        mailSender.setJavaMailProperties(greenMail.getSmtp().getServerSetup().configureJavaMailSessionProperties(null, false));

        emailService = new io.gravitee.am.service.impl.EmailServiceImpl(mailSender, TEMPLATES_PATH);
        emailService.afterPropertiesSet();

        freemarkerConfiguration.setLocalizedLookup(false);
        freemarkerConfiguration.setNewBuiltinClassResolver(TemplateClassResolver.SAFER_RESOLVER);
        TemplateConfiguration tcHTML = new TemplateConfiguration();
        tcHTML.setOutputFormat(HTMLOutputFormat.INSTANCE);
        freemarkerConfiguration.setTemplateConfigurations(
                new ConditionalTemplateConfigurationFactory(new FileExtensionMatcher("html"), tcHTML));
        freemarkerConfiguration.setTemplateLoader(new FileTemplateLoader(new File(TEMPLATES_PATH)));
    }

    @ParameterizedTest
    @CsvSource({
            "REGISTRATION_CONFIRMATION,Nouvel enregistrement d'utilisateur,,fr",
            "REGISTRATION_CONFIRMATION,New user registration,,en",
            "REGISTRATION_VERIFY,Vérifiez votre compte,http://localhost:1234/unittest/verifyRegistration?param1=PARAM_1&param2=PARAM_2,fr",
            "REGISTRATION_VERIFY,New user registration,http://localhost:1234/unittest/verifyRegistration?param3=PARAM_3&param4=PARAM_4,en",
    })
    public void validate_send_registration_confirmation_email(Template template, String subject, String registrationUrl, String lang) throws Exception {

        when(jwtBuilder.sign(any())).thenReturn("TOKEN");

        this.i18nDictionaryService = mock(I18nDictionaryService.class);
        when(i18nDictionaryService.findAll(any(), any())).thenReturn(Flowable.empty());
        
        var cut = new EmailServiceImpl(
                emailManager,
                emailService,
                freemarkerConfiguration,
                auditService,
                jwtBuilder,
                new DomainServiceImpl("http://localhost:1234/unittest/"),
                i18nDictionaryService,
                true,
                "New user registration",
                86400,
                "New user registration",
                604800,
                "Certificate will expire soon"
        );


        final var registrationTpl = template;
        final var email = new Email();
        email.setFrom("no-reply@gravitee.io");
        email.setSubject(String.format("${msg('email.%s.subject')}", template.template()));
        email.setTemplate(registrationTpl.template());
        when(emailManager.getEmail(any(), any(), any(), anyInt())).thenReturn(Maybe.just(email));

        var user = createUser(lang);
        user.setRegistrationUserUri(registrationUrl);

        var application = new Application();
        application.setName("name");
        application.setSettings(new ApplicationSettings());
        application.getSettings().setOauth(new ApplicationOAuthSettings());
        application.getSettings().getOauth().setClientId("CLIENT_ID");
        application.getSettings().getOauth().setClientName("app");

        cut.send(createDomain(), application, registrationTpl, user).test().await().assertComplete();

        org.assertj.core.api.Assertions.assertThat(greenMail.getReceivedMessages())
                .hasSize(1)
                .extracting(x -> new MimeMessageParser(x).parse())
                .allSatisfy(message ->
                                MimeMessageParserAssert
                                        .assertThat(message)
                                        .hasFrom("no-reply@gravitee.io")
                                        .hasTo("john.doe@unittest.com")
                                        .hasSubject(subject)
                                        .hasHtmlContent(Files.readString(Path.of(String.format("src/test/resources/templates/%s_%s.html", registrationTpl.template(), lang))))
                );
    }

    @Test
    public void must_not_send_email_when_feature_is_disabled() throws Exception {

        var cut = new EmailServiceImpl(
                emailManager,
                emailService,
                freemarkerConfiguration,
                auditService,
                jwtBuilder,
                new DomainServiceImpl("http://localhost:1234/unittest/"),
                i18nDictionaryService,
                false,
                "New user registration",
                86400,
                "New user registration",
                604800,
                "Certificate will expire soon"
        );

        cut.send(createDomain(), null, Template.REGISTRATION_CONFIRMATION, createUser("en")).test().await().assertComplete();
        org.assertj.core.api.Assertions.assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    @Test
    public void must_not_send_email_when_error_occurred() throws Exception {

        var cut = Mockito.mock(EmailService.class);
        when(cut.send(any(), any(), any(), any())).thenReturn(Maybe.error(new IllegalStateException("Error")));

        cut.send(createDomain(), null, Template.REGISTRATION_CONFIRMATION, createUser("en")).test().await().assertError(IllegalStateException.class);
        org.assertj.core.api.Assertions.assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    private static Domain createDomain() {
        var domain = new Domain();
        domain.setId("id");
        return domain;
    }

    private static User createUser(String en) {
        final User user = new User();
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setPreferredLanguage(en);
        user.setEmail("john.doe@unittest.com");
        return user;
    }
}
