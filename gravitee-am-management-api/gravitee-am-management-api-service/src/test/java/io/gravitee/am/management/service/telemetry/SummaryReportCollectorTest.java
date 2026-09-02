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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.dataplane.api.DataPlaneDescription;
import io.gravitee.am.management.service.telemetry.model.SummaryReport;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.IdentityProvider;
import io.gravitee.am.model.Installation;
import io.gravitee.am.model.scim.SCIMSettings;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.repository.management.api.ApplicationRepository;
import io.gravitee.am.repository.management.api.CertificateRepository;
import io.gravitee.am.repository.management.api.DomainRepository;
import io.gravitee.am.repository.management.api.EnvironmentRepository;
import io.gravitee.am.repository.management.api.FactorRepository;
import io.gravitee.am.repository.management.api.IdentityProviderRepository;
import io.gravitee.am.repository.management.api.OrganizationRepository;
import io.gravitee.am.service.InstallationService;
import io.gravitee.node.api.Monitoring;
import io.gravitee.node.api.Node;
import io.gravitee.node.api.NodeMonitoringRepository;
import io.gravitee.node.api.license.License;
import io.gravitee.node.api.license.LicenseManager;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class SummaryReportCollectorTest {

    private static final String INSTALLATION_ID = "5f0c2d3e-8b1a-4f6c-9e2d-7a1b3c4d5e6f";
    private static final String HOSTNAME = "am-management-7d9f5c8b4-xk2vq";
    private static final String NODE_IP = "10.42.7.13";

    @Mock
    private Node node;

    @Mock
    private Environment environment;

    @Mock
    private InstallationService installationService;

    @Mock
    private NodeMonitoringRepository nodeMonitoringRepository;

    @Mock
    private DataPlaneRegistry dataPlaneRegistry;

    @Mock
    private LicenseManager licenseManager;

    @Mock
    private DomainRepository domainRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private IdentityProviderRepository identityProviderRepository;

    @Mock
    private FactorRepository factorRepository;

    @Mock
    private CertificateRepository certificateRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private License license;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SummaryReportCollector collector;

    @BeforeEach
    void setUp() {
        final TelemetrySettings settings = new TelemetrySettings(
            true,
            "http://localhost:8080/v1/am",
            "acme-prod",
            0,
            10000,
            "0 0 4 * * *",
            0,
            true,
            "0 0 3 * * SUN",
            100,
            1000,
            4,
            3600000
        );
        collector = new SummaryReportCollector(
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
            new LastDomainPassHolder()
        );

        final Installation installation = new Installation();
        installation.setId(INSTALLATION_ID);
        installation.setCreatedAt(new Date(0));
        lenient().when(installationService.get()).thenReturn(Single.just(installation));

        lenient().when(node.id()).thenReturn("b5057132-9c23-4985-8571-329c23998558");
        lenient().when(node.application()).thenReturn("gio-am-management");

        lenient().when(environment.getProperty(anyString(), anyString())).thenAnswer(call -> call.getArgument(1));
        lenient().when(environment.getProperty(anyString())).thenReturn(null);

        lenient().when(nodeMonitoringRepository.findByTypeAndTimeFrame(any(), anyLong(), anyLong())).thenReturn(Flowable.just(nodeInfos()));
        lenient().when(dataPlaneRegistry.getDataPlanes()).thenReturn(List.of(new DataPlaneDescription("default", "Default", "mongodb", "", null)));

        lenient().when(licenseManager.getPlatformLicense()).thenReturn(license);
        lenient().when(license.getTier()).thenReturn("planet");
        lenient().when(license.getPacks()).thenReturn(Set.of("enterprise-identity"));
        lenient().when(license.getExpirationDate()).thenReturn(null);
        lenient().when(license.isExpired()).thenReturn(false);

        lenient().when(domainRepository.findAll()).thenReturn(Flowable.fromIterable(domains()));
        lenient().when(identityProviderRepository.findAll()).thenReturn(Flowable.just(identityProvider("mongo-am-idp"), identityProvider("mongo-am-idp")));
        lenient().when(factorRepository.findAll()).thenReturn(Flowable.just(factor("otp-am-factor")));
        lenient().when(certificateRepository.findAll()).thenReturn(Flowable.just(certificate("pkcs12-am-certificate")));
        lenient().when(organizationRepository.count()).thenReturn(Single.just(1L));
        lenient().when(environmentRepository.count()).thenReturn(Single.just(2L));
        lenient().when(applicationRepository.count()).thenReturn(Single.just(39L));
    }

    @Test
    void shouldCountTheDomainsAndTheirFeatureFlags() {
        final SummaryReport report = collector.collect().blockingGet();

        assertThat(report.usage().domains()).isEqualTo(3);
        assertThat(report.usage().domainSettings()).containsEntry("enabled", 2L).containsEntry("scim", 1L).containsEntry("saml", 0L);
    }

    @Test
    void shouldGroupTheChildrenByType() {
        final SummaryReport report = collector.collect().blockingGet();

        assertThat(report.usage().identityProvidersByType()).containsEntry("mongo-am-idp", 2L);
        assertThat(report.usage().factorsByType()).containsEntry("otp-am-factor", 1L);
        assertThat(report.usage().certificatesByType()).containsEntry("pkcs12-am-certificate", 1L);
    }

    @Test
    void shouldReadTheNodeInventory() {
        final SummaryReport report = collector.collect().blockingGet();

        assertThat(report.topology()).containsKey("management");
        assertThat(report.topology().get("management").nodes()).isEqualTo(1);
        assertThat(report.topology().get("management").versions()).containsExactly("4.13.0");
        assertThat(report.plugins()).singleElement().satisfies(plugin -> assertThat(plugin.id()).isEqualTo("sms-am-factor"));
    }

    @Test
    void shouldCarryTheLicenceAndTheLabel() {
        final SummaryReport report = collector.collect().blockingGet();

        assertThat(report.license().tier()).isEqualTo("planet");
        assertThat(report.license().packs()).containsExactly("enterprise-identity");
        assertThat(report.installation().label()).isEqualTo("acme-prod");
        assertThat(report.installation().id()).isEqualTo(INSTALLATION_ID);
    }

    @Test
    void shouldNeverCarryAHostnameOrAnIpAddress() throws Exception {
        final String json = objectMapper.writeValueAsString(collector.collect().blockingGet());

        assertThat(json).doesNotContain(HOSTNAME).doesNotContain(NODE_IP);
        assertThat(json).doesNotMatch("(?s).*\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b.*");
    }

    private Monitoring nodeInfos() {
        final Monitoring monitoring = new Monitoring();
        monitoring.setType(Monitoring.NODE_INFOS);
        monitoring.setPayload(
            """
            {
              "id": "b5057132-9c23-4985-8571-329c23998558",
              "application": "gio-am-management",
              "status": "STARTED",
              "version": "4.13.0",
              "jdkVersion": "25.0.1",
              "hostname": "%s",
              "ip": "%s",
              "pluginInfos": [{ "id": "sms-am-factor", "type": "factor", "version": "1.0.3" }]
            }
            """.formatted(HOSTNAME, NODE_IP)
        );
        return monitoring;
    }

    private static List<Domain> domains() {
        final Domain enabled = new Domain();
        enabled.setId("domain-1");
        enabled.setEnabled(true);

        final Domain withScim = new Domain();
        withScim.setId("domain-2");
        withScim.setEnabled(true);
        final SCIMSettings scim = new SCIMSettings();
        scim.setEnabled(true);
        withScim.setScim(scim);

        final Domain disabled = new Domain();
        disabled.setId("domain-3");
        disabled.setEnabled(false);

        return List.of(enabled, withScim, disabled);
    }

    private static IdentityProvider identityProvider(String type) {
        final IdentityProvider provider = new IdentityProvider();
        provider.setType(type);
        return provider;
    }

    private static Factor factor(String type) {
        final Factor factor = new Factor();
        factor.setType(type);
        return factor;
    }

    private static Certificate certificate(String type) {
        final Certificate certificate = new Certificate();
        certificate.setType(type);
        return certificate;
    }
}
