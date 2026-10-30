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
package io.gravitee.am.management.handlers.management.api.resources.organizations.environments.domains;

import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.service.exception.FormNotFoundException;
import io.gravitee.am.service.model.UpdateForm;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

public class ApplicationFormResourceTest extends JerseySpringTest {

    private static final String DOMAIN_ID = "domain-a";
    private static final String APPLICATION_ID = "app-x";
    private static final String TEMPLATE_ID = "form-1";

    @BeforeEach
    public void resetMocks() {
        reset(formService);
        doReturn(Maybe.just(domain())).when(domainService).findById(DOMAIN_ID);
        doReturn(Maybe.just(new Application())).when(applicationService).findById(DOMAIN_ID, APPLICATION_ID);
    }

    @Test
    public void shouldUpdate_returns404_whenTemplateIsNotOwnedByApplication() {
        // domain-level template or template of another application
        doReturn(Single.error(new FormNotFoundException(TEMPLATE_ID)))
                .when(formService).update(eq(DOMAIN_ID), eq(APPLICATION_ID), eq(TEMPLATE_ID), any(UpdateForm.class), any());

        final Response response = target().request().put(Entity.json(update()));

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
        verify(formService, never()).update(eq(DOMAIN_ID), eq(TEMPLATE_ID), any(UpdateForm.class), any());
    }

    @Test
    public void shouldDelete_returns404_whenTemplateIsNotOwnedByApplication() {
        doReturn(Completable.error(new FormNotFoundException(TEMPLATE_ID)))
                .when(formService).delete(eq(DOMAIN_ID), eq(APPLICATION_ID), eq(TEMPLATE_ID), any());

        final Response response = target().request().delete();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
        verify(formService, never()).delete(eq(DOMAIN_ID), eq(TEMPLATE_ID), any());
    }

    @Test
    public void shouldDelete_returns204_whenTemplateIsOwnedByApplication() {
        doReturn(Completable.complete())
                .when(formService).delete(eq(DOMAIN_ID), eq(APPLICATION_ID), eq(TEMPLATE_ID), any());

        final Response response = target().request().delete();

        assertEquals(HttpStatusCode.NO_CONTENT_204, response.getStatus());
    }

    private WebTarget target() {
        return target("domains").path(DOMAIN_ID).path("applications").path(APPLICATION_ID).path("forms").path(TEMPLATE_ID);
    }

    private static UpdateForm update() {
        final UpdateForm update = new UpdateForm();
        update.setContent("c");
        return update;
    }

    private static Domain domain() {
        final Domain domain = new Domain();
        domain.setId(DOMAIN_ID);
        return domain;
    }
}
