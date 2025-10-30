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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(MockitoJUnitRunner.class)
public class ResourceConsistencyValidationServiceImplTest {

    private ResourceConsistencyValidationServiceImpl service;

    @Before
    public void setUp() {
        service = new ResourceConsistencyValidationServiceImpl();
    }

    @Test
    public void shouldReturnAuthorizationResourcesWhenTokenRequestHasNoResources() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setResources(Collections.emptySet());
        Set<String> authorizationResources = new HashSet<>(Arrays.asList("https://api.example.com/photos", "https://api.example.com/albums"));

        Set<String> result = service.resolveFinalResources(tokenRequest, authorizationResources);

        assertThat(result).isEqualTo(authorizationResources);
    }

    @Test
    public void shouldReturnTokenRequestResourcesWhenSubsetOfAuthorizationResources() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setResources(new HashSet<>(Arrays.asList("https://api.example.com/photos")));
        Set<String> authorizationResources = new HashSet<>(Arrays.asList("https://api.example.com/photos", "https://api.example.com/albums"));

        Set<String> result = service.resolveFinalResources(tokenRequest, authorizationResources);

        assertThat(result).isEqualTo(new HashSet<>(Arrays.asList("https://api.example.com/photos")));
    }

    @Test
    public void shouldReturnTokenRequestResourcesWhenIdenticalToAuthorizationResources() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setResources(new HashSet<>(Arrays.asList("https://api.example.com/photos", "https://api.example.com/albums")));
        Set<String> authorizationResources = new HashSet<>(Arrays.asList("https://api.example.com/photos", "https://api.example.com/albums"));

        Set<String> result = service.resolveFinalResources(tokenRequest, authorizationResources);

        assertThat(result).isEqualTo(new HashSet<>(Arrays.asList("https://api.example.com/photos", "https://api.example.com/albums")));
    }

    @Test(expected = InvalidResourceException.class)
    public void shouldThrowWhenTokenRequestHasResourcesNotInAuthorization() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setResources(new HashSet<>(Arrays.asList("https://api.example.com/photos", "https://api.example.com/unknown")));
        Set<String> authorizationResources = new HashSet<>(Arrays.asList("https://api.example.com/photos", "https://api.example.com/albums"));

        service.resolveFinalResources(tokenRequest, authorizationResources);
    }

    @Test(expected = InvalidResourceException.class)
    public void shouldThrowWhenAuthorizationHasNoResources() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setResources(new HashSet<>(Arrays.asList("https://api.example.com/photos")));
        Set<String> authorizationResources = Collections.emptySet();

        service.resolveFinalResources(tokenRequest, authorizationResources);
    }

    @Test
    public void shouldReturnEmptyWhenBothTokenRequestAndAuthorizationHaveNoResources() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setResources(Collections.emptySet());
        Set<String> authorizationResources = Collections.emptySet();

        Set<String> result = service.resolveFinalResources(tokenRequest, authorizationResources);

        assertThat(result).isEmpty();
    }
}


