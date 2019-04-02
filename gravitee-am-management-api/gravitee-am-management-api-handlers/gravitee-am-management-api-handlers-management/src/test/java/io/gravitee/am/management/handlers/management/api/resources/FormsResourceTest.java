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
import io.gravitee.am.model.Form;
import io.gravitee.am.model.Template;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewForm;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.Maybe;
import io.reactivex.Single;
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
public class FormsResourceTest extends JerseySpringTest {

    @Test
    public void shouldGetForm() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        final Form mockForm = new Form();
        mockForm.setId("form-1-id");
        mockForm.setTemplate(Template.LOGIN.template());
        mockForm.setDomain(domainId);

        doReturn(Maybe.just(mockForm)).when(formService).findByDomainAndTemplate(domainId, Template.LOGIN.template());

        final Response response = target("domains")
                .path(domainId).path("forms")
                .queryParam("template", Template.LOGIN)
                .request()
                .get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final Email responseEntity = response.readEntity(Email.class);
        assertTrue(responseEntity.getId().equals("form-1-id"));
    }

    @Test
    public void shouldGetForms_technicalManagementException() {
        final String domainId = "domain-1";
        doReturn(Maybe.error(new TechnicalManagementException("error occurs"))).when(formService).findByDomainAndTemplate(domainId, Template.LOGIN.template());

        final Response response = target("domains")
                .path(domainId).path("forms")
                .queryParam("template", Template.LOGIN)
                .request()
                .get();
        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
    }

    @Test
    public void shouldCreate() {
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        NewForm newForm = new NewForm();
        newForm.setTemplate(Template.LOGIN);
        newForm.setContent("content");

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(new Form())).when(formService).create(eq(domainId), any(), any(User.class));

        final Response response = target("domains")
                .path(domainId)
                .path("forms")
                .request().post(Entity.json(newForm));
        assertEquals(HttpStatusCode.CREATED_201, response.getStatus());
    }
}
