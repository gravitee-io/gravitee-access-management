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
import io.gravitee.am.management.service.encryption.ConfigurationEncryptionResult;
import io.gravitee.am.management.service.encryption.ConfigurationEncryptionResult.Status;
import io.gravitee.am.management.service.encryption.ConfigurationEncryptionService;
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

import java.util.List;
import java.util.Optional;

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
class EncryptConfigurationsEndpointTest {

    @Mock
    private ConfigurationEncryptionService configurationEncryptionService;

    @Mock
    private RoutingContext routingContext;

    private HttpServerResponse response;

    private EncryptConfigurationsEndpoint endpoint;

    @BeforeEach
    void setUp() {
        response = mock(HttpServerResponse.class, RETURNS_SELF);
        when(routingContext.response()).thenReturn(response);
        endpoint = new EncryptConfigurationsEndpoint(configurationEncryptionService, new ObjectMapper());
    }

    @Test
    void shouldBeMountedAsAPostOnEncryptionReencrypt() {
        assertThat(endpoint.method()).isEqualTo(HttpMethod.POST);
        assertThat(endpoint.path()).isEqualTo("/encryption/reencrypt");
    }

    @Test
    void shouldReturn400WhenNoKeyIsConfigured() {
        when(configurationEncryptionService.currentKeyId()).thenReturn(Optional.empty());

        endpoint.handle(routingContext);

        verify(response).setStatusCode(400);
        assertThat(body()).contains("repositories.management.encryption.keys");
        verify(configurationEncryptionService, never()).encryptAll(any());
    }

    @Test
    void shouldReturn200WithTheReportWhenEveryPluginIsEncrypted() {
        givenResults(result("identity_provider", Status.DONE, 3), result("reporter", Status.ALREADY_DONE, 0));

        endpoint.handle(routingContext);

        verify(response).setStatusCode(200);
        assertThat(body())
                .contains("\"keyId\":\"v2\"")
                .contains("\"plugin\":\"identity_provider\",\"status\":\"DONE\",\"encrypted\":3")
                .contains("\"plugin\":\"reporter\",\"status\":\"ALREADY_DONE\"");
    }

    @Test
    void shouldReturn409WhenAnotherCallIsStillRunning() {
        givenResults(result("identity_provider", Status.DONE, 3), result("reporter", Status.IN_PROGRESS, 0));

        endpoint.handle(routingContext);

        verify(response).setStatusCode(409);
    }

    @Test
    void shouldReturn500WhenAPluginFailed() {
        givenResults(result("identity_provider", Status.IN_PROGRESS, 0),
                new ConfigurationEncryptionResult("reporter", Status.FAILED, 0, "key [v1] is not configured"));

        endpoint.handle(routingContext);

        verify(response).setStatusCode(500);
        assertThat(body()).contains("key [v1] is not configured");
    }

    private void givenResults(ConfigurationEncryptionResult... results) {
        when(configurationEncryptionService.currentKeyId()).thenReturn(Optional.of("v2"));
        when(configurationEncryptionService.encryptAll("v2")).thenReturn(Single.just(List.of(results)));
    }

    private static ConfigurationEncryptionResult result(String plugin, Status status, long encrypted) {
        return new ConfigurationEncryptionResult(plugin, status, encrypted, null);
    }

    private String body() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(response).end(captor.capture());
        return captor.getValue();
    }
}
