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

import io.gravitee.am.dataplan.api.provider.DataPlanProvider;
import io.gravitee.am.dataplan.api.repository.DataPlanPOCRepository;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.VirtualHost;
import io.gravitee.am.plugins.dataplan.core.DataPlanLoader;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.DomainRepository;
import io.gravitee.am.service.DomainReadService;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DomainReadServiceImplTest {
    private final DomainRepository domainRepository = mock();
    private final DomainReadService underTest;

    {
        DataPlanLoader mock = mock(DataPlanLoader.class);
        DataPlanProvider dataPlanProvider = mock(DataPlanProvider.class);
        DataPlanPOCRepository pocRepository = mock(DataPlanPOCRepository.class);
        when(pocRepository.writeValue(any())).thenReturn(Completable.complete());
        when(dataPlanProvider.getDataPlanPOCRepository()).thenReturn(pocRepository);
        when(mock.getDataPlanProvider(any())).thenReturn(Optional.of(dataPlanProvider));
        underTest = new DomainReadServiceImpl(domainRepository, "http://localhost:8092", mock);
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
        DataPlanLoader mock = mock(DataPlanLoader.class);
        DataPlanProvider dataPlanProvider = mock(DataPlanProvider.class);
        DataPlanPOCRepository pocRepository = mock(DataPlanPOCRepository.class);
        when(pocRepository.writeValue(any())).thenReturn(Completable.complete());
        when(dataPlanProvider.getDataPlanPOCRepository()).thenReturn(pocRepository);
        when(mock.getDataPlanProvider(any())).thenReturn(Optional.of(dataPlanProvider));
        var underTest = new DomainReadServiceImpl(mock(), "https://localhost:8092", mock);

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

}
