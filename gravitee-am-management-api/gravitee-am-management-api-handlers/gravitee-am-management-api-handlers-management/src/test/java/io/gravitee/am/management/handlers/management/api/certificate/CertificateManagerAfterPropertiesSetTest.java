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
package io.gravitee.am.management.handlers.management.api.certificate;

import io.gravitee.am.model.Certificate;
import io.gravitee.am.plugins.certificate.core.CertificatePluginManager;
import io.gravitee.am.service.CertificateService;
import io.reactivex.Single;
import io.reactivex.internal.operators.single.SingleDefer;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.TestScheduler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.gravitee.am.management.handlers.management.api.certificate.CertificateManager.RETRY_DELAY;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class CertificateManagerAfterPropertiesSetTest {

    @Mock
    private CertificateService certificateService;

    @Mock
    private CertificatePluginManager certificatePluginManager;

    @InjectMocks
    private CertificateManager cut = new CertificateManager();

    @Before
    public void setUp() {
        RxJavaPlugins.reset();
    }

    @After
    public void tearDown() {
        RxJavaPlugins.reset();
    }

    @Test
    public void shouldInitializeNormally() {

        Mockito.when(certificateService.findAll())
                .thenReturn(Single.just(Collections.singletonList(new Certificate())));

        cut.afterPropertiesSet();

        verify(certificatePluginManager, times(1)).create(any(), any(), any());
        verify(certificateService, times(1)).setCertificateProviders(anyMap());
    }

    @Test
    public void shouldRetryWhenExceptionIsThrown() {

        TestScheduler testScheduler = new TestScheduler();
        RxJavaPlugins.setComputationSchedulerHandler(scheduler -> testScheduler);

        int maxExceptionCount = 3;
        AtomicInteger count = new AtomicInteger(0);

        Single<List<Certificate>> single = new SingleDefer<>(() -> {
            if (count.incrementAndGet() <= maxExceptionCount) {
                return Single.error(new RuntimeException("Timeout #" + count));
            } else {
                return Single.just(Collections.singletonList(new Certificate()));
            }
        }).subscribeOn(testScheduler);

        Mockito.when(certificateService.findAll())
                .thenReturn(single);

        new Thread(() -> cut.afterPropertiesSet()).start();

        // Manage virtual time to fire retries.
        while (count.get() < maxExceptionCount) {
            testScheduler.advanceTimeBy(RETRY_DELAY, TimeUnit.SECONDS);
        }

        verify(certificatePluginManager, times(1)).create(any(), any(), any());
        verify(certificateService, times(1)).setCertificateProviders(anyMap());
    }
}