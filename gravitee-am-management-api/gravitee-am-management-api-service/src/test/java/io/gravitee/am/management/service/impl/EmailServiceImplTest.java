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
import io.gravitee.am.management.service.impl.utils.MimeMessageParser;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Email;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.impl.I18nDictionaryService;
import io.gravitee.am.service.impl.application.DomainReadServiceImpl;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import jakarta.mail.internet.InternetAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
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

    @Mock
    private Environment environment;



    JavaMailSenderImpl mailSender;

    I18nDictionaryService i18nDictionaryService;

    @BeforeEach
    void init() throws Exception {

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

    @Test
    void should_resolve_dynamic_from_and_from_name() throws Exception {

        when(jwtBuilder.sign(any())).thenReturn("TOKEN");

        this.i18nDictionaryService = mock(I18nDictionaryService.class);
        when(i18nDictionaryService.findAll(any(), any())).thenReturn(Flowable.empty());
        when(environment.getProperty("email.enabled", "false")).thenReturn("true");
        when(environment.getProperty("user.registration.email.subject", "New user registration")).thenReturn("New user registration");
        when(environment.getProperty("user.registration.token.expire-after", "86400")).thenReturn("86400");
        when(environment.getProperty("user.registration.verify.email.subject", "New user registration")).thenReturn("New user registration");
        when(environment.getProperty("user.registration.verify.token.expire-after", "604800")).thenReturn("604800");
        when(environment.getProperty("services.certificate.expiryEmailSubject", "Certificate will expire soon")).thenReturn("Certificate will expire soon");

        var cut = new EmailServiceImpl(
                emailManager,
                emailService,
                freemarkerConfiguration,
                auditService,
                jwtBuilder,
                new DomainReadServiceImpl(mock(), "http://localhost:1234/unittest"),
                i18nDictionaryService,
                environment
        );

        var emailTemplate = new Email();
        emailTemplate.setFrom("${user.firstName}.${user.lastName}@gravitee.io");
        emailTemplate.setFromName("${domain.id}-team");
        emailTemplate.setSubject("Welcome ${user.firstName}");
        emailTemplate.setTemplate(Template.REGISTRATION_CONFIRMATION.template());
        emailTemplate.setExpiresAfter(86400);

        when(emailManager.getEmail(any(), any(), any(), anyInt())).thenReturn(Maybe.just(emailTemplate));

        var domain = createDomain();
        domain.setId("unit-domain");

        var user = createUser("en");

        cut.send(domain, null, Template.REGISTRATION_CONFIRMATION, user).test().await().assertComplete();

        var receivedMessages = greenMail.getReceivedMessages();
        org.assertj.core.api.Assertions.assertThat(receivedMessages).hasSize(1);

        var message = receivedMessages[0];
        var parsedMessage = new MimeMessageParser(message).parse();

        MimeMessageParserAssert.assertThat(parsedMessage).hasFrom("John.Doe@gravitee.io");
        MimeMessageParserAssert.assertThat(parsedMessage).hasSubject("Welcome John");
        org.assertj.core.api.Assertions.assertThat(((InternetAddress) message.getFrom()[0]).getPersonal()).isEqualTo("unit-domain-team");
    }

    @ParameterizedTest
    @CsvSource({
            "REGISTRATION_CONFIRMATION,Nouvel enregistrement d'utilisateur,,fr",
            "REGISTRATION_CONFIRMATION,New user registration,,en",
            "REGISTRATION_VERIFY,Vérifiez votre compte,http://localhost:1234/unittest/verifyRegistration?param1=PARAM_1&param2=PARAM_2,fr",
            "REGISTRATION_VERIFY,New user registration,http://localhost:1234/unittest/verifyRegistration?param3=PARAM_3&param4=PARAM_4,en",
    })
    void validate_send_registration_confirmation_email(Template template, String subject, String registrationUrl, String lang) throws Exception {

        when(jwtBuilder.sign(any())).thenReturn("TOKEN");

        this.i18nDictionaryService = mock(I18nDictionaryService.class);
        when(i18nDictionaryService.findAll(any(), any())).thenReturn(Flowable.empty());
        when(environment.getProperty("email.enabled", "false")).thenReturn("true");
        when(environment.getProperty("user.registration.email.subject", "New user registration")).thenReturn("New user registration");
        when(environment.getProperty("user.registration.token.expire-after", "86400")).thenReturn("86400");
        when(environment.getProperty("user.registration.verify.email.subject", "New user registration")).thenReturn("New user registration");
        when(environment.getProperty("user.registration.verify.token.expire-after", "604800")).thenReturn("604800");
        when(environment.getProperty("services.certificate.expiryEmailSubject", "Certificate will expire soon")).thenReturn("Certificate will expire soon");

        var cut = new EmailServiceImpl(
                emailManager,
                emailService,
                freemarkerConfiguration,
                auditService,
                jwtBuilder,
                new DomainReadServiceImpl(mock(), "http://localhost:1234/unittest"),
                i18nDictionaryService,
                environment
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
    void must_not_send_email_when_feature_is_disabled() throws Exception {
        when(environment.getProperty("email.enabled", "false")).thenReturn("false");
        when(environment.getProperty("user.registration.email.subject", "New user registration")).thenReturn("New user registration");
        when(environment.getProperty("user.registration.token.expire-after", "86400")).thenReturn("86400");
        when(environment.getProperty("user.registration.verify.email.subject", "New user registration")).thenReturn("New user registration");
        when(environment.getProperty("user.registration.verify.token.expire-after", "604800")).thenReturn("604800");
        when(environment.getProperty("services.certificate.expiryEmailSubject", "Certificate will expire soon")).thenReturn("Certificate will expire soon");

        var cut = new EmailServiceImpl(
                emailManager,
                emailService,
                freemarkerConfiguration,
                auditService,
                jwtBuilder,
                new DomainServiceImpl(),
                i18nDictionaryService,
                environment
        );

        cut.send(createDomain(), null, Template.REGISTRATION_CONFIRMATION, createUser("en")).test().await().assertComplete();
        org.assertj.core.api.Assertions.assertThat(greenMail.getReceivedMessages()).isEmpty();
    }

    @Test
    void must_not_send_email_when_error_occurred() throws Exception {

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
        user.setReferenceId("id");
        user.setReferenceType(ReferenceType.DOMAIN);
        return user;
    }
}
