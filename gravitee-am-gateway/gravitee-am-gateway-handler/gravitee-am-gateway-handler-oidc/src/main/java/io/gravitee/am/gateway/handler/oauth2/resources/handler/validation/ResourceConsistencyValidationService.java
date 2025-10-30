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
 * Service for validating resource parameter consistency between authorization and token requests
 * according to RFC 8707.
 * 
 * RFC 8707 requires that:
 * 1. Token request resources must be identical to or subsets of authorization resources
 * 2. If token request contains resources not in original authorization, reject with invalid_target
 * 3. If no resources in token request, use original authorization resources
 *
 * @author GraviteeSource Team
 */
public interface ResourceConsistencyValidationService {

    /**
     * Validates and resolves the final resources to be used for token issuance according to RFC 8707:
     * - If token request provides resources, they must be a subset of authorization resources and are returned
     * - If token request provides no resources, authorization resources are returned
     *
     * @param tokenRequest the token request containing resource parameters
     * @param authorizationResources resources from the original authorization or refresh token
     * @return the final set of resources to apply to the token
     * @throws InvalidResourceException if the request resources are not a subset of authorization resources
     */
    Set<String> resolveFinalResources(OAuth2Request tokenRequest, Set<String> authorizationResources);
}
