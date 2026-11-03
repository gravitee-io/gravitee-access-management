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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import io.gravitee.am.common.audit.Status;
import io.gravitee.am.dataplane.api.DataPlaneDescription;
import io.gravitee.am.jwt.JWTBuilder;
import io.gravitee.am.management.service.EmailManager;
import io.gravitee.am.management.service.model.StagedEmail;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Email;
import io.gravitee.am.model.EmailStaging;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.exception.BatchEmailException;
import io.gravitee.am.service.impl.I18nDictionaryService;
import io.gravitee.am.service.impl.application.DomainReadServiceImpl;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.List;

import static io.gravitee.am.management.service.impl.EmailServiceImplTest.TEMPLATES_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailServiceImplBatchTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP.dynamicPort());

    @Mock
    private EmailManager emailManager;

    @Mock
    private JWTBuilder jwtBuilder;

    @Mock
    private AuditService auditService;

    @Mock
    private DataPlaneRegistry dataPlaneRegistry;

    @Mock
    private I18nDictionaryService i18nDictionaryService;

    private io.gravitee.am.service.impl.EmailServiceImpl coreEmailService;

    private EmailServiceImpl cut;

    private final Domain domain = domain();

    @BeforeEach
    void init() throws Exception {
        var mailSender = new JavaMailSenderImpl();
        mailSender.setJavaMailProperties(greenMail.getSmtp().getServerSetup().configureJavaMailSessionProperties(null, false));
        var realCoreEmailService = new io.gravitee.am.service.impl.EmailServiceImpl(mailSender, TEMPLATES_PATH);
        realCoreEmailService.afterPropertiesSet();
        coreEmailService = spy(realCoreEmailService);

        var templates = new StringTemplateLoader();
        templates.putTemplate("registration_confirmation.html", "Hello ${user.firstName}");
        var freemarkerConfiguration = new Configuration(Configuration.VERSION_2_3_22);
        freemarkerConfiguration.setTemplateLoader(templates);

        when(jwtBuilder.sign(any())).thenReturn("TOKEN");
        when(i18nDictionaryService.findAll(any(), any())).thenReturn(Flowable.empty());
        when(dataPlaneRegistry.getDescription(any()))
                .thenReturn(new DataPlaneDescription("default", "Default DataPlane", "mongo", "baseProp", "http://localhost:1234/unittest"));

        var emailTemplate = new Email();
        emailTemplate.setFrom("no-reply@gravitee.io");
        emailTemplate.setSubject("Welcome ${user.firstName}");
        emailTemplate.setTemplate("registration_confirmation");
        emailTemplate.setExpiresAfter(86400);
        when(emailManager.getEmail(any(), any(), any(), anyInt())).thenReturn(Maybe.just(emailTemplate));

        cut = new EmailServiceImpl(
                emailManager,
                coreEmailService,
                freemarkerConfiguration,
                auditService,
                jwtBuilder,
                new DomainReadServiceImpl(mock(), dataPlaneRegistry, mock(), new MockEnvironment(), "http://localhost:1234/unittest"),
                i18nDictionaryService,
                new MockEnvironment().withProperty("email.enabled", "true")
        );
    }

    @Test
    void shouldSendStagedEmailsInOneBatchAndAuditEachUser() throws Exception {
        var john = stagedEmail("john", "john@unittest.com");
        var jane = stagedEmail("jane", "jane@unittest.com");

        var prepared = cut.prepare(domain, List.of(john, jane)).blockingGet();
        cut.batch(prepared, 2);

        verify(coreEmailService).batch(any());
        assertThat(greenMail.getReceivedMessages())
                .extracting(MimeMessage::getSubject)
                .containsExactlyInAnyOrder("Welcome john", "Welcome jane");
        assertThat(List.of(john.staging(), jane.staging())).allMatch(EmailStaging::isProcessed);
        assertThat(auditStatuses(2)).containsOnly(Status.SUCCESS);
    }

    @Test
    void shouldRetryOnlyRejectedRecipients() {
        var john = stagedEmail("john", "john@unittest.com");
        var jane = stagedEmail("jane", "jane@unittest.com");
        doThrow(new BatchEmailException("rejected", List.of("jane@unittest.com"))).when(coreEmailService).batch(any());

        cut.batch(cut.prepare(domain, List.of(john, jane)).blockingGet(), 2);

        assertThat(john.staging().isProcessed()).isTrue();
        assertThat(jane.staging().isProcessed()).isFalse();
        assertThat(jane.staging().getAttempts()).isEqualTo(1);
        assertThat(auditStatuses(1)).containsOnly(Status.SUCCESS);
    }

    @Test
    void shouldAuditFailureWhenMaxAttemptsReached() {
        var jane = stagedEmail("jane", "jane@unittest.com");
        jane.staging().setAttempts(1);
        doThrow(new BatchEmailException("rejected", List.of("jane@unittest.com"))).when(coreEmailService).batch(any());

        cut.batch(cut.prepare(domain, List.of(jane)).blockingGet(), 2);

        assertThat(jane.staging().isProcessed()).isTrue();
        assertThat(jane.staging().getAttempts()).isEqualTo(2);
        assertThat(auditStatuses(1)).containsOnly(Status.FAILURE);
    }

    @Test
    void shouldRetryEveryUserSharingRejectedAddress() {
        var john = stagedEmail("john", "shared@unittest.com");
        var jane = stagedEmail("jane", "shared@unittest.com");
        doThrow(new BatchEmailException("rejected", List.of("shared@unittest.com"))).when(coreEmailService).batch(any());

        cut.batch(cut.prepare(domain, List.of(john, jane)).blockingGet(), 2);

        assertThat(List.of(john.staging(), jane.staging())).allMatch(staging -> !staging.isProcessed() && staging.getAttempts() == 1);
        verify(auditService, never()).report(any());
    }

    @Test
    void shouldDropAndAuditEmailThatCannotBePrepared() {
        var john = stagedEmail("john", "john@unittest.com");
        when(emailManager.getEmail(any(), any(), any(), anyInt())).thenReturn(Maybe.error(new IllegalStateException("no template")));

        var prepared = cut.prepare(domain, List.of(john)).blockingGet();
        cut.batch(prepared, 2);

        assertThat(prepared).singleElement().satisfies(stagedEmail -> assertThat(stagedEmail.email()).isNull());
        assertThat(john.staging().isProcessed()).isTrue();
        verify(coreEmailService, never()).batch(any());
        assertThat(auditStatuses(1)).containsOnly(Status.FAILURE);
    }

    @Test
    void shouldDropAndAuditUserWithoutEmail() {
        var john = stagedEmail("john", "john@unittest.com");
        var jane = stagedEmail("jane", null);

        cut.batch(cut.prepare(domain, List.of(john, jane)).blockingGet(), 2);

        assertThat(greenMail.getReceivedMessages())
                .extracting(MimeMessage::getSubject)
                .containsExactly("Welcome john");
        assertThat(List.of(john.staging(), jane.staging())).allMatch(EmailStaging::isProcessed);
        assertThat(auditStatuses(2)).containsExactlyInAnyOrder(Status.SUCCESS, Status.FAILURE);
    }

    @Test
    void shouldNotSendWhenEmailDisabled() {
        var disabled = new EmailServiceImpl(emailManager, coreEmailService, new Configuration(Configuration.VERSION_2_3_22), auditService,
                jwtBuilder, mock(), i18nDictionaryService, new MockEnvironment());
        var john = stagedEmail("john", "john@unittest.com");

        disabled.batch(disabled.prepare(domain, List.of(john)).blockingGet(), 2);

        verify(coreEmailService, never()).batch(any());
        assertThat(john.staging().isProcessed()).isFalse();
    }

    private List<Status> auditStatuses(int expectedAudits) {
        ArgumentCaptor<AuditBuilder> captor = ArgumentCaptor.forClass(AuditBuilder.class);
        verify(auditService, times(expectedAudits)).report(captor.capture());
        return captor.getAllValues().stream()
                .map(builder -> builder.build(new ObjectMapper()).getOutcome().getStatus())
                .toList();
    }

    private static StagedEmail stagedEmail(String firstName, String email) {
        var user = new User();
        user.setId(firstName + "-id");
        user.setFirstName(firstName);
        user.setEmail(email);
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId("domain-id");
        var staging = new EmailStaging();
        staging.setUserId(user.getId());
        staging.setReferenceType(ReferenceType.DOMAIN);
        staging.setReferenceId("domain-id");
        staging.setEmailTemplateName(Template.REGISTRATION_CONFIRMATION.name());
        return new StagedEmail(user, null, staging);
    }

    private static Domain domain() {
        var domain = new Domain();
        domain.setId("domain-id");
        domain.setPath("/domain");
        return domain;
    }
}
