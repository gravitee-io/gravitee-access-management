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
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.oidc.CrossAppAccessResourceServer;
import io.gravitee.am.model.oidc.CrossAppAccessSettings;
import io.gravitee.am.model.oidc.TrustDomain;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

    private static TrustDomain trustDomain(String id, String name, CrossAppAccessSettings crossAppAccess) {
        return TrustDomain.builder()
                .id(id)
                .referenceType(ReferenceType.DOMAIN)
                .referenceId(DOMAIN_ID)
                .name(name)
                .issuer("https://" + name + "/")
                .crossAppAccess(crossAppAccess)
                .build();
    }

    private static CrossAppAccessSettings crossAppAccess(boolean enabled, CrossAppAccessResourceServer... resourceServers) {
        return CrossAppAccessSettings.builder()
                .enabled(enabled)
                .resourceServers(List.of(resourceServers))
                .build();
    }

    private static CrossAppAccessResourceServer resourceServer(String id, String name, String resource) {
        return CrossAppAccessResourceServer.builder().id(id).name(name).resource(resource).build();
    }

    private List<CrossAppAccessResourceServerEntity> list() {
        final Response response = target("domains").path(DOMAIN_ID).path("caa").path("resource-servers").request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());
        return response.readEntity(new GenericType<>() {
        });
    }

    @Test
    public void shouldFlattenResourceServersAcrossTrustDomains() {
        stubDomain();
        doReturn(Flowable.just(
                trustDomain("td-1", "acme", crossAppAccess(true,
                        resourceServer("rs-1", "Calendar", "https://calendar.acme.com"),
                        resourceServer("rs-2", "Files", "https://files.acme.com"))),
                trustDomain("td-2", "globex", crossAppAccess(true,
                        resourceServer("rs-3", "Mail", "https://mail.globex.com")))))
                .when(trustDomainService).findByReference(ReferenceType.DOMAIN, DOMAIN_ID);

        List<CrossAppAccessResourceServerEntity> resourceServers = list();

        assertEquals(3, resourceServers.size());
        assertEquals(new CrossAppAccessResourceServerEntity("td-1", "acme", "rs-1", "Calendar", "https://calendar.acme.com"),
                resourceServers.get(0));
        assertEquals(new CrossAppAccessResourceServerEntity("td-2", "globex", "rs-3", "Mail", "https://mail.globex.com"),
                resourceServers.get(2));
    }

    @Test
    public void shouldSkipTrustDomainWithCrossAppAccessDisabled() {
        stubDomain();
        doReturn(Flowable.just(
                trustDomain("td-1", "acme", crossAppAccess(false, resourceServer("rs-1", "Calendar", "https://calendar.acme.com"))),
                trustDomain("td-2", "globex", crossAppAccess(true, resourceServer("rs-3", "Mail", "https://mail.globex.com")))))
                .when(trustDomainService).findByReference(ReferenceType.DOMAIN, DOMAIN_ID);

        List<CrossAppAccessResourceServerEntity> resourceServers = list();

        assertEquals(1, resourceServers.size());
        assertEquals("rs-3", resourceServers.get(0).resourceServerId());
    }

    @Test
    public void shouldSkipTrustDomainWithoutCrossAppAccess() {
        stubDomain();
        doReturn(Flowable.just(trustDomain("td-1", "acme", null)))
                .when(trustDomainService).findByReference(ReferenceType.DOMAIN, DOMAIN_ID);

        assertTrue(list().isEmpty());
    }

    @Test
    public void shouldRejectListWithoutDomainSettingsPermission() {
        doReturn(Single.just(false)).when(permissionService).hasPermission(any(User.class), any(PermissionAcls.class));

        final Response response = target("domains").path(DOMAIN_ID).path("caa").path("resource-servers").request().get();

        assertEquals(HttpStatusCode.FORBIDDEN_403, response.getStatus());
        verify(trustDomainService, never()).findByReference(any(), any());
    }
}
