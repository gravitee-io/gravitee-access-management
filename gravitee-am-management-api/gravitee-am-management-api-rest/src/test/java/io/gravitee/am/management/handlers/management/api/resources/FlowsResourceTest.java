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
import io.gravitee.am.model.Entrypoint;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.flow.Flow;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.junit.Test;

import jakarta.ws.rs.core.Response;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class FlowsResourceTest extends JerseySpringTest {

    public static final String DOMAIN_ID = "domain-1";

    @Test
    public void shouldGetFlows() {
        doReturn(Flowable.just(new Flow(), new Flow())).when(flowService).findAll(ReferenceType.DOMAIN, DOMAIN_ID, true);

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("flows")
                .request()
                .get();
        assertEquals(HttpStatusCode.OK_200, response.getStatus());

        final List<Entrypoint> responseEntity = readEntity(response, List.class);
        assertEquals(2, responseEntity.size());
    }

    @Test
    public void shouldGetFlows_technicalManagementException() {
        doReturn(Flowable.error(new TechnicalManagementException("error occurs"))).when(flowService).findAll(ReferenceType.DOMAIN, DOMAIN_ID, true);

        final Response response = target("domains")
                .path(DOMAIN_ID)
                .path("flows")
                .request()
                .get();
        assertEquals(HttpStatusCode.INTERNAL_SERVER_ERROR_500, response.getStatus());
    }
}
