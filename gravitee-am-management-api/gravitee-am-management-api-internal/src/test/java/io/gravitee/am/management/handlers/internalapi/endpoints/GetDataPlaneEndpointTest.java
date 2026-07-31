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
import io.gravitee.am.management.handlers.internalapi.endpoints.GetDataPlaneEndpoint;
import io.gravitee.am.service.DataPlaneDefinitionService;
import io.gravitee.am.service.exception.DataPlaneDefinitionNotFoundException;
import io.gravitee.am.service.model.DataPlaneDefinitionSummary;
import io.gravitee.common.http.HttpMethod;
import io.reactivex.rxjava3.core.Single;
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

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GetDataPlaneEndpointTest {

    @Mock
    private DataPlaneDefinitionService dataPlaneDefinitionService;

    @Mock
    private RoutingContext routingContext;

    private HttpServerResponse response;

    private GetDataPlaneEndpoint endpoint;

    @BeforeEach
    void setUp() {
        response = mock(HttpServerResponse.class, RETURNS_SELF);
        when(routingContext.response()).thenReturn(response);
        endpoint = new GetDataPlaneEndpoint(dataPlaneDefinitionService, new ObjectMapper());
    }

    @Test
    void shouldBeMountedAsAGetOnDataplanesWithAnIdParameter() {
        assertThat(endpoint.method()).isEqualTo(HttpMethod.GET);
        assertThat(endpoint.path()).isEqualTo("/dataplanes/:id");
    }

    @Test
    void shouldReturn200WithTheDefinition() {
        when(routingContext.pathParam(GetDataPlaneEndpoint.PARAM_ID)).thenReturn("dp-acme");
        when(dataPlaneDefinitionService.findById("dp-acme")).thenReturn(Single.just(summary()));

        endpoint.handle(routingContext);

        verify(response).setStatusCode(200);
        assertThat(responseBody())
                .contains("dp-acme")
                .contains("gravitee-am-acme")
                .contains("mongo:27017");
    }

    @Test
    void shouldNeverExposeTheStoredConnectionSettings() {
        when(routingContext.pathParam(GetDataPlaneEndpoint.PARAM_ID)).thenReturn("dp-acme");
        when(dataPlaneDefinitionService.findById("dp-acme")).thenReturn(Single.just(summary()));

        endpoint.handle(routingContext);

        assertThat(responseBody())
                .doesNotContain("configuration")
                .doesNotContain("password")
                .doesNotContain("username");
    }

    @Test
    void shouldReturn404WhenTheDefinitionDoesNotExist() {
        when(routingContext.pathParam(GetDataPlaneEndpoint.PARAM_ID)).thenReturn("dp-missing");
        when(dataPlaneDefinitionService.findById("dp-missing"))
                .thenReturn(Single.error(new DataPlaneDefinitionNotFoundException("dp-missing")));

        endpoint.handle(routingContext);

        verify(response).setStatusCode(404);
        assertThat(responseBody()).contains("dp-missing");
    }

    @Test
    void shouldReturn500OnAnUnexpectedFailure() {
        when(routingContext.pathParam(GetDataPlaneEndpoint.PARAM_ID)).thenReturn("dp-acme");
        when(dataPlaneDefinitionService.findById("dp-acme")).thenReturn(Single.error(new IllegalStateException("boom")));

        endpoint.handle(routingContext);

        verify(response).setStatusCode(500);
        assertThat(responseBody()).doesNotContain("boom");
    }

    private String responseBody() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(response).end(captor.capture());
        return captor.getValue();
    }

    private DataPlaneDefinitionSummary summary() {
        return new DataPlaneDefinitionSummary(
                "dp-acme",
                "Customer ACME",
                "mongodb",
                "https://gw-acme.cloud.gravitee.io",
                "DEFAULT",
                "DEFAULT",
                "gravitee-am-acme",
                List.of("mongo:27017"),
                new Date(),
                new Date());
    }
}
