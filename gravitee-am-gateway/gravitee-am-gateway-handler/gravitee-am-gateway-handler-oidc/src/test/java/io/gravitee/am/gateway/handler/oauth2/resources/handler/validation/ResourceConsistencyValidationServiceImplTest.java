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
package io.gravitee.am.gateway.handler.oauth2.resources.handler.validation;

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ResourceConsistencyValidationServiceImpl
 */
@RunWith(MockitoJUnitRunner.class)
public class ResourceConsistencyValidationServiceImplTest {

    private ResourceConsistencyValidationServiceImpl service;

    @Before
    public void setUp() {
        service = new ResourceConsistencyValidationServiceImpl();
    }

    @Test
    public void shouldReturnAuthorizationResourcesWhenTokenRequestHasNoResources() {
        // Given
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setResources(Collections.emptySet());
        Set<String> authorizationResources = new HashSet<>(Arrays.asList("https://api.example.com/photos", "https://api.example.com/albums"));

        // When
        Set<String> result = service.resolveFinalResources(tokenRequest, authorizationResources);

        // Then
        assertThat(result).isEqualTo(authorizationResources);
    }

    @Test
    public void shouldReturnTokenRequestResourcesWhenSubsetOfAuthorizationResources() {
        // Given
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setResources(new HashSet<>(Arrays.asList("https://api.example.com/photos")));
        Set<String> authorizationResources = new HashSet<>(Arrays.asList("https://api.example.com/photos", "https://api.example.com/albums"));

        // When
        Set<String> result = service.resolveFinalResources(tokenRequest, authorizationResources);

        // Then
        assertThat(result).isEqualTo(new HashSet<>(Arrays.asList("https://api.example.com/photos")));
    }

    @Test
    public void shouldReturnTokenRequestResourcesWhenIdenticalToAuthorizationResources() {
        // Given
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setResources(new HashSet<>(Arrays.asList("https://api.example.com/photos", "https://api.example.com/albums")));
        Set<String> authorizationResources = new HashSet<>(Arrays.asList("https://api.example.com/photos", "https://api.example.com/albums"));

        // When
        Set<String> result = service.resolveFinalResources(tokenRequest, authorizationResources);

        // Then
        assertThat(result).isEqualTo(new HashSet<>(Arrays.asList("https://api.example.com/photos", "https://api.example.com/albums")));
    }

    @Test
    public void shouldThrowWhenTokenRequestHasResourcesNotInAuthorization() {
        // Given
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setResources(new HashSet<>(Arrays.asList("https://api.example.com/photos", "https://api.example.com/unknown")));
        Set<String> authorizationResources = new HashSet<>(Arrays.asList("https://api.example.com/photos", "https://api.example.com/albums"));

        // Then
        try {
            service.resolveFinalResources(tokenRequest, authorizationResources);
            org.junit.Assert.fail("Expected InvalidResourceException");
        } catch (InvalidResourceException ex) {
            assertThat(ex.getOAuth2ErrorCode()).isEqualTo("invalid_target");
        }
    }

    @Test
    public void shouldThrowWhenAuthorizationHasNoResources() {
        // Given
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setResources(new HashSet<>(Arrays.asList("https://api.example.com/photos")));
        Set<String> authorizationResources = Collections.emptySet();

        try {
            service.resolveFinalResources(tokenRequest, authorizationResources);
            org.junit.Assert.fail("Expected InvalidResourceException");
        } catch (InvalidResourceException ex) {
            assertThat(ex.getOAuth2ErrorCode()).isEqualTo("invalid_target");
        }
    }

    @Test
    public void shouldReturnEmptyWhenBothTokenRequestAndAuthorizationHaveNoResources() {
        // Given
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setResources(Collections.emptySet());
        Set<String> authorizationResources = Collections.emptySet();

        // When
        Set<String> result = service.resolveFinalResources(tokenRequest, authorizationResources);

        // Then
        assertThat(result).isEmpty();
    }
}
