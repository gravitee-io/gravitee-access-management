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

import com.fasterxml.jackson.databind.ObjectMapper;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapperBuilder;
import io.gravitee.am.common.audit.Status;
import io.gravitee.am.gateway.handler.common.email.impl.EmailServiceImpl;
import io.gravitee.am.jwt.JWTBuilder;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Email;
import io.gravitee.am.model.EmailStaging;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.monitoring.provider.GatewayMetricProvider;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.DomainReadService;
import io.gravitee.am.service.exception.BatchEmailException;
import io.gravitee.am.service.i18n.DictionaryProvider;
import io.gravitee.am.service.i18n.GraviteeMessageResolver;
import io.vertx.rxjava3.core.MultiMap;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Qualifier;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import static freemarker.template.Configuration.AUTO_DETECT_NAMING_CONVENTION;
import static freemarker.template.Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS;
import static java.util.concurrent.TimeUnit.DAYS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */

public class EmailServiceImplTest {

    @Mock
    private EmailManager emailManager;

    @Mock
    private io.gravitee.am.service.EmailService emailService;

    @Mock
    private Configuration freemarkerConfiguration;

    @Mock
    private Domain domain;

    @Mock
    private AuditService auditService;

    @Mock
    @Qualifier("managementJwtBuilder")
    private JWTBuilder jwtBuilder;

    @Mock
    private DomainReadService domainService;

    @Mock
    private GraviteeMessageResolver graviteeMessageResolver;

    @Mock
    private GatewayMetricProvider gatewayMetricProvider;

    @InjectMocks
    private EmailService emailServiceSpy;

    private ObjectMapper mapper = new ObjectMapper();

    @Test
    public void must_not_send_email_due_to_not_enabled() throws IOException {
        var emailService = instantiateEmailService(false);

        emailServiceSpy = Mockito.spy(emailService);
        MockitoAnnotations.openMocks(this);

        emailServiceSpy.send(Template.RESET_PASSWORD, Mockito.mock(User.class), Mockito.mock(Client.class));

        verify(freemarkerConfiguration, never()).getTemplate(anyString());
        verify(emailManager, never()).getEmail(any(), any(), anyInt());
        verify(auditService, never()).report(any());
        verify(this.emailService, never()).send(any());
    }

