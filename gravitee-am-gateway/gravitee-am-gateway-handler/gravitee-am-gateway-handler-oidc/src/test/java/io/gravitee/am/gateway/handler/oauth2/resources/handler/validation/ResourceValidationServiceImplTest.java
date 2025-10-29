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

import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceManager;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.model.ProtectedResource;
import java.util.Set;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.when;

/**
 * TDD for ResourceValidationServiceImpl: validates that requested resources
 * are recognized protected resources; otherwise errors with invalid_target.
 */
@RunWith(MockitoJUnitRunner.class)
public class ResourceValidationServiceImplTest {

    @Mock
    private ProtectedResourceManager protectedResourceManager;

    private ResourceValidationServiceImpl service;

    @Before
    public void init() {
        service = new ResourceValidationServiceImpl(protectedResourceManager);
    }

    @Test
    public void shouldRejectUnknownResource_withInvalidTarget() {
        AuthorizationRequest request = new AuthorizationRequest();
        request.setResources(Set.of("https://api.example.com/unknown"));

        when(protectedResourceManager.entities()).thenReturn(Collections.emptyList());

        TestObserver<Void> observer = service.validate(request).test();
        observer.awaitDone(1, TimeUnit.SECONDS);
        observer.assertError(t -> t instanceof InvalidResourceException &&
                ((InvalidResourceException) t).getOAuth2ErrorCode().equals("invalid_target"));
    }

    @Test
    public void shouldAcceptKnownResource() {
        AuthorizationRequest request = new AuthorizationRequest();
        request.setResources(Set.of("https://api.example.com/photos"));

        ProtectedResource known = new ProtectedResource();
        known.setId("res-1");
        known.setResourceIdentifiers(Arrays.asList("https://api.example.com/photos", "https://api.example.com/albums"));

        when(protectedResourceManager.entities()).thenReturn(List.of(known));

        TestObserver<Void> observer = service.validate(request).test();
        observer.awaitDone(1, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoErrors();
    }
}


