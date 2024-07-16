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

import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapperBuilder;
import io.gravitee.am.gateway.handler.common.email.impl.EmailServiceImpl;
import io.gravitee.am.jwt.JWTBuilder;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Email;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.DomainReadService;
import io.gravitee.am.service.i18n.DictionaryProvider;
import io.vertx.rxjava3.core.MultiMap;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Qualifier;

import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;

import static freemarker.template.Configuration.AUTO_DETECT_NAMING_CONVENTION;
import static freemarker.template.Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS;
import static java.util.concurrent.TimeUnit.DAYS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

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

    @InjectMocks
    private EmailService emailServiceSpy;

    @Test
    public void must_not_send_email_due_to_not_enabled() throws IOException {
        var emailService = new EmailServiceImpl(
                false,
                "Please reset your password",
                300,
                "Account has been locked",
                86400,
                "Verification Code",
                300,
                "Please verify Attempt",
                "Complete your registration",
                Long.valueOf(DAYS.toSeconds(7)).intValue());

        emailServiceSpy = Mockito.spy(emailService);
        MockitoAnnotations.openMocks(this);

        emailServiceSpy.send(Template.RESET_PASSWORD, Mockito.mock(User.class), Mockito.mock(Client.class));

        verify(freemarkerConfiguration, times(0)).getTemplate(anyString());
        verify(emailManager, times(0)).getEmail(any(), any(), anyInt());
        verify(auditService, times(0)).report(any());
        verify(this.emailService, times(0)).send(any());
    }

    @Test
    public void must_send_email() throws IOException {
        var emailService = new EmailServiceImpl(
                true,
                "Please reset your password",
                300,
                "Account has been locked",
                86400,
                "Verification Code",
                300,
                "Please verify Attempt",
                "Complete your registration",
                Long.valueOf(DAYS.toSeconds(7)).intValue());
        emailServiceSpy = Mockito.spy(emailService);
        MockitoAnnotations.openMocks(this);

        final Email email = buildEmail();

        var templateLoader = Mockito.mock(TemplateLoader.class);

        final DictionaryProvider mockDictionaryProvider = Mockito.mock(DictionaryProvider.class);
        when(this.emailService.getDictionaryProvider()).thenReturn(mockDictionaryProvider);
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
        var emailService = new EmailServiceImpl(
                true,
                "Please reset your password",
                300,
                "Account has been locked",
                86400,
                "Verification Code",
                300,
                "Please verify Attempt",
                "Complete your registration",
                Long.valueOf(DAYS.toSeconds(7)).intValue());
        emailServiceSpy = Mockito.spy(emailService);
        MockitoAnnotations.openMocks(this);

        final Email email = buildEmail();

        var templateLoader = Mockito.mock(TemplateLoader.class);

        final DictionaryProvider mockDictionaryProvider = Mockito.mock(DictionaryProvider.class);
        when(this.emailService.getDictionaryProvider()).thenReturn(mockDictionaryProvider);
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
