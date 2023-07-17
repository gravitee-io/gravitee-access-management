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

import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapperBuilder;
import io.gravitee.am.jwt.JWTBuilder;
import io.gravitee.am.management.service.EmailManager;
import io.gravitee.am.management.service.EmailService;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Email;
import io.gravitee.am.model.Template;
import io.gravitee.am.model.User;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.DomainService;
import io.gravitee.am.service.i18n.DictionaryProvider;
import io.gravitee.am.service.impl.I18nDictionaryService;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.Before;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    private AuditService auditService;

    @Mock
    @Qualifier("managementJwtBuilder")
    private JWTBuilder jwtBuilder;

    @Mock
    private DomainService domainService;

    @InjectMocks
    private EmailService emailServiceSpy;

    private I18nDictionaryService i18nDictionaryService;

    @Before
    public void init() {
        this.i18nDictionaryService = mock(I18nDictionaryService.class);
        when(i18nDictionaryService.findAll(any(), any())).thenReturn(Flowable.empty());
    }

    @Test
    public void must_not_send_email_due_to_not_enabled() throws IOException {
        var emailService = new EmailServiceImpl(
                false,
                "New user registration",
                86400,
                "Certificate will expire soon"
        );

        emailServiceSpy = Mockito.spy(emailService);
        MockitoAnnotations.openMocks(this);

        emailServiceSpy.send(mock(Domain.class), mock(Application.class), Template.REGISTRATION_CONFIRMATION, mock(User.class))
                .blockingAwait();

        verify(freemarkerConfiguration, times(0)).getTemplate(anyString());
        verify(emailManager, times(0)).getEmail(any(), any(), any(), anyInt());
        verify(auditService, times(0)).report(any());
        verify(this.emailService, times(0)).send(any());
    }

    @Test
    public void must_send_email() throws IOException {
        var emailService = new EmailServiceImpl(
                true,
                "New user registration",
                86400,
                "Certificate will expire soon"
        );

        final Email email = buildEmail();

        emailServiceSpy = Mockito.spy(emailService);
        MockitoAnnotations.openMocks(this);

        final DictionaryProvider mockDictionaryProvider = Mockito.mock(DictionaryProvider.class);
        when(this.emailService.getDictionaryProvider()).thenReturn(mockDictionaryProvider);
        when(mockDictionaryProvider.getDictionaryFor(any())).thenReturn(new Properties());

        when(freemarkerConfiguration.getIncompatibleImprovements()).thenReturn(DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
        when(freemarkerConfiguration.getNamingConvention()).thenReturn(AUTO_DETECT_NAMING_CONVENTION);
        when(freemarkerConfiguration.getObjectWrapper()).thenReturn(new DefaultObjectWrapperBuilder(DEFAULT_INCOMPATIBLE_IMPROVEMENTS).build());

        var templateLoader = mock(TemplateLoader.class);
        var templateMock = new freemarker.template.Template("content", new StringReader(email.getTemplate()), freemarkerConfiguration);
        when(templateLoader.findTemplateSource(anyString())).thenReturn(templateMock);
        when(this.freemarkerConfiguration.getTemplateLoader()).thenReturn(templateLoader);
        when(freemarkerConfiguration.getTemplate(anyString())).thenReturn(templateMock);

        when(emailManager.getEmail(any(), any(), any(), anyInt())).thenReturn(Maybe.just(email));

        var client = new Application();
        client.setId(email.getClient());
        var oauth = new ApplicationOAuthSettings();
        oauth.setClientId(email.getClient());
        var settings = new ApplicationSettings();
        settings.setOauth(oauth);
        client.setSettings(settings);

        var domain = new Domain();
        domain.setName("domain");
        domain.setPath("/domain");

        emailServiceSpy.send(domain, client, Template.REGISTRATION_CONFIRMATION, mock(User.class)).test()
                .assertNoErrors()
                .assertComplete();

        verify(freemarkerConfiguration, times(1)).getTemplate(eq(email.getTemplate() + ".html"));
        verify(emailManager, times(1)).getEmail(any(), any(), any(), anyInt());
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
