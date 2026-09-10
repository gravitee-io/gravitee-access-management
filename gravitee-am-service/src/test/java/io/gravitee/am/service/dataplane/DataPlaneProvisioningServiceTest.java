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
package io.gravitee.am.service.dataplane;

import io.gravitee.am.model.ManagedBy;
import io.gravitee.am.service.DataPlaneDefinitionService;
import io.gravitee.am.service.exception.DataPlaneInUseByDomainsException;
import io.gravitee.am.service.model.DataPlaneDefinitionSummary;
import io.gravitee.am.service.model.NewDataPlaneDefinition;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class DataPlaneProvisioningServiceTest {

    private static final String DATA_PLANE_ID = "dp-acme";

    @Mock
    private DataPlaneDefinitionService dataPlaneDefinitionService;

    @Mock
    private ProvisionedDataPlaneLoader provisionedDataPlaneLoader;

    private DataPlaneProvisioningService provisioningService() {
        return new DataPlaneProvisioningService(dataPlaneDefinitionService, provisionedDataPlaneLoader);
    }

    @Test
    void should_serve_a_provisioned_data_plane_on_this_node() {
        var definition = new NewDataPlaneDefinition();
        when(dataPlaneDefinitionService.create(definition, ManagedBy.NONE, null)).thenReturn(Single.just(summary()));
        when(provisionedDataPlaneLoader.activate(DATA_PLANE_ID)).thenReturn(Completable.complete());

        provisioningService().provision(definition, ManagedBy.NONE, null).test()
                .assertValue(summary -> DATA_PLANE_ID.equals(summary.id()));

        InOrder inOrder = inOrder(dataPlaneDefinitionService, provisionedDataPlaneLoader);
        inOrder.verify(dataPlaneDefinitionService).create(definition, ManagedBy.NONE, null);
        inOrder.verify(provisionedDataPlaneLoader).activate(DATA_PLANE_ID);
    }

    @Test
    void should_not_serve_a_data_plane_that_was_not_stored() {
        var definition = new NewDataPlaneDefinition();
        when(dataPlaneDefinitionService.create(any(), any(), any()))
                .thenReturn(Single.error(new IllegalStateException("connection lost")));

        provisioningService().provision(definition, ManagedBy.NONE, null).test()
                .assertError(IllegalStateException.class);

        verify(provisionedDataPlaneLoader, never()).activate(anyString());
    }

    @Test
    void should_rebuild_the_provider_of_a_reprovisioned_data_plane() {
        var definition = new NewDataPlaneDefinition();
        when(dataPlaneDefinitionService.update(DATA_PLANE_ID, definition, null)).thenReturn(Single.just(summary()));
        when(provisionedDataPlaneLoader.activate(DATA_PLANE_ID)).thenReturn(Completable.complete());

        provisioningService().reprovision(DATA_PLANE_ID, definition, null).test()
                .assertValue(summary -> DATA_PLANE_ID.equals(summary.id()));

        InOrder inOrder = inOrder(dataPlaneDefinitionService, provisionedDataPlaneLoader);
        inOrder.verify(dataPlaneDefinitionService).update(DATA_PLANE_ID, definition, null);
        inOrder.verify(provisionedDataPlaneLoader).activate(DATA_PLANE_ID);
    }

    @Test
    void should_keep_serving_a_data_plane_whose_new_configuration_was_refused() {
        when(dataPlaneDefinitionService.update(anyString(), any(), any()))
                .thenReturn(Single.error(new IllegalStateException("connection lost")));

        provisioningService().reprovision(DATA_PLANE_ID, new NewDataPlaneDefinition(), null).test()
                .assertError(IllegalStateException.class);

        verify(provisionedDataPlaneLoader, never()).activate(anyString());
    }

    @Test
    void should_stop_serving_a_deprovisioned_data_plane_on_this_node() {
        when(dataPlaneDefinitionService.delete(DATA_PLANE_ID, null)).thenReturn(Completable.complete());

        provisioningService().deprovision(DATA_PLANE_ID, null).test().assertComplete();

        InOrder inOrder = inOrder(dataPlaneDefinitionService, provisionedDataPlaneLoader);
        inOrder.verify(dataPlaneDefinitionService).delete(DATA_PLANE_ID, null);
        inOrder.verify(provisionedDataPlaneLoader).deactivate(DATA_PLANE_ID);
    }

    @Test
    void should_keep_serving_a_data_plane_whose_deletion_was_refused() {
        when(dataPlaneDefinitionService.delete(anyString(), any()))
                .thenReturn(Completable.error(new DataPlaneInUseByDomainsException(DATA_PLANE_ID)));

        provisioningService().deprovision(DATA_PLANE_ID, null).test()
                .assertError(DataPlaneInUseByDomainsException.class);

        verify(provisionedDataPlaneLoader, never()).deactivate(anyString());
    }

    private static DataPlaneDefinitionSummary summary() {
        return new DataPlaneDefinitionSummary(DATA_PLANE_ID, "ACME", "mongodb", "https://gateway", "org", "env",
                "gravitee-am", List.of("mongo:27017"), ManagedBy.NONE, null, null);
    }
}
