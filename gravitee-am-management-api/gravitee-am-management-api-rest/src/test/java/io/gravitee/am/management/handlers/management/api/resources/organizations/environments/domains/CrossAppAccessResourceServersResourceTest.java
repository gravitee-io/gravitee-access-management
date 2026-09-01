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
import io.gravitee.am.management.handlers.management.api.model.CrossAppAccessResourceServerEntity;
import io.gravitee.am.management.service.permissions.PermissionAcls;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.oidc.CrossAppAccessResourceServerView;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

/**
 * @author GraviteeSource Team
 */
public class CrossAppAccessResourceServersResourceTest extends JerseySpringTest {

    private static final String DOMAIN_ID = "domain-id";

    @BeforeEach
    public void resetTrustDomainService() {
        reset(trustDomainService);
    }

    private void stubDomain() {
        Domain domain = new Domain();
        domain.setId(DOMAIN_ID);
        doReturn(Maybe.just(domain)).when(domainService).findById(DOMAIN_ID);
    }

    private void stubSearch(CrossAppAccessResourceServerView... resourceServers) {
        stubDomain();
        doReturn(Flowable.fromArray(resourceServers))
                .when(trustDomainService)
                .searchCrossAppAccessResourceServers(eq(Reference.domain(DOMAIN_ID)), anyString(), anyInt());
    }

    private WebTarget resourceServersTarget() {
        return target("domains").path(DOMAIN_ID).path("xaa").path("resource-servers");
    }

    private List<CrossAppAccessResourceServerEntity> list(WebTarget target) {
        final Response response = target.request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        return response.readEntity(new GenericType<>() {
        });
    }

    @Test
    public void shouldReturnTheResourceServersTheSearchFound() {
        stubSearch(new CrossAppAccessResourceServerView("td-1", "acme", "rs-1", "Calendar", "https://calendar.acme.com"),
                new CrossAppAccessResourceServerView("td-2", "globex", "rs-3", "Mail", "https://mail.globex.com"));

        List<CrossAppAccessResourceServerEntity> resourceServers = list(resourceServersTarget());

        assertEquals(List.of(
                        new CrossAppAccessResourceServerEntity("td-1", "acme", "rs-1", "Calendar", "https://calendar.acme.com"),
                        new CrossAppAccessResourceServerEntity("td-2", "globex", "rs-3", "Mail", "https://mail.globex.com")),
                resourceServers);
    }

    @Test
    public void shouldSearchWithTheRequestedTermAndLimit() {
        stubSearch();

        list(resourceServersTarget().queryParam("q", "mail").queryParam("limit", 25));

        verify(trustDomainService).searchCrossAppAccessResourceServers(Reference.domain(DOMAIN_ID), "mail", 25);
    }

    @Test
    public void shouldSearchWithABlankTermAndTenResultsByDefault() {
        stubSearch();

        list(resourceServersTarget());

        verify(trustDomainService).searchCrossAppAccessResourceServers(Reference.domain(DOMAIN_ID), "", 10);
    }

    @Test
    public void shouldRejectALimitOutsideTheAllowedRange() {
        stubDomain();

        assertEquals(HttpStatusCode.BAD_REQUEST_400,
                resourceServersTarget().queryParam("limit", 0).request().get().getStatus());
        assertEquals(HttpStatusCode.BAD_REQUEST_400,
                resourceServersTarget().queryParam("limit", 101).request().get().getStatus());
    }

    @Test
    public void shouldRejectListWithoutDomainSettingsPermission() {
        doReturn(Single.just(false)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));

        final Response response = resourceServersTarget().request().get();

        assertEquals(HttpStatusCode.FORBIDDEN_403, response.getStatus());
        verify(trustDomainService, never()).searchCrossAppAccessResourceServers(any(), any(), anyInt());
    }
}
