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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.dataplane.api.DataPlaneDescription;
import io.gravitee.am.management.service.telemetry.model.SummaryReport;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Installation;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.repository.management.api.ApplicationRepository;
import io.gravitee.am.repository.management.api.CertificateRepository;
import io.gravitee.am.repository.management.api.DomainRepository;
import io.gravitee.am.repository.management.api.EnvironmentRepository;
import io.gravitee.am.repository.management.api.FactorRepository;
import io.gravitee.am.repository.management.api.IdentityProviderRepository;
import io.gravitee.am.repository.management.api.OrganizationRepository;
import io.gravitee.am.service.InstallationService;
import io.gravitee.common.util.Version;
import io.gravitee.node.api.Monitoring;
import io.gravitee.node.api.Node;
import io.gravitee.node.api.NodeMonitoringRepository;
import io.gravitee.node.api.infos.NodeInfos;
import io.gravitee.node.api.infos.NodeStatus;
import io.gravitee.node.api.infos.PluginInfos;
import io.gravitee.node.api.license.License;
import io.gravitee.node.api.license.LicenseManager;
import io.reactivex.rxjava3.core.Single;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;

/**
 * Builds the daily {@link SummaryReport} in one bounded pass over the management repositories.
 * <p>
 * The pass costs the same on an installation with five domains and one with fifty thousand: the
 * domain stream carries the settings the counters need, and the user total comes from the last
 * domain pass rather than from a query.
 *
 * @author GraviteeSource Team
 */
@CustomLog
@RequiredArgsConstructor
public class SummaryReportCollector {

    /** A node whose last NODE_INFOS row is older than this is treated as gone. */
    private static final Duration LIVE_NODE_WINDOW = Duration.ofMinutes(10);

