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
import io.gravitee.am.management.handlers.internalapi.endpoints.ListDataPlanesEndpoint;
import io.gravitee.am.service.DataPlaneDefinitionService;
import io.gravitee.am.service.model.DataPlaneDefinitionSummary;
import io.gravitee.common.http.HttpMethod;
import io.reactivex.rxjava3.core.Flowable;
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
class ListDataPlanesEndpointTest {

    @Mock
    private DataPlaneDefinitionService dataPlaneDefinitionService;

    @Mock
    private RoutingContext routingContext;

    private HttpServerResponse response;

    private ListDataPlanesEndpoint endpoint;

    @BeforeEach
    void setUp() {
        response = mock(HttpServerResponse.class, RETURNS_SELF);
        when(routingContext.response()).thenReturn(response);
        endpoint = new ListDataPlanesEndpoint(dataPlaneDefinitionService, new ObjectMapper());
    }

    @Test
    void shouldBeMountedAsAGetOnDataplanes() {
        assertThat(endpoint.method()).isEqualTo(HttpMethod.GET);
        assertThat(endpoint.path()).isEqualTo("/dataplanes");
    }

    @Test
    void shouldReturn200WithEveryDefinition() {
        when(dataPlaneDefinitionService.findAll()).thenReturn(Flowable.just(summary("dp-acme", "env-1"), summary("dp-other", "env-2")));

        endpoint.handle(routingContext);

        verify(response).setStatusCode(200);
        assertThat(responseBody())
                .contains("dp-acme")
                .contains("dp-other")
                .contains("gravitee-am-acme")
                .contains("mongo:27017");
    }

    @Test
    void shouldNeverExposeTheStoredConnectionSettings() {
        when(dataPlaneDefinitionService.findAll()).thenReturn(Flowable.just(summary("dp-acme", "env-1")));

        endpoint.handle(routingContext);

        assertThat(responseBody())
                .doesNotContain("configuration")
                .doesNotContain("password")
                .doesNotContain("username");
    }

    @Test
    void shouldReturn200WithAnEmptyArrayWhenNothingIsProvisioned() {
        when(dataPlaneDefinitionService.findAll()).thenReturn(Flowable.empty());

        endpoint.handle(routingContext);

        verify(response).setStatusCode(200);
        assertThat(responseBody()).isEqualTo("[]");
    }

    @Test
    void shouldReturn500OnAnUnexpectedFailure() {
        when(dataPlaneDefinitionService.findAll()).thenReturn(Flowable.error(new IllegalStateException("boom")));

        endpoint.handle(routingContext);

        verify(response).setStatusCode(500);
        assertThat(responseBody()).doesNotContain("boom");
    }

    private String responseBody() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(response).end(captor.capture());
        return captor.getValue();
    }

    private DataPlaneDefinitionSummary summary(String id, String environmentId) {
        return new DataPlaneDefinitionSummary(
                id,
                "Data plane " + id,
                "mongodb",
                "https://gw-" + id + ".cloud.gravitee.io",
                "DEFAULT",
                environmentId,
                "gravitee-am-acme",
                List.of("mongo:27017"),
                new Date(),
                new Date());
    }
}
