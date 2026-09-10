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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.management.handlers.automation.AutomationJerseySpringTest;
import io.gravitee.am.management.handlers.automation.model.AutomationDataPlane;
import io.gravitee.am.model.ManagedBy;
import io.gravitee.am.service.exception.DataPlaneDefinitionAlreadyExistsException;
import io.gravitee.am.service.model.DataPlaneDefinitionSummary;
import io.gravitee.am.service.model.NewDataPlaneDefinition;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
class DataPlanesResourceTest extends AutomationJerseySpringTest {

    private static final String DATA_PLANE_ID = "acme-eu";
    private static final String SECRET = "sup3r-s3cret";

    private final ObjectMapper mapper = new ObjectMapper();

    private DataPlaneDefinitionSummary summary(String id, ManagedBy managedBy) {
        return new DataPlaneDefinitionSummary(id, "Data plane " + id, "mongodb", "https://gw.example.com",
                ORG_ID, ENV_ID, "gravitee-am-" + id, List.of("mongo:27017"), managedBy, new Date(), new Date());
    }

    private AutomationDataPlane definition(String id) {
        AutomationDataPlane definition = new AutomationDataPlane();
        definition.setId(id);
        definition.setName("Data plane " + id);
        definition.setType("mongodb");
        definition.setGatewayUrl("https://gw.example.com");
        definition.setConfiguration(readTree("{\"mongodb\":{\"dbname\":\"gravitee-am-acme\",\"host\":\"mongo\","
                + "\"port\":27017,\"username\":\"am-user\",\"password\":\"" + SECRET + "\"}}"));
        return definition;
    }

