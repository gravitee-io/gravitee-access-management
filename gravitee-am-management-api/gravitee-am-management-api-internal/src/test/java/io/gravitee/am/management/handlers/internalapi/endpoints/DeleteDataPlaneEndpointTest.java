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
package io.gravitee.am.management.handlers.internalapi.endpoints;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.service.DataPlaneDefinitionService;
import io.gravitee.am.service.dataplane.ProvisionedDataPlaneLoader;
import io.gravitee.am.service.exception.DataPlaneDefinitionNotFoundException;
import io.gravitee.am.service.exception.DataPlaneInUseByDomainsException;
import io.gravitee.common.http.HttpMethod;
import io.reactivex.rxjava3.core.Completable;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeleteDataPlaneEndpointTest {

    @Mock
    private DataPlaneDefinitionService dataPlaneDefinitionService;

    @Mock
    private DataPlaneRegistry dataPlaneRegistry;

    @Mock
    private ProvisionedDataPlaneLoader provisionedDataPlaneLoader;

    @Mock
    private RoutingContext routingContext;

    private HttpServerResponse response;

    private DeleteDataPlaneEndpoint endpoint;

    @BeforeEach
    void setUp() {
        response = mock(HttpServerResponse.class, RETURNS_SELF);
        when(routingContext.response()).thenReturn(response);
        endpoint = new DeleteDataPlaneEndpoint(dataPlaneDefinitionService, dataPlaneRegistry, provisionedDataPlaneLoader, new ObjectMapper());
    }

    @Test
    void shouldBeMountedAsADeleteOnDataplanesWithAnIdParameter() {
        assertThat(endpoint.method()).isEqualTo(HttpMethod.DELETE);
        assertThat(endpoint.path()).isEqualTo("/dataplanes/:id");
    }

    @Test
    void shouldReturn204OnDeletion() {
        when(routingContext.pathParam(DeleteDataPlaneEndpoint.PARAM_ID)).thenReturn("dp-acme");
        when(dataPlaneDefinitionService.delete("dp-acme")).thenReturn(Completable.complete());

        endpoint.handle(routingContext);

        verify(response).setStatusCode(204);
        verify(response).end();
    }

    @Test
    void shouldStopServingTheDataPlaneOnThisNode() {
        when(routingContext.pathParam(DeleteDataPlaneEndpoint.PARAM_ID)).thenReturn("dp-acme");
        when(dataPlaneDefinitionService.delete("dp-acme")).thenReturn(Completable.complete());

        endpoint.handle(routingContext);

        verify(dataPlaneRegistry).unregister("dp-acme");
        verify(provisionedDataPlaneLoader).forget("dp-acme");
    }

    @Test
    void shouldKeepServingTheDataPlaneWhenTheDeletionIsRefused() {
        when(routingContext.pathParam(DeleteDataPlaneEndpoint.PARAM_ID)).thenReturn("dp-acme");
        when(dataPlaneDefinitionService.delete("dp-acme"))
                .thenReturn(Completable.error(new DataPlaneInUseByDomainsException("dp-acme")));

        endpoint.handle(routingContext);

        verify(dataPlaneRegistry, never()).unregister(any());
        verify(provisionedDataPlaneLoader, never()).forget(any());
    }

    @Test
    void shouldReturn404WhenTheDefinitionDoesNotExist() {
        when(routingContext.pathParam(DeleteDataPlaneEndpoint.PARAM_ID)).thenReturn("dp-missing");
        when(dataPlaneDefinitionService.delete("dp-missing"))
                .thenReturn(Completable.error(new DataPlaneDefinitionNotFoundException("dp-missing")));

        endpoint.handle(routingContext);

        verify(response).setStatusCode(404);
        assertThat(responseBody()).contains("dp-missing");
    }

    @Test
    void shouldReturn409WhenADomainStillUsesTheDataPlane() {
        when(routingContext.pathParam(DeleteDataPlaneEndpoint.PARAM_ID)).thenReturn("dp-acme");
        when(dataPlaneDefinitionService.delete("dp-acme"))
                .thenReturn(Completable.error(new DataPlaneInUseByDomainsException("dp-acme")));

        endpoint.handle(routingContext);

        verify(response).setStatusCode(409);
        assertThat(responseBody()).contains("used by at least one domain");
    }

    @Test
    void shouldReturn500OnAnUnexpectedFailure() {
        when(routingContext.pathParam(DeleteDataPlaneEndpoint.PARAM_ID)).thenReturn("dp-acme");
        when(dataPlaneDefinitionService.delete("dp-acme")).thenReturn(Completable.error(new IllegalStateException("boom")));

        endpoint.handle(routingContext);

        verify(response).setStatusCode(500);
        assertThat(responseBody()).doesNotContain("boom");
    }

    private String responseBody() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(response).end(captor.capture());
        return captor.getValue();
    }
}
