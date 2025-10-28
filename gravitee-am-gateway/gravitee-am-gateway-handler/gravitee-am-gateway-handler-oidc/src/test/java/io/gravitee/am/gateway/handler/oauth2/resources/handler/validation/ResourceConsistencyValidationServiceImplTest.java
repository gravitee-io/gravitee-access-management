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
import io.reactivex.rxjava3.observers.TestObserver;
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
    public void shouldPassWhenTokenRequestHasNoResources() {
        // Given
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setResources(Collections.emptySet());
        Set<String> authorizationResources = new HashSet<>(Arrays.asList("https://api.example.com/photos", "https://api.example.com/albums"));

        // When
        TestObserver<Void> observer = service.validateConsistency(tokenRequest, authorizationResources).test();

        // Then
        observer.awaitDone(1, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoErrors();
    }

    @Test
    public void shouldPassWhenTokenRequestResourcesAreSubsetOfAuthorizationResources() {
        // Given
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setResources(new HashSet<>(Arrays.asList("https://api.example.com/photos")));
        Set<String> authorizationResources = new HashSet<>(Arrays.asList("https://api.example.com/photos", "https://api.example.com/albums"));

        // When
        TestObserver<Void> observer = service.validateConsistency(tokenRequest, authorizationResources).test();

        // Then
        observer.awaitDone(1, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoErrors();
    }

    @Test
    public void shouldPassWhenTokenRequestResourcesAreIdenticalToAuthorizationResources() {
        // Given
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setResources(new HashSet<>(Arrays.asList("https://api.example.com/photos", "https://api.example.com/albums")));
        Set<String> authorizationResources = new HashSet<>(Arrays.asList("https://api.example.com/photos", "https://api.example.com/albums"));

        // When
        TestObserver<Void> observer = service.validateConsistency(tokenRequest, authorizationResources).test();

        // Then
        observer.awaitDone(1, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoErrors();
    }

    @Test
    public void shouldRejectWhenTokenRequestHasResourcesNotInAuthorization() {
        // Given
        TokenRequest tokenRequest = new TokenRequest();
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.RESOURCE, "https://api.example.com/photos");
        parameters.add(Parameters.RESOURCE, "https://api.example.com/unknown");
        tokenRequest.setParameters(parameters);
        Set<String> authorizationResources = new HashSet<>(Arrays.asList("https://api.example.com/photos", "https://api.example.com/albums"));

        // When
        TestObserver<Void> observer = service.validateConsistency(tokenRequest, authorizationResources).test();

        // Then
        observer.awaitDone(1, TimeUnit.SECONDS);
        observer.assertError(InvalidResourceException.class);
        observer.assertError(ex -> {
            assertThat(((InvalidResourceException) ex).getOAuth2ErrorCode()).isEqualTo("invalid_target");
            return true;
        });
    }

    @Test
    public void shouldRejectWhenAuthorizationHasNoResources() {
        // Given
        TokenRequest tokenRequest = new TokenRequest();
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(Parameters.RESOURCE, "https://api.example.com/photos");
        tokenRequest.setParameters(parameters);
        Set<String> authorizationResources = Collections.emptySet();

        // When
        TestObserver<Void> observer = service.validateConsistency(tokenRequest, authorizationResources).test();

        // Then
        observer.awaitDone(1, TimeUnit.SECONDS);
        observer.assertError(InvalidResourceException.class);
        observer.assertError(ex -> {
            assertThat(((InvalidResourceException) ex).getOAuth2ErrorCode()).isEqualTo("invalid_target");
            return true;
        });
    }

    @Test
    public void shouldPassWhenBothTokenRequestAndAuthorizationHaveNoResources() {
        // Given
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setResources(Collections.emptySet());
        Set<String> authorizationResources = Collections.emptySet();

        // When
        TestObserver<Void> observer = service.validateConsistency(tokenRequest, authorizationResources).test();

        // Then
        observer.awaitDone(1, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoErrors();
    }
}
