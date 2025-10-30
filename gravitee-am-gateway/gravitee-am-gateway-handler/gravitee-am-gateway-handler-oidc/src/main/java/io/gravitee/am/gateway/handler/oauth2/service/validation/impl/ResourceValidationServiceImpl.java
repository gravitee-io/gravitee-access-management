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
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.service.validation.ResourceValidationService;
import io.reactivex.rxjava3.core.Completable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ResourceValidationServiceImpl implements ResourceValidationService {

    private final ProtectedResourceManager protectedResourceManager;

    @Autowired
    public ResourceValidationServiceImpl(ProtectedResourceManager protectedResourceManager) {
        this.protectedResourceManager = protectedResourceManager;
    }

    @Override
    public Completable validate(OAuth2Request request) {
        return Completable.fromCallable(() -> {
            Set<String> requestedResources = request.getResources();

            if (requestedResources == null || requestedResources.isEmpty()) {
                return null;
            }

            var configuredResources = protectedResourceManager.entities();

            Set<String> allResourceIdentifiers = configuredResources.stream()
                .filter(protectedResource -> protectedResource.getResourceIdentifiers() != null)
                .flatMap(protectedResource -> protectedResource.getResourceIdentifiers().stream())
                .collect(Collectors.toSet());

            if (!allResourceIdentifiers.containsAll(requestedResources)) {
                throw new InvalidResourceException(
                    "The requested resource is not recognized by this authorization server."
                );
            }

            return null;
        });
    }
}


