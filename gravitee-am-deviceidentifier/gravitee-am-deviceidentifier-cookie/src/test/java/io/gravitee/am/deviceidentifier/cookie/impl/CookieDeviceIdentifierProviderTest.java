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
package io.gravitee.am.deviceidentifier.cookie.impl;

import io.gravitee.am.deviceidentifier.cookie.CookieDeviceIdentifierConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class CookieDeviceIdentifierProviderTest {

    @Spy
    private CookieDeviceIdentifierConfiguration configuration = new CookieDeviceIdentifierConfiguration();

    @InjectMocks
    private CookieDeviceIdentifierProvider provider;

    @Test
    void shouldNotUseEtagByDefault() {
        assertThat(provider.useEtagToKeepIdentifier()).isFalse();
    }

    @Test
    void shouldUseEtagWhenEnabled() {
        configuration.setUseEtag(true);

        assertThat(provider.useEtagToKeepIdentifier()).isTrue();
    }

    @Test
    void shouldNotUseEtagWithoutConfiguration() {
        assertThat(new CookieDeviceIdentifierProvider().useEtagToKeepIdentifier()).isFalse();
    }
}
