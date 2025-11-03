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
package io.gravitee.am.gateway.handler.oauth2.resources.request;

import io.gravitee.am.common.oauth2.Parameters;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ResourceParameterUtils
 */
@RunWith(MockitoJUnitRunner.class)
public class ResourceParameterUtilsTest {

    @Mock
    private RoutingContext routingContext;

    @Mock
    private HttpServerRequest httpServerRequest;

    @Mock
    private MultiMap params;

    @Test
    public void shouldReturnEmptyForEmptyCases() {
        // Given
        when(routingContext.request()).thenReturn(httpServerRequest);
        when(httpServerRequest.params()).thenReturn(params);

        java.util.List<java.util.List<String>> cases = java.util.Arrays.asList(
            null,
            java.util.Collections.emptyList(),
            java.util.Arrays.asList(null, null)
        );

        for (java.util.List<String> providedParams : cases) {
            when(params.getAll(Parameters.RESOURCE)).thenReturn(providedParams);
            // When
            Set<String> result = ResourceParameterUtils.parseResourceParameters(routingContext);
            // Then
            assertThat(result).isEmpty();
        }
    }

    @Test
    public void shouldParseSingleResourceParameter() {
        // Given
        when(routingContext.request()).thenReturn(httpServerRequest);
        when(httpServerRequest.params()).thenReturn(params);
        when(params.getAll(Parameters.RESOURCE)).thenReturn(java.util.Arrays.asList("https://api.example.com/photos"));

        // When
        Set<String> result = ResourceParameterUtils.parseResourceParameters(routingContext);

        // Then
        assertThat(result).containsExactly("https://api.example.com/photos");
    }

    @Test
    public void shouldParseMultipleResourceParameters() {
        // Given
        when(routingContext.request()).thenReturn(httpServerRequest);
        when(httpServerRequest.params()).thenReturn(params);
        when(params.getAll(Parameters.RESOURCE)).thenReturn(java.util.Arrays.asList(
            "https://api.example.com/photos",
            "https://api.example.com/albums"
        ));

        // When
        Set<String> result = ResourceParameterUtils.parseResourceParameters(routingContext);

        // Then
        assertThat(result).containsExactlyInAnyOrder(
            "https://api.example.com/photos",
            "https://api.example.com/albums"
        );
    }

    @Test
    public void shouldIncludeEmptyResourceParameter() {
        // Given
        when(routingContext.request()).thenReturn(httpServerRequest);
        when(httpServerRequest.params()).thenReturn(params);
        when(params.getAll(Parameters.RESOURCE)).thenReturn(java.util.Arrays.asList(""));

        // When
        Set<String> result = ResourceParameterUtils.parseResourceParameters(routingContext);

        // Then
        assertThat(result).containsExactly("");
    }

    @Test
    public void shouldTrimResourceParameters() {
        // Given
        when(routingContext.request()).thenReturn(httpServerRequest);
        when(httpServerRequest.params()).thenReturn(params);
        when(params.getAll(Parameters.RESOURCE)).thenReturn(java.util.Arrays.asList("  https://api.example.com/photos  "));

        // When
        Set<String> result = ResourceParameterUtils.parseResourceParameters(routingContext);

        // Then
        assertThat(result).containsExactly("https://api.example.com/photos");
    }

    @Test
    public void shouldFilterOutNullResourceParameters() {
        // Given
        when(routingContext.request()).thenReturn(httpServerRequest);
        when(httpServerRequest.params()).thenReturn(params);
        when(params.getAll(Parameters.RESOURCE)).thenReturn(java.util.Arrays.asList(
            "https://api.example.com/photos",
            null,
            "https://api.example.com/albums"
        ));

        // When
        Set<String> result = ResourceParameterUtils.parseResourceParameters(routingContext);

        // Then
        assertThat(result).containsExactlyInAnyOrder(
            "https://api.example.com/photos",
            "https://api.example.com/albums"
        );
    }

    @Test
    public void shouldReturnEmptyWhenAllResourceParametersAreNull() {
        // Given
        when(routingContext.request()).thenReturn(httpServerRequest);
        when(httpServerRequest.params()).thenReturn(params);
        when(params.getAll(Parameters.RESOURCE)).thenReturn(java.util.Arrays.asList(null, null));

        // When
        Set<String> result = ResourceParameterUtils.parseResourceParameters(routingContext);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    public void shouldWorkWithHttpServerRequestDirectly() {
        // Given
        when(httpServerRequest.params()).thenReturn(params);
        when(params.getAll(Parameters.RESOURCE)).thenReturn(java.util.Arrays.asList("https://api.example.com/photos"));

        // When
        Set<String> result = ResourceParameterUtils.parseResourceParameters(httpServerRequest);

        // Then
        assertThat(result).containsExactly("https://api.example.com/photos");
    }

    @Test
    public void shouldDeduplicateIdenticalResourceParameters() {
        // Given
        when(routingContext.request()).thenReturn(httpServerRequest);
        when(httpServerRequest.params()).thenReturn(params);
        when(params.getAll(Parameters.RESOURCE)).thenReturn(java.util.Arrays.asList(
            "https://api.example.com/photos",
            "https://api.example.com/photos",  // duplicate
            "https://api.example.com/albums"
        ));

        // When
        Set<String> result = ResourceParameterUtils.parseResourceParameters(routingContext);

        // Then
        assertThat(result)
            .containsExactlyInAnyOrder(
                "https://api.example.com/photos",
                "https://api.example.com/albums"
            )
            .hasSize(2); // Should only have 2 unique entries
    }

    @Test
    public void shouldDeduplicateIdenticalResourceParametersWithDifferentWhitespace() {
        // Given
        when(routingContext.request()).thenReturn(httpServerRequest);
        when(httpServerRequest.params()).thenReturn(params);
        when(params.getAll(Parameters.RESOURCE)).thenReturn(java.util.Arrays.asList(
            "https://api.example.com/photos",
            "  https://api.example.com/photos  ",  // same but with whitespace
            "https://api.example.com/albums"
        ));

        // When
        Set<String> result = ResourceParameterUtils.parseResourceParameters(routingContext);

        // Then
        assertThat(result)
            .containsExactlyInAnyOrder(
                "https://api.example.com/photos",
                "https://api.example.com/albums"
            )
            .hasSize(2); // Should only have 2 unique entries after trimming
    }
}
