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
package io.gravitee.am.management.service;

import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import io.gravitee.am.management.service.impl.EmailManagerImpl;
import io.gravitee.am.model.*;
import io.gravitee.am.service.ApplicationService;
import io.gravitee.am.service.EmailTemplateService;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Date;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class EmailManagerTest {

    public static final String REFERENCE_ID = "domain1";
    public static final String CLIENT = "client1";
    public static final String APPLICATION_ID = "application1";

    @InjectMocks
    private EmailManagerImpl emailManager = new EmailManagerImpl();

    @Mock
    private StringTemplateLoader templateLoader;

    @Mock
    private Configuration configuration;

    @Mock
    private EmailTemplateService emailTemplateService;

    @Mock
    private ApplicationService applicationService;

    @Before
    public void setUp() {
        emailManager.setSubject("defaultSubject");
        emailManager.setDefaultFrom("defaultFrom");
    }

    @Test
    public void shouldGetTemplate() {
        when(emailTemplateService.findByTemplate(eq(ReferenceType.DOMAIN), eq(REFERENCE_ID), eq(Template.REGISTRATION_CONFIRMATION.template()))).thenReturn(Maybe.empty());
        when(applicationService.findByDomainAndClientId(eq(REFERENCE_ID), eq(CLIENT))).thenReturn(Maybe.empty());

        final User user = new User();
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId(REFERENCE_ID);
        user.setClient(CLIENT);
        Email email = emailManager.getEmail(Template.REGISTRATION_CONFIRMATION, user, "subject", 1000).blockingGet();

        Assert.assertNotNull(email);
        Assert.assertEquals(Template.REGISTRATION_CONFIRMATION.template(), email.getTemplate());
        Assert.assertEquals("defaultSubject", email.getSubject());
        Assert.assertEquals(1000, email.getExpiresAfter());
    }

    @Test
    public void shouldGetTemplate_domainTemplate() {
        // load templates
        Email domainEmail = new Email();
        domainEmail.setEnabled(true);
        domainEmail.setExpiresAfter(10000);
        domainEmail.setTemplate(Template.REGISTRATION_CONFIRMATION.template());
        domainEmail.setReferenceType(ReferenceType.DOMAIN);
        domainEmail.setReferenceId(REFERENCE_ID);
        domainEmail.setSubject("Domain subject");
        domainEmail.setUpdatedAt(new Date());

        emailManager.loadEmail(domainEmail);

        when(emailTemplateService.findByTemplate(eq(ReferenceType.DOMAIN), eq(REFERENCE_ID), eq(Template.REGISTRATION_CONFIRMATION.template()))).thenReturn(Maybe.just(domainEmail));
        when(applicationService.findByDomainAndClientId(eq(REFERENCE_ID), eq(CLIENT))).thenReturn(Maybe.empty());

        final User user = new User();
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId(REFERENCE_ID);
        user.setClient(CLIENT);
        String templateKey = Template.REGISTRATION_CONFIRMATION.template() + EmailManager.TEMPLATE_NAME_SEPARATOR + ReferenceType.DOMAIN + REFERENCE_ID;
        Email email = emailManager.getEmail(Template.REGISTRATION_CONFIRMATION, user, "subject", 1000).blockingGet();

        Assert.assertNotNull(email);
        Assert.assertEquals(templateKey, email.getTemplate());
        Assert.assertEquals("Domain subject", email.getSubject());
        Assert.assertEquals(10000, email.getExpiresAfter());
    }

    @Test
    public void shouldGetTemplate_clientTemplate() {
        // load templates
        Email domainEmail = new Email();
        domainEmail.setEnabled(true);
        domainEmail.setExpiresAfter(10000);
        domainEmail.setUpdatedAt(new Date());
        domainEmail.setTemplate(Template.REGISTRATION_CONFIRMATION.template());
        domainEmail.setReferenceType(ReferenceType.DOMAIN);
        domainEmail.setReferenceId(REFERENCE_ID);
        domainEmail.setSubject("Domain subject");

        Email clientEmail = new Email();
        clientEmail.setEnabled(true);
        clientEmail.setUpdatedAt(new Date());
        clientEmail.setExpiresAfter(10001);
        clientEmail.setTemplate(Template.REGISTRATION_CONFIRMATION.template());
        clientEmail.setReferenceType(ReferenceType.DOMAIN);
        clientEmail.setReferenceId(REFERENCE_ID);
        clientEmail.setClient(APPLICATION_ID);
        clientEmail.setSubject("Client subject");

        var application = new Application();
        application.setName("Application 1");
        application.setId(APPLICATION_ID);

        emailManager.loadEmail(domainEmail);
        emailManager.loadEmail(clientEmail);

        when(emailTemplateService.findByClientAndTemplate(eq(ReferenceType.DOMAIN), eq(REFERENCE_ID), eq(APPLICATION_ID), eq(Template.REGISTRATION_CONFIRMATION.template()))).thenReturn(Maybe.just(clientEmail));
        when(applicationService.findByDomainAndClientId(eq(REFERENCE_ID), eq(CLIENT))).thenReturn(Maybe.just(application));

        final User user = new User();
        user.setReferenceType(ReferenceType.DOMAIN);
        user.setReferenceId(REFERENCE_ID);
        user.setClient(CLIENT);

        String templateKey = Template.REGISTRATION_CONFIRMATION.template() + EmailManager.TEMPLATE_NAME_SEPARATOR + ReferenceType.DOMAIN + REFERENCE_ID;
        String templateClientKey = templateKey + EmailManager.TEMPLATE_NAME_SEPARATOR + APPLICATION_ID;

        Email email = emailManager.getEmail(Template.REGISTRATION_CONFIRMATION, user, "subject", 1000).blockingGet();

        Assert.assertNotNull(email);
        Assert.assertEquals(templateClientKey, email.getTemplate());
        Assert.assertEquals("Client subject", email.getSubject());
        Assert.assertEquals(10001, email.getExpiresAfter());
    }
}
