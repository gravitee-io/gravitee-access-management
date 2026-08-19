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

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.management.service.permissions.PermissionAcls;
import io.gravitee.am.model.Acl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Membership;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.permissions.Permission;
import io.gravitee.am.model.resource.ServiceResource;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

public class ServiceResourcesResourceTest extends JerseySpringTest {

    private static final String DOMAIN_ID = "domain-1";

    @Test
    public void shouldList() {
        final ServiceResource serviceResource = new ServiceResource();
        serviceResource.setId("resource-1");
        serviceResource.setName("my-smtp");
        serviceResource.setType("smtp-am-resource");
        serviceResource.setConfiguration("{\"password\":\"secret\"}");

        doReturn(Maybe.just(domain())).when(domainService).findById(DOMAIN_ID);
        doReturn(Flowable.just(serviceResource)).when(serviceResourceServiceProxy).findByDomain(DOMAIN_ID);

        final Response response = listResources();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final List<Map<String, Object>> resources = response.readEntity(List.class);
        assertEquals(1, resources.size());
        assertEquals(Map.of("id", "resource-1", "name", "my-smtp", "type", "smtp-am-resource"), resources.get(0));
    }

    @Test
    public void shouldListWithDomainResourcePermission() {
        doReturn(Maybe.just(domain())).when(domainService).findById(DOMAIN_ID);
        doReturn(Flowable.empty()).when(serviceResourceServiceProxy).findByDomain(DOMAIN_ID);

        listResources();

        final ArgumentCaptor<PermissionAcls> captor = ArgumentCaptor.forClass(PermissionAcls.class);
        verify(permissionService, atLeastOnce()).hasPermission(any(User.class), captor.capture());

        final List<PermissionAcls> checked = captor.getAllValues();
        assertTrue("DOMAIN_RESOURCE[LIST] should open the endpoint",
                checked.stream().anyMatch(acls -> acls.match(granted(Permission.DOMAIN_RESOURCE))));
        assertFalse("DOMAIN_FACTOR[LIST] should not open the endpoint",
                checked.stream().anyMatch(acls -> acls.match(granted(Permission.DOMAIN_FACTOR))));
    }

    private Response listResources() {
        return target("domains").path(DOMAIN_ID).path("resources").request().get();
    }

    private Domain domain() {
        final Domain domain = new Domain();
        domain.setId(DOMAIN_ID);
        return domain;
    }

    private Map<Membership, Map<Permission, Set<Acl>>> granted(Permission permission) {
        final Membership membership = new Membership();
        membership.setReferenceType(ReferenceType.DOMAIN);
        membership.setReferenceId(DOMAIN_ID);
        return Map.of(membership, Map.of(permission, Set.of(Acl.LIST)));
    }
}
