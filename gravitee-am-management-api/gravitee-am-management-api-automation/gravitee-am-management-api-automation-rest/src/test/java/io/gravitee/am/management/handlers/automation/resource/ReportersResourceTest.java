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
import io.gravitee.am.service.exception.ReporterConfigurationException;
import io.gravitee.am.service.exception.InvalidPluginConfigurationException;
import io.gravitee.am.service.exception.PluginNotDeployedException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
class ReportersResourceTest extends AutomationJerseySpringTest {

    private static final String DOMAIN_KEY = "customer-auth";
    private final String domainId = AutomationIds.domainId(ENV_ID, DOMAIN_KEY);
    private final Reference reference = Reference.domain(domainId);

    private Domain domain() {
        Domain domain = new Domain();
        domain.setId(domainId);
        domain.setAutomationKey(DOMAIN_KEY);
        domain.setReferenceId(ENV_ID);
        domain.setManagedBy(ManagedBy.AUTOMATION_API);
        return domain;
    }

    private Reporter reporter(String id, String key, boolean system, ManagedBy managedBy) {
        return Reporter.builder()
                .id(id)
                .automationKey(key)
                .name(key)
                .type("reporter-am-file")
                .configuration("{}")
                .enabled(true)
                .system(system)
                .dataType("AUDIT")
                .reference(reference)
                .managedBy(managedBy)
                .build();
    }

    private AutomationReporter definition(String key, boolean isSystem) {
        AutomationReporter in = new AutomationReporter();
        in.setAutomationKey(key);
        in.setName(key);
        in.setType("reporter-am-file");
        in.setConfiguration("{}");
        in.setSystem(isSystem);
        return in;
    }

    /**
     * A minimal payload — only {@code key} and {@code system:true} — the AAPI accepts on the system
     * path. {@code name}/{@code type}/{@code configuration} are ignored when {@code system} is true,
     * so this models the way a caller is meant to provision a system reporter.
     */
    private AutomationReporter systemDefinition(String key) {
        AutomationReporter in = new AutomationReporter();
        in.setAutomationKey(key);
        in.setSystem(true);
        return in;
    }

    @Test
    void list_returns_only_automation_managed_sorted() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(reporterService.findByReference(eq(reference)))
                .thenReturn(Flowable.just(
                        reporter("id-b", "beta", false, ManagedBy.AUTOMATION_API),
                        reporter("id-legacy", "legacy", true, null),
                        reporter("id-a", "alpha", false, ManagedBy.AUTOMATION_API)));

        Response response = reportersTarget(DOMAIN_KEY).request().get();

