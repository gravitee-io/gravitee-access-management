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
package io.gravitee.am.service.impl.application;

import io.gravitee.am.dataplane.api.DataPlaneDescription;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Entrypoint;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.VirtualHost;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.DomainRepository;
import io.gravitee.am.service.DomainReadService;
import io.gravitee.am.service.EntryPointManager;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DomainReadServiceImplTest {
    private static final String ENVIRONMENT_ID = "env#1";

    private final DomainRepository domainRepository = mock();
    private final DataPlaneRegistry dataPlaneRegistry = mock();
    private final EntryPointManager entryPointManager = mock();
    private final MockEnvironment springEnvironment = new MockEnvironment();
    private final DomainReadService underTest = new DomainReadServiceImpl(domainRepository, dataPlaneRegistry, entryPointManager, springEnvironment, "http://default:8092");

    @BeforeEach
    public void init() {
        when(dataPlaneRegistry.getDescription(any())).thenReturn(new DataPlaneDescription("default", "Legcay DataPlane", "mongo", "baseProp", "http://localhost:8092"));
    }

    @Test
    public void shouldFindById() {
        when(domainRepository.findById("my-domain")).thenReturn(Maybe.just(new Domain()));
        TestObserver testObserver = underTest.findById("my-domain").test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldFindById_notExistingDomain() {
        when(domainRepository.findById("my-domain")).thenReturn(Maybe.empty());
        TestObserver testObserver = underTest.findById("my-domain").test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertNoValues();
    }

    @Test
    public void shouldFindById_technicalException() {
        when(domainRepository.findById("my-domain")).thenReturn(Maybe.error(TechnicalException::new));
        TestObserver testObserver = new TestObserver();
        underTest.findById("my-domain").subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldFindAll() {
        when(domainRepository.findAll()).thenReturn(Flowable.just(new Domain()));
        TestObserver<List<Domain>> testObserver = underTest.listAll().toList().test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(domains -> domains.size() == 1);
    }

    @Test
    public void shouldFindAll_technicalException() {
        when(domainRepository.findAll()).thenReturn(Flowable.error(TechnicalException::new));
        underTest.listAll().test()
                .assertNotComplete()
                .assertError(TechnicalManagementException.class);
    }

    @Test
    void shouldBuildUrl_contextPathMode() {

        Domain domain = new Domain();
        domain.setPath("/testPath");
        domain.setVhostMode(false);

        String url = underTest.buildUrl(domain, "/mySubPath?myParam=param1");

        assertEquals("http://localhost:8092/testPath/mySubPath?myParam=param1", url);
    }

    @Test
    void shouldBuildUrl_contextPathMode_usingDefault() {
        when(dataPlaneRegistry.getDescription(any())).thenReturn(new DataPlaneDescription("default", "Legcay DataPlane", "mongo", "baseProp", null));

        Domain domain = new Domain();
        domain.setPath("/testPath");
        domain.setVhostMode(false);

        String url = underTest.buildUrl(domain, "/mySubPath?myParam=param1");

        assertEquals("http://default:8092/testPath/mySubPath?myParam=param1", url);
    }

    @Test
    void shouldBuildUrl_vhostMode() {
        Domain domain = new Domain();
        domain.setPath("/testPath");
        domain.setVhostMode(true);
        ArrayList<VirtualHost> vhosts = new ArrayList<>();
        VirtualHost firstVhost = new VirtualHost();
        firstVhost.setHost("test1.gravitee.io");
        firstVhost.setPath("/test1");
        vhosts.add(firstVhost);
        VirtualHost secondVhost = new VirtualHost();
        secondVhost.setHost("test2.gravitee.io");
        secondVhost.setPath("/test2");
        secondVhost.setOverrideEntrypoint(true);
        vhosts.add(secondVhost);
        domain.setVhosts(vhosts);

        String url = underTest.buildUrl(domain, "/mySubPath?myParam=param1");

        assertEquals("http://test2.gravitee.io/test2/mySubPath?myParam=param1", url);
    }

    @Test
    void shouldBuildUrl_vhostModeAndHttps() {

        var underTest = new DomainReadServiceImpl(mock(), dataPlaneRegistry, entryPointManager, springEnvironment, "http://localhost:8092");
        when(dataPlaneRegistry.getDescription(any())).thenReturn(new DataPlaneDescription("default", "Legcay DataPlane", "mongo", "baseProp", "https://localhost:8092"));


        Domain domain = new Domain();
        domain.setPath("/testPath");
        domain.setVhostMode(true);
        ArrayList<VirtualHost> vhosts = new ArrayList<>();
        VirtualHost firstVhost = new VirtualHost();
        firstVhost.setHost("test1.gravitee.io");
        firstVhost.setPath("/test1");
        vhosts.add(firstVhost);
        VirtualHost secondVhost = new VirtualHost();
        secondVhost.setHost("test2.gravitee.io");
        secondVhost.setPath("/test2");
        secondVhost.setOverrideEntrypoint(true);
        vhosts.add(secondVhost);
        domain.setVhosts(vhosts);

        String url = underTest.buildUrl(domain, "/mySubPath?myParam=param1");

        assertEquals("https://test2.gravitee.io/test2/mySubPath?myParam=param1", url);
    }

    @Test
    void shouldBuildUrl_nonCloud_neverConsultsTheEntrypointManager() {
        Domain domain = cloudDomain();

        underTest.buildUrl(domain, "/mySubPath");

        verifyNoInteractions(entryPointManager);
    }

    @Test
    void shouldBuildUrl_cloud_usesEnvironmentEntrypoint() {
        enableCloudMode();
        when(entryPointManager.findPrimaryByEnvironmentId(ENVIRONMENT_ID)).thenReturn(Optional.of(entrypoint("https://auth.acme.com")));

        String url = underTest.buildUrl(cloudDomain(), "/mySubPath?myParam=param1");

        assertEquals("https://auth.acme.com/testPath/mySubPath?myParam=param1", url);
    }

    @Test
    void shouldBuildUrl_cloud_stripsTrailingSlashFromEntrypoint() {
        enableCloudMode();
        when(entryPointManager.findPrimaryByEnvironmentId(ENVIRONMENT_ID)).thenReturn(Optional.of(entrypoint("https://auth.acme.com/")));

        String url = underTest.buildUrl(cloudDomain(), "/mySubPath");

        assertEquals("https://auth.acme.com/testPath/mySubPath", url);
    }

    @Test
    void shouldBuildUrl_cloud_noEntrypoint_fallsBackToDataPlaneUrl() {
        enableCloudMode();
        when(entryPointManager.findPrimaryByEnvironmentId(ENVIRONMENT_ID)).thenReturn(Optional.empty());

        String url = underTest.buildUrl(cloudDomain(), "/mySubPath?myParam=param1");

        assertEquals("http://localhost:8092/testPath/mySubPath?myParam=param1", url);
    }

    @Test
    void shouldBuildUrl_cloud_noEntrypoint_nullDataPlaneUrl_fallsBackToConfiguredGatewayUrl() {
        enableCloudMode();
        when(dataPlaneRegistry.getDescription(any())).thenReturn(new DataPlaneDescription("default", "Legcay DataPlane", "mongo", "baseProp", null));
        when(entryPointManager.findPrimaryByEnvironmentId(ENVIRONMENT_ID)).thenReturn(Optional.empty());

        String url = underTest.buildUrl(cloudDomain(), "/mySubPath?myParam=param1");

        assertEquals("http://default:8092/testPath/mySubPath?myParam=param1", url);
    }

    @Test
    void shouldBuildUrl_cloud_vhostModeStillWins() {
        // Unreachable in production: managed cloud leaves domainRestrictions unset, so vhost mode never
        // turns on. Pinned so the precedence is defined if that ever changes.
        enableCloudMode();
        when(entryPointManager.findPrimaryByEnvironmentId(ENVIRONMENT_ID)).thenReturn(Optional.of(entrypoint("https://auth.acme.com")));

        Domain domain = cloudDomain();
        domain.setVhostMode(true);
        VirtualHost vhost = new VirtualHost();
        vhost.setHost("legacy.gravitee.io");
        vhost.setPath("/legacy");
        vhost.setOverrideEntrypoint(true);
        domain.setVhosts(new ArrayList<>(List.of(vhost)));

        String url = underTest.buildUrl(domain, "/mySubPath");

        assertEquals("https://legacy.gravitee.io/legacy/mySubPath", url);
    }

    @Test
    public void shouldBuildUrl_threeArgFormPassesNoRequestOrigin() {
        when(dataPlaneRegistry.getDescription(any())).thenReturn(new DataPlaneDescription(null, null, null, null, "https://gw.gravitee.io"));
        Domain domain = cloudDomain();

        assertEquals(underTest.buildUrl(domain, "/mySubPath", null, null), underTest.buildUrl(domain, "/mySubPath", null));
    }

    @Test
    public void shouldBuildUrl_cloud_requestOriginWinsWhenItIsAnEnvironmentEntrypoint() {
        enableCloudMode();
        when(entryPointManager.findAllByEnvironmentId(ENVIRONMENT_ID))
                .thenReturn(List.of(entrypoint("https://generated.gravitee.io"), entrypoint("https://custom.acme.com")));

        String url = underTest.buildUrl(cloudDomain(), "/mySubPath", null, "https://custom.acme.com");

        assertEquals("https://custom.acme.com/testPath/mySubPath", url);
    }

    @Test
    public void shouldBuildUrl_cloud_requestOriginMatchIsCaseInsensitiveAndIgnoresTrailingSlash() {
        enableCloudMode();
        when(entryPointManager.findAllByEnvironmentId(ENVIRONMENT_ID)).thenReturn(List.of(entrypoint("https://Custom.Acme.com/")));

        String url = underTest.buildUrl(cloudDomain(), "/mySubPath", null, "https://custom.acme.com");

        assertEquals("https://Custom.Acme.com/testPath/mySubPath", url);
    }

    @Test
    public void shouldBuildUrl_cloud_requestOriginMatchesEntrypointCarryingAnExplicitDefaultPort() {
        // resolveOrigin drops :443, so a stored entrypoint that spells it out has to compare equal or
        // the request would be ignored on a host the user legitimately reached us on.
        enableCloudMode();
        when(entryPointManager.findAllByEnvironmentId(ENVIRONMENT_ID)).thenReturn(List.of(entrypoint("https://custom.acme.com:443")));

        String url = underTest.buildUrl(cloudDomain(), "/mySubPath", null, "https://custom.acme.com");

        assertEquals("https://custom.acme.com:443/testPath/mySubPath", url);
    }

    @Test
    public void shouldBuildUrl_cloud_blankRequestOriginFallsBack() {
        enableCloudMode();
        when(entryPointManager.findPrimaryByEnvironmentId(ENVIRONMENT_ID)).thenReturn(Optional.of(entrypoint("https://custom.acme.com")));

        String url = underTest.buildUrl(cloudDomain(), "/mySubPath", null, "   ");

        assertEquals("https://custom.acme.com/testPath/mySubPath", url);
    }

    @Test
    public void shouldBuildUrl_cloud_unknownRequestOriginIsRejected() {
        // The whole point of the check: a forged Host must never steer the link.
        enableCloudMode();
        when(entryPointManager.findAllByEnvironmentId(ENVIRONMENT_ID)).thenReturn(List.of(entrypoint("https://custom.acme.com")));
        when(entryPointManager.findPrimaryByEnvironmentId(ENVIRONMENT_ID)).thenReturn(Optional.of(entrypoint("https://custom.acme.com")));

        String url = underTest.buildUrl(cloudDomain(), "/mySubPath", null, "https://evil.example");

        assertEquals("https://custom.acme.com/testPath/mySubPath", url);
    }

    @Test
    public void shouldBuildUrl_cloud_noMatchingEntrypointFallsBackToTheFirstCustomHost() {
        enableCloudMode();
        when(entryPointManager.findAllByEnvironmentId(ENVIRONMENT_ID)).thenReturn(List.of(entrypoint("https://custom.acme.com")));
        when(entryPointManager.findPrimaryByEnvironmentId(ENVIRONMENT_ID)).thenReturn(Optional.of(entrypoint("https://custom.acme.com")));

        String url = underTest.buildUrl(cloudDomain(), "/mySubPath", null, null);

        assertEquals("https://custom.acme.com/testPath/mySubPath", url);
    }

    @Test
    public void shouldBuildUrl_nonCloud_ignoresRequestOrigin() {
        // Agreed 29 Jul: the change is limited to managed cloud, self-hosted behaviour must not move.
        when(dataPlaneRegistry.getDescription(any())).thenReturn(new DataPlaneDescription(null, null, null, null, "https://gw.gravitee.io"));

        String url = underTest.buildUrl(cloudDomain(), "/mySubPath", null, "https://custom.acme.com");

        assertEquals("https://gw.gravitee.io/testPath/mySubPath", url);
        verifyNoInteractions(entryPointManager);
    }

    private void enableCloudMode() {
        springEnvironment.setProperty("cloud.enabled", "true");
        springEnvironment.setProperty("installation.type", "managed");
    }

    private static Domain cloudDomain() {
        Domain domain = new Domain();
        domain.setPath("/testPath");
        domain.setVhostMode(false);
        domain.setReferenceType(ReferenceType.ENVIRONMENT);
        domain.setReferenceId(ENVIRONMENT_ID);
        return domain;
    }

    private static Entrypoint entrypoint(String url) {
        Entrypoint entrypoint = new Entrypoint();
        entrypoint.setUrl(url);
        return entrypoint;
    }

}
