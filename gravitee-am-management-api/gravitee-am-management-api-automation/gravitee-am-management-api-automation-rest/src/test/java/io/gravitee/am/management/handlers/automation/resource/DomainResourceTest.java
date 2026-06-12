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
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import jakarta.ws.rs.core.Response;
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
class DomainResourceTest extends AutomationJerseySpringTest {

    private static final String DOMAIN_KEY = "customer-auth";
    private static final String BROWNFIELD_ID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
    private final String domainId = AutomationIds.domainId(ENV_ID, DOMAIN_KEY);

    private Domain domain() {
        Domain domain = new Domain();
        domain.setId(domainId);
        domain.setAutomationKey(DOMAIN_KEY);
        domain.setHrid(DOMAIN_KEY);
        domain.setName("Customer Auth");
        domain.setPath("/customer-auth");
        domain.setReferenceId(ENV_ID);
        domain.setDataPlaneId("default");
        domain.setManagedBy(ManagedBy.AUTOMATION_API);
        return domain;
    }

    private Domain managementDomain() {
        Domain domain = domain();
        domain.setManagedBy(null);
        return domain;
    }

    @Test
    void get_returns_domain_when_found() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));

        Response response = domainsTarget().path(DOMAIN_KEY).request().get();

        assertEquals(200, response.getStatus());
        assertEquals(DOMAIN_KEY, readEntity(response, AutomationDomain.class).getAutomationKey());
    }

    @Test
    void get_returns_404_when_absent() {
        when(domainService.findById(eq(AutomationIds.domainId(ENV_ID, "missing")))).thenReturn(Maybe.empty());

        Response response = domainsTarget().path("missing").request().get();

        assertEquals(404, response.getStatus());
    }

    @Test
    void delete_returns_204() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(domainService.delete(any(), eq(domainId), any())).thenReturn(Completable.complete());

        Response response = domainsTarget().path(DOMAIN_KEY).request().delete();

        assertEquals(204, response.getStatus());
    }

    @Test
    void get_returns_404_when_domain_is_not_automation_managed() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(managementDomain()));

        Response response = domainsTarget().path(DOMAIN_KEY).request().get();

        assertEquals(404, response.getStatus());
    }

    @Test
    void delete_returns_204_when_domain_is_not_automation_managed() {
        // A domain the Automation API does not manage is treated as "nothing to delete" — an
        // idempotent no-op (204), and it is never actually deleted.
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(managementDomain()));

        Response response = domainsTarget().path(DOMAIN_KEY).request().delete();

        assertEquals(204, response.getStatus());
        verify(domainService, never()).delete(any(), any(), any());
    }

    @Test
    void delete_returns_204_when_domain_absent() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.empty());

        Response response = domainsTarget().path(DOMAIN_KEY).request().delete();

        assertEquals(204, response.getStatus());
        verify(domainService, never()).delete(any(), any(), any());
    }

    private Domain brownfieldDomain(String referenceId) {
        Domain domain = domain();
        domain.setId(BROWNFIELD_ID);
        domain.setAutomationKey(null);
        domain.setReferenceId(referenceId);
        domain.setManagedBy(ManagedBy.NONE);
        return domain;
    }

    @Test
    void get_by_id_returns_brownfield_domain_bypassing_managed_by() {
        when(domainService.findById(eq(BROWNFIELD_ID))).thenReturn(Maybe.just(brownfieldDomain(ENV_ID)));

        Response response = domainsTarget().path("id:" + BROWNFIELD_ID).request().get();

        assertEquals(200, response.getStatus());
    }

    @Test
    void get_by_id_returns_404_when_domain_in_other_environment() {
        when(domainService.findById(eq(BROWNFIELD_ID))).thenReturn(Maybe.just(brownfieldDomain("other-env")));

        Response response = domainsTarget().path("id:" + BROWNFIELD_ID).request().get();

        assertEquals(404, response.getStatus());
    }

    @Test
    void delete_by_id_returns_204_for_brownfield_domain() {
        when(domainService.findById(eq(BROWNFIELD_ID))).thenReturn(Maybe.just(brownfieldDomain(ENV_ID)));
        when(domainService.delete(any(), eq(BROWNFIELD_ID), any())).thenReturn(Completable.complete());

        Response response = domainsTarget().path("id:" + BROWNFIELD_ID).request().delete();

        assertEquals(204, response.getStatus());
        verify(domainService).delete(any(), eq(BROWNFIELD_ID), any());
    }

    @Test
    void get_returns_403_without_revealing_existence_when_not_permitted() {
        denyPermission();
        // findById is stubbed empty: even for an absent domain the caller must get 403, not 404.
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.empty());

        Response response = domainsTarget().path(DOMAIN_KEY).request().get();

        assertEquals(403, response.getStatus());
    }

    @Test
    void delete_returns_403_when_not_permitted() {
        denyPermission();

        Response response = domainsTarget().path(DOMAIN_KEY).request().delete();

        assertEquals(403, response.getStatus());
        verify(domainService, never()).delete(any(), any(), any());
    }
}
