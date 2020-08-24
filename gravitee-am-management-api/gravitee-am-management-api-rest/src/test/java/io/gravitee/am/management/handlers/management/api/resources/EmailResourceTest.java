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
import io.gravitee.am.service.exception.EmailNotFoundException;
import io.gravitee.am.service.model.UpdateEmail;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.junit.Ignore;
import org.junit.Test;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EmailResourceTest extends JerseySpringTest {

    @Test
    @Ignore
    public void shouldUpdate() {
        final String emailId = "email-1";
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        UpdateEmail updateEmail = new UpdateEmail();
        updateEmail.setFrom("test");
        updateEmail.setSubject("subject");
        updateEmail.setContent("content");
        updateEmail.setExpiresAfter(1000);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Single.just(new Email())).when(emailTemplateService).update(eq(domainId), eq(emailId), any(), any(User.class));

        final Response response = target("domains")
                .path(domainId)
                .path("emails")
                .path(emailId)
                .request().put(Entity.json(updateEmail));
        assertEquals(HttpStatusCode.OK_200, response.getStatus());
    }

    @Test
    public void shouldDelete() {
        final String emailId = "email-1";
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Completable.complete()).when(emailTemplateService).delete(eq(emailId), any());

        final Response response = target("domains")
                .path(domainId)
                .path("emails")
                .path(emailId)
                .request().delete();
        assertEquals(HttpStatusCode.NO_CONTENT_204, response.getStatus());
    }

    @Test
    public void shouldDelete_emailNotFound() {
        final String emailId = "email-1";
        final String domainId = "domain-1";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Completable.error(new EmailNotFoundException(emailId))).when(emailTemplateService).delete(eq(emailId), any());

        final Response response = target("domains")
                .path(domainId)
                .path("emails")
                .path(emailId)
                .request().delete();
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

}