    @Test
    public void must_send_email() throws IOException {
        var emailService = instantiateEmailService(true);
        emailServiceSpy = Mockito.spy(emailService);
        MockitoAnnotations.openMocks(this);

        final Email email = buildEmail();

        var templateLoader = Mockito.mock(TemplateLoader.class);
        when(domain.getId()).thenReturn("id");
        final DictionaryProvider mockDictionaryProvider = Mockito.mock(DictionaryProvider.class);
        when(this.emailService.getDefaultDictionaryProvider()).thenReturn(mockDictionaryProvider);
        when(mockDictionaryProvider.getDictionaryFor(any())).thenReturn(new Properties());

        when(freemarkerConfiguration.getIncompatibleImprovements()).thenReturn(DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
        when(freemarkerConfiguration.getNamingConvention()).thenReturn(AUTO_DETECT_NAMING_CONVENTION);
        when(freemarkerConfiguration.getObjectWrapper()).thenReturn(new DefaultObjectWrapperBuilder(DEFAULT_INCOMPATIBLE_IMPROVEMENTS).build());

        var templateMock = new freemarker.template.Template("content", new StringReader(email.getTemplate()), freemarkerConfiguration);
        when(templateLoader.findTemplateSource(anyString())).thenReturn(templateMock);

        when(this.freemarkerConfiguration.getTemplateLoader()).thenReturn(templateLoader);
        when(freemarkerConfiguration.getTemplate(anyString())).thenReturn(templateMock);

        when(emailManager.getEmail(anyString(), any(), anyInt())).thenReturn(email);

        when(jwtBuilder.sign(any())).thenReturn("TOKEN");

        final Client client = new Client();
        client.setClientId(email.getClient());
        emailServiceSpy.send(Template.RESET_PASSWORD, Mockito.mock(User.class), client);

        verify(freemarkerConfiguration, times(1)).getTemplate(eq(email.getTemplate() + ".html"));
        verify(emailManager, times(1)).getEmail(any(), any(), anyInt());
        verify(auditService, times(1)).report(any());
        verify(this.emailService, times(1)).send(any());
    }

    @Test
    public void must_send_email_with_query_params_in_url() throws IOException {
        var emailService = instantiateEmailService(true);
        emailServiceSpy = Mockito.spy(emailService);
        MockitoAnnotations.openMocks(this);

        final Email email = buildEmail();

        var templateLoader = Mockito.mock(TemplateLoader.class);

        final DictionaryProvider mockDictionaryProvider = Mockito.mock(DictionaryProvider.class);
        when(this.emailService.getDefaultDictionaryProvider()).thenReturn(mockDictionaryProvider);
        when(mockDictionaryProvider.getDictionaryFor(any())).thenReturn(new Properties());

        when(freemarkerConfiguration.getIncompatibleImprovements()).thenReturn(DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
        when(freemarkerConfiguration.getNamingConvention()).thenReturn(AUTO_DETECT_NAMING_CONVENTION);
        when(freemarkerConfiguration.getObjectWrapper()).thenReturn(new DefaultObjectWrapperBuilder(DEFAULT_INCOMPATIBLE_IMPROVEMENTS).build());

        var templateMock = new freemarker.template.Template("content", new StringReader(email.getTemplate()), freemarkerConfiguration);
        when(templateLoader.findTemplateSource(anyString())).thenReturn(templateMock);

        when(this.freemarkerConfiguration.getTemplateLoader()).thenReturn(templateLoader);
        when(freemarkerConfiguration.getTemplate(anyString())).thenReturn(templateMock);

        when(emailManager.getEmail(anyString(), any(), anyInt())).thenReturn(email);

        when(jwtBuilder.sign(any())).thenReturn("TOKEN");
        when(domain.getId()).thenReturn("id");

        final Client client = new Client();
        client.setClientId(email.getClient());

        final MultiMap queryParams = MultiMap.caseInsensitiveMultiMap();
        queryParams.add("key", "value");
        queryParams.add("key2", "value2");
        queryParams.add("client_id", client.getClientId());

        emailServiceSpy.send(Template.RESET_PASSWORD, Mockito.mock(User.class), client, queryParams);

        verify(freemarkerConfiguration, times(1)).getTemplate(eq(email.getTemplate() + ".html"));
        verify(emailManager, times(1)).getEmail(any(), any(), anyInt());
        verify(auditService, times(1)).report(any());
        verify(this.emailService, times(1)).send(any());
    }

    @Test
    public void send_with_dynamic_from_fromName_valid() throws IOException {
        var emailService = instantiateEmailService(true);
        emailServiceSpy = Mockito.spy(emailService);
        MockitoAnnotations.openMocks(this);

        final Email email = new Email();
        email.setEnabled(true);
        email.setFrom("${user.firstName}.${user.lastName}@gravitee.io");
        email.setFromName("${domain.id}-team");
        email.setSubject("Subject ${user.firstName}");
        email.setTemplate("dynamic_template");
        email.setContent("Hello ${user.firstName}");
        email.setExpiresAfter(300);

        when(freemarkerConfiguration.getIncompatibleImprovements()).thenReturn(DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
        when(freemarkerConfiguration.getNamingConvention()).thenReturn(AUTO_DETECT_NAMING_CONVENTION);
        when(freemarkerConfiguration.getObjectWrapper()).thenReturn(new DefaultObjectWrapperBuilder(DEFAULT_INCOMPATIBLE_IMPROVEMENTS).build());
        var templateMock = new freemarker.template.Template("content", new StringReader(email.getContent()), freemarkerConfiguration);
        when(freemarkerConfiguration.getTemplate(eq(email.getTemplate() + ".html"))).thenReturn(templateMock);

        final DictionaryProvider mockDictionaryProvider = Mockito.mock(DictionaryProvider.class);
        when(this.emailService.getDefaultDictionaryProvider()).thenReturn(mockDictionaryProvider);
        when(mockDictionaryProvider.getDictionaryFor(any())).thenReturn(new Properties());

        when(graviteeMessageResolver.getDynamicDictionaryProvider()).thenReturn(null);
        when(jwtBuilder.sign(any())).thenReturn("TOKEN");
        when(domain.getId()).thenReturn("domain-id");
        when(domainService.buildUrl(any(), any(), any())).thenReturn("http://localhost/reset");
        when(emailManager.getEmail(anyString(), any(), anyInt())).thenReturn(email);

        final User user = new User();
        user.setId("user-id");
        user.setEmail("john.doe@gravitee.io");
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setReferenceId("domain-id");
        user.setReferenceType(ReferenceType.DOMAIN);

        final Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");

        emailServiceSpy.send(Template.RESET_PASSWORD, user, client);

        ArgumentCaptor<io.gravitee.am.common.email.Email> captor = ArgumentCaptor.forClass(io.gravitee.am.common.email.Email.class);
        verify(this.emailService).send(captor.capture());

        io.gravitee.am.common.email.Email sentEmail = captor.getValue();
        assertThat(sentEmail.getFromName()).isEqualTo("domain-id-team");
    }

    private static EmailServiceImpl instantiateEmailService(boolean enabled) {
        return new EmailServiceImpl(
                enabled,
                "Please reset your password",
                300,
                "Account has been locked",
                86400,
                "Verification Code",
                300,
                "Please verify Attempt",
                "Complete your registration",
                Long.valueOf(DAYS.toSeconds(7)).intValue(),
                "Verify your registration",
                Long.valueOf(DAYS.toSeconds(7)).intValue(),
                "Sign in",
                Long.valueOf(DAYS.toSeconds(7)).intValue());
    }

    @Test
    public void must_not_send_batch_emails_when_disabled() {
        var emailService = instantiateEmailService(false);

        emailServiceSpy = Mockito.spy(emailService);
        MockitoAnnotations.openMocks(this);

        User user = new User();
        user.setId("user-id");
        user.setEmail("test@gravitee.io");
        Client client = new Client();
        client.setClientId("client-id");

        List<EmailContainer> containers = Arrays.asList(new EmailContainer(user, client, defaultStagingEmail(user, client)));

        emailServiceSpy.batch(containers, 1);

        verify(emailManager, never()).getEmail(any(), any(), anyInt());
        verify(this.emailService, never()).batch(any());
        verify(auditService, never()).report(any());
    }

    protected EmailStaging defaultStagingEmail(User user, Client client) {
        EmailStaging emailStaging = new EmailStaging();
        emailStaging.setEmailTemplateName(Template.RESET_PASSWORD.name());
        emailStaging.setApplicationId(client.getId());
        emailStaging.setAttempts(0);
        emailStaging.setCreatedAt(new Date());
        emailStaging.setUpdatedAt(new Date());
        emailStaging.setReferenceId("domain-id");
        emailStaging.setReferenceType(ReferenceType.DOMAIN);
        return emailStaging;
    }

    @Test
    public void must_send_batch_emails_successfully() throws IOException {
        var emailService = instantiateEmailService(true);

        emailServiceSpy = Mockito.spy(emailService);
        MockitoAnnotations.openMocks(this);

        final Email emailTemplate = buildEmail();

        var templateLoader = Mockito.mock(TemplateLoader.class);
        when(domain.getId()).thenReturn("domain-id");

        final DictionaryProvider mockDictionaryProvider = Mockito.mock(DictionaryProvider.class);
        when(this.emailService.getDefaultDictionaryProvider()).thenReturn(mockDictionaryProvider);
        when(mockDictionaryProvider.getDictionaryFor(any())).thenReturn(new Properties());

        when(freemarkerConfiguration.getIncompatibleImprovements()).thenReturn(DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
        when(freemarkerConfiguration.getNamingConvention()).thenReturn(AUTO_DETECT_NAMING_CONVENTION);
        when(freemarkerConfiguration.getObjectWrapper()).thenReturn(new DefaultObjectWrapperBuilder(DEFAULT_INCOMPATIBLE_IMPROVEMENTS).build());

        var templateMock = new freemarker.template.Template("content", new StringReader(emailTemplate.getTemplate()), freemarkerConfiguration);
        when(templateLoader.findTemplateSource(anyString())).thenReturn(templateMock);
        when(freemarkerConfiguration.getTemplateLoader()).thenReturn(templateLoader);
        when(freemarkerConfiguration.getTemplate(anyString())).thenReturn(templateMock);

        when(emailManager.getEmail(anyString(), any(), anyInt())).thenReturn(emailTemplate);
        when(jwtBuilder.sign(any())).thenReturn("TOKEN");
        when(domainService.buildUrl(any(), any(), any())).thenReturn("http://localhost/reset");

        User user1 = new User();
        user1.setId("user-1");
        user1.setEmail("user1@gravitee.io");
        user1.setFirstName("User1");

        User user2 = new User();
        user2.setId("user-2");
        user2.setEmail("user2@gravitee.io");
        user2.setFirstName("User2");

        Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");

        List<EmailContainer> containers = Arrays.asList(
                new EmailContainer(user1, client, defaultStagingEmail(user1, client)),
                new EmailContainer(user2, client, defaultStagingEmail(user2, client))
        );

        emailServiceSpy.batch(containers, 1);

        ArgumentCaptor<List<io.gravitee.am.common.email.Email>> captor = ArgumentCaptor.forClass(List.class);
        verify(this.emailService, times(1)).batch(captor.capture());

        List<io.gravitee.am.common.email.Email> sentEmails = captor.getValue();
        assertThat(sentEmails).hasSize(2);

        // Verify successful audit traces
        verify(auditService, times(2)).report(argThat(builder -> builder.build(mapper).getOutcome().getStatus().equals(Status.SUCCESS)));
    }

    @Test
    public void must_handle_batch_email_exception_with_all_failed_emails() throws IOException {
        var emailService = instantiateEmailService(true);

        emailServiceSpy = Mockito.spy(emailService);
        MockitoAnnotations.openMocks(this);

        final Email emailTemplate = buildEmail();

        var templateLoader = Mockito.mock(TemplateLoader.class);
        when(domain.getId()).thenReturn("domain-id");

        final DictionaryProvider mockDictionaryProvider = Mockito.mock(DictionaryProvider.class);
        when(this.emailService.getDefaultDictionaryProvider()).thenReturn(mockDictionaryProvider);
        when(mockDictionaryProvider.getDictionaryFor(any())).thenReturn(new Properties());

        when(freemarkerConfiguration.getIncompatibleImprovements()).thenReturn(DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
        when(freemarkerConfiguration.getNamingConvention()).thenReturn(AUTO_DETECT_NAMING_CONVENTION);
        when(freemarkerConfiguration.getObjectWrapper()).thenReturn(new DefaultObjectWrapperBuilder(DEFAULT_INCOMPATIBLE_IMPROVEMENTS).build());

        var templateMock = new freemarker.template.Template("content", new StringReader(emailTemplate.getTemplate()), freemarkerConfiguration);
        when(templateLoader.findTemplateSource(anyString())).thenReturn(templateMock);
        when(freemarkerConfiguration.getTemplateLoader()).thenReturn(templateLoader);
        when(freemarkerConfiguration.getTemplate(anyString())).thenReturn(templateMock);

        when(emailManager.getEmail(anyString(), any(), anyInt())).thenReturn(emailTemplate);
        when(jwtBuilder.sign(any())).thenReturn("TOKEN");
        when(domainService.buildUrl(any(), any(), any())).thenReturn("http://localhost/reset");

        User user1 = new User();
        user1.setId("user-1");
        user1.setEmail("user1@gravitee.io");
        user1.setFirstName("User1");

        User user2 = new User();
        user2.setId("user-2");
        user2.setEmail("user2@gravitee.io");
        user2.setFirstName("User2");

        User user3 = new User();
        user3.setId("user-3");
        user3.setEmail("user3@gravitee.io");
        user3.setFirstName("User3");

        Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");

        List<EmailContainer> containers = Arrays.asList(
                new EmailContainer(user1, client, defaultStagingEmail(user1, client)),
                new EmailContainer(user2, client, defaultStagingEmail(user2, client)),
                new EmailContainer(user3, client, defaultStagingEmail(user3, client))
        );

        // Simulate BatchEmailException with failed emails
        List<String> failedEmails = Arrays.asList("user1@gravitee.io", "user3@gravitee.io");
        BatchEmailException batchException = new BatchEmailException("Batch email failed", failedEmails);

        Mockito.doThrow(batchException).when(this.emailService).batch(any());

        emailServiceSpy.batch(containers, 1);

        ArgumentCaptor<List<io.gravitee.am.common.email.Email>> captor = ArgumentCaptor.forClass(List.class);
        verify(this.emailService, times(1)).batch(captor.capture());

        // Verify that the exception contains all failed emails
        List<io.gravitee.am.common.email.Email> sentEmails = captor.getValue();
        assertThat(sentEmails).hasSize(3);

        // Verify audit service was called for failed emails only (user1 and user3)
        // 2 calls for failures
        verify(auditService, times(2)).report(argThat(builder -> builder.build(mapper).getOutcome().getStatus().equals(Status.FAILURE)));
        verify(auditService, times(1)).report(argThat(builder -> builder.build(mapper).getOutcome().getStatus().equals(Status.SUCCESS)));
    }

    @Test
    public void must_group_users_by_email_address_and_handle_batch_exception() throws IOException {
        var emailService = instantiateEmailService(true);

        emailServiceSpy = Mockito.spy(emailService);
        MockitoAnnotations.openMocks(this);

        final Email emailTemplate = buildEmail();

        var templateLoader = Mockito.mock(TemplateLoader.class);
        when(domain.getId()).thenReturn("domain-id");

        final DictionaryProvider mockDictionaryProvider = Mockito.mock(DictionaryProvider.class);
        when(this.emailService.getDefaultDictionaryProvider()).thenReturn(mockDictionaryProvider);
        when(mockDictionaryProvider.getDictionaryFor(any())).thenReturn(new Properties());

        when(freemarkerConfiguration.getIncompatibleImprovements()).thenReturn(DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
        when(freemarkerConfiguration.getNamingConvention()).thenReturn(AUTO_DETECT_NAMING_CONVENTION);
        when(freemarkerConfiguration.getObjectWrapper()).thenReturn(new DefaultObjectWrapperBuilder(DEFAULT_INCOMPATIBLE_IMPROVEMENTS).build());

        var templateMock = new freemarker.template.Template("content", new StringReader(emailTemplate.getTemplate()), freemarkerConfiguration);
        when(templateLoader.findTemplateSource(anyString())).thenReturn(templateMock);
        when(freemarkerConfiguration.getTemplateLoader()).thenReturn(templateLoader);
        when(freemarkerConfiguration.getTemplate(anyString())).thenReturn(templateMock);

        when(emailManager.getEmail(anyString(), any(), anyInt())).thenReturn(emailTemplate);
        when(jwtBuilder.sign(any())).thenReturn("TOKEN");
        when(domainService.buildUrl(any(), any(), any())).thenReturn("http://localhost/reset");

        // Create multiple users with the same email address
        User user1 = new User();
        user1.setId("user-1");
        user1.setEmail("shared@gravitee.io");
        user1.setFirstName("User1");

        User user2 = new User();
        user2.setId("user-2");
        user2.setEmail("shared@gravitee.io");
        user2.setFirstName("User2");

        User user3 = new User();
        user3.setId("user-3");
        user3.setEmail("different@gravitee.io");
        user3.setFirstName("User3");

        Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");

        List<EmailContainer> containers = Arrays.asList(
                new EmailContainer(user1, client, defaultStagingEmail(user1, client)),
                new EmailContainer(user2, client, defaultStagingEmail(user2, client)),
                new EmailContainer(user3, client, defaultStagingEmail(user3, client))
        );

        // Simulate BatchEmailException with the shared email failing
        List<String> failedEmails = Arrays.asList("shared@gravitee.io");
        BatchEmailException batchException = new BatchEmailException("Batch email failed", failedEmails);

        Mockito.doThrow(batchException).when(this.emailService).batch(any());

        emailServiceSpy.batch(containers, 1);

        // Verify audit service was called for both users with the shared email
        // 2 calls for user1 and user2 who share the same email
        verify(auditService, times(2)).report(argThat(builder -> builder.build(mapper).getOutcome().getStatus().equals(Status.FAILURE)));
    }

    @Test
    public void must_exclude_emails_that_fail_during_preparation() throws Exception {
        var emailService = instantiateEmailService(true);

        emailServiceSpy = Mockito.spy(emailService);
        MockitoAnnotations.openMocks(this);

        final Email emailTemplate = buildEmail();

        var templateLoader = Mockito.mock(TemplateLoader.class);
        when(domain.getId()).thenReturn("domain-id");

        final DictionaryProvider mockDictionaryProvider = Mockito.mock(DictionaryProvider.class);
        when(this.emailService.getDefaultDictionaryProvider()).thenReturn(mockDictionaryProvider);
        when(mockDictionaryProvider.getDictionaryFor(any())).thenReturn(new Properties());

        when(freemarkerConfiguration.getIncompatibleImprovements()).thenReturn(DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
        when(freemarkerConfiguration.getNamingConvention()).thenReturn(AUTO_DETECT_NAMING_CONVENTION);
        when(freemarkerConfiguration.getObjectWrapper()).thenReturn(new DefaultObjectWrapperBuilder(DEFAULT_INCOMPATIBLE_IMPROVEMENTS).build());

        // Create template that will work for most cases
        var templateMock = new freemarker.template.Template("content", new StringReader(emailTemplate.getTemplate()), freemarkerConfiguration);

        when(templateLoader.findTemplateSource(anyString())).thenReturn(templateMock);
        when(freemarkerConfiguration.getTemplateLoader()).thenReturn(templateLoader);

        // Make the second call to getTemplate throw an IOException to simulate failure during preparation
        when(freemarkerConfiguration.getTemplate(anyString()))
                .thenReturn(templateMock)
                .thenThrow(new IOException("Template loading failed"))
                .thenReturn(templateMock);

        when(emailManager.getEmail(anyString(), any(), anyInt())).thenReturn(emailTemplate);
        when(jwtBuilder.sign(any())).thenReturn("TOKEN");
        when(domainService.buildUrl(any(), any(), any())).thenReturn("http://localhost/reset");

        User user1 = new User();
        user1.setId("user-1");
        user1.setEmail("user1@gravitee.io");
        user1.setFirstName("User1");
        user1.setUsername("user1");

        User user2 = new User();
        user2.setId("user-2");
        user2.setEmail("user2@gravitee.io");
        user2.setFirstName("User2");
        user2.setUsername("user2");

        User user3 = new User();
        user3.setId("user-3");
        user3.setEmail("user3@gravitee.io");
        user3.setFirstName("User3");
        user3.setUsername("user3");

        Client client = new Client();
        client.setId("client-id");
        client.setClientId("client-id");

        List<EmailContainer> containers = Arrays.asList(
                new EmailContainer(user1, client, defaultStagingEmail(user1, client)),
                new EmailContainer(user2, client, defaultStagingEmail(user2, client)),
                new EmailContainer(user3, client, defaultStagingEmail(user3, client))
        );

        emailServiceSpy.batch(containers, 1);

        ArgumentCaptor<List<io.gravitee.am.common.email.Email>> captor = ArgumentCaptor.forClass(List.class);
        verify(this.emailService, times(1)).batch(captor.capture());

        // Only 2 emails should be sent (user2 failed during preparation)
        List<io.gravitee.am.common.email.Email> sentEmails = captor.getValue();
        assertThat(sentEmails).hasSize(2);

        // Verify audit service was called: 1 time for user2 failure during preparation, 2 times for successful emails
        verify(auditService, times(2)).report(argThat(builder -> builder.build(mapper).getOutcome().getStatus().equals(Status.SUCCESS)));
        verify(auditService, times(1)).report(argThat(builder -> builder.build(mapper).getOutcome().getStatus().equals(Status.FAILURE)));
    }

    private Email buildEmail() {
        final Email email = new Email();
        email.setEnabled(true);
        email.setFrom("from@gravitee.io");
        email.setExpiresAfter(300);
        email.setTemplate("some_template");
        email.setSubject("subject");
        email.setFromName("Gravitee.io");
        email.setContent("Reset password content");
        email.setClient("some # client");
        return email;
    }
}
