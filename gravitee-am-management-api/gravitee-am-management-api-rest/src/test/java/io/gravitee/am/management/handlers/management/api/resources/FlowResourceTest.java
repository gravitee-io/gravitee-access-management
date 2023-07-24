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

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.management.handlers.management.api.model.ErrorEntity;
import io.gravitee.am.management.handlers.management.api.model.FlowEntity;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.flow.Flow;
import io.gravitee.am.model.flow.Step;
import io.gravitee.am.model.flow.Type;
import io.gravitee.am.service.exception.FlowNotFoundException;
import io.gravitee.am.service.exception.PluginNotDeployedException;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.Test;

import javax.ws.rs.core.Response;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class FlowResourceTest extends JerseySpringTest {

    public static final String DOMAIN_ID = "domain#1";
    public static final String FLOW_ID = "flow#1";

    @Test
    public void shouldGetFlow() {
        final Flow mockFlow = new Flow();
        mockFlow.setId(FLOW_ID);
        mockFlow.setName("name");

        doReturn(Maybe.just(mockFlow)).when(flowService).findById(ReferenceType.DOMAIN, DOMAIN_ID, FLOW_ID);

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("flows")
                .path(FLOW_ID)
                .request()
                .get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final FlowEntity flow = readEntity(response, FlowEntity.class);
        assertEquals(mockFlow.getId(), flow.getId());
        assertEquals(mockFlow.getName(), flow.getName());
    }

    @Test
    public void shouldNotGetFlow_notFound() {
        doReturn(Maybe.empty()).when(flowService).findById(ReferenceType.DOMAIN, DOMAIN_ID, FLOW_ID);

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("flows")
                .path(FLOW_ID)
                .request()
                .get();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldUpdateFlow() {

        io.gravitee.am.service.model.Flow flowToUpdate = new io.gravitee.am.service.model.Flow();
        flowToUpdate.setName("updatedName");
        flowToUpdate.setType(Type.LOGIN);
        Step step = new Step();
        step.setPolicy("dummy-policy");
        flowToUpdate.setPost(List.of(step));

        Flow updatedFlow = new Flow();
        updatedFlow.setPost(List.of(step));
        updatedFlow.setName(flowToUpdate.getName());
        updatedFlow.setType(flowToUpdate.getType());

        doReturn(Completable.complete()).when(policyPluginService).checkPluginDeployment(step.getPolicy());
        doReturn(Single.just(updatedFlow)).when(flowService).update(eq(ReferenceType.DOMAIN), eq(DOMAIN_ID), eq(FLOW_ID), any(Flow.class), any(User.class));

        final Response response = put(target("domains")
                .path(DOMAIN_ID)
                .path("flows")
                .path(FLOW_ID), flowToUpdate);

        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final FlowEntity flow = readEntity(response, FlowEntity.class);
        assertEquals(flowToUpdate.getName(), flow.getName());
    }

    @Test
    public void shouldUpdateFlow_EmptyStep() {

        io.gravitee.am.service.model.Flow flowToUpdate = new io.gravitee.am.service.model.Flow();
        flowToUpdate.setName("updatedName");
        flowToUpdate.setType(Type.LOGIN);

        Flow updatedFlow = new Flow();
        updatedFlow.setName(flowToUpdate.getName());
        updatedFlow.setType(flowToUpdate.getType());

        doReturn(Single.just(updatedFlow)).when(flowService).update(eq(ReferenceType.DOMAIN), eq(DOMAIN_ID), eq(FLOW_ID), any(Flow.class), any(User.class));

        final Response response = put(target("domains")
                .path(DOMAIN_ID)
                .path("flows")
                .path(FLOW_ID), flowToUpdate);

        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final FlowEntity flow = readEntity(response, FlowEntity.class);
        assertEquals(flowToUpdate.getName(), flow.getName());
    }

    @Test
    public void shouldNotUpdateFlow_PolicyNotDeployed() {

        io.gravitee.am.service.model.Flow flowToUpdate = new io.gravitee.am.service.model.Flow();
        flowToUpdate.setName("updatedName");
        flowToUpdate.setType(Type.LOGIN);
        Step step = new Step();
        step.setPolicy("dummy-policy");
        flowToUpdate.setPost(List.of(step));

        Flow updatedFlow = new Flow();
        updatedFlow.setPost(List.of(step));
        updatedFlow.setName(flowToUpdate.getName());
        updatedFlow.setType(flowToUpdate.getType());

        doReturn(Completable.error(PluginNotDeployedException.forType(step.getPolicy()))).when(policyPluginService).checkPluginDeployment(step.getPolicy());

        final Response response = put(target("domains")
                .path(DOMAIN_ID)
                .path("flows")
                .path(FLOW_ID), flowToUpdate);

        assertEquals(HttpStatusCode.BAD_REQUEST_400, response.getStatus());

        final ErrorEntity error = readEntity(response, ErrorEntity.class);
        assertEquals("Plugin type dummy-policy not deployed", error.getMessage());
    }

    @Test
    public void shouldNotUpdateEntrypoint_notFound() {

        io.gravitee.am.service.model.Flow flowToUpdate = new io.gravitee.am.service.model.Flow();
        flowToUpdate.setName("updatedName");
        flowToUpdate.setType(Type.LOGIN);

        doReturn(Single.error(new FlowNotFoundException(FLOW_ID))).when(flowService).update(eq(ReferenceType.DOMAIN), eq(DOMAIN_ID), eq(FLOW_ID), any(Flow.class), any(User.class));

        final Response response = put(target("domains")
                .path(DOMAIN_ID)
                .path("flows")
                .path(FLOW_ID), flowToUpdate);

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }
}
