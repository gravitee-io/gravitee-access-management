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
package io.gravitee.am.management.handlers.automation.resource;

import io.gravitee.am.management.service.impl.DefaultIdentityProviderServiceImpl;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Computes the deterministic internal UUID for an Automation-API-managed resource by folding
 * the resource's external {@code key} with the id of its scope (environment for domains,
 * domain for identity providers, certificates and reporters). The same {@code (scope, key)} pair
 * always resolves to the same primary key, which gives idempotent upserts and uniqueness-within-scope
 * without any database index or constraint.
 *
 * @author GraviteeSource Team
 */
public final class AutomationIds {

    private AutomationIds() {
    }

    public static String domainId(String environmentId, String key) {
        return deterministicId(environmentId, key);
    }

    public static String domainId(String environmentId, AutomationRef ref) {
        return switch (ref) {
            case AutomationRef.IdRef(String id) -> id;
            case AutomationRef.KeyRef(String key) -> deterministicId(environmentId, key);
        };
    }

    public static String identityProviderId(String domainId, String key) {
        return deterministicId(domainId, key);
    }

    public static String certificateId(String domainId, String key) {
        return deterministicId(domainId, key);
    }

    public static String reporterId(String domainId, String key) {
        return deterministicId(domainId, key);
    }

    /**
     * The conventional id of a domain's system identity provider — the same id the platform assigns to
     * the built-in default, and the one the gateway falls back to for user registration.
     */
    public static String systemIdentityProviderId(String domainId) {
        return DefaultIdentityProviderServiceImpl.DEFAULT_IDP_PREFIX + domainId.toLowerCase();
    }

    private static String deterministicId(String scopeId, String key) {
        return UUID.nameUUIDFromBytes((scopeId + "/" + key).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
