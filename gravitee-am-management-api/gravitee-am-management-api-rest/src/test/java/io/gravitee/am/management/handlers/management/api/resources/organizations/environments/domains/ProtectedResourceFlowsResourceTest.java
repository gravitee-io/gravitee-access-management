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
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ProtectedResource;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.flow.Flow;
import io.gravitee.am.model.flow.Type;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

public class ProtectedResourceFlowsResourceTest extends JerseySpringTest {

    private static final String DOMAIN_ID = "domain-1";
    private static final String PROTECTED_RESOURCE_ID = "protected-resource-1";

    @Test
    public void shouldGetFlows() {
        Flow tokenFlow = new Flow();
        tokenFlow.setType(Type.TOKEN);

        doReturn(Flowable.just(tokenFlow)).when(flowService).findByApplication(ReferenceType.DOMAIN, DOMAIN_ID, PROTECTED_RESOURCE_ID);
        doReturn(Maybe.just(domain(DOMAIN_ID))).when(domainService).findById(DOMAIN_ID);
        doReturn(Maybe.just(protectedResource())).when(protectedResourceService).findById(PROTECTED_RESOURCE_ID);

        final Response response = flowsTarget().request().get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final List<?> responseEntity = response.readEntity(new GenericType<List<?>>() {});
        assertEquals(1, responseEntity.size());
    }

    @Test
    public void shouldGetFlows_protectedResourceNotFound() {
        doReturn(Maybe.just(domain(DOMAIN_ID))).when(domainService).findById(DOMAIN_ID);
        doReturn(Maybe.empty()).when(protectedResourceService).findById(PROTECTED_RESOURCE_ID);

        final Response response = flowsTarget().request().get();
        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldUpdateFlows() {
        Flow tokenFlow = new Flow();
        tokenFlow.setType(Type.TOKEN);

        doReturn(Maybe.just(domain(DOMAIN_ID))).when(domainService).findById(DOMAIN_ID);
        doReturn(Maybe.just(protectedResource())).when(protectedResourceService).findById(PROTECTED_RESOURCE_ID);
        doReturn(Completable.complete()).when(flowValidator).validateAll(anyList());
        doReturn(Single.just(List.of(tokenFlow)))
                .when(flowService)
                .createOrUpdate(eq(ReferenceType.DOMAIN), eq(DOMAIN_ID), eq(PROTECTED_RESOURCE_ID), anyList(), any(User.class));

        final Response response = flowsTarget().request().put(Entity.json(List.of(newTokenFlow())));
        assertEquals(HttpStatusCode.OK_200, response.getStatus());
    }

    @Test
    public void shouldNotUpdateFlows_nonTokenFlowRejected() {
        io.gravitee.am.service.model.Flow loginFlow = newTokenFlow();
        loginFlow.setType(Type.LOGIN);

        final Response response = flowsTarget().request().put(Entity.json(List.of(loginFlow)));
        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());
    }

    @Test
    public void shouldGetFlows_technicalManagementException() {
        doReturn(Maybe.just(domain(DOMAIN_ID))).when(domainService).findById(DOMAIN_ID);
        doReturn(Maybe.just(protectedResource())).when(protectedResourceService).findById(PROTECTED_RESOURCE_ID);
        doReturn(Flowable.error(new TechnicalManagementException("error occurs")))
                .when(flowService).findByApplication(ReferenceType.DOMAIN, DOMAIN_ID, PROTECTED_RESOURCE_ID);

        final Response response = flowsTarget().request().get();
        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
    }

    private jakarta.ws.rs.client.WebTarget flowsTarget() {
        return target("domains")
                .path(DOMAIN_ID)
                .path("protected-resources")
                .path(PROTECTED_RESOURCE_ID)
                .path("flows");
    }

    private static Domain domain(String id) {
        Domain domain = new Domain();
        domain.setId(id);
        return domain;
    }

    private static ProtectedResource protectedResource() {
        ProtectedResource protectedResource = new ProtectedResource();
        protectedResource.setId(PROTECTED_RESOURCE_ID);
        return protectedResource;
    }

    private static io.gravitee.am.service.model.Flow newTokenFlow() {
        io.gravitee.am.service.model.Flow flow = new io.gravitee.am.service.model.Flow();
        flow.setType(Type.TOKEN);
        flow.setName("TOKEN");
        flow.setEnabled(true);
        return flow;
    }
}
