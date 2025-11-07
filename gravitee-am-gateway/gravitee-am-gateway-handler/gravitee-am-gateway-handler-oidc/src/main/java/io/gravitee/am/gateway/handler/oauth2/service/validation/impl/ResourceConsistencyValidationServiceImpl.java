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
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.service.validation.ResourceConsistencyValidationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Implementation of {@link ResourceConsistencyValidationService} that validates resource consistency
 * between authorization and token requests according to RFC 8707.
 * <p>
 * The validation can be controlled via the {@code legacy.rfc8707.enabled} configuration property.
 * When disabled ({@code false}), consistency validation is skipped to allow backward compatibility.
 * <p>
 * Default behaviour: validation is enabled ({@code true}) to maintain RFC 8707 compliance.
 *
 * @author GraviteeSource Team
 */
@Slf4j
@Component
public class ResourceConsistencyValidationServiceImpl implements ResourceConsistencyValidationService {

    static final String LEGACY_RFC8707_ENABLED = "legacy.rfc8707.enabled";

    private final boolean validationEnabled;

    @Autowired
    public ResourceConsistencyValidationServiceImpl(Environment environment) {
        this.validationEnabled = environment.getProperty(LEGACY_RFC8707_ENABLED, Boolean.class, true);
    }

    @Override
    public Set<String> resolveFinalResources(OAuth2Request tokenRequest, Set<String> authorizationResources) {
        final Set<String> requested = tokenRequest.getResources();
        if (requested == null || requested.isEmpty()) {
            return authorizationResources == null ? java.util.Collections.emptySet() : authorizationResources;
        }

        // Check feature flag: if disabled, skip consistency validation
        if (!validationEnabled) {
            log.debug("Resource consistency validation skipped (legacy.rfc8707.enabled=false)");
            // When validation is disabled, return requested resources without consistency check
            return requested;
        }

        // When validation is enabled, perform consistency check
        final Set<String> authorized = authorizationResources == null ? java.util.Collections.emptySet() : authorizationResources;
        if (!authorized.containsAll(requested)) {
            throw new InvalidResourceException(
                "The requested resource is not recognized by this authorization server."
            );
        }
        return requested;
    }
}

