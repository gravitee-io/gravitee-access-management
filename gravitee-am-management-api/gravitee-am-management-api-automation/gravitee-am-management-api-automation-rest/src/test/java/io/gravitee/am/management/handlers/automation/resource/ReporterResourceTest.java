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
import io.gravitee.am.management.handlers.automation.model.AutomationReporter;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ManagedBy;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.Reporter;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
class ReporterResourceTest extends AutomationJerseySpringTest {

    private static final String DOMAIN_KEY = "customer-auth";
    private static final String REPORTER_KEY = "audit-log";
    private static final String BROWNFIELD_REPORTER_ID = "88888888-9999-aaaa-bbbb-cccccccccccc";
    private final String domainId = AutomationIds.domainId(ENV_ID, DOMAIN_KEY);
    private final String reporterId = AutomationIds.reporterId(domainId, REPORTER_KEY);

    private Domain domain() {
        Domain domain = new Domain();
        domain.setId(domainId);
        domain.setAutomationKey(DOMAIN_KEY);
        domain.setReferenceId(ENV_ID);
        domain.setManagedBy(ManagedBy.AUTOMATION_API);
        return domain;
    }

    private Reporter reporter(boolean system, ManagedBy managedBy) {
        return Reporter.builder()
                .id(reporterId)
                .automationKey(REPORTER_KEY)
                .name("Audit log")
                .type("reporter-am-file")
                .configuration("{}")
                .enabled(true)
                .system(system)
                .dataType("AUDIT")
                .reference(Reference.domain(domainId))
                .managedBy(managedBy)
                .build();
    }

    private Reporter brownfieldReporter(boolean system, ManagedBy managedBy) {
        return Reporter.builder()
                .id(BROWNFIELD_REPORTER_ID)
                .name("Legacy audit")
                .type("reporter-am-file")
                .system(system)
                .reference(Reference.domain(domainId))
                .managedBy(managedBy)
                .build();
    }

    private Response getRequest() {
        return reportersTarget(DOMAIN_KEY).path(REPORTER_KEY).request().get();
    }

    @Test
    void get_returns_reporter_when_found() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(reporterService.findByReference(eq(Reference.domain(domainId))))
                .thenReturn(Flowable.just(reporter(true, ManagedBy.AUTOMATION_API)));

        Response response = getRequest();

        assertEquals(200, response.getStatus());
        AutomationReporter body = readEntity(response, AutomationReporter.class);
        assertEquals(REPORTER_KEY, body.getAutomationKey());
        assertTrue(body.isSystem(), "system reporter must be projected as system:true");
    }

    @Test
    void get_returns_404_when_reporter_absent() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(reporterService.findByReference(eq(Reference.domain(domainId)))).thenReturn(Flowable.empty());

        assertEquals(404, getRequest().getStatus());
    }

    @Test
    void get_returns_404_when_reporter_not_automation_managed() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(reporterService.findByReference(eq(Reference.domain(domainId))))
                .thenReturn(Flowable.just(reporter(true, null)));

        assertEquals(404, getRequest().getStatus());
    }

    @Test
    void get_returns_404_when_domain_absent() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.empty());

        assertEquals(404, getRequest().getStatus());
    }

    @Test
    void delete_returns_204_and_bypasses_system_guard() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(reporterService.findByReference(eq(Reference.domain(domainId))))
                .thenReturn(Flowable.just(reporter(true, ManagedBy.AUTOMATION_API)));
        when(reporterService.delete(eq(reporterId), any(), eq(true))).thenReturn(Completable.complete());

        Response response = reportersTarget(DOMAIN_KEY).path(REPORTER_KEY).request().delete();

        assertEquals(204, response.getStatus());
    }

    @Test
    void delete_returns_204_when_reporter_absent() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(reporterService.findByReference(eq(Reference.domain(domainId)))).thenReturn(Flowable.empty());

        Response response = reportersTarget(DOMAIN_KEY).path(REPORTER_KEY).request().delete();

        assertEquals(204, response.getStatus());
        verify(reporterService, never()).delete(anyString(), any(), anyBoolean());
    }

    @Test
    void delete_returns_204_when_domain_absent() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.empty());

        Response response = reportersTarget(DOMAIN_KEY).path(REPORTER_KEY).request().delete();

        assertEquals(204, response.getStatus());
        verify(reporterService, never()).delete(anyString(), any(), anyBoolean());
    }

    @Test
    void delete_by_id_keeps_system_guard_for_a_reporter_it_does_not_own() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(reporterService.findById(eq(BROWNFIELD_REPORTER_ID)))
                .thenReturn(Maybe.just(brownfieldReporter(true, ManagedBy.NONE)));
        when(reporterService.delete(eq(BROWNFIELD_REPORTER_ID), any(), eq(false))).thenReturn(Completable.complete());

        Response response = reportersTarget(DOMAIN_KEY).path("id:" + BROWNFIELD_REPORTER_ID).request().delete();

        assertEquals(204, response.getStatus());
        // a reporter the Automation API does not manage keeps the system guard (removeSystemReporter=false)
        verify(reporterService).delete(eq(BROWNFIELD_REPORTER_ID), any(), eq(false));
        verify(reporterService, never()).delete(anyString(), any(), eq(true));
    }

    @Test
    void delete_by_id_waives_system_guard_for_an_automation_managed_reporter() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(reporterService.findById(eq(BROWNFIELD_REPORTER_ID)))
                .thenReturn(Maybe.just(brownfieldReporter(true, ManagedBy.AUTOMATION_API)));
        when(reporterService.delete(eq(BROWNFIELD_REPORTER_ID), any(), eq(true))).thenReturn(Completable.complete());

        Response response = reportersTarget(DOMAIN_KEY).path("id:" + BROWNFIELD_REPORTER_ID).request().delete();

        assertEquals(204, response.getStatus());
        // the Automation API owns this reporter, so it may remove it even if system (removeSystemReporter=true)
        verify(reporterService).delete(eq(BROWNFIELD_REPORTER_ID), any(), eq(true));
    }

    @Test
    void get_returns_403_without_revealing_existence_when_not_permitted() {
        denyPermission();
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.empty());

        assertEquals(403, getRequest().getStatus());
    }

    @Test
    void delete_returns_403_when_not_permitted() {
        denyPermission();

        Response response = reportersTarget(DOMAIN_KEY).path(REPORTER_KEY).request().delete();

        assertEquals(403, response.getStatus());
        verify(reporterService, never()).delete(anyString(), any(), anyBoolean());
    }
}