    private static final DateTimeFormatter INSTANT_FORMAT = DateTimeFormatter.ISO_INSTANT;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);

    private final TelemetrySettings settings;
    private final Node node;
    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final InstallationService installationService;
    private final NodeMonitoringRepository nodeMonitoringRepository;
    private final DataPlaneRegistry dataPlaneRegistry;
    private final LicenseManager licenseManager;
    private final DomainRepository domainRepository;
    private final ApplicationRepository applicationRepository;
    private final IdentityProviderRepository identityProviderRepository;
    private final FactorRepository factorRepository;
    private final CertificateRepository certificateRepository;
    private final OrganizationRepository organizationRepository;
    private final EnvironmentRepository environmentRepository;
    private final LastDomainPassHolder lastDomainPass;

    public Single<SummaryReport> collect() {
        return Single.zip(
            installationService.get(),
            liveNodes(),
            domainUsage(),
            countsByType(),
            platformCounts(),
            (installation, nodes, domains, byType, counts) ->
                new SummaryReport(
                    SummaryReport.SCHEMA_VERSION,
                    UUID.randomUUID().toString(),
                    INSTANT_FORMAT.format(Instant.now()),
                    SummaryReport.PRODUCT,
                    installation(installation),
                    reporter(),
                    topology(nodes),
                    storage(),
                    license(),
                    plugins(nodes),
                    usage(domains, byType, counts)
                )
        );
    }

    private SummaryReport.Installation installation(Installation installation) {
        final boolean cloudConnected = installation.getAdditionalInformation() != null &&
            installation.getAdditionalInformation().containsKey(Installation.COCKPIT_INSTALLATION_ID);
        return new SummaryReport.Installation(
            installation.getId(),
            settings.label().isBlank() ? null : settings.label(),
            installation.getCreatedAt() == null ? null : INSTANT_FORMAT.format(installation.getCreatedAt().toInstant()),
            environment.getProperty("installation.type", "standalone"),
            cloudConnected
        );
    }

    private SummaryReport.Reporter reporter() {
        final Version version = Version.RUNTIME_VERSION;
        final Runtime runtime = Runtime.getRuntime();
        return new SummaryReport.Reporter(
            node.id(),
            node.application(),
            version.MAJOR_VERSION,
            version.BUILD_ID,
            version.REVISION,
            ManagementFactory.getRuntimeMXBean().getUptime() / 1000,
            new SummaryReport.Jvm(System.getProperty("java.version"), System.getProperty("java.vendor")),
            new SummaryReport.Os(System.getProperty("os.name"), System.getProperty("os.arch"), System.getProperty("os.version")),
            new SummaryReport.RuntimeInfo(
                System.getenv("KUBERNETES_SERVICE_HOST") != null,
                Files.exists(Path.of("/.dockerenv")),
                runtime.availableProcessors(),
                runtime.maxMemory() / (1024 * 1024)
            )
        );
    }

    /**
     * Reads the NODE_INFOS rows written by gravitee-node for every gateway and management node. The
     * host name and the IP those rows carry are dropped here and never leave this method.
     */
    private Single<List<NodeInfos>> liveNodes() {
        final long to = System.currentTimeMillis();
        final long from = to - LIVE_NODE_WINDOW.toMillis();
        return nodeMonitoringRepository
            .findByTypeAndTimeFrame(Monitoring.NODE_INFOS, from, to)
            .map(monitoring -> objectMapper.readValue(monitoring.getPayload(), NodeInfos.class))
            .filter(infos -> infos.getStatus() == NodeStatus.STARTED)
            .toList()
            .onErrorReturn(throwable -> {
                log.debug("Unable to read the node inventory for the telemetry summary", throwable);
                return List.of();
            });
    }

    private Map<String, SummaryReport.NodeGroup> topology(List<NodeInfos> nodes) {
        final Map<String, List<NodeInfos>> byApplication = new LinkedHashMap<>();
        nodes.forEach(infos -> byApplication.computeIfAbsent(groupName(infos.getApplication()), key -> new ArrayList<>()).add(infos));

        final Map<String, SummaryReport.NodeGroup> topology = new LinkedHashMap<>();
        byApplication.forEach((application, group) ->
            topology.put(
                application,
                new SummaryReport.NodeGroup(group.size(), distinct(group, NodeInfos::getVersion), distinct(group, NodeInfos::getJdkVersion))
            )
        );
        return topology;
    }

    private static String groupName(String application) {
        if (application == null) {
            return "unknown";
        }
        return application.contains("management") ? "management" : "gateway";
    }

    private static List<String> distinct(List<NodeInfos> nodes, java.util.function.Function<NodeInfos, String> field) {
        final SortedSet<String> values = new TreeSet<>();
        nodes.stream().map(field).filter(java.util.Objects::nonNull).forEach(values::add);
        return List.copyOf(values);
    }

    private List<SummaryReport.PluginInfo> plugins(List<NodeInfos> nodes) {
        record Key(String id, String type, String version) {}
        final Map<Key, SortedSet<String>> nodeTypes = new HashMap<>();
        for (NodeInfos infos : nodes) {
            if (infos.getPluginInfos() == null) {
                continue;
            }
            for (PluginInfos plugin : infos.getPluginInfos()) {
                nodeTypes
                    .computeIfAbsent(new Key(plugin.getId(), plugin.getType(), plugin.getVersion()), key -> new TreeSet<>())
                    .add(groupName(infos.getApplication()));
            }
        }
        return nodeTypes
            .entrySet()
            .stream()
            .map(entry -> new SummaryReport.PluginInfo(entry.getKey().id(), entry.getKey().type(), entry.getKey().version(), List.copyOf(entry.getValue())))
            .sorted(Comparator.comparing(SummaryReport.PluginInfo::id).thenComparing(plugin -> String.valueOf(plugin.version())))
            .toList();
    }

    private SummaryReport.Storage storage() {
        final SummaryReport.StorageTarget management = new SummaryReport.StorageTarget(
            environment.getProperty("repositories.management.type", "mongodb"),
            environment.getProperty("repositories.management.jdbc.driver")
        );
        final List<SummaryReport.StorageTarget> dataPlanes = dataPlaneRegistry
            .getDataPlanes()
            .stream()
            .map(DataPlaneDescription::type)
            .distinct()
            .map(type -> new SummaryReport.StorageTarget(type, null))
            .toList();
        return new SummaryReport.Storage(management, dataPlanes);
    }

    private SummaryReport.LicenseInfo license() {
        final License license = licenseManager.getPlatformLicense();
        if (license == null) {
            return null;
        }
        return new SummaryReport.LicenseInfo(
            license.getTier(),
            List.copyOf(license.getPacks()),
            license.getExpirationDate() == null ? null : DATE_FORMAT.format(license.getExpirationDate().toInstant()),
            license.isExpired()
        );
    }

    /**
     * One stream over the domains. It yields the domain total and every {@code domainSettings}
     * counter, so no feature flag costs a query of its own.
     */
    private Single<DomainUsage> domainUsage() {
        final AtomicReference<DomainUsage> accumulator = new AtomicReference<>(DomainUsage.empty());
        return domainRepository
            .findAll()
            .doOnNext(domain -> accumulator.updateAndGet(usage -> usage.add(domain)))
            .ignoreElements()
            .andThen(Single.fromCallable(accumulator::get));
    }

    private Single<Map<String, Map<String, Long>>> countsByType() {
        return Single.zip(
            identityProviderRepository.findAll().toList(),
            factorRepository.findAll().toList(),
            certificateRepository.findAll().toList(),
            (idps, factors, certificates) ->
                Map.of(
                    "identityProvidersByType",
                    group(idps.stream().map(io.gravitee.am.model.IdentityProvider::getType)),
                    "factorsByType",
                    group(factors.stream().map(io.gravitee.am.model.Factor::getType)),
                    "certificatesByType",
                    group(certificates.stream().map(io.gravitee.am.model.Certificate::getType))
                )
        );
    }

    private static Map<String, Long> group(java.util.stream.Stream<String> types) {
        final Map<String, Long> counts = new LinkedHashMap<>();
        types.filter(java.util.Objects::nonNull).forEach(type -> counts.merge(type, 1L, Long::sum));
        return counts;
    }

    private Single<PlatformCounts> platformCounts() {
        return Single.zip(
            organizationRepository.count(),
            environmentRepository.count(),
            applicationRepository.count(),
            PlatformCounts::new
        );
    }

    private SummaryReport.Usage usage(DomainUsage domains, Map<String, Map<String, Long>> byType, PlatformCounts counts) {
        return new SummaryReport.Usage(
            counts.organizations(),
            counts.environments(),
            domains.total(),
            counts.applications(),
            byType.get("identityProvidersByType"),
            byType.get("factorsByType"),
            byType.get("certificatesByType"),
            domains.settings(),
            lastDomainPass.get()
        );
    }

    private record PlatformCounts(long organizations, long environments, long applications) {}

    /**
     * The running totals of the domain stream.
     */
    private record DomainUsage(long total, Map<String, Long> settings) {
        static DomainUsage empty() {
            return new DomainUsage(0, new LinkedHashMap<>());
        }

        DomainUsage add(Domain domain) {
            final Map<String, Long> next = new LinkedHashMap<>(settings);
            next.merge("enabled", domain.isEnabled() ? 1L : 0L, Long::sum);
            next.merge("master", domain.isMaster() ? 1L : 0L, Long::sum);
            next.merge("vhostMode", domain.isVhostMode() ? 1L : 0L, Long::sum);
            next.merge("alertEnabled", Boolean.TRUE.equals(domain.isAlertEnabled()) ? 1L : 0L, Long::sum);
            DomainFlags.of(domain).forEach((flag, on) -> next.merge(flag, on ? 1L : 0L, Long::sum));
            return new DomainUsage(total + 1, next);
        }
    }
}
