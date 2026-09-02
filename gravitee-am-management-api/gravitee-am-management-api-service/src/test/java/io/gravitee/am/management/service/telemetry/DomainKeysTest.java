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
package io.gravitee.am.management.service.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * @author GraviteeSource Team
 */
class DomainKeysTest {

    private static final String INSTALLATION = "5f0c2d3e-8b1a-4f6c-9e2d-7a1b3c4d5e6f";
    private static final String OTHER_INSTALLATION = "0e1d2c3b-4a59-4687-9a8b-7c6d5e4f3a2b";
    private static final String DOMAIN = "domain-1";

    @Test
    void shouldProduceSixteenHexCharacters() {
        assertThat(DomainKeys.key(INSTALLATION, DOMAIN)).matches("^[0-9a-f]{16}$");
    }

    @Test
    void shouldBeStableAcrossPasses() {
        assertThat(DomainKeys.key(INSTALLATION, DOMAIN)).isEqualTo(DomainKeys.key(INSTALLATION, DOMAIN));
    }

    @Test
    void shouldDifferPerInstallation() {
        assertThat(DomainKeys.key(INSTALLATION, DOMAIN)).isNotEqualTo(DomainKeys.key(OTHER_INSTALLATION, DOMAIN));
    }

    @Test
    void shouldDifferPerDomain() {
        assertThat(DomainKeys.key(INSTALLATION, DOMAIN)).isNotEqualTo(DomainKeys.key(INSTALLATION, "domain-2"));
    }

    @Test
    void shouldNotLeakTheDomainIdentifier() {
        assertThat(DomainKeys.key(INSTALLATION, DOMAIN)).doesNotContain(DOMAIN);
    }

    @Test
    void shouldFingerprintSixteenHexCharacters() {
        assertThat(DomainKeys.fingerprint("{\"key\":\"9f2c4e7a1b3d5f60\"}")).matches("^[0-9a-f]{16}$");
    }

    @Test
    void shouldChangeTheFingerprintWhenTheContentChanges() {
        assertThat(DomainKeys.fingerprint("a")).isNotEqualTo(DomainKeys.fingerprint("b"));
    }
}
