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
package io.gravitee.am.service.reporter.builder.management;

import com.google.common.collect.ImmutableMap;
import io.gravitee.am.reporter.api.audit.model.AuditEntity;

import java.util.HashMap;
import java.util.List;

/**
 * Base class for credential audit builders that need to store route paths in audit attributes.
 * This class provides common functionality for both WebAuthn and Certificate credential audit builders.
 *
 * @author GraviteeSource Team
 */
public abstract class CredentialAuditBuilderBase<T extends CredentialAuditBuilderBase<T>> extends ManagementAuditBuilder<T> {

    protected static final String ROUTE_PATH_ATTRIBUTE_KEY = "routePath";

    protected String[] routePath;

    protected CredentialAuditBuilderBase() {
        super();
    }

    /**
     * Sets the route path for UI navigation.
     * The route path should be an array of route segments, e.g., ["users", userId, "credentials", credentialId]
     *
     * @param routePath Array of route path segments
     * @return this builder for method chaining
     */
    protected T setRoutePath(String[] routePath) {
        this.routePath = routePath;
        return (T) this;
    }

    @Override
    protected AuditEntity createTarget() {
        AuditEntity target = super.createTarget();
        if (routePath != null) {
            HashMap<String, Object> attributes = new HashMap<>(target.getAttributes());
            attributes.put(ROUTE_PATH_ATTRIBUTE_KEY, List.of(routePath));
            target.setAttributes(ImmutableMap.copyOf(attributes));
        }
        return target;
    }
}

