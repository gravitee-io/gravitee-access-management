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
package io.gravitee.am.service.secrets.resolver;

import io.gravitee.am.service.secrets.errors.SecretNotFoundException;
import io.gravitee.am.service.secrets.providers.SecretProviderRegistry;
import io.gravitee.secrets.api.core.Secret;
import io.gravitee.secrets.api.core.SecretURL;
import io.gravitee.secrets.api.plugin.SecretProvider;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author GraviteeSource Team
 */
@Slf4j
@RequiredArgsConstructor
public class RegistryBasedSecretResolver implements SecretResolver {

    private final SecretProviderRegistry registry;

    @Override
    public Single<Secret> resolveSecretFromUrl(SecretURL url) {
        return resolveSecretProvider(url)
                .flatMapMaybe(secretProvider -> resolveSecretValue(url, secretProvider))
                .switchIfEmpty(Single.error(new SecretNotFoundException("Unable to resolve secret from URL")))
                .doOnError(e -> log.error("Unable to resolve secret from URL: {}", url, e));
    }

    private Single<SecretProvider> resolveSecretProvider(SecretURL url) {
        return Single.fromCallable(() -> registry.get(url.provider()));
    }

    private static Maybe<Secret> resolveSecretValue(SecretURL url, SecretProvider secretProvider) {
        return secretProvider.resolve(url)
            .flatMap(secretMap -> Maybe.fromOptional(secretMap.getSecret(url)));
    }
}
