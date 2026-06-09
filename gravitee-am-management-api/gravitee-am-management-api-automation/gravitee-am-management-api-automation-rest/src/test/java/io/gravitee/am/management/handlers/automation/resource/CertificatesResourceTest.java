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
import io.gravitee.am.management.handlers.automation.model.AutomationCertificate;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ManagedBy;
import io.gravitee.am.service.exception.InvalidPluginConfigurationException;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
class CertificatesResourceTest extends AutomationJerseySpringTest {

    private static final String DOMAIN_KEY = "customer-auth";
    private final String domainId = AutomationIds.domainId(ENV_ID, DOMAIN_KEY);

    private Domain domain() {
        Domain domain = new Domain();
        domain.setId(domainId);
        domain.setAutomationKey(DOMAIN_KEY);
        domain.setReferenceId(ENV_ID);
        domain.setManagedBy(ManagedBy.AUTOMATION_API);
        return domain;
    }

    private Certificate cert(String id, String key, boolean system, ManagedBy managedBy) {
        Certificate c = new Certificate();
        c.setId(id);
        c.setAutomationKey(key);
        c.setName(key);
        c.setType("javakeystore-am-certificate");
        c.setConfiguration("{}");
        c.setSystem(system);
        c.setDomain(domainId);
        c.setManagedBy(managedBy);
        return c;
    }

    private AutomationCertificate definition(String key, boolean isSystem) {
        AutomationCertificate in = new AutomationCertificate();
        in.setAutomationKey(key);
        in.setName(key);
        in.setType("javakeystore-am-certificate");
        in.setConfiguration("{}");
        in.setSystem(isSystem);
        return in;
    }

    /**
     * A minimal payload — only {@code key} and {@code system:true} — the AAPI accepts on the system
     * path. {@code name}/{@code type}/{@code configuration} are ignored when {@code system} is true,
     * so this models the way a caller is meant to provision a system resource.
     */
    private AutomationCertificate systemDefinition(String key) {
        AutomationCertificate in = new AutomationCertificate();
        in.setAutomationKey(key);
        in.setSystem(true);
        return in;
    }

    @Test
    void list_returns_only_automation_managed_sorted() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(certificateService.findByDomain(eq(domainId)))
                .thenReturn(Flowable.just(
                        cert("id-b", "beta", false, ManagedBy.AUTOMATION_API),
                        cert("id-legacy", "legacy", true, null),
                        cert("id-a", "alpha", false, ManagedBy.AUTOMATION_API)));

        Response response = certificatesTarget(DOMAIN_KEY).request().get();

