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
package io.gravitee.am.management.service.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.management.service.telemetry.DomainPassRunner;
import io.gravitee.am.management.service.telemetry.LastDomainPassHolder;
import io.gravitee.am.management.service.telemetry.SummaryReportCollector;
import io.gravitee.am.management.service.telemetry.TelemetryPublisher;
import io.gravitee.am.management.service.telemetry.TelemetryService;
import io.gravitee.am.management.service.telemetry.TelemetrySettings;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.repository.management.api.ApplicationRepository;
import io.gravitee.am.repository.management.api.CertificateRepository;
import io.gravitee.am.repository.management.api.DomainRepository;
import io.gravitee.am.repository.management.api.EnvironmentRepository;
import io.gravitee.am.repository.management.api.FactorRepository;
import io.gravitee.am.repository.management.api.IdentityProviderRepository;
import io.gravitee.am.repository.management.api.OrganizationRepository;
import io.gravitee.am.service.InstallationService;
import io.gravitee.am.service.http.WebClientBuilder;
import io.gravitee.node.api.Node;
import io.gravitee.node.api.NodeMonitoringRepository;
import io.gravitee.node.api.cluster.ClusterManager;
import io.gravitee.node.api.license.LicenseManager;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.client.WebClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.TaskScheduler;

/**
 * Wires the anonymous usage reporter. Every key lives under {@code telemetry} in gravitee.yml and
 * the feature is on by default; {@code telemetry.enabled=false} turns both reports off.
 *
 * @author GraviteeSource Team
 */
@Configuration
public class TelemetryConfiguration {

    @Bean
    public TelemetrySettings telemetrySettings(Environment environment) {
        return new TelemetrySettings(
            environment.getProperty("telemetry.enabled", Boolean.class, true),
            environment.getProperty("telemetry.endpoint", "https://telemetry.gravitee.io/v1/am"),
            environment.getProperty("telemetry.label", ""),
            environment.getProperty("telemetry.initialDelay", Long.class, 600000L),
            environment.getProperty("telemetry.timeout", Long.class, 10000L),
            environment.getProperty("telemetry.summary.cron", "0 0 4 * * *"),
            environment.getProperty("telemetry.spreadMinutes", Integer.class, 60),
            environment.getProperty("telemetry.domains.enabled", Boolean.class, true),
            environment.getProperty("telemetry.domains.cron", "0 0 3 * * SUN"),
            environment.getProperty("telemetry.domains.batchSize", Integer.class, 100),
            environment.getProperty("telemetry.domains.batchDelay", Long.class, 1000L),
            environment.getProperty("telemetry.domains.concurrency", Integer.class, 4),
            environment.getProperty("telemetry.domains.maxDuration", Long.class, 3600000L)
        );
    }

    /**
     * The client honours the shared {@code httpClient.proxy.*} and {@code httpClient.ssl.*} block,
     * so a customer behind a proxy needs no telemetry-specific setting.
     */
    @Bean("telemetryWebClient")
    public WebClient telemetryWebClient(Vertx vertx, WebClientBuilder webClientBuilder, TelemetrySettings settings) {
        return webClientBuilder.createWebClient(vertx, new io.vertx.ext.web.client.WebClientOptions(), settings.endpoint());
    }

    @Bean
    public LastDomainPassHolder lastDomainPassHolder() {
        return new LastDomainPassHolder();
    }

    @Bean
    public TelemetryPublisher telemetryPublisher(
        @Qualifier("telemetryWebClient") WebClient webClient,
        ObjectMapper objectMapper,
        TelemetrySettings settings
    ) {
        return new TelemetryPublisher(webClient, objectMapper, settings);
    }

    @Bean
    public SummaryReportCollector summaryReportCollector(
        TelemetrySettings settings,
        Node node,
        Environment environment,
        ObjectMapper objectMapper,
        InstallationService installationService,
        @Lazy NodeMonitoringRepository nodeMonitoringRepository,
        DataPlaneRegistry dataPlaneRegistry,
        LicenseManager licenseManager,
        @Lazy DomainRepository domainRepository,
        @Lazy ApplicationRepository applicationRepository,
        @Lazy IdentityProviderRepository identityProviderRepository,
        @Lazy FactorRepository factorRepository,
        @Lazy CertificateRepository certificateRepository,
        @Lazy OrganizationRepository organizationRepository,
        @Lazy EnvironmentRepository environmentRepository,
        LastDomainPassHolder lastDomainPassHolder
    ) {
        return new SummaryReportCollector(
            settings,
            node,
            environment,
            objectMapper,
            installationService,
            nodeMonitoringRepository,
            dataPlaneRegistry,
            licenseManager,
            domainRepository,
            applicationRepository,
            identityProviderRepository,
            factorRepository,
            certificateRepository,
            organizationRepository,
            environmentRepository,
            lastDomainPassHolder
        );
    }

    @Bean
    public DomainPassRunner domainPassRunner(
        TelemetrySettings settings,
        ObjectMapper objectMapper,
        TelemetryPublisher publisher,
        @Lazy DomainRepository domainRepository,
        @Lazy ApplicationRepository applicationRepository,
        @Lazy IdentityProviderRepository identityProviderRepository,
        @Lazy FactorRepository factorRepository,
        @Lazy CertificateRepository certificateRepository,
        DataPlaneRegistry dataPlaneRegistry,
        LastDomainPassHolder lastDomainPassHolder
    ) {
        return new DomainPassRunner(
            settings,
            objectMapper,
            publisher,
            domainRepository,
            applicationRepository,
            identityProviderRepository,
            factorRepository,
            certificateRepository,
            dataPlaneRegistry,
            lastDomainPassHolder
        );
    }

    @Bean
    public TelemetryService telemetryService(
        TelemetrySettings settings,
        TaskScheduler taskScheduler,
        ClusterManager clusterManager,
        InstallationService installationService,
        SummaryReportCollector summaryReportCollector,
        DomainPassRunner domainPassRunner,
        TelemetryPublisher telemetryPublisher
    ) {
        return new TelemetryService(
            settings,
            taskScheduler,
            clusterManager,
            installationService,
            summaryReportCollector,
            domainPassRunner,
            telemetryPublisher
        );
    }
}
