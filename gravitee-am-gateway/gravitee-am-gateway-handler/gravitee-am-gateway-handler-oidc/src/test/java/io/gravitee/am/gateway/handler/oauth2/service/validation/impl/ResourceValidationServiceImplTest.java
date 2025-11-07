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

import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceManager;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidResourceException;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.model.ProtectedResource;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceValidationServiceImplTest {

    @Mock
    private ProtectedResourceManager protectedResourceManager;

    @Mock
    private Environment environment;

    private ResourceValidationServiceImpl service;

    @BeforeEach
    void setUp() {
        // Default: feature flag enabled (true)
        when(environment.getProperty(ResourceValidationServiceImpl.LEGACY_RFC8707_ENABLED, Boolean.class, true)).thenReturn(true);
        service = new ResourceValidationServiceImpl(protectedResourceManager, environment);
    }

    // Helper methods to reduce duplication
    private ResourceValidationServiceImpl createServiceWithFeatureFlag(boolean enabled) {
        when(environment.getProperty(ResourceValidationServiceImpl.LEGACY_RFC8707_ENABLED, Boolean.class, true)).thenReturn(enabled);
        return new ResourceValidationServiceImpl(protectedResourceManager, environment);
    }

    private AuthorizationRequest createRequestWithResources(String... resources) {
        AuthorizationRequest request = new AuthorizationRequest();
        request.setResources(Set.of(resources));
        return request;
    }

    private ProtectedResource createProtectedResource(String id, String... resourceIdentifiers) {
        ProtectedResource resource = new ProtectedResource();
        resource.setId(id);
        resource.setResourceIdentifiers(List.of(resourceIdentifiers));
        return resource;
    }

    private void assertValidationSucceeds(ResourceValidationServiceImpl service, AuthorizationRequest request) {
        TestObserver<Void> observer = service.validate(request).test();
        observer.awaitDone(1, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoErrors();
    }

    private void assertValidationFails(ResourceValidationServiceImpl service, AuthorizationRequest request) {
        TestObserver<Void> observer = service.validate(request).test();
        observer.awaitDone(1, TimeUnit.SECONDS);
        observer.assertError(t -> t instanceof InvalidResourceException &&
                ((InvalidResourceException) t).getOAuth2ErrorCode().equals("invalid_target"));
    }

    @Nested
    class WhenValidationEnabled {

        @Test
        void shouldRejectUnknownResource() {
            AuthorizationRequest request = createRequestWithResources("https://api.example.com/unknown");
            when(protectedResourceManager.entities()).thenReturn(Collections.emptyList());

            assertValidationFails(service, request);
        }

        @Test
        void shouldAcceptKnownResource() {
            AuthorizationRequest request = createRequestWithResources("https://api.example.com/photos");
            ProtectedResource known = createProtectedResource("res-1",
                    "https://api.example.com/photos", "https://api.example.com/albums");
            when(protectedResourceManager.entities()).thenReturn(List.of(known));

            assertValidationSucceeds(service, request);
        }

        @Test
        void shouldRejectWhenSomeResourcesUnknown() {
            AuthorizationRequest request = createRequestWithResources(
                    "https://api.example.com/photos", "https://api.example.com/unknown");
            ProtectedResource known = createProtectedResource("res-1",
                    "https://api.example.com/photos", "https://api.example.com/albums");
            when(protectedResourceManager.entities()).thenReturn(List.of(known));

            assertValidationFails(service, request);
        }

        @Test
        void shouldAcceptWhenAllRequestedResourcesAreKnown() {
            AuthorizationRequest request = createRequestWithResources(
                    "https://api.example.com/photos", "https://api.example.com/albums");
            ProtectedResource known = createProtectedResource("res-1",
                    "https://api.example.com/photos", "https://api.example.com/albums");
            when(protectedResourceManager.entities()).thenReturn(List.of(known));

            assertValidationSucceeds(service, request);
        }

        @Test
        void shouldAcceptWhenNoResourcesRequested() {
            AuthorizationRequest request = new AuthorizationRequest();
            request.setResources(Collections.emptySet());

            assertValidationSucceeds(service, request);
        }
    }

    @Nested
    class WhenValidationDisabled {

        @Test
        void shouldSkipValidation_whenInvalidResourceRequested() {
            // When feature flag is disabled, validation is skipped, so protectedResourceManager is never called
            ResourceValidationServiceImpl serviceWithFlagDisabled = createServiceWithFeatureFlag(false);
            AuthorizationRequest request = createRequestWithResources("https://api.example.com/unknown");

            assertValidationSucceeds(serviceWithFlagDisabled, request);
        }

        @Test
        void shouldSkipValidation_whenMultipleInvalidResourcesRequested() {
            // When feature flag is disabled, validation is skipped, so protectedResourceManager is never called
            ResourceValidationServiceImpl serviceWithFlagDisabled = createServiceWithFeatureFlag(false);
            AuthorizationRequest request = createRequestWithResources(
                    "https://api.example.com/unknown1", "https://api.example.com/unknown2");

            assertValidationSucceeds(serviceWithFlagDisabled, request);
        }
    }
}


