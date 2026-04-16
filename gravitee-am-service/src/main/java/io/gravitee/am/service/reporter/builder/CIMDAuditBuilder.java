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
package io.gravitee.am.service.reporter.builder;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.service.reporter.builder.gateway.GatewayAuditBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Audit builder for CIMD metadata fetch events.
 *
 * @author GraviteeSource Team
 */
public class CIMDAuditBuilder extends GatewayAuditBuilder<CIMDAuditBuilder> {

    private final Map<String, Object> cimdData = new HashMap<>();

    public CIMDAuditBuilder() {
        super();
        type(EventType.CIMD_METADATA_FETCHED);
    }

    public CIMDAuditBuilder rejected() {
        type(EventType.CIMD_METADATA_REJECTED);
        return this;
    }

    public CIMDAuditBuilder metadataUri(String uri) {
        if (uri != null) cimdData.put("metadataUri", uri);
        return this;
    }

    public CIMDAuditBuilder softwareId(String softwareId) {
        if (softwareId != null) cimdData.put("softwareId", softwareId);
        return this;
    }

    public CIMDAuditBuilder jwksSource(String source) {
        if (source != null) cimdData.put("jwksSource", source);
        return this;
    }

    public CIMDAuditBuilder fetchDurationMs(long duration) {
        cimdData.put("fetchDurationMs", duration);
        return this;
    }

    public CIMDAuditBuilder cacheHit(boolean hit) {
        cimdData.put("cacheHit", hit);
        return this;
    }

    public CIMDAuditBuilder documentHash(String hash) {
        if (hash != null) cimdData.put("documentHash", hash);
        return this;
    }

    public CIMDAuditBuilder rejectionReason(String reason) {
        if (reason != null) cimdData.put("rejectionReason", reason);
        return this;
    }

    public CIMDAuditBuilder resolvedIp(String ip) {
        if (ip != null) cimdData.put("resolvedIp", ip);
        return this;
    }

    @Override
    public Audit build(ObjectMapper mapper) {
        if (!cimdData.isEmpty()) {
            setNewValue(cimdData);
        }
        return super.build(mapper);
    }
}
