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
package io.gravitee.am.service.secrets.providers;

import io.gravitee.am.service.secrets.errors.SecretProviderNotFoundException;
import io.gravitee.secrets.api.plugin.SecretProvider;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author GraviteeSource Team
 */
public class SecretProviderRegistry {

    public static final String SECRET_PROVIDER_NOT_FOUND_FOR_ID = "No secret-provider plugin found for provider: '%s'";

    private final Map<String, SecretProvider> providers = new ConcurrentHashMap<>();

    public void register(String id, SecretProvider provider) {
        providers.put(id, provider);
    }

    public SecretProvider get(String id) {
        return Optional.ofNullable(providers.get(id))
            .orElseThrow(() -> new SecretProviderNotFoundException(SECRET_PROVIDER_NOT_FOUND_FOR_ID.formatted(id)));
    }
}
