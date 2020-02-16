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
package io.gravitee.am.gateway.handler.email;

import io.gravitee.am.gateway.handler.common.email.EmailManager;
import io.gravitee.am.gateway.handler.common.email.impl.EmailManagerImpl;
import io.gravitee.am.model.Email;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Template;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EmailManagerTest {

    private EmailManager emailManager = new EmailManagerImpl();

    @Before
    public void setUp() {
        ((EmailManagerImpl) emailManager).setSubject("defaultSubject");
        ((EmailManagerImpl) emailManager).setDefaultFrom("defaultFrom");
    }

    @Test
    public void shouldGetTemplate() {
        Email email = emailManager.getEmail(Template.RESET_PASSWORD.template(), "subject", 1000);

        Assert.assertNotNull(email);
        Assert.assertEquals(Template.RESET_PASSWORD.template(), email.getTemplate());
        Assert.assertEquals("defaultSubject", email.getSubject());
        Assert.assertEquals(1000, email.getExpiresAfter());
    }

    @Test
    public void shouldGetTemplate_domainTemplate() {
        // load templates
        Email domainEmail = new Email();
        domainEmail.setEnabled(true);
        domainEmail.setExpiresAfter(10000);
        domainEmail.setTemplate(Template.RESET_PASSWORD.template());
        domainEmail.setReferenceType(ReferenceType.DOMAIN);
        domainEmail.setReferenceId("domain1");
        domainEmail.setSubject("Domain subject");

        String templateKey = Template.RESET_PASSWORD.template();
        ConcurrentMap<String, Email> templateNames = new ConcurrentHashMap<>();
        templateNames.put(templateKey, domainEmail);

        ((EmailManagerImpl) emailManager).setEmailTemplates(templateNames);

        Email email = emailManager.getEmail(templateKey, "subject", 1000);

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
        domainEmail.setTemplate(Template.RESET_PASSWORD.template());
        domainEmail.setReferenceType(ReferenceType.DOMAIN);
        domainEmail.setReferenceId("domain1");
        domainEmail.setSubject("Domain subject");

        Email clientEmail = new Email();
        clientEmail.setEnabled(true);
        clientEmail.setExpiresAfter(10001);
        clientEmail.setTemplate(Template.RESET_PASSWORD.template());
        clientEmail.setReferenceType(ReferenceType.DOMAIN);
        clientEmail.setReferenceId("domain1");
        clientEmail.setClient("client1");
        clientEmail.setSubject("Client subject");

        String templateKey = Template.RESET_PASSWORD.template();
        String templateClientKey = templateKey + EmailManager.TEMPLATE_NAME_SEPARATOR + "client1";
        ConcurrentMap<String, Email> templateNames = new ConcurrentHashMap<>();
        templateNames.put(templateKey, domainEmail);
        templateNames.put(templateClientKey, clientEmail);

        ((EmailManagerImpl) emailManager).setEmailTemplates(templateNames);

        Email email = emailManager.getEmail(templateClientKey, "subject", 1000);

        Assert.assertNotNull(email);
        Assert.assertEquals(templateClientKey, email.getTemplate());
        Assert.assertEquals("Client subject", email.getSubject());
        Assert.assertEquals(10001, email.getExpiresAfter());
    }
}
