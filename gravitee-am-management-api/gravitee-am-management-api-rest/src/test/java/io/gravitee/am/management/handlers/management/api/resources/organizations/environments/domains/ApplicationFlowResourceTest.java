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

import io.gravitee.am.management.handlers.management.api.JerseySpringTest;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.flow.Flow;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Maybe;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;

public class ApplicationFlowResourceTest extends JerseySpringTest {

    private static final String DOMAIN_ID = "domain-a";
    private static final String APPLICATION_ID = "app-1";
    private static final String FLOW_ID = "flow-1";

    @Test
    public void shouldGetFlow() {
        doReturn(Maybe.just(application())).when(applicationService).findById(DOMAIN_ID, APPLICATION_ID);
        doReturn(Maybe.just(flow(APPLICATION_ID))).when(flowService).findById(ReferenceType.DOMAIN, DOMAIN_ID, FLOW_ID);

        final Response response = flowTarget().request().get();

        assertEquals(HttpStatusCode.OK_200, response.getStatus());
    }

    @Test
    public void shouldNotGetFlow_applicationFromAnotherDomain() {
        doReturn(Maybe.empty()).when(applicationService).findById(DOMAIN_ID, APPLICATION_ID);
        doReturn(Maybe.just(flow(APPLICATION_ID))).when(flowService).findById(ReferenceType.DOMAIN, DOMAIN_ID, FLOW_ID);

        final Response response = flowTarget().request().get();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    @Test
    public void shouldNotGetFlow_flowFromAnotherApplication() {
        doReturn(Maybe.just(application())).when(applicationService).findById(DOMAIN_ID, APPLICATION_ID);
        doReturn(Maybe.just(flow("app-2"))).when(flowService).findById(ReferenceType.DOMAIN, DOMAIN_ID, FLOW_ID);

        final Response response = flowTarget().request().get();

        assertEquals(HttpStatusCode.NOT_FOUND_404, response.getStatus());
    }

    private WebTarget flowTarget() {
        return target("domains").path(DOMAIN_ID).path("applications").path(APPLICATION_ID).path("flows").path(FLOW_ID);
    }

    private static Application application() {
        final Application application = new Application();
        application.setId(APPLICATION_ID);
        application.setDomain(DOMAIN_ID);
        return application;
    }

    private static Flow flow(String application) {
        final Flow flow = new Flow();
        flow.setId(FLOW_ID);
        flow.setApplication(application);
        return flow;
    }
}
