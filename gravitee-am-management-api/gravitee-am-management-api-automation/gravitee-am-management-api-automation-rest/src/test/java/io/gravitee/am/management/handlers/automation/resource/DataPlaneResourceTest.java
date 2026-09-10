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
import io.gravitee.am.management.handlers.automation.model.AutomationDataPlane;
import io.gravitee.am.model.ManagedBy;
import io.gravitee.am.service.exception.DataPlaneInUseByDomainsException;
import io.gravitee.am.service.model.DataPlaneDefinitionSummary;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
class DataPlaneResourceTest extends AutomationJerseySpringTest {

    private static final String DATA_PLANE_ID = "acme-eu";

    private DataPlaneDefinitionSummary summary(String id, ManagedBy managedBy) {
        return new DataPlaneDefinitionSummary(id, "Data plane " + id, "mongodb", "https://gw.example.com",
                ORG_ID, ENV_ID, "gravitee-am-" + id, List.of("mongo:27017"), managedBy, new Date(), new Date());
    }

    private void givenEnvironmentHolds(DataPlaneDefinitionSummary... summaries) {
        when(dataPlaneDefinitionService.findByEnvironmentId(ENV_ID)).thenReturn(Flowable.fromArray(summaries));
    }

    private Response getRequest(String reference) {
        return dataPlanesTarget().path(reference).request().get();
    }

    private Response deleteRequest(String reference) {
        return dataPlanesTarget().path(reference).request().delete();
    }

    @Test
    void get_returns_a_data_plane_the_automation_api_manages() {
        givenEnvironmentHolds(summary(DATA_PLANE_ID, ManagedBy.AUTOMATION_API));

        Response response = getRequest(DATA_PLANE_ID);

        assertEquals(200, response.getStatus());
        AutomationDataPlane body = readEntity(response, AutomationDataPlane.class);
        assertEquals(DATA_PLANE_ID, body.getId());
        assertEquals("gravitee-am-" + DATA_PLANE_ID, body.getDatabase());
        assertNull(body.getConfiguration());
    }

    @Test
    void get_returns_404_when_the_data_plane_is_absent() {
        givenEnvironmentHolds();

        assertEquals(404, getRequest(DATA_PLANE_ID).getStatus());
    }

    @Test
    void get_returns_404_when_the_data_plane_is_not_automation_managed() {
        givenEnvironmentHolds(summary(DATA_PLANE_ID, ManagedBy.NONE));

        assertEquals(404, getRequest(DATA_PLANE_ID).getStatus());
    }

    @Test
    void get_reaches_a_data_plane_it_does_not_manage_by_id_reference() {
        givenEnvironmentHolds(summary(DATA_PLANE_ID, ManagedBy.NONE));

        Response response = getRequest("id:" + DATA_PLANE_ID);

        assertEquals(200, response.getStatus());
        assertEquals(DATA_PLANE_ID, readEntity(response, AutomationDataPlane.class).getId());
    }

    @Test
    void get_returns_403_when_the_permission_is_denied() {
        givenEnvironmentHolds(summary(DATA_PLANE_ID, ManagedBy.AUTOMATION_API));
        denyPermission();

        assertEquals(403, getRequest(DATA_PLANE_ID).getStatus());
    }

    @Test
    void delete_removes_the_data_plane_and_stops_serving_it() {
        givenEnvironmentHolds(summary(DATA_PLANE_ID, ManagedBy.AUTOMATION_API));
        when(dataPlaneDefinitionService.delete(anyString(), any())).thenReturn(Completable.complete());

        Response response = deleteRequest(DATA_PLANE_ID);

        assertEquals(204, response.getStatus());
        verify(dataPlaneDefinitionService).delete(eq(DATA_PLANE_ID), any());
        verify(provisionedDataPlaneLoader).deactivate(DATA_PLANE_ID);
    }

    @Test
    void delete_returns_204_when_the_data_plane_is_absent() {
        givenEnvironmentHolds();

        assertEquals(204, deleteRequest(DATA_PLANE_ID).getStatus());
        verify(dataPlaneDefinitionService, never()).delete(anyString(), any());
    }

    @Test
    void delete_returns_204_when_the_data_plane_is_not_automation_managed() {
        givenEnvironmentHolds(summary(DATA_PLANE_ID, ManagedBy.NONE));

        assertEquals(204, deleteRequest(DATA_PLANE_ID).getStatus());
        verify(dataPlaneDefinitionService, never()).delete(anyString(), any());
    }

    @Test
    void delete_returns_409_and_keeps_serving_it_while_a_domain_references_it() {
        givenEnvironmentHolds(summary(DATA_PLANE_ID, ManagedBy.AUTOMATION_API));
        when(dataPlaneDefinitionService.delete(anyString(), any()))
                .thenReturn(Completable.error(new DataPlaneInUseByDomainsException(DATA_PLANE_ID)));

        Response response = deleteRequest(DATA_PLANE_ID);

        assertEquals(409, response.getStatus());
        verify(provisionedDataPlaneLoader, never()).deactivate(anyString());
    }

    @Test
    void delete_returns_403_before_anything_is_removed() {
        givenEnvironmentHolds(summary(DATA_PLANE_ID, ManagedBy.AUTOMATION_API));
        denyPermission();

        Response response = deleteRequest(DATA_PLANE_ID);

        assertEquals(403, response.getStatus());
        verify(dataPlaneDefinitionService, never()).delete(anyString(), any());
        verify(provisionedDataPlaneLoader, never()).deactivate(anyString());
    }
}
