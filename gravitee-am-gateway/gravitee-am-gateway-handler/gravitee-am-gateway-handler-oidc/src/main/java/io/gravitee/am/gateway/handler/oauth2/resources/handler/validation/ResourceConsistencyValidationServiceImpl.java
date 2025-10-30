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
    public Set<String> resolveFinalResources(OAuth2Request tokenRequest, Set<String> authorizationResources) {
        final Set<String> requested = tokenRequest.getResources();
        if (requested == null || requested.isEmpty()) {
            // No requested resources: use authorization resources (may be null/empty)
            return authorizationResources == null ? java.util.Collections.emptySet() : authorizationResources;
        }

        final Set<String> authorized = authorizationResources == null ? java.util.Collections.emptySet() : authorizationResources;
        if (!authorized.containsAll(requested)) {
            throw new InvalidResourceException(
                "The requested resource is not recognized by this authorization server."
            );
        }
        return requested;
    }
}
