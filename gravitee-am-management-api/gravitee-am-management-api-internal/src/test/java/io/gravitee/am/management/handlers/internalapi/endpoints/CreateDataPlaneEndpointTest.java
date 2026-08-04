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
import io.gravitee.am.management.handlers.internalapi.endpoints.CreateDataPlaneEndpoint;
import io.gravitee.am.service.DataPlaneDefinitionService;
import io.gravitee.am.service.exception.DataPlaneDefinitionAlreadyExistsException;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.am.service.model.DataPlaneDefinitionSummary;
import io.gravitee.am.service.model.NewDataPlaneDefinition;
import io.gravitee.common.http.HttpMethod;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RequestBody;
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
class CreateDataPlaneEndpointTest {

    private static final String PAYLOAD = """
            {
              "id": "dp-acme",
              "name": "Customer ACME",
              "type": "mongodb",
              "gatewayUrl": "https://gw-acme.cloud.gravitee.io",
              "configuration": {
                "mongodb": {
                  "dbname": "gravitee-am-acme",
                  "host": "mongo",
                  "username": "am-user",
                  "password": "sup3r-s3cret"
                }
              }
            }
            """;

    @Mock
    private DataPlaneDefinitionService dataPlaneDefinitionService;

    @Mock
    private RoutingContext routingContext;

    @Mock
    private RequestBody requestBody;

    private HttpServerResponse response;

    private CreateDataPlaneEndpoint endpoint;

    @BeforeEach
    void setUp() {
        response = mock(HttpServerResponse.class, RETURNS_SELF);
        when(routingContext.body()).thenReturn(requestBody);
        when(routingContext.response()).thenReturn(response);
        endpoint = new CreateDataPlaneEndpoint(dataPlaneDefinitionService, new ObjectMapper());
    }

    @Test
    void shouldBeMountedAsAPostOnDataplanes() {
        assertThat(endpoint.method()).isEqualTo(HttpMethod.POST);
        assertThat(endpoint.path()).isEqualTo("/dataplanes");
    }

    @Test
    void shouldReturn201WithTheCreatedDefinition() {
        when(requestBody.asString()).thenReturn(PAYLOAD);
        when(dataPlaneDefinitionService.create(any())).thenReturn(Single.just(summary()));

        endpoint.handle(routingContext);

        verify(response).setStatusCode(201);
        assertThat(responseBody()).contains("dp-acme").contains("Customer ACME").contains("gravitee-am-acme");
    }

    @Test
    void shouldNotEchoTheSubmittedCredentialsBack() {
        when(requestBody.asString()).thenReturn(PAYLOAD);
        when(dataPlaneDefinitionService.create(any())).thenReturn(Single.just(summary()));

        endpoint.handle(routingContext);

        assertThat(responseBody())
                .doesNotContain("sup3r-s3cret")
                .doesNotContain("am-user")
                .doesNotContain("password")
                .doesNotContain("configuration");
    }

    @Test
    void shouldPassTheDeserialisedPayloadToTheService() {
        when(requestBody.asString()).thenReturn(PAYLOAD);
        when(dataPlaneDefinitionService.create(any())).thenReturn(Single.just(summary()));

        endpoint.handle(routingContext);

        ArgumentCaptor<NewDataPlaneDefinition> captor = ArgumentCaptor.forClass(NewDataPlaneDefinition.class);
        verify(dataPlaneDefinitionService).create(captor.capture());

        NewDataPlaneDefinition payload = captor.getValue();
        assertThat(payload.getId()).isEqualTo("dp-acme");
        assertThat(payload.getType()).isEqualTo("mongodb");
        assertThat(payload.getGatewayUrl()).isEqualTo("https://gw-acme.cloud.gravitee.io");
        assertThat(payload.getConfiguration().at("/mongodb/dbname").asText()).isEqualTo("gravitee-am-acme");
    }

    @Test
    void shouldReturn400OnMalformedJson() {
        when(requestBody.asString()).thenReturn("{ not json");

        endpoint.handle(routingContext);

        verify(response).setStatusCode(400);
        verify(dataPlaneDefinitionService, never()).create(any());
    }

    @Test
    void shouldNameTheUnknownFieldOnAMisspelledProperty() {
        when(requestBody.asString()).thenReturn("""
                {"id":"dp-acme","name":"n","type":"mongodb","gatewayURL":"https://gw","configuration":{"mongodb":{"dbname":"d","host":"h"}}}
                """);

        endpoint.handle(routingContext);

        verify(response).setStatusCode(400);
        verify(dataPlaneDefinitionService, never()).create(any());
        assertThat(responseBody()).contains("unknown field [gatewayURL]");
    }

    @Test
    void shouldNotEchoAnUnknownFieldValueBack() {
        when(requestBody.asString()).thenReturn("""
                {"id":"dp-acme","password":"sup3r-s3cret"}
                """);

        endpoint.handle(routingContext);

        verify(response).setStatusCode(400);
        assertThat(responseBody()).contains("unknown field [password]").doesNotContain("sup3r-s3cret");
    }

    @Test
    void shouldReturn400OnAnEmptyBody() {
        when(requestBody.asString()).thenReturn(null);

        endpoint.handle(routingContext);

        verify(response).setStatusCode(400);
        verify(dataPlaneDefinitionService, never()).create(any());
    }

    @Test
    void shouldReturn400WhenTheServiceRejectsTheDefinition() {
        when(requestBody.asString()).thenReturn(PAYLOAD);
        when(dataPlaneDefinitionService.create(any()))
                .thenReturn(Single.error(new InvalidParameterException("configuration.mongodb requires either 'uri' or 'dbname'")));

        endpoint.handle(routingContext);

        verify(response).setStatusCode(400);
        assertThat(responseBody()).contains("dbname");
    }

    @Test
    void shouldReturn409OnADuplicateId() {
        when(requestBody.asString()).thenReturn(PAYLOAD);
        when(dataPlaneDefinitionService.create(any()))
                .thenReturn(Single.error(new DataPlaneDefinitionAlreadyExistsException("dp-acme")));

        endpoint.handle(routingContext);

        verify(response).setStatusCode(409);
        assertThat(responseBody()).contains("dp-acme");
    }

    @Test
    void shouldReturn500OnAnUnexpectedFailure() {
        when(requestBody.asString()).thenReturn(PAYLOAD);
        when(dataPlaneDefinitionService.create(any())).thenReturn(Single.error(new IllegalStateException("boom")));

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
