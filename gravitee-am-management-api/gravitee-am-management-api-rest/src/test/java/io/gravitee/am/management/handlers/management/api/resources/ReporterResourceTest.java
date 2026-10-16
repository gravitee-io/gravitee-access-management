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

import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Organization;
import io.gravitee.am.model.Reference;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class ReporterResourceTest extends JerseySpringTest {

    @Test
    public void shouldGetReporter_wrongDomain() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);
        final String reporterId = "reporter-of-another-domain";

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.empty()).when(reporterService).findById(Reference.domain(domainId), reporterId);

        final Response response = target("domains").path(domainId).path("reporters").path(reporterId).request().get();
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldGetOrganizationReporter_wrongOrganization() {
        final String organizationId = "organization-id";
        final String reporterId = "reporter-of-another-organization";

        doReturn(Single.just(new Organization())).when(organizationService).findById(organizationId);
        doReturn(Maybe.empty()).when(reporterService).findById(Reference.organization(organizationId), reporterId);

        final Response response = target("organizations").path(organizationId).path("reporters").path(reporterId).request().get();
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldNotDeleteReporter_wrongDomain() {
        final String domainId = "domain-id";
        final Domain mockDomain = new Domain();
        mockDomain.setId(domainId);
        final String reporterId = "reporter-of-another-domain";

        doReturn(Maybe.just(mockDomain)).when(domainService).findById(domainId);
        doReturn(Maybe.empty()).when(reporterService).findById(Reference.domain(domainId), reporterId);

        final Response response = target("domains").path(domainId).path("reporters").path(reporterId).request().delete();
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
        verify(reporterService, never()).delete(eq(reporterId), any());
    }

    @Test
    public void shouldNotDeleteOrganizationReporter_wrongOrganization() {
        final String organizationId = "organization-id";
        final String reporterId = "reporter-of-another-organization";

        doReturn(Single.just(new Organization())).when(organizationService).findById(organizationId);
        doReturn(Maybe.empty()).when(reporterService).findById(Reference.organization(organizationId), reporterId);

        final Response response = target("organizations").path(organizationId).path("reporters").path(reporterId).request().delete();
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
        verify(reporterService, never()).delete(eq(reporterId), any());
    }
}
