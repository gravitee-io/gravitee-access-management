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
import io.gravitee.am.management.handlers.automation.model.AutomationIdentityProvider;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.ManagedBy;
import io.gravitee.am.model.ReferenceType;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
class IdentityProviderResourceTest extends AutomationJerseySpringTest {

    private static final String DOMAIN_KEY = "customer-auth";
    private static final String IDP_KEY = "dev-users";
    private static final String BROWNFIELD_ID = "11111111-2222-3333-4444-555555555555";
    private final String domainId = AutomationIds.domainId(ENV_ID, DOMAIN_KEY);
    private final String idpId = AutomationIds.identityProviderId(domainId, IDP_KEY);

    private Domain domain() {
        Domain domain = new Domain();
        domain.setId(domainId);
        domain.setAutomationKey(DOMAIN_KEY);
        domain.setReferenceId(ENV_ID);
        domain.setManagedBy(ManagedBy.AUTOMATION_API);
        return domain;
    }

    private IdentityProvider idp(ManagedBy managedBy) {
        IdentityProvider idp = new IdentityProvider();
        idp.setId(idpId);
        idp.setAutomationKey(IDP_KEY);
        idp.setName("Dev Users");
        idp.setType("inline-am-idp");
        idp.setReferenceType(ReferenceType.DOMAIN);
        idp.setReferenceId(domainId);
        idp.setManagedBy(managedBy);
        return idp;
    }

    private Response getRequest() {
        return identitiesTarget(DOMAIN_KEY).path(IDP_KEY).request().get();
    }

    @Test
    void get_returns_idp_when_found() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(identityProviderService.findAll(eq(ReferenceType.DOMAIN), anyString()))
                .thenReturn(Flowable.just(idp(ManagedBy.AUTOMATION_API)));

        Response response = getRequest();

        assertEquals(200, response.getStatus());
        assertEquals(IDP_KEY, readEntity(response, AutomationIdentityProvider.class).getAutomationKey());
    }

    @Test
    void get_returns_404_when_idp_absent() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(identityProviderService.findAll(eq(ReferenceType.DOMAIN), anyString())).thenReturn(Flowable.empty());

        Response response = getRequest();

        assertEquals(404, response.getStatus());
    }

    @Test
    void get_returns_404_when_idp_not_automation_managed() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(identityProviderService.findAll(eq(ReferenceType.DOMAIN), anyString()))
                .thenReturn(Flowable.just(idp(null)));

        Response response = getRequest();

        assertEquals(404, response.getStatus());
    }

    @Test
    void get_returns_404_when_domain_absent() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.empty());

        Response response = getRequest();

        assertEquals(404, response.getStatus());
    }

    @Test
    void delete_returns_204() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(identityProviderService.findAll(eq(ReferenceType.DOMAIN), anyString()))
                .thenReturn(Flowable.just(idp(ManagedBy.AUTOMATION_API)));
        when(identityProviderService.delete(eq(ReferenceType.DOMAIN), eq(domainId), eq(idpId), any()))
                .thenReturn(Completable.complete());

        Response response = identitiesTarget(DOMAIN_KEY).path(IDP_KEY).request().delete();

        assertEquals(204, response.getStatus());
    }

    @Test
    void delete_returns_204_when_idp_absent() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(identityProviderService.findAll(eq(ReferenceType.DOMAIN), anyString())).thenReturn(Flowable.empty());

        Response response = identitiesTarget(DOMAIN_KEY).path(IDP_KEY).request().delete();

        assertEquals(204, response.getStatus());
        verify(identityProviderService, never()).delete(any(), anyString(), anyString(), any());
    }

    @Test
    void delete_returns_204_when_domain_absent() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.empty());

        Response response = identitiesTarget(DOMAIN_KEY).path(IDP_KEY).request().delete();

        assertEquals(204, response.getStatus());
        verify(identityProviderService, never()).delete(any(), anyString(), anyString(), any());
    }

    private IdentityProvider brownfieldIdp(String referenceId) {
        IdentityProvider idp = new IdentityProvider();
        idp.setId(BROWNFIELD_ID);
        idp.setName("Legacy LDAP");
        idp.setType("ldap-am-idp");
        idp.setReferenceType(ReferenceType.DOMAIN);
        idp.setReferenceId(referenceId);
        idp.setManagedBy(ManagedBy.NONE);
        return idp;
    }

    @Test
    void get_by_id_returns_brownfield_idp_bypassing_managed_by() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(identityProviderService.findById(eq(BROWNFIELD_ID))).thenReturn(Maybe.just(brownfieldIdp(domainId)));

        Response response = identitiesTarget(DOMAIN_KEY).path("id:" + BROWNFIELD_ID).request().get();

        assertEquals(200, response.getStatus());
    }

    @Test
    void get_by_id_resolves_child_under_an_id_addressed_domain() {
        // mixed addressing: both the domain and the identity provider are addressed by id:
        String brownfieldDomainId = "dddddddd-eeee-ffff-0000-111111111111";
        Domain brownfieldDomain = new Domain();
        brownfieldDomain.setId(brownfieldDomainId);
        brownfieldDomain.setReferenceId(ENV_ID);
        brownfieldDomain.setManagedBy(ManagedBy.NONE);
        when(domainService.findById(eq(brownfieldDomainId))).thenReturn(Maybe.just(brownfieldDomain));
        when(identityProviderService.findById(eq(BROWNFIELD_ID))).thenReturn(Maybe.just(brownfieldIdp(brownfieldDomainId)));

        Response response = identitiesTarget("id:" + brownfieldDomainId).path("id:" + BROWNFIELD_ID).request().get();

        assertEquals(200, response.getStatus());
    }

    @Test
    void get_by_id_returns_404_when_idp_belongs_to_another_domain() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(identityProviderService.findById(eq(BROWNFIELD_ID))).thenReturn(Maybe.just(brownfieldIdp("another-domain")));

        Response response = identitiesTarget(DOMAIN_KEY).path("id:" + BROWNFIELD_ID).request().get();

        assertEquals(404, response.getStatus());
    }

    @Test
    void delete_by_id_returns_204_for_brownfield_idp() {
        when(domainService.findById(eq(domainId))).thenReturn(Maybe.just(domain()));
        when(identityProviderService.findById(eq(BROWNFIELD_ID))).thenReturn(Maybe.just(brownfieldIdp(domainId)));
        when(identityProviderService.delete(eq(ReferenceType.DOMAIN), eq(domainId), eq(BROWNFIELD_ID), any()))
                .thenReturn(Completable.complete());

        Response response = identitiesTarget(DOMAIN_KEY).path("id:" + BROWNFIELD_ID).request().delete();

        assertEquals(204, response.getStatus());
        verify(identityProviderService).delete(eq(ReferenceType.DOMAIN), eq(domainId), eq(BROWNFIELD_ID), any());
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

        Response response = identitiesTarget(DOMAIN_KEY).path(IDP_KEY).request().delete();

        assertEquals(403, response.getStatus());
        verify(identityProviderService, never()).delete(any(), anyString(), anyString(), any());
    }
}
