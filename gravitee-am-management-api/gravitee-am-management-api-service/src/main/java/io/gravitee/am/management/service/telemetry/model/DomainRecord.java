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
package io.gravitee.am.management.service.telemetry.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * One domain in a {@link DomainBatch}.
 * <p>
 * {@code key} is an HMAC of the domain identifier keyed with the installation identifier, so it is
 * stable across passes and meaningless outside the installation. The record carries no domain name,
 * no hrid and no raw identifier.
 *
 * @author GraviteeSource Team
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DomainRecord(
    String key,
    String createdMonth,
    String dataPlaneType,
    Boolean enabled,
    Boolean master,
    Boolean vhostMode,
    Boolean alertEnabled,
    Map<String, Boolean> settings,
    Map<String, Long> identityProvidersByType,
    Map<String, Long> factorsByType,
    Map<String, Long> certificatesByType,
    Long applications,
    Long users,
    String fingerprint
) {
    /**
     * Returns a copy carrying the counts that the enrichment pass resolved.
     */
    public DomainRecord withCounts(long applicationCount, long userCount) {
        return new DomainRecord(
            key,
            createdMonth,
            dataPlaneType,
            enabled,
            master,
            vhostMode,
            alertEnabled,
            settings,
            identityProvidersByType,
            factorsByType,
            certificatesByType,
            applicationCount,
            userCount,
            fingerprint
        );
    }
}