        assertEquals(200, response.getStatus());
        List<AutomationCertificate> body = readListEntity(response, AutomationCertificate.class);
        assertEquals(2, body.size());
        assertEquals("alpha", body.get(0).getAutomationKey());
        assertEquals("beta", body.get(1).getAutomationKey());
    }

    @Test
    void put_creates_when_absent() {
        String certId = AutomationIds.certificateId(domainId, "my-cert");
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(certificateService.findByDomain(eq(domainId))).thenReturn(Flowable.empty());
        when(certificateService.create(any(Domain.class), any(), any(), eq(false)))
                .thenReturn(Single.just(cert(certId, "my-cert", false, ManagedBy.AUTOMATION_API)));

        Response response = put(certificatesTarget(DOMAIN_KEY), definition("my-cert", false));

        assertEquals(200, response.getStatus());
        assertEquals("my-cert", readEntity(response, AutomationCertificate.class).getAutomationKey());
    }

    @Test
    void put_creates_system_from_config() {
        String certId = AutomationIds.certificateId(domainId, "sys-cert");
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(certificateService.findByDomain(eq(domainId))).thenReturn(Flowable.empty());
        when(certificateService.createSystem(any(Domain.class), eq(certId), eq("sys-cert"), any()))
                .thenReturn(Single.just(cert(certId, "sys-cert", true, ManagedBy.AUTOMATION_API)));

        Response response = put(certificatesTarget(DOMAIN_KEY), systemDefinition("sys-cert"));

        assertEquals(200, response.getStatus());
        assertTrue(readEntity(response, AutomationCertificate.class).isSystem());
        // The payload path must never run for a system create.
        verify(certificateService, never()).create(any(Domain.class), any(), any(), eq(true));
    }

    @Test
    void put_existing_system_is_idempotent_noop() {
        // Re-PUTting a system certificate must not touch the service; gravitee.yaml owns the config.
        String certId = AutomationIds.certificateId(domainId, "sys-cert");
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(certificateService.findByDomain(eq(domainId)))
                .thenReturn(Flowable.just(cert(certId, "sys-cert", true, ManagedBy.AUTOMATION_API)));

        Response response = put(certificatesTarget(DOMAIN_KEY), systemDefinition("sys-cert"));

        assertEquals(200, response.getStatus());
        verify(certificateService, never()).update(any(Domain.class), anyString(), any(), any());
        verify(certificateService, never()).create(any(Domain.class), any(), any(), eq(true));
        verify(certificateService, never()).createSystem(any(Domain.class), anyString(), anyString(), any());
    }

    @Test
    void put_rejects_non_system_missing_required_fields() {
        // name/type/configuration are no longer @NotNull on the model so the system path can omit them;
        // the resource enforces them programmatically for non-system creates.
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(certificateService.findByDomain(eq(domainId))).thenReturn(Flowable.empty());

        AutomationCertificate def = new AutomationCertificate();
        def.setAutomationKey("incomplete");
        // name, type, configuration intentionally left null

        Response response = put(certificatesTarget(DOMAIN_KEY), def);

        assertEquals(400, response.getStatus());
        verify(certificateService, never()).create(any(Domain.class), any(), any(), eq(false));
    }

    @Test
    void put_updates_when_present() {
        String certId = AutomationIds.certificateId(domainId, "my-cert");
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(certificateService.findByDomain(eq(domainId)))
                .thenReturn(Flowable.just(cert(certId, "my-cert", false, ManagedBy.AUTOMATION_API)));
        when(certificateService.update(any(Domain.class), eq(certId), any(), any()))
                .thenReturn(Single.just(cert(certId, "my-cert", false, ManagedBy.AUTOMATION_API)));

        Response response = put(certificatesTarget(DOMAIN_KEY), definition("my-cert", false));

        assertEquals(200, response.getStatus());
    }

    @Test
    void put_create_propagates_invalid_configuration_as_400() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(certificateService.findByDomain(eq(domainId))).thenReturn(Flowable.empty());
        when(certificateService.create(any(Domain.class), any(), any(), eq(false)))
                .thenReturn(Single.error(InvalidPluginConfigurationException.fromValidationError("not valid")));

        AutomationCertificate def = definition("my-cert", false);
        def.setConfiguration("not-json");

        Response response = put(certificatesTarget(DOMAIN_KEY), def);

        assertEquals(400, response.getStatus());
        verify(certificateService).create(any(Domain.class), any(), any(), eq(false));
    }

    @Test
    void put_update_rejects_missing_required_fields() {
        String certId = AutomationIds.certificateId(domainId, "my-cert");
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(certificateService.findByDomain(eq(domainId)))
                .thenReturn(Flowable.just(cert(certId, "my-cert", false, ManagedBy.AUTOMATION_API)));

        AutomationCertificate def = new AutomationCertificate();
        def.setAutomationKey("my-cert");
        // name and type intentionally omitted

        Response response = put(certificatesTarget(DOMAIN_KEY), def);

        assertEquals(400, response.getStatus());
        verify(certificateService, never()).update(any(Domain.class), eq(certId), any(), any());
    }

    @Test
    void put_update_rejects_configuration_not_matching_schema() {
        String certId = AutomationIds.certificateId(domainId, "my-cert");
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(certificateService.findByDomain(eq(domainId)))
                .thenReturn(Flowable.just(cert(certId, "my-cert", false, ManagedBy.AUTOMATION_API)));
        when(certificateService.update(any(Domain.class), eq(certId), any(), any()))
                .thenReturn(Single.error(InvalidPluginConfigurationException.fromValidationError("not valid")));

        Response response = put(certificatesTarget(DOMAIN_KEY), definition("my-cert", false));

        assertEquals(400, response.getStatus());
        verify(certificateService).update(any(Domain.class), eq(certId), any(), any());
    }

    @Test
    void put_update_rejects_blank_configuration() {
        String certId = AutomationIds.certificateId(domainId, "my-cert");
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(certificateService.findByDomain(eq(domainId)))
                .thenReturn(Flowable.just(cert(certId, "my-cert", false, ManagedBy.AUTOMATION_API)));

        AutomationCertificate def = definition("my-cert", false);
        def.setConfiguration("");

        Response response = put(certificatesTarget(DOMAIN_KEY), def);

        assertEquals(400, response.getStatus());
        assertTrue(response.readEntity(String.class)
                .contains("Field 'configuration' is required for a non-system certificate"));
        verify(certificateService, never()).update(any(Domain.class), eq(certId), any(), any());
    }

    @Test
    void put_update_rejects_type_change_without_config_checks() {
        String certId = AutomationIds.certificateId(domainId, "my-cert");
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(certificateService.findByDomain(eq(domainId)))
                .thenReturn(Flowable.just(cert(certId, "my-cert", false, ManagedBy.AUTOMATION_API)));

        AutomationCertificate def = definition("my-cert", false); // existing type is javakeystore-am-certificate
        def.setType("pkcs12-am-certificate");

        Response response = put(certificatesTarget(DOMAIN_KEY), def);

        assertEquals(400, response.getStatus());
        assertTrue(response.readEntity(String.class)
                .contains("The 'type' is immutable for an existing certificate"));
        verify(certificateService, never()).update(any(Domain.class), eq(certId), any(), any());
    }

    @Test
    void put_rejects_changing_system_on_existing() {
        String certId = AutomationIds.certificateId(domainId, "my-cert");
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        // existing is non-system; the PUT flips system -> rejected (immutable)
        when(certificateService.findByDomain(eq(domainId)))
                .thenReturn(Flowable.just(cert(certId, "my-cert", false, ManagedBy.AUTOMATION_API)));

        Response response = put(certificatesTarget(DOMAIN_KEY), definition("my-cert", true));

        assertEquals(400, response.getStatus());
    }

    @Test
    void put_rejects_collision_with_non_automation_certificate() {
        String certId = AutomationIds.certificateId(domainId, "my-cert");
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(certificateService.findByDomain(eq(domainId)))
                .thenReturn(Flowable.just(cert(certId, "my-cert", false, null)));

        Response response = put(certificatesTarget(DOMAIN_KEY), definition("my-cert", false));

        assertEquals(400, response.getStatus());
    }

    @Test
    void put_rejects_second_system() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(certificateService.findByDomain(eq(domainId)))
                .thenReturn(Flowable.just(cert("existing-system", "primary", true, ManagedBy.AUTOMATION_API)));

        Response response = put(certificatesTarget(DOMAIN_KEY), systemDefinition("second-system"));

        assertEquals(400, response.getStatus());
    }

    @Test
    void put_returns_404_when_domain_absent() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.empty());

        Response response = put(certificatesTarget(DOMAIN_KEY), definition("my-cert", false));

        assertEquals(404, response.getStatus());
    }

    @Test
    void put_rejects_invalid_key_pattern() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(certificateService.findByDomain(anyString())).thenReturn(Flowable.empty());

        Response response = put(certificatesTarget(DOMAIN_KEY), definition("Bad Key!", false));

        assertEquals(400, response.getStatus());
    }
}
