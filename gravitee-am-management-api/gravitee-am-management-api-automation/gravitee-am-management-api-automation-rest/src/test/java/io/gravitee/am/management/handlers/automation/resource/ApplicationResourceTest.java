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
package io.gravitee.am.management.handlers.automation.resource;

import io.gravitee.am.management.handlers.automation.AutomationJerseySpringTest;
import io.gravitee.am.management.handlers.automation.model.AutomationApplication;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ManagedBy;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
class ApplicationResourceTest extends AutomationJerseySpringTest {

    private static final String DOMAIN_KEY = "customer-auth";
    private static final String APP_KEY = "mobile-app";
    private static final String BROWNFIELD_ID = "11111111-2222-3333-4444-555555555555";
    private final String domainId = AutomationIds.domainId(ENV_ID, DOMAIN_KEY);
    private final String appId = AutomationIds.applicationId(domainId, APP_KEY);

    @BeforeEach
    void stubDomain() {
        Domain domain = new Domain();
        domain.setId(domainId);
        domain.setAutomationKey(DOMAIN_KEY);
        domain.setReferenceId(ENV_ID);
        domain.setManagedBy(ManagedBy.AUTOMATION_API);
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain));
    }

    private Application application(String id, String key, ManagedBy managedBy) {
        Application app = new Application();
        app.setId(id);
        app.setDomain(domainId);
        app.setAutomationKey(key);
        app.setManagedBy(managedBy);
        app.setName("Mobile app");
        app.setCertificate("cert-uuid");
        return app;
    }

    @Test
    void get_returns_the_application_by_key() {
        when(applicationService.findById(domainId, appId)).thenReturn(Maybe.just(application(appId, APP_KEY, ManagedBy.AUTOMATION_API)));

        Response response = applicationsTarget(DOMAIN_KEY).path(APP_KEY).request().get();

        assertEquals(200, response.getStatus());
        assertEquals(APP_KEY, readEntity(response, AutomationApplication.class).getAutomationKey());
    }

    @Test
    void get_returns_404_when_not_automation_managed() {
        when(applicationService.findById(domainId, appId)).thenReturn(Maybe.just(application(appId, APP_KEY, null)));

        Response response = applicationsTarget(DOMAIN_KEY).path(APP_KEY).request().get();

        assertEquals(404, response.getStatus());
    }

    @Test
    void get_returns_a_brownfield_application_by_id() {
        when(applicationService.findById(domainId, BROWNFIELD_ID)).thenReturn(Maybe.just(application(BROWNFIELD_ID, null, null)));

        Response response = applicationsTarget(DOMAIN_KEY).path("id:" + BROWNFIELD_ID).request().get();

        assertEquals(200, response.getStatus());
        assertEquals("id:cert-uuid", readEntity(response, AutomationApplication.class).getCertificate());
    }

    @Test
    void get_is_forbidden_without_permission() {
        denyPermission();

        Response response = applicationsTarget(DOMAIN_KEY).path(APP_KEY).request().get();

        assertEquals(403, response.getStatus());
        verify(applicationService, never()).findById(any(), any());
    }

    @Test
    void delete_removes_the_application() {
        when(applicationService.findById(domainId, appId)).thenReturn(Maybe.just(application(appId, APP_KEY, ManagedBy.AUTOMATION_API)));
        when(applicationService.delete(eq(appId), any(), any(Domain.class))).thenReturn(Completable.complete());

        Response response = applicationsTarget(DOMAIN_KEY).path(APP_KEY).request().delete();

        assertEquals(204, response.getStatus());
        verify(applicationService).delete(eq(appId), any(), any(Domain.class));
    }

    @Test
    void delete_returns_204_when_absent() {
        when(applicationService.findById(domainId, appId)).thenReturn(Maybe.empty());

        Response response = applicationsTarget(DOMAIN_KEY).path(APP_KEY).request().delete();

        assertEquals(204, response.getStatus());
        verify(applicationService, never()).delete(any(), any(), any(Domain.class));
    }
}
