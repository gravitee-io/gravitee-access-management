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

/**
 * The resolved {@code telemetry.*} block of gravitee.yml.
 *
 * @author GraviteeSource Team
 */
public record TelemetrySettings(
    boolean enabled,
    String endpoint,
    String label,
    long initialDelay,
    long timeout,
    String summaryCron,
    int spreadMinutes,
    boolean domainsEnabled,
    String domainsCron,
    int batchSize,
    long batchDelay,
    int concurrency,
    long maxDuration
) {
    public String reportsUrl() {
        return trimmedEndpoint() + "/reports";
    }

    public String domainsUrl() {
        return trimmedEndpoint() + "/domains";
    }

    private String trimmedEndpoint() {
        return endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
    }
}
