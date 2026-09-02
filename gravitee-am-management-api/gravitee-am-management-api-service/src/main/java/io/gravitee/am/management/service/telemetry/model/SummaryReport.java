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
import java.util.List;
import java.util.Map;

/**
 * The daily usage summary, schema version 1, as accepted by {@code POST /v1/am/reports}.
 * <p>
 * Every field is anonymous. The report carries counts, feature flags, plugin identifiers, the
 * random installation UUID and the operator-chosen label. It must never carry a host name, an IP
 * address, a URL, a domain name, an application name or any user data.
 *
 * @author GraviteeSource Team
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SummaryReport(
    int schemaVersion,
    String reportId,
    String sentAt,
    String product,
    Installation installation,
    Reporter reporter,
    Map<String, NodeGroup> topology,
    Storage storage,
    LicenseInfo license,
    List<PluginInfo> plugins,
    Usage usage
) {
    public static final int SCHEMA_VERSION = 1;
    public static final String PRODUCT = "am";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Installation(String id, String label, String createdAt, String type, Boolean cloudConnected) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Reporter(
        String nodeId,
        String application,
        String version,
        String buildId,
        String revision,
        Long uptimeSeconds,
        Jvm jvm,
        Os os,
        RuntimeInfo runtime
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Jvm(String version, String vendor) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Os(String name, String arch, String version) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RuntimeInfo(Boolean kubernetes, Boolean container, Integer cpus, Long maxHeapMb) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NodeGroup(int nodes, List<String> versions, List<String> jdkVersions) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Storage(StorageTarget management, List<StorageTarget> dataPlanes) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StorageTarget(String type, String driver) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LicenseInfo(String tier, List<String> packs, String expiresAt, Boolean expired) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PluginInfo(String id, String type, String version, List<String> nodeTypes) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Usage(
        Long organizations,
        Long environments,
        Long domains,
        Long applications,
        Map<String, Long> identityProvidersByType,
        Map<String, Long> factorsByType,
        Map<String, Long> certificatesByType,
        Map<String, Long> domainSettings,
        LastDomainPass lastDomainPass
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LastDomainPass(String runId, String completedAt, Boolean complete, Long users) {}
}
