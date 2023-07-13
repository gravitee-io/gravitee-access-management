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
import io.gravitee.am.model.Form;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Template;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewForm;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.doReturn;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class FormsResourceTest extends JerseySpringTest {
    private final String DOMAIN_ID = "domain-1";
    private Domain mockDomain;

    @Before
    public void setUp() {
        mockDomain= new Domain();
        mockDomain.setId(DOMAIN_ID);
    }

    @Test
    public void shouldGetForm() {
        final Form mockForm = new Form();
        mockForm.setId("form-1-id");
        mockForm.setTemplate(Template.LOGIN.template());
        mockForm.setReferenceType(ReferenceType.DOMAIN);
        mockForm.setReferenceId(DOMAIN_ID);

        final Form defaultMockForm = new Form();
        defaultMockForm.setId("default-form-1-id");
        defaultMockForm.setTemplate(Template.LOGIN.template());
        defaultMockForm.setReferenceType(ReferenceType.DOMAIN);
        defaultMockForm.setReferenceId(DOMAIN_ID);

        doReturn(Maybe.just(mockForm)).when(formService).findByDomainAndTemplate(DOMAIN_ID, Template.LOGIN.template());
        doReturn(Single.just(defaultMockForm)).when(formService).getDefaultByDomainAndTemplate(DOMAIN_ID, Template.LOGIN.template());

        final Response response = target("domains")
                .path(DOMAIN_ID).path("forms")
                .queryParam("template", Template.LOGIN)
                .request()
                .get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final Form responseEntity = readEntity(response, Form.class);
        assertTrue(responseEntity.getId().equals("form-1-id"));
    }

    @Test
    public void shouldGetDefaultForm() {
        final Form defaultMockForm = new Form();
        defaultMockForm.setId("default-form-1-id");
        defaultMockForm.setTemplate(Template.LOGIN.template());
        defaultMockForm.setReferenceType(ReferenceType.DOMAIN);
        defaultMockForm.setReferenceId(DOMAIN_ID);

        doReturn(Maybe.empty()).when(formService).findByDomainAndTemplate(DOMAIN_ID, Template.LOGIN.template());
        doReturn(Single.just(defaultMockForm)).when(formService).getDefaultByDomainAndTemplate(DOMAIN_ID, Template.LOGIN.template());

        final Response response = target("domains")
                .path(DOMAIN_ID).path("forms")
                .queryParam("template", Template.LOGIN)
                .request()
                .get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final Form responseEntity = readEntity(response, Form.class);
        assertTrue(responseEntity.getId().equals("default-form-1-id"));
    }

    @Test
    public void shouldGetForms_technicalManagementException() {
        doReturn(Maybe.error(new TechnicalManagementException("error occurs"))).when(formService).findByDomainAndTemplate(DOMAIN_ID, Template.LOGIN.template());

        final Response response = target("domains")
                .path(DOMAIN_ID).path("forms")
                .queryParam("template", Template.LOGIN)
                .request()
                .get();
        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
    }

    @Test
    @Ignore
    public void shouldCreate() {
        NewForm newForm = new NewForm();
        newForm.setTemplate(Template.LOGIN);
        newForm.setContent("content");

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(DOMAIN_ID);
        doReturn(Single.just(new Form())).when(formService).create(eq(DOMAIN_ID), any(), any(User.class));

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("forms")
                .request().post(Entity.json(newForm));
        assertEquals(HttpStatusCode.CREATED_201, response.getStatus());
    }
}
