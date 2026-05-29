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
import io.gravitee.am.management.handlers.automation.model.AutomationDomain;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ManagedBy;
import io.gravitee.am.model.ReferenceType;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
class DomainsResourceTest extends AutomationJerseySpringTest {

    private Domain domain(String id, String key) {
        Domain domain = new Domain();
        domain.setId(id);
        domain.setAutomationKey(key);
        domain.setHrid(key);
        domain.setName(key);
        domain.setPath("/" + key);
        domain.setReferenceId(ENV_ID);
        domain.setDataPlaneId("default");
        domain.setManagedBy(ManagedBy.AUTOMATION_API);
        return domain;
    }

    private Domain managementDomain(String id, String key) {
        Domain domain = domain(id, key);
        domain.setManagedBy(null);
        return domain;
    }

    private AutomationDomain definition(String key) {
        AutomationDomain in = new AutomationDomain();
        in.setAutomationKey(key);
        in.setName(key);
        in.setPath("/" + key);
        in.setDataPlaneId("default");
        return in;
    }

    @Test
    void list_returns_domains_sorted() {
        when(domainService.findAllByEnvironment(ORG_ID, ENV_ID))
                .thenReturn(Flowable.just(domain("id-b", "beta"), domain("id-a", "alpha")));

        Response response = domainsTarget().request().get();

        assertEquals(200, response.getStatus());
        List<AutomationDomain> body = readListEntity(response, AutomationDomain.class);
        assertEquals(2, body.size());
        assertEquals("alpha", body.get(0).getAutomationKey());
        assertEquals("beta", body.get(1).getAutomationKey());
    }

    @Test
    void put_creates_when_domain_absent() {
        String domainId = AutomationIds.domainId(ENV_ID, "customer-auth");
        Domain created = domain(domainId, "customer-auth");
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.empty());
        when(domainService.create(eq(ORG_ID), eq(ENV_ID), any(), any()))
                .thenReturn(Single.just(created));
        when(identityProviderService.findAll(eq(ReferenceType.DOMAIN), anyString())).thenReturn(Flowable.empty());
        when(domainService.update(eq(domainId), any(Domain.class), eq(false))).thenReturn(Single.just(created));

        Response response = put(domainsTarget(), definition("customer-auth"));

        assertEquals(200, response.getStatus());
        assertEquals("customer-auth", readEntity(response, AutomationDomain.class).getAutomationKey());
    }

    @Test
    void put_updates_when_domain_present() {
        String domainId = AutomationIds.domainId(ENV_ID, "customer-auth");
        Domain existing = domain(domainId, "customer-auth");
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(existing));
        when(identityProviderService.findAll(eq(ReferenceType.DOMAIN), anyString())).thenReturn(Flowable.empty());
        when(domainService.update(eq(domainId), any(Domain.class), eq(false))).thenReturn(Single.just(existing));

        Response response = put(domainsTarget(), definition("customer-auth"));

        assertEquals(200, response.getStatus());
        assertEquals("customer-auth", readEntity(response, AutomationDomain.class).getAutomationKey());
    }

    @Test
    void put_is_rejected_when_required_fields_missing() {
        AutomationDomain invalid = new AutomationDomain();
        invalid.setAutomationKey("customer-auth"); // name, path, dataPlaneId missing

        Response response = put(domainsTarget(), invalid);

        assertEquals(400, response.getStatus());
    }

    @Test
    void list_filters_out_non_automation_managed_domains() {
        Domain managed = domain("id-a", "alpha");
        Domain management = managementDomain("id-b", "beta");
        when(domainService.findAllByEnvironment(ORG_ID, ENV_ID))
                .thenReturn(Flowable.just(managed, management));

        Response response = domainsTarget().request().get();

        assertEquals(200, response.getStatus());
        List<AutomationDomain> body = readListEntity(response, AutomationDomain.class);
        assertEquals(1, body.size());
        assertEquals("alpha", body.get(0).getAutomationKey());
    }

    @Test
    void put_rejects_when_existing_domain_is_not_automation_managed() {
        String domainId = AutomationIds.domainId(ENV_ID, "customer-auth");
        when(domainService.findById(eq(domainId)))
                .thenReturn(Maybe.just(managementDomain(domainId, "customer-auth")));

        Response response = put(domainsTarget(), definition("customer-auth"));

        assertEquals(400, response.getStatus());
    }

    @Test
    void put_rejects_invalid_key_pattern() {
        AutomationDomain invalid = definition("Customer Auth!"); // uppercase + space + bang

        Response response = put(domainsTarget(), invalid);

        assertEquals(400, response.getStatus());
    }
}
