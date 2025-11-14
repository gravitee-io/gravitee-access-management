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

import com.google.common.collect.ArrayListMultimap;
import io.gravitee.am.service.secrets.errors.SecretNotFoundException;
import io.gravitee.am.service.secrets.errors.SecretProviderNotFoundException;
import io.gravitee.am.service.secrets.providers.SecretProviderRegistry;
import io.gravitee.secrets.api.core.Secret;
import io.gravitee.secrets.api.core.SecretMap;
import io.gravitee.secrets.api.core.SecretURL;
import io.gravitee.secrets.api.plugin.SecretProvider;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class RegistryBasedSecretResolverTest {

    @Mock
    private SecretProviderRegistry registry;

    @Mock
    private SecretProvider secretProvider;

    @InjectMocks
    private RegistryBasedSecretResolver cut;

    private static final SecretMap TEST_SECRET_MAP = SecretMap.of(Map.of("secretKey", "secretValue"));

    @BeforeEach
    public void init() {
        lenient().when(registry.get("validProvider")).thenReturn(secretProvider);
        lenient().when(registry.get("incorrectProvider")).thenThrow(new SecretProviderNotFoundException("Provider not found"));
    }

    @Test
    public void shouldResolveSecretFromUrl() {
        SecretURL secretURL = new SecretURL("validProvider", "secretPath", "secretKey", ArrayListMultimap.create(), false);
        when(secretProvider.resolve(secretURL)).thenReturn(Maybe.just(TEST_SECRET_MAP));

        cut.resolveSecretFromUrl(secretURL)
            .test()
            .assertComplete()
            .assertResult(new Secret("secretValue"));
    }

    @Test
    public void shouldFailOnInvalidSecretProvider() {
        SecretURL secretURL = new SecretURL("incorrectProvider", "secretPath", "secretKey", ArrayListMultimap.create(), false);
        cut.resolveSecretFromUrl(secretURL)
            .test()
            .assertError(SecretProviderNotFoundException.class);
    }

    @Test
    public void shouldFailOnInvalidSecretPath() {
        SecretURL secretURL = new SecretURL("validProvider", "invalidPath", "secretKey", ArrayListMultimap.create(), false);
        when(secretProvider.resolve(secretURL)).thenReturn(Maybe.empty());

        cut.resolveSecretFromUrl(secretURL)
                .test()
                .assertError(SecretNotFoundException.class);
    }

    @Test
    public void shouldFailOnInvalidSecretKey() {
        SecretURL secretURL = new SecretURL("validProvider", "secretPath", "invalidKey", ArrayListMultimap.create(), false);
        when(secretProvider.resolve(secretURL)).thenReturn(Maybe.just(TEST_SECRET_MAP));

        cut.resolveSecretFromUrl(secretURL)
                .test()
                .assertError(SecretNotFoundException.class);
    }
}
