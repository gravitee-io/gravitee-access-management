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
package io.gravitee.am.management.handlers.management.api.resources;

import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.Reporter;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewDomain;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.Single;
import org.junit.Test;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DomainsResourceTest extends JerseySpringTest {

    @Test
    public void shouldGetDomains() {
        final Domain mockDomain = new Domain();
        mockDomain.setId("domain-id-1");
        mockDomain.setName("domain-name-1");

        final Domain mockDomain2 = new Domain();
        mockDomain2.setId("domain-id-2");
        mockDomain2.setName("domain-name-2");

        final Set<Domain> domains = new HashSet<>(Arrays.asList(mockDomain, mockDomain2));

        doReturn(Single.just(domains)).when(domainService).findAll();

        final Response response = target("domains").request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final List<Domain> responseEntity = response.readEntity(List.class);
        assertTrue(responseEntity.size() == 2);
    }

    @Test
    public void shouldGetDomains_technicalManagementException() {
        doReturn(Single.error(new TechnicalManagementException("error occurs"))).when(domainService).findAll();

        final Response response = target("domains").request().get();
        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
    }

    @Test
    public void shouldCreate() {
        NewDomain newDomain = new NewDomain();
        newDomain.setName("domain-name");

        Domain domain = new Domain();
        domain.setId("domain-id");
        domain.setName("domain-name");

        doReturn(Single.just(domain)).when(domainService).create(any(), any());
        doReturn(Single.just(new IdentityProvider())).when(identityProviderManager).create(domain.getId());
        doReturn(Single.just(new Reporter())).when(reporterService).createDefault(domain.getId());

        final Response response = target("domains").request().post(Entity.json(newDomain));
        assertEquals(HttpStatusCode.CREATED_201, response.getStatus());
    }
}