    private com.fasterxml.jackson.databind.JsonNode readTree(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private void givenEnvironmentHolds(DataPlaneDefinitionSummary... summaries) {
        when(dataPlaneDefinitionService.findByEnvironmentId(ENV_ID)).thenReturn(Flowable.fromArray(summaries));
    }

    @Test
    void list_returns_only_the_data_planes_the_automation_api_manages() {
        givenEnvironmentHolds(summary("acme-eu", ManagedBy.AUTOMATION_API),
                summary("legacy", ManagedBy.NONE),
                summary("acme-us", ManagedBy.AUTOMATION_API));

        Response response = dataPlanesTarget().request().get();

        assertEquals(200, response.getStatus());
        List<AutomationDataPlane> body = readListEntity(response, AutomationDataPlane.class);
        assertEquals(List.of("acme-eu", "acme-us"), body.stream().map(AutomationDataPlane::getId).toList());
    }

    @Test
    void list_never_returns_the_connection_settings() {
        givenEnvironmentHolds(summary(DATA_PLANE_ID, ManagedBy.AUTOMATION_API));

        String body = readEntity(dataPlanesTarget().request().get(), String.class);

        assertFalse(body.contains("configuration"), body);
        assertFalse(body.contains(SECRET), body);
    }

    @Test
    void list_returns_403_when_the_permission_is_denied() {
        givenEnvironmentHolds(summary(DATA_PLANE_ID, ManagedBy.AUTOMATION_API));
        denyPermission();

        assertEquals(403, dataPlanesTarget().request().get().getStatus());
    }

    @Test
    void put_creates_a_data_plane_the_automation_api_owns() {
        givenEnvironmentHolds();
        when(dataPlaneDefinitionService.create(any(), any(), any()))
                .thenReturn(Single.just(summary(DATA_PLANE_ID, ManagedBy.AUTOMATION_API)));

        Response response = put(dataPlanesTarget(), definition(DATA_PLANE_ID));

        assertEquals(200, response.getStatus());
        ArgumentCaptor<NewDataPlaneDefinition> captor = ArgumentCaptor.forClass(NewDataPlaneDefinition.class);
        verify(dataPlaneDefinitionService).create(captor.capture(), eq(ManagedBy.AUTOMATION_API), any());
        assertEquals(DATA_PLANE_ID, captor.getValue().getId());
        assertEquals(ORG_ID, captor.getValue().getOrganizationId());
        assertEquals(ENV_ID, captor.getValue().getEnvironmentId());
    }

    @Test
    void put_registers_the_new_data_plane_on_this_node() {
        givenEnvironmentHolds();
        when(dataPlaneDefinitionService.create(any(), any(), any()))
                .thenReturn(Single.just(summary(DATA_PLANE_ID, ManagedBy.AUTOMATION_API)));

        put(dataPlanesTarget(), definition(DATA_PLANE_ID));

        verify(provisionedDataPlaneLoader).activate(DATA_PLANE_ID);
    }

    @Test
    void put_never_returns_the_connection_settings() {
        givenEnvironmentHolds();
        when(dataPlaneDefinitionService.create(any(), any(), any()))
                .thenReturn(Single.just(summary(DATA_PLANE_ID, ManagedBy.AUTOMATION_API)));

        String body = readEntity(put(dataPlanesTarget(), definition(DATA_PLANE_ID)), String.class);

        assertFalse(body.contains("configuration"), body);
        assertFalse(body.contains(SECRET), body);
    }

    @Test
    void put_returns_409_when_the_id_belongs_to_a_data_plane_it_does_not_manage() {
        // not automation-managed, so the resolver skips it and the create path refuses the taken id
        givenEnvironmentHolds(summary(DATA_PLANE_ID, ManagedBy.NONE));
        when(dataPlaneDefinitionService.create(any(), any(), any()))
                .thenReturn(Single.error(new DataPlaneDefinitionAlreadyExistsException(DATA_PLANE_ID)));

        Response response = put(dataPlanesTarget(), definition(DATA_PLANE_ID));

        assertEquals(409, response.getStatus());
    }

    @Test
    void put_rejects_an_id_that_is_not_a_valid_key() {
        givenEnvironmentHolds();

        Response response = put(dataPlanesTarget(), definition("Not A Key"));

        assertEquals(400, response.getStatus());
        verify(dataPlaneDefinitionService, never()).create(any(), any(), any());
    }

    @Test
    void put_updates_a_data_plane_it_already_manages() {
        givenEnvironmentHolds(summary(DATA_PLANE_ID, ManagedBy.AUTOMATION_API));
        when(dataPlaneDefinitionService.update(anyString(), any(), any()))
                .thenReturn(Single.just(summary(DATA_PLANE_ID, ManagedBy.AUTOMATION_API)));

        Response response = put(dataPlanesTarget(), definition(DATA_PLANE_ID));

        assertEquals(200, response.getStatus());
        verify(dataPlaneDefinitionService).update(eq(DATA_PLANE_ID), any(), any());
        verify(dataPlaneDefinitionService, never()).create(any(), any(), any());
    }

    @Test
    void put_by_id_reference_updates_a_data_plane_it_does_not_manage() {
        givenEnvironmentHolds(summary(DATA_PLANE_ID, ManagedBy.NONE));
        when(dataPlaneDefinitionService.update(anyString(), any(), any()))
                .thenReturn(Single.just(summary(DATA_PLANE_ID, ManagedBy.NONE)));

        Response response = put(dataPlanesTarget(), definition("id:" + DATA_PLANE_ID));

        assertEquals(200, response.getStatus());
        verify(dataPlaneDefinitionService).update(eq(DATA_PLANE_ID), any(), any());
        verify(dataPlaneDefinitionService, never()).create(any(), any(), any());
    }

    @Test
    void put_by_id_reference_returns_404_when_nothing_exists_to_update() {
        givenEnvironmentHolds();

        Response response = put(dataPlanesTarget(), definition("id:" + DATA_PLANE_ID));

        assertEquals(404, response.getStatus());
        verify(dataPlaneDefinitionService, never()).create(any(), any(), any());
    }

    @Test
    void put_returns_403_before_anything_is_created() {
        givenEnvironmentHolds();
        denyPermission();

        Response response = put(dataPlanesTarget(), definition(DATA_PLANE_ID));

        assertEquals(403, response.getStatus());
        verify(dataPlaneDefinitionService, never()).create(any(), any(), any());
        verify(provisionedDataPlaneLoader, never()).activate(anyString());
    }

    @Test
    void put_returns_403_before_anything_is_updated() {
        givenEnvironmentHolds(summary(DATA_PLANE_ID, ManagedBy.AUTOMATION_API));
        denyPermission();

        Response response = put(dataPlanesTarget(), definition(DATA_PLANE_ID));

        assertEquals(403, response.getStatus());
        verify(dataPlaneDefinitionService, never()).update(anyString(), any(), any());
    }
}
