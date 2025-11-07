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
package io.gravitee.am.gateway.handler.oauth2.service.validation.impl;

import io.gravitee.am.gateway.handler.oauth2.exception.InvalidResourceException;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceConsistencyValidationServiceImplTest {

    @Mock
    private Environment environment;

    private ResourceConsistencyValidationServiceImpl service;

    @BeforeEach
    void setUp() {
        // Default: feature flag enabled (true)
        when(environment.getProperty(ResourceConsistencyValidationServiceImpl.LEGACY_RFC8707_ENABLED, Boolean.class, true)).thenReturn(true);
        service = new ResourceConsistencyValidationServiceImpl(environment);
    }

    // Helper methods to reduce duplication
    private ResourceConsistencyValidationServiceImpl createServiceWithFeatureFlag(boolean enabled) {
        when(environment.getProperty(ResourceConsistencyValidationServiceImpl.LEGACY_RFC8707_ENABLED, Boolean.class, true)).thenReturn(enabled);
        return new ResourceConsistencyValidationServiceImpl(environment);
    }

    @Nested
    class WhenValidationEnabled {

        @Test
        void shouldReturnAuthorizationResourcesWhenTokenRequestHasNoResources() {
            TokenRequest tokenRequest = new TokenRequest();
            tokenRequest.setResources(Collections.emptySet());
            Set<String> authorizationResources = Set.of("https://api.example.com/photos", "https://api.example.com/albums");

            Set<String> result = service.resolveFinalResources(tokenRequest, authorizationResources);

            assertThat(result).isEqualTo(authorizationResources);
        }

        @Test
        void shouldReturnTokenRequestResourcesWhenSubsetOfAuthorizationResources() {
            TokenRequest tokenRequest = new TokenRequest();
            tokenRequest.setResources(Set.of("https://api.example.com/photos"));
            Set<String> authorizationResources = Set.of("https://api.example.com/photos", "https://api.example.com/albums");

            Set<String> result = service.resolveFinalResources(tokenRequest, authorizationResources);

            assertThat(result).isEqualTo(Set.of("https://api.example.com/photos"));
        }

        @Test
        void shouldReturnTokenRequestResourcesWhenIdenticalToAuthorizationResources() {
            TokenRequest tokenRequest = new TokenRequest();
            tokenRequest.setResources(Set.of("https://api.example.com/photos", "https://api.example.com/albums"));
            Set<String> authorizationResources = Set.of("https://api.example.com/photos", "https://api.example.com/albums");

            Set<String> result = service.resolveFinalResources(tokenRequest, authorizationResources);

            assertThat(result).isEqualTo(Set.of("https://api.example.com/photos", "https://api.example.com/albums"));
        }

        @Test
        void shouldThrowWhenTokenRequestHasResourcesNotInAuthorization() {
            TokenRequest tokenRequest = new TokenRequest();
            tokenRequest.setResources(Set.of("https://api.example.com/photos", "https://api.example.com/unknown"));
            Set<String> authorizationResources = Set.of("https://api.example.com/photos", "https://api.example.com/albums");

            assertThatThrownBy(() -> service.resolveFinalResources(tokenRequest, authorizationResources))
                    .isInstanceOf(InvalidResourceException.class);
        }

        @Test
        void shouldThrowWhenAuthorizationHasNoResources() {
            TokenRequest tokenRequest = new TokenRequest();
            tokenRequest.setResources(Set.of("https://api.example.com/photos"));
            Set<String> authorizationResources = Collections.emptySet();

            assertThatThrownBy(() -> service.resolveFinalResources(tokenRequest, authorizationResources))
                    .isInstanceOf(InvalidResourceException.class);
        }

        @Test
        void shouldReturnEmptyWhenBothTokenRequestAndAuthorizationHaveNoResources() {
            TokenRequest tokenRequest = new TokenRequest();
            tokenRequest.setResources(Collections.emptySet());
            Set<String> authorizationResources = Collections.emptySet();

            Set<String> result = service.resolveFinalResources(tokenRequest, authorizationResources);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    class WhenValidationDisabled {

        @Test
        void shouldSkipConsistencyValidation_whenFeatureFlagDisabled_andResourcesMismatch() {
            // Setup: feature flag disabled
            ResourceConsistencyValidationServiceImpl serviceWithFlagDisabled = createServiceWithFeatureFlag(false);

            TokenRequest tokenRequest = new TokenRequest();
            tokenRequest.setResources(Set.of("https://api.example.com/photos", "https://api.example.com/unknown"));
            Set<String> authorizationResources = Set.of("https://api.example.com/photos", "https://api.example.com/albums");

            // Should not throw exception, should return requested resources
            Set<String> result = serviceWithFlagDisabled.resolveFinalResources(tokenRequest, authorizationResources);

            assertThat(result).isEqualTo(Set.of("https://api.example.com/photos", "https://api.example.com/unknown"));
        }
    }

}


