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

import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.reactivex.rxjava3.core.Completable;

import java.util.Set;

/**
 * Implementation of ResourceConsistencyValidationService that validates resource parameter
 * consistency between authorization and token requests according to RFC 8707.
 *
 * @author GraviteeSource Team
 */
public class ResourceConsistencyValidationServiceImpl implements ResourceConsistencyValidationService {

    @Override
    public Completable validateConsistency(OAuth2Request tokenRequest, Set<String> authorizationResources) {
        return Completable.fromAction(() -> {
            Set<String> tokenRequestResources = tokenRequest.getResources();

            // If no resources in token request, validation passes (will use authorization resources)
            if (tokenRequestResources == null || tokenRequestResources.isEmpty()) {
                return;
            }

            // Token request resources must be a subset of the authorization resources
            if (authorizationResources == null || !authorizationResources.containsAll(tokenRequestResources)) {
                throw new InvalidResourceException(
                    "The requested resource is not recognized by this authorization server."
                );
            }
        });
    }
}
