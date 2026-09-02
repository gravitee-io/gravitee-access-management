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
class TelemetrySettingsTest {

    @Test
    void shouldAppendThePathsToTheEndpoint() {
        final TelemetrySettings settings = settings("https://telemetry.gravitee.io/v1/am");
        assertThat(settings.reportsUrl()).isEqualTo("https://telemetry.gravitee.io/v1/am/reports");
        assertThat(settings.domainsUrl()).isEqualTo("https://telemetry.gravitee.io/v1/am/domains");
    }

    @Test
    void shouldTolerateATrailingSlash() {
        final TelemetrySettings settings = settings("http://localhost:8080/v1/am/");
        assertThat(settings.reportsUrl()).isEqualTo("http://localhost:8080/v1/am/reports");
        assertThat(settings.domainsUrl()).isEqualTo("http://localhost:8080/v1/am/domains");
    }

    private static TelemetrySettings settings(String endpoint) {
        return new TelemetrySettings(true, endpoint, "", 0, 10000, "0 0 4 * * *", 0, true, "0 0 3 * * SUN", 100, 1000, 4, 3600000);
    }
}
