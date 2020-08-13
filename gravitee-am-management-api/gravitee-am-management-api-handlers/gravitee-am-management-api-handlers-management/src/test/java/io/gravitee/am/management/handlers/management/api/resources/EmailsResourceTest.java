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
package io.gravitee.am.management.handlers.management.api.resources;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Email;
import io.gravitee.am.model.Template;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewEmail;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.junit.Ignore;
import org.junit.Test;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EmailsResourceTest extends JerseySpringTest {

    @Test
    public void shouldGetEmail() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final Email mockEmail = new Email();
        mockEmail.setId("email-1-id");
        mockEmail.setTemplate(Template.LOGIN.template());
        mockEmail.setDomain(domainId);

        doReturn(Maybe.just(mockEmail)).when(emailTemplateService).findByDomainAndTemplate(domainId, Template.LOGIN.template());

        final Response response = target("domains").path(domainId).path("emails").queryParam("template", Template.LOGIN).request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final Email responseEntity = response.readEntity(Email.class);
        assertTrue(responseEntity.getId().equals("email-1-id"));
    }

    @Test
    public void shouldGetEmails_technicalManagementException() {
        final String domainId = "domain-1";
        doReturn(Maybe.error(new TechnicalManagementException("error occurs"))).when(emailTemplateService).findByDomainAndTemplate(domainId, Template.LOGIN.template());

        final Response response = target("domains").path(domainId).path("emails").queryParam("template", Template.LOGIN).request().get();
        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
    }

    @Test
    @Ignore
    public void shouldCreate() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        NewEmail newEmail = new NewEmail();
        newEmail.setTemplate(Template.LOGIN);
        newEmail.setFrom("test");
        newEmail.setSubject("subject");
        newEmail.setContent("content");
        newEmail.setExpiresAfter(1000);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(new Email())).when(emailTemplateService).create(eq(domainId), any(), any(User.class));

        final Response response = target("domains")
                .path(domainId)
                .path("emails")
                .request().post(Entity.json(newEmail));
        assertEquals(HttpStatusCode.CREATED_201, response.getStatus());
    }
}