        assertEquals(200, response.getStatus());
        List<AutomationReporter> body = readListEntity(response, AutomationReporter.class);
        assertEquals(2, body.size());
        assertEquals("alpha", body.get(0).getAutomationKey());
        assertEquals("beta", body.get(1).getAutomationKey());
    }

    @Test
    void put_creates_when_absent() {
        String reporterId = AutomationIds.reporterId(domainId, "audit-log");
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(reporterService.findByReference(eq(reference))).thenReturn(Flowable.empty());
        when(reporterService.create(eq(reference), any(), any(), eq(false)))
                .thenReturn(Single.just(reporter(reporterId, "audit-log", false, ManagedBy.AUTOMATION_API)));

        Response response = put(reportersTarget(DOMAIN_KEY), definition("audit-log", false));

        assertEquals(200, response.getStatus());
        assertEquals("audit-log", readEntity(response, AutomationReporter.class).getAutomationKey());
    }

    @Test
    void put_creates_system_from_config() {
        String reporterId = AutomationIds.reporterId(domainId, "sys-reporter");
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(reporterService.findByReference(eq(reference))).thenReturn(Flowable.empty());
        when(reporterService.createSystem(eq(reference), eq(reporterId), eq("sys-reporter"), any()))
                .thenReturn(Single.just(reporter(reporterId, "sys-reporter", true, ManagedBy.AUTOMATION_API)));

        Response response = put(reportersTarget(DOMAIN_KEY), systemDefinition("sys-reporter"));

        assertEquals(200, response.getStatus());
        assertTrue(readEntity(response, AutomationReporter.class).isSystem());
        // The payload path must never run for a system create.
        verify(reporterService, never()).create(eq(reference), any(), any(), eq(true));
    }

    @Test
    void put_existing_system_is_idempotent_noop() {
        // Re-PUTting a system reporter must not touch the service; gravitee.yaml owns the config.
        String reporterId = AutomationIds.reporterId(domainId, "sys-reporter");
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(reporterService.findByReference(eq(reference)))
                .thenReturn(Flowable.just(reporter(reporterId, "sys-reporter", true, ManagedBy.AUTOMATION_API)));

        Response response = put(reportersTarget(DOMAIN_KEY), systemDefinition("sys-reporter"));

        assertEquals(200, response.getStatus());
        verify(reporterService, never()).update(eq(reference), anyString(), any(), any(), anyBoolean());
        verify(reporterService, never()).create(eq(reference), any(), any(), eq(true));
        verify(reporterService, never()).createSystem(eq(reference), anyString(), anyString(), any());
    }

    @Test
    void put_rejects_non_system_missing_required_fields() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(reporterService.findByReference(eq(reference))).thenReturn(Flowable.empty());

        AutomationReporter def = new AutomationReporter();
        def.setAutomationKey("incomplete");
        // name, type, configuration intentionally left null

        Response response = put(reportersTarget(DOMAIN_KEY), def);

        assertEquals(400, response.getStatus());
        verify(reporterService, never()).create(eq(reference), any(), any(), eq(false));
    }

    @Test
    void put_rejects_non_system_database_reporter_type() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(reporterService.findByReference(eq(reference))).thenReturn(Flowable.empty());
        when(reporterService.create(eq(reference), any(), any(), eq(false)))
                .thenReturn(Single.error(new ReporterConfigurationException(
                        "Reporter type 'mongodb' cannot be created manually")));

        AutomationReporter def = definition("audit-mongo", false);
        def.setType("mongodb");

        Response response = put(reportersTarget(DOMAIN_KEY), def);

        assertEquals(400, response.getStatus());
    }

    @Test
    void put_rejects_unknown_type() {
        // An undeployed/unknown plugin type must be rejected before the reporter is persisted.
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(reporterService.findByReference(eq(reference))).thenReturn(Flowable.empty());
        when(reporterPluginService.checkPluginDeployment(eq("reporter-am-unknown")))
                .thenReturn(Completable.error(PluginNotDeployedException.forType("reporter-am-unknown")));

        AutomationReporter def = definition("audit-log", false);
        def.setType("reporter-am-unknown");

        Response response = put(reportersTarget(DOMAIN_KEY), def);

        assertEquals(400, response.getStatus());
        verify(reporterService, never()).create(eq(reference), any(), any(), eq(false));
    }

    @Test
    void put_create_propagates_invalid_configuration_as_400() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(reporterService.findByReference(eq(reference))).thenReturn(Flowable.empty());
        when(reporterPluginService.checkPluginDeployment(eq("reporter-am-file"))).thenReturn(Completable.complete());
        when(reporterService.create(eq(reference), any(), any(), eq(false)))
                .thenReturn(Single.error(InvalidPluginConfigurationException.fromValidationError("not valid")));

        AutomationReporter def = definition("audit-log", false);
        def.setConfiguration("not-json");

        Response response = put(reportersTarget(DOMAIN_KEY), def);

        assertEquals(400, response.getStatus());
        verify(reporterPluginService).checkPluginDeployment(eq("reporter-am-file"));
        verify(reporterService).create(eq(reference), any(), any(), eq(false));
    }

    @Test
    void put_checks_plugin_deployment_before_create() {
        String reporterId = AutomationIds.reporterId(domainId, "audit-log");
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(reporterService.findByReference(eq(reference))).thenReturn(Flowable.empty());
        when(reporterService.create(eq(reference), any(), any(), eq(false)))
                .thenReturn(Single.just(reporter(reporterId, "audit-log", false, ManagedBy.AUTOMATION_API)));

        Response response = put(reportersTarget(DOMAIN_KEY), definition("audit-log", false));

        assertEquals(200, response.getStatus());
        verify(reporterPluginService).checkPluginDeployment(eq("reporter-am-file"));
    }

    @Test
    void put_updates_when_present() {
        String reporterId = AutomationIds.reporterId(domainId, "audit-log");
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(reporterService.findByReference(eq(reference)))
                .thenReturn(Flowable.just(reporter(reporterId, "audit-log", false, ManagedBy.AUTOMATION_API)));
        when(reporterService.update(eq(reference), eq(reporterId), any(), any(), eq(false)))
                .thenReturn(Single.just(reporter(reporterId, "audit-log", false, ManagedBy.AUTOMATION_API)));

        Response response = put(reportersTarget(DOMAIN_KEY), definition("audit-log", false));

        assertEquals(200, response.getStatus());
    }

    @Test
    void put_update_rejects_missing_required_fields() {
        String reporterId = AutomationIds.reporterId(domainId, "audit-log");
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(reporterService.findByReference(eq(reference)))
                .thenReturn(Flowable.just(reporter(reporterId, "audit-log", false, ManagedBy.AUTOMATION_API)));

        AutomationReporter def = new AutomationReporter();
        def.setAutomationKey("audit-log");
        // name and type intentionally omitted

        Response response = put(reportersTarget(DOMAIN_KEY), def);

        assertEquals(400, response.getStatus());
        verify(reporterService, never()).update(eq(reference), eq(reporterId), any(), any(), eq(false));
    }

    @Test
    void put_update_rejects_configuration_not_matching_schema() {
        String reporterId = AutomationIds.reporterId(domainId, "audit-log");
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(reporterService.findByReference(eq(reference)))
                .thenReturn(Flowable.just(reporter(reporterId, "audit-log", false, ManagedBy.AUTOMATION_API)));
        when(reporterPluginService.checkPluginDeployment(eq("reporter-am-file"))).thenReturn(Completable.complete());
        when(reporterService.update(eq(reference), eq(reporterId), any(), any(), eq(false)))
                .thenReturn(Single.error(InvalidPluginConfigurationException.fromValidationError("not valid")));

        Response response = put(reportersTarget(DOMAIN_KEY), definition("audit-log", false));

        assertEquals(400, response.getStatus());
        verify(reporterPluginService).checkPluginDeployment(eq("reporter-am-file"));
        verify(reporterService).update(eq(reference), eq(reporterId), any(), any(), eq(false));
    }

    @Test
    void put_update_rejects_blank_configuration() {
        String reporterId = AutomationIds.reporterId(domainId, "audit-log");
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(reporterService.findByReference(eq(reference)))
                .thenReturn(Flowable.just(reporter(reporterId, "audit-log", false, ManagedBy.AUTOMATION_API)));

        AutomationReporter def = definition("audit-log", false);
        def.setConfiguration("");

        Response response = put(reportersTarget(DOMAIN_KEY), def);

        assertEquals(400, response.getStatus());
        assertTrue(response.readEntity(String.class)
                .contains("Field 'configuration' is required for a non-system reporter"));
        verify(reporterService, never()).update(eq(reference), eq(reporterId), any(), any(), eq(false));
    }

    @Test
    void put_update_rejects_type_change() {
        String reporterId = AutomationIds.reporterId(domainId, "audit-log");
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(reporterService.findByReference(eq(reference)))
                .thenReturn(Flowable.just(reporter(reporterId, "audit-log", false, ManagedBy.AUTOMATION_API)));

        AutomationReporter def = definition("audit-log", false); // existing type is reporter-am-file
        def.setType("reporter-am-kafka");

        Response response = put(reportersTarget(DOMAIN_KEY), def);

        assertEquals(400, response.getStatus());
        assertTrue(response.readEntity(String.class)
                .contains("The 'type' is immutable for an existing reporter"));
        verify(reporterPluginService, never()).checkPluginDeployment(anyString());
        verify(reporterService, never()).update(eq(reference), eq(reporterId), any(), any(), eq(false));
    }

    @Test
    void put_rejects_changing_system_on_existing() {
        String reporterId = AutomationIds.reporterId(domainId, "audit-log");
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        // existing is non-system; the PUT flips system -> rejected (immutable)
        when(reporterService.findByReference(eq(reference)))
                .thenReturn(Flowable.just(reporter(reporterId, "audit-log", false, ManagedBy.AUTOMATION_API)));

        Response response = put(reportersTarget(DOMAIN_KEY), definition("audit-log", true));

        assertEquals(400, response.getStatus());
    }

    @Test
    void put_rejects_second_system() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(reporterService.findByReference(eq(reference)))
                .thenReturn(Flowable.just(reporter("existing-system", "primary", true, ManagedBy.AUTOMATION_API)));

        Response response = put(reportersTarget(DOMAIN_KEY), systemDefinition("second-system"));

        assertEquals(400, response.getStatus());
    }

    @Test
    void put_returns_404_when_domain_absent() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.empty());

        Response response = put(reportersTarget(DOMAIN_KEY), definition("audit-log", false));

        assertEquals(404, response.getStatus());
    }

    @Test
    void put_rejects_invalid_key_pattern() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(reporterService.findByReference(eq(reference))).thenReturn(Flowable.empty());

        Response response = put(reportersTarget(DOMAIN_KEY), definition("Bad Key!", false));

        assertEquals(400, response.getStatus());
    }
}
